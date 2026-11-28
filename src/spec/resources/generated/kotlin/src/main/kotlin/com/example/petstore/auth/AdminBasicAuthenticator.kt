
package com.example.petstore.auth

/**
 * Authenticator for the `adminBasic` security scheme.
 *
 * Deprecated: the API marks the security scheme 'adminBasic' as deprecated.
 */
class AdminBasicAuthenticator : BasicAuthenticator {
    /**
     * Creates an authenticator for the `adminBasic` security scheme.
     *
     * @param host the base URL of the API
     * @param username the username credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` (an empty value counts as unset)
     * @param password the password credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` (an empty value counts as unset)
     * @throws IllegalArgumentException if a credential is neither passed nor set in its variable
     */
    constructor(
        host: String,
        username: String? = null,
        password: String? = null,
    ) : super(
        host,
        username,
        password,
        "SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME",
        "SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD",
    )
}
