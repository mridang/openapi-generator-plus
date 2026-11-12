
package com.example.petstore.auth.oauth

/**
 * Authenticator for the `machineAuth` security scheme.
 */
class MachineAuthClientCredentialsAuthenticator : OAuth2ClientCredentialsAuthenticator {
    /**
     * Creates an authenticator for the `machineAuth` security scheme.
     *
     * @param host the host credential
     * @param clientId the clientId credential
     * @param clientSecret the clientSecret credential
     */
    constructor(
        host: String,
        clientId: String,
        clientSecret: String,
    ) : super(host, clientId, clientSecret, "https://auth.example.com/oauth/token", listOf("pets:write", "pets:read"))
}
