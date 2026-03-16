<?php

require_once __DIR__ . '/../vendor/autoload.php';

use Testcontainers\Container\GenericContainer;
use Testcontainers\Wait\WaitForLog;

$hostAppPath = getenv('HOST_APP_PATH') ?: getcwd();
$specPath = $hostAppPath . '/specs/openapi.yaml';

$prism = (new GenericContainer('stoplight/prism:5'))
    ->withExposedPorts(4010)
    ->withMount($specPath, '/tmp/openapi.yaml')
    ->withCommand(['mock', '-h', '0.0.0.0', '/tmp/openapi.yaml'])
    ->withWait(new WaitForLog('Prism is listening'))
    ->start();

$baseUrl = 'http://' . $prism->getHost() . ':' . $prism->getMappedPort(4010);

putenv('API_BASE_URL=' . $baseUrl);

// Start WireMock with HTTPS
$keystorePath = $hostAppPath . '/certs/server-keystore.p12';
$mappingsPath = $hostAppPath . '/wiremock/mappings';

$wiremock = (new GenericContainer('wiremock/wiremock:3.13.0'))
    ->withExposedPorts(8080, 8443)
    ->withMount($keystorePath, '/tmp/keystore.p12')
    ->withMount($mappingsPath, '/home/wiremock/mappings')
    ->withCommand([
        '--port', '8080',
        '--https-port', '8443',
        '--https-keystore', '/tmp/keystore.p12',
        '--keystore-type', 'PKCS12',
        '--keystore-password', 'changeit',
        '--key-manager-password', 'changeit',
        '--verbose',
    ])
    ->withWait(new WaitForLog('port:'))
    ->start();

$wiremockHost = $wiremock->getHost();
putenv('WIREMOCK_HTTPS_URL=https://' . $wiremockHost . ':' . $wiremock->getMappedPort(8443));
putenv('WIREMOCK_HTTP_URL=http://' . $wiremockHost . ':' . $wiremock->getMappedPort(8080));

// Start Squid proxy
$squidConfPath = $hostAppPath . '/proxy/squid.conf';

$squid = (new GenericContainer('ubuntu/squid:5.2-22.04_beta'))
    ->withExposedPorts(3128)
    ->withMount($squidConfPath, '/etc/squid/squid.conf')
    ->start();

// Give Squid a moment to initialize
sleep(3);

putenv('PROXY_URL=http://' . $squid->getHost() . ':' . $squid->getMappedPort(3128));
putenv('CA_CERT_PATH=' . getcwd() . '/certs/ca.pem');

register_shutdown_function(function () use ($prism, $wiremock, $squid) {
    $squid->stop();
    $wiremock->stop();
    $prism->stop();
});
