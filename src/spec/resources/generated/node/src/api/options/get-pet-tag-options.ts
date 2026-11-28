import type { PetCriteria } from "../../models/index.js";

/**
 * Options for the getPetTag operation.
 */
export interface GetPetTagOptions {
  readonly colors?: Array<string>;
  readonly sizes?: Array<string>;
  readonly filter?: string;
  /** Query value whose RFC 3986 reserved characters must be sent literally (OAS allowReserved), for example a version expression such as v1.0/beta:rc1 keeping the slash and colon. */
  readonly revision?: string;
  /** Object-valued query parameter, form style exploded (the OAS default for query) */
  readonly criteria?: PetCriteria;
  readonly criteriaCsv?: PetCriteria;
  readonly criteriaDeep?: PetCriteria;
  /** Opaque bytes, base64 on the wire (OAS format byte) */
  readonly fingerprint?: Buffer;
  readonly xCriteria?: PetCriteria;
  readonly xFingerprint?: Buffer;
}
