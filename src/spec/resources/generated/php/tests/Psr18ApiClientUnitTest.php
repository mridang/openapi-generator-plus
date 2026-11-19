<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PetstoreClient\Auth\ApiKeyLocation;
use PetstoreClient\Errors\ApiException;
use PetstoreClient\Psr18ApiClient;
use PetstoreClient\TransportOptions;
use PetstoreClient\TransportOptionsBuilder;
use Nyholm\Psr7\Factory\Psr17Factory;
use Psr\Http\Client\ClientExceptionInterface;
use Psr\Http\Client\ClientInterface;
use Psr\Http\Message\RequestInterface;
use Psr\Http\Message\ResponseInterface;
use Symfony\Component\Serializer\Attribute\SerializedName;

/**
 * Build a PSR-7 response with the given status, body, and headers.
 *
 * @param array<string, string> $headers
 */
function psr18Response(int $status, string $body = '', array $headers = []): ResponseInterface
{
    $factory = new Psr17Factory();
    $response = $factory->createResponse($status)->withBody($factory->createStream($body));
    foreach ($headers as $name => $value) {
        $response = $response->withHeader($name, $value);
    }

    return $response;
}

function newPsr18Client(StubPsr18Client $stub, ?TransportOptions $transport = null): Psr18ApiClient
{
    $factory = new Psr17Factory();

    return new Psr18ApiClient($stub, $factory, $factory, $transport);
}

test('bhv langphp 014 psr18 sends get request and returns response', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{"method":"GET"}'));
    $client = newPsr18Client($stub);

    $response = $client->sendRequest('GET', 'http://example.com/echo', [], null);

    expect($response->statusCode)->toBe(200);
    expect($response->body)->toContain('GET');
    expect($stub->requests[0]->getMethod())->toBe('GET');
});

test('bhv langphp 015 psr18 sends post with json body', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, 'ok'));
    $client = newPsr18Client($stub);

    $response = $client->sendRequest(
        'POST',
        'http://example.com/echo',
        ['Content-Type' => 'application/json'],
        '{"key":"value"}'
    );

    expect($response->statusCode)->toBe(200);
    expect((string) $stub->requests[0]->getBody())->toBe('{"key":"value"}');
    expect($stub->requests[0]->getHeaderLine('Content-Type'))->toBe('application/json');
});

test('bhv langphp 016 psr18 returns response headers lowercased', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, 'ok', ['X-Test-Header' => 'test-value']));
    $client = newPsr18Client($stub);

    $response = $client->sendRequest('GET', 'http://example.com/echo', [], null);

    expect($response->headers)->toHaveKey('x-test-header');
    expect($response->headers['x-test-header'])->toBe('test-value');
});

test('bhv misc 274 psr18 returns non 2xx status code', function (): void {
    $stub = new StubPsr18Client(psr18Response(404, 'not found'));
    $client = newPsr18Client($stub);

    $response = $client->sendRequest('GET', 'http://example.com/not-found', [], null);

    expect($response->statusCode)->toBe(404);
    expect($response->body)->toBe('not found');
});

test('bhv transport 090 psr18 joins multi value response headers', function (): void {
    $factory = new Psr17Factory();
    $response = $factory->createResponse(200)
        ->withBody($factory->createStream('ok'))
        ->withHeader('X-Custom-Value', ['val1', 'val2']);
    $stub = new StubPsr18Client($response);
    $client = newPsr18Client($stub);

    $result = $client->sendRequest('GET', 'http://example.com/multi-header', [], null);

    expect($result->headers)->toHaveKey('x-custom-value');
    expect($result->headers['x-custom-value'])->toContain('val1');
    expect($result->headers['x-custom-value'])->toContain('val2');
});

test('bhv transport 052 psr18 injects custom user agent', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $transport = (new TransportOptionsBuilder())->userAgent('MyApp/1.0')->build();
    $client = newPsr18Client($stub, $transport);

    $client->sendRequest('GET', 'http://example.com/test', [], null);

    expect($stub->requests[0]->getHeaderLine('User-Agent'))->toBe('MyApp/1.0');
});

test('bhv misc 273 psr18 injects request id matching uuid v4', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $transport = (new TransportOptionsBuilder())->injectRequestId(true)->build();
    $client = newPsr18Client($stub, $transport);

    $client->sendRequest('GET', 'http://example.com/test', [], null);

    expect($stub->requests[0]->getHeaderLine('X-Request-ID'))
        ->toMatch('/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/');
});

