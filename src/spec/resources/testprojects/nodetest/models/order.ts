import { Expose, Type } from 'class-transformer';

export class Order {
  @Expose({ name: 'id' })
  id?: number;
  @Expose({ name: 'petId' })
  petId?: number;
  @Expose({ name: 'quantity' })
  quantity?: number;
  @Expose({ name: 'shipDate' })
  shipDate?: Date;
  @Expose({ name: 'status' })
  status?: string;
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
