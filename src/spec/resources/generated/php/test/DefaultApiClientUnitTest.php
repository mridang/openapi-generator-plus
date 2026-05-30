<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PetstoreClient\DefaultApiClient;
use PetstoreClient\TransportOptionsBuilder;
use Symfony\Component\HttpClient\MockHttpClient;
use Symfony\Component\HttpClient\Response\MockResponse;

/**
 * Collect a Symfony HttpClient request body into a single string.
 *
 * Symfony normalises iterable bodies into a `Closure(int): string` that
 * returns chunks and signals end-of-body with an empty string. Strings
 * and iterables are passed through as-is so each test can inspect the
 * raw payload regardless of how the client supplied it.
 *
 * @param mixed $body Body value from the captured request options
 */
function collectDefaultApiClientRequestBody(mixed $body): string
{
    if (is_string($body)) {
        return $body;
    }
    if ($body instanceof \Closure) {
        $buffer = '';
        while (true) {
            $chunk = $body(16384);
            if (!is_string($chunk) || $chunk === '') {
                break;
            }
            $buffer .= $chunk;
        }
        return $buffer;
    }
    if (is_iterable($body)) {
        $buffer = '';
        foreach ($body as $chunk) {
            $buffer .= (string) $chunk;
        }
        return $buffer;
    }
    return '';
}

test('sends get request and returns response', function (): void {
    $mockResponse = new MockResponse('{"method":"GET","body":""}', [
        'http_code' => 200,
        'response_headers' => ['X-Test-Header' => 'test-value'],
    ]);
    $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

    $response = $client->sendRequest('GET', 'http://example.com/echo', [], null);

    expect($response->statusCode)->toBe(200);
    expect($response->body)->toContain('GET');
});

test('sends post with json body', function (): void {
    $mockResponse = new MockResponse('{"method":"POST","body":"key"}', [
        'http_code' => 200,
    ]);
    $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

    $response = $client->sendRequest(
        'POST',
        'http://example.com/echo',
        ['Content-Type' => 'application/json'],
        '{"key":"value"}'
    );

    expect($response->statusCode)->toBe(200);
    expect($response->body)->toContain('POST');
    expect($response->body)->toContain('key');
});

test('returns response headers', function (): void {
    $mockResponse = new MockResponse('ok', [
        'http_code' => 200,
        'response_headers' => ['X-Test-Header' => 'test-value'],
    ]);
    $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

    $response = $client->sendRequest('GET', 'http://example.com/echo', [], null);

    expect($response->headers)->toHaveKey('x-test-header');
    expect($response->headers['x-test-header'])->toBe('test-value');
});

test('returns non 2xx status code', function (): void {
    $mockResponse = new MockResponse('not found', [
        'http_code' => 404,
    ]);
    $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

    $response = $client->sendRequest('GET', 'http://example.com/not-found', [], null);

    expect($response->statusCode)->toBe(404);
    expect($response->body)->toBe('not found');
});

test('sends put request', function (): void {
    $mockResponse = new MockResponse('{"method":"PUT"}', [
        'http_code' => 200,
    ]);
    $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

    $response = $client->sendRequest('PUT', 'http://example.com/echo', [], 'update');

    expect($response->statusCode)->toBe(200);
    expect($response->body)->toContain('PUT');
});

test('sends delete request', function (): void {
    $mockResponse = new MockResponse('{"method":"DELETE"}', [
        'http_code' => 200,
    ]);
    $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

    $response = $client->sendRequest('DELETE', 'http://example.com/echo', [], null);

    expect($response->statusCode)->toBe(200);
    expect($response->body)->toContain('DELETE');
});

test('returns json body for vendor json content type', function (): void {
    $mockResponse = new MockResponse('{"format":"vendor"}', [
        'http_code' => 200,
        'response_headers' => ['Content-Type' => 'application/vnd.api+json'],
    ]);
    $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

    $response = $client->sendRequest('GET', 'http://example.com/vendor-json', [], null);

    expect($response->statusCode)->toBe(200);
    expect($response->body)->toContain('vendor');
});

test('joins multi value response headers', function (): void {
    $mockResponse = new MockResponse('ok', [
        'http_code' => 200,
        'response_headers' => ['X-Custom-Value' => ['val1', 'val2']],
    ]);
    $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

    $response = $client->sendRequest('GET', 'http://example.com/multi-header', [], null);

    expect($response->statusCode)->toBe(200);
    expect($response->headers)->toHaveKey('x-custom-value');
    expect($response->headers['x-custom-value'])->toContain('val1');
    expect($response->headers['x-custom-value'])->toContain('val2');
});

