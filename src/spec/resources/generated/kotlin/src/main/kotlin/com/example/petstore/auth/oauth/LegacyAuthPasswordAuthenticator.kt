
package com.example.petstore.auth.oauth

import com.example.petstore.auth.CredentialEnvironment

/**
 * Authenticator for the `legacyAuth` security scheme.
 */
class LegacyAuthPasswordAuthenticator : OAuth2PasswordAuthenticator {
    /**
     * Creates an authenticator for the `legacyAuth` security scheme.
     *
     * @param host the base URL of the API
     * @param clientId the clientId credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID` (an empty value counts as unset)
     * @param clientSecret the clientSecret credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET` (an empty value counts as unset)
     * @param username the username credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME` (an empty value counts as unset)
     * @param password the password credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD` (an empty value counts as unset)
     * @throws IllegalArgumentException if a credential is neither passed nor set in its variable
     */
    constructor(
        host: String,
        clientId: String? = null,
        clientSecret: String? = null,
        username: String? = null,
        password: String? = null,
    ) : super(
        host,
        CredentialEnvironment.require(clientId, "SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID", "clientId"),
        CredentialEnvironment.require(clientSecret, "SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET", "clientSecret"),
        "https://auth.example.com/oauth/token",
        CredentialEnvironment.require(username, "SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME", "username"),
        CredentialEnvironment.require(password, "SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD", "password"),
        listOf("read"),
        "https://auth.example.com/oauth/refresh",
    )
}
