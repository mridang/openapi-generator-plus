import type { Authenticator } from '../../auth/authenticator.js';

/**
 * Options for the addPet operation.
 */
export interface AddPetOptions {
  readonly auth?: Authenticator;
}
