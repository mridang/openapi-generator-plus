@file:Suppress("detekt:all")

package com.example.petstore.api.options

/**
 * Options for the findPetsByStatus operation.
 */
class FindPetsByStatusOptions(
    val status: String? = null,
    val filter: Map<String, String>? = null,
)
