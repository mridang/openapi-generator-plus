
package com.example.petstore.auth.oauth

import com.example.petstore.auth.CredentialEnvironment

/**
 * Authenticator for the `browserAuth` security scheme.
 */
class BrowserAuthImplicitAuthenticator : OAuth2ImplicitAuthenticator {
    /**
     * Creates an authenticator for the `browserAuth` security scheme.
     *
     * @param host the base URL of the API
     * @param clientId the clientId credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID` (an empty value counts as unset)
     * @throws IllegalArgumentException if a credential is neither passed nor set in its variable
     */
    constructor(
        host: String,
        clientId: String? = null,
    ) : super(
        host,
        CredentialEnvironment.require(clientId, "SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID", "clientId"),
        "https://auth.example.com/authorize",
        listOf("read"),
    )
}
