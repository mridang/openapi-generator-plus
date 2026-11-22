/**
 * Options for the listPetCatalog operation.
 */
export interface ListPetCatalogOptions {
  /** The number of pets on a page */
  readonly limit?: number;
  /** Where to continue, as returned in next_cursor of the previous page */
  readonly cursor?: string;
}
