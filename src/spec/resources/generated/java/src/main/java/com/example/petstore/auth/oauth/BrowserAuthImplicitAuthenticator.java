package com.example.petstore.auth.oauth;

import java.util.List;

public final class BrowserAuthImplicitAuthenticator extends OAuth2ImplicitAuthenticator {
  public BrowserAuthImplicitAuthenticator(String host) {
    super(host, "https://auth.example.com/authorize", List.of("read"));
  }
}
