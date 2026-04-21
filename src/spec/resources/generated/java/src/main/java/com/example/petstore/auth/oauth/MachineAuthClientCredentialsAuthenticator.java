package com.example.petstore.auth.oauth;

import java.util.List;

public final class MachineAuthClientCredentialsAuthenticator
    extends OAuth2ClientCredentialsAuthenticator {
  public MachineAuthClientCredentialsAuthenticator(
      String host, String clientId, String clientSecret) {
    super(
        host,
        clientId,
        clientSecret,
        "https://auth.example.com/oauth/token",
        List.of("pets:read", "pets:write"));
  }
}
