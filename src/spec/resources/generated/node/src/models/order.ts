import { Expose } from 'class-transformer';

export class Order {
  /** @example 10 */
  @Expose({ name: 'id' })
  id?: number;
  /** @example 198772 */
  @Expose({ name: 'petId' })
  petId?: number;
  /** @example 7 */
  @Expose({ name: 'quantity' })
  quantity?: number;
  /** @example null */
  @Expose({ name: 'shipDate' })
  shipDate?: string;
  /**
   * Order Status
   * @example approved
   */
  @Expose({ name: 'status' })
  status?: string;
  /** @example null */
  @Expose({ name: 'complete' })
  complete?: boolean;

  constructor(data?: Partial<Order>) {
    Object.assign(this, data);
  }
}

/**
 * @export
 */
export const OrderStatusEnum = {
  Placed: 'placed',
  Approved: 'approved',
  Delivered: 'delivered',
  UnknownDefaultOpenApi: '11184809'
} as const;
export type OrderStatusEnum = (typeof OrderStatusEnum)[keyof typeof OrderStatusEnum];
