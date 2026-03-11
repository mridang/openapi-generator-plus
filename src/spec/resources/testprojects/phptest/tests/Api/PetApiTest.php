<?php

namespace PetstoreClient\Test\Api;

use PHPUnit\Framework\TestCase;
use PetstoreClient\Api\PetApi;
use PetstoreClient\Auth\BearerAuthenticator;
use PetstoreClient\Configuration;
use PetstoreClient\Models\Pet;

/**
 * Integration tests for the Pet API endpoints.
 */
class PetApiTest extends TestCase
{
    private PetApi $api;
    private BearerAuthenticator $auth;

    protected function setUp(): void
    {
        $baseUrl = getenv('API_BASE_URL') ?: 'http://localhost:4010';
        $config = Configuration::getDefaultConfiguration()
            ->setBaseUrl($baseUrl);
        $config->setDefaultHeader('Authorization', 'Bearer test-token');
        $this->api = new PetApi(config: $config);
        $this->auth = new BearerAuthenticator($baseUrl, 'test-token');
    }

    public function testAddPet(): void
    {
        $pet = new Pet(name: 'TestDog', photoUrls: ['http://example.com/photo.jpg']);
        $pet->id = 12345;
        $pet->status = 'available';

        $result = $this->api->addPet($this->auth, $pet);

        $this->assertInstanceOf(Pet::class, $result);
        $this->assertNotNull($result->name);
    }

    public function testFindPetsByStatus(): void
    {
        $result = $this->api->findPetsByStatus('available');

        $this->assertIsArray($result);
        $this->assertNotEmpty($result);
        $this->assertInstanceOf(Pet::class, $result[0]);
    }

    public function testGetPetById(): void
    {
        $result = $this->api->getPetById(1);

        $this->assertInstanceOf(Pet::class, $result);
        $this->assertNotNull($result->id);
        $this->assertNotNull($result->name);
    }

    public function testUpdatePet(): void
    {
        $pet = new Pet(name: 'UpdatedDog', photoUrls: ['http://example.com/updated.jpg']);
        $pet->id = 1;
        $pet->status = 'pending';

        $result = $this->api->updatePet(1, $pet);

        $this->assertInstanceOf(Pet::class, $result);
    }

    public function testDeletePet(): void
    {
        $this->api->deletePet($this->auth, 1);

        $this->assertTrue(true);
    }
}
