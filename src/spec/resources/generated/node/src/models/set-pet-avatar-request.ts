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
    if (data !== undefined) {
      if (this.data == null) {
        throw new Error('data is required');
      }
      if (this.mimeType == null) {
        throw new Error('mimeType is required');
      }
    }
  }
}
