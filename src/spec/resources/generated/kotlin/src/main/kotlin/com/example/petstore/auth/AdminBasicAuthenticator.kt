
package com.example.petstore.auth

/**
 * Authenticator for the `adminBasic` security scheme.
 */
class AdminBasicAuthenticator : BasicAuthenticator {
    /**
     * Creates an authenticator for the `adminBasic` security scheme.
     *
     * @param host the host credential
     * @param username the username credential
     * @param password the password credential
     */
    constructor(host: String, username: String, password: String) : super(host, username, password)
}
