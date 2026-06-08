import type { Authenticator } from '../../auth/authenticator.js';

/**
 * Options for the deletePet operation.
 */
export interface DeletePetOptions {
  apiKey?: string;
  auth?: Authenticator;
}
