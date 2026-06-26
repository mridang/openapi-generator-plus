@file:Suppress("detekt:all")

package com.example.petstore.api.options

import com.example.petstore.auth.Authenticator

/**
 * Options for the addPet operation.
 */
class AddPetOptions(
    val auth: Authenticator? = null,
)
