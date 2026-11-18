<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PetstoreClient\Api\BaseApi;
use PetstoreClient\Configuration;
use PetstoreClient\DefaultApiClient;
use PetstoreClient\Servers;
use PetstoreClient\TransportOptions;
use PetstoreClient\Auth\Authenticator;
use PetstoreClient\Auth\BearerAuthenticator;
use PetstoreClient\Auth\ApiKeyAuthenticator;
use PetstoreClient\Auth\ApiKeyLocation;
use PetstoreClient\Errors\ApiException;
use PetstoreClient\Errors\ClientException;
use PetstoreClient\Errors\ServerException;
use PetstoreClient\Errors\BadRequestException;
use PetstoreClient\Errors\UnauthorizedException;
use PetstoreClient\Errors\ForbiddenException;
use PetstoreClient\Errors\NotFoundException;
use PetstoreClient\Errors\ConflictException;
use PetstoreClient\Errors\UnprocessableEntityException;
use PetstoreClient\Errors\TooManyRequestsException;
use PetstoreClient\Errors\InternalServerErrorException;
use PetstoreClient\ApiClient;
use PetstoreClient\ApiHttpResponse;
use PetstoreClient\Api\PetApi;
use PetstoreClient\Api\StoreApi;
use PetstoreClient\Api\Options\FindPetsByStatusOptions;
use PetstoreClient\Api\Options\AddPetOptions;
use PetstoreClient\Models\Category;
use PetstoreClient\Models\Pet;

function makeBaseApiTestableApi(): TestableApi
{
    $url = getenv('CHASM_HTTP_URL') ?: '';
    $config = new Configuration($url);
    return new TestableApi(new DefaultApiClient(TransportOptions::builder()->maxRetries(0)->build()), $config);
}

test('bhv errors 037 throws correct exception', function (int $status, string $expectedClass): void {
    try {
        makeBaseApiTestableApi()->call(
            'GET',
            "/test/status/$status",
            [],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        test()->fail('Expected exception not thrown');
    } catch (ApiException $e) {
        expect($e::class)->toBe($expectedClass);
        expect($e->getCode())->toBe($status);
        expect($e->getResponseBody())->not->toBeEmpty();
        expect($e->getRequestMethod())->toBe('GET');
        expect($e->getRequestPath())->toBe("/test/status/$status");
    }
})->with([
    [400, BadRequestException::class],
    [401, UnauthorizedException::class],
    [403, ForbiddenException::class],
    [404, NotFoundException::class],
    [409, ConflictException::class],
    [422, UnprocessableEntityException::class],
    [429, TooManyRequestsException::class],
    [418, ClientException::class],
    [500, InternalServerErrorException::class],
    [502, ServerException::class],
]);

test('bhv errors 029 get typed error body deserializes into given class', function (): void {
    $ex = new ApiException(400, 'boom', [], '{"id":42,"name":"Dogs"}');
    $typed = $ex->getTypedErrorBody(Category::class);

    expect($typed)->toBeInstanceOf(Category::class);
    assert($typed instanceof Category);
    expect($typed->id)->toBe(42);
    expect($typed->name)->toBe('Dogs');
});

test('bhv errors 028 get typed error body returns null for empty body', function (): void {
    $ex = new ApiException(400, 'boom', [], '');
    expect($ex->getTypedErrorBody(Category::class))->toBeNull();
});

test('bhv errors 030 get typed error body returns null for whitespace body', function (): void {
    $ex = new ApiException(400, 'boom', [], "   \n\t");
    expect($ex->getTypedErrorBody(Category::class))->toBeNull();
});

test('bhv errors 034 parses json error body', function (): void {
    try {
        makeBaseApiTestableApi()->call(
            'GET',
            '/test/status/400',
            [],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        test()->fail('Expected exception not thrown');
    } catch (BadRequestException $e) {
        expect($e->getErrorBody())->not->toBeNull();
    }
});

test('bhv errors 033 not found hierarchy', function (): void {
    try {
        makeBaseApiTestableApi()->call(
            'GET',
            '/test/status/404',
            [],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        test()->fail('Expected exception not thrown');
    } catch (NotFoundException $e) {
        expect($e::class)->toBe(NotFoundException::class);
        expect($e)->toBeInstanceOf(ClientException::class);
        expect($e)->toBeInstanceOf(ApiException::class);
        expect($e)->toBeInstanceOf(\PetstoreClient\Errors\OpenAPIException::class);
    }
});

test('bhv errors 031 internal server error hierarchy', function (): void {
    try {
        makeBaseApiTestableApi()->call(
            'GET',
            '/test/status/500',
            [],
            [],
            null,
            ['application/json'],
            'application/json',
            null
        );
        test()->fail('Expected exception not thrown');
    } catch (InternalServerErrorException $e) {
        expect($e::class)->toBe(InternalServerErrorException::class);
        expect($e)->toBeInstanceOf(ServerException::class);
        expect($e)->toBeInstanceOf(ApiException::class);
        expect($e)->toBeInstanceOf(\PetstoreClient\Errors\OpenAPIException::class);
    }
});

test('bhv serializationwire 004 deserializes json response', function (): void {
    $result = makeBaseApiTestableApi()->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        'object'
    );
    expect($result)->toBeArray();
    /** @var array<string, mixed> $result */
    expect($result['method'])->toBe('GET');
});

test('bhv serializationwire 019 returns raw string for non json', function (): void {
    $result = makeBaseApiTestableApi()->call(
        'GET',
        '/test/text-plain',
        [],
        [],
        null,
        ['text/plain'],
        'application/json',
        'string'
    );
    expect($result)->toBeString();
    expect($result)->not->toBeEmpty();
});

test('bhv operations 015 returns null when return type is null', function (): void {
    $result = makeBaseApiTestableApi()->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect($result)->toBeNull();
});

test('bhv serializationwire 002 appends query params', function (): void {
    $result = makeBaseApiTestableApi()->call(
        'GET',
        '/test/echo',
        ['foo' => 'bar'],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect($result)->toBeNull();
});

test('bhv authintegration 010 forwards auth headers', function (): void {
    $auth = new TestAuthenticator(headers: ['X-Custom' => 'auth-value']);
    $result = makeBaseApiTestableApi()->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        'object',
        $auth
    );
    expect($result)->toBeArray();
    /** @var array{headers: array<string, string>} $result */
    expect($result['headers']['x-custom'])->toBe('auth-value');
});

test('bhv authintegration 017 sets cookie header', function (): void {
    $auth = new TestAuthenticator(cookies: ['session' => 'abc123']);
    $result = makeBaseApiTestableApi()->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        'object',
        $auth
    );
    expect($result)->toBeArray();
    /** @var array{headers: array<string, string>} $result */
    expect($result['headers']['cookie'])->toBe('session=abc123');
});

/**
 * A transport that issues a new token (t1, t2, ...) at the token endpoint and
 * accepts only the bearer token t2 at every other URL, answering 401 otherwise.
 * Every request it receives is appended to $requests.
 *
 * @param \ArrayObject<int, array{url: string, headers: array<string, string>}> $requests
 */
function tokenRotatingApiClient(\ArrayObject $requests): ApiClient
{
    return new class ($requests) implements ApiClient {
        private int $issued = 0;

        /** @param \ArrayObject<int, array{url: string, headers: array<string, string>}> $requests */
        public function __construct(private readonly \ArrayObject $requests)
        {
        }

        public function close(): void
        {
        }

        /** @param array<string, string> $headers */
        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            $this->requests[] = ['url' => $url, 'headers' => $headers];
            if ($url === 'https://auth.example.com/token') {
                $this->issued++;
                return new ApiHttpResponse(
                    200,
                    (string) json_encode(['access_token' => 't' . $this->issued, 'expires_in' => 3600]),
                    ['Content-Type' => 'application/json']
                );
            }
            $status = ($headers['Authorization'] ?? '') === 'Bearer t2' ? 200 : 401;
            return new ApiHttpResponse($status, '{}', ['Content-Type' => 'application/json']);
        }
    };
}

