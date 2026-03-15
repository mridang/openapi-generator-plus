import type { ApiClient } from '../api-client.js';
import type { Authenticator } from '../auth/authenticator.js';
import { BaseApi } from './base-api.js';
import { Configuration } from '../configuration.js';
import { ObjectSerializer } from '../object-serializer.js';
import { ApiResponse, Pet, PetPassport, Photo, PhotoMetadata, SetPetAvatarThumbnailRequest } from '../models/index.js';

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
   * Uploads one or more photos with structured metadata. The metadata part is serialised as JSON within the multipart body.
   * Add photos to the pet's gallery
   * @param petId  (required)
   * @param files  (required)
   * @param metadata  (required)
   * @return Array<Photo>
   */
  async addPetPhotos(petId: number, files: Array<Buffer>, metadata: PhotoMetadata): Promise<Array<Photo>> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling addPetPhotos');
    }
    if (files == null) {
      throw new Error('Missing required parameter "files" when calling addPetPhotos');
    }
    if (metadata == null) {
      throw new Error('Missing required parameter "metadata" when calling addPetPhotos');
    }
    let path = `/pet/{petId}/photos`;
    path = path.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    const formBody: Record<string, unknown> = {};
    if (files != null) {
      formBody['files'] = files;
    }
    if (metadata != null) {
      formBody['metadata'] = metadata;
    }

    return (await this.invokeApi(
      'POST',
      path,
      queryParams,
      headerParams,
      formBody,
      ['application/json'],
      'multipart/form-data',
      (json: unknown) => ObjectSerializer.deserializeArray(json, Photo),
      null
    )) as Array<Photo>;
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
   * Returns the raw document bytes as an octet-stream. The original MIME type is communicated via the Content-Type response header.
   * Download a vet document
   * @param petId  (required)
   * @param documentId  (required)
   * @return Buffer
   */
  async downloadPetDocument(petId: number, documentId: number): Promise<Buffer> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling downloadPetDocument');
    }
    if (documentId == null) {
      throw new Error('Missing required parameter "documentId" when calling downloadPetDocument');
    }
    let path = `/pet/{petId}/documents/{documentId}`;
    path = path.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    path = path.replace(`{${'documentId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(documentId)));
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return (await this.invokeApi(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/octet-stream'],
      'application/json',
      (json: unknown) => json as Buffer,
      null
    )) as Buffer;
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
   * Returns the raw image bytes of the pet's current avatar.
   * Get the pet's profile photo
   * @param petId  (required)
   * @return Buffer
   */
  async getPetAvatar(petId: number): Promise<Buffer> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetAvatar');
    }
    let path = `/pet/{petId}/avatar`;
    path = path.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return (await this.invokeApi(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['image/jpeg', 'image/png'],
      'application/json',
      (json: unknown) => json as Buffer,
      null
    )) as Buffer;
  }

  /**
   * Returns a compact base64-encoded thumbnail suitable for embedding directly in mobile UI without a separate image request.
   * Get the pet's avatar thumbnail as base64
   * @param petId  (required)
   * @return string
   */
  async getPetAvatarThumbnail(petId: number): Promise<string> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetAvatarThumbnail');
    }
    let path = `/pet/{petId}/avatar/thumbnail`;
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
      (json: unknown) => json as string,
      null
    )) as string;
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
   * Returns a single JSON document combining the pet's profile with an embedded base64 thumbnail and base64-encoded scans of each passport page, suitable for mobile clients that prefer a single-request workflow.
   * Get the pet's passport
   * @param petId  (required)
   * @return PetPassport
   */
  async getPetPassport(petId: number): Promise<PetPassport> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetPassport');
    }
    let path = `/pet/{petId}/passport`;
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
      (json: unknown) => ObjectSerializer.deserialize(json, PetPassport),
      null
    )) as PetPassport;
  }

  /**
   * Returns the raw image bytes or JSON metadata depending on the Accept header sent by the client.
   * Get a photo or its metadata
   * @param petId  (required)
   * @param photoId  (required)
   * @return Buffer
   */
  async getPetPhoto(petId: number, photoId: number): Promise<Buffer> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling getPetPhoto');
    }
    if (photoId == null) {
      throw new Error('Missing required parameter "photoId" when calling getPetPhoto');
    }
    let path = `/pet/{petId}/photos/{photoId}`;
    path = path.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    path = path.replace(`{${'photoId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(photoId)));
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return (await this.invokeApi(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['image/jpeg', 'image/png', 'application/json'],
      'application/json',
      (json: unknown) => json as Buffer,
      null
    )) as Buffer;
  }

  /**
   * Accepts either raw image bytes (image/jpeg or image/png) or a JSON envelope carrying a base64-encoded image for clients that prefer a JSON-only workflow.
   * Set the pet's profile photo
   * @param petId  (required)
   * @param body  (required)
   */
  async setPetAvatar(petId: number, body: Buffer): Promise<void> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling setPetAvatar');
    }
    if (body == null) {
      throw new Error('Missing required parameter "body" when calling setPetAvatar');
    }
    let path = `/pet/{petId}/avatar`;
    path = path.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    (await this.invokeApi('PUT', path, queryParams, headerParams, body, [], 'image/jpeg', null, null)) as void;
  }

  /**
   * Accepts either a single base64-encoded thumbnail or an array of candidates; the server selects the most suitable one.
   * Set the pet's avatar thumbnail as base64
   * @param petId  (required)
   * @param setPetAvatarThumbnailRequest  (required)
   */
  async setPetAvatarThumbnail(
    petId: number,
    setPetAvatarThumbnailRequest: SetPetAvatarThumbnailRequest | null
  ): Promise<void> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling setPetAvatarThumbnail');
    }
    if (setPetAvatarThumbnailRequest == null) {
      throw new Error('Missing required parameter "setPetAvatarThumbnailRequest" when calling setPetAvatarThumbnail');
    }
    let path = `/pet/{petId}/avatar/thumbnail`;
    path = path.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    (await this.invokeApi(
      'PUT',
      path,
      queryParams,
      headerParams,
      setPetAvatarThumbnailRequest,
      [],
      'application/json',
      null,
      null
    )) as void;
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

  /**
   * Attaches a single adoption certificate document. No metadata fields are required alongside the file.
   * Upload the pet's adoption certificate
   * @param petId  (required)
   * @param file  (required)
   * @return ApiResponse
   */
  async uploadPetCertificate(petId: number, file: Buffer): Promise<ApiResponse> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling uploadPetCertificate');
    }
    if (file == null) {
      throw new Error('Missing required parameter "file" when calling uploadPetCertificate');
    }
    let path = `/pet/{petId}/certificate`;
    path = path.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    const formBody: Record<string, unknown> = {};
    if (file != null) {
      formBody['file'] = file;
    }

    return (await this.invokeApi(
      'POST',
      path,
      queryParams,
      headerParams,
      formBody,
      ['application/json'],
      'multipart/form-data',
      (json: unknown) => ObjectSerializer.deserialize(json, ApiResponse),
      null
    )) as ApiResponse;
  }

  /**
   * Accepts either a multipart upload with document classification fields, or a raw octet-stream for server-to-server and CLI clients that prefer to stream bytes directly.
   * Attach a vet document or health record
   * @param petId  (required)
   * @param file  (required)
   * @param documentType  (optional)
   * @param notes  (optional)
   * @return ApiResponse
   */
  async uploadPetDocument(
    petId: number,
    file: Buffer,
    documentType?: UploadPetDocumentDocumentTypeEnum,
    notes?: string
  ): Promise<ApiResponse> {
    if (petId == null) {
      throw new Error('Missing required parameter "petId" when calling uploadPetDocument');
    }
    if (file == null) {
      throw new Error('Missing required parameter "file" when calling uploadPetDocument');
    }
    let path = `/pet/{petId}/documents`;
    path = path.replace(`{${'petId'}}`, encodeURIComponent(ObjectSerializer.toPathValue(petId)));
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    const formBody: Record<string, unknown> = {};
    if (file != null) {
      formBody['file'] = file;
    }
    if (documentType != null) {
      formBody['documentType'] = documentType;
    }
    if (notes != null) {
      formBody['notes'] = notes;
    }

    return (await this.invokeApi(
      'POST',
      path,
      queryParams,
      headerParams,
      formBody,
      ['application/json'],
      'multipart/form-data',
      (json: unknown) => ObjectSerializer.deserialize(json, ApiResponse),
      null
    )) as ApiResponse;
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
export type UploadPetDocumentDocumentTypeEnum =
  (typeof UploadPetDocumentDocumentTypeEnum)[keyof typeof UploadPetDocumentDocumentTypeEnum];
