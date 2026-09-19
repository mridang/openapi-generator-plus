@file:Suppress("detekt:all")

package com.example.petstore.auth

class AdminBasicAuthenticator : BasicAuthenticator {
    constructor(host: String, username: String, password: String) : super(host, username, password)
}
