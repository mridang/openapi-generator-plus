package com.example.petstore.auth;

public final class AdminBasicAuthenticator extends BasicAuthenticator {
  public AdminBasicAuthenticator(String host, String username, String password) {
    super(host, username, password);
  }
}
