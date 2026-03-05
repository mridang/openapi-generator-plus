package com.example.petstore;

import java.util.Map;

/**
 * Interface for API HTTP transport.
 *
 * <p>Implementations handle the actual HTTP request/response cycle. The default implementation uses
 * Apache HttpClient 5.
 */
public interface ApiClient {

  /**
   * Send an HTTP request and return the response.
   *
   * @param method HTTP method (GET, POST, PUT, DELETE, etc.)
   * @param url Fully qualified URL
   * @param headers HTTP headers
   * @param body Request body (serialized JSON string, or null)
   * @return ApiResponse containing status code, body, and headers
   * @throws ApiException if the request fails
   */
  ApiResponse sendRequest(String method, String url, Map<String, String> headers, String body)
      throws ApiException;
}