test('bhv retries 001 a 401 drops the cached token and replays the request once with a fresh one', function (): void {
    /** @var \ArrayObject<int, array{url: string, headers: array<string, string>}> $requests */
    $requests = new \ArrayObject();
    $client = tokenRotatingApiClient($requests);
    $auth = new \PetstoreClient\Auth\OAuth\OAuth2ClientCredentialsAuthenticator(
        'https://api.example.com',
        'id',
        'secret',
        'https://auth.example.com/token',
        []
    );
    $auth->setApiClient($client);
    $api = new TestableApi($client, new Configuration('http://localhost'), $auth);

    $result = $api->call('GET', '/items', [], [], null, ['application/json'], null, 'object');

    expect($result)->toBeArray();
    $apiCalls = array_values(array_filter(
        $requests->getArrayCopy(),
        static fn (array $r): bool => $r['url'] !== 'https://auth.example.com/token'
    ));
    expect($apiCalls)->toHaveCount(2);
    expect($apiCalls[0]['headers']['Authorization'])->toBe('Bearer t1');
    expect($apiCalls[1]['headers']['Authorization'])->toBe('Bearer t2');
});

test('bhv authintegration 002 a per-call oauth authenticator is given the transport before it is used', function (): void {
    /** @var \ArrayObject<int, array{url: string, headers: array<string, string>}> $requests */
    $requests = new \ArrayObject();
    $client = tokenRotatingApiClient($requests);
    $auth = new \PetstoreClient\Auth\OAuth\OAuth2ClientCredentialsAuthenticator(
        'https://api.example.com',
        'id',
        'secret',
        'https://auth.example.com/token',
        []
    );
    $api = new TestableApi($client, new Configuration('http://localhost'));

    $api->call('GET', '/items', [], [], null, ['application/json'], null, null, $auth);

    $first = $requests[0] ?? ['url' => ''];
    expect($first['url'])->toBe('https://auth.example.com/token');
});

test('a binary body is sent as raw bytes whether given as string, stream resource or PSR-7 stream', function (
    string $kind
): void {
    $client = new CapturingApiClient();
    $api = new TestableApi($client, new Configuration('http://localhost'));
    $resource = fopen('php://memory', 'r+');
    expect($resource)->not->toBeFalse();
    assert(is_resource($resource));
    fwrite($resource, 'raw-bytes');
    rewind($resource);
    $body = match ($kind) {
        'string' => 'raw-bytes',
        'resource' => $resource,
        default => (new \Nyholm\Psr7\Factory\Psr17Factory())->createStream('raw-bytes'),
    };

    $api->call('POST', '/test/echo', [], [], $body, ['application/json'], 'application/octet-stream', null);

    expect($client->capturedBody)->toBe('raw-bytes');
    fclose($resource);
})->with(['string', 'resource', 'psr7']);

test('a binary body file that can no longer be read throws instead of sending an empty body', function (): void {
    $client = new CapturingApiClient();
    $api = new TestableApi($client, new Configuration('http://localhost'));
    $path = tempnam(sys_get_temp_dir(), 'body');
    assert($path !== false);
    file_put_contents($path, 'bytes');
    $file = new \SplFileObject($path);
    unlink($path);

    expect(fn (): mixed => $api->call(
        'POST',
        '/test/echo',
        [],
        [],
        $file,
        ['application/json'],
        'application/octet-stream',
        null
    ))->toThrow(\InvalidArgumentException::class, 'Failed to read request body file');
    expect($client->capturedBody)->toBeNull();
});

test('bhv authintegration 009 falls back to client-level authenticator when no per-call auth is supplied', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $clientAuth = new TestAuthenticator(headers: ['X-Client-Auth' => 'client-level-token']);
    $api = new TestableApi($client, $config, $clientAuth);
    $api->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect($client->capturedHeaders['X-Client-Auth'] ?? '')->toBe('client-level-token');
});

test('bhv authintegration 013 per-call auth overrides the client-level authenticator', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $clientAuth = new TestAuthenticator(headers: ['X-Client-Auth' => 'client-level-token']);
    $perCallAuth = new TestAuthenticator(headers: ['X-Client-Auth' => 'per-call-token']);
    $api = new TestableApi($client, $config, $clientAuth);
    $api->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        null,
        $perCallAuth
    );
    expect($client->capturedHeaders['X-Client-Auth'] ?? '')->toBe('per-call-token');
});

