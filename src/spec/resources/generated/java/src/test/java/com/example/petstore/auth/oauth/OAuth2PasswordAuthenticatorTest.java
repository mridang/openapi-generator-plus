package com.example.petstore.auth.oauth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.petstore.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class OAuth2PasswordAuthenticatorTest {

  @Test
  void testSendsGrantType() {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    var mockClient =
        new com.example.petstore.ApiClient() {
          @Override
          public com.example.petstore.ApiResponse sendRequest(
              String method, String url, Map<String, String> headers, @Nullable Object body) {
            capturedBody.set((String) body);
            return new ApiResponse(
                200, "{\"access_token\":\"token\",\"expires_in\":3600}", Map.of());
          }
        };

    var auth =
        new OAuth2PasswordAuthenticator(
            "http://api",
            "client-id",
            "client-secret",
            "http://auth/token",
            "user",
            "pass",
            List.of("read"));
    auth.setApiClient(mockClient);
    auth.getAuthHeaders();

    assertThat(capturedBody.get()).contains("grant_type=password");
  }

  @Test
  void testSendsUsernameAndPassword() {
    AtomicReference<String> capturedBody = new AtomicReference<>();
    var mockClient =
        new com.example.petstore.ApiClient() {
          @Override
          public com.example.petstore.ApiResponse sendRequest(
              String method, String url, Map<String, String> headers, @Nullable Object body) {
            capturedBody.set((String) body);
            return new ApiResponse(
                200, "{\"access_token\":\"token\",\"expires_in\":3600}", Map.of());
          }
        };

    var auth =
        new OAuth2PasswordAuthenticator(
            "http://api",
            "client-id",
            "client-secret",
            "http://auth/token",
            "testuser",
            "testpass",
            List.of("read"));
    auth.setApiClient(mockClient);
    auth.getAuthHeaders();

    String body = capturedBody.get();
    assertThat(body).contains("username=testuser");
    assertThat(body).contains("password=testpass");
  }
}
