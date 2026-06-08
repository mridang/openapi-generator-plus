/**
 * Options for the setPetPreferences operation.
 */
export interface SetPetPreferencesOptions {
  readonly nickname: string;
  readonly tags?: Array<string>;
  readonly note?: string;
}