// -- one credential per security scheme (F857) --

/**
 * Builds a TestableApi with the given scheme credentials and the default authenticator.
 *
 * @param array<string, Authenticator> $schemes
 * @return array{0: TestableApi, 1: CapturingApiClient}
 */
function newSchemeApi(array $schemes, ?Authenticator $default = null): array
{
    $client = new CapturingApiClient();

    return [new TestableApi($client, new Configuration('http://localhost'), $default, $schemes), $client];
}

test('bhv authintegration 003 an operation uses the credential of the scheme its requirement names', function (): void {
    [$api, $client] = newSchemeApi([
        'bearer' => new TestAuthenticator(headers: ['Authorization' => 'Bearer b']),
        'key' => new TestAuthenticator(headers: ['X-Key' => 'k']),
    ]);

    $api->callWithRequirements(['application/json'], [['key']]);

    expect($client->capturedHeaders['X-Key'] ?? null)->toBe('k');
    expect($client->capturedHeaders)->not->toHaveKey('Authorization');
});

test('bhv authintegration 014 an operation uses the first alternative whose schemes are all configured', function (): void {
    [$api, $client] = newSchemeApi([
        'bearer' => new TestAuthenticator(headers: ['Authorization' => 'Bearer b']),
    ]);

    $api->callWithRequirements(['application/json'], [['key'], ['bearer']]);

    expect($client->capturedHeaders['Authorization'] ?? null)->toBe('Bearer b');
});

test('an alternative that needs two schemes applies both credentials', function (): void {
    [$api, $client] = newSchemeApi([
        'bearer' => new TestAuthenticator(headers: ['Authorization' => 'Bearer b']),
        'key' => new TestAuthenticator(headers: ['X-Key' => 'k']),
    ]);

    $api->callWithRequirements(['application/json'], [['key', 'bearer']]);

    expect($client->capturedHeaders['X-Key'] ?? null)->toBe('k');
    expect($client->capturedHeaders['Authorization'] ?? null)->toBe('Bearer b');
});

test('bhv authintegration 007 an alternative with a scheme that has no credential is not used', function (): void {
    [$api, $client] = newSchemeApi([
        'key' => new TestAuthenticator(headers: ['X-Key' => 'k']),
    ]);

    $api->callWithRequirements(['application/json'], [['key', 'bearer']]);

    expect($client->capturedHeaders)->not->toHaveKey('X-Key');
});

test('bhv authintegration 016 an operation sends no credential when none of its schemes is configured', function (): void {
    [$api, $client] = newSchemeApi(
        ['key' => new TestAuthenticator(headers: ['X-Key' => 'k'])],
        new TestAuthenticator(headers: ['X-Default' => 'd'])
    );

    $api->callWithRequirements(['application/json'], [['bearer']]);

    expect($client->capturedHeaders)->not->toHaveKey('X-Key');
    expect($client->capturedHeaders)->not->toHaveKey('X-Default');
});

test('bhv authintegration 008 without scheme credentials the default authenticator applies to a secured operation', function (): void {
    [$api, $client] = newSchemeApi([], new TestAuthenticator(headers: ['X-Default' => 'd']));

    $api->callWithRequirements(['application/json'], [['bearer']]);

    expect($client->capturedHeaders['X-Default'] ?? null)->toBe('d');
});

// -- AUTH-APPLIED (Wave A1) --
//
// Regression guard for the Elixir bug where a CONFIGURED authenticator was
// silently dropped: a Bearer / apiKey authenticator handed to the client must
// actually appear on the OUTBOUND request to a SECURED operation. addPet is
// secured (apiKeyHeader / petStoreBearer). We drive the real generated PetApi
// through a CapturingApiClient and assert the credential is on the wire.

test('bhv authintegration 006 configured bearer authenticator is applied to a secured operation', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $auth = new BearerAuthenticator('http://localhost', 'secret-jwt-token');
    $api = new PetApi($client, $config, $auth);
    $pet = new Pet('Rex', new \Ds\Set(['http://example.com/rex.png']));
    try {
        $api->addPetWithHttpInfo($pet);
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured request
    }
    expect($client->capturedHeaders['Authorization'] ?? '')->toBe('Bearer secret-jwt-token');
});

test('the bearer scheme credential is applied to an operation that requires it', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $auth = new BearerAuthenticator('http://localhost', 'per-op-token');
    $api = new PetApi($client, $config, null, ['petStoreBearer' => $auth]);
    $pet = new Pet('Rex', new \Ds\Set(['http://example.com/rex.png']));
    try {
        $api->addPetWithHttpInfo($pet);
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured request
    }
    expect($client->capturedHeaders['Authorization'] ?? '')->toBe('Bearer per-op-token');
});

test('configured api-key header authenticator is applied to a secured operation', function (): void {
    // apiKeyHeader scheme defined by the spec sends X-API-Key as a header.
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $auth = new ApiKeyAuthenticator('http://localhost', 'X-API-Key', 'secret-api-key', ApiKeyLocation::HEADER);
    $api = new PetApi($client, $config, $auth);
    $pet = new Pet('Rex', new \Ds\Set(['http://example.com/rex.png']));
    try {
        $api->addPetWithHttpInfo($pet);
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured request
    }
    expect($client->capturedHeaders['X-API-Key'] ?? '')->toBe('secret-api-key');
});

// -- AUTH-SUPPRESSED (Wave A2) — security:[] operations --
//
// An operation declared `security: []` in the spec is explicitly
// UNAUTHENTICATED. Even when the client is configured WITH an authenticator,
// the generated method passes the NoAuth sentinel, so BaseApi must NOT fall
// back to the client credential. getInventory is declared `security: []`. We
// configure the client with BOTH a bearer (Authorization header) and an
// api-key (X-API-Key header) authenticator, then drive getInventory through a
// CapturingApiClient and assert NONE of the credentials reach the wire.

