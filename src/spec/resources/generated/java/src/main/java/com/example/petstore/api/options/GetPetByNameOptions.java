package com.example.petstore.api.options;

/** Options for the getPetByName operation. */
public final class GetPetByNameOptions {
  private final String category;
  private final String xCorrelationId;

  /**
   * Creates an options instance with the required parameters.
   *
   * @param category the {@code category} parameter
   * @param xCorrelationId the {@code X-Correlation-Id} parameter
   */
  public GetPetByNameOptions(String category, String xCorrelationId) {
    this.category = category;
    this.xCorrelationId = xCorrelationId;
  }

  /**
   * Returns the {@code category} parameter.
   *
   * @return the {@code category} parameter
   */
  public String category() {
    return category;
  }

  /**
   * Returns the {@code X-Correlation-Id} parameter.
   *
   * @return the {@code X-Correlation-Id} parameter
   */
  public String xCorrelationId() {
    return xCorrelationId;
  }
}
