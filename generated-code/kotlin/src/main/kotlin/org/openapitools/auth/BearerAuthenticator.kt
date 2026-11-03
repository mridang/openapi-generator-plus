
package org.openapitools.auth

/**
 * Authenticator for the `bearer` security scheme.
 */
class BearerAuthenticator : BearerAuthenticator {
    /**
     * Creates an authenticator for the `bearer` security scheme.
     *
     * @param host the host credential
     * @param token the token credential
     */
    constructor(host: String, token: String) : super(host, token)
}
