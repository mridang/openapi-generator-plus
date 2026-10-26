
package com.example.petstore.auth

/**
 * Authenticator for the `apiKeyHeader` security scheme.
 */
class ApiKeyHeaderAuthenticator : ApiKeyAuthenticator {
    /**
     * Creates an authenticator for the `apiKeyHeader` security scheme.
     *
     * @param host the host credential
     * @param apiKey the apiKey credential
     */
    constructor(host: String, apiKey: String) : super(host, "X-API-Key", apiKey, ApiKeyLocation.HEADER)
}