test('bhv authintegration 005 configured bearer authenticator is suppressed on a security:[] operation', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $auth = new BearerAuthenticator('http://localhost', 'secret-jwt-token');
    $api = new StoreApi($client, $config, $auth);
    try {
        $api->getInventoryWithHttpInfo();
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured request
    }
    expect($client->capturedHeaders)->not->toHaveKey('Authorization');
    expect($client->capturedUrl)->not->toContain('secret-jwt-token');
});

test('bhv authintegration 015 configured api-key authenticator is suppressed on a security:[] operation', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $auth = new ApiKeyAuthenticator('http://localhost', 'X-API-Key', 'secret-api-key', ApiKeyLocation::HEADER);
    $api = new StoreApi($client, $config, $auth);
    try {
        $api->getInventoryWithHttpInfo();
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured request
    }
    expect($client->capturedHeaders)->not->toHaveKey('X-API-Key');
    expect($client->capturedUrl)->not->toContain('secret-api-key');
});

test('configured api-key query authenticator sends no api-key param on a security:[] operation', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $auth = new ApiKeyAuthenticator('http://localhost', 'api_key', 'secret-api-key', ApiKeyLocation::QUERY);
    $api = new StoreApi($client, $config, $auth);
    try {
        $api->getInventoryWithHttpInfo();
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured request
    }
    expect($client->capturedUrl)->not->toContain('api_key=');
    expect($client->capturedUrl)->not->toContain('secret-api-key');
});

test('configured cookie authenticator sets no cookie on a security:[] operation', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $auth = new ApiKeyAuthenticator('http://localhost', 'session', 'secret-session', ApiKeyLocation::COOKIE);
    $api = new StoreApi($client, $config, $auth);
    try {
        $api->getInventoryWithHttpInfo();
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured request
    }
    expect($client->capturedHeaders)->not->toHaveKey('Cookie');
});

// -- AUTH-APPLIED on an INHERITED-security operation --
//
// getPetById declares no security of its own, so it inherits the global
// requirement. This generator strips the inherited auth methods off such an
// operation, so the generated method passes null (not the NoAuth sentinel) and
// BaseApi falls back to the client-level authenticator. A configured client
// credential MUST reach the wire — the regression that silently dropped auth on
// every inherited-security operation.
test('bhv authintegration 012 configured bearer authenticator is applied on an inherited-security operation', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $auth = new BearerAuthenticator('http://localhost', 'secret-jwt-token');
    $api = new PetApi($client, $config, $auth);
    try {
        $api->getPetByIdWithHttpInfo(1);
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured request
    }
    expect($client->capturedHeaders)->toHaveKey('Authorization');
    expect($client->capturedHeaders['Authorization'])->toBe('Bearer secret-jwt-token');
});

test('bhv serializationwire 022 serializes json body', function (): void {
    $result = makeBaseApiTestableApi()->call(
        'POST',
        '/test/echo',
        [],
        [],
        ['key' => 'value'],
        ['application/json'],
        'application/json',
        'object'
    );
    expect($result)->toBeArray();
    /** @var array{body: string} $result */
    /** @var array<string, mixed> $parsedBody */
    $parsedBody = (array) json_decode($result['body'], true);
    expect($parsedBody['key'])->toBe('value');
});

test('bhv operations 016 sends no body when null', function (): void {
    makeBaseApiTestableApi()->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect(true)->toBeTrue();
});

test('bhv operations 041 findPetsByStatus omitting status sends no status key in query', function (): void {
    // findPetsByStatus has an optional `status` query param declared
    // allowEmptyValue:true. allowEmptyValue means the server tolerates an empty
    // value IF the client chooses to send the key — it does NOT mean the SDK
    // must always send the key. When the caller omits status (null), the built
    // request URL must carry no `status` key at all rather than a spurious
    // empty `status=` pair.
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $api = new PetApi($client, $config);
    try {
        $api->findPetsByStatus(new FindPetsByStatusOptions());
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured URL
    }
    expect($client->capturedUrl)->not->toContain('status=');
});

test('bhv options 002 null options omits allow empty value param from the query', function (): void {
    $client = new CapturingApiClient();
    $api = new PetApi($client, new Configuration('http://localhost'));
    try {
        $api->findPetsByStatus();
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured URL
    }
    expect($client->capturedUrl)->not->toContain('status=');
});

test('bhv serializationwire 001 allowEmptyValue param omitted when value is null in options', function (): void {
    // Same wire contract as the serializationwire 010 test below: a null value in the options object
    // is omitted from the query, and the allowEmptyValue flag does not force an empty key.
    $client = new CapturingApiClient();
    $api = new PetApi($client, new Configuration('http://localhost'));
    try {
        $api->findPetsByStatus(new FindPetsByStatusOptions());
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured URL
    }
    expect($client->capturedUrl)->not->toContain('status=');
});

test('bhv serializationwire 010 findPetsByStatus omitting status sends no status= in query', function (): void {
    // findPetsByStatus has an optional `status` query param declared
    // allowEmptyValue:true. allowEmptyValue means the server tolerates an empty
    // value IF the client chooses to send the key — it does NOT mean the SDK
    // must always send the key. When the caller omits status (null), the built
    // request URL must carry no `status` key at all rather than a spurious
    // empty `status=` pair.
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $api = new PetApi($client, $config);
    try {
        $api->findPetsByStatus(new FindPetsByStatusOptions());
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured URL
    }
    expect($client->capturedUrl)->not->toContain('status=');
});

test('bhv operations 003 allowEmptyValue param included when value is empty string', function (): void {
    // When the caller explicitly supplies an empty value for the
    // allowEmptyValue param, the key IS present with an empty value: `status=`.
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $api = new PetApi($client, $config);
    try {
        $api->findPetsByStatus(new FindPetsByStatusOptions(''));
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured URL
    }
    expect($client->capturedUrl)->toContain('status=');
});

