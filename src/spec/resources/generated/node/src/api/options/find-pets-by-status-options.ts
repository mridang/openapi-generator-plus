/**
 * Options for the findPetsByStatus operation.
 */
export interface FindPetsByStatusOptions {
  status?: string;
  filter?: { [key: string]: string };
}
