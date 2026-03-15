<?php

namespace PetstoreClient\Api;

use PetstoreClient\ApiClient;
use PetstoreClient\ApiException;
use PetstoreClient\Configuration;
use PetstoreClient\DefaultApiClient;
use PetstoreClient\ObjectSerializer;
use PetstoreClient\Auth\Authenticator;

/**
 * PetApi provides methods for the Pet API group.
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
    public function addPet(Authenticator $auth, $pet)
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
     * @param int $petId
     * @param \SplFileObject[] $files
     * @param \PetstoreClient\Models\PhotoMetadata $metadata
     * @return \PetstoreClient\Models\Photo[]
     * @throws ApiException
     */
    public function addPetPhotos($petId, $files, $metadata)
    {
        $path = '/pet/{petId}/photos';
        $path = str_replace(
            '{' . 'petId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($petId)),
            $path
        );
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
    public function deletePet(Authenticator $auth, $petId): void
    {
        $path = '/pet/{petId}';
        $path = str_replace(
            '{' . 'petId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($petId)),
            $path
        );
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
     * @param int $petId
     * @param int $documentId
     * @return \SplFileObject
     * @throws ApiException
     */
    public function downloadPetDocument($petId, $documentId)
    {
        $path = '/pet/{petId}/documents/{documentId}';
        $path = str_replace(
            '{' . 'petId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($petId)),
            $path
        );
        $path = str_replace(
            '{' . 'documentId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($documentId)),
            $path
        );
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
     */
    public function findPetsByStatus($status = 'available')
    {
        $path = '/pet/findByStatus';
        $queryParams = [];
        if ($status !== null) {
            $queryParams['status'] = ObjectSerializer::toQueryValue($status);
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
     * @param int $petId
     * @return \SplFileObject
     * @throws ApiException
     */
    public function getPetAvatar($petId)
    {
        $path = '/pet/{petId}/avatar';
        $path = str_replace(
            '{' . 'petId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($petId)),
            $path
        );
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
     * @param int $petId
     * @return string
     * @throws ApiException
     */
    public function getPetAvatarThumbnail($petId)
    {
        $path = '/pet/{petId}/avatar/thumbnail';
        $path = str_replace(
            '{' . 'petId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($petId)),
            $path
        );
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
     * @param int $petId ID of pet to return
     * @return \PetstoreClient\Models\Pet
     * @throws ApiException
     */
    public function getPetById($petId)
    {
        $path = '/pet/{petId}';
        $path = str_replace(
            '{' . 'petId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($petId)),
            $path
        );
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
     * @param int $petId
     * @return \PetstoreClient\Models\PetPassport
     * @throws ApiException
     */
    public function getPetPassport($petId)
    {
        $path = '/pet/{petId}/passport';
        $path = str_replace(
            '{' . 'petId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($petId)),
            $path
        );
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
     * @param int $petId
     * @param int $photoId
     * @return \SplFileObject
     * @throws ApiException
     */
    public function getPetPhoto($petId, $photoId)
    {
        $path = '/pet/{petId}/photos/{photoId}';
        $path = str_replace(
            '{' . 'petId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($petId)),
            $path
        );
        $path = str_replace(
            '{' . 'photoId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($photoId)),
            $path
        );
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
     * @param int $petId
     * @param \SplFileObject $body
     * @throws ApiException
     */
    public function setPetAvatar($petId, $body): void
    {
        $path = '/pet/{petId}/avatar';
        $path = str_replace(
            '{' . 'petId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($petId)),
            $path
        );
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
     * @param int $petId
     * @param \PetstoreClient\Models\SetPetAvatarThumbnailRequest $setPetAvatarThumbnailRequest
     * @throws ApiException
     */
    public function setPetAvatarThumbnail($petId, $setPetAvatarThumbnailRequest): void
    {
        $path = '/pet/{petId}/avatar/thumbnail';
        $path = str_replace(
            '{' . 'petId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($petId)),
            $path
        );
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
    public function updatePet($petId, $pet)
    {
        $path = '/pet/{petId}';
        $path = str_replace(
            '{' . 'petId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($petId)),
            $path
        );
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
     * @param int $petId
     * @param \SplFileObject $file
     * @return \PetstoreClient\Models\ApiResponse
     * @throws ApiException
     */
    public function uploadPetCertificate($petId, $file)
    {
        $path = '/pet/{petId}/certificate';
        $path = str_replace(
            '{' . 'petId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($petId)),
            $path
        );
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
     * @param int $petId
     * @param \SplFileObject $file
     * @param string|null $documentType
     * @param string|null $notes
     * @return \PetstoreClient\Models\ApiResponse
     * @throws ApiException
     */
    public function uploadPetDocument($petId, $file, $documentType = null, $notes = null)
    {
        $path = '/pet/{petId}/documents';
        $path = str_replace(
            '{' . 'petId' . '}',
            rawurlencode(ObjectSerializer::toPathValue($petId)),
            $path
        );
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
