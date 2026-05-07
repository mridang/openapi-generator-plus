package com.example.petstore.api.options;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

/** Options for the deletePet operation. */
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public final class DeletePetOptions {
  @Nullable private String apiKey;

  public DeletePetOptions() {}

  public DeletePetOptions apiKey(String apiKey) {
    this.apiKey = apiKey;
    return this;
  }

  @Nullable
  public String apiKey() {
    return apiKey;
  }
}