test('injects custom user agent', function (): void {
    $capturedHeaders = [];
    $mockClient = new MockHttpClient(
        function (string $method, string $url, array $options) use (&$capturedHeaders): MockResponse {
            $capturedHeaders = [];
            foreach ($options['normalized_headers'] ?? [] as $values) {
                [$name, $value] = explode(': ', $values[0], 2);
                $capturedHeaders[$name] = $value;
            }
            return new MockResponse('{}', ['http_code' => 200]);
        }
    );

    $transport = (new TransportOptionsBuilder())
        ->userAgent('MyApp/1.0')
        ->build();
    $client = new DefaultApiClient($transport, $mockClient);
    $client->sendRequest('GET', 'http://example.com/test', [], null);

    expect($capturedHeaders['User-Agent'] ?? null)->toBe('MyApp/1.0');
});

test('injects default user agent when not explicitly set', function (): void {
    $capturedHeaders = [];
    $mockClient = new MockHttpClient(
        function (string $method, string $url, array $options) use (&$capturedHeaders): MockResponse {
            $capturedHeaders = [];
            foreach ($options['normalized_headers'] ?? [] as $values) {
                [$name, $value] = explode(': ', $values[0], 2);
                $capturedHeaders[$name] = $value;
            }
            return new MockResponse('{}', ['http_code' => 200]);
        }
    );

    $client = new DefaultApiClient(null, $mockClient);
    $client->sendRequest('GET', 'http://example.com/test', [], null);

    expect($capturedHeaders['User-Agent'] ?? null)->not->toBeEmpty();
});

test('injects request id', function (): void {
    $capturedHeaders = [];
    $mockClient = new MockHttpClient(
        function (string $method, string $url, array $options) use (&$capturedHeaders): MockResponse {
            $capturedHeaders = [];
            foreach ($options['normalized_headers'] ?? [] as $values) {
                [$name, $value] = explode(': ', $values[0], 2);
                $capturedHeaders[$name] = $value;
            }
            return new MockResponse('{}', ['http_code' => 200]);
        }
    );

    $transport = (new TransportOptionsBuilder())
        ->injectRequestId(true)
        ->build();
    $client = new DefaultApiClient($transport, $mockClient);
    $client->sendRequest('GET', 'http://example.com/test', [], null);

    $requestId = $capturedHeaders['X-Request-ID'] ?? null;
    expect($requestId)->not->toBeNull();
    expect($requestId)->toMatch('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/');
});

test('does not inject request id when disabled', function (): void {
    $capturedHeaders = [];
    $mockClient = new MockHttpClient(
        function (string $method, string $url, array $options) use (&$capturedHeaders): MockResponse {
            $capturedHeaders = [];
            foreach ($options['normalized_headers'] ?? [] as $values) {
                [$name, $value] = explode(': ', $values[0], 2);
                $capturedHeaders[$name] = $value;
            }
            return new MockResponse('{}', ['http_code' => 200]);
        }
    );

    $transport = (new TransportOptionsBuilder())
        ->injectRequestId(false)
        ->build();
    $client = new DefaultApiClient($transport, $mockClient);
    $client->sendRequest('GET', 'http://example.com/test', [], null);

    expect($capturedHeaders)->not->toHaveKey('X-Request-ID');
});

test('does not override caller request id', function (): void {
    $capturedHeaders = [];
    $mockClient = new MockHttpClient(
        function (string $method, string $url, array $options) use (&$capturedHeaders): MockResponse {
            $capturedHeaders = [];
            foreach ($options['normalized_headers'] ?? [] as $values) {
                [$name, $value] = explode(': ', $values[0], 2);
                $capturedHeaders[$name] = $value;
            }
            return new MockResponse('{}', ['http_code' => 200]);
        }
    );

    $transport = (new TransportOptionsBuilder())
        ->injectRequestId(true)
        ->build();
    $client = new DefaultApiClient($transport, $mockClient);
    $client->sendRequest('GET', 'http://example.com/test', ['X-Request-ID' => 'caller-id'], null);

    expect($capturedHeaders['X-Request-ID'] ?? null)->toBe('caller-id');
});