test('bhv langphp 017 psr18 does not inject request id when disabled', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $transport = (new TransportOptionsBuilder())->injectRequestId(false)->build();
    $client = newPsr18Client($stub, $transport);

    $client->sendRequest('GET', 'http://example.com/test', [], null);

    expect($stub->requests[0]->hasHeader('X-Request-ID'))->toBeFalse();
});

test('bhv misc 266 psr18 does not override caller request id', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $transport = (new TransportOptionsBuilder())->injectRequestId(true)->build();
    $client = newPsr18Client($stub, $transport);

    $client->sendRequest('GET', 'http://example.com/test', ['X-Request-ID' => 'caller-id'], null);

    expect($stub->requests[0]->getHeaderLine('X-Request-ID'))->toBe('caller-id');
});

test('bhv transport 040 psr18 includes transport default headers', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $transport = (new TransportOptionsBuilder())->defaultHeader('X-Custom', 'custom-value')->build();
    $client = newPsr18Client($stub, $transport);

    $client->sendRequest('GET', 'http://example.com/test', [], null);

    expect($stub->requests[0]->getHeaderLine('X-Custom'))->toBe('custom-value');
});

test('bhv transport 047 psr18 caller headers override defaults', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $transport = (new TransportOptionsBuilder())->defaultHeader('Accept', 'text/plain')->build();
    $client = newPsr18Client($stub, $transport);

    $client->sendRequest('GET', 'http://example.com/test', ['Accept' => 'application/json'], null);

    expect($stub->requests[0]->getHeaderLine('Accept'))->toBe('application/json');
});

test('bhv serializationwire 034 psr18 decodes iso 8859 1 body to utf 8 when charset declared', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(200, "\xE9", ['Content-Type' => 'text/plain; charset=ISO-8859-1'])
    );
    $client = newPsr18Client($stub);

    $response = $client->sendRequest('GET', 'http://example.com/iso', [], null);

    expect($response->body)->toBe("\xC3\xA9"); // UTF-8 'é'
});

test('bhv misc 224 transport default headers are not sent to another origin after a redirect', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(302, '', ['Location' => 'https://other.example.com/final']),
        psr18Response(200, 'ok')
    );
    $transport = (new TransportOptionsBuilder())->defaultHeader('X-Api-Key', 'secret')->followRedirects(true)->build();
    $client = newPsr18Client($stub, $transport);

    $client->sendRequest('GET', 'https://api.example.com/start', [], null);

    expect($stub->requests[1]->hasHeader('X-Api-Key'))->toBeFalse();
});

test('bhv redirects 013 psr18 cross origin redirect strips authorization and cookie', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(302, '', ['Location' => 'https://other.example.com/final']),
        psr18Response(200, 'ok')
    );
    $transport = TransportOptions::builder()->followRedirects(true)->build();
    $client = newPsr18Client($stub, $transport);

    $client->sendRequest('GET', 'https://api.example.com/start', [
        'Authorization' => 'Bearer secret',
        'Cookie' => 'session=xyz',
        'X-Trace' => 'keep',
    ], null);

    expect($stub->requests[1]->hasHeader('Authorization'))->toBeFalse();
    expect($stub->requests[1]->hasHeader('Cookie'))->toBeFalse();
    expect($stub->requests[1]->hasHeader('X-Trace'))->toBeTrue();
});

test('bhv redirects 021 psr18 no redirect arg surfaces 302 to caller', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(302, '', ['Location' => 'https://attacker.example.com/token'])
    );
    $transport = TransportOptions::builder()->followRedirects(true)->build();
    $client = newPsr18Client($stub, $transport);

    $response = $client->sendRequest('POST', 'https://auth.example.com/token', [], 'grant_type=x', noRedirect: true);

    expect($response->statusCode)->toBe(302);
});

test('bhv redirects 017 psr18 follows redirect when not suppressed', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(302, '', ['Location' => 'https://auth.example.com/final']),
        psr18Response(200, 'ok')
    );
    $transport = TransportOptions::builder()->followRedirects(true)->build();
    $client = newPsr18Client($stub, $transport);

    $response = $client->sendRequest('GET', 'https://auth.example.com/start', [], null);

    expect($response->statusCode)->toBe(200);
});

