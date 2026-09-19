@file:Suppress("detekt:all")

package com.example.petstore.api.options

/**
 * Options for the findPetsByStatus operation.
 */
class FindPetsByStatusOptions(
    /**
     * Status values that need to be considered for filter
     *
     * @deprecated This parameter is deprecated.
     */
    @Deprecated("This parameter is deprecated.")
    val status: String? = null,
    /** Filter criteria as key-value pairs */
    val filter: Map<String, String>? = null,
)
