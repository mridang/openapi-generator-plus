<?php

declare(strict_types=1);

namespace PetstoreClient\Test\Api;

use PetstoreClient\Api\Options\AddPetOptions;
use PetstoreClient\Api\Options\AddPetPhotosOptions;
use PetstoreClient\Api\Options\DeletePetOptions;
use PetstoreClient\Api\Options\FindPetsByStatusOptions;
use PetstoreClient\Api\Options\GetPetTagOptions;
use PetstoreClient\Api\Options\UploadPetCertificateOptions;
use PetstoreClient\Api\Options\UploadPetDocumentOptions;
use PetstoreClient\Api\PetApi;
use PetstoreClient\ApiException;
use PetstoreClient\Auth\BearerAuthenticator;
use PetstoreClient\Configuration;
use PetstoreClient\Errors\NotFoundException;
use PetstoreClient\Errors\ServerException;
use PetstoreClient\Models\ApiResponse as ApiResponseModel;
use PetstoreClient\Models\Pet;
use PetstoreClient\Models\PetPassport;
use PetstoreClient\Models\PetStatusEnum;
use PetstoreClient\Models\Photo;
use PetstoreClient\Models\PhotoMetadata;
use PetstoreClient\Models\SetPetAvatarThumbnailRequest;

/**
 * Integration tests for the Pet API endpoints.
 */
beforeEach(function (): void {
    $baseUrl = getenv('API_BASE_URL') ?: 'http://localhost:4010';
    $config = Configuration::builder()
        ->baseUrl($baseUrl)
        ->defaultHeader('Authorization', 'Bearer test-token')
        ->build();
    $this->api = new PetApi(config: $config);
    $this->auth = new BearerAuthenticator($baseUrl, 'test-token');
});

function newPetApiForMock(int $statusCode, string $contentType, string $body): PetApi
{
    $client = new PetMockApiClient($statusCode, $body, $contentType);
    $config = Configuration::builder()
        ->baseUrl('http://localhost:9999')
        ->build();
    return new PetApi(apiClient: $client, config: $config);
}

// -- Integration tests via Chasm --

test('add pet', function (): void {
    $pet = new Pet(name: 'TestDog', photoUrls: new \Ds\Set(['http://example.com/photo.jpg']));
    $pet->id = 12345;
    $pet->status = PetStatusEnum::AVAILABLE;

    $result = $this->api->addPet($pet, new AddPetOptions(auth: $this->auth));

    expect($result)->toBeInstanceOf(Pet::class);
});

test('add pet with http info exposes status and headers', function (): void {
    $pet = new Pet(name: 'TestDog', photoUrls: new \Ds\Set(['http://example.com/photo.jpg']));
    $pet->id = 67890;
    $pet->status = PetStatusEnum::AVAILABLE;

    $result = $this->api->addPetWithHttpInfo($pet, new AddPetOptions(auth: $this->auth));

    expect($result->statusCode)->toBe(200);
    expect($result->data)->toBeInstanceOf(Pet::class);
    expect($result->headers)->not->toBeEmpty();
});

test('get pet by id', function (): void {
    $result = $this->api->getPetById(1);

    expect($result)->toBeInstanceOf(Pet::class);
});

test('get pet by id with http info exposes status and data', function (): void {
    $result = $this->api->getPetByIdWithHttpInfo(1);

    expect($result->statusCode)->toBe(200);
    expect($result->data)->toBeInstanceOf(Pet::class);
});

test('update pet with http info exposes status', function (): void {
    $pet = new Pet(name: 'UpdatedDog', photoUrls: new \Ds\Set(['http://example.com/updated.jpg']));
    $pet->id = 1;
    $pet->status = PetStatusEnum::PENDING;

    $result = $this->api->updatePetWithHttpInfo(1, $pet);

    expect($result->statusCode)->toBeGreaterThanOrEqual(200);
    expect($result->statusCode)->toBeLessThan(300);
});

