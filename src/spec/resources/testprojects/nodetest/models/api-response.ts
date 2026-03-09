import { Expose } from 'class-transformer';

export class ApiResponse {
  @Expose({ name: 'code' })


  code?: number;
  @Expose({ name: 'type' })


  type?: string;
  @Expose({ name: 'message' })


  message?: string;

  constructor(data?: Partial<ApiResponse>) {
    Object.assign(this, data);
  }
}
