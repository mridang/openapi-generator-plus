import type { Authenticator } from "../../auth/authenticator.js";

/**
 * Options for the deletePet operation.
 */
export interface DeletePetOptions {
  /** Session cookie used for authentication */
  readonly apiKey?: string;
  readonly auth?: Authenticator;
}
