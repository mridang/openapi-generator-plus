/**
 * Options for the attachPetFiles operation.
 */
export interface AttachPetFilesOptions {
  readonly file: Buffer | Blob;
  readonly caption?: string;
}
