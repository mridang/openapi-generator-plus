import { Expose } from 'class-transformer';

export class DryFood {
  /** @example null */
  @Expose({ name: 'foodType' })
  foodType!: string;
  /** @example null */
  @Expose({ name: 'weightKg' })
  weightKg!: number;

  constructor(data?: Partial<DryFood>) {
    Object.assign(this, data);
    if (data !== undefined) {
      if (this.foodType == null) {
        throw new Error('foodType is required');
      }
      if (this.weightKg == null) {
        throw new Error('weightKg is required');
      }
    }
  }
}
