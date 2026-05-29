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

CHASM = Testcontainers::DockerContainer.new('mridang/chasm:1.2.5')
CHASM.with_exposed_port(4010)
CHASM.with_filesystem_binds(["#{spec_path}:/tmp/openapi.yaml:ro"])
CHASM.with_command('mock', '/tmp/openapi.yaml', '--host', '0.0.0.0')
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
chasm_url = "http://#{chasm_host}:#{chasm_port}"

ENV['API_BASE_URL'] = chasm_url

# Configure the client to use the Chasm mock server
PetstoreClient.configure do |b|
  b.base_url chasm_url
  b.default_header 'Authorization', 'Bearer test-token'
end

# Create a shared Docker network for proxy tests so Squid can reach WireMock
# directly via container alias, avoiding host.docker.internal DNS issues.
PROXY_NETWORK = Docker::Network.create("proxy-test-network-#{SecureRandom.hex(4)}")
Minitest.after_run { PROXY_NETWORK.remove }

# Start WireMock server with HTTPS
keystore_path = File.join(host_app_path, 'test', 'fixtures', 'certs', 'server-keystore.p12')
mappings_path = File.join(host_app_path, 'test', 'fixtures', 'wiremock', 'mappings')

WIREMOCK = Testcontainers::DockerContainer.new('wiremock/wiremock:3.13.0')
WIREMOCK.with_exposed_port(8080)
WIREMOCK.with_exposed_port(8443)
WIREMOCK.with_filesystem_binds([
  "#{keystore_path}:/tmp/keystore.p12:ro",
  "#{mappings_path}:/home/wiremock/mappings:ro"
])
WIREMOCK.with_command(
  '--port', '8080',
  '--https-port', '8443',
  '--https-keystore', '/tmp/keystore.p12',
  '--keystore-type', 'PKCS12',
  '--keystore-password', 'changeit',
  '--key-manager-password', 'changeit',
  '--verbose'
)
WIREMOCK.with_wait_for(:logs, /port:/, timeout: 120)

WIREMOCK.start
Minitest.after_run { WIREMOCK.stop }

PROXY_NETWORK.connect(WIREMOCK._container.id, {}, { 'EndpointConfig' => { 'Aliases' => ['wiremock'] } })

wiremock_host = ENV['TESTCONTAINERS_HOST_OVERRIDE'] || WIREMOCK.host
ENV['WIREMOCK_HTTPS_URL'] = "https://#{wiremock_host}:#{WIREMOCK.mapped_port(8443)}"
ENV['WIREMOCK_HTTP_URL'] = "http://#{wiremock_host}:#{WIREMOCK.mapped_port(8080)}"
ENV['WIREMOCK_INTERNAL_HTTP_URL'] = 'http://wiremock:8080'
ENV['WIREMOCK_INTERNAL_HTTPS_URL'] = 'https://wiremock:8443'

# Start Squid proxy
squid_conf_path = File.join(host_app_path, 'test', 'fixtures', 'proxy', 'squid.conf')

SQUID = Testcontainers::DockerContainer.new('ubuntu/squid:5.2-22.04_beta')
SQUID.with_exposed_port(3128)
SQUID.with_filesystem_binds(["#{squid_conf_path}:/etc/squid/squid.conf:ro"])

SQUID.start
Minitest.after_run { SQUID.stop }

PROXY_NETWORK.connect(SQUID._container.id)

sleep 3

squid_host = ENV['TESTCONTAINERS_HOST_OVERRIDE'] || SQUID.host
ENV['PROXY_URL'] = "http://#{squid_host}:#{SQUID.mapped_port(3128)}"
ENV['CA_CERT_PATH'] = File.join(Dir.pwd, 'test', 'fixtures', 'certs', 'ca.pem')
