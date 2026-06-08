package com.example.petstore.api.options

import com.example.petstore.auth.Authenticator

/**
 * Options for the deletePet operation.
 */
class DeletePetOptions(
    val apiKey: String? = null,
    val auth: Authenticator? = null,
)
