package com.example.petstore.auth;

import java.util.Base64;
import java.util.Collections;
import java.util.Map;

/** Authenticator for HTTP Basic authentication. */
public class BasicAuthenticator implements Authenticator {

  private final String host;
  private final String authHeader;

  public BasicAuthenticator(String host, String username, String password) {
    this.host = host;
    this.authHeader =
        "Basic "
            + Base64.getEncoder()
                .encodeToString(
                    (username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @Override
  public String getHost() {
    return host;
  }

  @Override
  public Map<String, String> getAuthHeaders() {
    return Collections.singletonMap("Authorization", authHeader);
  }
}
