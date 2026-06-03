package com.example.petstore.auth.oauth

import com.example.petstore.auth.Authenticator

class SsoAuthAuthenticator : OpenIdConnectAuthenticator {
    constructor(
        host: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String,
    ) : super(host, "https://auth.example.com/.well-known/openid-configuration", clientId, clientSecret, redirectUri, listOf())
}
