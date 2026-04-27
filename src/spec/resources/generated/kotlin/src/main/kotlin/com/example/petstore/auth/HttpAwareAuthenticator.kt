package com.example.petstore.auth

import com.example.petstore.ApiClient

/**
 * Base class for authenticators that need to make HTTP calls
 * (e.g., token exchange, OIDC discovery).
 */
abstract class HttpAwareAuthenticator : BaseAuthenticator() {
    open var apiClient: ApiClient? = null
}
