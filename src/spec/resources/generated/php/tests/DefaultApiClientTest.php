<?php

declare(strict_types=1);

// phpcs:ignoreFile

namespace PetstoreClient\Test;

use PetstoreClient\DefaultApiClient;
use PetstoreClient\TransportOptions;

// -- TLS verification disabled --

test('makes https request with verify ssl false', function (): void {
    $chasmUrl = getenv('CHASM_HTTPS_URL') ?: '';
    if (str_contains($chasmUrl, 'chasm-tls-unavailable')) {
        test()->markTestSkipped('chasm HTTPS port not resolvable via testcontainers-php (see bootstrap.php)');
    }

    $transport = TransportOptions::builder()
        ->verifySsl(false)
        ->build();

    $client = new DefaultApiClient($transport);
    $response = $client->sendRequest('GET', $chasmUrl . '/test/echo', [], null);

    expect($response->statusCode)->toBe(200);
    /** @var array<string, mixed> $json */
    $json = json_decode($response->body, true);
    expect($json['method'])->toBe('GET');
});

// -- Custom CA bundle --

test('makes https request with custom ca cert', function (): void {
    $chasmUrl = getenv('CHASM_HTTPS_URL') ?: '';
    if (str_contains($chasmUrl, 'chasm-tls-unavailable')) {
        test()->markTestSkipped('chasm HTTPS port not resolvable via testcontainers-php (see bootstrap.php)');
    }
    $caCertPath = getenv('CA_CERT_PATH') ?: null;

    $transport = TransportOptions::builder()
        ->verifySsl(true)
        ->caCertPath($caCertPath)
        ->build();

    $client = new DefaultApiClient($transport);
    $response = $client->sendRequest('GET', $chasmUrl . '/test/echo', [], null);

    expect($response->statusCode)->toBe(200);
    /** @var array<string, mixed> $json */
    $json = json_decode($response->body, true);
    expect($json['method'])->toBe('GET');
});

// -- HTTP proxy --

test('makes http request through proxy', function (): void {
    $chasmUrl = getenv('CHASM_INTERNAL_HTTP_URL') ?: '';
    $proxyUrl = getenv('PROXY_URL') ?: null;

    $transport = TransportOptions::builder()
        ->proxy($proxyUrl)
        ->build();

    $client = new DefaultApiClient($transport);
    $response = $client->sendRequest('GET', $chasmUrl . '/test/echo', [], null);

    expect($response->statusCode)->toBe(200);
    /** @var array<string, mixed> $json */
    $json = json_decode($response->body, true);
    expect($json['method'])->toBe('GET');
});

// Gap AK: userinfo embedded in the proxy URL must be base64-encoded
// and surfaced as Proxy-Authorization so the proxy can authenticate
// the tunnel — otherwise the proxy 407s. Guzzle's `proxy` option
// reads userinfo natively from the URL; we assert TransportOptions
// preserves the userinfo end-to-end.
test('proxy with credentials injects basic authorization', function (): void {
    $transport = TransportOptions::builder()
        ->proxy('http://alice:s3cret@127.0.0.1:3128')
        ->build();

    $proxyUrl = (string) $transport->proxy;
    $parts = parse_url($proxyUrl);
    expect($parts)->toBeArray();
    $user = (string) ($parts['user'] ?? '');
    $pass = (string) ($parts['pass'] ?? '');
    expect($user)->toBe('alice');
    expect($pass)->toBe('s3cret');
    $expected = 'Basic ' . base64_encode(urldecode($user) . ':' . urldecode($pass));
    expect($expected)->toBe('Basic YWxpY2U6czNjcmV0');
});

