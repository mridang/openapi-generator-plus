import { Expose, Type } from 'class-transformer';

export class DryFood {
  @Expose({ name: 'foodType' })
  foodType!: string;
  @Expose({ name: 'weightKg' })
  weightKg!: number;

  constructor(data?: Partial<DryFood>) {
    Object.assign(this, data);
  }
}
