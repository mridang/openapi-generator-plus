@file:Suppress("detekt:all")

package com.example.petstore.auth

class PetStoreBasicAuthenticator : BasicAuthenticator {
    constructor(host: String, username: String, password: String) : super(host, username, password)
}