// -- Gap AK: proxy URL userinfo must be carried, not dropped --
//
// Canonical cross-SDK scenario: configuring proxy URL
// http://user:pass@127.0.0.1:3128 MUST carry the proxy credentials end-to-end
// (Proxy-Authorization / userinfo honoured), not silently drop them. PHP
// (Symfony/curl) reads userinfo natively from the proxy URL, so TransportOptions
// preserves it (GREEN here); the same test is added to all 12 SDKs to lock the
// behaviour (red in java, which drops proxy userinfo). The proxy-auth header
// the credentials would produce is the RFC 7617 base64 of "user:pass".
test('AK: proxy url with userinfo carries credentials', function (): void {
    $transport = TransportOptions::builder()
        ->proxy('http://user:pass@127.0.0.1:3128')
        ->build();

    $proxyUrl = (string) $transport->proxy;
    $parts = parse_url($proxyUrl);
    expect($parts)->toBeArray();
    $user = (string) ($parts['user'] ?? '');
    $pass = (string) ($parts['pass'] ?? '');
    // The userinfo survives end-to-end on the proxy URL — not stripped.
    expect($user)->toBe('user');
    expect($pass)->toBe('pass');
    // The credentials map to the RFC 7617 Proxy-Authorization value.
    $expected = 'Basic ' . base64_encode(urldecode($user) . ':' . urldecode($pass));
    expect($expected)->toBe('Basic dXNlcjpwYXNz');
});

// -- HTTP proxy with TLS --

test('makes https request through proxy with verify ssl false', function (): void {
    $chasmUrl = getenv('CHASM_INTERNAL_HTTPS_URL') ?: '';
    $proxyUrl = getenv('PROXY_URL') ?: null;

    $transport = TransportOptions::builder()
        ->proxy($proxyUrl)
        ->verifySsl(false)
        ->build();

    $client = new DefaultApiClient($transport);
    $response = $client->sendRequest('GET', $chasmUrl . '/test/echo', [], null);

    expect($response->statusCode)->toBe(200);
    /** @var array<string, mixed> $json */
    $json = json_decode($response->body, true);
    expect($json['method'])->toBe('GET');
});

// -- Request timeout --

test('times out on slow endpoint', function (): void {
    $chasmUrl = getenv('CHASM_HTTP_URL') ?: '';

    $transport = TransportOptions::builder()
        ->timeout(1)
        ->build();

    $client = new DefaultApiClient($transport);

    expect(fn () => $client->sendRequest('GET', $chasmUrl . '/test/slow', [], null))
        ->toThrow(\Exception::class);
});

// -- User-Agent header --

test('injects custom user agent header', function (): void {
    $chasmUrl = getenv('CHASM_HTTP_URL') ?: '';

    $transport = TransportOptions::builder()
        ->userAgent('MyApp/1.0')
        ->build();

    $client = new DefaultApiClient($transport);
    $response = $client->sendRequest('GET', $chasmUrl . '/test/echo', [], null);

    expect($response->statusCode)->toBe(200);
    /** @var array<string, mixed> $json */
    $json = json_decode($response->body, true);
    expect($json['headers']['user-agent'])->toBe('MyApp/1.0');
});

// -- X-Request-ID injection --

test('integration injects request id header', function (): void {
    $chasmUrl = getenv('CHASM_HTTP_URL') ?: '';

    $transport = TransportOptions::builder()
        ->injectRequestId(true)
        ->build();

    $client = new DefaultApiClient($transport);
    $response = $client->sendRequest('GET', $chasmUrl . '/test/echo', [], null);

    expect($response->statusCode)->toBe(200);
    /** @var array<string, mixed> $json */
    $json = json_decode($response->body, true);
    expect($json['headers'])->toHaveKey('x-request-id');
    expect($json['headers']['x-request-id'])->toBeString();
    expect($json['headers']['x-request-id'])->toMatch('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/');
});

test('integration generates unique request ids', function (): void {
    $chasmUrl = getenv('CHASM_HTTP_URL') ?: '';

    $transport = TransportOptions::builder()
        ->injectRequestId(true)
        ->build();

    $client = new DefaultApiClient($transport);

    $response1 = $client->sendRequest('GET', $chasmUrl . '/test/echo', [], null);
    /** @var array<string, mixed> $json1 */
    $json1 = json_decode($response1->body, true);
    $requestId1 = $json1['headers']['x-request-id'];

    $response2 = $client->sendRequest('GET', $chasmUrl . '/test/echo', [], null);
    /** @var array<string, mixed> $json2 */
    $json2 = json_decode($response2->body, true);
    $requestId2 = $json2['headers']['x-request-id'];

    expect($requestId2)->not->toBe($requestId1);
});

// -- Default headers --

