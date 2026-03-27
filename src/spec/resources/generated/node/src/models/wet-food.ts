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
    if (data !== undefined) {
      if (this.foodType == null) {
        throw new Error('foodType is required');
      }
      if (this.volumeMl == null) {
        throw new Error('volumeMl is required');
      }
    }
  }
}
