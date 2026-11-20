import type { Temporal } from 'temporal-polyfill';

/**
 * Options for the getNativeValues operation.
 */
export interface GetNativeValuesOptions {
  readonly address4?: string;
  readonly delay?: Temporal.Duration;
  readonly amount?: number;
  readonly xAddress6?: string;
}
