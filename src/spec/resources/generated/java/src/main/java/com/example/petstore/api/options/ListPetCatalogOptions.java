package com.example.petstore.api.options;

import java.util.Map;
import javax.annotation.Nullable;

/** Options for the listPetCatalog operation. */
public final class ListPetCatalogOptions {
  @Nullable private Integer limit;
  @Nullable private String cursor;
  @Nullable private Integer timeout;
  @Nullable private Integer maxRetries;
  @Nullable private Map<String, String> headers;

  /** Creates an options instance with the required parameters. */
  public ListPetCatalogOptions() {}

  /**
   * Sets the {@code limit} parameter.
   *
   * <p>The number of pets on a page.
   *
   * @param limit The number of pets on a page.
   * @return this options instance for chaining
   */
  public ListPetCatalogOptions limit(Integer limit) {
    this.limit = limit;
    return this;
  }

  /**
   * Returns the {@code limit} parameter.
   *
   * <p>The number of pets on a page.
   *
   * @return the {@code limit} parameter, or {@code null} if unset
   */
  @Nullable
  public Integer limit() {
    return limit;
  }

  /**
   * Sets the {@code cursor} parameter.
   *
   * <p>Where to continue, as returned in next_cursor of the previous page.
   *
   * @param cursor Where to continue, as returned in next_cursor of the previous page.
   * @return this options instance for chaining
   */
  public ListPetCatalogOptions cursor(String cursor) {
    this.cursor = cursor;
    return this;
  }

  /**
   * Returns the {@code cursor} parameter.
   *
   * <p>Where to continue, as returned in next_cursor of the previous page.
   *
   * @return the {@code cursor} parameter, or {@code null} if unset
   */
  @Nullable
  public String cursor() {
    return cursor;
  }

  /**
   * Returns an independent copy of these options: the same value for every parameter, the same
   * timeout, retry count and headers. A paged list uses it to send each page with its own cursor.
   *
   * @return the copy
   */
  public ListPetCatalogOptions copy() {
    ListPetCatalogOptions copy = new ListPetCatalogOptions();
    copy.limit = this.limit;
    copy.cursor = this.cursor;
    copy.timeout = timeout;
    copy.maxRetries = maxRetries;
    copy.headers = headers;
    return copy;
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
  public ListPetCatalogOptions timeout(@Nullable Integer timeout) {
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
  public ListPetCatalogOptions maxRetries(@Nullable Integer maxRetries) {
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
  public ListPetCatalogOptions headers(@Nullable Map<String, String> headers) {
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
