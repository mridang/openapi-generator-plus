package com.example.petstore;

import com.example.petstore.api.PetApi;
import com.example.petstore.api.StoreApi;
import com.example.petstore.auth.Authenticator;
import java.util.Collections;
import java.util.Map;

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
    ApiClient apiClient = new DefaultApiClient(config);
    this.pet = new PetApi(apiClient, config);
    this.store = new StoreApi(apiClient, config);
  }

  /**
   * Creates a client authenticated with a static Bearer token.
   *
   * @param host API base URL.
   * @param accessToken Bearer token.
   * @return Configured client instance.
   */
  public static Client withToken(String host, String accessToken) {
    return new Client(
        new Authenticator() {
          @Override
          public String getHost() {
            return host;
          }

          @Override
          public Map<String, String> getAuthHeaders() {
            return Collections.singletonMap("Authorization", "Bearer " + accessToken);
          }
        });
  }
}
