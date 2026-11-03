package com.example.petstore.api.options;

import java.util.List;
import javax.annotation.Nullable;

/** Options for the getPetTag operation. */
public final class GetPetTagOptions {
  @Nullable private List<String> colors;
  @Nullable private List<String> sizes;
  @Nullable private String filter;
  @Nullable private String revision;

  /** Creates an options instance with the required parameters. */
  public GetPetTagOptions() {}

  /**
   * Sets the {@code colors} parameter.
   *
   * @param colors the {@code colors} parameter
   * @return this options instance for chaining
   */
  public GetPetTagOptions colors(List<String> colors) {
    this.colors = colors == null ? null : java.util.List.copyOf(colors);
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
    this.sizes = sizes == null ? null : java.util.List.copyOf(sizes);
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

  /**
   * Sets the {@code revision} parameter.
   *
   * <p>Query value whose RFC 3986 reserved characters must be sent literally (OAS allowReserved),
   * for example a version expression such as v1.0/beta:rc1 keeping the slash and colon.
   *
   * @param revision Query value whose RFC 3986 reserved characters must be sent literally (OAS
   *     allowReserved), for example a version expression such as v1.0/beta:rc1 keeping the slash
   *     and colon.
   * @return this options instance for chaining
   */
  public GetPetTagOptions revision(String revision) {
    this.revision = revision;
    return this;
  }

  /**
   * Returns the {@code revision} parameter.
   *
   * <p>Query value whose RFC 3986 reserved characters must be sent literally (OAS allowReserved),
   * for example a version expression such as v1.0/beta:rc1 keeping the slash and colon.
   *
   * @return the {@code revision} parameter, or {@code null} if unset
   */
  @Nullable
  public String revision() {
    return revision;
  }
}
