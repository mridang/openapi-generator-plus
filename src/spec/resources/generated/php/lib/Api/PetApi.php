<?php

namespace PetstoreClient\Api;

use PetstoreClient\ApiClient;
use PetstoreClient\ApiException;
use PetstoreClient\ApiResult;
use PetstoreClient\Configuration;
use PetstoreClient\DefaultApiClient;
use PetstoreClient\ValueSerializer;
use PetstoreClient\Auth\Authenticator;

/**
 * PetApi provides methods for the Pet API group.
 * Everything about your Pets
 * @see https://example.com/docs/pets Find out more about pets
 */

/**
 * Server type for the getExternalPetInfo operation.
 */
abstract class GetExternalPetInfoServer
{
    abstract public function getUrl(): string;
}


final class GetExternalPetInfoServerServer0 extends GetExternalPetInfoServer
{
    public function getUrl(): string
    {
        return 'https://external-api.example.com/v1';
    }
}

/**
 * Server type for the getMultiServerPetInfo operation.
 */
abstract class GetMultiServerPetInfoServer
{
    abstract public function getUrl(): string;
}



enum GetMultiServerPetInfoServerRegion: string
{
    case US = 'us';
    case EU = 'eu';
    case AP = 'ap';
}


/**
 * Primary
 */
final class GetMultiServerPetInfoServerPrimary extends GetMultiServerPetInfoServer
{
    public function getUrl(): string
    {
        return 'https://primary.example.com/v1';
    }
}

/**
 * Regional
 */
final class GetMultiServerPetInfoServerRegional extends GetMultiServerPetInfoServer
{
    private readonly GetMultiServerPetInfoServerRegion $region;

    public function __construct(GetMultiServerPetInfoServerRegion $region)
    {
        $this->region = $region;
    }

    public function getUrl(): string
    {
        $url = 'https://{region}.example.com/v1';
        return str_replace('{' . 'region' . '}', $this->region->value, $url);
    }
}

/**
 * Server type for the getStagingPetInfo operation.
 */
abstract class GetStagingPetInfoServer
{
    abstract public function getUrl(): string;
}


enum GetStagingPetInfoServerEnvironment: string
{
    case STAGING = 'staging';
    case SANDBOX = 'sandbox';
}


enum GetStagingPetInfoServerVersion: string
{
    case V2 = 'v2';
    case V3 = 'v3';
}


/**
 * Staging server
 */
final class GetStagingPetInfoServerStagingServer extends GetStagingPetInfoServer
{
    private readonly GetStagingPetInfoServerEnvironment $environment;
    private readonly GetStagingPetInfoServerVersion $version;

    public function __construct(GetStagingPetInfoServerEnvironment $environment, GetStagingPetInfoServerVersion $version)
    {
        $this->environment = $environment;
        $this->version = $version;
    }

    public function getUrl(): string
    {
        $url = 'https://{environment}.example.com/api/{version}';
        $url = str_replace('{' . 'environment' . '}', $this->environment->value, $url);
        return str_replace('{' . 'version' . '}', $this->version->value, $url);
    }
}

class PetApi extends BaseApi
{
    /**
     * Add a new pet to the store
     * @param Authenticator $auth Authenticator for this operation
     * @param \PetstoreClient\Models\Pet $pet Create a new pet in the store
     * @return \PetstoreClient\Models\Pet
     * @throws ApiException
     */
    public function addPet(Authenticator $auth, \PetstoreClient\Models\Pet $pet)
    {
        /** @var \PetstoreClient\Models\Pet $result */
        $result = $this->addPetWithHttpInfo($auth, $pet)->data;
        return $result;
    }

    /**
     * @param \PetstoreClient\Models\Pet $pet Create a new pet in the store
     * @return ApiResult<\PetstoreClient\Models\Pet>
     * @throws ApiException
     */
    public function addPetWithHttpInfo(Authenticator $auth, \PetstoreClient\Models\Pet $pet): ApiResult
    {
        $path = '/pet';
        $queryParams = [];
        $headerParams = [];
        $requestBody = $pet;

        /** @var ApiResult<\PetstoreClient\Models\Pet> $result */
        $result = $this->invokeApiForResult(
            'POST',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Pet',
            $auth
        );
        return $result;
    }