test('delete pet with http info exposes status', function (): void {
    $result = $this->api->deletePetWithHttpInfo(1, new DeletePetOptions(auth: $this->auth));

    expect($result->statusCode)->toBeGreaterThanOrEqual(200);
    expect($result->statusCode)->toBeLessThan(300);
});

test('find pets by status with http info exposes status', function (): void {
    $result = $this->api->findPetsByStatusWithHttpInfo(new FindPetsByStatusOptions('available'));

    expect($result->statusCode)->toBe(200);
});

test('get pet passport with http info exposes status', function (): void {
    $result = $this->api->getPetPassportWithHttpInfo(1);

    expect($result->statusCode)->toBe(200);
    expect($result->data)->toBeInstanceOf(PetPassport::class);
});

test('find pets by status', function (): void {
    $result = $this->api->findPetsByStatus(new FindPetsByStatusOptions('available'));

    // Phase 2 PHP type-surface: OAS `array<Pet>` now maps to `\Ds\Vector<Pet>`.
    // DsAwareObjectNormalizer wraps Symfony's collection-iteration plain
    // array into the declared \Ds\Vector container while preserving the
    // already-denormalized Pet instances inside.
    expect($result)->toBeInstanceOf(\Ds\Vector::class);
    expect($result->count())->toBeGreaterThan(0);
    expect($result->first())->toBeInstanceOf(Pet::class);
});

test('get pet passport', function (): void {
    $result = $this->api->getPetPassport(1);

    expect($result)->toBeInstanceOf(PetPassport::class);
});

test('update pet', function (): void {
    $pet = new Pet(name: 'UpdatedDog', photoUrls: new \Ds\Set(['http://example.com/updated.jpg']));
    $pet->id = 1;
    $pet->status = PetStatusEnum::PENDING;

    $result = $this->api->updatePet(1, $pet);

    expect($result)->toBeInstanceOf(Pet::class);
});

test('delete pet', function (): void {
    $this->api->deletePet(1, new DeletePetOptions(auth: $this->auth));

    expect(true)->toBeTrue();
});

test('set pet avatar', function (): void {
    $tmpFile = tempnam(sys_get_temp_dir(), 'avatar');
    file_put_contents($tmpFile, "\xFF\xD8\xFF");
    $body = new \SplFileObject($tmpFile, 'r');

    $this->api->setPetAvatar(1, $body);

    expect(true)->toBeTrue();
    unlink($tmpFile);
});

test('get pet avatar', function (): void {
    $result = $this->api->getPetAvatar(1);

    expect($result)->not->toBeNull();
    expect($result)->toBeString();
});

test('get pet avatar returns decoded bytes not base64 string', function (): void {
    /* The transport base64-encodes binary response bodies for transit. A
     * binary operation must hand back the DECODED raw bytes, so re-encoding
     * the returned value as base64 must reproduce the raw (still-encoded)
     * body exposed on the ApiResult. If the decode regressed and the base64
     * STRING leaked through, base64_encode($data) would double-encode and
     * never equal $rawBody. */
    $result = $this->api->getPetAvatarWithHttpInfo(1);

    expect($result->data)->toBeString();
    expect($result->rawBody)->not->toBeNull();
    expect(base64_encode($result->data))->toBe($result->rawBody);
});

test('get pet avatar thumbnail', function (): void {
    $result = $this->api->getPetAvatarThumbnail(1);

    expect($result)->not->toBeNull();
});

test('set pet avatar thumbnail', function (): void {
    $request = new SetPetAvatarThumbnailRequest('iVBORw0KGgoAAAANSUhEUg==');

    $this->api->setPetAvatarThumbnail(1, $request);

    expect(true)->toBeTrue();
});

test('upload pet certificate', function (): void {
    $tmpFile = tempnam(sys_get_temp_dir(), 'cert');
    file_put_contents($tmpFile, 'certificate-content');
    $file = new \SplFileObject($tmpFile, 'r');

    $result = $this->api->uploadPetCertificate(1, new UploadPetCertificateOptions($file));

    expect($result)->toBeInstanceOf(ApiResponseModel::class);
    unlink($tmpFile);
});

