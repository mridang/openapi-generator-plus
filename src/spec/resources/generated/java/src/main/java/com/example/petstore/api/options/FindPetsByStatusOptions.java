package com.example.petstore.api.options;

import java.time.LocalDate;
import java.util.Map;
import javax.annotation.Nullable;

/** Options for the findPetsByStatus operation. */
public final class FindPetsByStatusOptions {
  @Deprecated @Nullable private String status;
  @Nullable private Map<String, String> filter;
  @Nullable private LocalDate bornAfter;
  @Nullable private Integer n2ndPage;
  @Nullable private LocalDate reportDate;
  @Nullable private Integer timeout;
  @Nullable private Integer maxRetries;
  @Nullable private Map<String, String> headers;

  /** Creates an options instance with the required parameters. */
  public FindPetsByStatusOptions() {}

  /**
   * Sets the {@code status} parameter.
   *
   * <p>Status values that need to be considered for filter.
   *
   * @param status Status values that need to be considered for filter. Example — Available only:
   *     {@code available} Example — Sold pets: {@code sold}
   * @return this options instance for chaining
   * @deprecated This parameter is deprecated.
   */
  @Deprecated
  public FindPetsByStatusOptions status(String status) {
    this.status = status;
    return this;
  }

  /**
   * Returns the {@code status} parameter.
   *
   * <p>Status values that need to be considered for filter.
   *
   * @return the {@code status} parameter, or {@code null} if unset
   * @deprecated This parameter is deprecated.
   */
  @Nullable
  @Deprecated
  public String status() {
    return status;
  }

  /**
   * Sets the {@code filter} parameter.
   *
   * <p>Filter criteria as key-value pairs.
   *
   * @param filter Filter criteria as key-value pairs.
   * @return this options instance for chaining
   */
  public FindPetsByStatusOptions filter(Map<String, String> filter) {
    this.filter = filter == null ? null : java.util.Map.copyOf(filter);
    return this;
  }

  /**
   * Returns the {@code filter} parameter.
   *
   * <p>Filter criteria as key-value pairs.
   *
   * @return the {@code filter} parameter, or {@code null} if unset
   */
  @Nullable
  public Map<String, String> filter() {
    return filter;
  }

  /**
   * Sets the {@code bornAfter} parameter.
   *
   * <p>Only return pets born on or after this date.
   *
   * @param bornAfter Only return pets born on or after this date.
   * @return this options instance for chaining
   */
  public FindPetsByStatusOptions bornAfter(LocalDate bornAfter) {
    this.bornAfter = bornAfter;
    return this;
  }

  /**
   * Returns the {@code bornAfter} parameter.
   *
   * <p>Only return pets born on or after this date.
   *
   * @return the {@code bornAfter} parameter, or {@code null} if unset
   */
  @Nullable
  public LocalDate bornAfter() {
    return bornAfter;
  }

  /**
   * Sets the {@code 2ndPage} parameter.
   *
   * @param n2ndPage the {@code 2ndPage} parameter
   * @return this options instance for chaining
   */
  public FindPetsByStatusOptions n2ndPage(Integer n2ndPage) {
    this.n2ndPage = n2ndPage;
    return this;
  }

  /**
   * Returns the {@code 2ndPage} parameter.
   *
   * @return the {@code 2ndPage} parameter, or {@code null} if unset
   */
  @Nullable
  public Integer n2ndPage() {
    return n2ndPage;
  }

  /**
   * Sets the {@code Report-Date} parameter.
   *
   * <p>Reference date for the report.
   *
   * @param reportDate Reference date for the report.
   * @return this options instance for chaining
   */
  public FindPetsByStatusOptions reportDate(LocalDate reportDate) {
    this.reportDate = reportDate;
    return this;
  }

  /**
   * Returns the {@code Report-Date} parameter.
   *
   * <p>Reference date for the report.
   *
   * @return the {@code Report-Date} parameter, or {@code null} if unset
   */
  @Nullable
  public LocalDate reportDate() {
    return reportDate;
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
  public FindPetsByStatusOptions timeout(@Nullable Integer timeout) {
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
  public FindPetsByStatusOptions maxRetries(@Nullable Integer maxRetries) {
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
  public FindPetsByStatusOptions headers(@Nullable Map<String, String> headers) {
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