test('bhv operations 040 findPetsByStatus with explicit empty status sends status= in query', function (): void {
    // When the caller explicitly supplies an empty value for the
    // allowEmptyValue param, the key IS present with an empty value: `status=`.
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $api = new PetApi($client, $config);
    try {
        $api->findPetsByStatus(new FindPetsByStatusOptions(''));
    } catch (\Exception) {
        // Response deserialization may fail; we only care about the captured URL
    }
    expect($client->capturedUrl)->toContain('status=');
});

test('bhv serializationwire 016 allow empty value includes param in query string', function (): void {
    $result = makeBaseApiTestableApi()->call(
        'GET',
        '/test/echo',
        ['filter' => ''],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect($result)->toBeNull();
});

test('bhv serializationwire 009 expands array query params', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'GET',
        '/test/echo',
        ['tags' => ['a', 'b']],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect($client->capturedUrl)->toContain('tags=a&tags=b');
});

test('bhv serializationwire 020 serializes boolean query params', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'GET',
        '/test/echo',
        ['active' => true],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect($client->capturedUrl)->toContain('active=true');
});

test('bhv serializationwire 023 serializes number query params', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'GET',
        '/test/echo',
        ['limit' => 10],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect($client->capturedUrl)->toContain('limit=10');
    expect($client->capturedUrl)->not->toContain('limit=10.0');
});

test('bhv serializationwire 015 handles empty query params', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect($client->capturedUrl)->not->toContain('?');
});

test('bhv operations 007 collapses double-slash when base url has trailing slash', function (): void {
    // baseUrl='http://host/' + path='/v1/thing' must produce
    // 'http://host/v1/thing', not 'http://host//v1/thing' which most
    // servers route to 404.
    $client = new CapturingApiClient();
    $config = new Configuration('http://host/');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'GET',
        '/v1/thing',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect($client->capturedUrl)->toBe('http://host/v1/thing');
});

// -- server variable overrides via Configuration --

test('bhv options 003 server variable overrides resolve in base url', function (): void {
    $config = Configuration::builder()
        ->server(Servers::server1(), ['environment' => 'staging'])
        ->build();
    expect($config->baseUrl)->toBe('https://staging.example.com/api/v3');
});

test('bhv operations 009 default server variables produce correct base url', function (): void {
    $config = Configuration::builder()
        ->server(Servers::server1())
        ->build();
    expect($config->baseUrl)->toBe('https://api.example.com/api/v3');
});

test('bhv errors 032 invalid enum value throws exception', function (): void {
    expect(fn () => Configuration::builder()
        ->server(Servers::server1(), ['environment' => 'invalid'])
        ->build())->toThrow(\InvalidArgumentException::class);
});

test('bhv operations 005 api request uses resolved server url', function (): void {
    $config = Configuration::builder()
        ->server(Servers::server1(), ['environment' => 'staging'])
        ->build();
    expect($config->baseUrl)->toStartWith('https://staging.example.com/api/v3');
});

// -- content-type deserialization --

test('bhv serializationwire 025 skips deserialization for non json content type', function (): void {
    $client = new class implements ApiClient {
        public function close(): void
        {
        }

        /** @param array<string, string> $headers */
        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            return new ApiHttpResponse(200, 'hello', ['Content-Type' => 'text/plain']);
        }
    };
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $result = $testApi->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['text/plain'],
        'application/json',
        'string'
    );
    expect($result)->toBe('hello');
});

test('bhv errors 035 rejects a text plain body for a model return with a serialization error naming the content type', function (string $returnType): void {
    $client = new class implements ApiClient {
        public function close(): void
        {
        }

        /** @param array<string, string> $headers */
        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            return new ApiHttpResponse(200, 'hello', ['Content-Type' => 'text/plain; charset=utf-8']);
        }
    };
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);

    expect(fn () => $testApi->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        $returnType
    ))
        ->toThrow(
            \PetstoreClient\Errors\SerializationException::class,
            "Content-Type 'text/plain'"
        );
})->with([\PetstoreClient\Models\Pet::class, 'int', 'array<string>']);

test('a binary return type gets the raw bytes of a JSON body', function (): void {
    $client = new class implements ApiClient {
        public function close(): void
        {
        }

        /** @param array<string, string> $headers */
        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            return new ApiHttpResponse(200, '{"id":7,"name":"photo"}', ['Content-Type' => 'application/json']);
        }
    };
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $result = $testApi->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['image/jpeg', 'application/json'],
        'application/json',
        'string'
    );
    expect($result)->toBe('{"id":7,"name":"photo"}');
});

test('bhv serializationwire 005 deserializes vendor json mime types', function (): void {
    $client = new class implements ApiClient {
        public function close(): void
        {
        }

        /** @param array<string, string> $headers */
        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            return new ApiHttpResponse(200, '{"title":"Not Found"}', ['Content-Type' => 'application/problem+json']);
        }
    };
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $result = $testApi->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        'object'
    );
    expect($result)->toBeArray();
    /** @var array<string, mixed> $result */
    expect($result['title'])->toBe('Not Found');
});

// -- body serialization by content type --

test('bhv serializationwire 024 serializes text plain body', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'POST',
        '/test/echo',
        [],
        [],
        'hello world',
        ['application/json'],
        'text/plain',
        null
    );
    expect($client->capturedBody)->not->toBeNull();
    expect($client->capturedBody)->toBeString();
    expect($client->capturedBody)->toContain('hello world');
});

test('bhv serializationwire 021 serializes form urlencoded body', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'POST',
        '/test/echo',
        [],
        [],
        ['name' => 'alice'],
        ['application/json'],
        'application/x-www-form-urlencoded',
        null
    );
    expect($client->capturedBody)->not->toBeNull();
    expect($client->capturedBody)->toBeString();
    expect($client->capturedBody)->toContain('name=alice');
});

test('bhv serializationwire 012 form urlencoded body encodes space as plus', function (): void {
    // application/x-www-form-urlencoded bodies encode a space as '+'
    // (WHATWG form-encoding), matching the other 11 SDKs, not '%20'.
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'POST',
        '/test/echo',
        [],
        [],
        ['q' => 'a b c'],
        ['application/json'],
        'application/x-www-form-urlencoded',
        null
    );
    expect($client->capturedBody)->toBe('q=a+b+c');
});

