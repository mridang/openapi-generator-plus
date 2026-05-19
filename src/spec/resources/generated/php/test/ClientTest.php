<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PHPUnit\Framework\TestCase;
use PetstoreClient\Client;
use PetstoreClient\Auth\ApiKeyAuthenticator;
use PetstoreClient\Auth\ApiKeyLocation;
use PetstoreClient\Auth\BearerAuthenticator;
use PetstoreClient\TransportOptions;

class ClientTest extends TestCase
{
    private BearerAuthenticator $authenticator;

    protected function setUp(): void
    {
        $this->authenticator = new BearerAuthenticator('/api/v3', 'test-token');
    }

    public function testConstructWithAuthenticatorOnly(): void
    {
        $client = new Client($this->authenticator);

        $this->assertInstanceOf(Client::class, $client);
    }

    public function testConstructWithAuthenticatorAndTransportOptions(): void
    {
        $transport = TransportOptions::builder()->build();

        $client = new Client($this->authenticator, $transport);

        $this->assertInstanceOf(Client::class, $client);
    }

    public function testApiKeyHeaderRejectsCrlfAndNonAscii(): void
    {
        // RFC 7230 §3.2.6 — header field-value is HTAB / SP / VCHAR.
        // ApiKeyAuthenticator's HEADER location must reject anything
        // outside printable ASCII + TAB to prevent header injection
        // (\r\n) and silent UTF-8 mangling that varies per HTTP lib.
        $this->expectException(\InvalidArgumentException::class);
        new ApiKeyAuthenticator('/api/v3', 'X-Api-Key', "abc\r\nInjected: yes", ApiKeyLocation::HEADER);
    }

    public function testApiKeyHeaderRejectsNonAscii(): void
    {
        $this->expectException(\InvalidArgumentException::class);
        new ApiKeyAuthenticator('/api/v3', 'X-Api-Key', 'kéy', ApiKeyLocation::HEADER);
    }

    public function testApiKeyQueryAllowsNonAscii(): void
    {
        // Non-header locations accept arbitrary chars.
        $auth = new ApiKeyAuthenticator('/api/v3', 'api_key', 'kéy', ApiKeyLocation::QUERY);
        $this->assertSame(['api_key' => 'kéy'], $auth->getQueryParams());
    }

    public function testApiGroupsAreAccessible(): void
    {
        $client = new Client($this->authenticator);

        $this->assertInstanceOf(\PetstoreClient\Api\PetApi::class, $client->pet);
        $this->assertInstanceOf(\PetstoreClient\Api\StoreApi::class, $client->store);
    }
}
