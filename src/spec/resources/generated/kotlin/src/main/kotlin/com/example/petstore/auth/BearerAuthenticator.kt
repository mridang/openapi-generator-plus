package com.example.petstore.auth

/**
 * Provides HTTP Bearer token authentication.
 */
open class BearerAuthenticator(
    private val host: String,
    private val token: String,
) : BaseAuthenticator() {
    override fun getHost(): String = host

    override fun getAuthHeaders(): Map<String, String> = mapOf("Authorization" to "Bearer $token")
}
