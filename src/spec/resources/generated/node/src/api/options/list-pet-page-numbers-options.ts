/**
 * Options for the listPetPageNumbers operation.
 */
export interface ListPetPageNumbersOptions {
  /** The number of the page, from 1 */
  readonly page?: number;
  /** The number of pets on a page */
  readonly perPage?: number;
}
