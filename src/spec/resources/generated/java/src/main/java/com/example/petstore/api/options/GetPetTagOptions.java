package com.example.petstore.api.options;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import javax.annotation.Nullable;

/** Options for the getPetTag operation. */
@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2"})
public final class GetPetTagOptions {
  @Nullable private List<String> colors;
  @Nullable private List<String> sizes;
  @Nullable private String filter;

  /** Creates an options instance with the required parameters. */
  public GetPetTagOptions() {}

  /**
   * Sets the {@code colors} parameter.
   *
   * @param colors the {@code colors} parameter
   * @return this options instance for chaining
   */
  public GetPetTagOptions colors(List<String> colors) {
    this.colors = colors;
    return this;
  }

  /**
   * Returns the {@code colors} parameter.
   *
   * @return the {@code colors} parameter, or {@code null} if unset
   */
  @Nullable
  public List<String> colors() {
    return colors;
  }

  /**
   * Sets the {@code sizes} parameter.
   *
   * @param sizes the {@code sizes} parameter
   * @return this options instance for chaining
   */
  public GetPetTagOptions sizes(List<String> sizes) {
    this.sizes = sizes;
    return this;
  }

  /**
   * Returns the {@code sizes} parameter.
   *
   * @return the {@code sizes} parameter, or {@code null} if unset
   */
  @Nullable
  public List<String> sizes() {
    return sizes;
  }

  /**
   * Sets the {@code filter} parameter.
   *
   * @param filter the {@code filter} parameter
   * @return this options instance for chaining
   */
  public GetPetTagOptions filter(String filter) {
    this.filter = filter;
    return this;
  }

  /**
   * Returns the {@code filter} parameter.
   *
   * @return the {@code filter} parameter, or {@code null} if unset
   */
  @Nullable
  public String filter() {
    return filter;
  }
}
