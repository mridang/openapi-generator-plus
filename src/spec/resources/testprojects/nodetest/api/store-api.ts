import type { ApiClient } from '../ApiClient.js';
import { BaseApi } from './BaseApi.js';
import { Configuration } from '../Configuration.js';
import { ObjectSerializer } from '../ObjectSerializer.js';
import type { Order } from '../models/index.js';
import { OrderFromJSON, OrderToJSON } from '../models/index.js';

/**
 * StoreApi provides methods for the Store API group.
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
    const path = `/store/order/{orderId}`.replace(
      `{${'orderId'}}`,
      encodeURIComponent(ObjectSerializer.toPathValue(orderId))
    );
    const queryParams: Record<string, any> = {};
    const headerParams: Record<string, string> = {};
    (await this.invokeApi('DELETE', path, queryParams, headerParams, null, [], 'application/json', null)) as void;
  }

  /**
   * Returns pet inventories by status
   * @return { [key: string]: number }
   */
  async getInventory(): Promise<{ [key: string]: number }> {
    const path = `/store/inventory`;
    const queryParams: Record<string, any> = {};
    const headerParams: Record<string, string> = {};
    return this.invokeApi(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: any) => json as { [key: string]: number }
    ) as { [key: string]: number };
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
    const path = `/store/order/{orderId}`.replace(
      `{${'orderId'}}`,
      encodeURIComponent(ObjectSerializer.toPathValue(orderId))
    );
    const queryParams: Record<string, any> = {};
    const headerParams: Record<string, string> = {};
    return this.invokeApi(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: any) => ObjectSerializer.deserialize(json, OrderFromJSON)
    ) as Order;
  }

  /**
   * Place an order for a pet
   * @param order  (optional)
   * @return Order
   */
  async placeOrder(order?: Order): Promise<Order> {
    const path = `/store/order`;
    const queryParams: Record<string, any> = {};
    const headerParams: Record<string, string> = {};
    return this.invokeApi(
      'POST',
      path,
      queryParams,
      headerParams,
      ObjectSerializer.serialize(order, OrderToJSON),
      ['application/json'],
      'application/json',
      (json: any) => ObjectSerializer.deserialize(json, OrderFromJSON)
    ) as Order;
  }
}
