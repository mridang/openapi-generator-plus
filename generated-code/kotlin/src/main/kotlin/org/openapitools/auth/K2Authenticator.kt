
package org.openapitools.auth

/**
 * Authenticator for the `k2` security scheme.
 */
class K2Authenticator : ApiKeyAuthenticator {
    /**
     * Creates an authenticator for the `k2` security scheme.
     *
     * @param host the host credential
     * @param apiKey the apiKey credential
     */
    constructor(host: String, apiKey: String) : super(host, "x-token", apiKey, ApiKeyLocation.HEADER)
}
