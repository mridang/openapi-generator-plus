import { Expose } from 'class-transformer';

/**
 * Tags are deprecated, use categories instead
 *
 * @deprecated This schema is deprecated.
 */
export class Tag {
  @Expose({ name: 'id' })
  id?: number;
  @Expose({ name: 'name' })
  name?: string;

  constructor(data?: Partial<Tag>) {
    Object.assign(this, data);
  }
}
