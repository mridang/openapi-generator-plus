import { Expose } from 'class-transformer';

export class Medication {
  /** @example null */
  @Expose({ name: 'drugName' })
  drugName!: string;
  /** @example null */
  @Expose({ name: 'dosage' })
  dosage?: string;

  constructor(data?: Partial<Medication>) {
    Object.assign(this, data);
    if (data !== undefined) {
      if (this.drugName == null) {
        throw new Error('drugName is required');
      }
    }
  }
}
