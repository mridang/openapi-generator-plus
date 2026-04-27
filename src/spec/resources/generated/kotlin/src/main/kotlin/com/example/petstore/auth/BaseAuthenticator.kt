package com.example.petstore.auth

/**
 * Base implementation of Authenticator with sensible defaults.
 */
abstract class BaseAuthenticator : Authenticator {
    override fun getQueryParams(): Map<String, String> = emptyMap()

    override fun getCookieParams(): Map<String, String> = emptyMap()
}
