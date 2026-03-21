import { Expose } from 'class-transformer';

export class PhotoMetadataLocation {
  @Expose({ name: 'lat' })
  lat?: number;
  @Expose({ name: 'lng' })
  lng?: number;

  constructor(data?: Partial<PhotoMetadataLocation>) {
    Object.assign(this, data);
  }
}
