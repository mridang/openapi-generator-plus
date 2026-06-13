@file:Suppress("detekt:all")

package com.example.petstore.auth

class InternalApiKeyAuthenticator : ApiKeyAuthenticator {
    constructor(host: String, apiKey: String) : super(host, "X-Internal-Key", apiKey, ApiKeyLocation.HEADER)
}
