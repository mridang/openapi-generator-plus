package com.example.petstore.auth.oauth

import com.example.petstore.auth.Authenticator

class BrowserAuthImplicitAuthenticator : OAuth2ImplicitAuthenticator {
    constructor(host: String, clientId: String) : super(host, clientId, "https://auth.example.com/authorize", listOf("read"))
}
