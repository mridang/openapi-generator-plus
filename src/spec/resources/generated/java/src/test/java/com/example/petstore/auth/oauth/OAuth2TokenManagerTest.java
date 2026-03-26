package com.example.petstore.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.petstore.ApiResponse;
import java.lang.reflect.Field;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class OAuth2TokenManagerTest {

  @Test
  void testStoresRefreshToken() throws Exception {
    var mockClient =
        new com.example.petstore.ApiClient() {
          @Override
          public ApiResponse sendRequest(
              String method, String url, Map<String, String> headers, @Nullable Object body) {
            return new ApiResponse(
                200,
                "{\"access_token\":\"test-access\",\"refresh_token\":\"test-refresh\",\"expires_in\":3600}",
                Map.of());
          }
        };

    OAuth2TokenManager manager = new OAuth2TokenManager();
    manager.setApiClient(mockClient);
    manager.getAccessToken("http://auth/token", Map.of("grant_type", "authorization_code"));

    // The manager should store the refresh_token from the token response.
    // Check all fields to see if refresh_token value was stored.
    boolean found = false;
    for (Field field : OAuth2TokenManager.class.getDeclaredFields()) {
      field.setAccessible(true);
      Object value = field.get(manager);
      if ("test-refresh".equals(value)) {
        found = true;
        break;
      }
    }
    assertThat(found)
        .describedAs("TokenManager should store the refresh_token from the token response")
        .isTrue();
  }

  @Test
  void testExtractsAccessToken() {
    var mockClient =
        new com.example.petstore.ApiClient() {
          @Override
          public ApiResponse sendRequest(
              String method, String url, Map<String, String> headers, @Nullable Object body) {
            return new ApiResponse(
                200,
                "{\"access_token\":\"expected-token\",\"expires_in\":3600}",
                Map.of());
          }
        };

    OAuth2TokenManager manager = new OAuth2TokenManager();
    manager.setApiClient(mockClient);
    String token =
        manager.getAccessToken("http://auth/token", Map.of("grant_type", "client_credentials"));

    assertThat(token).isEqualTo("expected-token");
  }

  @Test
  void testDetectsTokenExpiry() {
    var callCount = new java.util.concurrent.atomic.AtomicInteger(0);
    var mockClient =
        new com.example.petstore.ApiClient() {
          @Override
          public ApiResponse sendRequest(
              String method, String url, Map<String, String> headers, @Nullable Object body) {
            int count = callCount.incrementAndGet();
            return new ApiResponse(
                200,
                "{\"access_token\":\"token-" + count + "\",\"expires_in\":1}",
                Map.of());
          }
        };

    OAuth2TokenManager manager = new OAuth2TokenManager();
    manager.setApiClient(mockClient);

    // First call fetches a token with expires_in=1 (already expired after subtracting 30s buffer)
    String first =
        manager.getAccessToken("http://auth/token", Map.of("grant_type", "client_credentials"));
    // Second call should detect expiry and fetch a new token
    String second =
        manager.getAccessToken("http://auth/token", Map.of("grant_type", "client_credentials"));

    assertThat(callCount.get())
        .describedAs("Expired token should trigger a new fetch")
        .isEqualTo(2);
    assertThat(second).isNotEqualTo(first);
  }
}