test('generates unique request ids', function (): void {
    $capturedIds = [];

    $mockClient1 = new MockHttpClient(
        function (string $method, string $url, array $options) use (&$capturedIds): MockResponse {
            $requestHeaders = [];
            foreach ($options['normalized_headers'] ?? [] as $values) {
                [$name, $value] = explode(': ', $values[0], 2);
                $requestHeaders[$name] = $value;
            }
            $capturedIds[] = $requestHeaders['X-Request-ID'] ?? null;
            return new MockResponse('{}', ['http_code' => 200]);
        }
    );
    $mockClient2 = new MockHttpClient(
        function (string $method, string $url, array $options) use (&$capturedIds): MockResponse {
            $requestHeaders = [];
            foreach ($options['normalized_headers'] ?? [] as $values) {
                [$name, $value] = explode(': ', $values[0], 2);
                $requestHeaders[$name] = $value;
            }
            $capturedIds[] = $requestHeaders['X-Request-ID'] ?? null;
            return new MockResponse('{}', ['http_code' => 200]);
        }
    );

    $transport = (new TransportOptionsBuilder())
        ->injectRequestId(true)
        ->build();

    $client1 = new DefaultApiClient($transport, $mockClient1);
    $client1->sendRequest('GET', 'http://example.com/test', [], null);

    $client2 = new DefaultApiClient($transport, $mockClient2);
    $client2->sendRequest('GET', 'http://example.com/test', [], null);

    expect($capturedIds)->toHaveCount(2);
    expect($capturedIds[1])->not->toBe($capturedIds[0]);
});

test('unit includes transport default headers', function (): void {
    $capturedHeaders = [];
    $mockClient = new MockHttpClient(
        function (string $method, string $url, array $options) use (&$capturedHeaders): MockResponse {
            $capturedHeaders = [];
            foreach ($options['normalized_headers'] ?? [] as $values) {
                [$name, $value] = explode(': ', $values[0], 2);
                $capturedHeaders[$name] = $value;
            }
            return new MockResponse('{}', ['http_code' => 200]);
        }
    );

    $transport = (new TransportOptionsBuilder())
        ->defaultHeader('X-Custom', 'custom-value')
        ->build();
    $client = new DefaultApiClient($transport, $mockClient);
    $client->sendRequest('GET', 'http://example.com/test', [], null);

    expect($capturedHeaders['X-Custom'] ?? null)->toBe('custom-value');
});

test('caller headers override defaults', function (): void {
    $capturedHeaders = [];
    $mockClient = new MockHttpClient(
        function (string $method, string $url, array $options) use (&$capturedHeaders): MockResponse {
            $capturedHeaders = [];
            foreach ($options['normalized_headers'] ?? [] as $values) {
                [$name, $value] = explode(': ', $values[0], 2);
                $capturedHeaders[$name] = $value;
            }
            return new MockResponse('{}', ['http_code' => 200]);
        }
    );

    $transport = (new TransportOptionsBuilder())
        ->defaultHeader('Accept', 'text/plain')
        ->build();
    $client = new DefaultApiClient($transport, $mockClient);
    $client->sendRequest('GET', 'http://example.com/test', ['Accept' => 'application/json'], null);

    expect($capturedHeaders['Accept'] ?? null)->toBe('application/json');
});

test('decodes iso 8859 1 body to utf 8 when charset declared', function (): void {
    $body = "\xE9"; // ISO-8859-1 'é'
    $mockResponse = new MockResponse($body, [
        'http_code' => 200,
        'response_headers' => ['Content-Type' => 'text/plain; charset=ISO-8859-1'],
    ]);
    $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

    $response = $client->sendRequest('GET', 'http://example.com/iso', [], null);

    expect($response->statusCode)->toBe(200);
    expect($response->body)->toBe("\xC3\xA9"); // UTF-8 'é'
});

test('treats absent charset as utf 8', function (): void {
    $body = "héllo"; // already UTF-8
    $mockResponse = new MockResponse($body, [
        'http_code' => 200,
        'response_headers' => ['Content-Type' => 'text/plain'],
    ]);
    $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

    $response = $client->sendRequest('GET', 'http://example.com/utf8', [], null);

    expect($response->body)->toBe($body);
});

test('falls back to utf 8 on unknown charset without throwing', function (): void {
    $body = "hello";
    $mockResponse = new MockResponse($body, [
        'http_code' => 200,
        'response_headers' => ['Content-Type' => 'text/plain; charset=not-a-real-charset'],
    ]);
    $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

    $response = $client->sendRequest('GET', 'http://example.com/unknown', [], null);

    expect($response->statusCode)->toBe(200);
    expect($response->body)->toBe($body);
});

