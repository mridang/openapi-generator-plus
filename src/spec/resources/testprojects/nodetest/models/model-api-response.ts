import { Expose, Type } from 'class-transformer';

export class ModelApiResponse {
  @Expose({ name: 'code' })
  code?: number;
  @Expose({ name: 'type' })
  type?: string;
  @Expose({ name: 'message' })
  message?: string;

  constructor(data?: Partial<ModelApiResponse>) {
    Object.assign(this, data);
  }
}
