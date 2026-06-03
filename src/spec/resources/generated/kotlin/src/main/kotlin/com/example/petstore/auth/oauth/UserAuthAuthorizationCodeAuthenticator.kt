package com.example.petstore.auth.oauth

import com.example.petstore.auth.Authenticator

class UserAuthAuthorizationCodeAuthenticator : OAuth2AuthorizationCodeAuthenticator {
    constructor(
        host: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String,
    ) : super(
        host,
        clientId,
        clientSecret,
        "https://auth.example.com/authorize",
        "https://auth.example.com/oauth/token",
        redirectUri,
        listOf("pets:write", "pets:read"),
        "https://auth.example.com/oauth/refresh",
    )
}
