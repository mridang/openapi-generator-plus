package com.example.petstore.api.options;

import com.example.petstore.models.Swatch;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.util.Map;
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
  @Nullable private Integer timeout;
  @Nullable private Integer maxRetries;
  @Nullable private Map<String, String> headers;

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

  /**
   * Sets the end-to-end timeout for this call only, in milliseconds. It replaces the transport's
   * timeout for this call.
   *
   * @param timeout the timeout in milliseconds (positive), or {@code null} to keep the transport's
   *     timeout
   * @return this options instance for chaining
   * @throws IllegalArgumentException if {@code timeout} is not positive
   */
  public UploadPetDocumentOptions timeout(@Nullable Integer timeout) {
    if (timeout != null && timeout <= 0) {
      throw new IllegalArgumentException(
          "Invalid timeout: must be a positive number of milliseconds (got " + timeout + ")");
    }
    this.timeout = timeout;
    return this;
  }

  /**
   * Returns the per-call timeout in milliseconds.
   *
   * @return the timeout, or {@code null} to use the transport's timeout
   */
  @Nullable
  public Integer timeout() {
    return timeout;
  }

  /**
   * Sets the retry count for this call only. It replaces the transport's retry count for this call.
   *
   * @param maxRetries the number of retries ({@code 0} disables retries), or {@code null} to keep
   *     the transport's retry count
   * @return this options instance for chaining
   * @throws IllegalArgumentException if {@code maxRetries} is negative
   */
  public UploadPetDocumentOptions maxRetries(@Nullable Integer maxRetries) {
    if (maxRetries != null && maxRetries < 0) {
      throw new IllegalArgumentException(
          "Invalid maxRetries: must not be negative (got " + maxRetries + ")");
    }
    this.maxRetries = maxRetries;
    return this;
  }

  /**
   * Returns the per-call retry count.
   *
   * @return the retry count, or {@code null} to use the transport's retry count
   */
  @Nullable
  public Integer maxRetries() {
    return maxRetries;
  }

  /**
   * Sets extra request headers for this call only. They are sent after the default headers and
   * replace a default header of the same name; the authentication headers are applied after them.
   *
   * @param headers the headers to send, or {@code null} for none
   * @return this options instance for chaining
   */
  public UploadPetDocumentOptions headers(@Nullable Map<String, String> headers) {
    this.headers = headers == null ? null : java.util.Map.copyOf(headers);
    return this;
  }

  /**
   * Returns the per-call request headers.
   *
   * @return the headers, or {@code null} when none were set
   */
  @Nullable
  public Map<String, String> headers() {
    return headers;
  }
}
