<?php

namespace PetstoreClient\Test\Api;

use PHPUnit\Framework\TestCase;
use PetstoreClient\Api\PetApi;
use PetstoreClient\Configuration;
use PetstoreClient\Models\Pet;

/**
 * Integration tests for the Pet API endpoints.
 */
class PetApiTest extends TestCase
{
    private PetApi $api;

    protected function setUp(): void
    {
        $config = Configuration::getDefaultConfiguration()
            ->setBaseUrl(getenv('API_BASE_URL') ?: 'http://localhost:4010');
        $this->api = new PetApi(config: $config);
    }

    public function testAddPet(): void
    {
        $pet = new Pet();
        $pet->setId(12345);
        $pet->setName('TestDog');
        $pet->setPhotoUrls(['http://example.com/photo.jpg']);
        $pet->setStatus('available');

        $result = $this->api->addPet($pet);

        $this->assertInstanceOf(Pet::class, $result);
        $this->assertNotNull($result->getName());
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
        $this->assertNotNull($result->getId());
        $this->assertNotNull($result->getName());
    }

    public function testUpdatePet(): void
    {
        $pet = new Pet();
        $pet->setId(1);
        $pet->setName('UpdatedDog');
        $pet->setPhotoUrls(['http://example.com/updated.jpg']);
        $pet->setStatus('pending');

        $result = $this->api->updatePet(1, $pet);

        $this->assertInstanceOf(Pet::class, $result);
    }

    public function testDeletePet(): void
    {
        $this->api->deletePet(1);

        $this->assertTrue(true);
    }
}
