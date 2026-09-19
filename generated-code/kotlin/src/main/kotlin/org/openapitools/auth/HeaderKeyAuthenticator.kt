@file:Suppress("detekt:all")

package org.openapitools.auth

class HeaderKeyAuthenticator : ApiKeyAuthenticator {
    constructor(host: String, apiKey: String) : super(host, "X-Api-Key", apiKey, ApiKeyLocation.HEADER)
}
