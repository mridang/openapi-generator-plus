import { Expose } from 'class-transformer';

export class WetFood {
  /** @example null */
  @Expose({ name: 'foodType' })
  foodType!: string;
  /** @example null */
  @Expose({ name: 'volumeMl' })
  volumeMl!: number;

  constructor(data?: Partial<WetFood>) {
    Object.assign(this, data);
  }
}
