import type { PhotoMetadata } from '../../models/index.js';

export interface AddPetPhotosOptions {
  files: Array<Buffer>;
  metadata: PhotoMetadata;
}
