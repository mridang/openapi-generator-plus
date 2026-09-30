package com.example.petstore.api.options;

import java.time.LocalDate;
import java.util.List;
import javax.annotation.Nullable;

/** Options for the setPetPreferences operation. */
public final class SetPetPreferencesOptions {
  private final String nickname;
  @Nullable private List<String> tags;
  @Nullable private String note;
  @Nullable private LocalDate renewalDate;

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
}
