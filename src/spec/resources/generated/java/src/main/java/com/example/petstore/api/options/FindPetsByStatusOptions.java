package com.example.petstore.api.options;

import java.time.LocalDate;
import java.util.Map;
import javax.annotation.Nullable;

/** Options for the findPetsByStatus operation. */
public final class FindPetsByStatusOptions {
  @Deprecated @Nullable private String status;
  @Nullable private Map<String, String> filter;
  @Nullable private LocalDate bornAfter;
  @Nullable private LocalDate reportDate;

  /** Creates an options instance with the required parameters. */
  public FindPetsByStatusOptions() {}

  /**
   * Sets the {@code status} parameter.
   *
   * <p>Status values that need to be considered for filter.
   *
   * @param status Status values that need to be considered for filter.
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
}
