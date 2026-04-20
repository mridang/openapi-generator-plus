package com.example.petstore.exceptions;

import java.util.Map;
import javax.annotation.Nullable;

/**
 * Exception for HTTP 422 Unprocessable Entity.
 */
public class UnprocessableEntityException extends ClientException {
    private static final long serialVersionUID = 1L;

    public UnprocessableEntityException(
            String message,
            @Nullable Map<String, String> responseHeaders,
            @Nullable String responseBody,
            @Nullable Object errorBody) {
        super(422, message, responseHeaders, responseBody, errorBody);
    }
}
