package com.example.petstore.api.options

/**
 * Options for the deletePet operation.
 */
class DeletePetOptions {
    var apiKey: String? = null
        private set

    fun apiKey(apiKey: String): DeletePetOptions =
        apply {
            this.apiKey = apiKey
        }
}
