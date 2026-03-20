import { Expose } from 'class-transformer';

export class WetFood {
  @Expose({ name: 'foodType' })
  foodType!: string;
  @Expose({ name: 'volumeMl' })
  volumeMl!: number;

  constructor(data?: Partial<WetFood>) {
    Object.assign(this, data);
  }
}
