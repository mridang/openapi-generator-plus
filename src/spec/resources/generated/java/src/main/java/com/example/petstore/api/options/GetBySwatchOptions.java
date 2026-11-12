package com.example.petstore.api.options;

import com.example.petstore.models.Swatch;
import javax.annotation.Nullable;

/** Options for the getBySwatch operation. */
public final class GetBySwatchOptions {
  @Nullable private Swatch querySwatch;
  @Nullable private Swatch preferredSwatch;

  /** Creates an options instance with the required parameters. */
  public GetBySwatchOptions() {}

  /**
   * Sets the {@code querySwatch} parameter.
   *
   * @param querySwatch the {@code querySwatch} parameter
   * @return this options instance for chaining
   */
  public GetBySwatchOptions querySwatch(Swatch querySwatch) {
    this.querySwatch = querySwatch;
    return this;
  }

  /**
   * Returns the {@code querySwatch} parameter.
   *
   * @return the {@code querySwatch} parameter, or {@code null} if unset
   */
  @Nullable
  public Swatch querySwatch() {
    return querySwatch;
  }

  /**
   * Sets the {@code Preferred-Swatch} parameter.
   *
   * @param preferredSwatch the {@code Preferred-Swatch} parameter
   * @return this options instance for chaining
   */
  public GetBySwatchOptions preferredSwatch(Swatch preferredSwatch) {
    this.preferredSwatch = preferredSwatch;
    return this;
  }

  /**
   * Returns the {@code Preferred-Swatch} parameter.
   *
   * @return the {@code Preferred-Swatch} parameter, or {@code null} if unset
   */
  @Nullable
  public Swatch preferredSwatch() {
    return preferredSwatch;
  }
}
