package com.example.petstore.auth

class ApiKeyQueryAuthenticator : ApiKeyAuthenticator {
    constructor(host: String, apiKey: String) : super(host, "api_key", apiKey, ApiKeyLocation.QUERY)
}
