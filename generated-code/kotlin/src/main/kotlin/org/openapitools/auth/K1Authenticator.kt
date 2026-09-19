package org.openapitools.auth

class K1Authenticator : ApiKeyAuthenticator {
    constructor(host: String, apiKey: String) : super(host, "X-Token", apiKey, ApiKeyLocation.HEADER)
}