test('upload pet document', function (): void {
    $tmpFile = tempnam(sys_get_temp_dir(), 'doc');
    file_put_contents($tmpFile, 'document-content');
    $file = new \SplFileObject($tmpFile, 'r');

    $options = new UploadPetDocumentOptions($file, 'vaccination_record', 'Annual checkup');
    $result = $this->api->uploadPetDocument(1, $options);

    expect($result)->toBeInstanceOf(ApiResponseModel::class);
    unlink($tmpFile);
});

test('add pet photos', function (): void {
    $tmpFile = tempnam(sys_get_temp_dir(), 'photo');
    file_put_contents($tmpFile, 'photo-content');
    $file = new \SplFileObject($tmpFile, 'r');

    $metadata = new PhotoMetadata(caption: 'Test photo', isPrimary: true);
    $result = $this->api->addPetPhotos(1, new AddPetPhotosOptions([$file], $metadata));

    // Phase 2 PHP type-surface: OAS `array<Photo>` now maps to `\Ds\Vector<Photo>`.
    // DsAwareObjectNormalizer wraps the inner-denormalized Photos into the
    // typed Vector container; see `find pets by status` for full notes.
    expect($result)->toBeInstanceOf(\Ds\Vector::class);
    expect($result->count())->toBeGreaterThan(0);
    expect($result->first())->toBeInstanceOf(Photo::class);
    unlink($tmpFile);
});

test('download pet document', function (): void {
    $result = $this->api->downloadPetDocument(1, 1);

    expect($result)->not->toBeNull();
    expect($result)->toBeString();
});

test('get pet photo', function (): void {
    $result = $this->api->getPetPhoto(1, 1);

    expect($result)->not->toBeNull();
    expect($result)->toBeString();
});

test('get external pet info uses per operation server url', function (): void {
    test()->markTestSkipped('Per-operation server URL cannot be validated against a local mock server');
});

test('get pet tag sends styled parameters', function (): void {
    $result = $this->api->getPetTag(5, 'cute', new GetPetTagOptions(colors: ['blue', 'black'], sizes: ['S', 'M']));

    expect($result)->not->toBeNull();
});

// -- Mock-based error handling tests --

test('error handling not found', function (): void {
    $api = newPetApiForMock(404, 'application/json', '{"message":"Pet not found"}');

    expect(fn () => $api->getPetById(99999))->toThrow(NotFoundException::class);
});

test('error handling server error', function (): void {
    $api = newPetApiForMock(500, 'application/json', '{"message":"Internal server error"}');

    expect(fn () => $api->getPetById(1))->toThrow(ServerException::class);
});

test('empty body for body returning op throws api exception', function (): void {
    /* getPetById declares a non-void return type. A 2xx with an empty body
     * is a contract violation, so the convenience method must throw the
     * SDK's typed ApiException instead of returning a silent null. */
    $api = newPetApiForMock(200, 'application/json', '');

    expect(fn (): mixed => $api->getPetById(1))->toThrow(ApiException::class);
});

// -- Mock-based binary download test --

test('download binary mock', function (): void {
    $binaryData = "\x89\x50\x4E\x47\x0D\x0A\x1A\x0A";
    $api = newPetApiForMock(200, 'application/octet-stream', $binaryData);

    $result = $api->getPetAvatar(1);

    expect($result)->not->toBeNull();
    expect($result)->toBe($binaryData);
});

test('upload multipart mock', function (): void {
    $apiResponse = '{"code":200,"type":"ok","message":"upload successful"}';
    $api = newPetApiForMock(200, 'application/json', $apiResponse);

    $tmpFile = tempnam(sys_get_temp_dir(), 'cert');
    file_put_contents($tmpFile, 'certificate-content');
    $file = new \SplFileObject($tmpFile, 'r');

    $result = $api->uploadPetCertificate(1, new UploadPetCertificateOptions($file));

    expect($result)->not->toBeNull();
    unlink($tmpFile);
});

