@file:Suppress("detekt:all")

package com.example.petstore.api.options

import com.example.petstore.auth.Authenticator

/**
 * Options for the deletePet operation.
 */
class DeletePetOptions(
    /** Session cookie used for authentication */
    val apiKey: String? = null,
    val auth: Authenticator? = null,
)
