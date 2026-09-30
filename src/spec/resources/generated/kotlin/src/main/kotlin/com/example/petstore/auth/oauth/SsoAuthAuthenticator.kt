
package com.example.petstore.auth.oauth

/**
 * Authenticator for the `ssoAuth` security scheme.
 */
class SsoAuthAuthenticator : OpenIdConnectAuthenticator {
    /**
     * Creates an authenticator for the `ssoAuth` security scheme.
     *
     * @param host the host credential
     * @param clientId the clientId credential
     * @param clientSecret the clientSecret credential
     * @param redirectUri the redirectUri credential
     */
    constructor(
        host: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String,
    ) : super(host, "https://auth.example.com/.well-known/openid-configuration", clientId, clientSecret, redirectUri, listOf())
}