test('form urlencoded body renders date-time, enum and number fields canonically', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call('POST', '/test/echo', [], [], [
        'at' => new \DateTime('2024-01-15T10:00:00+00:00'),
        'status' => \PetstoreClient\Models\PetStatusEnum::SOLD,
        'ratio' => 1.0,
        'flag' => true,
        'tags' => ['a', null, 'b'],
    ], ['application/json'], 'application/x-www-form-urlencoded', null);
    expect($client->capturedBody)->toBe(
        'at=2024-01-15T10%3A00%3A00.000%2B00%3A00&status=sold&ratio=1.0&flag=true&tags=a&tags=b'
    );
});

test('bhv serializationwire 014 form urlencoded body rejects a nested map field', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    expect(fn () => $testApi->call(
        'POST',
        '/test/echo',
        [],
        [],
        ['location' => ['lat' => 1.5]],
        ['application/json'],
        'application/x-www-form-urlencoded',
        null
    ))
        ->toThrow(
            \PetstoreClient\Errors\SerializationException::class,
            'form-urlencoded field location must be a scalar or array of scalars'
        );
    expect($client->capturedBody)->toBeNull();
});

test('bhv operations 014 passes binary body as is', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'POST',
        '/test/echo',
        [],
        [],
        "\x01\x02\x03",
        ['application/json'],
        'application/octet-stream',
        null
    );
    expect($client->capturedBody)->not->toBeNull();
});

// -- header flow-through --

test('bhv serializationwire 007 empty content type defaults to json', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'POST',
        '/test/echo',
        [],
        [],
        [],
        ['application/json'],
        '',
        null
    );
    expect($client->capturedHeaders['Content-Type'] ?? '')->toBe('application/json');
});

test('bhv operations 002 all headers from selector flow through', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'POST',
        '/test/echo',
        [],
        [],
        [],
        ['application/json'],
        'application/json',
        null
    );
    expect($client->capturedHeaders)->toHaveKey('Accept');
    expect($client->capturedHeaders)->toHaveKey('Content-Type');
});

// -- Default header merge order (Gap C / R5-1) --
//
// Canonical cross-SDK scenario: a caller's config default `Accept` must win
// over the operation-negotiated Accept. selectHeaders() runs first (negotiating
// Accept from $accepts), then config->defaultHeaders is merged OVER it
// (matching java putAll, python, elixir). RED in ruby before its merge-order
// fix; GREEN in PHP (base_api array_merge puts the config defaults last).

test('bhv operations 008 config default Accept wins over operation-negotiated Accept', function (): void {
    $client = new CapturingApiClient();
    $config = Configuration::builder()
        ->baseUrl('http://localhost')
        ->defaultHeader('Accept', 'application/xml')
        ->build();
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect($client->capturedHeaders['Accept'] ?? null)->toBe('application/xml');
});

test('bhv options 001 config default header overrides operation negotiated accept', function (): void {
    $client = new CapturingApiClient();
    $config = Configuration::builder()
        ->baseUrl('http://localhost')
        ->defaultHeader('Accept', 'application/xml')
        ->build();
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect($client->capturedHeaders['Accept'] ?? null)->toBe('application/xml');
});

test('bhv operations 004 an explicit Accept header parameter wins over the configured default and the negotiated one', function (): void {
    $client = new CapturingApiClient();
    $config = Configuration::builder()
        ->baseUrl('http://localhost')
        ->defaultHeader('Accept', 'application/xml')
        ->build();
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'GET',
        '/test/echo',
        [],
        ['Accept' => 'text/csv'],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect($client->capturedHeaders['Accept'] ?? null)->toBe('text/csv');
    expect(array_keys(array_change_key_case($client->capturedHeaders, CASE_LOWER)))
        ->toContain('accept');
});

test('bhv headers 030 a default header in another case replaces the negotiated one instead of being sent beside it', function (): void {
    $client = new CapturingApiClient();
    $config = Configuration::builder()
        ->baseUrl('http://localhost')
        ->defaultHeader('accept', 'application/vnd.x+json')
        ->build();
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    $names = array_filter(
        array_keys($client->capturedHeaders),
        static fn (string $name): bool => strtolower($name) === 'accept',
    );
    expect(array_values($names))->toBe(['accept']);
    expect($client->capturedHeaders['accept'] ?? null)->toBe('application/vnd.x+json');
});

test('bhv operations 001 a per call header replaces a default header of the same name', function (): void {
    $client = new CapturingApiClient();
    $config = Configuration::builder()
        ->baseUrl('http://localhost')
        ->defaultHeader('X-Tenant', 'default-tenant')
        ->build();
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'GET',
        '/test/echo',
        [],
        ['X-Tenant' => 'per-call-tenant'],
        null,
        ['application/json'],
        'application/json',
        null
    );
    $tenantValues = array_filter(
        $client->capturedHeaders,
        static fn (string $name): bool => strcasecmp($name, 'X-Tenant') === 0,
        ARRAY_FILTER_USE_KEY
    );
    expect(array_values($tenantValues))->toBe(['per-call-tenant']);
});

test('bhv serializationwire 013 form urlencoded body keeps a well formed surrogate pair', function (): void {
    // U+1F600 is a surrogate pair in UTF-16 and four UTF-8 bytes; the form body must carry
    // exactly those bytes percent-encoded, with no lone surrogate.
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'POST',
        '/test/echo',
        [],
        [],
        ['q' => "\u{1F600}"],
        ['application/json'],
        'application/x-www-form-urlencoded',
        null
    );
    expect($client->capturedBody)->toBe('q=%F0%9F%98%80');
});

// -- BinaryResponseTests --

test('bhv serializationwire 018 octet stream response decoded as base 64 bytes', function (): void {
    $binaryData = "\x89\x50\x4E\x47\x0D\x0A\x1A\x0A";
    $encoded = base64_encode($binaryData);
    $client = new class implements ApiClient {
        public string $body = '';
        public function close(): void
        {
        }

        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            return new ApiHttpResponse(200, $this->body, ['Content-Type' => 'application/octet-stream']);
        }
    };
    $client->body = $encoded;
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $result = $testApi->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/octet-stream'],
        'application/octet-stream',
        null
    );
    expect($result)->toBe($binaryData);
});

