
package com.example.petstore.auth

/**
 * Authenticator for the `serviceToken` security scheme.
 */
class ServiceTokenAuthenticator : BearerAuthenticator {
    /**
     * Creates an authenticator for the `serviceToken` security scheme.
     *
     * @param host the host credential
     * @param token the token credential
     */
    constructor(host: String, token: String) : super(host, token)
}
