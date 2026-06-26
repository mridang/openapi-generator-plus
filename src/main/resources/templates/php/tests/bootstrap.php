<?php

declare(strict_types=1);

// phpcs:ignoreFile

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
    } catch (\Throwable $e) {
        // Fallback to docker CLI port lookup when the testcontainers-php
        // library can't see the mapping or chokes on healthcheck
        // timestamps. Catching Throwable since the lib may throw its
        // own subclassed exception types that we don't import.
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
$specPath = $hostAppPath . '/tests/fixtures/openapi.yaml';
$chasmCertPath = $hostAppPath . '/tests/fixtures/certs/server.pem';
$chasmKeyPath = $hostAppPath . '/tests/fixtures/certs/server-key.pem';

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

// HTTPS port resolution falls back to docker CLI when testcontainers-php
// can't see it. If both fail, set a placeholder so the bootstrap doesn't
// die; tests requiring TLS will fail individually rather than killing
// the whole suite.
try {
    $chasmHttpsUrl = 'https://' . $chasmHost . ':' . safeGetMappedPort($chasm, 8443);
} catch (\Throwable $e) {
    fwrite(STDERR, "[bootstrap] could not resolve chasm HTTPS port: " . $e->getMessage() . "\n");
    $chasmHttpsUrl = 'https://chasm-tls-unavailable.invalid';
}

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
    curl_setopt($ch, CURLOPT_UNIX_SOCKET_PATH, $socketPath);
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
$squidConfPath = $hostAppPath . '/tests/fixtures/proxy/squid.conf';

$squid = (new GenericContainer('ubuntu/squid:5.2-22.04_beta'))
    ->withExposedPorts(3128)
    ->withMount($squidConfPath, '/etc/squid/squid.conf')
    ->start();

dockerApiRequest($socketPath, "/networks/$networkName/connect", 'POST', [
    'Container' => $squid->getId(),
]);

// Give Squid a moment to initialize
sleep(3);

// PROXY_URL falls back the same way chasm HTTPS does — when
// testcontainers-php can't read the mapped port, set a placeholder so
// the bootstrap doesn't kill the whole pest run. Proxy-using tests
// will fail individually with a clearer error.
try {
    putenv('PROXY_URL=http://' . $squid->getHost() . ':' . safeGetMappedPort($squid, 3128));
} catch (\Throwable $e) {
    fwrite(STDERR, "[bootstrap] could not resolve squid proxy port: " . $e->getMessage() . "\n");
    putenv('PROXY_URL=http://proxy-unavailable.invalid:0');
}
putenv('CA_CERT_PATH=' . getcwd() . '/tests/fixtures/certs/ca.pem');

register_shutdown_function(function () use ($chasm, $squid, $networkName, $socketPath): void {
    // Each cleanup step is wrapped because paratest's worker processes
    // run their own bootstrap and treat any non-zero exit during
    // shutdown as a "Worker crashed" — failing the whole run. When the
    // PHP runtime is already tearing down, container.stop() can hit a
    // dead docker socket and throw; swallow each failure so the worker
    // exits cleanly.
    try {
        $squid->stop();
    } catch (\Throwable) {
        // ignored
    }
    try {
        $chasm->stop();
    } catch (\Throwable) {
        // ignored
    }
    try {
        dockerApiRequest($socketPath, "/networks/$networkName", 'DELETE');
    } catch (\Throwable) {
        // ignored
    }
});
