import type { Temporal } from "temporal-polyfill";
import type { Decimal } from "../../brand.js";

/**
 * Options for the getNativeValues operation.
 */
export interface GetNativeValuesOptions {
  readonly address4?: string;
  readonly delay?: Temporal.Duration;
  readonly amount?: Decimal;
  readonly xAddress6?: string;
}
