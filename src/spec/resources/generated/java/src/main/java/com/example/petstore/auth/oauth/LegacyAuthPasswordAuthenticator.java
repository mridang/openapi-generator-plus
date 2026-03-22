package com.example.petstore.auth.oauth;

import java.util.List;

public final class LegacyAuthPasswordAuthenticator extends OAuth2PasswordAuthenticator {
  public LegacyAuthPasswordAuthenticator(
      String host, String clientId, String clientSecret, String username, String password) {
    super(
        host,
        clientId,
        clientSecret,
        "https://auth.example.com/oauth/token",
        username,
        password,
        List.of("read"));
  }
}
