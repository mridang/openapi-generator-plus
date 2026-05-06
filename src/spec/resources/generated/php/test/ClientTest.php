<?php

declare(strict_types=1);

namespace PetstoreClient\Test;

use PHPUnit\Framework\TestCase;
use PetstoreClient\Client;
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

    public function testApiGroupsAreAccessible(): void
    {
        $client = new Client($this->authenticator);

        $this->assertInstanceOf(\PetstoreClient\Api\PetApi::class, $client->pet);
        $this->assertInstanceOf(\PetstoreClient\Api\StoreApi::class, $client->store);
    }
}
