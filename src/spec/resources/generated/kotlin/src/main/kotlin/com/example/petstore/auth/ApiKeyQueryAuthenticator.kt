
package com.example.petstore.auth

/**
 * Authenticator for the `apiKeyQuery` security scheme.
 */
class ApiKeyQueryAuthenticator : ApiKeyAuthenticator {
    /**
     * Creates an authenticator for the `apiKeyQuery` security scheme.
     *
     * @param host the host credential
     * @param apiKey the apiKey credential
     */
    constructor(host: String, apiKey: String) : super(host, "api_key", apiKey, ApiKeyLocation.QUERY)
}
