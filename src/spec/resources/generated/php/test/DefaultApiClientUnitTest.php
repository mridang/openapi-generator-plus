<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PHPUnit\Framework\TestCase;
use PetstoreClient\DefaultApiClient;
use PetstoreClient\TransportOptionsBuilder;
use Symfony\Component\HttpClient\MockHttpClient;
use Symfony\Component\HttpClient\Response\MockResponse;

class DefaultApiClientUnitTest extends TestCase
{
    public function testSendsGetRequestAndReturnsResponse(): void
    {
        $mockResponse = new MockResponse('{"method":"GET","body":""}', [
            'http_code' => 200,
            'response_headers' => ['X-Test-Header' => 'test-value'],
        ]);
        $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

        $response = $client->sendRequest('GET', 'http://example.com/echo', [], null);

        $this->assertSame(200, $response->statusCode);
        $this->assertStringContainsString('GET', $response->body);
    }

    public function testSendsPostWithJsonBody(): void
    {
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

        $this->assertSame(200, $response->statusCode);
        $this->assertStringContainsString('POST', $response->body);
        $this->assertStringContainsString('key', $response->body);
    }

    public function testReturnsResponseHeaders(): void
    {
        $mockResponse = new MockResponse('ok', [
            'http_code' => 200,
            'response_headers' => ['X-Test-Header' => 'test-value'],
        ]);
        $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

        $response = $client->sendRequest('GET', 'http://example.com/echo', [], null);

        $this->assertArrayHasKey('x-test-header', $response->headers);
        $this->assertSame('test-value', $response->headers['x-test-header']);
    }

    public function testReturnsNon2xxStatusCode(): void
    {
        $mockResponse = new MockResponse('not found', [
            'http_code' => 404,
        ]);
        $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

        $response = $client->sendRequest('GET', 'http://example.com/not-found', [], null);

        $this->assertSame(404, $response->statusCode);
        $this->assertSame('not found', $response->body);
    }

    public function testSendsPutRequest(): void
    {
        $mockResponse = new MockResponse('{"method":"PUT"}', [
            'http_code' => 200,
        ]);
        $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

        $response = $client->sendRequest('PUT', 'http://example.com/echo', [], 'update');

        $this->assertSame(200, $response->statusCode);
        $this->assertStringContainsString('PUT', $response->body);
    }

    public function testSendsDeleteRequest(): void
    {
        $mockResponse = new MockResponse('{"method":"DELETE"}', [
            'http_code' => 200,
        ]);
        $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

        $response = $client->sendRequest('DELETE', 'http://example.com/echo', [], null);

        $this->assertSame(200, $response->statusCode);
        $this->assertStringContainsString('DELETE', $response->body);
    }

    public function testReturnsJsonBodyForVendorJsonContentType(): void
    {
        $mockResponse = new MockResponse('{"format":"vendor"}', [
            'http_code' => 200,
            'response_headers' => ['Content-Type' => 'application/vnd.api+json'],
        ]);
        $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

        $response = $client->sendRequest('GET', 'http://example.com/vendor-json', [], null);

        $this->assertSame(200, $response->statusCode);
        $this->assertStringContainsString('vendor', $response->body);
    }

    public function testJoinsMultiValueResponseHeaders(): void
    {
        $mockResponse = new MockResponse('ok', [
            'http_code' => 200,
            'response_headers' => ['X-Custom-Value' => ['val1', 'val2']],
        ]);
        $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

        $response = $client->sendRequest('GET', 'http://example.com/multi-header', [], null);

        $this->assertSame(200, $response->statusCode);
        $this->assertArrayHasKey('x-custom-value', $response->headers);
        $this->assertStringContainsString('val1', $response->headers['x-custom-value']);
        $this->assertStringContainsString('val2', $response->headers['x-custom-value']);
    }

    public function testInjectsCustomUserAgent(): void
    {
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

        $this->assertSame('MyApp/1.0', $capturedHeaders['User-Agent'] ?? null);
    }

    public function testInjectsDefaultUserAgentWhenNotExplicitlySet(): void
    {
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

        $this->assertNotEmpty($capturedHeaders['User-Agent'] ?? null);
    }

    public function testInjectsRequestId(): void
    {
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
        $this->assertNotNull($requestId);
        $this->assertMatchesRegularExpression(
            '/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/',
            $requestId
        );
    }

    public function testDoesNotInjectRequestIdWhenDisabled(): void
    {
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

        $this->assertArrayNotHasKey('X-Request-ID', $capturedHeaders);
    }

    public function testDoesNotOverrideCallerRequestId(): void
    {
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

        $this->assertSame('caller-id', $capturedHeaders['X-Request-ID'] ?? null);
    }

