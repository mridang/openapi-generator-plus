package com.example.petstore.auth

class PetStoreBearerAuthenticator : BearerAuthenticator {
    constructor(host: String, token: String) : super(host, token)
}
