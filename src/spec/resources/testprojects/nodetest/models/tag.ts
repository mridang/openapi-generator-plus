import { Expose } from 'class-transformer';

export class Tag {
  @Expose({ name: 'id' })
  id?: number;
  @Expose({ name: 'name' })
  name?: string;

  constructor(data?: Partial<Tag>) {
    Object.assign(this, data);
  }
}
