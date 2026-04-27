package com.example.petstore.exceptions

/**
 * Exception for HTTP 400 Bad Request.
 */
class BadRequestException(
    message: String,
    responseHeaders: Map<String, String>?,
    responseBody: String?,
    errorBody: Any? = null,
) : ClientException(400, message, responseHeaders, responseBody, errorBody)