test('bhv operations 012 image png response decoded as bytes', function (): void {
    $binaryData = "\x89\x50\x4E\x47\x0D\x0A\x1A\x0A\x00\x00\x00\x0D";
    $encoded = base64_encode($binaryData);
    $client = new class implements ApiClient {
        public string $body = '';
        public function close(): void
        {
        }

        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            return new ApiHttpResponse(200, $this->body, ['Content-Type' => 'image/png']);
        }
    };
    $client->body = $encoded;
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $result = $testApi->call(
        'GET',
        '/api/img',
        [],
        [],
        null,
        ['image/png'],
        'image/png',
        null
    );
    expect($result)->toBe($binaryData);
});

test('bhv serializationwire 017 binary response roundtrips nul and high bytes', function (): void {
    // Use a byte sequence containing NUL (0x00) and a high byte (0xFF)
    // that would be mangled by any naive UTF-8 string conversion.
    $binaryData = "\x00\xFF\x42";
    $encoded = base64_encode($binaryData);
    $client = new class implements ApiClient {
        public string $body = '';
        public function close(): void
        {
        }

        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            return new ApiHttpResponse(200, $this->body, ['Content-Type' => 'application/octet-stream']);
        }
    };
    $client->body = $encoded;
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $result = $testApi->call(
        'GET',
        '/api/bin',
        [],
        [],
        null,
        ['application/octet-stream'],
        'application/octet-stream',
        null
    );
    expect($result)->toBe($binaryData);
    assert(is_string($result));
    expect(strlen($result))->toBe(3);
});

test('bhv serializationwire 006 empty binary body yields null', function (): void {
    $client = new class implements ApiClient {
        public function close(): void
        {
        }

        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            return new ApiHttpResponse(200, '', ['Content-Type' => 'application/octet-stream']);
        }
    };
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $result = $testApi->call(
        'GET',
        '/api/bin',
        [],
        [],
        null,
        ['application/octet-stream'],
        'application/octet-stream',
        null
    );
    expect($result)->toBeNull();
});

test('bhv serializationwire 003 json response parsed to object', function (): void {
    $client = new class implements ApiClient {
        public function close(): void
        {
        }

        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            return new ApiHttpResponse(200, '{"id":42,"name":"test"}', ['Content-Type' => 'application/json']);
        }
    };
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $result = $testApi->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        null,
        'object'
    );
    expect($result)->toBe(['id' => 42, 'name' => 'test']);
});

test('bhv operations 017 text plain response returns string', function (): void {
    $client = new class implements ApiClient {
        public function close(): void
        {
        }

        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            return new ApiHttpResponse(200, 'hello world', ['Content-Type' => 'text/plain']);
        }
    };
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $result = $testApi->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['text/plain'],
        null,
        null
    );
    expect($result)->toBeString();
    expect($result)->toBe('hello world');
});

test('bhv operations 010 empty body yields null', function (): void {
    $client = new class implements ApiClient {
        public function close(): void
        {
        }

        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            return new ApiHttpResponse(200, '', ['Content-Type' => 'application/octet-stream']);
        }
    };
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $result = $testApi->call(
        'GET',
        '/test/echo',
        [],
        [],
        null,
        ['application/octet-stream'],
        null,
        null
    );
    expect($result)->toBeNull();
});

// -- NullBodyContentTypeTests --

test('bhv operations 013 null body post does not send content type', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'POST',
        '/test/echo',
        [],
        [],
        null,
        ['application/json'],
        'application/json',
        null
    );
    expect($client->capturedHeaders)->not->toHaveKey('Content-Type');
});

test('bhv operations 011 empty string body includes content type', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'POST',
        '/test/echo',
        [],
        [],
        '',
        ['application/json'],
        'application/json',
        null
    );
    expect($client->capturedHeaders)->toHaveKey('Content-Type');
});

test('bhv serializationwire 008 empty json object body includes content type', function (): void {
    $client = new CapturingApiClient();
    $config = new Configuration('http://localhost');
    $testApi = new TestableApi($client, $config);
    $testApi->call(
        'POST',
        '/test/echo',
        [],
        [],
        '{}',
        ['application/json'],
        'application/json',
        null
    );
    expect($client->capturedHeaders)->toHaveKey('Content-Type');
    expect($client->capturedHeaders['Content-Type'])->toBe('application/json');
});

/**
 * A transport that answers 503 for the first $failures requests and 200 after,
 * recording the Authorization header of every request it receives.
 *
 * @param \ArrayObject<int, string> $seen
 */
function flakyTransportApi(int $failures, \ArrayObject $seen, ?Authenticator $auth): TestableApi
{
    $mock = new \Symfony\Component\HttpClient\MockHttpClient(
        function (string $method, string $url, array $options) use ($failures, $seen): \Symfony\Component\HttpClient\Response\MockResponse {
            $authorization = '';
            /** @var array<int, string> $lines */
            $lines = $options['headers'] ?? [];
            foreach ($lines as $line) {
                if (stripos($line, 'authorization:') === 0) {
                    $authorization = trim(substr($line, strlen('authorization:')));
                }
            }
            $seen[] = $authorization;
            $status = count($seen) <= $failures ? 503 : 200;

            return new \Symfony\Component\HttpClient\Response\MockResponse(
                '{"ok":true}',
                ['http_code' => $status, 'response_headers' => ['Content-Type' => 'application/json']]
            );
        }
    );

    return new TestableApi(new StubbedDefaultApiClient($mock), new Configuration('http://localhost'), $auth);
}

test('bhv retries 005 a transient failure is retried and the retry carries freshly built authentication headers', function (): void {
    $auth = new class implements Authenticator {
        private int $issued = 0;

        public function getHost(): string
        {
            return '';
        }

        public function getAuthHeaders(): array
        {
            $this->issued++;

            return ['Authorization' => 'Bearer t' . $this->issued];
        }

        public function getQueryParams(): array
        {
            return [];
        }

        public function getCookieParams(): array
        {
            return [];
        }
    };
    /** @var \ArrayObject<int, string> $seen */
    $seen = new \ArrayObject();
    $api = flakyTransportApi(2, $seen, $auth);

    $result = $api->call('GET', '/items', [], [], null, ['application/json'], null, 'object');

    expect($result)->toBeArray();
    expect($seen->getArrayCopy())->toBe(['Bearer t1', 'Bearer t2', 'Bearer t3']);
});

