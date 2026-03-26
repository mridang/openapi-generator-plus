package com.example.petstore.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.petstore.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class OAuth2AuthorizationCodeAuthenticatorTest {

  @Test
  void testBuildsAuthorizationUrl() {
    var auth =
        new OAuth2AuthorizationCodeAuthenticator(
            "http://api",
            "my-client-id",
            "my-secret",
            "http://auth/authorize",
            "http://auth/token",
            "http://callback",
            List.of("read", "write"));

    String url = auth.buildAuthorizationUrl("state123");

    assertThat(url).contains("response_type=code");
    assertThat(url).contains("client_id=my-client-id");
    assertThat(url).contains("redirect_uri=");
    assertThat(url).contains("state=state123");
    assertThat(url).contains("scope=read+write");
  }

  @Test
  void testExchangesCodeForToken() {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    var mockClient =
        new com.example.petstore.ApiClient() {
          @Override
          public com.example.petstore.ApiResponse sendRequest(
              String method, String url, Map<String, String> headers, @Nullable Object body) {
            capturedBody.set((String) body);
            return new ApiResponse(
                200,
                "{\"access_token\":\"test-token\",\"refresh_token\":\"test-refresh\",\"expires_in\":3600}",
                Map.of());
          }
        };

    var auth =
        new OAuth2AuthorizationCodeAuthenticator(
            "http://api",
            "my-client-id",
            "my-secret",
            "http://auth/authorize",
            "http://auth/token",
            "http://callback",
            List.of("read"));
    auth.setApiClient(mockClient);
    auth.exchangeCode("test-code");

    String body = capturedBody.get();
    assertThat(body).contains("grant_type=authorization_code");
    assertThat(body).contains("code=test-code");
    assertThat(body).contains("client_id=my-client-id");
    assertThat(body).contains("client_secret=my-secret");
  }

  @Test
  void testRefreshIncludesRefreshToken() {
    AtomicReference<String> lastBody = new AtomicReference<>();
    AtomicInteger callCount = new AtomicInteger(0);
    var mockClient =
        new com.example.petstore.ApiClient() {
          @Override
          public com.example.petstore.ApiResponse sendRequest(
              String method, String url, Map<String, String> headers, @Nullable Object body) {
            lastBody.set((String) body);
            int count = callCount.incrementAndGet();
            return new ApiResponse(
                200,
                "{\"access_token\":\"token-"
                    + count
                    + "\",\"refresh_token\":\"refresh-abc\",\"expires_in\":1}",
                Map.of());
          }
        };

    var auth =
        new OAuth2AuthorizationCodeAuthenticator(
            "http://api",
            "my-client-id",
            "my-secret",
            "http://auth/authorize",
            "http://auth/token",
            "http://callback",
            List.of("read"));
    auth.setApiClient(mockClient);

    // Initial code exchange (token expires immediately: expires_in=1, minus 30s buffer)
    auth.exchangeCode("test-code");
    assertThat(callCount.get()).isEqualTo(1);

    // Getting auth headers triggers refresh since token is expired
    auth.getAuthHeaders();
    assertThat(callCount.get()).isEqualTo(2);

    // The refresh request should include the actual refresh_token value
    String refreshBody = lastBody.get();
    assertThat(refreshBody)
        .describedAs("Refresh request should include the refresh_token value from initial exchange")
        .contains("refresh_token=refresh-abc");
  }
}
