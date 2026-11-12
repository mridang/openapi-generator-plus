
package org.openapitools.auth

/**
 * Authenticator for the `headerKey` security scheme.
 */
class HeaderKeyAuthenticator : ApiKeyAuthenticator {
    /**
     * Creates an authenticator for the `headerKey` security scheme.
     *
     * @param host the host credential
     * @param apiKey the apiKey credential
     */
    constructor(host: String, apiKey: String) : super(host, "X-Api-Key", apiKey, ApiKeyLocation.HEADER)
}