test('bhv langphp 018 psr18 resolves a relative Location per RFC 3986', function (string $location, string $expected): void {
    $stub = new StubPsr18Client(
        psr18Response(302, '', ['Location' => $location]),
        psr18Response(200, 'ok')
    );
    $transport = TransportOptions::builder()->followRedirects(true)->build();
    $client = newPsr18Client($stub, $transport);

    $client->sendRequest('GET', 'https://h/v1/items/1?x=1', [], null);

    expect((string) $stub->requests[1]->getUri())->toBe($expected);
})->with([
    'query only' => ['?page=2', 'https://h/v1/items/1?page=2'],
    'parent segment' => ['../owners/9', 'https://h/v1/owners/9'],
    'current segment' => ['./a', 'https://h/v1/items/a'],
    'sibling' => ['b', 'https://h/v1/items/b'],
    'absolute path' => ['/z', 'https://h/z'],
]);

test('bhv retries 016 psr18 https to http downgrade refuses body replay on 307', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(307, '', ['Location' => 'http://insecure.example.com/sink'])
    );
    $transport = TransportOptions::builder()->followRedirects(true)->build();
    $client = newPsr18Client($stub, $transport);

    expect(fn (): mixed => $client->sendRequest('POST', 'https://api.example.com/secret', [], 'sensitive=payload'))
        ->toThrow(function (\Exception $e): void {
            expect($e::class)->toBe(ApiException::class);
            expect($e->getCode())->toBe(307);
        });
});

test('bhv redirects 029 psr18 redirect method and body follow RFC 9110', function (
    int $status,
    string $method,
    string $expectedMethod,
    string $expectedBody
): void {
    $stub = new StubPsr18Client(
        psr18Response($status, '', ['Location' => 'https://api.example.com/next']),
        psr18Response(200, 'ok')
    );
    $transport = TransportOptions::builder()->followRedirects(true)->build();
    $client = newPsr18Client($stub, $transport);

    $client->sendRequest($method, 'https://api.example.com/start', [], 'payload');

    expect($stub->requests[1]->getMethod())->toBe($expectedMethod);
    expect((string) $stub->requests[1]->getBody())->toBe($expectedBody);
})->with([
    '301 post becomes get' => [301, 'POST', 'GET', ''],
    '302 post becomes get' => [302, 'POST', 'GET', ''],
    '301 put keeps method and body' => [301, 'PUT', 'PUT', 'payload'],
    '302 patch keeps method and body' => [302, 'PATCH', 'PATCH', 'payload'],
    '302 delete keeps method and body' => [302, 'DELETE', 'DELETE', 'payload'],
    '303 post becomes get' => [303, 'POST', 'GET', ''],
    '303 put becomes get' => [303, 'PUT', 'GET', ''],
    '303 delete becomes get' => [303, 'DELETE', 'GET', ''],
    '307 post keeps method and body' => [307, 'POST', 'POST', 'payload'],
    '308 put keeps method and body' => [308, 'PUT', 'PUT', 'payload'],
]);

test('bhv misc 126 psr18 303 keeps head as head', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(303, '', ['Location' => 'https://api.example.com/next']),
        psr18Response(200, 'ok')
    );
    $transport = TransportOptions::builder()->followRedirects(true)->build();
    $client = newPsr18Client($stub, $transport);

    $client->sendRequest('HEAD', 'https://api.example.com/start', [], null);

    expect($stub->requests[1]->getMethod())->toBe('HEAD');
});

test('bhv langphp 020 psr18 301 put https to http downgrade refuses body replay', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(301, '', ['Location' => 'http://insecure.example.com/sink'])
    );
    $transport = TransportOptions::builder()->followRedirects(true)->build();
    $client = newPsr18Client($stub, $transport);

    expect(fn (): mixed => $client->sendRequest('PUT', 'https://api.example.com/secret', [], 'k=v'))
        ->toThrow(ApiException::class);
});

test('bhv redirects 026 psr18 exceeding max redirects throws', function (): void {
    $loop = [];
    for ($i = 0; $i < 10; $i++) {
        $loop[] = psr18Response(302, '', ['Location' => 'https://api.example.com/next/' . $i]);
    }
    $transport = TransportOptions::builder()->followRedirects(true)->maxRedirects(3)->build();
    $client = newPsr18Client(new StubPsr18Client(...$loop), $transport);

    expect(fn (): mixed => $client->sendRequest('GET', 'https://api.example.com/start', [], null))
        ->toThrow(function (\Exception $e): void {
            expect($e::class)->toBe(ApiException::class);
            expect($e->getCode())->toBe(302);
        });
});

