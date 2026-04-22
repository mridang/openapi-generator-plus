import { Expose } from 'class-transformer';

export class Surgery {
  /** @example null */
  @Expose({ name: 'procedureName' })
  procedureName!: string;
  /** @example null */
  @Expose({ name: 'durationMinutes' })
  durationMinutes?: number;

  constructor(data?: Partial<Surgery>) {
    Object.assign(this, data);
    if (data !== undefined) {
      if (this.procedureName == null) {
        throw new Error('procedureName is required');
      }
    }
  }
}
