import type { ApiClient } from '../api-client.js';
import type { Authenticator } from '../auth/authenticator.js';
import { BaseApi } from './base-api.js';
import { Configuration } from '../configuration.js';
import { ObjectSerializer } from '../object-serializer.js';
import { Pet } from '../models/index.js';

/**
 * PetApi provides methods for the Pet API group.
 */
export class PetApi extends BaseApi {
  constructor(config?: Configuration, apiClient?: ApiClient) {
    super(config, apiClient);
  }

  /**
   * Add a new pet to the store
   * @param auth authenticator for this operation
   * @param pet Create a new pet in the store (required)
   * @return Pet
   */
  async addPet(auth: Authenticator, pet: Pet): Promise<Pet> {
    if (pet == null) {
      throw new Error('Missing required parameter "pet" when calling addPet');
    }
    const path = `/pet`;
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return (await this.invokeApi(
      'POST',
      path,
      queryParams,
      headerParams,
      pet,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Pet),
      auth
    )) as Pet;
  }

  /**
   * Deletes a pet
   * @param auth authenticator for this operation
   * @param petId Pet id to delete (required)
   */
  async deletePet(auth: Authenticator, petId: number): Promise<void> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling deletePet');
    }
    let path = `/pet/{petId}`;
    path = path.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    (await this.invokeApi('DELETE', path, queryParams, headerParams, null, [], 'application/json', null, auth)) as void;
  }

  /**
   * Finds Pets by status
   * @param status Status values that need to be considered for filter (optional)
   * @return Array<Pet>
   */
  async findPetsByStatus(status?: FindPetsByStatusStatusEnum): Promise<Array<Pet>> {
    const path = `/pet/findByStatus`;
    const queryParams: Record<string, unknown> = {};
    if (status != null) {
      queryParams['status'] = ObjectSerializer.toQueryValue(status);
    }
    const headerParams: Record<string, string> = {};
    return (await this.invokeApi(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserializeArray(json, Pet),
      null
    )) as Array<Pet>;
  }

  /**
   * Returns a single pet
   * Find pet by ID
   * @param petId ID of pet to return (required)
   * @return Pet
   */
  async getPetById(petId: number): Promise<Pet> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetById');
    }
    let path = `/pet/{petId}`;
    path = path.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return (await this.invokeApi(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Pet),
      null
    )) as Pet;
  }

  /**
   * Update an existing pet
   * @param petId ID of pet to update (required)
   * @param pet Pet object that needs to be updated (required)
   * @return Pet
   */
  async updatePet(petId: number, pet: Pet): Promise<Pet> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling updatePet');
    }
    if (pet == null) {
      throw new Error('Missing required parameter "pet" when calling updatePet');
    }
    let path = `/pet/{petId}`;
    path = path.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return (await this.invokeApi(
      'PUT',
      path,
      queryParams,
      headerParams,
      pet,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Pet),
      null
    )) as Pet;
  }
}

/**
 * @export
 */
export const FindPetsByStatusStatusEnum = {
  Available: 'available',
  Pending: 'pending',
  Sold: 'sold',
  UnknownDefaultOpenApi: '11184809'
} as const;
export type FindPetsByStatusStatusEnum = (typeof FindPetsByStatusStatusEnum)[keyof typeof FindPetsByStatusStatusEnum];
