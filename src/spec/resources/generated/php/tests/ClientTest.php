<?php

declare(strict_types=1);

use PetstoreClient\Auth\ApiKeyAuthenticator;
use PetstoreClient\Auth\ApiKeyLocation;
use PetstoreClient\Auth\BearerAuthenticator;
use PetstoreClient\Client;
use PetstoreClient\TransportOptions;

beforeEach(function (): void {
    $this->authenticator = new BearerAuthenticator('/api/v3', 'test-token');
});

test('construct with authenticator only', function (): void {
    $client = new Client($this->authenticator);

    expect($client)->toBeInstanceOf(Client::class);
});

test('construct with authenticator and null transport options', function (): void {
    // A null transport must fall back to the default builder output, so
    // constructing with an explicit null is equivalent to omitting it.
    $client = new Client($this->authenticator, null);

    expect($client)->toBeInstanceOf(Client::class);
});

test('construct with authenticator and transport options', function (): void {
    $transport = TransportOptions::builder()->build();

    $client = new Client($this->authenticator, $transport);

    expect($client)->toBeInstanceOf(Client::class);
});

test('bearer rejects crlf', function (): void {
    expect(fn () => new BearerAuthenticator('/api/v3', "tok\r\nInjected: yes"))
        ->toThrow(\InvalidArgumentException::class);
});

test('bearer rejects non ascii', function (): void {
    expect(fn () => new BearerAuthenticator('/api/v3', 'ñoño'))
        ->toThrow(\InvalidArgumentException::class);
});

test('bearer rejects empty token', function (): void {
    // An empty / whitespace-only token would emit "Authorization: Bearer "
    // (no credential), so it must be rejected at construction.
    expect(fn () => new BearerAuthenticator('/api/v3', ''))
        ->toThrow(\InvalidArgumentException::class);
    expect(fn () => new BearerAuthenticator('/api/v3', '   '))
        ->toThrow(\InvalidArgumentException::class);
});

test('api key header rejects crlf and non ascii', function (): void {
    // RFC 7230 §3.2.6 — header field-value is HTAB / SP / VCHAR.
    // ApiKeyAuthenticator's HEADER location must reject anything
    // outside printable ASCII + TAB to prevent header injection
    // (\r\n) and silent UTF-8 mangling that varies per HTTP lib.
    expect(fn () => new ApiKeyAuthenticator('/api/v3', 'X-Api-Key', "abc\r\nInjected: yes", ApiKeyLocation::HEADER))
        ->toThrow(\InvalidArgumentException::class);
});

test('api key header rejects non ascii', function (): void {
    expect(fn () => new ApiKeyAuthenticator('/api/v3', 'X-Api-Key', 'kéy', ApiKeyLocation::HEADER))
        ->toThrow(\InvalidArgumentException::class);
});

test('api key query allows non ascii', function (): void {
    // Non-header locations accept arbitrary chars.
    $auth = new ApiKeyAuthenticator('/api/v3', 'api_key', 'kéy', ApiKeyLocation::QUERY);
    expect($auth->getQueryParams())->toBe(['api_key' => 'kéy']);
});

test('api groups are accessible', function (): void {
    $client = new Client($this->authenticator);

    expect($client->pet)->toBeInstanceOf(\PetstoreClient\Api\PetApi::class);
    expect($client->store)->toBeInstanceOf(\PetstoreClient\Api\StoreApi::class);
});