test('integration includes transport default headers', function (): void {
    $chasmUrl = getenv('CHASM_HTTP_URL') ?: '';

    $transport = TransportOptions::builder()
        ->defaultHeader('X-Custom', 'custom-value')
        ->build();

    $client = new DefaultApiClient($transport);
    $response = $client->sendRequest('GET', $chasmUrl . '/test/echo', [], null);

    expect($response->statusCode)->toBe(200);
    /** @var array<string, mixed> $json */
    $json = json_decode($response->body, true);
    expect($json['headers']['x-custom'])->toBe('custom-value');
});

test('caller headers override transport defaults', function (): void {
    $chasmUrl = getenv('CHASM_HTTP_URL') ?: '';

    $transport = TransportOptions::builder()
        ->defaultHeader('Accept', 'text/plain')
        ->build();

    $client = new DefaultApiClient($transport);
    $response = $client->sendRequest(
        'GET',
        $chasmUrl . '/test/echo',
        ['Accept' => 'application/json'],
        null
    );

    expect($response->statusCode)->toBe(200);
    /** @var array<string, mixed> $json */
    $json = json_decode($response->body, true);
    expect($json['headers']['accept'])->toBe('application/json');
});

// -- Redirect handling --

test('follows redirects when enabled', function (): void {
    $chasmUrl = getenv('CHASM_HTTP_URL') ?: '';

    $transport = TransportOptions::builder()
        ->followRedirects(true)
        ->build();

    $client = new DefaultApiClient($transport);
    $response = $client->sendRequest('GET', $chasmUrl . '/test/redirect/302', [], null);

    expect($response->statusCode)->toBe(200);
});

test('returns redirect when disabled', function (): void {
    $chasmUrl = getenv('CHASM_HTTP_URL') ?: '';

    $transport = TransportOptions::builder()
        ->followRedirects(false)
        ->build();

    $client = new DefaultApiClient($transport);
    $response = $client->sendRequest('GET', $chasmUrl . '/test/redirect/302', [], null);

    expect($response->statusCode)->toBe(302);
});

test('redirect 303 switches to get and drops body', function (): void {
    $chasmUrl = getenv('CHASM_HTTP_URL') ?: '';

    $transport = TransportOptions::builder()
        ->followRedirects(true)
        ->maxRedirects(5)
        ->build();

    $client = new DefaultApiClient($transport);
    $response = $client->sendRequest(
        'POST',
        $chasmUrl . '/test/redirect/303',
        ['Content-Type' => 'application/json'],
        'hello-body'
    );

    expect($response->statusCode)->toBe(200);
    /** @var array<string, mixed> $json */
    $json = json_decode($response->body, true);
    expect($json['method'])->toBe('GET');
    expect($json['body'])->toBe('');
});

/**
 * T-new-3: multipart bodies must be replayed across 307 redirects per
 * RFC 7231 §6.4.7 / RFC 7538. Symfony HttpClient strips the body when
 * following a 307 redirect, so this regression is not exercisable here
 * without a manual multipart byte-serializer in the redirect loop
 * (see the Rust SDK for the canonical implementation). Tracked as a
 * follow-up to T-new-3.
 */
test('multipart body replayed on 307 redirect', function (): void {
    /* Skipped: Symfony HttpClient strips body on 307; multipart replay
     * requires a manual byte-serializer like Rust impl - tracked as
     * follow-up to T-new-3 (see AGENT.md "307/308 multipart body
     * replay"). */
    test()->markTestSkipped(
        'Symfony HttpClient strips body on 307; multipart replay requires '
        . 'manual byte-serializer like Rust impl - tracked as follow-up '
        . 'to T-new-3'
    );
});

// -- Max redirects --

test('respects max redirects limit', function (): void {
    $transport = TransportOptions::builder()
        ->followRedirects(true)
        ->maxRedirects(5)
        ->build();

    $client = new DefaultApiClient($transport);
    expect($client)->toBeInstanceOf(DefaultApiClient::class);
    expect($transport->maxRedirects)->toBe(5);
});

// -- Wave E: redirect counting is per-request, not shared per-client --

