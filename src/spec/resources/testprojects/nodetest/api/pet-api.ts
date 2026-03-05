import type { ApiClient } from '../ApiClient.js';
import { BaseApi } from './BaseApi.js';
import { Configuration } from '../Configuration.js';
import { ObjectSerializer } from '../ObjectSerializer.js';
import type { Pet } from '../models/index.js';
import { PetFromJSON, PetToJSON } from '../models/index.js';

/**
 * PetApi provides methods for the Pet API group.
 */
export class PetApi extends BaseApi {
  constructor(config?: Configuration, apiClient?: ApiClient) {
    super(config, apiClient);
  }

  /**
   * Add a new pet to the store
   * @param pet Create a new pet in the store (required)
   * @return Pet
   */
  async addPet(pet: Pet): Promise<Pet> {
    if (pet == null) {
      throw new Error('Missing required parameter "pet" when calling addPet');
    }
    const path = `/pet`;
    const queryParams: Record<string, any> = {};
    const headerParams: Record<string, string> = {};
    return this.invokeApi(
      'POST',
      path,
      queryParams,
      headerParams,
      ObjectSerializer.serialize(pet, PetToJSON),
      ['application/json'],
      'application/json',
      (json: any) => ObjectSerializer.deserialize(json, PetFromJSON)
    ) as Pet;
  }

  /**
   * Deletes a pet
   * @param petId Pet id to delete (required)
   */
  async deletePet(petId: number): Promise<void> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling deletePet');
    }
    const path = `/pet/{petId}`.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    const queryParams: Record<string, any> = {};
    const headerParams: Record<string, string> = {};
    (await this.invokeApi('DELETE', path, queryParams, headerParams, null, [], 'application/json', null)) as void;
  }

  /**
   * Finds Pets by status
   * @param status Status values that need to be considered for filter (optional)
   * @return Array<Pet>
   */
  async findPetsByStatus(status?: FindPetsByStatusStatusEnum): Promise<Array<Pet>> {
    const path = `/pet/findByStatus`;
    const queryParams: Record<string, any> = {};
    if (status != null) {
      queryParams['status'] = ObjectSerializer.toQueryValue(status);
    }
    const headerParams: Record<string, string> = {};
    return this.invokeApi(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: any) => ObjectSerializer.deserializeArray(json, PetFromJSON)
    ) as Array<Pet>;
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
    const path = `/pet/{petId}`.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    const queryParams: Record<string, any> = {};
    const headerParams: Record<string, string> = {};
    return this.invokeApi(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: any) => ObjectSerializer.deserialize(json, PetFromJSON)
    ) as Pet;
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
    const path = `/pet/{petId}`.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    const queryParams: Record<string, any> = {};
    const headerParams: Record<string, string> = {};
    return this.invokeApi(
      'PUT',
      path,
      queryParams,
      headerParams,
      ObjectSerializer.serialize(pet, PetToJSON),
      ['application/json'],
      'application/json',
      (json: any) => ObjectSerializer.deserialize(json, PetFromJSON)
    ) as Pet;
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
