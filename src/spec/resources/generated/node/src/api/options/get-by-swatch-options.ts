import type { Swatch } from "../../models/index.js";

/**
 * Options for the getBySwatch operation.
 */
export interface GetBySwatchOptions {
  readonly querySwatch?: Swatch;
  readonly preferredSwatch?: Swatch;
}
