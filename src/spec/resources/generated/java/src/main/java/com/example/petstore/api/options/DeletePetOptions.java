package com.example.petstore.api.options;

import com.example.petstore.auth.Authenticator;
import javax.annotation.Nullable;

/** Options for the deletePet operation. */
public final class DeletePetOptions {
  @Nullable private String apiKey;
  @Nullable private Authenticator auth;

  /** Creates an options instance with the required parameters. */
  public DeletePetOptions() {}

  /**
   * Sets the {@code api_key} parameter.
   *
   * <p>Session cookie used for authentication.
   *
   * @param apiKey Session cookie used for authentication.
   * @return this options instance for chaining
   */
  public DeletePetOptions apiKey(String apiKey) {
    this.apiKey = apiKey;
    return this;
  }

  /**
   * Returns the {@code api_key} parameter.
   *
   * <p>Session cookie used for authentication.
   *
   * @return the {@code api_key} parameter, or {@code null} if unset
   */
  @Nullable
  public String apiKey() {
    return apiKey;
  }

  /**
   * Sets the per-operation authenticator. When set, it overrides the client's configured
   * credentials for this call only.
   *
   * @param auth the authenticator to use for this operation
   * @return this options instance for chaining
   */
  public DeletePetOptions auth(Authenticator auth) {
    this.auth = auth;
    return this;
  }

  /**
   * Returns the per-operation authenticator.
   *
   * @return the authenticator, or {@code null} to use the configured credentials
   */
  @Nullable
  public Authenticator auth() {
    return auth;
  }
}
