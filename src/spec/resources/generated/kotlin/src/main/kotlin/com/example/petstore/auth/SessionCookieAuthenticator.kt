package com.example.petstore.auth

class SessionCookieAuthenticator : ApiKeyAuthenticator {
    constructor(host: String, apiKey: String) : super(host, "SESSION_ID", apiKey, ApiKeyLocation.COOKIE)
}