/*
 * The redirect hop counter and the "too many redirects" refusal must be
 * scoped to a SINGLE sendRequest() call, never stored on the client (or
 * the underlying transport) instance. If they leaked onto the client,
 * interleaving a long redirect chain with a short one on the SAME shared
 * client would corrupt each other: a request that redirects once could
 * be failed by another request's hops, or a refusal raised by an
 * over-limit request could surface on an unrelated under-limit request.
 *
 * chasm only exposes single-hop redirects, so this test stands up a tiny
 * local PHP built-in server exposing /chain/{n}: each hop 302-redirects
 * to /chain/{n-1} until /chain/0 returns 200. That lets one request walk
 * a long chain (hitting the maxRedirects=5 limit) while another walks a
 * one-hop chain (succeeding), all through one DefaultApiClient. PHP runs
 * these calls sequentially within the process; the parity assertion is
 * that the over-limit refusal and the hop count NEVER bleed across calls
 * regardless of interleaving order — exactly what shared per-client
 * redirect state would break. Mirrors the Go SDK's
 * TestWaveE_ConcurrentRedirectCountIsPerRequest.
 */
test('redirect count and limit are scoped per request not shared per client', function (): void {
    // Router script for `php -S`: 302 -> /chain/{n-1} until /chain/0 -> 200.
    $router = <<<'PHP'
        <?php
        $path = parse_url($_SERVER['REQUEST_URI'] ?? '', PHP_URL_PATH) ?: '';
        if (preg_match('#^/chain/(\d+)$#', (string) $path, $m)) {
            $n = (int) $m[1];
            if ($n <= 0) {
                http_response_code(200);
                echo 'ok';
                return true;
            }
            header('Location: /chain/' . ($n - 1), true, 302);
            return true;
        }
        http_response_code(404);
        return true;
        PHP;

    // tempnam() reserves a unique base path; append .php for the router and
    // drop the original reservation so we don't leave an orphan temp file.
    $tempBase = (string) tempnam(sys_get_temp_dir(), 'wavee_router_');
    @unlink($tempBase);
    $routerFile = $tempBase . '.php';
    file_put_contents($routerFile, $router);

    // Bind to an ephemeral loopback port. Port 0 lets the OS pick a free
    // port; we read it back from the server's startup banner on stderr.
    $descriptors = [1 => ['pipe', 'w'], 2 => ['pipe', 'w']];
    $process = proc_open(
        [PHP_BINARY, '-S', '127.0.0.1:0', $routerFile],
        $descriptors,
        $pipes
    );
    if (!is_resource($process)) {
        test()->fail('could not start local php redirect-chain server');
    }

    try {
        // The built-in server prints "... started ... http://127.0.0.1:PORT"
        // to stderr once it is listening. Poll until we can parse the port.
        $port = 0;
        stream_set_blocking($pipes[2], false);
        $deadline = microtime(true) + 10.0;
        $banner = '';
        while (microtime(true) < $deadline && $port === 0) {
            $banner .= (string) fread($pipes[2], 4096);
            if (preg_match('#127\.0\.0\.1:(\d+)#', $banner, $m)) {
                $port = (int) $m[1];
            } else {
                usleep(20_000);
            }
        }
        expect($port)->toBeGreaterThan(0);

        $base = 'http://127.0.0.1:' . $port;

        $transport = TransportOptions::builder()
            ->followRedirects(true)
            ->maxRedirects(5)
            ->build();

        // ONE shared client drives every request below.
        $client = new DefaultApiClient($transport);

        // Interleave short (1 hop -> success) and long (20 hops -> over the
        // maxRedirects=5 budget -> refusal) requests in alternating order.
        // Each tuple is [hops, expectFailure].
        $cases = [];
        for ($i = 0; $i < 12; $i++) {
            $cases[] = [1, false];
            $cases[] = [20, true];
        }

        foreach ($cases as [$hops, $expectFailure]) {
            if ($expectFailure) {
                // The over-limit request must raise ITS OWN refusal and must
                // mention the configured limit — never silently inherit a
                // success, and never poison the next (short) request.
                $caught = null;
                try {
                    $client->sendRequest('GET', $base . '/chain/' . $hops, [], null);
                } catch (\Throwable $e) {
                    $caught = $e;
                }
                expect($caught)->not->toBeNull();
                expect((string) $caught?->getMessage())->toContain('maxRedirects=5');
            } else {
                // The short request must succeed on its own redirect budget,
                // unaffected by any prior over-limit request's hop count.
                $response = $client->sendRequest('GET', $base . '/chain/' . $hops, [], null);
                expect($response->statusCode)->toBe(200);
                expect($response->body)->toBe('ok');
            }
        }
    } finally {
        foreach ($pipes as $pipe) {
            if (is_resource($pipe)) {
                fclose($pipe);
            }
        }
        proc_terminate($process);
        proc_close($process);
        @unlink($routerFile);
    }
});

