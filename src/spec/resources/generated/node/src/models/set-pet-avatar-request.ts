import { Expose } from 'class-transformer';

export class SetPetAvatarRequest {
  @Expose({ name: 'data' })
  data!: string;
  @Expose({ name: 'mimeType' })
  mimeType!: string;

  constructor(data?: Partial<SetPetAvatarRequest>) {
    Object.assign(this, data);
  }
}
