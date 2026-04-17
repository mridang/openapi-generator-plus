<?php

declare(strict_types=1);

require_once __DIR__ . '/../vendor/autoload.php';

use Testcontainers\Container\GenericContainer;
use Testcontainers\Container\StartedGenericContainer;
use Testcontainers\Wait\WaitForLog;

/**
 * Wrapper around getMappedPort that falls back to `docker port` CLI
 * when the beluga-php/docker-php-api library throws a TypeError
 * due to unparseable healthcheck timestamps.
 */
function safeGetMappedPort(StartedGenericContainer $container, int $port): int
{
    try {
        return $container->getMappedPort($port);
    } catch (\TypeError $e) {
        $containerId = $container->getId();
        $rawOutput = shell_exec("docker port $containerId $port 2>/dev/null");
        $output = is_string($rawOutput) ? trim($rawOutput) : '';
        if (preg_match('/:(\d+)$/', $output, $matches)) {
            return (int) $matches[1];
        }
        throw $e;
    }
}

$hostAppPath = getenv('HOST_APP_PATH') ?: getcwd();
$specPath = $hostAppPath . '/specs/openapi.yaml';

$prism = (new GenericContainer('stoplight/prism:5'))
    ->withExposedPorts(4010)
    ->withMount($specPath, '/tmp/openapi.yaml')
    ->withCommand(['mock', '-m', 'false', '-h', '0.0.0.0', '/tmp/openapi.yaml'])
    ->withWait(new WaitForLog('Prism is listening', false, 120000))
    ->start();

$baseUrl = 'http://' . $prism->getHost() . ':' . safeGetMappedPort($prism, 4010);

putenv('API_BASE_URL=' . $baseUrl);

// Start WireMock with HTTPS
$keystorePath = $hostAppPath . '/test/fixtures/certs/server-keystore.p12';
$mappingsPath = $hostAppPath . '/test/fixtures/wiremock/mappings';

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
    ->withWait(new WaitForLog('port:', false, 120000))
    ->start();

$wiremockHost = $wiremock->getHost();
putenv('WIREMOCK_HTTPS_URL=https://' . $wiremockHost . ':' . safeGetMappedPort($wiremock, 8443));
putenv('WIREMOCK_HTTP_URL=http://' . $wiremockHost . ':' . safeGetMappedPort($wiremock, 8080));

// Create a shared Docker network so Squid can reach WireMock directly
// via container alias, avoiding host.docker.internal DNS issues.
// Uses the Docker Engine API via Unix socket (no docker CLI needed).
$dockerSocket = getenv('DOCKER_HOST') ?: 'unix:///var/run/docker.sock';
$socketPath = str_replace('unix://', '', $dockerSocket);
$networkName = 'proxy-test-network-' . bin2hex(random_bytes(4));

/**
 * Send a request to the Docker Engine API via Unix socket.
 *
 * @param string                              $socketPath Unix socket path
 * @param string                              $endpoint   API path (e.g. "/networks/create")
 * @param string                              $method     HTTP method
 * @param array<string, mixed>|null           $body       JSON body to send
 */
function dockerApiRequest(string $socketPath, string $endpoint, string $method = 'POST', ?array $body = null): void
{
    $ch = curl_init("http://localhost$endpoint");
    /** @phpstan-ignore argument.type */
    curl_setopt($ch, CURLOPT_UNIX_SOCKET_PATH, $socketPath);
    /** @phpstan-ignore argument.type */
    curl_setopt($ch, CURLOPT_CUSTOMREQUEST, $method);
    curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
    if ($body !== null) {
        curl_setopt($ch, CURLOPT_POSTFIELDS, (string) json_encode($body));
        curl_setopt($ch, CURLOPT_HTTPHEADER, ['Content-Type: application/json']);
    }
    curl_exec($ch);
    curl_close($ch);
}

dockerApiRequest($socketPath, '/networks/create', 'POST', ['Name' => $networkName]);
dockerApiRequest($socketPath, "/networks/$networkName/connect", 'POST', [
    'Container' => $wiremock->getId(),
    'EndpointConfig' => ['Aliases' => ['wiremock']],
]);

putenv('WIREMOCK_INTERNAL_HTTP_URL=http://wiremock:8080');
putenv('WIREMOCK_INTERNAL_HTTPS_URL=https://wiremock:8443');

// Start Squid proxy
$squidConfPath = $hostAppPath . '/test/fixtures/proxy/squid.conf';

$squid = (new GenericContainer('ubuntu/squid:5.2-22.04_beta'))
    ->withExposedPorts(3128)
    ->withMount($squidConfPath, '/etc/squid/squid.conf')
    ->start();

dockerApiRequest($socketPath, "/networks/$networkName/connect", 'POST', [
    'Container' => $squid->getId(),
]);

// Give Squid a moment to initialize
sleep(3);

putenv('PROXY_URL=http://' . $squid->getHost() . ':' . safeGetMappedPort($squid, 3128));
putenv('CA_CERT_PATH=' . getcwd() . '/test/fixtures/certs/ca.pem');

register_shutdown_function(function () use ($prism, $wiremock, $squid, $networkName, $socketPath): void {
    $squid->stop();
    $wiremock->stop();
    $prism->stop();
    dockerApiRequest($socketPath, "/networks/$networkName", 'DELETE');
});
