# frozen_string_literal: true

require 'securerandom'
require 'simplecov'
require 'simplecov-cobertura'

SimpleCov.start do
  formatter SimpleCov::Formatter::CoberturaFormatter
  coverage_dir '.out'
  add_filter '/test/'
  track_files 'lib/**/*.rb'
end

$LOAD_PATH.unshift File.expand_path('../lib', __dir__)

require 'minitest/reporters'
require 'better_junit'

Minitest::Reporters.use! [
  Minitest::Reporters::DefaultReporter.new,
  MinitestPlus::BetterJUnit.new(path: '.out/reports/junit.xml')
]

require 'minitest/autorun'
require 'minitest/pride'

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

# Configure the client to use the Chasm mock server
PetstoreClient.configure do |b|
  b.base_url chasm_url
  b.default_header 'Authorization', 'Bearer test-token'
end

# Create a shared Docker network for proxy tests so Squid can reach Chasm
# directly via container alias, avoiding host.docker.internal DNS issues.
# Register the network removal hook BEFORE the container stop hooks so it
# runs LAST in Minitest's LIFO after_run order — otherwise the network
# removal fails with 403 Forbidden because containers are still connected.
PROXY_NETWORK = Docker::Network.create("proxy-test-network-#{SecureRandom.hex(4)}")
Minitest.after_run { PROXY_NETWORK.remove }

PROXY_NETWORK.connect(CHASM._container.id, {}, { 'EndpointConfig' => { 'Aliases' => ['chasm'] } })

# Start Squid proxy
squid_conf_path = File.join(host_app_path, 'test', 'fixtures', 'proxy', 'squid.conf')

SQUID = Testcontainers::DockerContainer.new('ubuntu/squid:5.2-22.04_beta')
SQUID.with_exposed_port(3128)
SQUID.with_filesystem_binds(["#{squid_conf_path}:/etc/squid/squid.conf:ro"])

SQUID.start
Minitest.after_run { SQUID.stop }

PROXY_NETWORK.connect(SQUID._container.id)

# CHASM.stop was previously registered above with the container creation
# (line ~59). Re-register here so it runs BEFORE PROXY_NETWORK.remove
# (LIFO: last-registered runs first). The earlier registration is now a
# fallback; this re-add ensures the container detaches before network teardown.
Minitest.after_run do
  begin
    CHASM.stop
  rescue StandardError
    nil
  end
end

sleep 3

squid_host = ENV['TESTCONTAINERS_HOST_OVERRIDE'] || SQUID.host
ENV['PROXY_URL'] = "http://#{squid_host}:#{SQUID.mapped_port(3128)}"
ENV['CA_CERT_PATH'] = File.join(Dir.pwd, 'test', 'fixtures', 'certs', 'ca.pem')
