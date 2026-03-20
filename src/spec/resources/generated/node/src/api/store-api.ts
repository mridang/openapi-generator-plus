import type { ApiClient } from '../api-client.js';
import { BaseApi } from './base-api.js';
import { Configuration } from '../configuration.js';
import { ObjectSerializer } from '../object-serializer.js';
import { ValueSerializer } from '../value-serializer.js';
import { Order } from '../models/index.js';

/**
 * StoreApi provides methods for the Store API group.
 * Access to Petstore orders
 */
export class StoreApi extends BaseApi {
  constructor(config?: Configuration, apiClient?: ApiClient) {
    super(config, apiClient);
  }

  /**
   * Delete purchase order by ID
   * @param orderId ID of the order to delete (required)
   */
  async deleteOrder(orderId: number): Promise<void> {
    if (orderId == null) {
      throw new Error('Missing required parameter "orderId" when calling deleteOrder');
    }
    let path = `/store/order/{orderId}`;
    path = path.replace(`{${'orderId'}}`, ValueSerializer.serialize(orderId, 'path', 'number') as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    (await this.invokeApi('DELETE', path, queryParams, headerParams, null, [], 'application/json', null, null)) as void;
  }

  /**
   * Returns pet inventories by status
   * @return { [key: string]: number }
   */
  async getInventory(): Promise<{ [key: string]: number }> {
    const path = `/store/inventory`;
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return (await this.invokeApi(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => json as { [key: string]: number },
      null
    )) as { [key: string]: number };
  }

  /**
   * Find purchase order by ID
   * @param orderId ID of order to return (required)
   * @return Order
   */
  async getOrderById(orderId: number): Promise<Order> {
    if (orderId == null) {
      throw new Error('Missing required parameter "orderId" when calling getOrderById');
    }
    let path = `/store/order/{orderId}`;
    path = path.replace(`{${'orderId'}}`, ValueSerializer.serialize(orderId, 'path', 'number') as string);
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return (await this.invokeApi(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Order),
      null
    )) as Order;
  }

  /**
   * Place an order for a pet
   * @param order  (optional)
   * @return Order
   */
  async placeOrder(order?: Order): Promise<Order> {
    const path = `/store/order`;
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return (await this.invokeApi(
      'POST',
      path,
      queryParams,
      headerParams,
      order,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Order),
      null
    )) as Order;
  }
}
