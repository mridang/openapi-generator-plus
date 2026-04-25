import type { ApiClient } from '../api-client.js';
import type { ApiResult } from '../api-result.js';
import type { Authenticator } from '../auth/authenticator.js';
import { BaseApi } from './base-api.js';
import { Configuration } from '../configuration.js';
import { ObjectSerializer } from '../object-serializer.js';
import { ValueSerializer } from '../value-serializer.js';
import { ApiResponse, Pet, PetPassport, PetTreatment, Photo, SetPetAvatarThumbnailRequest } from '../models/index.js';
import type { AddPetPhotosOptions } from './options/add-pet-photos-options.js';
import type { FindPetsByStatusOptions } from './options/find-pets-by-status-options.js';
import type { GetPetTagOptions } from './options/get-pet-tag-options.js';
import type { UploadPetCertificateOptions } from './options/upload-pet-certificate-options.js';
import type { UploadPetDocumentOptions } from './options/upload-pet-document-options.js';

export abstract class GetExternalPetInfoServer {
  abstract getUrl(): string;
}

export class GetExternalPetInfoServerServer0 extends GetExternalPetInfoServer {
  constructor() {
    super();
  }
  getUrl(): string {
    return 'https://external-api.example.com/v1';
  }
}
export abstract class GetMultiServerPetInfoServer {
  abstract getUrl(): string;
}


export enum GetMultiServerPetInfoServerRegion {
  US = 'us',
  EU = 'eu',
  AP = 'ap',
}

export class GetMultiServerPetInfoServerPrimary extends GetMultiServerPetInfoServer {
  constructor() {
    super();
  }
  getUrl(): string {
    return 'https://primary.example.com/v1';
  }
}
export class GetMultiServerPetInfoServerRegional extends GetMultiServerPetInfoServer {
  readonly region: GetMultiServerPetInfoServerRegion;
  constructor(region: GetMultiServerPetInfoServerRegion) {
    super();
    this.region = region;
  }
  getUrl(): string {
    let url = 'https://{region}.example.com/v1';
    url = url.replace('{' + 'region' + '}', this.region);
    return url;
  }
}
export abstract class GetStagingPetInfoServer {
  abstract getUrl(): string;
}

export enum GetStagingPetInfoServerEnvironment {
  STAGING = 'staging',
  SANDBOX = 'sandbox',
}

export enum GetStagingPetInfoServerVersion {
  V2 = 'v2',
  V3 = 'v3',
}

export class GetStagingPetInfoServerStagingServer extends GetStagingPetInfoServer {
  readonly environment: GetStagingPetInfoServerEnvironment;
  readonly version: GetStagingPetInfoServerVersion;
  constructor(environment: GetStagingPetInfoServerEnvironment, version: GetStagingPetInfoServerVersion) {
    super();
    this.environment = environment;
    this.version = version;
  }
  getUrl(): string {
    let url = 'https://{environment}.example.com/api/{version}';
    url = url.replace('{' + 'environment' + '}', this.environment);
    url = url.replace('{' + 'version' + '}', this.version);
    return url;
  }
}
/**
 * PetApi provides methods for the Pet API group.
 * Everything about your Pets
 * @see {@link https://example.com/docs/pets} Find out more about pets
 */
export class PetApi extends BaseApi {
  constructor(apiClient?: ApiClient, config?: Configuration) {
    super(apiClient, config);
  }

  /**
   * Add a new pet to the store
   * @param auth authenticator for this operation
   * @param pet Create a new pet in the store (required)
   * @return Pet
   * @throws {ApiError} if fails to make API call
   */
  async addPet(auth: Authenticator, pet: Pet): Promise<Pet> {
    if (pet == null) {
      throw new Error('Missing required parameter "pet" when calling addPet');
    }
    return (await this.addPetWithHttpInfo(auth, pet)).data as Pet;
  }

