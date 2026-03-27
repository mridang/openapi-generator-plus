import { Expose } from 'class-transformer';

export class Metadata {
  /** @example null */
  @Expose({ name: 'createdAt' })
  createdAt?: string;

  [key: string]: unknown;

  constructor(data?: Partial<Metadata>) {
    Object.assign(this, data);
  }
}
