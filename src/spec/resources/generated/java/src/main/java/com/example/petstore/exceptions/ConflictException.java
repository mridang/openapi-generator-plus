package com.example.petstore.exceptions;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/** Exception for HTTP 409 Conflict. */
public class ConflictException extends ClientException {
  private static final long serialVersionUID = 1L;

  public ConflictException(
      String message,
      @Nullable Map<String, List<String>> responseHeaders,
      @Nullable String responseBody,
      @Nullable Object errorBody) {
    super(409, message, responseHeaders, responseBody, errorBody);
  }
}