test('bhv redirects 054 psr18 timeout is one budget across all redirect hops', function (): void {
    /* Every hop answers within the 400 ms timeout, but three of them together
     * outlast it, so the call must time out instead of restarting the clock. */
    $slow = new class implements ClientInterface {
        public int $sent = 0;

        public function sendRequest(RequestInterface $request): ResponseInterface
        {
            $this->sent++;
            usleep(250000);

            return psr18Response(302, '', ['Location' => 'https://api.example.com/next/' . $this->sent]);
        }
    };
    $factory = new Psr17Factory();
    $transport = TransportOptions::builder()->timeout(400)->build();
    $client = new Psr18ApiClient($slow, $factory, $factory, $transport);

    expect(fn (): mixed => $client->sendRequest('GET', 'https://api.example.com/start', [], null))
        ->toThrow(\PetstoreClient\Errors\NetworkTimeoutException::class);
    expect($slow->sent)->toBe(2);
});

test('bhv retries 019 psr18 timeout budget starts afresh for every call', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{}'), psr18Response(200, '{}'));
    $client = newPsr18Client($stub, TransportOptions::builder()->timeout(400)->build());

    expect($client->sendRequest('GET', 'http://example.com/a', [], null)->statusCode)->toBe(200);
    usleep(450000);
    expect($client->sendRequest('GET', 'http://example.com/b', [], null)->statusCode)->toBe(200);
});

test('bhv redirects 055 psr18 redirect to non http scheme throws', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(302, '', ['Location' => 'file:///etc/passwd'])
    );
    $transport = TransportOptions::builder()->followRedirects(true)->build();
    $client = newPsr18Client($stub, $transport);

    expect(fn (): mixed => $client->sendRequest('GET', 'https://api.example.com/start', [], null))
        ->toThrow(function (\Exception $e): void {
            expect($e::class)->toBe(ApiException::class);
            expect($e->getCode())->toBe(302);
        });
});

test('bhv lifecycle 043 psr18 send after close throws logic exception', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, 'ok'));
    $client = newPsr18Client($stub);
    $client->close();

    expect(fn (): mixed => $client->sendRequest('GET', 'http://example.com/after-close', [], null))
        ->toThrow(function (\Exception $e): void {
            expect($e::class)->toBe(\LogicException::class);
        });
});

test('bhv langphp 021 psr18 transport failure raises NetworkException', function (): void {
    // The stub throws a PSR-18 ClientExceptionInterface once it runs out of
    // canned responses: no HTTP response arrived.
    $client = newPsr18Client(new StubPsr18Client());

    try {
        $client->sendRequest('GET', 'http://example.com/refused', [], null);
        test()->fail('Expected NetworkException');
    } catch (\PetstoreClient\Errors\NetworkException $e) {
        expect($e)->not->toBeInstanceOf(\PetstoreClient\Errors\NetworkTimeoutException::class);
        expect($e->getStatusCode())->toBe(0);
        expect($e->getPrevious())->toBeInstanceOf(ClientExceptionInterface::class);
    }
});

test('bhv transport 031 psr18 timeout wrapped by the client raises NetworkTimeoutException', function (): void {
    // Symfony's Psr18Client wraps its TimeoutException in a PSR-18
    // NetworkExceptionInterface; the SDK finds it in the chain.
    $stub = new class () implements ClientInterface {
        public function sendRequest(RequestInterface $request): ResponseInterface
        {
            $timeout = new \Symfony\Component\HttpClient\Exception\TimeoutException('Idle timeout reached');
            throw new class ('Idle timeout reached', 0, $timeout) extends \RuntimeException implements ClientExceptionInterface {
            };
        }
    };
    $factory = new Psr17Factory();
    $client = new Psr18ApiClient($stub, $factory, $factory);

    expect(fn (): mixed => $client->sendRequest('GET', 'http://example.com/slow', [], null))
        ->toThrow(\PetstoreClient\Errors\NetworkTimeoutException::class);
});

