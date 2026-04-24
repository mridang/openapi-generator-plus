# frozen_string_literal: true

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

PRISM = Testcontainers::DockerContainer.new('stoplight/prism:5')
PRISM.with_exposed_port(4010)
PRISM.with_filesystem_binds(["#{spec_path}:/tmp/openapi.yaml:ro"])
PRISM.with_command('mock', '-m', 'false', '-h', '0.0.0.0', '/tmp/openapi.yaml')

PRISM.start

# Use Minitest.after_run instead of at_exit to stop Prism AFTER tests complete.
# at_exit hooks run in LIFO order, and minitest/autorun registers its at_exit
# (which runs the tests) before ours, so at_exit { PRISM.stop } would stop
# Prism before the tests even start.
Minitest.after_run { PRISM.stop }

# Wait for Prism to be ready by checking container logs
60.times do
  logs = PRISM._container.logs(stdout: true, stderr: true)
  break if logs.include?('Prism is listening')

  sleep 1
end

# Ruby testcontainers does not respect TESTCONTAINERS_HOST_OVERRIDE like other
# language implementations, so we read it ourselves for DooD compatibility.
prism_host = ENV['TESTCONTAINERS_HOST_OVERRIDE'] || PRISM.host
prism_port = PRISM.mapped_port(4010)
prism_url = "http://#{prism_host}:#{prism_port}"

ENV['API_BASE_URL'] = prism_url

# Configure the client to use the Prism mock server
PetstoreClient.configure do |b|
  b.base_url prism_url
  b.default_header 'Authorization', 'Bearer test-token'
end

# Create a shared Docker network for proxy tests so Squid can reach WireMock
# directly via container alias, avoiding host.docker.internal DNS issues.
PROXY_NETWORK = Docker::Network.create('proxy-test-network')
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

WIREMOCK.start
Minitest.after_run { WIREMOCK.stop }

60.times do
  logs = WIREMOCK._container.logs(stdout: true, stderr: true)
  break if logs.include?('port:')

  sleep 1
end

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
