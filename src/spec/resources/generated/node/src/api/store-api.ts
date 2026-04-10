import type { ApiClient } from '../api-client.js';
import type { ApiResult } from '../api-result.js';
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
  constructor(apiClient?: ApiClient, config?: Configuration) {
    super(apiClient, config);
  }

  /**
   * Delete purchase order by ID
   * @param orderId ID of the order to delete (required)
   * @throws {ApiError} if fails to make API call
   */
  async deleteOrder(orderId: number): Promise<void> {
    if (orderId == null) {
      throw new Error('Missing required parameter "orderId" when calling deleteOrder');
    }
    await this.deleteOrderWithHttpInfo(orderId);
  }

  /**
   * Delete purchase order by ID (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async deleteOrderWithHttpInfo(orderId: number): Promise<ApiResult<void>> {
    if (orderId == null) {
      throw new Error('Missing required parameter "orderId" when calling deleteOrder');
    }
    let path = `/store/order/{orderId}`;
    path = path.replace(
      `{${'orderId'}}`,
      ValueSerializer.serializeStyled('orderId', orderId, 'path', 'number', null, 'simple', false) as string
    );
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'DELETE',
      path,
      queryParams,
      headerParams,
      null,
      [],
      'application/json',
      null,
      null
    );
  }

  /**
   * Returns pet inventories by status
   * @return { [key: string]: number }
   * @throws {ApiError} if fails to make API call
   */
  async getInventory(): Promise<{ [key: string]: number }> {
    return (await this.getInventoryWithHttpInfo()).data as { [key: string]: number };
  }

  /**
   * Returns pet inventories by status (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async getInventoryWithHttpInfo(): Promise<ApiResult<{ [key: string]: number }>> {
    const path = `/store/inventory`;
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => json as { [key: string]: number },
      null
    );
  }

  /**
   * Find purchase order by ID
   * @param orderId ID of order to return (required)
   * @return Order
   * @throws {ApiError} if fails to make API call
   */
  async getOrderById(orderId: number): Promise<Order> {
    if (orderId == null) {
      throw new Error('Missing required parameter "orderId" when calling getOrderById');
    }
    return (await this.getOrderByIdWithHttpInfo(orderId)).data as Order;
  }

  /**
   * Find purchase order by ID (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async getOrderByIdWithHttpInfo(orderId: number): Promise<ApiResult<Order>> {
    if (orderId == null) {
      throw new Error('Missing required parameter "orderId" when calling getOrderById');
    }
    let path = `/store/order/{orderId}`;
    path = path.replace(
      `{${'orderId'}}`,
      ValueSerializer.serializeStyled('orderId', orderId, 'path', 'number', null, 'simple', false) as string
    );
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'GET',
      path,
      queryParams,
      headerParams,
      null,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Order),
      null
    );
  }

  /**
   * Place an order for a pet
   * @param order  (optional)
   * @return Order
   * @throws {ApiError} if fails to make API call
   */
  async placeOrder(order?: Order): Promise<Order> {
    return (await this.placeOrderWithHttpInfo(order)).data as Order;
  }

  /**
   * Place an order for a pet (with HTTP info)
   * @throws {ApiError} if fails to make API call
   */
  async placeOrderWithHttpInfo(order?: Order): Promise<ApiResult<Order>> {
    const path = `/store/order`;
    const queryParams: Record<string, unknown> = {};
    const headerParams: Record<string, string> = {};
    return await this.invokeApiForResult(
      'POST',
      path,
      queryParams,
      headerParams,
      order,
      ['application/json'],
      'application/json',
      (json: unknown) => ObjectSerializer.deserialize(json, Order),
      null
    );
  }
}