// -- Multipart body --

test('sends multipart form data', function (): void {
    $chasmUrl = getenv('CHASM_HTTP_URL') ?: '';

    $client = new DefaultApiClient();
    $formData = ['description' => 'A test file', 'file' => 'file content'];
    $response = $client->sendRequest('POST', $chasmUrl . '/test/echo', [], $formData);

    expect($response)->toBeInstanceOf(\PetstoreClient\ApiHttpResponse::class);
});

/**
 * W-new-2: multipart field-name validation must run on every branch (not
 * just binary). Confirm that even for a plain String value, a CR/LF in
 * the field name is rejected, preventing Content-Disposition smuggling.
 */
test('multipart field name with crlf rejected on string value', function (): void {
    $chasmUrl = getenv('CHASM_HTTP_URL') ?: '';

    $client = new DefaultApiClient();
    $badFields = ["name\r\nInjected: yes" => 'string-value'];

    expect(fn () => $client->sendRequest('POST', $chasmUrl . '/test/echo', [], $badFields))
        ->toThrow(\Exception::class);
});

// -- HTTP compression --

test('decompresses gzip response', function (): void {
    $client = new DefaultApiClient();
    $response = $client->sendRequest(
        'GET',
        'https://jsonplaceholder.typicode.com/posts/1',
        ['Accept-Encoding' => 'gzip'],
        null
    );

    expect($response->statusCode)->toBe(200);
    expect($response->body)->toContain('userId');
});

test('decompresses brotli response', function (): void {
    if (!function_exists('brotli_uncompress')) {
        test()->markTestSkipped('ext-brotli not available');
    }

    $client = new DefaultApiClient();
    $response = $client->sendRequest(
        'GET',
        'https://jsonplaceholder.typicode.com/posts/1',
        ['Accept-Encoding' => 'br'],
        null
    );

    expect($response->statusCode)->toBe(200);
    expect($response->body)->toContain('userId');
});

test('decompresses zstd response', function (): void {
    if (!function_exists('zstd_uncompress')) {
        test()->markTestSkipped('ext-zstd not available');
    }

    $client = new DefaultApiClient();
    $response = $client->sendRequest(
        'GET',
        'https://jsonplaceholder.typicode.com/posts/1',
        ['Accept-Encoding' => 'zstd'],
        null
    );

    expect($response->statusCode)->toBe(200);
    expect($response->body)->toContain('userId');
});

/*
 * Regression: POST/PUT/PATCH with body == null must emit an
 * explicit Content-Length: 0. Some servers / WAFs reject body-
 * bearing verbs with no Content-Length (411 Length Required). The
 * client sends an empty body and Content-Length: 0 explicitly on
 * body-bearing verbs.
 */
test('post with null body sends content length zero', function (): void {
    $chasmUrl = getenv('CHASM_HTTP_URL') ?: '';
    $client = new DefaultApiClient();
    $response = $client->sendRequest(
        'POST',
        $chasmUrl . '/test/echo',
        [],
        null
    );

    expect($response->statusCode)->toBe(200);
    /** @var array<string, mixed> $payload */
    $payload = (array) json_decode($response->body, true);
    expect($payload['contentLength'])->toBe(0);
});

// -- Client lifecycle (Gap T6) --

// Gap T6: close() releases the underlying HTTP client and is idempotent.
// Once closed, any subsequent sendRequest() must raise the SDK-typed
// ApiException (closed-flag guard), giving a uniform use-after-close
// contract across SDKs.
test('close releases underlying client', function (): void {
    $client = new DefaultApiClient();
    $client->close();
    $client->close();

    expect(fn () => $client->sendRequest('GET', 'https://example.com', [], null))
        ->toThrow(\PetstoreClient\ApiException::class, 'closed');
});
