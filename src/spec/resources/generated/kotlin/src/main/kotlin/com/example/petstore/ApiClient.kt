package com.example.petstore

/**
 * Interface for API HTTP transport.
 */
interface ApiClient {
    suspend fun sendRequest(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: Any?,
    ): ApiResponse
}
