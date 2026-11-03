
package com.example.petstore.auth

/**
 * Authenticator for the `petStoreBasic` security scheme.
 */
class PetStoreBasicAuthenticator : BasicAuthenticator {
    /**
     * Creates an authenticator for the `petStoreBasic` security scheme.
     *
     * @param host the host credential
     * @param username the username credential
     * @param password the password credential
     */
    constructor(host: String, username: String, password: String) : super(host, username, password)
}
