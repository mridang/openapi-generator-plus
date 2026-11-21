
package com.example.petstore.auth.oauth

import com.example.petstore.auth.CredentialEnvironment

/**
 * Authenticator for the `userAuth` security scheme.
 */
class UserAuthAuthorizationCodeAuthenticator : OAuth2AuthorizationCodeAuthenticator {
    /**
     * Creates an authenticator for the `userAuth` security scheme.
     *
     * @param host the base URL of the API
     * @param clientId the clientId credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID` (an empty value counts as unset)
     * @param clientSecret the clientSecret credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET` (an empty value counts as unset)
     * @param redirectUri the redirectUri credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI` (an empty value counts as unset)
     * @throws IllegalArgumentException if a credential is neither passed nor set in its variable
     */
    constructor(
        host: String,
        clientId: String? = null,
        clientSecret: String? = null,
        redirectUri: String? = null,
    ) : super(
        host,
        CredentialEnvironment.require(clientId, "SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID", "clientId"),
        CredentialEnvironment.require(clientSecret, "SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET", "clientSecret"),
        "https://auth.example.com/authorize",
        "https://auth.example.com/oauth/token",
        CredentialEnvironment.require(redirectUri, "SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI", "redirectUri"),
        listOf("pets:read", "pets:write"),
        "https://auth.example.com/oauth/refresh",
    )
}
