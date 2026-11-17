package com.example.petstore.api.options;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Options for the setPetPreferences operation. */
public final class SetPetPreferencesOptions {
  private final String nickname;
  @Nullable private List<String> tags;
  @Nullable private String note;
  @Nullable private LocalDate renewalDate;
  @Nullable private OffsetDateTime reminder;
  @Nullable private Integer timeout;
  @Nullable private Integer maxRetries;
  @Nullable private Map<String, String> headers;

  /**
   * Creates an options instance with the required parameters.
   *
   * @param nickname the {@code nickname} parameter
   */
  public SetPetPreferencesOptions(String nickname) {
    this.nickname = nickname;
  }

  /**
   * Returns the {@code nickname} parameter.
   *
   * @return the {@code nickname} parameter
   */
  public String nickname() {
    return nickname;
  }

  /**
   * Sets the {@code tags} parameter.
   *
   * @param tags the {@code tags} parameter
   * @return this options instance for chaining
   */
  public SetPetPreferencesOptions tags(List<String> tags) {
    this.tags = tags == null ? null : java.util.List.copyOf(tags);
    return this;
  }

  /**
   * Returns the {@code tags} parameter.
   *
   * @return the {@code tags} parameter, or {@code null} if unset
   */
  @Nullable
  public List<String> tags() {
    return tags;
  }

  /**
   * Sets the {@code note} parameter.
   *
   * @param note the {@code note} parameter
   * @return this options instance for chaining
   */
  public SetPetPreferencesOptions note(String note) {
    this.note = note;
    return this;
  }

  /**
   * Returns the {@code note} parameter.
   *
   * @return the {@code note} parameter, or {@code null} if unset
   */
  @Nullable
  public String note() {
    return note;
  }

  /**
   * Sets the {@code renewalDate} parameter.
   *
   * @param renewalDate the {@code renewalDate} parameter
   * @return this options instance for chaining
   */
  public SetPetPreferencesOptions renewalDate(LocalDate renewalDate) {
    this.renewalDate = renewalDate;
    return this;
  }

  /**
   * Returns the {@code renewalDate} parameter.
   *
   * @return the {@code renewalDate} parameter, or {@code null} if unset
   */
  @Nullable
  public LocalDate renewalDate() {
    return renewalDate;
  }

  /**
   * Sets the {@code reminder} parameter.
   *
   * @param reminder the {@code reminder} parameter
   * @return this options instance for chaining
   */
  public SetPetPreferencesOptions reminder(OffsetDateTime reminder) {
    this.reminder = reminder;
    return this;
  }

  /**
   * Returns the {@code reminder} parameter.
   *
   * @return the {@code reminder} parameter, or {@code null} if unset
   */
  @Nullable
  public OffsetDateTime reminder() {
    return reminder;
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
  public SetPetPreferencesOptions timeout(@Nullable Integer timeout) {
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
  public SetPetPreferencesOptions maxRetries(@Nullable Integer maxRetries) {
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
  public SetPetPreferencesOptions headers(@Nullable Map<String, String> headers) {
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
