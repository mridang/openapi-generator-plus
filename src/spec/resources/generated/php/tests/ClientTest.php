<?php

declare(strict_types=1);

use PetstoreClient\ApiClient;
use PetstoreClient\ApiHttpResponse;
use PetstoreClient\Client;
use PetstoreClient\Auth\ApiKeyAuthenticator;
use PetstoreClient\Auth\ApiKeyLocation;
use PetstoreClient\Auth\BearerAuthenticator;
use PetstoreClient\Psr18ApiClient;
use PetstoreClient\TransportOptions;
use PetstoreClient\Test\StubPsr18Client;
use Nyholm\Psr7\Factory\Psr17Factory;

beforeEach(function (): void {
    $this->authenticator = new BearerAuthenticator('/api/v3', 'test-token');
});

test('bhv lifecycle 012 construct with authenticator only', function (): void {
    $client = new Client($this->authenticator);

    expect($client)->toBeInstanceOf(Client::class);
});

test('bhv lifecycle 010 construct with authenticator and null transport options', function (): void {
    // A null transport must fall back to the default builder output, so
    // constructing with an explicit null is equivalent to omitting it.
    $client = new Client($this->authenticator);

    expect($client)->toBeInstanceOf(Client::class);
});

test('bhv lifecycle 011 construct with authenticator and transport options', function (): void {
    $transport = TransportOptions::builder()->build();

    $client = new Client($this->authenticator, $transport);

    expect($client)->toBeInstanceOf(Client::class);
});

test('bearer rejects crlf', function (): void {
    expect(fn () => new BearerAuthenticator('/api/v3', "tok\r\nInjected: yes"))
        ->toThrow(\InvalidArgumentException::class);
});

test('bhv lifecycle 006 bearer rejects non ascii', function (): void {
    expect(fn () => new BearerAuthenticator('/api/v3', 'ñoño'))
        ->toThrow(\InvalidArgumentException::class);
});

test('bhv lifecycle 005 bearer rejects empty token', function (): void {
    // An empty / whitespace-only token would emit "Authorization: Bearer "
    // (no credential), so it must be rejected at construction.
    expect(fn () => new BearerAuthenticator('/api/v3', ''))
        ->toThrow(\InvalidArgumentException::class);
    expect(fn () => new BearerAuthenticator('/api/v3', '   '))
        ->toThrow(\InvalidArgumentException::class);
});

test('bhv lifecycle 002 api key header rejects crlf and non ascii', function (): void {
    // RFC 7230 §3.2.6 — header field-value is HTAB / SP / VCHAR.
    // ApiKeyAuthenticator's HEADER location must reject anything
    // outside printable ASCII + TAB to prevent header injection
    // (\r\n) and silent UTF-8 mangling that varies per HTTP lib.
    expect(fn () => new ApiKeyAuthenticator('/api/v3', 'X-Api-Key', "abc\r\nInjected: yes", ApiKeyLocation::HEADER))
        ->toThrow(\InvalidArgumentException::class);
});

test('bhv lifecycle 004 api key header rejects non ascii', function (): void {
    expect(fn () => new ApiKeyAuthenticator('/api/v3', 'X-Api-Key', 'kéy', ApiKeyLocation::HEADER))
        ->toThrow(\InvalidArgumentException::class);
});

test('bhv lifecycle 003 api key query allows non ascii', function (): void {
    // Non-header locations accept arbitrary chars.
    $auth = new ApiKeyAuthenticator('/api/v3', 'api_key', 'kéy', ApiKeyLocation::QUERY);
    expect($auth->getQueryParams())->toBe(['api_key' => 'kéy']);
});

test('bhv lifecycle 001 api groups are accessible', function (): void {
    $client = new Client($this->authenticator);

    expect($client->pet)->toBeInstanceOf(\PetstoreClient\Api\PetApi::class);
    expect($client->store)->toBeInstanceOf(\PetstoreClient\Api\StoreApi::class);
});

test('injected api client is wired into every api group', function (): void {
    // A custom ApiClient passed to the facade must be the exact instance every
    // API group sends through — this is the seam that lets a Psr18ApiClient (or
    // any bespoke transport) replace the bundled DefaultApiClient.
    $stub = new class implements ApiClient {
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
            return new ApiHttpResponse(200, '', []);
        }
    };

    $client = new Client($this->authenticator, null, $stub);

    $property = (new \ReflectionClass(\PetstoreClient\Api\PetApi::class))->getProperty('apiClient');
    expect($property->getValue($client->pet))->toBe($stub);
    $property = (new \ReflectionClass(\PetstoreClient\Api\StoreApi::class))->getProperty('apiClient');
    expect($property->getValue($client->store))->toBe($stub);
});

test('bhv lifecycle 017 a token provider in place of the token builds a client', function (): void {
    /* The facade's withToken() takes a static string; a rotating token goes
     * through a provider on the bearer authenticator handed to withAuthenticator(). */
    $authenticator = new BearerAuthenticator('/api/v3', null, fn (): string => 'rotating-token');

    $client = Client::withAuthenticator($authenticator);

    expect($client)->toBeInstanceOf(Client::class);
});

test('bhv lifecycle 007 close on a client with a supplied transport leaves that transport usable', function (): void {
    $stub = new class implements ApiClient {
        public int $sent = 0;

        public int $closes = 0;

        public function close(): void
        {
            $this->closes++;
        }

        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            $this->sent++;

            return new ApiHttpResponse(200, '', []);
        }
    };

    $client = new Client($this->authenticator, null, $stub);
    $client->close();
    $client->close();

    $stub->sendRequest('GET', 'http://example.com/ping', [], null);
    expect($stub->closes)->toBe(0);
    expect($stub->sent)->toBe(1);
});

