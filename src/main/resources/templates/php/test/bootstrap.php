<?php

declare(strict_types=1);

/* phpcs:disable PSR1.Files.SideEffects */

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
    } catch (\TypeError | \RuntimeException $e) {
        // Fallback to docker CLI port lookup when the testcontainers-php
        // library can't see the mapping (RuntimeException) or chokes on
        // healthcheck timestamps (TypeError).
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
$specPath = $hostAppPath . '/test/fixtures/openapi.yaml';
$chasmCertPath = $hostAppPath . '/test/fixtures/certs/server.pem';
$chasmKeyPath = $hostAppPath . '/test/fixtures/certs/server-key.pem';

$chasm = (new GenericContainer('mridang/chasm:1.3.0'))
    ->withExposedPorts(4010, 8443)
    ->withMount($specPath, '/tmp/openapi.yaml')
    ->withMount($chasmCertPath, '/certs/cert.pem')
    ->withMount($chasmKeyPath, '/certs/key.pem')
    ->withCommand([
        'mock', '/tmp/openapi.yaml', '--host', '0.0.0.0',
        '--tls-cert', '/certs/cert.pem',
        '--tls-key', '/certs/key.pem',
        '--tls-port', '8443',
    ])
    ->withWait(new WaitForLog('Listening on', false, 120000))
    ->start();

$chasmHost = $chasm->getHost();
$chasmHttpUrl = 'http://' . $chasmHost . ':' . safeGetMappedPort($chasm, 4010);
$chasmHttpsUrl = 'https://' . $chasmHost . ':' . safeGetMappedPort($chasm, 8443);

putenv('API_BASE_URL=' . $chasmHttpUrl);
putenv('CHASM_HTTP_URL=' . $chasmHttpUrl);
putenv('CHASM_HTTPS_URL=' . $chasmHttpsUrl);
putenv('CHASM_INTERNAL_HTTP_URL=http://chasm:4010');
putenv('CHASM_INTERNAL_HTTPS_URL=https://chasm:8443');

// Create a shared Docker network so Squid can reach Chasm directly
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
}

dockerApiRequest($socketPath, '/networks/create', 'POST', ['Name' => $networkName]);
dockerApiRequest($socketPath, "/networks/$networkName/connect", 'POST', [
    'Container' => $chasm->getId(),
    'EndpointConfig' => ['Aliases' => ['chasm']],
]);

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

register_shutdown_function(function () use ($chasm, $squid, $networkName, $socketPath): void {
    $squid->stop();
    $chasm->stop();
    dockerApiRequest($socketPath, "/networks/$networkName", 'DELETE');
});
