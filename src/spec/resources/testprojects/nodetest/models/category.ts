import { Expose } from 'class-transformer';

export class Category {
  @Expose({ name: 'id' })
  id?: number;
  @Expose({ name: 'name' })
  name?: string;

  constructor(data?: Partial<Category>) {
    Object.assign(this, data);
  }
}
