import type { Authenticator } from "../../auth/authenticator.js";

/**
 * Options for the deletePet operation.
 */
export interface DeletePetOptions {
  readonly apiKey?: string;
  readonly auth?: Authenticator;
}
