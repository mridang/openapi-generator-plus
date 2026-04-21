package com.example.petstore.auth;

public final class ServiceTokenAuthenticator extends BearerAuthenticator {
  public ServiceTokenAuthenticator(String host, String token) {
    super(host, token);
  }
}