    public function testGeneratesUniqueRequestIds(): void
    {
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

        $this->assertCount(2, $capturedIds);
        $this->assertNotSame($capturedIds[0], $capturedIds[1]);
    }

    public function testIncludesTransportDefaultHeaders(): void
    {
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

        $this->assertSame('custom-value', $capturedHeaders['X-Custom'] ?? null);
    }

    public function testCallerHeadersOverrideDefaults(): void
    {
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

        $this->assertSame('application/json', $capturedHeaders['Accept'] ?? null);
    }

    public function testDecodesIso88591BodyToUtf8WhenCharsetDeclared(): void
    {
        $body = "\xE9"; // ISO-8859-1 'é'
        $mockResponse = new MockResponse($body, [
            'http_code' => 200,
            'response_headers' => ['Content-Type' => 'text/plain; charset=ISO-8859-1'],
        ]);
        $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

        $response = $client->sendRequest('GET', 'http://example.com/iso', [], null);

        $this->assertSame(200, $response->statusCode);
        $this->assertSame("\xC3\xA9", $response->body); // UTF-8 'é'
    }

    public function testTreatsAbsentCharsetAsUtf8(): void
    {
        $body = "héllo"; // already UTF-8
        $mockResponse = new MockResponse($body, [
            'http_code' => 200,
            'response_headers' => ['Content-Type' => 'text/plain'],
        ]);
        $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

        $response = $client->sendRequest('GET', 'http://example.com/utf8', [], null);

        $this->assertSame($body, $response->body);
    }

    public function testFallsBackToUtf8OnUnknownCharsetWithoutThrowing(): void
    {
        $body = "hello";
        $mockResponse = new MockResponse($body, [
            'http_code' => 200,
            'response_headers' => ['Content-Type' => 'text/plain; charset=not-a-real-charset'],
        ]);
        $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

        $response = $client->sendRequest('GET', 'http://example.com/unknown', [], null);

        $this->assertSame(200, $response->statusCode);
        $this->assertSame($body, $response->body);
    }

    public function testMultipartPngFileGetsImagePngContentType(): void
    {
        $tmp = tempnam(sys_get_temp_dir(), 'mp_');
        $this->assertIsString($tmp);
        $pngPath = $tmp . '.png';
        rename($tmp, $pngPath);
        file_put_contents($pngPath, "\x89PNG\r\n\x1A\n");

        $capturedBody = '';
        $mockClient = new MockHttpClient(
            function (string $method, string $url, array $options) use (&$capturedBody): MockResponse {
                $capturedBody = $this->collectRequestBody($options['body'] ?? '');
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

            $this->assertStringContainsString('Content-Type: image/png', $capturedBody);
        } finally {
            @unlink($pngPath);
        }
    }

    public function testMultipartResourceFallsBackToOctetStream(): void
    {
        $stream = fopen('php://temp', 'w+');
        $this->assertIsResource($stream);
        fwrite($stream, "\x00\xFF\x42 raw bytes");
        rewind($stream);

        $capturedBody = '';
        $mockClient = new MockHttpClient(
            function (string $method, string $url, array $options) use (&$capturedBody): MockResponse {
                $capturedBody = $this->collectRequestBody($options['body'] ?? '');
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

        $this->assertStringContainsString('Content-Type: application/octet-stream', $capturedBody);
    }

    public function testDecodesIso88591ErrorBodyToUtf8(): void
    {
        $body = "\xE9rreur"; // ISO-8859-1 'érreur'
        $mockResponse = new MockResponse($body, [
            'http_code' => 500,
            'response_headers' => ['Content-Type' => 'text/plain; charset=ISO-8859-1'],
        ]);
        $client = new DefaultApiClient(null, new MockHttpClient($mockResponse));

        $response = $client->sendRequest('GET', 'http://example.com/err', [], null);

        $this->assertSame(500, $response->statusCode);
        $this->assertSame("\xC3\xA9rreur", $response->body);
    }

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
    private function collectRequestBody(mixed $body): string
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

    // -- Proxy auth (gap #29) --

    public function testProxyWithBasicAuthIsAcceptedByBuilder(): void
    {
        // Verify the transport accepts a proxy URL embedding user:pass and exposes it.
        // The actual proxy-auth header is constructed by Symfony's underlying CurlHttpClient.
        $opts = (new TransportOptionsBuilder())
            ->proxy('http://user:secret@proxy.example.com:8080')
            ->build();

        $this->assertSame('http://user:secret@proxy.example.com:8080', $opts->proxy);
    }

    public function testProxyAuthSquidEndToEnd(): void
    {
        // Container-backed Squid+auth proxy is not provisioned in this suite.
        $this->markTestSkipped('Skipped: requires a containerized Squid proxy with basic-auth credentials.');
    }
}