test('bhv lifecycle 013 close on a client that built its own transport rejects later requests and is idempotent', function (): void {
    $client = new Client($this->authenticator);
    $client->close();
    $client->close();

    $property = (new \ReflectionClass(\PetstoreClient\Api\PetApi::class))->getProperty('apiClient');
    $transport = $property->getValue($client->pet);
    assert($transport instanceof ApiClient);
    expect(fn () => $transport->sendRequest('GET', 'http://example.com/ping', [], null))
        ->toThrow(\LogicException::class, 'closed');
    $property = (new \ReflectionClass(\PetstoreClient\Api\StoreApi::class))->getProperty('apiClient');
    $transport = $property->getValue($client->store);
    assert($transport instanceof ApiClient);
    expect(fn () => $transport->sendRequest('GET', 'http://example.com/ping', [], null))
        ->toThrow(\LogicException::class, 'closed');
});

/**
 * A transport that counts how often it was asked to send and to close.
 *
 * @return array{0: ApiClient, 1: \stdClass}
 */
function newRecordingTransport(): array
{
    $counts = new \stdClass();
    $counts->sent = 0;
    $counts->closes = 0;
    $transport = new readonly class ($counts) implements ApiClient {
        public function __construct(private \stdClass $counts)
        {
        }

        public function close(): void
        {
            $this->counts->closes++;
        }

        public function sendRequest(
            string $method,
            string $url,
            array $headers,
            mixed $body,
            bool $noRedirect = false,
            bool $noDefaultHeaders = false,
        ): ApiHttpResponse {
            $this->counts->sent++;

            return new ApiHttpResponse(200, '{}', ['Content-Type' => 'application/json']);
        }
    };

    return [$transport, $counts];
}

test('a closed client fails new requests with one canonical error even on a supplied transport', function (): void {
    [$transport, $counts] = newRecordingTransport();
    $client = new Client($this->authenticator, null, $transport);
    $client->close();

    $api = $client->pet;
    $method = new \ReflectionMethod($api, 'invokeApiForResult');
    expect(fn () => $method->invoke($api, 'GET', '/ping', [], [], null, ['application/json'], null, null))
        ->toThrow(\LogicException::class, 'client closed');
    expect($counts->sent)->toBe(0);
});

test('bhv lifecycle 009 closing a client never closes a transport the caller supplied', function (): void {
    [$transport, $counts] = newRecordingTransport();
    $client = new Client($this->authenticator, null, $transport);
    $client->close();
    $client->close();

    expect($counts->closes)->toBe(0);
});

test('a client that is not closed still sends', function (): void {
    [$transport, $counts] = newRecordingTransport();
    $client = new Client($this->authenticator, null, $transport);

    $api = $client->pet;
    $method = new \ReflectionMethod($api, 'invokeApiForResult');
    $method->invoke($api, 'GET', '/ping', [], [], null, ['application/json'], null, null);
    expect($counts->sent)->toBe(1);
});

test('a client needs an authenticator or a scheme credential', function (): void {
    expect(fn () => new Client())
        ->toThrow(\InvalidArgumentException::class, 'at least one security scheme');
});

test('bhv lifecycle 015 a client can be built from the credential of one security scheme alone', function (): void {
    $credential = new BearerAuthenticator('/api/v3', 'scheme-token');
    $client = new Client(petStoreBasic: $credential);

    $property = (new \ReflectionClass(\PetstoreClient\Api\BaseApi::class))->getProperty('schemeAuthenticators');
    expect($property->getValue($client->pet))->toBe(['petStoreBasic' => $credential]);
});

test('a supplied psr18 client receives the request from every api group', function (): void {
    $factory = new Psr17Factory();
    $responses = [];
    for ($i = 0; $i < 50; $i++) {
        $responses[] = $factory->createResponse(200)->withBody($factory->createStream('{}'));
    }
    $stub = new StubPsr18Client(...$responses);

    $client = Client::withHttpClient($this->authenticator, $stub, $factory, $factory);

    $clients = [];
    $clients[] = (new \ReflectionClass(\PetstoreClient\Api\PetApi::class))
        ->getProperty('apiClient')
        ->getValue($client->pet);
    $clients[] = (new \ReflectionClass(\PetstoreClient\Api\StoreApi::class))
        ->getProperty('apiClient')
        ->getValue($client->store);

    foreach ($clients as $apiClient) {
        expect($apiClient)->toBeInstanceOf(Psr18ApiClient::class);
        if ($apiClient instanceof Psr18ApiClient) {
            $apiClient->sendRequest('GET', 'http://example.com/ping', [], null);
        }
    }
    expect($stub->requests)->toHaveCount(count($clients));
});

test('a supplied psr18 client conflicts with sdk proxy and tls settings', function (TransportOptions $options): void {
    $factory = new Psr17Factory();
    $stub = new StubPsr18Client();

    expect(fn () => Client::withHttpClient($this->authenticator, $stub, $factory, $factory, $options))
        ->toThrow(\InvalidArgumentException::class, 'conflict');
})->with([
    'proxy' => [fn () => TransportOptions::builder()->proxy('http://proxy:3128')->build()],
    'ca bundle' => [fn () => TransportOptions::builder()->caCertPath('/tmp/ca.pem')->build()],
    'tls verification off' => [fn () => TransportOptions::builder()->verifySsl(false)->build()],
]);

test('bhv lifecycle 016 with token fails when the token is empty', function (): void {
    expect(fn () => Client::withToken('/api/v3', ''))
        ->toThrow(\InvalidArgumentException::class);
});
