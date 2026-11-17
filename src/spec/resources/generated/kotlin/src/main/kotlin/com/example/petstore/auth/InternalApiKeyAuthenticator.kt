
package com.example.petstore.auth

/**
 * Authenticator for the `internalApiKey` security scheme.
 */
class InternalApiKeyAuthenticator : ApiKeyAuthenticator {
    /**
     * Creates an authenticator for the `internalApiKey` security scheme.
     *
     * @param host the base URL of the API
     * @param apiKey the apiKey credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY` (an empty value counts as unset)
     * @param apiKeyProvider returns the current credential on every request, used when neither the argument nor the environment variable is set
     * @throws IllegalArgumentException if a credential is neither passed nor set in its variable
     */
    constructor(
        host: String,
        apiKey: String? = null,
        apiKeyProvider: (suspend () -> String)? = null,
    ) : super(
        host,
        "X-Internal-Key",
        apiKey,
        ApiKeyLocation.HEADER,
        apiKeyProvider,
        "SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY",
    )
}
