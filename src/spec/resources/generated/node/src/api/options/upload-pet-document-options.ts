/**
 * Options for the uploadPetDocument operation.
 */
export interface UploadPetDocumentOptions {
  readonly file: Buffer;
  readonly documentType?: string;
  readonly notes?: string;
}
