package com.example.petstore.api.options;

import com.example.petstore.auth.Authenticator;
import javax.annotation.Nullable;

/** Options for the addPet operation. */
public final class AddPetOptions {
  @Nullable private Authenticator auth;

  /** Creates an options instance with the required parameters. */
  public AddPetOptions() {}

  /**
   * Sets the per-operation authenticator. When set, it overrides the client's configured
   * credentials for this call only.
   *
   * @param auth the authenticator to use for this operation
   * @return this options instance for chaining
   */
  public AddPetOptions auth(Authenticator auth) {
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