test('an operation that keeps failing with a retryable status is attempted three times, then reported', function (): void {
    /** @var \ArrayObject<int, string> $seen */
    $seen = new \ArrayObject();
    $api = flakyTransportApi(99, $seen, null);

    expect(fn (): mixed => $api->call('GET', '/items', [], [], null, ['application/json'], null, null))
        ->toThrow(ServerException::class);
    expect(count($seen))->toBe(3);
});

/**
 * A transport that answers 401 to every API call. Its token endpoint issues a token for
 * the first $tokens requests and fails after that. Every request is appended to $requests.
 *
 * @param \ArrayObject<int, array{url: string, headers: array<string, string>}> $requests
 */
function alwaysUnauthorizedApiClient(\ArrayObject $requests, int $tokens): ApiClient
{
    return new class ($requests, $tokens) implements ApiClient {
        private int $issued = 0;

        /** @param \ArrayObject<int, array{url: string, headers: array<string, string>}> $requests */
        public function __construct(private readonly \ArrayObject $requests, private readonly int $tokens)
        {
        }

        public function close(): void
        {
        }

        /** @param array<string, string> $headers */
        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            $this->requests[] = ['url' => $url, 'headers' => $headers];
            if ($url === 'https://auth.example.com/token') {
                $this->issued++;
                if ($this->issued > $this->tokens) {
                    return new ApiHttpResponse(500, '{"error":"server_error"}', ['Content-Type' => 'application/json']);
                }
                return new ApiHttpResponse(
                    200,
                    (string) json_encode(['access_token' => 't' . $this->issued, 'expires_in' => 3600]),
                    ['Content-Type' => 'application/json']
                );
            }
            return new ApiHttpResponse(401, '{}', ['Content-Type' => 'application/json']);
        }
    };
}

test('bhv authintegration 001 a failed re-authentication leaves the 401 as it is', function (): void {
    /** @var \ArrayObject<int, array{url: string, headers: array<string, string>}> $requests */
    $requests = new \ArrayObject();
    $client = alwaysUnauthorizedApiClient($requests, 1);
    $auth = new \PetstoreClient\Auth\OAuth\OAuth2ClientCredentialsAuthenticator(
        'https://api.example.com',
        'id',
        'secret',
        'https://auth.example.com/token',
        []
    );
    $auth->setApiClient($client);
    $api = new TestableApi($client, new Configuration('http://localhost'), $auth);

    expect(fn (): mixed => $api->call('GET', '/items', [], [], null, ['application/json'], null, 'object'))
        ->toThrow(UnauthorizedException::class);
    $apiCalls = array_filter(
        $requests->getArrayCopy(),
        static fn (array $r): bool => $r['url'] !== 'https://auth.example.com/token'
    );
    expect($apiCalls)->toHaveCount(1);
});

test('bhv retries 008 reports a second 401 as unauthorized after exactly one replay', function (): void {
    /** @var \ArrayObject<int, array{url: string, headers: array<string, string>}> $requests */
    $requests = new \ArrayObject();
    $client = alwaysUnauthorizedApiClient($requests, 2);
    $auth = new \PetstoreClient\Auth\OAuth\OAuth2ClientCredentialsAuthenticator(
        'https://api.example.com',
        'id',
        'secret',
        'https://auth.example.com/token',
        []
    );
    $auth->setApiClient($client);
    $api = new TestableApi($client, new Configuration('http://localhost'), $auth);

    expect(fn (): mixed => $api->call('GET', '/items', [], [], null, ['application/json'], null, 'object'))
        ->toThrow(UnauthorizedException::class);
    $apiCalls = array_filter(
        $requests->getArrayCopy(),
        static fn (array $r): bool => $r['url'] !== 'https://auth.example.com/token'
    );
    expect($apiCalls)->toHaveCount(2);
});

test('bhv retries 002 a bearer authenticator is never replayed on a 401', function (): void {
    /** @var \ArrayObject<int, array{url: string, headers: array<string, string>}> $requests */
    $requests = new \ArrayObject();
    $client = alwaysUnauthorizedApiClient($requests, 0);
    $api = new TestableApi(
        $client,
        new Configuration('http://localhost'),
        new BearerAuthenticator('https://api.example.com', 'static-token')
    );

    expect(fn (): mixed => $api->call('GET', '/items', [], [], null, ['application/json'], null, 'object'))
        ->toThrow(UnauthorizedException::class);
    expect($requests)->toHaveCount(1);
});

test('bhv authintegration 004 appends auth cookies to a cookie header the operation already set', function (): void {
    $auth = new TestAuthenticator(cookies: ['session' => 'abc123']);
    $result = makeBaseApiTestableApi()->call(
        'GET',
        '/test/echo',
        [],
        ['Cookie' => 'theme=dark'],
        null,
        ['application/json'],
        'application/json',
        'object',
        $auth
    );
    expect($result)->toBeArray();
    /** @var array{headers: array<string, string>} $result */
    expect($result['headers']['cookie'])->toBe('theme=dark; session=abc123');
});

test('bhv authintegration 011 has no per call authenticator on any options object', function (string $class): void {
    expect(property_exists($class, 'authenticator'))->toBeFalse();
    expect(property_exists($class, 'auth'))->toBeFalse();
})->with([
    'add pet' => [AddPetOptions::class],
    'find pets by status' => [FindPetsByStatusOptions::class],
]);

test('bhv errors 027 an absolute url in the path bypasses the client base url', function (): void {
    $client = new CapturingApiClient();
    $api = new TestableApi($client, new Configuration('http://localhost'));

    $api->call('GET', 'https://other.example.com/v1/items', [], [], null, ['application/json'], null, null);

    expect($client->capturedUrl)->toBe('https://other.example.com/v1/items');
});
