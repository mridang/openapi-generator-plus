package com.example.petstore.auth.oauth

class MachineAuthClientCredentialsAuthenticator : OAuth2ClientCredentialsAuthenticator {
    constructor(
        host: String,
        clientId: String,
        clientSecret: String,
    ) : super(host, clientId, clientSecret, "https://auth.example.com/oauth/token", listOf("pets:write", "pets:read"))
}
