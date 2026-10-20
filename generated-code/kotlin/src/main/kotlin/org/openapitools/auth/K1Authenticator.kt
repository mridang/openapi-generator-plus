
package org.openapitools.auth

/**
 * Authenticator for the `k1` security scheme.
 */
class K1Authenticator : ApiKeyAuthenticator {
    /**
     * Creates an authenticator for the `k1` security scheme.
     *
     * @param host the host credential
     * @param apiKey the apiKey credential
     */
    constructor(host: String, apiKey: String) : super(host, "X-Token", apiKey, ApiKeyLocation.HEADER)
}
