package com.example.petstore.api.options;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.InputStream;
import javax.annotation.Nullable;

/** Options for the uploadPetDocument operation. */
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public final class UploadPetDocumentOptions {
  private final InputStream file;
  @Nullable private String documentType;
  @Nullable private String notes;

  /**
   * Creates an options instance with the required parameters.
   *
   * @param file the {@code file} parameter
   */
  public UploadPetDocumentOptions(InputStream file) {
    this.file = file;
  }

  /**
   * Returns the {@code file} parameter.
   *
   * @return the {@code file} parameter
   */
  public InputStream file() {
    return file;
  }

  /**
   * Sets the {@code documentType} parameter.
   *
   * @param documentType the {@code documentType} parameter
   * @return this options instance for chaining
   */
  public UploadPetDocumentOptions documentType(String documentType) {
    this.documentType = documentType;
    return this;
  }

  /**
   * Returns the {@code documentType} parameter.
   *
   * @return the {@code documentType} parameter, or {@code null} if unset
   */
  @Nullable
  public String documentType() {
    return documentType;
  }

  /**
   * Sets the {@code notes} parameter.
   *
   * @param notes the {@code notes} parameter
   * @return this options instance for chaining
   */
  public UploadPetDocumentOptions notes(String notes) {
    this.notes = notes;
    return this;
  }

  /**
   * Returns the {@code notes} parameter.
   *
   * @return the {@code notes} parameter, or {@code null} if unset
   */
  @Nullable
  public String notes() {
    return notes;
  }
}
