package com.example.petstore.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.petstore.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class OpenIdConnectAuthenticatorTest {

  private static final String DISCOVERY_JSON =
      "{\"authorization_endpoint\":\"http://auth/authorize\","
          + "\"token_endpoint\":\"http://auth/token\"}";

  @Test
  void testBuildsAuthorizationUrl() {
    var mockClient =
        new com.example.petstore.ApiClient() {
          @Override
          public com.example.petstore.ApiResponse sendRequest(
              String method, String url, Map<String, String> headers, @Nullable Object body) {
            return new ApiResponse(200, DISCOVERY_JSON, Map.of());
          }
        };

    var auth =
        new OpenIdConnectAuthenticator(
            "http://api",
            "http://auth/.well-known/openid-configuration",
            "client-id",
            "client-secret",
            "http://callback",
            List.of("openid"));
    auth.setApiClient(mockClient);

    String url = auth.buildAuthorizationUrl("state123");
    assertThat(url).contains("response_type=code");
    assertThat(url).contains("client_id=client-id");
    assertThat(url).contains("state=state123");
  }

  @Test
  void testObtainsToken() {
    AtomicInteger callCount = new AtomicInteger(0);
    var mockClient =
        new com.example.petstore.ApiClient() {
          @Override
          public com.example.petstore.ApiResponse sendRequest(
              String method, String url, Map<String, String> headers, @Nullable Object body) {
            int count = callCount.incrementAndGet();
            if (count == 1) {
              return new ApiResponse(200, DISCOVERY_JSON, Map.of());
            }
            return new ApiResponse(
                200, "{\"access_token\":\"oidc-token\",\"expires_in\":3600}", Map.of());
          }
        };

    var auth =
        new OpenIdConnectAuthenticator(
            "http://api",
            "http://auth/.well-known/openid-configuration",
            "client-id",
            "client-secret",
            "http://callback",
            List.of("openid"));
    auth.setApiClient(mockClient);

    // Discovery happens on first use, then delegates to auth code flow
    auth.exchangeCode("test-code");
    Map<String, String> headers = auth.getAuthHeaders();

    assertThat(headers).containsKey("Authorization");
    assertThat(headers.get("Authorization")).startsWith("Bearer ");
  }
}
