package com.example.petstore.auth


class ServiceTokenAuthenticator : BearerAuthenticator {
    constructor(host: String, token: String) : super(host, token)
}
