import { PhotoMetadataLocation } from './photo-metadata-location.js';
import { Expose, Type } from 'class-transformer';

export class PhotoMetadata {
  /** @example null */
  @Expose({ name: 'caption' })
  caption?: string;
  /** @example null */
  @Expose({ name: 'isPrimary' })
  isPrimary?: boolean;
  /** @example null */
  @Expose({ name: 'takenAt' })
  takenAt?: string;
  /** @example null */
  @Expose({ name: 'location' })
  @Type(() => PhotoMetadataLocation)
  location?: PhotoMetadataLocation;

  constructor(data?: Partial<PhotoMetadata>) {
    Object.assign(this, data);
  }
}
