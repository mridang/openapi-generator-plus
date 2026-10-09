
package com.example.petstore.auth

/**
 * Authenticator for the `petStoreBearer` security scheme.
 */
class PetStoreBearerAuthenticator : BearerAuthenticator {
    /**
     * Creates an authenticator for the `petStoreBearer` security scheme.
     *
     * @param host the host credential
     * @param token the token credential
     */
    constructor(host: String, token: String) : super(host, token)
}
