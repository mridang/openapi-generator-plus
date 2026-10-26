package com.example.petstore.api.options;

import java.time.OffsetDateTime;
import javax.annotation.Nullable;

/** Options for the getStockItem operation. */
public final class GetStockItemOptions {
  @Nullable private OffsetDateTime asOf;

  /** Creates an options instance with the required parameters. */
  public GetStockItemOptions() {}

  /**
   * Sets the {@code asOf} parameter.
   *
   * <p>Only consider stock as of this instant.
   *
   * @param asOf Only consider stock as of this instant.
   * @return this options instance for chaining
   */
  public GetStockItemOptions asOf(OffsetDateTime asOf) {
    this.asOf = asOf;
    return this;
  }

  /**
   * Returns the {@code asOf} parameter.
   *
   * <p>Only consider stock as of this instant.
   *
   * @return the {@code asOf} parameter, or {@code null} if unset
   */
  @Nullable
  public OffsetDateTime asOf() {
    return asOf;
  }
}
