/**
 * Options for the findPetsByStatus operation.
 */
export interface FindPetsByStatusOptions {
  /**
   * Status values that need to be considered for filter
   *
   * @deprecated This parameter is deprecated.
   */
  readonly status?: string;
  /** Filter criteria as key-value pairs */
  readonly filter?: { [key: string]: string };
}
