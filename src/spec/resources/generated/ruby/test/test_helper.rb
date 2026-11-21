# frozen_string_literal: true

require 'etc'
require 'securerandom'
require 'socket'

# Every dependency an emitted test loads is listed, with the test that needs
# it, in .openapi-generator/DEV-DEPENDENCIES. A client keep-lists its own
# Gemfile, so a dependency the generator started using is missing there until
# someone copies it across -- and the whole suite then dies with a LoadError
# from whichever require happened to come first. Probing them together turns
# that into one message naming the file to reconcile against.
DEV_DEPENDENCIES = '.openapi-generator/DEV-DEPENDENCIES'

REQUIRED_TEST_GEMS = {
  'simplecov' => 'simplecov',
  'simplecov-cobertura' => 'simplecov-cobertura',
  'minitest/autorun' => 'minitest',
  'minitest/reporters' => 'minitest-reporters',
  'better_junit' => 'better_coverage',
  'testcontainers' => 'testcontainers',
  'docker' => 'docker-api',
  'opentelemetry/sdk' => 'opentelemetry-sdk',
  'opentelemetry-metrics-sdk' => 'opentelemetry-metrics-sdk'
}.freeze

missing_test_gems = REQUIRED_TEST_GEMS.filter_map do |feature, gem_name|
  require feature
  nil
rescue LoadError
  gem_name
end

unless missing_test_gems.empty?
  raise "Missing test dependencies: #{missing_test_gems.join(', ')}. Every dependency the " \
        "generated tests load is listed in #{DEV_DEPENDENCIES}; add the missing entries to " \
        "this project's Gemfile and run bundle install."
end

SimpleCov.start do
  formatter SimpleCov::Formatter::CoberturaFormatter
  coverage_dir '.out'
  add_filter '/test/'
  track_files 'lib/**/*.rb'
end

$LOAD_PATH.unshift File.expand_path('../lib', File.dirname(__FILE__))

require 'minitest/reporters'
require 'better_junit'

Minitest::Reporters.use! [
  Minitest::Reporters::DefaultReporter.new,
  MinitestPlus::BetterJUnit.new(path: '.out/reports/junit.xml')
]

require 'minitest/autorun'
require 'minitest/pride'

# Run parallelizable test classes (those that call `parallelize_me!`) across
# all available CPU cores. The previous attempt at this failed because rake
# exited 1: Minitest.after_run hooks were running in the wrong order (network
# removal before container stops). That ordering is now fixed below — the
# CHASM.stop re-registration runs LAST (so via Minitest's LIFO it fires
# FIRST), detaching the container before PROXY_NETWORK.remove is invoked.
Minitest.parallel_executor = Minitest::Parallel::Executor.new(Etc.nprocessors)

require 'testcontainers'
require 'docker'
require 'net/http'
require 'petstore_client'

host_app_path = ENV['HOST_APP_PATH'] || Dir.pwd

# CHASM_HTTP_URL and PROXY_URL point at instances that are already running,
# with the other variables the tests read already set beside them; nothing is
# started.
shared_helpers = ENV.key?('CHASM_HTTP_URL') && ENV.key?('PROXY_URL')

unless shared_helpers
  spec_path = File.join(host_app_path, 'test', 'fixtures', 'openapi.yaml')
  chasm_cert_path = File.join(host_app_path, 'test', 'fixtures', 'certs', 'server.pem')
  chasm_key_path = File.join(host_app_path, 'test', 'fixtures', 'certs', 'server-key.pem')

  CHASM = Testcontainers::DockerContainer.new('mridang/chasm:1.3.0')
  CHASM.with_exposed_port(4010)
  CHASM.with_exposed_port(8443)
  CHASM.with_filesystem_binds([
                                "#{spec_path}:/tmp/openapi.yaml:ro",
                                "#{chasm_cert_path}:/certs/cert.pem:ro",
                                "#{chasm_key_path}:/certs/key.pem:ro"
                              ])
  CHASM.with_command(
    'mock', '/tmp/openapi.yaml', '--host', '0.0.0.0',
    '--tls-cert', '/certs/cert.pem',
    '--tls-key', '/certs/key.pem',
    '--tls-port', '8443'
  )
  CHASM.with_wait_for(:logs, /Listening on/, timeout: 120)

  CHASM.start

  # Use Minitest.after_run instead of at_exit to stop Chasm AFTER tests complete.
  # at_exit hooks run in LIFO order, and minitest/autorun registers its at_exit
  # (which runs the tests) before ours, so at_exit { CHASM.stop } would stop
  # Chasm before the tests even start.
  Minitest.after_run { CHASM.stop }

  # Ruby testcontainers does not respect TESTCONTAINERS_HOST_OVERRIDE like other
  # language implementations, so we read it ourselves for DooD compatibility.
  chasm_host = ENV['TESTCONTAINERS_HOST_OVERRIDE'] || CHASM.host
  chasm_port = CHASM.mapped_port(4010)
  chasm_tls_port = CHASM.mapped_port(8443)
  chasm_url = "http://#{chasm_host}:#{chasm_port}"

  ENV['API_BASE_URL'] = chasm_url
  ENV['CHASM_HTTP_URL'] = "http://#{chasm_host}:#{chasm_port}"
  ENV['CHASM_HTTPS_URL'] = "https://#{chasm_host}:#{chasm_tls_port}"
  ENV['CHASM_INTERNAL_HTTP_URL'] = 'http://chasm:4010'
  ENV['CHASM_INTERNAL_HTTPS_URL'] = 'https://chasm:8443'
