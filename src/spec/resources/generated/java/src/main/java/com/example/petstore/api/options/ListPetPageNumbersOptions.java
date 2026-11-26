package com.example.petstore.api.options;

import java.util.Map;
import javax.annotation.Nullable;

/** Options for the listPetPageNumbers operation. */
public final class ListPetPageNumbersOptions {
  @Nullable private Integer page;
  @Nullable private Integer perPage;
  @Nullable private Integer timeout;
  @Nullable private Integer maxRetries;
  @Nullable private Map<String, String> headers;

  /** Creates an options instance with the required parameters. */
  public ListPetPageNumbersOptions() {}

  /**
   * Sets the {@code page} parameter.
   *
   * <p>The number of the page, from 1.
   *
   * @param page The number of the page, from 1.
   * @return this options instance for chaining
   */
  public ListPetPageNumbersOptions page(Integer page) {
    this.page = page;
    return this;
  }

  /**
   * Returns the {@code page} parameter.
   *
   * <p>The number of the page, from 1.
   *
   * @return the {@code page} parameter, or {@code null} if unset
   */
  @Nullable
  public Integer page() {
    return page;
  }

  /**
   * Sets the {@code per_page} parameter.
   *
   * <p>The number of pets on a page.
   *
   * @param perPage The number of pets on a page.
   * @return this options instance for chaining
   */
  public ListPetPageNumbersOptions perPage(Integer perPage) {
    this.perPage = perPage;
    return this;
  }

  /**
   * Returns the {@code per_page} parameter.
   *
   * <p>The number of pets on a page.
   *
   * @return the {@code per_page} parameter, or {@code null} if unset
   */
  @Nullable
  public Integer perPage() {
    return perPage;
  }

  /**
   * Returns an independent copy of these options: the same value for every parameter, the same
   * timeout, retry count and headers. A paged list uses it to send each page with its own cursor.
   *
   * @return the copy
   */
  public ListPetPageNumbersOptions copy() {
    ListPetPageNumbersOptions copy = new ListPetPageNumbersOptions();
    copy.page = this.page;
    copy.perPage = this.perPage;
    copy.timeout = timeout;
    copy.maxRetries = maxRetries;
    copy.headers = headers == null ? null : java.util.Map.copyOf(headers);
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
  public ListPetPageNumbersOptions timeout(@Nullable Integer timeout) {
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
  public ListPetPageNumbersOptions maxRetries(@Nullable Integer maxRetries) {
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
  public ListPetPageNumbersOptions headers(@Nullable Map<String, String> headers) {
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
