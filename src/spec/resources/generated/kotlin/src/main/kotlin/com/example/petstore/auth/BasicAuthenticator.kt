package com.example.petstore.auth

import java.util.Base64

/**
 * Provides HTTP Basic authentication.
 */
open class BasicAuthenticator(
    private val host: String,
    private val username: String,
    private val password: String,
) : BaseAuthenticator() {
    override fun getHost(): String = host

    override fun getAuthHeaders(): Map<String, String> {
        val credentials = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
        return mapOf("Authorization" to "Basic $credentials")
    }
}
