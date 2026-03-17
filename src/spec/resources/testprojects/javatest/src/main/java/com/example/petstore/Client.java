package com.example.petstore;

import com.example.petstore.api.PetApi;
import com.example.petstore.api.StoreApi;
import com.example.petstore.auth.Authenticator;
import com.example.petstore.auth.BearerAuthenticator;

/**
 * Unified entry point for all API services. Takes an {@link Authenticator} and exposes each API
 * group as a typed property.
 */
public final class Client {

  public final PetApi pet;
  public final StoreApi store;

  /**
   * Creates a new client with the given authenticator.
   *
   * @param authenticator Provides host URL and auth headers.
   */
  public Client(Authenticator authenticator) {
    Configuration.Builder configBuilder =
        Configuration.builder()
            .baseUrl(authenticator.getHost())
            .defaultHeaders(authenticator.getAuthHeaders());
    for (java.util.Map.Entry<String, String> entry : authenticator.getQueryParams().entrySet()) {
      configBuilder.defaultHeader("_query_" + entry.getKey(), entry.getValue());
    }
    Configuration config = configBuilder.build();
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
    return new Client(new BearerAuthenticator(host, accessToken));
  }
}
