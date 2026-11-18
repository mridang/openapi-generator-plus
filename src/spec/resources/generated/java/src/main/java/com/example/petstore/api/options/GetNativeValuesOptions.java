package com.example.petstore.api.options;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import javax.annotation.Nullable;

/** Options for the getNativeValues operation. */
public final class GetNativeValuesOptions {
  @Nullable private String address4;
  @Nullable private Duration wait;
  @Nullable private BigDecimal amount;
  @Nullable private String xAddress6;
  @Nullable private Integer timeout;
  @Nullable private Integer maxRetries;
  @Nullable private Map<String, String> headers;

  /** Creates an options instance with the required parameters. */
  public GetNativeValuesOptions() {}

  /**
   * Sets the {@code address4} parameter.
   *
   * @param address4 the {@code address4} parameter
   * @return this options instance for chaining
   */
  public GetNativeValuesOptions address4(String address4) {
    this.address4 = address4;
    return this;
  }

  /**
   * Returns the {@code address4} parameter.
   *
   * @return the {@code address4} parameter, or {@code null} if unset
   */
  @Nullable
  public String address4() {
    return address4;
  }

  /**
   * Sets the {@code wait} parameter.
   *
   * @param wait the {@code wait} parameter
   * @return this options instance for chaining
   */
  public GetNativeValuesOptions wait(Duration wait) {
    this.wait = wait;
    return this;
  }

  /**
   * Returns the {@code wait} parameter.
   *
   * @return the {@code wait} parameter, or {@code null} if unset
   */
  @Nullable
  public Duration wait() {
    return wait;
  }

  /**
   * Sets the {@code amount} parameter.
   *
   * @param amount the {@code amount} parameter
   * @return this options instance for chaining
   */
  public GetNativeValuesOptions amount(BigDecimal amount) {
    this.amount = amount;
    return this;
  }

  /**
   * Returns the {@code amount} parameter.
   *
   * @return the {@code amount} parameter, or {@code null} if unset
   */
  @Nullable
  public BigDecimal amount() {
    return amount;
  }

  /**
   * Sets the {@code X-Address6} parameter.
   *
   * @param xAddress6 the {@code X-Address6} parameter
   * @return this options instance for chaining
   */
  public GetNativeValuesOptions xAddress6(String xAddress6) {
    this.xAddress6 = xAddress6;
    return this;
  }

  /**
   * Returns the {@code X-Address6} parameter.
   *
   * @return the {@code X-Address6} parameter, or {@code null} if unset
   */
  @Nullable
  public String xAddress6() {
    return xAddress6;
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
  public GetNativeValuesOptions timeout(@Nullable Integer timeout) {
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
  public GetNativeValuesOptions maxRetries(@Nullable Integer maxRetries) {
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
  public GetNativeValuesOptions headers(@Nullable Map<String, String> headers) {
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
