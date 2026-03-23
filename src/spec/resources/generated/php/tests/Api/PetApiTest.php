<?php

namespace PetstoreClient\Test\Api;

use PHPUnit\Framework\TestCase;
use PetstoreClient\Api\PetApi;
use PetstoreClient\Auth\BearerAuthenticator;
use PetstoreClient\Configuration;
use PetstoreClient\Models\ApiResponse;
use PetstoreClient\Models\Pet;
use PetstoreClient\Models\PetPassport;
use PetstoreClient\Models\PhotoMetadata;
use PetstoreClient\Models\SetPetAvatarThumbnailRequest;

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
        $config = Configuration::builder()
            ->baseUrl($baseUrl)
            ->defaultHeader('Authorization', 'Bearer test-token')
            ->build();
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

    public function testSetPetAvatar(): void
    {
        $tmpFile = tempnam(sys_get_temp_dir(), 'avatar');
        file_put_contents($tmpFile, "\xFF\xD8\xFF");
        $body = new \SplFileObject($tmpFile, 'r');

        $this->api->setPetAvatar(1, $body);

        $this->assertTrue(true);
        unlink($tmpFile);
    }

    public function testGetPetAvatar(): void
    {
        $result = $this->api->getPetAvatar(1);

        $this->assertNotNull($result);
        $this->assertIsString($result);
    }

    public function testGetPetAvatarThumbnail(): void
    {
        $result = $this->api->getPetAvatarThumbnail(1);

        $this->assertNotNull($result);
    }

    public function testSetPetAvatarThumbnail(): void
    {
        $request = new SetPetAvatarThumbnailRequest('iVBORw0KGgoAAAANSUhEUg==');

        $this->api->setPetAvatarThumbnail(1, $request);

        $this->assertTrue(true);
    }

    public function testUploadPetCertificate(): void
    {
        $tmpFile = tempnam(sys_get_temp_dir(), 'cert');
        file_put_contents($tmpFile, 'certificate-content');
        $file = new \SplFileObject($tmpFile, 'r');

        $result = $this->api->uploadPetCertificate(1, $file);

        $this->assertInstanceOf(ApiResponse::class, $result);
        unlink($tmpFile);
    }

    public function testUploadPetDocument(): void
    {
        $tmpFile = tempnam(sys_get_temp_dir(), 'doc');
        file_put_contents($tmpFile, 'document-content');
        $file = new \SplFileObject($tmpFile, 'r');

        $result = $this->api->uploadPetDocument(1, $file, 'vaccination_record', 'Annual checkup');

        $this->assertInstanceOf(ApiResponse::class, $result);
        unlink($tmpFile);
    }

    /**
     * @group skip
     * Prism does not validate multipart array fields correctly
     */
    public function testAddPetPhotos(): void
    {
        $this->markTestSkipped('Prism does not validate multipart array fields correctly');
    }

    public function testDownloadPetDocument(): void
    {
        $result = $this->api->downloadPetDocument(1, 1);

        $this->assertNotNull($result);
        $this->assertIsString($result);
    }

    /**
     * @group skip
     * Prism returns JSON for image content type
     */
    public function testGetPetPhoto(): void
    {
        $this->markTestSkipped('Prism returns JSON for image content type');
    }

    public function testGetPetPassport(): void
    {
        $result = $this->api->getPetPassport(1);

        $this->assertInstanceOf(PetPassport::class, $result);
    }
}
