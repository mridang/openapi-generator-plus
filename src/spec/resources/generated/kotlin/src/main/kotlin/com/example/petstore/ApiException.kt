package com.example.petstore

/**
 * Exception thrown when an API call fails.
 */
open class ApiException : Exception {
    val code: Int
    val responseHeaders: Map<String, String>?
    val responseBody: String?
    val errorBody: Any?

    constructor(message: String) : super(message) {
        this.code = 0
        this.responseHeaders = null
        this.responseBody = null
        this.errorBody = null
    }

    constructor(
        code: Int,
        message: String,
        responseHeaders: Map<String, String>?,
        responseBody: String?,
        errorBody: Any? = null,
    ) : super(message) {
        this.code = code
        this.responseHeaders = responseHeaders
        this.responseBody = responseBody
        this.errorBody = errorBody
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getTypedErrorBody(clazz: Class<T>): T? = if (clazz.isInstance(errorBody)) errorBody as T else null

    override val message: String
        get() = "ApiException{code=$code, message='${super.message}', responseHeaders=$responseHeaders, responseBody='$responseBody'}"
}