/**
 * Builds a PetApi backed by a header-capturing fake client plus the captured
 * header bag, so tests can assert which credentials reached the wire.
 *
 * @return array{0: PetApi, 1: \stdClass}
 */
function newHeaderCapturingPetApi(): array
{
    $captured = new \stdClass();
    /** @var array<string, string> $hdrs */
    $hdrs = [];
    $captured->headers = $hdrs;

    $client = new class ($captured) implements \PetstoreClient\ApiClient {
        public function __construct(private readonly \stdClass $captured)
        {
        }

        public function sendRequest(string $method, string $url, array $headers, mixed $body, bool $noRedirect = false): \PetstoreClient\ApiResponse
        {
            $this->captured->headers = $headers;
            return new \PetstoreClient\ApiResponse(
                200,
                '{"id":1,"name":"x","photoUrls":[]}',
                ['Content-Type' => 'application/json']
            );
        }
    };

    $config = Configuration::builder()
        ->baseUrl('http://localhost:9999')
        ->defaultHeader('Authorization', 'Bearer default-token')
        ->build();

    return [new PetApi(apiClient: $client, config: $config), $captured];
}

test('auth supplied inside options wins on the wire', function (): void {
    // The per-operation authenticator now travels inside the Options object.
    // The default header carries one token; the Options-borne authenticator
    // carries a different one and must win on the outgoing request.
    [$api, $captured] = newHeaderCapturingPetApi();
    $perCallAuth = new BearerAuthenticator('http://localhost:9999', 'per-call-token');

    $pet = new Pet(name: 'OverrideDog', photoUrls: new \Ds\Set(['http://example.com/p.jpg']));
    $pet->id = 1;
    $api->addPet($pet, new AddPetOptions(auth: $perCallAuth));

    expect($captured->headers['Authorization'] ?? null)->toBe('Bearer per-call-token');
});

test('auth omitted from options falls back to configuration credentials', function (): void {
    // With no Options (or an Options carrying a null auth) the operation must
    // fall back to the client's configured credentials — here the default
    // Authorization header set on the Configuration.
    [$api, $captured] = newHeaderCapturingPetApi();

    $pet = new Pet(name: 'DefaultDog', photoUrls: new \Ds\Set(['http://example.com/p.jpg']));
    $pet->id = 1;
    $api->addPet($pet);

    expect($captured->headers['Authorization'] ?? null)->toBe('Bearer default-token');
});

test('unsecured operation options has no auth field', function (): void {
    // getPetById is unsecured, so it takes no Options object at all and there
    // is no GetPetByIdOptions class minted for it — confirming unsecured
    // operations never gain an auth field.
    expect(class_exists('\\' . PetApi::class))->toBeTrue();
    expect(class_exists('PetstoreClient\\Api\\Options\\GetPetByIdOptions'))->toBeFalse();

    // The authed addPet operation, by contrast, mints an all-optional Options
    // class that DOES expose a nullable auth property.
    $reflection = new \ReflectionClass(AddPetOptions::class);
    expect($reflection->hasProperty('auth'))->toBeTrue();
    $authProp = $reflection->getProperty('auth');
    $type = $authProp->getType();
    expect($type)->toBeInstanceOf(\ReflectionNamedType::class);
    /** @var \ReflectionNamedType $type */
    expect($type->allowsNull())->toBeTrue();
});

test('options class is immutable: construction works but property writes throw', function (): void {
    // The per-operation Options classes are immutable value objects:
    // properties are constructor-promoted public readonly. Construction with
    // an auth credential works, the value is readable, but any attempt to
    // mutate a readonly property after construction must throw \Error.
    $auth = new BearerAuthenticator('http://localhost:9999', 'token');
    $opts = new AddPetOptions(auth: $auth);
    expect($opts->auth)->toBe($auth);

    expect(fn () => $opts->auth = new BearerAuthenticator('http://localhost:9999', 'other'))
        ->toThrow(\Error::class);
});
