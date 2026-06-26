@file:Suppress("detekt:all")

package com.example.petstore.auth.oauth

class SsoAuthAuthenticator : OpenIdConnectAuthenticator {
    constructor(
        host: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String,
    ) : super(host, "https://auth.example.com/.well-known/openid-configuration", clientId, clientSecret, redirectUri, listOf())
}
