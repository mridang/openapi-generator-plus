
package com.example.petstore.auth

/**
 * Authenticator for the `petStoreBearer` security scheme.
 */
class PetStoreBearerAuthenticator : BearerAuthenticator {
    /**
     * Creates an authenticator for the `petStoreBearer` security scheme.
     *
     * @param host the base URL of the API
     * @param token the token credential; left out, it is read once from `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN` (an empty value counts as unset)
     * @param tokenProvider returns the current credential on every request, used when neither the argument nor the environment variable is set
     * @throws IllegalArgumentException if a credential is neither passed nor set in its variable
     */
    constructor(
        host: String,
        token: String? = null,
        tokenProvider: (suspend () -> String)? = null,
    ) : super(host, token?.takeIf { it.isNotEmpty() }, tokenProvider, "SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN")
}
