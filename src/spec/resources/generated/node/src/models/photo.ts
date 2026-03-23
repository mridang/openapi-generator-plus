import { Expose } from 'class-transformer';

export class Photo {
  /** @example null */
  @Expose({ name: 'id' })
  id?: number;
  /** @example null */
  @Expose({ name: 'caption' })
  caption?: string;
  /** @example null */
  @Expose({ name: 'isPrimary' })
  isPrimary?: boolean;
  /** @example null */
  @Expose({ name: 'url' })
  url?: string;

  constructor(data?: Partial<Photo>) {
    Object.assign(this, data);
  }
}
