/**
 * Options for the listPetOffsets operation.
 */
export interface ListPetOffsetsOptions {
  /** The offset of the first pet on the page */
  readonly offset?: number;
  /** The number of pets on a page */
  readonly limit?: number;
}
