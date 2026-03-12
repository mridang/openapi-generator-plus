package com.example.petstore.auth.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.StringJoiner;
import javax.annotation.Nullable;

public class OAuth2TokenManager {

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  @Nullable private String accessToken;
  @Nullable private Instant tokenExpiry;

  public OAuth2TokenManager() {
    this.httpClient = HttpClient.newHttpClient();
    this.objectMapper = new ObjectMapper();
  }

  public synchronized String getAccessToken(String tokenUrl, Map<String, String> params) {
    if (accessToken != null && tokenExpiry != null && Instant.now().isBefore(tokenExpiry)) {
      return accessToken;
    }
    fetchToken(tokenUrl, params);
    if (accessToken == null) {
      throw new IllegalStateException("Token fetch did not return an access token");
    }
    return accessToken;
  }

  public synchronized void setAccessToken(String token) {
    this.accessToken = token;
    this.tokenExpiry = null;
  }

  private void fetchToken(String tokenUrl, Map<String, String> params) {
    StringJoiner body = new StringJoiner("&");
    for (Map.Entry<String, String> entry : params.entrySet()) {
      body.add(
          URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8)
              + "="
              + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
    }

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
            .build();

    try {
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new RuntimeException(
            "Token request failed with status " + response.statusCode() + ": " + response.body());
      }
      JsonNode json = objectMapper.readTree(response.body());
      this.accessToken = json.get("access_token").asText();
      if (json.has("expires_in")) {
        long expiresIn = json.get("expires_in").asLong();
        this.tokenExpiry = Instant.now().plusSeconds(expiresIn - 30);
      }
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Failed to fetch OAuth2 token", e);
    }
  }
}
