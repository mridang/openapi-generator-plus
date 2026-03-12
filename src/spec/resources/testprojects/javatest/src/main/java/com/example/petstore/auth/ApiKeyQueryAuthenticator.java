package com.example.petstore.auth;

public final class ApiKeyQueryAuthenticator extends ApiKeyAuthenticator {
  public ApiKeyQueryAuthenticator(String host, String apiKey) {
    super(host, "api_key", apiKey, ApiKeyLocation.QUERY);
  }
}
