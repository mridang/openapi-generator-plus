import type { PhotoMetadata } from '../../models/index.js';

/**
 * Options for the addPetPhotos operation.
 */
export interface AddPetPhotosOptions {
  readonly files: Array<Buffer>;
  readonly metadata: PhotoMetadata;
}
