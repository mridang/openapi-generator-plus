import type { PhotoMetadata } from '../../models/index.js';

/**
 * Options for the addPetPhotos operation.
 */
export interface AddPetPhotosOptions {
  files: Array<Buffer>;
  metadata: PhotoMetadata;
}