test('bhv transport 088 psr18 decompresses a valid gzip-encoded response body', function (): void {
    $payload = '{"ok":true}';
    $gzipped = gzencode($payload);
    expect($gzipped)->not->toBeFalse();
    $stub = new StubPsr18Client(
        psr18Response(200, (string) $gzipped, [
            'Content-Type' => 'application/json',
            'Content-Encoding' => 'gzip',
        ])
    );
    $client = newPsr18Client($stub);

    $response = $client->sendRequest('GET', 'http://example.com/echo', [], null);

    expect($response->body)->toBe($payload);
});

test('bhv transport 089 psr18 AL content-encoding gzip lie with plaintext body surfaces ApiException', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(200, 'plain, not gzip', [
            'Content-Type' => 'application/json',
            'Content-Encoding' => 'gzip',
        ])
    );
    $client = newPsr18Client($stub);

    expect(fn (): mixed => $client->sendRequest('GET', 'http://example.com/lie', [], null))
        ->toThrow(function (\Exception $e): void {
            // A response did arrive: ApiException with the real status, never NetworkException.
            expect($e::class)->toBe(ApiException::class);
            expect($e->getCode())->toBe(200);
        });
});

test('bhv serializationwire 039 psr18 multipart png file gets image png content type', function (): void {
    $tmp = tempnam(sys_get_temp_dir(), 'mp_');
    expect($tmp)->toBeString();
    $pngPath = $tmp . '.png';
    rename($tmp, $pngPath);
    file_put_contents($pngPath, "\x89PNG\r\n\x1A\n");

    try {
        $stub = new StubPsr18Client(psr18Response(200, '{}'));
        $client = newPsr18Client($stub);
        $client->sendRequest(
            'POST',
            'http://example.com/upload',
            [],
            ['file' => new \SplFileObject($pngPath)]
        );

        $sent = (string) $stub->requests[0]->getBody();
        expect($sent)->toContain('Content-Type: image/png');
        expect($stub->requests[0]->getHeaderLine('Content-Type'))->toStartWith('multipart/form-data; boundary=');
    } finally {
        @unlink($pngPath);
    }
});

test('bhv misc 101 psr18 multipart file that can no longer be read throws instead of sending an empty part', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $client = newPsr18Client($stub);
    $path = tempnam(sys_get_temp_dir(), 'part');
    assert($path !== false);
    file_put_contents($path, 'bytes');
    $file = new \SplFileObject($path);
    unlink($path);

    expect(fn (): mixed => $client->sendRequest('POST', 'http://example.com/upload', [], ['file' => $file]))
        ->toThrow(\InvalidArgumentException::class, 'Failed to read multipart file');
    expect($stub->requests)->toBe([]);
});

test('bhv langphp 022 psr18 multipart psr7 stream part is sent as a file part with its bytes', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $client = newPsr18Client($stub);

    $client->sendRequest(
        'POST',
        'http://example.com/upload',
        [],
        ['file' => (new Psr17Factory())->createStream('stream-bytes')]
    );

    $sent = (string) $stub->requests[0]->getBody();
    expect($sent)->toContain('stream-bytes');
    expect($sent)->toContain('filename="file"');
});

test('bhv langphp 023 psr18 multipart raw bytes part reuses field name as filename with octet stream', function (): void {
    /* A raw-bytes part with no explicit filename must reuse the field NAME as
     * the filename and emit a Content-Type guessed from that filename's
     * extension, falling back to application/octet-stream when there is none.
     * For a field named "file" (no extension) the wire bytes must therefore
     * carry name="file"; filename="file" AND Content-Type: application/octet-stream. */
    $stream = fopen('php://temp', 'w+');
    expect($stream)->toBeResource();
    fwrite($stream, "\x00\x01\x02");
    rewind($stream);

    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $client = newPsr18Client($stub);
    $client->sendRequest(
        'POST',
        'http://example.com/upload',
        [],
        ['file' => $stream]
    );

    $sent = (string) $stub->requests[0]->getBody();
    expect($sent)->toContain('Content-Disposition: form-data; name="file"; filename="file"');
    expect($sent)->toContain('Content-Type: application/octet-stream');
});

test('bhv lifecycle 020 psr18 multipart leaves a caller supplied stream open', function (): void {
    $stream = fopen('php://temp', 'w+');
    expect($stream)->toBeResource();
    fwrite($stream, 'raw bytes');
    rewind($stream);

    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    newPsr18Client($stub)->sendRequest('POST', 'http://example.com/upload', [], ['file' => $stream]);

    expect((string) $stub->requests[0]->getBody())->toContain('raw bytes');
    expect(is_resource($stream))->toBeTrue();
    if (is_resource($stream)) {
        fclose($stream);
    }
});

