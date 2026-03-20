package com.example.petstore.auth.oauth;

import java.util.List;

public final class UserAuthAuthorizationCodeAuthenticator
    extends OAuth2AuthorizationCodeAuthenticator {
  public UserAuthAuthorizationCodeAuthenticator(
      String host, String clientId, String clientSecret, String redirectUri) {
    super(
        host,
        clientId,
        clientSecret,
        "https://auth.example.com/authorize",
        "https://auth.example.com/oauth/token",
        redirectUri,
        List.of("pets:read", "pets:write"));
  }
}
