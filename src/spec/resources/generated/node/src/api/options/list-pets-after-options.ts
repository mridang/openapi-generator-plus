/**
 * Options for the listPetsAfter operation.
 */
export interface ListPetsAfterOptions {
  /** The id of the last pet of the previous page */
  readonly startingAfter?: string;
  /** The number of pets on a page */
  readonly limit?: number;
}
