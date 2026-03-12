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
import com.example.petstore.models.Pet;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** PetApi provides methods for the Pet API group. */
public class PetApi extends BaseApi {

  private static final TypeReference<Pet> addPetTypeRef = new TypeReference<>() {};

  private static final TypeReference<List<Pet>> findPetsByStatusTypeRef = new TypeReference<>() {};

  private static final TypeReference<Pet> getPetByIdTypeRef = new TypeReference<>() {};

  private static final TypeReference<Pet> updatePetTypeRef = new TypeReference<>() {};

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
}
