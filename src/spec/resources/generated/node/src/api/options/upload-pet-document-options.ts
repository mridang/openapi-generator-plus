import type { Swatch } from "../../models/index.js";

/**
 * Options for the uploadPetDocument operation.
 */
export interface UploadPetDocumentOptions {
  readonly file: Buffer;
  readonly documentType?: string;
  readonly notes?: string;
  readonly isPrimary?: boolean;
  readonly pageCount?: number;
  readonly takenAt?: Date;
  readonly swatch?: Swatch;
}
