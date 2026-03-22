import { Pet } from './pet.js';
import { Expose, Type } from 'class-transformer';

export class PetPassport {
  @Expose({ name: 'pet' })
  @Type(() => Pet)
  pet?: Pet;
  @Expose({ name: 'thumbnail' })
  thumbnail?: string;
  @Expose({ name: 'scans' })
  scans?: Array<string>;
  @Expose({ name: 'issuedAt' })
  issuedAt?: string;

  constructor(data?: Partial<PetPassport>) {
    Object.assign(this, data);
  }
}
