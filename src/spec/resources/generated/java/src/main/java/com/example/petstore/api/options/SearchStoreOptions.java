package com.example.petstore.api.options;

import com.example.petstore.models.PhotoMetadata;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Options for the searchStore operation. */
public final class SearchStoreOptions {
  @Nullable private PhotoMetadata q;
  @Nullable private Set<String> labels;
  @Nullable private LocalTime at;
  @Nullable private Map<String, String> xFilter;
  @Nullable private Integer timeout;
  @Nullable private Integer maxRetries;
  @Nullable private Map<String, String> headers;

  /** Creates an options instance with the required parameters. */
  public SearchStoreOptions() {}

  /**
   * Sets the {@code q} parameter.
   *
   * <p>JSON-encoded query parameter (OAS `content`, not `schema`).
   *
   * @param q JSON-encoded query parameter (OAS `content`, not `schema`).
   * @return this options instance for chaining
   */
  public SearchStoreOptions q(PhotoMetadata q) {
    this.q = q;
    return this;
  }

  /**
   * Returns the {@code q} parameter.
   *
   * <p>JSON-encoded query parameter (OAS `content`, not `schema`).
   *
   * @return the {@code q} parameter, or {@code null} if unset
   */
  @Nullable
  public PhotoMetadata q() {
    return q;
  }

  /**
   * Sets the {@code labels} parameter.
   *
   * @param labels the {@code labels} parameter
   * @return this options instance for chaining
   */
  public SearchStoreOptions labels(Set<String> labels) {
    this.labels = labels == null ? null : java.util.Set.copyOf(labels);
    return this;
  }

  /**
   * Returns the {@code labels} parameter.
   *
   * @return the {@code labels} parameter, or {@code null} if unset
   */
  @Nullable
  public Set<String> labels() {
    return labels;
  }

  /**
   * Sets the {@code at} parameter.
   *
   * @param at the {@code at} parameter
   * @return this options instance for chaining
   */
  public SearchStoreOptions at(LocalTime at) {
    this.at = at;
    return this;
  }

  /**
   * Returns the {@code at} parameter.
   *
   * @return the {@code at} parameter, or {@code null} if unset
   */
  @Nullable
  public LocalTime at() {
    return at;
  }

  /**
   * Sets the {@code X-Filter} parameter.
   *
   * @param xFilter the {@code X-Filter} parameter
   * @return this options instance for chaining
   */
  public SearchStoreOptions xFilter(Map<String, String> xFilter) {
    this.xFilter = xFilter == null ? null : java.util.Map.copyOf(xFilter);
    return this;
  }

  /**
   * Returns the {@code X-Filter} parameter.
   *
   * @return the {@code X-Filter} parameter, or {@code null} if unset
   */
  @Nullable
  public Map<String, String> xFilter() {
    return xFilter;
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
  public SearchStoreOptions timeout(@Nullable Integer timeout) {
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
  public SearchStoreOptions maxRetries(@Nullable Integer maxRetries) {
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
  public SearchStoreOptions headers(@Nullable Map<String, String> headers) {
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
