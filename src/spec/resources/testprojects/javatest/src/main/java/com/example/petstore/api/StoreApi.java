package com.example.petstore.api;

import com.example.petstore.ApiClient;
import com.example.petstore.ApiException;
import com.example.petstore.Configuration;
import com.example.petstore.ObjectSerializer;
import java.util.Map;
import com.example.petstore.models.Order;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** StoreApi provides methods for the Store API group. */
public class StoreApi extends BaseApi {

  public StoreApi() {
    super();
  }

  public StoreApi(ApiClient apiClient, Configuration config) {
    super(apiClient, config);
  }

  /**
   * Delete purchase order by ID
   *
   * @param orderId ID of the order to delete (required)
   * @throws ApiException if fails to make API call
   */
  public void deleteOrder(Long orderId) throws ApiException {
    if (orderId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'orderId' when calling deleteOrder");
    }
    String path =
        "/store/order/{orderId}"
            .replace("{" + "orderId" + "}", encode(ObjectSerializer.toPathValue(orderId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    invokeApi(
        "DELETE", path, queryParams, headerParams, null, new String[] {}, "application/json", null);
  }

  /**
   * Returns pet inventories by status
   *
   * @return Map<String, Integer>
   * @throws ApiException if fails to make API call
   */
  public Map<String, Integer> getInventory() throws ApiException {
    String path = "/store/inventory";
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    return invokeApi(
        "GET",
        path,
        queryParams,
        headerParams,
        null,
        new String[] {"application/json"},
        "application/json",
        new TypeReference<Map<String, Integer>>() {});
  }

  /**
   * Find purchase order by ID
   *
   * @param orderId ID of order to return (required)
   * @return Order
   * @throws ApiException if fails to make API call
   */
  public Order getOrderById(Long orderId) throws ApiException {
    if (orderId == null) {
      throw new IllegalArgumentException(
          "Missing the required parameter 'orderId' when calling getOrderById");
    }
    String path =
        "/store/order/{orderId}"
            .replace("{" + "orderId" + "}", encode(ObjectSerializer.toPathValue(orderId)));
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    return invokeApi(
        "GET",
        path,
        queryParams,
        headerParams,
        null,
        new String[] {"application/json"},
        "application/json",
        new TypeReference<Order>() {});
  }

  /**
   * Place an order for a pet
   *
   * @param order (optional)
   * @return Order
   * @throws ApiException if fails to make API call
   */
  public Order placeOrder(Order order) throws ApiException {
    String path = "/store/order";
    Map<String, Object> queryParams = new HashMap<>();
    Map<String, String> headerParams = new HashMap<>();
    return invokeApi(
        "POST",
        path,
        queryParams,
        headerParams,
        order,
        new String[] {"application/json"},
        "application/json",
        new TypeReference<Order>() {});
  }
}
