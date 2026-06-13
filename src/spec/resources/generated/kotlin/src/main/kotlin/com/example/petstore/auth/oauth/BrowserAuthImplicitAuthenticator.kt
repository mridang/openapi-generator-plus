@file:Suppress("detekt:all")

package com.example.petstore.auth.oauth

class BrowserAuthImplicitAuthenticator : OAuth2ImplicitAuthenticator {
    constructor(host: String, clientId: String) : super(host, clientId, "https://auth.example.com/authorize", listOf("read"))
}
