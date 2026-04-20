package com.example.petstore.exceptions;

import com.example.petstore.ApiException;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Exception for HTTP 4xx client errors.
 */
public class ClientException extends ApiException {
    private static final long serialVersionUID = 1L;

    public ClientException(
            int code,
            String message,
            @Nullable Map<String, String> responseHeaders,
            @Nullable String responseBody,
            @Nullable Object errorBody) {
        super(code, message, responseHeaders, responseBody, errorBody);
    }
}