test('bhv serialization 126 psr18 multipart non ascii field name preserved as utf 8', function (): void {
    $fieldName = 'caféMénù';
    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $client = newPsr18Client($stub);

    $client->sendRequest('POST', 'http://example.com/upload', [], [$fieldName => 'value']);

    $sent = (string) $stub->requests[0]->getBody();
    expect($sent)->toContain('name="' . $fieldName . '"');
});

test('bhv serializationwire 042 psr18 multipart escapes a quote and a backslash in the field name', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $client = newPsr18Client($stub);

    $client->sendRequest('POST', 'http://example.com/upload', [], ['a"b\c' => 'value']);

    expect((string) $stub->requests[0]->getBody())->toContain('Content-Disposition: form-data; name="a\"b\\\\c"');
});

test('bhv serialization 127 psr18 multipart scalar parts use the canonical text form', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $client = newPsr18Client($stub);

    $client->sendRequest(
        'POST',
        'http://example.com/upload',
        [],
        ['yes' => true, 'no' => false, 'count' => 7, 'ratio' => 1.0]
    );

    $sent = (string) $stub->requests[0]->getBody();
    expect($sent)->toContain("name=\"yes\"\r\n\r\ntrue\r\n");
    expect($sent)->toContain("name=\"no\"\r\n\r\nfalse\r\n");
    expect($sent)->toContain("name=\"count\"\r\n\r\n7\r\n");
    expect($sent)->toContain("name=\"ratio\"\r\n\r\n1.0\r\n");
});

/*
 * Cross-language multipart parity: a multipart/form-data body that carries a
 * MODEL part must serialize that part through the SDK's configured
 * ObjectSerializer, so the JSON uses the WIRE property names ("isEnabled",
 * "recordedAt" — never the snake_case "is_enabled"/"recorded_at") and the SDK's
 * RFC 3339 date-time format. The Psr18ApiClient's in-memory encodeMultipart()
 * routes object parts through ObjectSerializer::serialize(); this exercises
 * that exact path and asserts the emitted part JSON honours the SerializedName
 * mapping and the date-time wire format (sub-second precision preserved, e.g.
 * ...05.123+00:00).
 */
test('bhv serializationwire 045 psr18 multipart model part uses configured serializer wire names', function (): void {
    $metadata = new MultipartModelPart(
        isEnabled: true,
        recordedAt: new \DateTime('2020-01-02T03:04:05.123Z'),
    );

    $stub = new StubPsr18Client(psr18Response(200, '[]'));
    $client = newPsr18Client($stub);
    $client->sendRequest(
        'POST',
        'http://example.com/resources/1/uploads',
        [],
        ['metadata' => $metadata]
    );

    $sent = (string) $stub->requests[0]->getBody();

    /* The model part is a JSON part. */
    expect($sent)->toContain('Content-Disposition: form-data; name="metadata"');
    expect($sent)->toContain('Content-Type: application/json');

    /* Wire (camelCase) property names from the model's SerializedName mapping,
     * NOT the snake_case form. */
    expect($sent)->toContain('"isEnabled":true');
    expect($sent)->toContain('"recordedAt":');
    expect($sent)->not->toContain('is_enabled');
    expect($sent)->not->toContain('recorded_at');

    /* The date-time carries the SDK's RFC 3339 wire format with sub-second
     * precision (DateTime::RFC3339_EXTENDED). */
    expect($sent)->toContain('"recordedAt":"2020-01-02T03:04:05.123+00:00"');
});