end

chasm_url = ENV.fetch('CHASM_HTTP_URL')

# The Configuration every integration test uses: the Chasm mock server plus
# the bearer token its fixtures expect. There is no process-wide default
# Configuration, so each API instance is handed this one explicitly.
TEST_CONFIGURATION = Petstore::Client::Configuration.builder
                                                  .base_url(chasm_url)
                                                  .default_header('Authorization', 'Bearer test-token')
                                                  .build

unless shared_helpers
  # Create a shared Docker network for proxy tests so Squid can reach Chasm
  # directly via container alias, avoiding host.docker.internal DNS issues.
  # Register the network removal hook BEFORE the container stop hooks so it
  # runs LAST in Minitest's LIFO after_run order — otherwise the network
  # removal fails with 403 Forbidden because containers are still connected.
  PROXY_NETWORK = Docker::Network.create("proxy-test-network-#{SecureRandom.hex(4)}")
  Minitest.after_run { PROXY_NETWORK.remove }

  PROXY_NETWORK.connect(CHASM._container.id, {}, { 'EndpointConfig' => { 'Aliases' => ['chasm'] } })

  # ubuntu/squid declares VOLUME /var/log/squid and VOLUME /var/spool/squid, so
  # every proxy container Docker creates leaves two anonymous volumes behind
  # after the suite stops it. testcontainers-core 0.2 has no tmpfs setter, so the
  # Tmpfs host config is added to this one container's create options. mode=1777
  # because squid drops to the unprivileged `proxy` user before it opens its logs.
  module SquidTmpfs
    TMPFS = { '/var/log/squid' => 'rw,mode=1777', '/var/spool/squid' => 'rw,mode=1777' }.freeze

    def _container_create_options
      options = super
      options['HostConfig'] = (options['HostConfig'] || {}).merge('Tmpfs' => TMPFS)
      options
    end
  end

  # Start Squid proxy
  squid_conf_path = File.join(host_app_path, 'test', 'fixtures', 'proxy', 'squid.conf')

  SQUID = Testcontainers::DockerContainer.new('ubuntu/squid:6.10-24.10_beta')
  SQUID.singleton_class.prepend(SquidTmpfs)
  SQUID.with_exposed_ports(3128, 3129)
  SQUID.with_filesystem_binds(["#{squid_conf_path}:/etc/squid/squid.conf:ro"])

  SQUID.start
  Minitest.after_run { SQUID.stop }

  PROXY_NETWORK.connect(SQUID._container.id)

  # CHASM.stop was previously registered above with the container creation
  # (line ~59). Re-register here so it runs BEFORE PROXY_NETWORK.remove
  # (LIFO: last-registered runs first). The earlier registration is now a
  # fallback; this re-add ensures the container detaches before network teardown.
  Minitest.after_run do
    CHASM.stop
  rescue StandardError
    nil
  end

  sleep 3

  squid_host = ENV['TESTCONTAINERS_HOST_OVERRIDE'] || SQUID.host
  ENV['PROXY_URL'] = "http://#{squid_host}:#{SQUID.mapped_port(3128)}"
  # host:port of the fixture proxy port that requires Basic proxy credentials.
  ENV['PROXY_AUTH_HOST_PORT'] = "#{squid_host}:#{SQUID.mapped_port(3129)}"
end

ENV['CA_CERT_PATH'] = File.join(Dir.pwd, 'test', 'fixtures', 'certs', 'ca.pem')

