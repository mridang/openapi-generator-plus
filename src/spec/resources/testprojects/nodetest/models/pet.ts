import { Category } from './category.js';
import { Tag } from './tag.js';
import { Expose, Type } from 'class-transformer';

export class Pet {
  @Expose({ name: 'id' })


  id?: number;
  @Expose({ name: 'name' })


  name!: string;
  @Expose({ name: 'category' })
  @Type(() => Category)


  category?: Category;
  @Expose({ name: 'photoUrls' })


  photoUrls!: Array<string>;
  @Expose({ name: 'tags' })

  @Type(() => Tag)

  tags?: Array<Tag>;
  @Expose({ name: 'status' })


  status?: string;

  constructor(data?: Partial<Pet>) {
    Object.assign(this, data);
  }
}

/**
 * @export
 */
export const PetStatusEnum = {
  Available: 'available',
  Pending: 'pending',
  Sold: 'sold',
  UnknownDefaultOpenApi: '11184809'
} as const;
export type PetStatusEnum = (typeof PetStatusEnum)[keyof typeof PetStatusEnum];