    /**
     * Add photos to the pet&#39;s gallery
     * Uploads one or more photos with structured metadata. The metadata part is serialised as JSON within the multipart body.
     * @param \SplFileObject[] $files
     * @return \PetstoreClient\Models\Photo[]
     * @throws ApiException
     */
    public function addPetPhotos(int $petId, array $files, \PetstoreClient\Models\PhotoMetadata $metadata)
    {
        /** @var \PetstoreClient\Models\Photo[] $result */
        $result = $this->addPetPhotosWithHttpInfo($petId, $files, $metadata)->data;
        return $result;
    }

    /**
     * @param \SplFileObject[] $files
     * @return ApiResult<\PetstoreClient\Models\Photo[]>
     * @throws ApiException
     */
    public function addPetPhotosWithHttpInfo(int $petId, array $files, \PetstoreClient\Models\PhotoMetadata $metadata): ApiResult
    {
        $path = '/pet/{petId}/photos';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = [];
        $requestBody['files'] = $files;
        $requestBody['metadata'] = $metadata;

        /** @var ApiResult<\PetstoreClient\Models\Photo[]> $result */
        $result = $this->invokeApiForResult(
            'POST',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'multipart/form-data',
            '\PetstoreClient\Models\Photo[]',
        );
        return $result;
    }

    /**
     * Deletes a pet
     * @param Authenticator $auth Authenticator for this operation
     * @param int $petId Pet id to delete
     * @throws ApiException
     */
    public function deletePet(Authenticator $auth, int $petId): void
    {
        $this->deletePetWithHttpInfo($auth, $petId);
    }

    /**
     * @param int $petId Pet id to delete
     * @return ApiResult<null>
     * @throws ApiException
     */
    public function deletePetWithHttpInfo(Authenticator $auth, int $petId): ApiResult
    {
        $path = '/pet/{petId}';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<null> $result */
        $result = $this->invokeApiForResult(
            'DELETE',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            [],
            'application/json',
            null,
            $auth
        );
        return $result;
    }

    /**
     * Download a vet document
     * Returns the raw document bytes as an octet-stream. The original MIME type is communicated via the Content-Type response header.
     * @return \SplFileObject
     * @throws ApiException
     */
    public function downloadPetDocument(int $petId, int $documentId)
    {
        /** @var \SplFileObject $result */
        $result = $this->downloadPetDocumentWithHttpInfo($petId, $documentId)->data;
        return $result;
    }

