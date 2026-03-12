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
        $body = $pet;

        /** @var \PetstoreClient\Models\Pet $result */
        $result = $this->invokeApi(
            'POST',
            $path,
            $queryParams,
            $headerParams,
            $body,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Pet',
            $auth
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
        $body = null;

        $this->invokeApi(
            'DELETE',
            $path,
            $queryParams,
            $headerParams,
            $body,
            [],
            'application/json',
            null,
            $auth
        );
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
        $body = null;

        /** @var \PetstoreClient\Models\Pet[] $result */
        $result = $this->invokeApi(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $body,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Pet[]',
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
        $body = null;

        /** @var \PetstoreClient\Models\Pet $result */
        $result = $this->invokeApi(
            'GET',
            $path,
            $queryParams,
            $headerParams,
            $body,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Pet',
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
        $body = $pet;

        /** @var \PetstoreClient\Models\Pet $result */
        $result = $this->invokeApi(
            'PUT',
            $path,
            $queryParams,
            $headerParams,
            $body,
            ['application/json'],
            'application/json',
            '\PetstoreClient\Models\Pet',
        );
        return $result;
    }
}
