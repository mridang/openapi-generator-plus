
package com.example.petstore.auth

/**
 * Authenticator for the `sessionCookie` security scheme.
 */
class SessionCookieAuthenticator : ApiKeyAuthenticator {
    /**
     * Creates an authenticator for the `sessionCookie` security scheme.
     *
     * @param host the base URL of the API
     * @param apiKey the apiKey credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY` (an empty value counts as unset)
     * @param apiKeyProvider returns the current credential on every request, used when neither the argument nor the environment variable is set
     * @throws IllegalArgumentException if a credential is neither passed nor set in its variable
     */
    constructor(
        host: String,
        apiKey: String? = null,
        apiKeyProvider: (suspend () -> String)? = null,
    ) : super(host, "SESSION_ID", apiKey, ApiKeyLocation.COOKIE, apiKeyProvider, "SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY")
}
