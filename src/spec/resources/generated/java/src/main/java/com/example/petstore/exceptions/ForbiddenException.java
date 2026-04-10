package com.example.petstore.exceptions;

import java.util.Map;
import javax.annotation.Nullable;

/** Exception for HTTP 403 Forbidden. */
public class ForbiddenException extends ClientException {
  private static final long serialVersionUID = 1L;

  public ForbiddenException(
      String message,
      @Nullable Map<String, String> responseHeaders,
      @Nullable String responseBody,
      @Nullable Object errorBody) {
    super(403, message, responseHeaders, responseBody, errorBody);
  }
}