test('bhv langphp 024 psr18 multipart primitive parts are plain text and only models are json', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $client = newPsr18Client($stub);

    $client->sendRequest(
        'POST',
        'http://example.com/upload',
        [],
        [
            'flag' => true,
            'total' => 3,
            'at' => new \DateTime('2024-01-15T10:30:00Z'),
            'choice' => ApiKeyLocation::QUERY,
            'id' => \Symfony\Component\Uid\Uuid::fromString('E621E1F8-C36C-495A-93FC-0C247A3E6E5F'),
            'model' => new MultipartModelPart(isEnabled: true),
        ]
    );

    $sent = (string) $stub->requests[0]->getBody();
    expect($sent)->toContain("Content-Disposition: form-data; name=\"flag\"\r\n\r\ntrue\r\n");
    expect($sent)->toContain("Content-Disposition: form-data; name=\"total\"\r\n\r\n3\r\n");
    expect($sent)->toContain("Content-Disposition: form-data; name=\"at\"\r\n\r\n2024-01-15T10:30:00.000+00:00\r\n");
    expect($sent)->toContain("Content-Disposition: form-data; name=\"choice\"\r\n\r\nquery\r\n");
    expect($sent)->toContain("Content-Disposition: form-data; name=\"id\"\r\n\r\ne621e1f8-c36c-495a-93fc-0c247a3e6e5f\r\n");
    expect($sent)->toContain("Content-Disposition: form-data; name=\"model\"\r\nContent-Type: application/json\r\n\r\n{\"isEnabled\":true}\r\n");
    expect(substr_count($sent, 'Content-Type:'))->toBe(1);
});

test('bhv langphp 025 psr18 keeps an allowReserved query value byte for byte', function (): void {
    $stub = new StubPsr18Client(psr18Response(200, '{}'));
    $client = newPsr18Client($stub);
    $query = 'revision=' . \PetstoreClient\ValueSerializer::encodeQueryAllowingReserved("v1.0/beta:rc1[x]'y");

    $client->sendRequest('GET', 'http://example.com/echo?' . $query, [], null);

    expect($stub->requests[0]->getUri()->getQuery())->toBe("revision=v1.0/beta:rc1%5Bx%5D'y");
});

test('bhv redirects 046 psr18 a redirect chain within the limit reaches its target', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(302, '', ['Location' => 'https://api.example.com/hop-1']),
        psr18Response(302, '', ['Location' => 'https://api.example.com/hop-2']),
        psr18Response(200, 'arrived')
    );
    $transport = TransportOptions::builder()->followRedirects(true)->maxRedirects(3)->build();
    $client = newPsr18Client($stub, $transport);

    $response = $client->sendRequest('GET', 'https://api.example.com/start', [], null);

    expect($response->statusCode)->toBe(200);
    expect($response->body)->toBe('arrived');
});

test('bhv redirects 047 psr18 a refused redirect chain does not change the outcome of the next request', function (): void {
    $refused = new StubPsr18Client(
        psr18Response(302, '', ['Location' => 'https://api.example.com/a']),
        psr18Response(302, '', ['Location' => 'https://api.example.com/b'])
    );
    $transport = TransportOptions::builder()->followRedirects(true)->maxRedirects(1)->build();
    $client = newPsr18Client($refused, $transport);

    expect(fn (): mixed => $client->sendRequest('GET', 'https://api.example.com/start', [], null))
        ->toThrow(ApiException::class);

    $ok = new StubPsr18Client(psr18Response(200, 'second'));
    $response = newPsr18Client($ok, $transport)->sendRequest('GET', 'https://api.example.com/start', [], null);

    expect($response->body)->toBe('second');
});

test('bhv redirects 049 psr18 a 307 redirect repeats the post with the same body', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(307, '', ['Location' => 'https://api.example.com/moved']),
        psr18Response(200, 'ok')
    );
    $transport = TransportOptions::builder()->followRedirects(true)->build();
    $client = newPsr18Client($stub, $transport);

    $client->sendRequest('POST', 'https://api.example.com/start', [], 'payload');

    expect($stub->requests[1]->getMethod())->toBe('POST');
    expect((string) $stub->requests[1]->getBody())->toBe('payload');
});

test('bhv redirects 050 psr18 the follow up method matches the rfc 9110 table for each redirect status', function (int $status, string $expected): void {
    $stub = new StubPsr18Client(
        psr18Response($status, '', ['Location' => 'https://api.example.com/moved']),
        psr18Response(200, 'ok')
    );
    $client = newPsr18Client($stub, TransportOptions::builder()->followRedirects(true)->build());

    $client->sendRequest('POST', 'https://api.example.com/start', [], 'payload');

    expect($stub->requests[1]->getMethod())->toBe($expected);
})->with([
    '301 becomes get' => [301, 'GET'],
    '302 becomes get' => [302, 'GET'],
    '303 becomes get' => [303, 'GET'],
    '307 keeps post' => [307, 'POST'],
    '308 keeps post' => [308, 'POST'],
]);

