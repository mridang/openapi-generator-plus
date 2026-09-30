
package com.example.petstore.auth

/**
 * Authenticator for the `sessionCookie` security scheme.
 */
class SessionCookieAuthenticator : ApiKeyAuthenticator {
    /**
     * Creates an authenticator for the `sessionCookie` security scheme.
     *
     * @param host the host credential
     * @param apiKey the apiKey credential
     */
    constructor(host: String, apiKey: String) : super(host, "SESSION_ID", apiKey, ApiKeyLocation.COOKIE)
}