# A call hook that records what it is told, for the tests of the hook. It runs
# +edit+ with the headers when it starts and writes to +log+, when given, the
# order in which it started and finished.
class RecordingCallHook
  attr_reader :infos, :seen_headers, :outcomes

  def initialize(name = nil, log = nil, &edit)
    @name = name
    @log = log
    @edit = edit
    @infos = [] # : Array[untyped]
    @seen_headers = [] # : Array[Hash[String, String]]
    @outcomes = [] # : Array[untyped]
  end

  def start(info, headers)
    @infos << info
    @seen_headers << headers.dup
    @log&.push("start #{@name}")
    @edit&.call(headers)
    RecordingCallObserver.new(self)
  end

  def finished(outcome)
    @outcomes << outcome
    @log&.push("finish #{@name}")
  end
end

# The observer a RecordingCallHook returns.
class RecordingCallObserver
  def initialize(hook)
    @hook = hook
  end

  def finish(outcome)
    @hook.finished(outcome)
  end
end

# An in-process HTTP/1.1 server on a raw socket, so a test can stall a body,
# cut it short or reset the connection, which neither the shared mock server
# nor a plain HTTP server can do. +script+ runs once per connection with the
# server, the socket, the number of the hit and the request head.
class RawServer
  attr_reader :hits, :heads

  # Starts a server that runs +script+ for every connection it accepts.
  def self.serve(&)
    new(&)
  end

  def initialize(&script)
    @listener = TCPServer.new('127.0.0.1', 0) # steep:ignore UnexpectedPositionalArgument
    @script = script
    @lock = Mutex.new
    @hits = 0
    @heads = [] # : Array[String]
    @release = Queue.new
    @client_gone = Queue.new
    @acceptor = Thread.new { accept_loop }
  end

  def port
    @listener.addr[1]
  end

  def url(path)
    "http://127.0.0.1:#{port}#{path}"
  end

  # Lets a script that waits on the test carry on; also done when the server closes.
  def release!
    @release.close
  end

  def released?
    @release.closed?
  end

  # Waits until the test releases the script, or for 20 seconds.
  def await_release
    @release.pop(timeout: 20)
    nil
  end

  # Called by a script whose write failed because the client dropped the connection.
  def client_gone!
    @client_gone.close
  end

  # Whether a script saw the client drop the connection, waiting up to +seconds+ for it.
  def client_gone?(seconds = 10)
    @client_gone.pop(timeout: seconds)
    @client_gone.closed?
  end

  def close
    release!
    @listener.close
    @acceptor.join(2)
  end

  private

  def accept_loop
    loop do
      socket = @listener.accept
      Thread.new(socket) { |connection| handle(connection) }
    rescue IOError, SystemCallError
      break
    end
  end

  def handle(socket)
    head = +''
    head << socket.readpartial(1) until head.end_with?("\r\n\r\n")
    hit = @lock.synchronize do
      @heads << head
      @hits += 1
    end
    @script.call(self, socket, hit, head)
  rescue IOError, SystemCallError
    nil
  ensure
    socket.close unless socket.closed?
  end
end

# What a RawServer script writes.
module RawHttp
  # The status line and headers; the connection is closed after the body.
  def self.head(socket, status, *headers)
    lines = ["HTTP/1.1 #{status} Test", *headers, 'Connection: close', '', '']
    socket.write(lines.join("\r\n"))
    socket.flush
  end

  # One chunk of a chunked body.
  def self.chunk(socket, data)
    socket.write("#{data.bytesize.to_s(16)}\r\n#{data}\r\n")
    socket.flush
  end

  # The last chunk of a chunked body.
  def self.finish(socket)
    socket.write("0\r\n\r\n")
    socket.flush
  end

  # Makes the close of the socket reset the connection instead of ending it cleanly.
  def self.reset(socket)
    socket.setsockopt(Socket::SOL_SOCKET, Socket::SO_LINGER, [1, 0].pack('ii'))
    socket.close
  end

  # A script for other tests: one chunk, then silence until the test releases the server.
  def self.one_chunk_then_hold(server, socket, _hit, _head)
    head(socket, 200, 'Transfer-Encoding: chunked')
    chunk(socket, 'tick')
    server.await_release
  end

  # A script for other tests: a complete two byte text response.
  def self.reply_ok(_server, socket, _hit, _head)
    head(socket, 200, 'Content-Type: text/plain', 'Content-Length: 2')
    socket.write('ok')
  end
end

# Runs one test over a table of cases and names the case that failed. A table
# of cases is one test per behaviour, so the report carries each behaviour once
# and a failure still says which row broke.
module EachCase
  def each_case
    each do |*row|
      yield(*row)
    rescue Minitest::Assertion => e
      raise e if e.is_a?(Minitest::Skip)

      raise e.class, "case #{row.inspect}: #{e.message}", e.backtrace
    end
  end
end

Array.include(EachCase)
Hash.include(EachCase)
