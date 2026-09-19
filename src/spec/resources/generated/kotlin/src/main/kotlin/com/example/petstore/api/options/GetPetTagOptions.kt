@file:Suppress("detekt:all")

package com.example.petstore.api.options

/**
 * Options for the getPetTag operation.
 */
class GetPetTagOptions(
    val colors: List<String>? = null,
    val sizes: List<String>? = null,
    val filter: String? = null,
)
