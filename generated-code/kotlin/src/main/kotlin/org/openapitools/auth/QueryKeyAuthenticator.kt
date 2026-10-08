
package org.openapitools.auth

/**
 * Authenticator for the `queryKey` security scheme.
 */
class QueryKeyAuthenticator : ApiKeyAuthenticator {
    /**
     * Creates an authenticator for the `queryKey` security scheme.
     *
     * @param host the host credential
     * @param apiKey the apiKey credential
     */
    constructor(host: String, apiKey: String) : super(host, "api_key", apiKey, ApiKeyLocation.QUERY)
}
