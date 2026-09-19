@file:Suppress("detekt:all")

package com.example.petstore.auth.oauth

class LegacyAuthPasswordAuthenticator : OAuth2PasswordAuthenticator {
    constructor(
        host: String,
        clientId: String,
        clientSecret: String,
        username: String,
        password: String,
    ) : super(
        host,
        clientId,
        clientSecret,
        "https://auth.example.com/oauth/token",
        "https://auth.example.com/oauth/refresh",
        username,
        password,
        listOf("read"),
    )
}
