import { Expose } from 'class-transformer';

export class PhotoMetadataLocation {
  /** @example null */
  @Expose({ name: 'lat' })
  lat?: number;
  /** @example null */
  @Expose({ name: 'lng' })
  lng?: number;

  constructor(data?: Partial<PhotoMetadataLocation>) {
    Object.assign(this, data);
  }
}
