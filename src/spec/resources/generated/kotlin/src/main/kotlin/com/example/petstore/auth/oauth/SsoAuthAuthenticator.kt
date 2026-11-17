
package com.example.petstore.auth.oauth

import com.example.petstore.auth.CredentialEnvironment

/**
 * Authenticator for the `ssoAuth` security scheme.
 */
class SsoAuthAuthenticator : OpenIdConnectAuthenticator {
    /**
     * Creates an authenticator for the `ssoAuth` security scheme.
     *
     * @param host the base URL of the API
     * @param clientId the clientId credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID` (an empty value counts as unset)
     * @param clientSecret the clientSecret credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET` (an empty value counts as unset)
     * @param redirectUri the redirectUri credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI` (an empty value counts as unset)
     * @throws IllegalArgumentException if a credential is neither passed nor set in its variable
     */
    constructor(
        host: String,
        clientId: String? = null,
        clientSecret: String? = null,
        redirectUri: String? = null,
    ) : super(
        host,
        "https://auth.example.com/.well-known/openid-configuration",
        CredentialEnvironment.require(clientId, "SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID", "clientId"),
        CredentialEnvironment.require(clientSecret, "SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET", "clientSecret"),
        CredentialEnvironment.require(redirectUri, "SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI", "redirectUri"),
        listOf(),
    )
}
