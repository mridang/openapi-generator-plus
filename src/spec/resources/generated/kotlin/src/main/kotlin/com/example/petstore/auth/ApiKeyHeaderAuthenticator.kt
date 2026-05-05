package com.example.petstore.auth

class ApiKeyHeaderAuthenticator : ApiKeyAuthenticator {
    constructor(host: String, apiKey: String) : super(host, "X-API-Key", apiKey, ApiKeyLocation.HEADER)
}
