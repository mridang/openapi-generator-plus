package com.example.petstore;

import java.util.Map;
import javax.annotation.Nullable;

/** Exception thrown when an API call fails. */
public class ApiException extends Exception {
  private static final long serialVersionUID = 1L;

  private final int code;
  @Nullable private final transient Map<String, String> responseHeaders;
  @Nullable private final String responseBody;
  @Nullable private final transient Object errorBody;

  public ApiException(String message) {
    super(message);
    this.code = 0;
    this.responseHeaders = null;
    this.responseBody = null;
    this.errorBody = null;
  }

  public ApiException(
      int code,
      String message,
      @Nullable Map<String, String> responseHeaders,
      @Nullable String responseBody) {
    this(code, message, responseHeaders, responseBody, null);
  }

  public ApiException(
      int code,
      String message,
      @Nullable Map<String, String> responseHeaders,
      @Nullable String responseBody,
      @Nullable Object errorBody) {
    super(message);
    this.code = code;
    this.responseHeaders = responseHeaders;
    this.responseBody = responseBody;
    this.errorBody = errorBody;
  }

  /**
   * Get the HTTP status code.
   *
   * @return HTTP status code
   */
  public int getCode() {
    return code;
  }

  /**
   * Get the HTTP response headers.
   *
   * @return HTTP response headers
   */
  @Nullable
  public Map<String, String> getResponseHeaders() {
    return responseHeaders;
  }

  /**
   * Get the HTTP response body.
   *
   * @return Response body in the form of string
   */
  @Nullable
  public String getResponseBody() {
    return responseBody;
  }

  /**
   * Get the deserialized error body.
   *
   * @return The deserialized error body, or null if not available
   */
  @Nullable
  public Object getErrorBody() {
    return errorBody;
  }

  /**
   * Get the deserialized error body cast to the specified type.
   *
   * @param clazz the expected type of the error body
   * @param <T> the type parameter
   * @return the error body cast to the specified type, or null if not an instance
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public <T> T getTypedErrorBody(Class<T> clazz) {
    return clazz.isInstance(errorBody) ? (T) errorBody : null;
  }

  @Override
  public String getMessage() {
    return "ApiException{"
        + "code="
        + code
        + ", message='"
        + super.getMessage()
        + "', responseHeaders="
        + responseHeaders
        + ", responseBody='"
        + responseBody
        + '\''
        + '}';
  }
}
