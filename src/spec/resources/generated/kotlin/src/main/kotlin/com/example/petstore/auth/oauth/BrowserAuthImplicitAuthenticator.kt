
package com.example.petstore.auth.oauth

/**
 * Authenticator for the `browserAuth` security scheme.
 */
class BrowserAuthImplicitAuthenticator : OAuth2ImplicitAuthenticator {
    /**
     * Creates an authenticator for the `browserAuth` security scheme.
     *
     * @param host the host credential
     * @param clientId the clientId credential
     */
    constructor(host: String, clientId: String) : super(host, clientId, "https://auth.example.com/authorize", listOf("read"))
}
