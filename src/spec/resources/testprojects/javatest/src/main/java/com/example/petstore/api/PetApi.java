package com.example.petstore.api;

import com.example.petstore.ApiClient;
import com.example.petstore.ApiException;
import com.example.petstore.Configuration;
import com.example.petstore.ObjectSerializer;
import com.example.petstore.auth.AdminBasicAuthenticator;
import com.example.petstore.auth.ApiKeyHeaderAuthenticator;
import com.example.petstore.auth.Authenticator;
import com.example.petstore.auth.PetStoreBearerAuthenticator;
import com.example.petstore.auth.oauth.MachineAuthClientCredentialsAuthenticator;
import com.example.petstore.models.ApiResponse;
import com.example.petstore.models.Pet;
import com.example.petstore.models.PetPassport;
import com.example.petstore.models.Photo;
import com.example.petstore.models.PhotoMetadata;
import com.example.petstore.models.SetPetAvatarThumbnailRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** PetApi provides methods for the Pet API group. */
public class PetApi extends BaseApi {

  private static final TypeReference<Pet> addPetTypeRef = new TypeReference<>() {};

  private static final TypeReference<List<Photo>> addPetPhotosTypeRef = new TypeReference<>() {};

  private static final TypeReference<InputStream> downloadPetDocumentTypeRef =
      new TypeReference<>() {};

  private static final TypeReference<List<Pet>> findPetsByStatusTypeRef = new TypeReference<>() {};

  private static final TypeReference<InputStream> getPetAvatarTypeRef = new TypeReference<>() {};

  private static final TypeReference<byte[]> getPetAvatarThumbnailTypeRef =
      new TypeReference<>() {};

  private static final TypeReference<Pet> getPetByIdTypeRef = new TypeReference<>() {};

  private static final TypeReference<PetPassport> getPetPassportTypeRef = new TypeReference<>() {};

  private static final TypeReference<InputStream> getPetPhotoTypeRef = new TypeReference<>() {};

  private static final TypeReference<Pet> updatePetTypeRef = new TypeReference<>() {};

  private static final TypeReference<ApiResponse> uploadPetCertificateTypeRef =
      new TypeReference<>() {};

  private static final TypeReference<ApiResponse> uploadPetDocumentTypeRef =
      new TypeReference<>() {};

  public PetApi() {
    super();
  }

  public PetApi(ApiClient apiClient, Configuration config) {
    super(apiClient, config);
  }

  /**
   * Add a new pet to the store
   *
   * @param pet Create a new pet in the store (required)
   * @return Pet
   * @throws ApiException if fails to make API call
   */
  @Nullable
  public Pet addPet(PetStoreBearerAuthenticator auth, Pet pet) throws ApiException {
    return addPetInternal(auth, pet);
  }

  @Nullable
  public Pet addPet(ApiKeyHeaderAuthenticator auth, Pet pet) throws ApiException {
    return addPetInternal(auth, pet);
  }

  @Nullable
  private Pet addPetInternal(Authenticator auth, Pet pet) throws ApiException {
    if (pet == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'pet' when calling addPet");
    }
    String path = "/pet";
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    return invokeApi(
        "POST",
        path,
        queryParams,
        headerParams,
        pet,
        new String[] {"application/json"},
        "application/json",
        addPetTypeRef,
        auth);
  }

  /**
   * Add photos to the pet&#39;s gallery Uploads one or more photos with structured metadata. The
   * metadata part is serialised as JSON within the multipart body.
   *
   * @param petId (required)
   * @param files (required)
   * @param metadata (required)
   * @return List<Photo>
   * @throws ApiException if fails to make API call
   */
  @Nullable
  public List<Photo> addPetPhotos(Long petId, List<InputStream> files, PhotoMetadata metadata)
      throws ApiException {
    if (petId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'petId' when calling addPetPhotos");
    }
    if (files == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'files' when calling addPetPhotos");
    }
    if (metadata == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'metadata' when calling addPetPhotos");
    }
    String path =
        "/pet/{petId}/photos"
            .replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    Map<String, Object> formBody = new HashMap<>();
    formBody.put("files", files);
    formBody.put("metadata", metadata);

    return invokeApi(
        "POST",
        path,
        queryParams,
        headerParams,
        formBody,
        new String[] {"application/json"},
        "multipart/form-data",
        addPetPhotosTypeRef,
        null);
  }

  /**
   * Deletes a pet
   *
   * @param petId Pet id to delete (required)
   * @throws ApiException if fails to make API call
   */
  public void deletePet(MachineAuthClientCredentialsAuthenticator auth, Long petId)
      throws ApiException {
    deletePetInternal(auth, petId);
  }

  public void deletePet(AdminBasicAuthenticator auth, Long petId) throws ApiException {
    deletePetInternal(auth, petId);
  }

