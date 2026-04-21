package com.example.petstore.auth;

public final class PetStoreBearerAuthenticator extends BearerAuthenticator {
  public PetStoreBearerAuthenticator(String host, String token) {
    super(host, token);
  }
}