test('multipart png file gets image png content type', function (): void {
    $tmp = tempnam(sys_get_temp_dir(), 'mp_');
    expect($tmp)->toBeString();
    $pngPath = $tmp . '.png';
    rename($tmp, $pngPath);
    file_put_contents($pngPath, "\x89PNG\r\n\x1A\n");

    $capturedBody = '';
    $mockClient = new MockHttpClient(
        function (string $method, string $url, array $options) use (&$capturedBody): MockResponse {
            $capturedBody = collectDefaultApiClientRequestBody($options['body'] ?? '');
            return new MockResponse('{}', ['http_code' => 200]);
        }
    );

    try {
        $client = new DefaultApiClient(null, $mockClient);
        $client->sendRequest(
            'POST',
            'http://example.com/upload',
            [],
            ['file' => new \SplFileObject($pngPath)]
        );

        expect($capturedBody)->toContain('Content-Type: image/png');
    } finally {
        @unlink($pngPath);
    }
});

test('multipart resource falls back to octet stream', function (): void {
    $stream = fopen('php://temp', 'w+');
    expect($stream)->toBeResource();
    fwrite($stream, "\x00\xFF\x42 raw bytes");
    rewind($stream);

    $capturedBody = '';
    $mockClient = new MockHttpClient(
        function (string $method, string $url, array $options) use (&$capturedBody): MockResponse {
            $capturedBody = collectDefaultApiClientRequestBody($options['body'] ?? '');
            return new MockResponse('{}', ['http_code' => 200]);
        }
    );

    $client = new DefaultApiClient(null, $mockClient);
    $client->sendRequest(
        'POST',
        'http://example.com/upload',
        [],
        ['file' => $stream]
    );

    expect($capturedBody)->toContain('Content-Type: application/octet-stream');
});

test('decodes iso 8859 1 error body to utf 8', function (): void {
    $body = "\xE9rreur"; // ISO-8859-1 'érreur'
    $mockResponse = new MockResponse($body, [
        'http_code' => 500,
        'response_headers' => ['Content-Type' => 'text/plain; charset=ISO-8859-1'],
    ]);
    $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

    $response = $client->sendRequest('GET', 'http://example.com/err', [], null);

    expect($response->statusCode)->toBe(500);
    expect($response->body)->toBe("\xC3\xA9rreur");
});

// -- Proxy auth (gap #29) --

test('proxy with basic auth is accepted by builder', function (): void {
    // Verify the transport accepts a proxy URL embedding user:pass and exposes it.
    // The actual proxy-auth header is constructed by Symfony's underlying CurlHttpClient.
    $opts = (new TransportOptionsBuilder())
        ->proxy('http://user:secret@proxy.example.com:8080')
        ->build();

    expect($opts->proxy)->toBe('http://user:secret@proxy.example.com:8080');
});

test('proxy auth squid end to end', function (): void {
    // Container-backed Squid+auth proxy is not provisioned in this suite.
    test()->markTestSkipped('Skipped: requires a containerized Squid proxy with basic-auth credentials.');
});

// -- Gap BI: RFC 5987 filename* for non-ASCII multipart filenames --

test('multipart filename non ascii emits rfc 5987', function (): void {
    $directive = DefaultApiClient::buildFilenameDirective('日本.pdf');
    expect($directive)->toContain("filename*=UTF-8''");
    expect($directive)->toContain('%E6%97%A5%E6%9C%AC');
    expect($directive)->toStartWith('filename="');
});

test('multipart filename ascii only omits filename star', function (): void {
    $directive = DefaultApiClient::buildFilenameDirective('pet.png');
    expect($directive)->toBe('filename="pet.png"');
    expect($directive)->not->toContain('filename*=');
});

test('multipart filename crlf rejected', function (): void {
    foreach (["a\rb.pdf", "a\nb.pdf", "a\r\nb.pdf", "a\x00b.pdf"] as $bad) {
        try {
            DefaultApiClient::validateMultipartFilename($bad);
            test()->fail("expected InvalidArgumentException for: " . bin2hex($bad));
        } catch (\InvalidArgumentException) {
            // expected
        }
    }
    /* ASCII filename should not throw; record one assertion so PHPUnit
     * doesn't flag the test as risky. Avoids PHPStan's
     * method.alreadyNarrowedType complaint on assertTrue(true). */
    DefaultApiClient::validateMultipartFilename('pet.png');
    expect(true)->toBeTrue();
});
