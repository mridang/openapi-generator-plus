package com.example.petstore;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Represents a typed API response with deserialized data, status code,
 * raw body, and headers. Returned by {@code withHttpInfo} methods.
 *
 * @param <T> the type of the deserialized response data
 * @param statusCode the HTTP status code
 * @param data the deserialized response body (null for void responses)
 * @param rawBody the raw response body string
 * @param headers the response headers (unmodifiable)
 */
public record ApiResult<T>(
        int statusCode,
        @Nullable T data,
        @Nullable String rawBody,
        Map<String, String> headers) {

    /**
     * Creates an ApiResult with defensively copied headers.
     */
    public ApiResult(int statusCode, @Nullable T data, @Nullable String rawBody, Map<String, String> headers) {
        this.statusCode = statusCode;
        this.data = data;
        this.rawBody = rawBody;
        this.headers = Collections.unmodifiableMap(new HashMap<>(headers));
    }
}
