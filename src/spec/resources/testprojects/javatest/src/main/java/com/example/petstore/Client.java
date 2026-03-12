package com.example.petstore;

import com.example.petstore.api.PetApi;
import com.example.petstore.api.StoreApi;
import com.example.petstore.auth.Authenticator;

/**
 * Unified entry point for all API services. Takes an {@link Authenticator} and exposes each API
 * group as a typed property.
 */
public class Client {

  public final PetApi pet;
  public final StoreApi store;

  /**
   * Creates a new client with the given authenticator.
   *
   * @param authenticator Provides host URL and auth headers.
   */
  public Client(Authenticator authenticator) {
    Configuration config = new Configuration();
    config.setBaseUrl(authenticator.getHost());
    config.getDefaultHeaders().putAll(authenticator.getAuthHeaders());
    for (java.util.Map.Entry<String, String> entry : authenticator.getQueryParams().entrySet()) {
      config.getDefaultHeaders().put("_query_" + entry.getKey(), entry.getValue());
    }
    ApiClient apiClient = new DefaultApiClient(config);
    this.pet = new PetApi(apiClient, config);
    this.store = new StoreApi(apiClient, config);
  }
}
