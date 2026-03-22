<?php

namespace PetstoreClient\Api;

use PetstoreClient\ApiClient;
use PetstoreClient\ApiException;
use PetstoreClient\Configuration;
use PetstoreClient\DefaultApiClient;
use PetstoreClient\ValueSerializer;
use PetstoreClient\Auth\Authenticator;

/**
 * PetApi provides methods for the Pet API group.
 * Everything about your Pets
 * @see https://example.com/docs/pets Find out more about pets
 */
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
        $path = '/pet';
        $queryParams = [];
        $headerParams = [];
        $requestBody = $pet;

        /** @var \PetstoreClient\Models\Pet $result */
        $result = $this->invokeApi(
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
        $path = '/pet/{petId}/photos';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($petId, 'path', 'int');
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = [];
        $requestBody['files'] = $files;
        $requestBody['metadata'] = $metadata;

        /** @var \PetstoreClient\Models\Photo[] $result */
        $result = $this->invokeApi(
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
        $path = '/pet/{petId}';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($petId, 'path', 'int');
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        $this->invokeApi(
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
    }

    /**
     * Download a vet document
     * Returns the raw document bytes as an octet-stream. The original MIME type is communicated via the Content-Type response header.
     * @return \SplFileObject
     * @throws ApiException
     */
    public function downloadPetDocument(int $petId, int $documentId)
    {
        $path = '/pet/{petId}/documents/{documentId}';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($petId, 'path', 'int');
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($documentId, 'path', 'int');
        $path = str_replace('{' . 'documentId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var \SplFileObject $result */
        $result = $this->invokeApi(
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
     * @return \PetstoreClient\Models\Pet[]
     * @throws ApiException
     * @deprecated This operation is deprecated.
     * @see https://example.com/docs/filtering Find out more about filtering
     */
    public function findPetsByStatus(?string $status = null)
    {
        $path = '/pet/findByStatus';
        $queryParams = [];
        if ($status !== null) {
            $queryParams['status'] = ValueSerializer::serialize($status, 'query', 'string');
        }
        $headerParams = [];
        $requestBody = null;

        /** @var \PetstoreClient\Models\Pet[] $result */
        $result = $this->invokeApi(
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
     * Get the pet&#39;s profile photo
     * Returns the raw image bytes of the pet&#39;s current avatar.
     * @return \SplFileObject
     * @throws ApiException
     */
    public function getPetAvatar(int $petId)
    {
        $path = '/pet/{petId}/avatar';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($petId, 'path', 'int');
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var \SplFileObject $result */
        $result = $this->invokeApi(
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
        $path = '/pet/{petId}/avatar/thumbnail';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($petId, 'path', 'int');
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var string $result */
        $result = $this->invokeApi(
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
        $path = '/pet/{petId}';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($petId, 'path', 'int');
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var \PetstoreClient\Models\Pet $result */
        $result = $this->invokeApi(
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
        $path = '/pet/{petId}/passport';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($petId, 'path', 'int');
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var \PetstoreClient\Models\PetPassport $result */
        $result = $this->invokeApi(
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
        $path = '/pet/{petId}/photos/{photoId}';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($petId, 'path', 'int');
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($photoId, 'path', 'int');
        $path = str_replace('{' . 'photoId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = null;

        /** @var \SplFileObject $result */
        $result = $this->invokeApi(
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
     * Set the pet&#39;s profile photo
     * Accepts either raw image bytes (image/jpeg or image/png) or a JSON envelope carrying a base64-encoded image for clients that prefer a JSON-only workflow.
     * @throws ApiException
     */
    public function setPetAvatar(int $petId, \SplFileObject $body): void
    {
        $path = '/pet/{petId}/avatar';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($petId, 'path', 'int');
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = $body;

        $this->invokeApi(
            'PUT',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            [],
            'image/jpeg',
            null,
        );
    }

    /**
     * Set the pet&#39;s avatar thumbnail as base64
     * Accepts either a single base64-encoded thumbnail or an array of candidates; the server selects the most suitable one.
     * @throws ApiException
     */
    public function setPetAvatarThumbnail(int $petId, \PetstoreClient\Models\SetPetAvatarThumbnailRequest $setPetAvatarThumbnailRequest): void
    {
        $path = '/pet/{petId}/avatar/thumbnail';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($petId, 'path', 'int');
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = $setPetAvatarThumbnailRequest;

        $this->invokeApi(
            'PUT',
            $path,
            $queryParams,
            $headerParams,
            $requestBody,
            [],
            'application/json',
            null,
        );
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
        $path = '/pet/{petId}';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($petId, 'path', 'int');
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = $pet;

        /** @var \PetstoreClient\Models\Pet $result */
        $result = $this->invokeApi(
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
        $path = '/pet/{petId}/certificate';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($petId, 'path', 'int');
        $path = str_replace('{' . 'petId' . '}', $pathValue, $path);
        $queryParams = [];
        $headerParams = [];
        $requestBody = [];
        $requestBody['file'] = $file;

        /** @var \PetstoreClient\Models\ApiResponse $result */
        $result = $this->invokeApi(
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
        $path = '/pet/{petId}/documents';
        /** @var string $pathValue */
        $pathValue = ValueSerializer::serialize($petId, 'path', 'int');
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

        /** @var \PetstoreClient\Models\ApiResponse $result */
        $result = $this->invokeApi(
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
