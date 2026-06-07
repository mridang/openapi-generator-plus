package com.example.petstore.api.options;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.Map;
import javax.annotation.Nullable;

/** Options for the findPetsByStatus operation. */
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public final class FindPetsByStatusOptions {
  @Nullable private String status;
  @Nullable private Map<String, String> filter;

  /** Creates an options instance with the required parameters. */
  public FindPetsByStatusOptions() {}

  /**
   * Sets the {@code status} parameter.
   *
   * @param status the {@code status} parameter
   * @return this options instance for chaining
   */
  public FindPetsByStatusOptions status(String status) {
    this.status = status;
    return this;
  }

  /**
   * Returns the {@code status} parameter.
   *
   * @return the {@code status} parameter, or {@code null} if unset
   */
  @Nullable
  public String status() {
    return status;
  }

  /**
   * Sets the {@code filter} parameter.
   *
   * @param filter the {@code filter} parameter
   * @return this options instance for chaining
   */
  public FindPetsByStatusOptions filter(Map<String, String> filter) {
    this.filter = filter;
    return this;
  }

  /**
   * Returns the {@code filter} parameter.
   *
   * @return the {@code filter} parameter, or {@code null} if unset
   */
  @Nullable
  public Map<String, String> filter() {
    return filter;
  }
}
