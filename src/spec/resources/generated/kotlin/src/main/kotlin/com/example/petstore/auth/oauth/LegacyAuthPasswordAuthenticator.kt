
package com.example.petstore.auth.oauth

/**
 * Authenticator for the `legacyAuth` security scheme.
 */
class LegacyAuthPasswordAuthenticator : OAuth2PasswordAuthenticator {
    /**
     * Creates an authenticator for the `legacyAuth` security scheme.
     *
     * @param host the host credential
     * @param clientId the clientId credential
     * @param clientSecret the clientSecret credential
     * @param username the username credential
     * @param password the password credential
     */
    constructor(
        host: String,
        clientId: String,
        clientSecret: String,
        username: String,
        password: String,
    ) : super(
        host,
        clientId,
        clientSecret,
        "https://auth.example.com/oauth/token",
        username,
        password,
        listOf("read"),
        "https://auth.example.com/oauth/refresh",
    )
}
