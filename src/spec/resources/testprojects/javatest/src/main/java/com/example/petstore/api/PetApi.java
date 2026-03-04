package com.example.petstore.api;

import com.fasterxml.jackson.core.type.TypeReference;

import com.example.petstore.ApiException;
import com.example.petstore.ApiClient;
import com.example.petstore.Configuration;
import com.example.petstore.DefaultApiClient;
import com.example.petstore.ObjectSerializer;

import com.example.petstore.models.Pet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@javax.annotation.Generated(value = "io.github.mridang.codegen.generators.java.BetterJavaCodegen", date = "2026-03-04T22:28:53.916353+11:00[Australia/Sydney]", comments = "Generator version: 7.14.0")
public class PetApi extends BaseApi {

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
  public Pet addPet(Pet pet) throws ApiException {
    
    if (pet == null) {
      throw new IllegalArgumentException("Missing the required parameter 'pet' when calling addPet");
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
      new String[]{ "application/json" },
      "application/json",
      new TypeReference<Pet>(){}
    );
  }

  /**
   * Deletes a pet
   * 
   * @param petId Pet id to delete (required)
   * @throws ApiException if fails to make API call
   */
  public void deletePet(Long petId) throws ApiException {
    
    if (petId == null) {
      throw new IllegalArgumentException("Missing the required parameter 'petId' when calling deletePet");
    }
    
    String path = "/pet/{petId}"
      .replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));

    Map<String, Object> queryParams = new HashMap<>();

    Map<String, String> headerParams = new HashMap<>();


    invokeApi(
      "DELETE",
      path,
      queryParams,
      headerParams,
      null,
      new String[]{  },
      "application/json",
      null
    );
  }

  /**
   * Finds Pets by status
   * 
   * @param status Status values that need to be considered for filter (optional, default to available)
   * @return List&lt;Pet&gt;
   * @throws ApiException if fails to make API call
   */
  public List<Pet> findPetsByStatus(String status) throws ApiException {
    
    String path = "/pet/findByStatus";

    Map<String, Object> queryParams = new HashMap<>();
    if (status != null) {
      queryParams.put("status", ObjectSerializer.toQueryValue(status));
    }

    Map<String, String> headerParams = new HashMap<>();


    return invokeApi(
      "GET",
      path,
      queryParams,
      headerParams,
      null,
      new String[]{ "application/json" },
      "application/json",
      new TypeReference<List<Pet>>(){}
    );
  }

  /**
   * Find pet by ID
   * Returns a single pet
   * @param petId ID of pet to return (required)
   * @return Pet
   * @throws ApiException if fails to make API call
   */
  public Pet getPetById(Long petId) throws ApiException {
    
    if (petId == null) {
      throw new IllegalArgumentException("Missing the required parameter 'petId' when calling getPetById");
    }
    
    String path = "/pet/{petId}"
      .replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));

    Map<String, Object> queryParams = new HashMap<>();

    Map<String, String> headerParams = new HashMap<>();


    return invokeApi(
      "GET",
      path,
      queryParams,
      headerParams,
      null,
      new String[]{ "application/json" },
      "application/json",
      new TypeReference<Pet>(){}
    );
  }

  /**
   * Update an existing pet
   * 
   * @param petId ID of pet to update (required)
   * @param pet Pet object that needs to be updated (required)
   * @return Pet
   * @throws ApiException if fails to make API call
   */
  public Pet updatePet(Long petId, Pet pet) throws ApiException {
    
    if (petId == null) {
      throw new IllegalArgumentException("Missing the required parameter 'petId' when calling updatePet");
    }
    
    if (pet == null) {
      throw new IllegalArgumentException("Missing the required parameter 'pet' when calling updatePet");
    }
    
    String path = "/pet/{petId}"
      .replace("{" + "petId" + "}", encode(ObjectSerializer.toPathValue(petId)));

    Map<String, Object> queryParams = new HashMap<>();

    Map<String, String> headerParams = new HashMap<>();


    return invokeApi(
      "PUT",
      path,
      queryParams,
      headerParams,
      pet,
      new String[]{ "application/json" },
      "application/json",
      new TypeReference<Pet>(){}
    );
  }
}
