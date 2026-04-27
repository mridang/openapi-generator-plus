package com.example.petstore.auth

/**
 * Provides API key authentication.
 */
open class ApiKeyAuthenticator(
    private val host: String,
    private val paramName: String,
    private val apiKey: String,
    private val location: ApiKeyLocation,
) : BaseAuthenticator() {
    override fun getHost(): String = host

    override fun getAuthHeaders(): Map<String, String> = if (location == ApiKeyLocation.HEADER) mapOf(paramName to apiKey) else emptyMap()

    override fun getQueryParams(): Map<String, String> = if (location == ApiKeyLocation.QUERY) mapOf(paramName to apiKey) else emptyMap()

    override fun getCookieParams(): Map<String, String> = if (location == ApiKeyLocation.COOKIE) mapOf(paramName to apiKey) else emptyMap()
}
