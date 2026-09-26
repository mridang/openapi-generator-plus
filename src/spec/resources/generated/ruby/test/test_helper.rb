# frozen_string_literal: true

require 'etc'
require 'securerandom'

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
  'opentelemetry/sdk' => 'opentelemetry-sdk'
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

# The Configuration every integration test uses: the Chasm mock server plus
# the bearer token its fixtures expect. There is no process-wide default
# Configuration, so each API instance is handed this one explicitly.
TEST_CONFIGURATION = Petstore::Client::Configuration.builder
                                                  .base_url(chasm_url)
                                                  .default_header('Authorization', 'Bearer test-token')
                                                  .build

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

SQUID = Testcontainers::DockerContainer.new('ubuntu/squid:5.2-22.04_beta')
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
ENV['CA_CERT_PATH'] = File.join(Dir.pwd, 'test', 'fixtures', 'certs', 'ca.pem')
