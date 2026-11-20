import type { Temporal } from 'temporal-polyfill';

/**
 * Options for the setPetPreferences operation.
 */
export interface SetPetPreferencesOptions {
  readonly nickname: string;
  readonly tags?: Array<string>;
  readonly note?: string;
  readonly renewalDate?: string | Date;
  readonly reminder?: Date;
  readonly intervalDays?: number;
  readonly ratio?: number;
  readonly urgent?: boolean;
  readonly snooze?: Temporal.Duration;
}
