package com.example.petstore.api.options;

import com.example.petstore.models.Swatch;
import java.io.InputStream;
import java.time.OffsetDateTime;
import javax.annotation.Nullable;

/** Options for the uploadPetDocument operation. */
public final class UploadPetDocumentOptions {
  private final InputStream file;
  @Nullable private String documentType;
  @Nullable private String notes;
  @Nullable private Boolean isPrimary;
  @Nullable private Long pageCount;
  @Nullable private OffsetDateTime takenAt;
  @Nullable private Swatch swatch;

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

  /**
   * Sets the {@code isPrimary} parameter.
   *
   * @param isPrimary the {@code isPrimary} parameter
   * @return this options instance for chaining
   */
  public UploadPetDocumentOptions isPrimary(Boolean isPrimary) {
    this.isPrimary = isPrimary;
    return this;
  }

  /**
   * Returns the {@code isPrimary} parameter.
   *
   * @return the {@code isPrimary} parameter, or {@code null} if unset
   */
  @Nullable
  public Boolean isPrimary() {
    return isPrimary;
  }

  /**
   * Sets the {@code pageCount} parameter.
   *
   * @param pageCount the {@code pageCount} parameter
   * @return this options instance for chaining
   */
  public UploadPetDocumentOptions pageCount(Long pageCount) {
    this.pageCount = pageCount;
    return this;
  }

  /**
   * Returns the {@code pageCount} parameter.
   *
   * @return the {@code pageCount} parameter, or {@code null} if unset
   */
  @Nullable
  public Long pageCount() {
    return pageCount;
  }

  /**
   * Sets the {@code takenAt} parameter.
   *
   * @param takenAt the {@code takenAt} parameter
   * @return this options instance for chaining
   */
  public UploadPetDocumentOptions takenAt(OffsetDateTime takenAt) {
    this.takenAt = takenAt;
    return this;
  }

  /**
   * Returns the {@code takenAt} parameter.
   *
   * @return the {@code takenAt} parameter, or {@code null} if unset
   */
  @Nullable
  public OffsetDateTime takenAt() {
    return takenAt;
  }

  /**
   * Sets the {@code swatch} parameter.
   *
   * @param swatch the {@code swatch} parameter
   * @return this options instance for chaining
   */
  public UploadPetDocumentOptions swatch(Swatch swatch) {
    this.swatch = swatch;
    return this;
  }

  /**
   * Returns the {@code swatch} parameter.
   *
   * @return the {@code swatch} parameter, or {@code null} if unset
   */
  @Nullable
  public Swatch swatch() {
    return swatch;
  }
}
