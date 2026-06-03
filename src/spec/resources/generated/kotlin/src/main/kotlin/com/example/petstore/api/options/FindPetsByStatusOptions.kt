package com.example.petstore.api.options

/**
 * Options for the findPetsByStatus operation.
 */
class FindPetsByStatusOptions {
    var status: String? = null
        private set
    var filter: Map<String, String>? = null
        private set

    fun status(status: String): FindPetsByStatusOptions =
        apply {
            this.status = status
        }

    fun filter(filter: Map<String, String>): FindPetsByStatusOptions =
        apply {
            this.filter = filter
        }
}
