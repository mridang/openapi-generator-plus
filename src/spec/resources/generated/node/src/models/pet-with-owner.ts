import { Category } from './category.js';
import { Tag } from './tag.js';
import { Expose, Type } from 'class-transformer';

/**
 * A pet record extended with owner information
 */
export class PetWithOwner {
  /** @example 10 */
  @Expose({ name: 'id' })
  id?: number;
  /** @example doggie */
  @Expose({ name: 'name' })
  name!: string;
  /** @example null */
  @Expose({ name: 'category' })
  @Type(() => Category)
  category?: Category;
  /** @example null */
  @Expose({ name: 'photoUrls' })
  photoUrls!: Set<string>;
  /** @example null */
  @Expose({ name: 'tags' })
  @Type(() => Tag)
  tags?: Array<Tag>;
  /**
   * pet status in the store
   * @example null
   * @deprecated This property is deprecated.
   */
  @Expose({ name: 'status' })
  status?: string;
  /** @example null */
  @Expose({ name: 'ownerName' })
  ownerName!: string;
  /** @example null */
  @Expose({ name: 'ownerEmail' })
  ownerEmail?: string;

  constructor(data?: Partial<PetWithOwner>) {
    Object.assign(this, data);
    if (data !== undefined) {
      if (this.name == null) {
        throw new Error('name is required');
      }
      if (this.photoUrls == null) {
        throw new Error('photoUrls is required');
      }
      if (this.ownerName == null) {
        throw new Error('ownerName is required');
      }
    }
    if (this.status != null) {
      const statusValues = Object.values(PetWithOwnerStatusEnum);
      if (!(statusValues as readonly unknown[]).includes(this.status)) {
        this.status = statusValues[statusValues.length - 1] as (typeof statusValues)[number];
      }
    }
  }
}

/**
 * @export
 */
export enum PetWithOwnerStatusEnum {
  Available = 'available',
  Pending = 'pending',
  Sold = 'sold',
  UnknownDefaultOpenApi = '11184809',
}
