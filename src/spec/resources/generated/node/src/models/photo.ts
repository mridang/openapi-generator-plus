import { Expose } from 'class-transformer';

export class Photo {
  @Expose({ name: 'id' })
  id?: number;
  @Expose({ name: 'caption' })
  caption?: string;
  @Expose({ name: 'isPrimary' })
  isPrimary?: boolean;
  @Expose({ name: 'url' })
  url?: string;

  constructor(data?: Partial<Photo>) {
    Object.assign(this, data);
  }
}
