import { Expose } from 'class-transformer';

export class Category {
  /** @example 1 */
  @Expose({ name: 'id' })
  id?: number;
  /** @example Dogs */
  @Expose({ name: 'name' })
  name?: string;

  constructor(data?: Partial<Category>) {
    Object.assign(this, data);
  }
}