  private void deletePetInternal(Authenticator auth, Long petId) throws ApiException {
    if (petId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'petId' when calling deletePet");
    }
    String path =
        "/pet/{petId}".replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    invokeApi(
        "DELETE",
        path,
        queryParams,
        headerParams,
        null,
        new String[] {},
        "application/json",
        null,
        auth);
  }

  /**
   * Download a vet document Returns the raw document bytes as an octet-stream. The original MIME
   * type is communicated via the Content-Type response header.
   *
   * @param petId (required)
   * @param documentId (required)
   * @return InputStream
   * @throws ApiException if fails to make API call
   */
  @Nullable
  public InputStream downloadPetDocument(Long petId, Long documentId) throws ApiException {
    if (petId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'petId' when calling downloadPetDocument");
    }
    if (documentId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'documentId' when calling downloadPetDocument");
    }
    String path =
        "/pet/{petId}/documents/{documentId}"
            .replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)))
            .replace("{" + "documentId" + "}", encode(ObjectSerializer.toPathValue(documentId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    return invokeApi(
        "GET",
        path,
        queryParams,
        headerParams,
        null,
        new String[] {"application/octet-stream"},
        "application/json",
        downloadPetDocumentTypeRef,
        null);
  }

  /**
   * Finds Pets by status
   *
   * @param status Status values that need to be considered for filter (optional)
   * @return List<Pet>
   * @throws ApiException if fails to make API call
   */
  @Nullable
  public List<Pet> findPetsByStatus(String status) throws ApiException {
    String path = "/pet/findByStatus";
    Map<String, Object> queryParams = new HashMap<>();
    if (status != null) {
      queryParams.put("status", ObjectSerializer.toQueryValue(status, null));
    }
    Map<String, String> headerParams = new HashMap<>();
    return invokeApi(
        "GET",
        path,
        queryParams,
        headerParams,
        null,
        new String[] {"application/json"},
        "application/json",
        findPetsByStatusTypeRef,
        null);
  }

  /**
   * Get the pet&#39;s profile photo Returns the raw image bytes of the pet&#39;s current avatar.
   *
   * @param petId (required)
   * @return InputStream
   * @throws ApiException if fails to make API call
   */
  @Nullable
  public InputStream getPetAvatar(Long petId) throws ApiException {
    if (petId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'petId' when calling getPetAvatar");
    }
    String path =
        "/pet/{petId}/avatar"
            .replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    return invokeApi(
        "GET",
        path,
        queryParams,
        headerParams,
        null,
        new String[] {"image/jpeg", "image/png"},
        "application/json",
        getPetAvatarTypeRef,
        null);
  }

  /**
   * Get the pet&#39;s avatar thumbnail as base64 Returns a compact base64-encoded thumbnail
   * suitable for embedding directly in mobile UI without a separate image request.
   *
   * @param petId (required)
   * @return byte[]
   * @throws ApiException if fails to make API call
   */
  @Nullable
  public byte[] getPetAvatarThumbnail(Long petId) throws ApiException {
    if (petId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'petId' when calling getPetAvatarThumbnail");
    }
    String path =
        "/pet/{petId}/avatar/thumbnail"
            .replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    return invokeApi(
        "GET",
        path,
        queryParams,
        headerParams,
        null,
        new String[] {"application/json"},
        "application/json",
        getPetAvatarThumbnailTypeRef,
        null);
  }

  /**
   * Find pet by ID Returns a single pet
   *
   * @param petId ID of pet to return (required)
   * @return Pet
   * @throws ApiException if fails to make API call
   */
  @Nullable
  public Pet getPetById(Long petId) throws ApiException {
    if (petId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'petId' when calling getPetById");
    }
    String path =
        "/pet/{petId}".replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    return invokeApi(
        "GET",
        path,
        queryParams,
        headerParams,
        null,
        new String[] {"application/json"},
        "application/json",
        getPetByIdTypeRef,
        null);
  }

  /**
   * Get the pet&#39;s passport Returns a single JSON document combining the pet&#39;s profile with
   * an embedded base64 thumbnail and base64-encoded scans of each passport page, suitable for
   * mobile clients that prefer a single-request workflow.
   *
   * @param petId (required)
   * @return PetPassport
   * @throws ApiException if fails to make API call
   */
  @Nullable
  public PetPassport getPetPassport(Long petId) throws ApiException {
    if (petId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'petId' when calling getPetPassport");
    }
    String path =
        "/pet/{petId}/passport"
            .replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    return invokeApi(
        "GET",
        path,
        queryParams,
        headerParams,
        null,
        new String[] {"application/json"},
        "application/json",
        getPetPassportTypeRef,
        null);
  }

  /**
   * Get a photo or its metadata Returns the raw image bytes or JSON metadata depending on the
   * Accept header sent by the client.
   *
   * @param petId (required)
   * @param photoId (required)
   * @return InputStream
   * @throws ApiException if fails to make API call
   */
  @Nullable
  public InputStream getPetPhoto(Long petId, Long photoId) throws ApiException {
    if (petId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'petId' when calling getPetPhoto");
    }
    if (photoId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'photoId' when calling getPetPhoto");
    }
    String path =
        "/pet/{petId}/photos/{photoId}"
            .replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)))
            .replace("{" + "photoId" + "}", encode(ObjectSerializer.toPathValue(photoId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    return invokeApi(
        "GET",
        path,
        queryParams,
        headerParams,
        null,
        new String[] {"image/jpeg", "image/png", "application/json"},
        "application/json",
        getPetPhotoTypeRef,
        null);
  }

  /**
   * Set the pet&#39;s profile photo Accepts either raw image bytes (image/jpeg or image/png) or a
   * JSON envelope carrying a base64-encoded image for clients that prefer a JSON-only workflow.
   *
   * @param petId (required)
   * @param body (required)
   * @throws ApiException if fails to make API call
   */
  public void setPetAvatar(Long petId, InputStream body) throws ApiException {
    if (petId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'petId' when calling setPetAvatar");
    }
    if (body == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'body' when calling setPetAvatar");
    }
    String path =
        "/pet/{petId}/avatar"
            .replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    invokeApi(
        "PUT", path, queryParams, headerParams, body, new String[] {}, "image/jpeg", null, null);
  }

  /**
   * Set the pet&#39;s avatar thumbnail as base64 Accepts either a single base64-encoded thumbnail
   * or an array of candidates; the server selects the most suitable one.
   *
   * @param petId (required)
   * @param setPetAvatarThumbnailRequest (required)
   * @throws ApiException if fails to make API call
   */
  public void setPetAvatarThumbnail(
      Long petId, SetPetAvatarThumbnailRequest setPetAvatarThumbnailRequest) throws ApiException {
    if (petId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'petId' when calling setPetAvatarThumbnail");
    }
    if (setPetAvatarThumbnailRequest == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'setPetAvatarThumbnailRequest' when calling setPetAvatarThumbnail");
    }
    String path =
        "/pet/{petId}/avatar/thumbnail"
            .replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    invokeApi(
        "PUT",
        path,
        queryParams,
        headerParams,
        setPetAvatarThumbnailRequest,
        new String[] {},
        "application/json",
        null,
        null);
  }

  /**
   * Update an existing pet
   *
   * @param petId ID of pet to update (required)
   * @param pet Pet object that needs to be updated (required)
   * @return Pet
   * @throws ApiException if fails to make API call
   */
  @Nullable
  public Pet updatePet(Long petId, Pet pet) throws ApiException {
    if (petId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'petId' when calling updatePet");
    }
    if (pet == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'pet' when calling updatePet");
    }
    String path =
        "/pet/{petId}".replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    return invokeApi(
        "PUT",
        path,
        queryParams,
        headerParams,
        pet,
        new String[] {"application/json"},
        "application/json",
        updatePetTypeRef,
        null);
  }

  /**
   * Upload the pet&#39;s adoption certificate Attaches a single adoption certificate document. No
   * metadata fields are required alongside the file.
   *
   * @param petId (required)
   * @param file (required)
   * @return ApiResponse
   * @throws ApiException if fails to make API call
   */
  @Nullable
  public ApiResponse uploadPetCertificate(Long petId, InputStream file) throws ApiException {
    if (petId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'petId' when calling uploadPetCertificate");
    }
    if (file == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'file' when calling uploadPetCertificate");
    }
    String path =
        "/pet/{petId}/certificate"
            .replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    Map<String, Object> formBody = new HashMap<>();
    formBody.put("file", file);

    return invokeApi(
        "POST",
        path,
        queryParams,
        headerParams,
        formBody,
        new String[] {"application/json"},
        "multipart/form-data",
        uploadPetCertificateTypeRef,
        null);
  }

  /**
   * Attach a vet document or health record Accepts either a multipart upload with document
   * classification fields, or a raw octet-stream for server-to-server and CLI clients that prefer
   * to stream bytes directly.
   *
   * @param petId (required)
   * @param file (required)
   * @param documentType (optional)
   * @param notes (optional)
   * @return ApiResponse
   * @throws ApiException if fails to make API call
   */
  @Nullable
  public ApiResponse uploadPetDocument(
      Long petId, InputStream file, String documentType, String notes) throws ApiException {
    if (petId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'petId' when calling uploadPetDocument");
    }
    if (file == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'file' when calling uploadPetDocument");
    }
    String path =
        "/pet/{petId}/documents"
            .replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    Map<String, Object> formBody = new HashMap<>();
    formBody.put("file", file);
    if (documentType != null) {
      formBody.put("documentType", documentType);
    }
    if (notes != null) {
      formBody.put("notes", notes);
    }

    return invokeApi(
        "POST",
        path,
        queryParams,
        headerParams,
        formBody,
        new String[] {"application/json"},
        "multipart/form-data",
        uploadPetDocumentTypeRef,
        null);
  }
}
