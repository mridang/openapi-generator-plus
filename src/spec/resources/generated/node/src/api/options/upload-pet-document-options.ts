/**
 * Options for the uploadPetDocument operation.
 */
export interface UploadPetDocumentOptions {
  file: Buffer;
  documentType?: string;
  notes?: string;
}
