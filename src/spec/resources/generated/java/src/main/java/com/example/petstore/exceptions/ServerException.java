package com.example.petstore.exceptions;

import com.example.petstore.ApiException;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Exception for HTTP 5xx server errors.
 */
public class ServerException extends ApiException {
    private static final long serialVersionUID = 1L;

    public ServerException(
            int code,
            String message,
            @Nullable Map<String, String> responseHeaders,
            @Nullable String responseBody,
            @Nullable Object errorBody) {
        super(code, message, responseHeaders, responseBody, errorBody);
    }
}