test('bhv redirects 048 psr18 a 302 turns a post into a get and keeps every other method', function (string $method, string $expected): void {
    $stub = new StubPsr18Client(
        psr18Response(302, '', ['Location' => 'https://api.example.com/moved']),
        psr18Response(200, 'ok')
    );
    $client = newPsr18Client($stub, TransportOptions::builder()->followRedirects(true)->build());

    $client->sendRequest($method, 'https://api.example.com/start', [], null);

    expect($stub->requests[1]->getMethod())->toBe($expected);
})->with([
    'post becomes get' => ['POST', 'GET'],
    'put is kept' => ['PUT', 'PUT'],
    'patch is kept' => ['PATCH', 'PATCH'],
    'delete is kept' => ['DELETE', 'DELETE'],
    'head is kept' => ['HEAD', 'HEAD'],
    'get is kept' => ['GET', 'GET'],
]);

test('bhv redirects 051 psr18 a 303 turns every method into a get except head', function (string $method, string $expected): void {
    $stub = new StubPsr18Client(
        psr18Response(303, '', ['Location' => 'https://api.example.com/moved']),
        psr18Response(200, 'ok')
    );
    $client = newPsr18Client($stub, TransportOptions::builder()->followRedirects(true)->build());

    $client->sendRequest($method, 'https://api.example.com/start', [], null);

    expect($stub->requests[1]->getMethod())->toBe($expected);
})->with([
    'post becomes get' => ['POST', 'GET'],
    'put becomes get' => ['PUT', 'GET'],
    'delete becomes get' => ['DELETE', 'GET'],
    'head stays head' => ['HEAD', 'HEAD'],
]);

test('bhv redirects 052 psr18 a redirect to another origin removes authorization cookie and secret headers', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(302, '', ['Location' => 'https://other.example.com/final']),
        psr18Response(200, 'ok')
    );
    $client = newPsr18Client($stub, TransportOptions::builder()->followRedirects(true)->build());

    $client->sendRequest('GET', 'https://api.example.com/start', [
        'Authorization' => 'Bearer secret',
        'Proxy-Authorization' => 'Basic secret',
        'Cookie' => 'session=xyz',
        'X-Api-Token' => 'token-value',
        'X-Trace' => 'keep',
    ], null);

    expect($stub->requests[1]->hasHeader('Authorization'))->toBeFalse();
    expect($stub->requests[1]->hasHeader('Proxy-Authorization'))->toBeFalse();
    expect($stub->requests[1]->hasHeader('Cookie'))->toBeFalse();
    expect($stub->requests[1]->hasHeader('X-Api-Token'))->toBeFalse();
    expect($stub->requests[1]->hasHeader('X-Trace'))->toBeTrue();
});

test('bhv redirects 053 psr18 authorization is kept on a same origin redirect and removed when the scheme changes', function (): void {
    $same = new StubPsr18Client(
        psr18Response(302, '', ['Location' => 'https://api.example.com/next']),
        psr18Response(200, 'ok')
    );
    newPsr18Client($same, TransportOptions::builder()->followRedirects(true)->build())
        ->sendRequest('GET', 'https://api.example.com/start', ['Authorization' => 'Bearer secret'], null);
    expect($same->requests[1]->hasHeader('Authorization'))->toBeTrue();

    $downgrade = new StubPsr18Client(
        psr18Response(302, '', ['Location' => 'http://api.example.com/next']),
        psr18Response(200, 'ok')
    );
    newPsr18Client($downgrade, TransportOptions::builder()->followRedirects(true)->build())
        ->sendRequest('GET', 'https://api.example.com/start', ['Authorization' => 'Bearer secret'], null);
    expect($downgrade->requests[1]->hasHeader('Authorization'))->toBeFalse();
});

test('bhv misc 131 a supplied session follows redirects under the sdk redirect rules', function (): void {
    $stub = new StubPsr18Client(
        psr18Response(302, '', ['Location' => 'https://api.example.com/landing']),
        psr18Response(200, 'landed')
    );
    $transport = TransportOptions::builder()->followRedirects(true)->build();
    $client = newPsr18Client($stub, $transport);

    $response = $client->sendRequest('GET', 'https://api.example.com/start', [], null);

    expect($response->statusCode)->toBe(200);
    expect($stub->requests)->toHaveCount(2);
});
