package com.example.petstore.auth.oauth;

import com.example.petstore.ApiClient;
import com.example.petstore.auth.HttpAwareAuthenticator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authenticator for the OAuth2 Client Credentials flow.
 *
 * <p>Implements {@link HttpAwareAuthenticator} so that token exchange requests use the shared
 * {@link ApiClient} with the same transport configuration (proxy, TLS, timeouts) as regular API
 * calls.
 */
public class OAuth2ClientCredentialsAuthenticator implements HttpAwareAuthenticator {

  private final String host;
  private final String clientId;
  private final String clientSecret;
  private final String tokenUrl;
  private final List<String> scopes;
  private final OAuth2TokenManager tokenManager;

  /**
   * Create a new client credentials authenticator.
   *
   * @param host API base URL
   * @param clientId OAuth2 client ID
   * @param clientSecret OAuth2 client secret
   * @param tokenUrl token endpoint URL
   * @param scopes requested scopes
   */
  public OAuth2ClientCredentialsAuthenticator(
      String host, String clientId, String clientSecret, String tokenUrl, List<String> scopes) {
    this.host = host;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.tokenUrl = tokenUrl;
    this.scopes = List.copyOf(scopes);
    this.tokenManager = new OAuth2TokenManager();
  }

  @Override
  public void setApiClient(ApiClient apiClient) {
    tokenManager.setApiClient(apiClient);
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public Map<String, String> getAuthHeaders() {
    Map<String, String> params = new HashMap<>();
    params.put("grant_type", "client_credentials");
    params.put("client_id", clientId);
    params.put("client_secret", clientSecret);
    if (!scopes.isEmpty()) {
      params.put("scope", String.join(" ", scopes));
    }
    String token = tokenManager.getAccessToken(tokenUrl, params);
    return Collections.singletonMap("Authorization", "Bearer " + token);
  }
}
