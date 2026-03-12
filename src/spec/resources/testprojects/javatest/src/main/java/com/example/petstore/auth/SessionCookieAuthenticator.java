package com.example.petstore.auth;

public final class SessionCookieAuthenticator extends ApiKeyAuthenticator {
  public SessionCookieAuthenticator(String host, String apiKey) {
    super(host, "SESSION_ID", apiKey, ApiKeyLocation.COOKIE);
  }
}
