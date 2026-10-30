package com.example.petstore.api.options;

import java.time.LocalDate;
import java.util.List;
import javax.annotation.Nullable;

/** Options for the getPetSummary operation. */
public final class GetPetSummaryOptions {
  private final String session;
  @Nullable private List<LocalDate> days;

  /**
   * Creates an options instance with the required parameters.
   *
   * @param session Session token: \"a<b>\" & it's required.
   */
  public GetPetSummaryOptions(String session) {
    this.session = session;
  }

  /**
   * Returns the {@code session} parameter.
   *
   * <p>Session token: \"a<b>\" & it's required.
   *
   * @return the {@code session} parameter
   */
  public String session() {
    return session;
  }

  /**
   * Sets the {@code days} parameter.
   *
   * <p>Extra dates to summarise.
   *
   * @param days Extra dates to summarise.
   * @return this options instance for chaining
   */
  public GetPetSummaryOptions days(List<LocalDate> days) {
    this.days = days == null ? null : java.util.List.copyOf(days);
    return this;
  }

  /**
   * Returns the {@code days} parameter.
   *
   * <p>Extra dates to summarise.
   *
   * @return the {@code days} parameter, or {@code null} if unset
   */
  @Nullable
  public List<LocalDate> days() {
    return days;
  }
}
