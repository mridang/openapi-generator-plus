/**
 * Options for the findPetsByStatus operation.
 */
export interface FindPetsByStatusOptions {
  readonly status?: string;
  readonly filter?: { [key: string]: string };
}
