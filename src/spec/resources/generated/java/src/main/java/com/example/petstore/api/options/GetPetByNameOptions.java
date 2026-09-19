package com.example.petstore.api.options;


/** Options for the getPetByName operation. */
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
