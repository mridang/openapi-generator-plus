import { Expose } from 'class-transformer';

export class SetPetAvatarRequest {
  /**
   * Base64-encoded image data
   * @example null
   */
  @Expose({ name: 'data' })
  data!: string;
  /** @example image/jpeg */
  @Expose({ name: 'mimeType' })
  mimeType!: string;

  constructor(data?: Partial<SetPetAvatarRequest>) {
    Object.assign(this, data);
  }
}
