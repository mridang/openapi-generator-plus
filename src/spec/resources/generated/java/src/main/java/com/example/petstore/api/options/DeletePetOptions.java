package com.example.petstore.api.options;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

/** Options for the deletePet operation. */
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public final class DeletePetOptions {
  @Nullable private String apiKey;

  /** Creates an options instance with the required parameters. */
  public DeletePetOptions() {}

  /**
   * Sets the {@code api_key} parameter.
   *
   * @param apiKey the {@code api_key} parameter
   * @return this options instance for chaining
   */
  public DeletePetOptions apiKey(String apiKey) {
    this.apiKey = apiKey;
    return this;
  }

  /**
   * Returns the {@code api_key} parameter.
   *
   * @return the {@code api_key} parameter, or {@code null} if unset
   */
  @Nullable
  public String apiKey() {
    return apiKey;
  }
}
