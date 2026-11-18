/**
 * Options for the getPetByNote operation.
 */
export interface GetPetByNoteOptions {
  readonly note: string | null;
  readonly rank: number | null;
}
