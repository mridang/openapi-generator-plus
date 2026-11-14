package com.example.petstore.api.options;

import com.example.petstore.models.PhotoMetadata;
import java.time.LocalTime;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Options for the searchStore operation. */
public final class SearchStoreOptions {
  @Nullable private PhotoMetadata q;
  @Nullable private Set<String> labels;
  @Nullable private LocalTime at;
  @Nullable private Map<String, String> xFilter;

  /** Creates an options instance with the required parameters. */
  public SearchStoreOptions() {}

  /**
   * Sets the {@code q} parameter.
   *
   * <p>JSON-encoded query parameter (OAS `content`, not `schema`).
   *
   * @param q JSON-encoded query parameter (OAS `content`, not `schema`).
   * @return this options instance for chaining
   */
  public SearchStoreOptions q(PhotoMetadata q) {
    this.q = q;
    return this;
  }

  /**
   * Returns the {@code q} parameter.
   *
   * <p>JSON-encoded query parameter (OAS `content`, not `schema`).
   *
   * @return the {@code q} parameter, or {@code null} if unset
   */
  @Nullable
  public PhotoMetadata q() {
    return q;
  }

  /**
   * Sets the {@code labels} parameter.
   *
   * @param labels the {@code labels} parameter
   * @return this options instance for chaining
   */
  public SearchStoreOptions labels(Set<String> labels) {
    this.labels = labels == null ? null : java.util.Set.copyOf(labels);
    return this;
  }

  /**
   * Returns the {@code labels} parameter.
   *
   * @return the {@code labels} parameter, or {@code null} if unset
   */
  @Nullable
  public Set<String> labels() {
    return labels;
  }

  /**
   * Sets the {@code at} parameter.
   *
   * @param at the {@code at} parameter
   * @return this options instance for chaining
   */
  public SearchStoreOptions at(LocalTime at) {
    this.at = at;
    return this;
  }

  /**
   * Returns the {@code at} parameter.
   *
   * @return the {@code at} parameter, or {@code null} if unset
   */
  @Nullable
  public LocalTime at() {
    return at;
  }

  /**
   * Sets the {@code X-Filter} parameter.
   *
   * @param xFilter the {@code X-Filter} parameter
   * @return this options instance for chaining
   */
  public SearchStoreOptions xFilter(Map<String, String> xFilter) {
    this.xFilter = xFilter == null ? null : java.util.Map.copyOf(xFilter);
    return this;
  }

  /**
   * Returns the {@code X-Filter} parameter.
   *
   * @return the {@code X-Filter} parameter, or {@code null} if unset
   */
  @Nullable
  public Map<String, String> xFilter() {
    return xFilter;
  }
}
