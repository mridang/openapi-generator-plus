package com.example.petstore.api.options;

import com.example.petstore.models.PhotoMetadata;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Options for the addPetPhotos operation. */
public final class AddPetPhotosOptions {
  private final List<InputStream> files;
  private final PhotoMetadata metadata;
  @Nullable private Integer timeout;
  @Nullable private Integer maxRetries;
  @Nullable private Map<String, String> headers;

  /**
   * Creates an options instance with the required parameters.
   *
   * @param files the {@code files} parameter
   * @param metadata the {@code metadata} parameter
   */
  public AddPetPhotosOptions(List<InputStream> files, PhotoMetadata metadata) {
    this.files = java.util.List.copyOf(files);
    this.metadata = metadata;
  }

  /**
   * Returns the {@code files} parameter.
   *
   * @return the {@code files} parameter
   */
  public List<InputStream> files() {
    return files;
  }

  /**
   * Returns the {@code metadata} parameter.
   *
   * @return the {@code metadata} parameter
   */
  public PhotoMetadata metadata() {
    return metadata;
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
  public AddPetPhotosOptions timeout(@Nullable Integer timeout) {
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
  public AddPetPhotosOptions maxRetries(@Nullable Integer maxRetries) {
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
  public AddPetPhotosOptions headers(@Nullable Map<String, String> headers) {
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