    /**
     * @return ApiResult<\SplFileObject>
     * @throws ApiException
     */
    public function downloadPetDocumentWithHttpInfo(int $petId, int $documentId): ApiResult
    {
        $path = '/pet/{petId}/documents/{documentId}';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('documentId', $documentId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'documentId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<\SplFileObject> $result */
        $result = $this->invokeApiForResult(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/octet-stream'],
            'application/json',
            '\SplFileObject',
        );
        return $result;
    }

    /**
     * Finds Pets by status
     * @param string|null $status Status values that need to be considered for filter
     * @param array<string,string>|null $filter Filter criteria as key-value pairs
     * @return \PetstoreClient\Models\Pet[]
     * @throws ApiException
     * @deprecated This operation is deprecated.
     * @see https://example.com/docs/filtering Find out more about filtering
     */
    public function findPetsByStatus(?string $status = null, ?array $filter = null)
    {
        /** @var \PetstoreClient\Models\Pet[] $result */
        $result = $this->findPetsByStatusWithHttpInfo($status, $filter)->data;
        return $result;
    }

    /**
     * @param string|null $status Status values that need to be considered for filter
     * @param array<string,string>|null $filter Filter criteria as key-value pairs
     * @return ApiResult<\PetstoreClient\Models\Pet[]>
     * @throws ApiException
     */
    public function findPetsByStatusWithHttpInfo(?string $status = null, ?array $filter = null): ApiResult
    {
        $path = '/pet/findByStatus';
        $queryParams = [];
        if ($status !== null) {
            $queryParams['status'] = ValueSerializer::serializeStyled('status', $status, 'query', 'string', null, 'form', true);
        }
        if ($filter !== null) {
            $queryParams = array_merge($queryParams, ValueSerializer::serializeDeepObject('filter', $filter));
        }
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<\PetstoreClient\Models\Pet[]> $result */
        $result = $this->invokeApiForResult(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Pet[]',
        );
        return $result;
    }

    /**
     * Get external pet info
     * @return \PetstoreClient\Models\Pet
     * @throws ApiException
     */
    public function getExternalPetInfo(int $petId, ?GetExternalPetInfoServer $server = null)
    {
        /** @var \PetstoreClient\Models\Pet $result */
        $result = $this->getExternalPetInfoWithHttpInfo($petId, $server)->data;
        return $result;
    }

    /**
     * @return ApiResult<\PetstoreClient\Models\Pet>
     * @throws ApiException
     */
    public function getExternalPetInfoWithHttpInfo(int $petId, ?GetExternalPetInfoServer $server = null): ApiResult
    {
        $path = '/pet/{petId}/external';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $serverUrl = $server instanceof \PetstoreClient\Api\GetExternalPetInfoServer ? $server->getUrl() : 'https://external-api.example.com/v1';
        if (str_starts_with($serverUrl, 'http://') || str_starts_with($serverUrl, 'https://')) {
            $path = $serverUrl . $path;
        }
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<\PetstoreClient\Models\Pet> $result */
        $result = $this->invokeApiForResult(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Pet',
        );
        return $result;
    }

    /**
     * Get multi-server pet info
     * @return \PetstoreClient\Models\Pet
     * @throws ApiException
     */
    public function getMultiServerPetInfo(int $petId, ?GetMultiServerPetInfoServer $server = null)
    {
        /** @var \PetstoreClient\Models\Pet $result */
        $result = $this->getMultiServerPetInfoWithHttpInfo($petId, $server)->data;
        return $result;
    }

    /**
     * @return ApiResult<\PetstoreClient\Models\Pet>
     * @throws ApiException
     */
    public function getMultiServerPetInfoWithHttpInfo(int $petId, ?GetMultiServerPetInfoServer $server = null): ApiResult
    {
        $path = '/pet/{petId}/multi';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $serverUrl = $server instanceof \PetstoreClient\Api\GetMultiServerPetInfoServer ? $server->getUrl() : 'https://primary.example.com/v1';
        if (str_starts_with($serverUrl, 'http://') || str_starts_with($serverUrl, 'https://')) {
            $path = $serverUrl . $path;
        }
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<\PetstoreClient\Models\Pet> $result */
        $result = $this->invokeApiForResult(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Pet',
        );
        return $result;
    }

    /**
     * Get the pet&#39;s profile photo
     * Returns the raw image bytes of the pet&#39;s current avatar.
     * @return \SplFileObject
     * @throws ApiException
     */
    public function getPetAvatar(int $petId)
    {
        /** @var \SplFileObject $result */
        $result = $this->getPetAvatarWithHttpInfo($petId)->data;
        return $result;
    }

    /**
     * @return ApiResult<\SplFileObject>
     * @throws ApiException
     */
    public function getPetAvatarWithHttpInfo(int $petId): ApiResult
    {
        $path = '/pet/{petId}/avatar';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<\SplFileObject> $result */
        $result = $this->invokeApiForResult(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['image/jpeg', 'image/png'],
            'application/json',
            '\SplFileObject',
        );
        return $result;
    }

    /**
     * Get the pet&#39;s avatar thumbnail as base64
     * Returns a compact base64-encoded thumbnail suitable for embedding directly in mobile UI without a separate image request.
     * @return string
     * @throws ApiException
     */
    public function getPetAvatarThumbnail(int $petId)
    {
        /** @var string $result */
        $result = $this->getPetAvatarThumbnailWithHttpInfo($petId)->data;
        return $result;
    }

    /**
     * @return ApiResult<string>
     * @throws ApiException
     */
    public function getPetAvatarThumbnailWithHttpInfo(int $petId): ApiResult
    {
        $path = '/pet/{petId}/avatar/thumbnail';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<string> $result */
        $result = $this->invokeApiForResult(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'application/json',
            'string',
        );
        return $result;
    }

    /**
     * Find pet by ID
     * Returns a single pet
     * @param int $petId ID of pet to return
     * @return \PetstoreClient\Models\Pet
     * @throws ApiException
     * @deprecated This operation is deprecated.
     */
    public function getPetById(int $petId)
    {
        /** @var \PetstoreClient\Models\Pet $result */
        $result = $this->getPetByIdWithHttpInfo($petId)->data;
        return $result;
    }

    /**
     * @param int $petId ID of pet to return
     * @return ApiResult<\PetstoreClient\Models\Pet>
     * @throws ApiException
     */
    public function getPetByIdWithHttpInfo(int $petId): ApiResult
    {
        $path = '/pet/{petId}';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<\PetstoreClient\Models\Pet> $result */
        $result = $this->invokeApiForResult(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Pet',
        );
        return $result;
    }

    /**
     * Get the pet&#39;s passport
     * Returns a single JSON document combining the pet&#39;s profile with an embedded base64 thumbnail and base64-encoded scans of each passport page, suitable for mobile clients that prefer a single-request workflow.
     * @return \PetstoreClient\Models\PetPassport
     * @throws ApiException
     */
    public function getPetPassport(int $petId)
    {
        /** @var \PetstoreClient\Models\PetPassport $result */
        $result = $this->getPetPassportWithHttpInfo($petId)->data;
        return $result;
    }

    /**
     * @return ApiResult<\PetstoreClient\Models\PetPassport>
     * @throws ApiException
     */
    public function getPetPassportWithHttpInfo(int $petId): ApiResult
    {
        $path = '/pet/{petId}/passport';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<\PetstoreClient\Models\PetPassport> $result */
        $result = $this->invokeApiForResult(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\PetPassport',
        );
        return $result;
    }

    /**
     * Get a photo or its metadata
     * Returns the raw image bytes or JSON metadata depending on the Accept header sent by the client.
     * @return \SplFileObject
     * @throws ApiException
     */
    public function getPetPhoto(int $petId, int $photoId)
    {
        /** @var \SplFileObject $result */
        $result = $this->getPetPhotoWithHttpInfo($petId, $photoId)->data;
        return $result;
    }

    /**
     * @return ApiResult<\SplFileObject>
     * @throws ApiException
     */
    public function getPetPhotoWithHttpInfo(int $petId, int $photoId): ApiResult
    {
        $path = '/pet/{petId}/photos/{photoId}';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('photoId', $photoId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'photoId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<\SplFileObject> $result */
        $result = $this->invokeApiForResult(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['image/jpeg', 'image/png', 'application/json'],
            'application/json',
            '\SplFileObject',
        );
        return $result;
    }

    /**
     * Get a tag for a pet
     * @param string[]|null $colors
     * @param string[]|null $sizes
     * @return \PetstoreClient\Models\Pet
     * @throws ApiException
     */
    public function getPetTag(int $petId, string $tagName, ?array $colors = null, ?array $sizes = null, ?string $filter = null)
    {
        /** @var \PetstoreClient\Models\Pet $result */
        $result = $this->getPetTagWithHttpInfo($petId, $tagName, $colors, $sizes, $filter)->data;
        return $result;
    }

    /**
     * @param string[]|null $colors
     * @param string[]|null $sizes
     * @return ApiResult<\PetstoreClient\Models\Pet>
     * @throws ApiException
     */
    public function getPetTagWithHttpInfo(int $petId, string $tagName, ?array $colors = null, ?array $sizes = null, ?string $filter = null): ApiResult
    {
        $path = '/pet/{petId}/tag/{tagName}';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'matrix', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('tagName', $tagName, 'path', 'string', null, 'label', false);
        $path = str_replace('{' . 'tagName' . '}', $pathValue, $path);
        $queryParams = [];
        if ($colors !== null) {
            $queryParams['colors'] = ValueSerializer::serializeStyled('colors', $colors, 'query', 'string[]', 'pipes', 'pipeDelimited', false);
        }
        if ($sizes !== null) {
            $queryParams['sizes'] = ValueSerializer::serializeStyled('sizes', $sizes, 'query', 'string[]', 'ssv', 'spaceDelimited', false);
        }
        $serialized = ValueSerializer::serializeStyled('filter', $filter, 'query', 'string', null, 'form', true);
        $queryParams['filter'] = $serialized ?? '';
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<\PetstoreClient\Models\Pet> $result */
        $result = $this->invokeApiForResult(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Pet',
        );
        return $result;
    }

    /**
     * Get staging pet info
     * @return \PetstoreClient\Models\Pet
     * @throws ApiException
     */
    public function getStagingPetInfo(int $petId, ?GetStagingPetInfoServer $server = null)
    {
        /** @var \PetstoreClient\Models\Pet $result */
        $result = $this->getStagingPetInfoWithHttpInfo($petId, $server)->data;
        return $result;
    }

    /**
     * @return ApiResult<\PetstoreClient\Models\Pet>
     * @throws ApiException
     */
    public function getStagingPetInfoWithHttpInfo(int $petId, ?GetStagingPetInfoServer $server = null): ApiResult
    {
        $path = '/pet/{petId}/staging';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $serverUrl = $server instanceof \PetstoreClient\Api\GetStagingPetInfoServer ? $server->getUrl() : 'https://{environment}.example.com/api/{version}';
        if (str_starts_with($serverUrl, 'http://') || str_starts_with($serverUrl, 'https://')) {
            $path = $serverUrl . $path;
        }
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var ApiResult<\PetstoreClient\Models\Pet> $result */
        $result = $this->invokeApiForResult(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Pet',
        );
        return $result;
    }

    /**
     * Set the pet&#39;s profile photo
     * Accepts either raw image bytes (image/jpeg or image/png) or a JSON envelope carrying a base64-encoded image for clients that prefer a JSON-only workflow.
     * @throws ApiException
     */
    public function setPetAvatar(int $petId, \SplFileObject $body): void
    {
        $this->setPetAvatarWithHttpInfo($petId, $body);
    }

    /**
     * @return ApiResult<null>
     * @throws ApiException
     */
    public function setPetAvatarWithHttpInfo(int $petId, \SplFileObject $body): ApiResult
    {
        $path = '/pet/{petId}/avatar';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = $body;

        /** @var ApiResult<null> $result */
        $result = $this->invokeApiForResult(
            'PUT',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            [],
            'image/jpeg',
            null,
        );
        return $result;
    }

    /**
     * Set the pet&#39;s avatar thumbnail as base64
     * Accepts either a single base64-encoded thumbnail or an array of candidates; the server selects the most suitable one.
     * @throws ApiException
     */
    public function setPetAvatarThumbnail(int $petId, \PetstoreClient\Models\SetPetAvatarThumbnailRequest $setPetAvatarThumbnailRequest): void
    {
        $this->setPetAvatarThumbnailWithHttpInfo($petId, $setPetAvatarThumbnailRequest);
    }

    /**
     * @return ApiResult<null>
     * @throws ApiException
     */
    public function setPetAvatarThumbnailWithHttpInfo(int $petId, \PetstoreClient\Models\SetPetAvatarThumbnailRequest $setPetAvatarThumbnailRequest): ApiResult
    {
        $path = '/pet/{petId}/avatar/thumbnail';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = $setPetAvatarThumbnailRequest;

        /** @var ApiResult<null> $result */
        $result = $this->invokeApiForResult(
            'PUT',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            [],
            'application/json',
            null,
        );
        return $result;
    }

    /**
     * Update an existing pet
     * @param int $petId ID of pet to update
     * @param \PetstoreClient\Models\Pet $pet Pet object that needs to be updated
     * @return \PetstoreClient\Models\Pet
     * @throws ApiException
     */
    public function updatePet(int $petId, \PetstoreClient\Models\Pet $pet)
    {
        /** @var \PetstoreClient\Models\Pet $result */
        $result = $this->updatePetWithHttpInfo($petId, $pet)->data;
        return $result;
    }

    /**
     * @param int $petId ID of pet to update
     * @param \PetstoreClient\Models\Pet $pet Pet object that needs to be updated
     * @return ApiResult<\PetstoreClient\Models\Pet>
     * @throws ApiException
     */
    public function updatePetWithHttpInfo(int $petId, \PetstoreClient\Models\Pet $pet): ApiResult
    {
        $path = '/pet/{petId}';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = $pet;

        /** @var ApiResult<\PetstoreClient\Models\Pet> $result */
        $result = $this->invokeApiForResult(
            'PUT',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Pet',
        );
        return $result;
    }

    /**
     * Upload the pet&#39;s adoption certificate
     * Attaches a single adoption certificate document. No metadata fields are required alongside the file.
     * @return \PetstoreClient\Models\ApiResponse
     * @throws ApiException
     */
    public function uploadPetCertificate(int $petId, \SplFileObject $file)
    {
        /** @var \PetstoreClient\Models\ApiResponse $result */
        $result = $this->uploadPetCertificateWithHttpInfo($petId, $file)->data;
        return $result;
    }

    /**
     * @return ApiResult<\PetstoreClient\Models\ApiResponse>
     * @throws ApiException
     */
    public function uploadPetCertificateWithHttpInfo(int $petId, \SplFileObject $file): ApiResult
    {
        $path = '/pet/{petId}/certificate';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = [];
        $requestBody['file'] = $file;

        /** @var ApiResult<\PetstoreClient\Models\ApiResponse> $result */
        $result = $this->invokeApiForResult(
            'POST',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'multipart/form-data',
            '\PetstoreClient\Models\ApiResponse',
        );
        return $result;
    }

    /**
     * Attach a vet document or health record
     * Accepts either a multipart upload with document classification fields, or a raw octet-stream for server-to-server and CLI clients that prefer to stream bytes directly.
     * @return \PetstoreClient\Models\ApiResponse
     * @throws ApiException
     */
    public function uploadPetDocument(int $petId, \SplFileObject $file, ?string $documentType = null, ?string $notes = null)
    {
        /** @var \PetstoreClient\Models\ApiResponse $result */
        $result = $this->uploadPetDocumentWithHttpInfo($petId, $file, $documentType, $notes)->data;
        return $result;
    }

    /**
     * @return ApiResult<\PetstoreClient\Models\ApiResponse>
     * @throws ApiException
     */
    public function uploadPetDocumentWithHttpInfo(int $petId, \SplFileObject $file, ?string $documentType = null, ?string $notes = null): ApiResult
    {
        $path = '/pet/{petId}/documents';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serializeStyled('petId', $petId, 'path', 'int', null, 'simple', false);
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = [];
        $requestBody['file'] = $file;
        if ($documentType !== null) {
            $requestBody['documentType'] = $documentType;
        }
        if ($notes !== null) {
            $requestBody['notes'] = $notes;
        }

        /** @var ApiResult<\PetstoreClient\Models\ApiResponse> $result */
        $result = $this->invokeApiForResult(
            'POST',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            ['application/json'],
            'multipart/form-data',
            '\PetstoreClient\Models\ApiResponse',
        );
        return $result;
    }
}
