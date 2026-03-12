package com.example.petstore.api;

import com.example.petstore.ApiClient;
import com.example.petstore.ApiException;
import com.example.petstore.ApiResponse;
import com.example.petstore.Configuration;
import com.example.petstore.DefaultApiClient;
import com.example.petstore.HeaderSelector;
import com.example.petstore.ObjectSerializer;
import com.example.petstore.TraceContextUtil;
import com.example.petstore.auth.Authenticator;
import com.fasterxml.jackson.core.type.TypeReference;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;
import javax.annotation.Nullable;

/**
 * Base class for all API classes. Provides the {@code invokeApi} method that handles URL
 * construction, header selection, body serialization, request dispatch, and response
 * deserialization.
 */
public abstract class BaseApi {

  protected final ApiClient apiClient;
  protected final Configuration config;
  protected final ObjectSerializer objectSerializer;
  protected final HeaderSelector headerSelector;

  public BaseApi() {
    this(Configuration.getDefault());
  }

  public BaseApi(Configuration config) {
    this(new DefaultApiClient(config), config);
  }

  public BaseApi(ApiClient apiClient, Configuration config) {
    this.apiClient = apiClient;
    this.config = config;
    this.objectSerializer = new ObjectSerializer();
    this.headerSelector = new HeaderSelector();
  }

  public Configuration getConfig() {
    return config;
  }

  /**
   * Invoke an API operation.
   *
   * @param <T> the return type
   * @param method HTTP method (GET, POST, PUT, DELETE, etc.)
   * @param path URL path (with path params already substituted)
   * @param queryParams query parameters
   * @param headerParams custom header parameters
   * @param body request body (model object or null)
   * @param accepts acceptable response content types
   * @param contentType request content type
   * @param returnType return type for deserialization (null for void)
   * @param auth optional authenticator for operation-specific auth
   * @return deserialized response or null
   * @throws ApiException if the API call fails
   */
  @Nullable
  protected <T> T invokeApi(
      String method,
      String path,
      Map<String, Object> queryParams,
      Map<String, String> headerParams,
      @Nullable Object body,
      String[] accepts,
      String contentType,
      @Nullable TypeReference<T> returnType,
      @Nullable Authenticator auth)
      throws ApiException {

    String url = config.getBaseUrl() + path;

    if (auth != null) {
      for (Map.Entry<String, String> entry : auth.getQueryParams().entrySet()) {
        queryParams.put(entry.getKey(), entry.getValue());
      }
    }

    String query = buildQueryString(queryParams);
    if (!query.isEmpty()) {
      url += "?" + query;
    }

    boolean isMultipart = "multipart/form-data".equals(contentType);
    Map<String, String> headers = headerSelector.selectHeaders(accepts, contentType, isMultipart);
    headers.putAll(config.getDefaultHeaders());
    if (headerParams != null) {
      headers.putAll(headerParams);
    }
    if (auth != null) {
      headers.putAll(auth.getAuthHeaders());
      Map<String, String> cookies = auth.getCookieParams();
      if (!cookies.isEmpty()) {
        StringJoiner cookieJoiner = new StringJoiner("; ");
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
          cookieJoiner.add(entry.getKey() + "=" + entry.getValue());
        }
        String existing = headers.get("Cookie");
        if (existing != null && !existing.isEmpty()) {
          headers.put("Cookie", existing + "; " + cookieJoiner);
        } else {
          headers.put("Cookie", cookieJoiner.toString());
        }
      }
    }
    TraceContextUtil.injectTraceContext(headers);

    String serializedBody = null;
    if (body != null) {
      serializedBody = objectSerializer.serialize(body);
    }

    ApiResponse response = apiClient.sendRequest(method, url, headers, serializedBody);

    if (response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
      throw new ApiException(
          response.getStatusCode(),
          "API returned status code " + response.getStatusCode(),
          null,
          response.getBody());
    }

    if (returnType != null && response.getBody() != null && !response.getBody().isEmpty()) {
      return objectSerializer.deserialize(response.getBody(), returnType);
    }
    return null;
  }

  /**
   * Build a query string from query parameters.
   *
   * @param queryParams the query parameters
   * @return encoded query string
   */
  private String buildQueryString(Map<String, Object> queryParams) {
    if (queryParams == null || queryParams.isEmpty()) {
      return "";
    }
    StringJoiner joiner = new StringJoiner("&");
    for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
      if (entry.getValue() != null) {
        joiner.add(encode(entry.getKey()) + "=" + encode(String.valueOf(entry.getValue())));
      }
    }
    return joiner.toString();
  }

  /**
   * URL-encode a string.
   *
   * @param value the string to encode
   * @return URL-encoded string
   */
  String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
