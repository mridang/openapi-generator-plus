package com.example.petstore.auth;

import java.util.Collections;
import java.util.Map;

/**
 * Abstract base class providing default implementations for optional {@link Authenticator} methods.
 * Concrete authenticators should extend this class instead of implementing {@link Authenticator}
 * directly.
 */
public abstract class BaseAuthenticator implements Authenticator {

  @Override
  public abstract String getHost();

  @Override
  public abstract Map<String, String> getAuthHeaders();

  @Override
  public Map<String, String> getQueryParams() {
    return Collections.emptyMap();
  }

  @Override
  public Map<String, String> getCookieParams() {
    return Collections.emptyMap();
  }
}
