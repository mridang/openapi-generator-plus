
package com.example.petstore.auth

/**
 * Authenticator for the `internalApiKey` security scheme.
 */
class InternalApiKeyAuthenticator : ApiKeyAuthenticator {
    /**
     * Creates an authenticator for the `internalApiKey` security scheme.
     *
     * @param host the host credential
     * @param apiKey the apiKey credential
     */
    constructor(host: String, apiKey: String) : super(host, "X-Internal-Key", apiKey, ApiKeyLocation.HEADER)
}
