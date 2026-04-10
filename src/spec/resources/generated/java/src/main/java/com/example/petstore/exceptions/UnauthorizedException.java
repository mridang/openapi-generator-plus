package com.example.petstore.exceptions;

import java.util.Map;
import javax.annotation.Nullable;

/** Exception for HTTP 401 Unauthorized. */
public class UnauthorizedException extends ClientException {
  private static final long serialVersionUID = 1L;

  public UnauthorizedException(
      String message,
      @Nullable Map<String, String> responseHeaders,
      @Nullable String responseBody,
      @Nullable Object errorBody) {
    super(401, message, responseHeaders, responseBody, errorBody);
  }
}
