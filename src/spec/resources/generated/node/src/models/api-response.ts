import { Expose } from 'class-transformer';

export class ApiResponse {
  /** @example null */
  @Expose({ name: 'code' })
  code?: number;
  /** @example null */
  @Expose({ name: 'type' })
  type?: string;
  /** @example null */
  @Expose({ name: 'message' })
  message?: string;

  constructor(data?: Partial<ApiResponse>) {
    Object.assign(this, data);
  }
}
