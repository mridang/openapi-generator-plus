package com.example.petstore.exceptions

/**
 * Exception for HTTP 403 Forbidden.
 */
class ForbiddenException(
    message: String,
    responseHeaders: Map<String, String>?,
    responseBody: String?,
    errorBody: Any? = null,
) : ClientException(403, message, responseHeaders, responseBody, errorBody)
