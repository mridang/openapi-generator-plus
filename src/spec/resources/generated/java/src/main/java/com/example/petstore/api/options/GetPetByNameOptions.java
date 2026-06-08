package com.example.petstore.api.options;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/** Options for the getPetByName operation. */
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public final class GetPetByNameOptions {
  private final String category;

  /**
   * Creates an options instance with the required parameters.
   *
   * @param category the {@code category} parameter
   */
  public GetPetByNameOptions(String category) {
    this.category = category;
  }

  /**
   * Returns the {@code category} parameter.
   *
   * @return the {@code category} parameter
   */
  public String category() {
    return category;
  }
}
