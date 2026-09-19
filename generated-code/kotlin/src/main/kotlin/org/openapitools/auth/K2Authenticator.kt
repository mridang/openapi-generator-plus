@file:Suppress("detekt:all")

package org.openapitools.auth

class K2Authenticator : ApiKeyAuthenticator {
    constructor(host: String, apiKey: String) : super(host, "x-token", apiKey, ApiKeyLocation.HEADER)
}