  /**
   * Add a new pet to the store (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async addPetWithHttpInfo(auth: Authenticator, pet: Pet): Promise<ApiResult<Pet>> {
    if (pet == null) {
      throw new Error('Missing required parameter "pet" when calling addPet');
    }
    const path = `/pet`;
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'POST',
      path,
      queryParams,
      headerParams,
      pet,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Pet),
      auth
    );
  }

  /**
   * Add photos to the pet's gallery
   * Uploads one or more photos with structured metadata. The metadata part is serialised as JSON within the multipart body.
   * @param petId  (required)
   * @param options.files  (required)
   * @param options.metadata  (required)
   * @return Array<Photo>
   * @throws {ApiError} if fails to make API call
   */
  async addPetPhotos(petId: number, options: AddPetPhotosOptions): Promise<Array<Photo>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling addPetPhotos');
    }
    if (options.files == null) {
      throw new Error('Missing required parameter "files" when calling addPetPhotos');
    }
    if (options.metadata == null) {
      throw new Error('Missing required parameter "metadata" when calling addPetPhotos');
    }
    return (await this.addPetPhotosWithHttpInfo(petId, options)).data as Array<Photo>;
  }

  /**
   * Add photos to the pet's gallery (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async addPetPhotosWithHttpInfo(petId: number, options: AddPetPhotosOptions): Promise<ApiResult<Array<Photo>>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling addPetPhotos');
    }
    if (options.files == null) {
      throw new Error('Missing required parameter "files" when calling addPetPhotos');
    }
    if (options.metadata == null) {
      throw new Error('Missing required parameter "metadata" when calling addPetPhotos');
    }
    let path = `/pet/{petId}/photos`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    const formBody: Record<string, unknown> = {};
    if (options.files != null) {
      formBody['files'] = options.files;
    }
    if (options.metadata != null) {
      formBody['metadata'] = options.metadata;
    }

    return await this.invokeApiForResult(
      'POST',
      path,
      queryParams,
      headerParams,
      formBody,
      ['application/json'],
      'multipart/form-data',
      (json: unknown) => ObjectSerializer.deserializeArray(json, Photo),
      null
    );
  }

  /**
   * Record a treatment for a pet
   * @param auth authenticator for this operation
   * @param petId  (required)
   * @param petTreatment  (required)
   * @return PetTreatment
   * @throws {ApiError} if fails to make API call
   */
  async addPetTreatment(auth: Authenticator, petId: number, petTreatment: PetTreatment): Promise<PetTreatment> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling addPetTreatment');
    }
    if (petTreatment == null) {
      throw new Error('Missing required parameter "petTreatment" when calling addPetTreatment');
    }
    return (await this.addPetTreatmentWithHttpInfo(auth, petId, petTreatment)).data as PetTreatment;
  }

  /**
   * Record a treatment for a pet (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async addPetTreatmentWithHttpInfo(auth: Authenticator, petId: number, petTreatment: PetTreatment): Promise<ApiResult<PetTreatment>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling addPetTreatment');
    }
    if (petTreatment == null) {
      throw new Error('Missing required parameter "petTreatment" when calling addPetTreatment');
    }
    let path = `/pet/{petId}/treatment`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'POST',
      path,
      queryParams,
      headerParams,
      petTreatment,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, PetTreatment),
      auth
    );
  }

  /**
   * Deletes a pet
   * @param auth authenticator for this operation
   * @param petId Pet id to delete (required)
   * @throws {ApiError} if fails to make API call
   */
  async deletePet(auth: Authenticator, petId: number): Promise<void> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling deletePet');
    }
    await this.deletePetWithHttpInfo(auth, petId);
  }

  /**
   * Deletes a pet (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async deletePetWithHttpInfo(auth: Authenticator, petId: number): Promise<ApiResult<void>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling deletePet');
    }
    let path = `/pet/{petId}`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'DELETE',
      path,
      queryParams,
      headerParams,
      null,
      [],
      'application/json',
      null,
      auth
    );
  }

  /**
   * Download a vet document
   * Returns the raw document bytes as an octet-stream. The original MIME type is communicated via the Content-Type response header.
   * @param petId  (required)
   * @param documentId  (required)
   * @return Buffer
   * @throws {ApiError} if fails to make API call
   */
  async downloadPetDocument(petId: number, documentId: number): Promise<Buffer> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling downloadPetDocument');
    }
    if (documentId == null) {
      throw new Error('Missing required parameter "documentId" when calling downloadPetDocument');
    }
    return (await this.downloadPetDocumentWithHttpInfo(petId, documentId)).data as Buffer;
  }

  /**
   * Download a vet document (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async downloadPetDocumentWithHttpInfo(petId: number, documentId: number): Promise<ApiResult<Buffer>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling downloadPetDocument');
    }
    if (documentId == null) {
      throw new Error('Missing required parameter "documentId" when calling downloadPetDocument');
    }
    let path = `/pet/{petId}/documents/{documentId}`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    path = path.replace(`{${'documentId'}}`, ValueSerializer.serializeStyled('documentId', documentId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/octet-stream'],
      'application/json',
      (json: unknown) => json as Buffer,
      null
    );
  }

  /**
   * Finds Pets by status
   * @param options.status Status values that need to be considered for filter (optional) (deprecated)
   * @param options.filter Filter criteria as key-value pairs (optional)
   * @return Array<Pet>
   * @throws {ApiError} if fails to make API call
   * @deprecated This operation is deprecated.
   * @see {@link https://example.com/docs/filtering} Find out more about filtering
   */
  async findPetsByStatus(options: FindPetsByStatusOptions): Promise<Array<Pet>> {
    return (await this.findPetsByStatusWithHttpInfo(options)).data as Array<Pet>;
  }

  /**
   * Finds Pets by status (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async findPetsByStatusWithHttpInfo(options: FindPetsByStatusOptions): Promise<ApiResult<Array<Pet>>> {
    const path = `/pet/findByStatus`;
    const queryParams: Record<string, unknown> = {};
    if (options.status != null) {
      queryParams['status'] = ValueSerializer.serializeStyled('status', options.status, 'query', 'string', null, 'form', true);
    }
    if (options.filter != null) {
      const deepObj = ValueSerializer.serializeDeepObject('filter', options.filter as Record<string, unknown>);
      Object.assign(queryParams, deepObj);
    }
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserializeArray(json, Pet),
      null
    );
  }

  /**
   * Get external pet info
   * @param petId  (required)
   * @return Pet
   * @throws {ApiError} if fails to make API call
   */
  async getExternalPetInfo(petId: number, server?: GetExternalPetInfoServer): Promise<Pet> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getExternalPetInfo');
    }
    return (await this.getExternalPetInfoWithHttpInfo(petId, server)).data as Pet;
  }

  /**
   * Get external pet info (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async getExternalPetInfoWithHttpInfo(petId: number, server?: GetExternalPetInfoServer): Promise<ApiResult<Pet>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getExternalPetInfo');
    }
    let path = `/pet/{petId}/external`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const serverUrl = server ? server.getUrl() : 'https://external-api.example.com/v1';
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'GET',
      serverUrl.startsWith('http://') || serverUrl.startsWith('https://') ? serverUrl + path : path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Pet),
      null
    );
  }

  /**
   * Get multi-server pet info
   * @param petId  (required)
   * @return Pet
   * @throws {ApiError} if fails to make API call
   */
  async getMultiServerPetInfo(petId: number, server?: GetMultiServerPetInfoServer): Promise<Pet> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getMultiServerPetInfo');
    }
    return (await this.getMultiServerPetInfoWithHttpInfo(petId, server)).data as Pet;
  }

  /**
   * Get multi-server pet info (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async getMultiServerPetInfoWithHttpInfo(petId: number, server?: GetMultiServerPetInfoServer): Promise<ApiResult<Pet>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getMultiServerPetInfo');
    }
    let path = `/pet/{petId}/multi`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const serverUrl = server ? server.getUrl() : 'https://primary.example.com/v1';
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'GET',
      serverUrl.startsWith('http://') || serverUrl.startsWith('https://') ? serverUrl + path : path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Pet),
      null
    );
  }

  /**
   * Get the pet's profile photo
   * Returns the raw image bytes of the pet's current avatar.
   * @param petId  (required)
   * @return Buffer
   * @throws {ApiError} if fails to make API call
   */
  async getPetAvatar(petId: number): Promise<Buffer> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetAvatar');
    }
    return (await this.getPetAvatarWithHttpInfo(petId)).data as Buffer;
  }

  /**
   * Get the pet's profile photo (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async getPetAvatarWithHttpInfo(petId: number): Promise<ApiResult<Buffer>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetAvatar');
    }
    let path = `/pet/{petId}/avatar`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['image/jpeg', 'image/png'],
      'application/json',
      (json: unknown) => json as Buffer,
      null
    );
  }

  /**
   * Get the pet's avatar thumbnail as base64
   * Returns a compact base64-encoded thumbnail suitable for embedding directly in mobile UI without a separate image request.
   * @param petId  (required)
   * @return string
   * @throws {ApiError} if fails to make API call
   */
  async getPetAvatarThumbnail(petId: number): Promise<string> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetAvatarThumbnail');
    }
    return (await this.getPetAvatarThumbnailWithHttpInfo(petId)).data as string;
  }

  /**
   * Get the pet's avatar thumbnail as base64 (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async getPetAvatarThumbnailWithHttpInfo(petId: number): Promise<ApiResult<string>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetAvatarThumbnail');
    }
    let path = `/pet/{petId}/avatar/thumbnail`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => json as string,
      null
    );
  }

  /**
   * Find pet by ID
   * Returns a single pet
   * @param petId ID of pet to return (required)
   * @return Pet
   * @throws {ApiError} if fails to make API call
   * @deprecated This operation is deprecated.
   */
  async getPetById(petId: number): Promise<Pet> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetById');
    }
    return (await this.getPetByIdWithHttpInfo(petId)).data as Pet;
  }

  /**
   * Find pet by ID (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async getPetByIdWithHttpInfo(petId: number): Promise<ApiResult<Pet>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetById');
    }
    let path = `/pet/{petId}`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Pet),
      null
    );
  }

  /**
   * Get the pet's passport
   * Returns a single JSON document combining the pet's profile with an embedded base64 thumbnail and base64-encoded scans of each passport page, suitable for mobile clients that prefer a single-request workflow.
   * @param petId  (required)
   * @return PetPassport
   * @throws {ApiError} if fails to make API call
   */
  async getPetPassport(petId: number): Promise<PetPassport> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetPassport');
    }
    return (await this.getPetPassportWithHttpInfo(petId)).data as PetPassport;
  }

  /**
   * Get the pet's passport (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async getPetPassportWithHttpInfo(petId: number): Promise<ApiResult<PetPassport>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetPassport');
    }
    let path = `/pet/{petId}/passport`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, PetPassport),
      null
    );
  }

  /**
   * Get a photo or its metadata
   * Returns the raw image bytes or JSON metadata depending on the Accept header sent by the client.
   * @param petId  (required)
   * @param photoId  (required)
   * @return Buffer
   * @throws {ApiError} if fails to make API call
   */
  async getPetPhoto(petId: number, photoId: number): Promise<Buffer> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetPhoto');
    }
    if (photoId == null) {
      throw new Error('Missing required parameter "photoId" when calling getPetPhoto');
    }
    return (await this.getPetPhotoWithHttpInfo(petId, photoId)).data as Buffer;
  }

  /**
   * Get a photo or its metadata (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async getPetPhotoWithHttpInfo(petId: number, photoId: number): Promise<ApiResult<Buffer>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetPhoto');
    }
    if (photoId == null) {
      throw new Error('Missing required parameter "photoId" when calling getPetPhoto');
    }
    let path = `/pet/{petId}/photos/{photoId}`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    path = path.replace(`{${'photoId'}}`, ValueSerializer.serializeStyled('photoId', photoId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['image/jpeg', 'image/png', 'application/json'],
      'application/json',
      (json: unknown) => json as Buffer,
      null
    );
  }

  /**
   * Get a tag for a pet
   * @param petId  (required)
   * @param tagName  (required)
   * @param options.colors  (optional)
   * @param options.sizes  (optional)
   * @param options.filter  (optional)
   * @return Pet
   * @throws {ApiError} if fails to make API call
   */
  async getPetTag(petId: number, tagName: string, options: GetPetTagOptions): Promise<Pet> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetTag');
    }
    if (tagName == null) {
      throw new Error('Missing required parameter "tagName" when calling getPetTag');
    }
    return (await this.getPetTagWithHttpInfo(petId, tagName, options)).data as Pet;
  }

  /**
   * Get a tag for a pet (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async getPetTagWithHttpInfo(petId: number, tagName: string, options: GetPetTagOptions): Promise<ApiResult<Pet>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetTag');
    }
    if (tagName == null) {
      throw new Error('Missing required parameter "tagName" when calling getPetTag');
    }
    let path = `/pet/{petId}/tag/{tagName}`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'matrix', false) as string);
    path = path.replace(`{${'tagName'}}`, ValueSerializer.serializeStyled('tagName', tagName, 'path', 'string', null, 'label', false) as string);
    const queryParams: Record<string, unknown> = {};
    if (options.colors != null) {
      queryParams['colors'] = ValueSerializer.serializeStyled('colors', options.colors, 'query', 'Array<string>', 'pipes', 'pipeDelimited', false);
    }
    if (options.sizes != null) {
      queryParams['sizes'] = ValueSerializer.serializeStyled('sizes', options.sizes, 'query', 'Array<string>', 'ssv', 'spaceDelimited', false);
    }
    {
      const serialized = ValueSerializer.serializeStyled('filter', options.filter, 'query', 'string', null, 'form', true);
      queryParams['filter'] = serialized ?? '';
    }
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Pet),
      null
    );
  }

  /**
   * Get staging pet info
   * @param petId  (required)
   * @return Pet
   * @throws {ApiError} if fails to make API call
   */
  async getStagingPetInfo(petId: number, server?: GetStagingPetInfoServer): Promise<Pet> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getStagingPetInfo');
    }
    return (await this.getStagingPetInfoWithHttpInfo(petId, server)).data as Pet;
  }

  /**
   * Get staging pet info (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async getStagingPetInfoWithHttpInfo(petId: number, server?: GetStagingPetInfoServer): Promise<ApiResult<Pet>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getStagingPetInfo');
    }
    let path = `/pet/{petId}/staging`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const serverUrl = server ? server.getUrl() : 'https://{environment}.example.com/api/{version}';
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'GET',
      serverUrl.startsWith('http://') || serverUrl.startsWith('https://') ? serverUrl + path : path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Pet),
      null
    );
  }

  /**
   * Set the pet's profile photo
   * Accepts either raw image bytes (image/jpeg or image/png) or a JSON envelope carrying a base64-encoded image for clients that prefer a JSON-only workflow.
   * @param petId  (required)
   * @param body  (required)
   * @throws {ApiError} if fails to make API call
   */
  async setPetAvatar(petId: number, body: Buffer): Promise<void> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling setPetAvatar');
    }
    if (body == null) {
      throw new Error('Missing required parameter "body" when calling setPetAvatar');
    }
    await this.setPetAvatarWithHttpInfo(petId, body);
  }

  /**
   * Set the pet's profile photo (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async setPetAvatarWithHttpInfo(petId: number, body: Buffer): Promise<ApiResult<void>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling setPetAvatar');
    }
    if (body == null) {
      throw new Error('Missing required parameter "body" when calling setPetAvatar');
    }
    let path = `/pet/{petId}/avatar`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'PUT',
      path,
      queryParams,
      headerParams,
      body,
      [],
      'image/jpeg',
      null,
      null
    );
  }

  /**
   * Set the pet's avatar thumbnail as base64
   * Accepts either a single base64-encoded thumbnail or an array of candidates; the server selects the most suitable one.
   * @param petId  (required)
   * @param setPetAvatarThumbnailRequest  (required)
   * @throws {ApiError} if fails to make API call
   */
  async setPetAvatarThumbnail(petId: number, setPetAvatarThumbnailRequest: SetPetAvatarThumbnailRequest | null): Promise<void> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling setPetAvatarThumbnail');
    }
    if (setPetAvatarThumbnailRequest == null) {
      throw new Error('Missing required parameter "setPetAvatarThumbnailRequest" when calling setPetAvatarThumbnail');
    }
    await this.setPetAvatarThumbnailWithHttpInfo(petId, setPetAvatarThumbnailRequest);
  }

  /**
   * Set the pet's avatar thumbnail as base64 (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async setPetAvatarThumbnailWithHttpInfo(petId: number, setPetAvatarThumbnailRequest: SetPetAvatarThumbnailRequest | null): Promise<ApiResult<void>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling setPetAvatarThumbnail');
    }
    if (setPetAvatarThumbnailRequest == null) {
      throw new Error('Missing required parameter "setPetAvatarThumbnailRequest" when calling setPetAvatarThumbnail');
    }
    let path = `/pet/{petId}/avatar/thumbnail`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'PUT',
      path,
      queryParams,
      headerParams,
      setPetAvatarThumbnailRequest,
      [],
      'application/json',
      null,
      null
    );
  }

  /**
   * Update an existing pet
   * @param petId ID of pet to update (required)
   * @param pet Pet object that needs to be updated (required)
   * @return Pet
   * @throws {ApiError} if fails to make API call
   */
  async updatePet(petId: number, pet: Pet): Promise<Pet> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling updatePet');
    }
    if (pet == null) {
      throw new Error('Missing required parameter "pet" when calling updatePet');
    }
    return (await this.updatePetWithHttpInfo(petId, pet)).data as Pet;
  }

  /**
   * Update an existing pet (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async updatePetWithHttpInfo(petId: number, pet: Pet): Promise<ApiResult<Pet>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling updatePet');
    }
    if (pet == null) {
      throw new Error('Missing required parameter "pet" when calling updatePet');
    }
    let path = `/pet/{petId}`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'PUT',
      path,
      queryParams,
      headerParams,
      pet,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Pet),
      null
    );
  }

  /**
   * Upload the pet's adoption certificate
   * Attaches a single adoption certificate document. No metadata fields are required alongside the file.
   * @param petId  (required)
   * @param options.file  (required)
   * @return ApiResponse
   * @throws {ApiError} if fails to make API call
   */
  async uploadPetCertificate(petId: number, options: UploadPetCertificateOptions): Promise<ApiResponse> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling uploadPetCertificate');
    }
    if (options.file == null) {
      throw new Error('Missing required parameter "file" when calling uploadPetCertificate');
    }
    return (await this.uploadPetCertificateWithHttpInfo(petId, options)).data as ApiResponse;
  }

  /**
   * Upload the pet's adoption certificate (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async uploadPetCertificateWithHttpInfo(petId: number, options: UploadPetCertificateOptions): Promise<ApiResult<ApiResponse>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling uploadPetCertificate');
    }
    if (options.file == null) {
      throw new Error('Missing required parameter "file" when calling uploadPetCertificate');
    }
    let path = `/pet/{petId}/certificate`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    const formBody: Record<string, unknown> = {};
    if (options.file != null) {
      formBody['file'] = options.file;
    }

    return await this.invokeApiForResult(
      'POST',
      path,
      queryParams,
      headerParams,
      formBody,
      ['application/json'],
      'multipart/form-data',
      (json: unknown) => ObjectSerializer.deserialize(json, ApiResponse),
      null
    );
  }

  /**
   * Attach a vet document or health record
   * Accepts either a multipart upload with document classification fields, or a raw octet-stream for server-to-server and CLI clients that prefer to stream bytes directly.
   * @param petId  (required)
   * @param options.file  (required)
   * @param options.documentType  (optional)
   * @param options.notes  (optional)
   * @return ApiResponse
   * @throws {ApiError} if fails to make API call
   */
  async uploadPetDocument(petId: number, options: UploadPetDocumentOptions): Promise<ApiResponse> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling uploadPetDocument');
    }
    if (options.file == null) {
      throw new Error('Missing required parameter "file" when calling uploadPetDocument');
    }
    return (await this.uploadPetDocumentWithHttpInfo(petId, options)).data as ApiResponse;
  }

  /**
   * Attach a vet document or health record (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async uploadPetDocumentWithHttpInfo(petId: number, options: UploadPetDocumentOptions): Promise<ApiResult<ApiResponse>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling uploadPetDocument');
    }
    if (options.file == null) {
      throw new Error('Missing required parameter "file" when calling uploadPetDocument');
    }
    let path = `/pet/{petId}/documents`;
    path = path.replace(`{${'petId'}}`, ValueSerializer.serializeStyled('petId', petId, 'path', 'number', null, 'simple', false) as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    const formBody: Record<string, unknown> = {};
    if (options.file != null) {
      formBody['file'] = options.file;
    }
    if (options.documentType != null) {
      formBody['documentType'] = options.documentType;
    }
    if (options.notes != null) {
      formBody['notes'] = options.notes;
    }

    return await this.invokeApiForResult(
      'POST',
      path,
      queryParams,
      headerParams,
      formBody,
      ['application/json'],
      'multipart/form-data',
      (json: unknown) => ObjectSerializer.deserialize(json, ApiResponse),
      null
    );
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
/**
 * @export
 */
export const UploadPetDocumentDocumentTypeEnum = {
  HealthCertificate: 'health_certificate',
  VaccinationRecord: 'vaccination_record',
  MicrochipRecord: 'microchip_record',
  UnknownDefaultOpenApi: '11184809'
} as const;
export type UploadPetDocumentDocumentTypeEnum = (typeof UploadPetDocumentDocumentTypeEnum)[keyof typeof UploadPetDocumentDocumentTypeEnum];
