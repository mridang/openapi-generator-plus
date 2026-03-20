import { PhotoMetadataLocation } from './photo-metadata-location.js';
import { Expose, Type } from 'class-transformer';

export class PhotoMetadata {
  @Expose({ name: 'caption' })
  caption?: string;
  @Expose({ name: 'isPrimary' })
  isPrimary?: boolean;
  @Expose({ name: 'takenAt' })
  takenAt?: string;
  @Expose({ name: 'location' })
  @Type(() => PhotoMetadataLocation)
  location?: PhotoMetadataLocation;

  constructor(data?: Partial<PhotoMetadata>) {
    Object.assign(this, data);
  }
}
