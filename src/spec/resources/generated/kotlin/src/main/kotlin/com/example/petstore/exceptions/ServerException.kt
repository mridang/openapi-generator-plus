package com.example.petstore.exceptions

import com.example.petstore.ApiException

/**
 * Exception for HTTP 5xx server errors.
 */
open class ServerException(
    code: Int,
    message: String,
    responseHeaders: Map<String, String>?,
    responseBody: String?,
    errorBody: Any? = null,
) : ApiException(code, message, responseHeaders, responseBody, errorBody)
