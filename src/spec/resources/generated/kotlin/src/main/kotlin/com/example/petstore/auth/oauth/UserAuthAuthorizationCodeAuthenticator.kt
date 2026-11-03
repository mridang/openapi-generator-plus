
package com.example.petstore.auth.oauth

/**
 * Authenticator for the `userAuth` security scheme.
 */
class UserAuthAuthorizationCodeAuthenticator : OAuth2AuthorizationCodeAuthenticator {
    /**
     * Creates an authenticator for the `userAuth` security scheme.
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
    ) : super(
        host,
        clientId,
        clientSecret,
        "https://auth.example.com/authorize",
        "https://auth.example.com/oauth/token",
        redirectUri,
        listOf("pets:write", "pets:read"),
        "https://auth.example.com/oauth/refresh",
    )
}
