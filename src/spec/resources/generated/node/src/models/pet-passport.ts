import { Pet } from './pet.js';
import { Expose, Type } from 'class-transformer';

export class PetPassport {
  /** @example null */
  @Expose({ name: 'pet' })
  @Type(() => Pet)
  pet?: Pet;
  /**
   * Base64-encoded primary thumbnail
   * @example [B@33f11aa7
   */
  @Expose({ name: 'thumbnail' })
  thumbnail?: string;
  /**
   * Base64-encoded scans of each passport page
   * @example null
   */
  @Expose({ name: 'scans' })
  scans?: Array<string>;
  /** @example null */
  @Expose({ name: 'issuedAt' })
  issuedAt?: string;

  constructor(data?: Partial<PetPassport>) {
    Object.assign(this, data);
  }
}
