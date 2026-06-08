package com.example.petstore.api.options

import com.example.petstore.auth.Authenticator

/**
 * Options for the deletePet operation.
 */
class DeletePetOptions(
    val auth: Authenticator? = null,
) {
    var apiKey: String? = null
        private set

    fun apiKey(apiKey: String): DeletePetOptions =
        apply {
            this.apiKey = apiKey
        }
}
