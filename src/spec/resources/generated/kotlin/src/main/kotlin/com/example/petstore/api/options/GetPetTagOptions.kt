package com.example.petstore.api.options


/**
 * Options for the getPetTag operation.
 */
class GetPetTagOptions() {
    var colors: List<String>? = null
        private set
    var sizes: List<String>? = null
        private set
    var filter: String? = null
        private set

    fun colors(colors: List<String>): GetPetTagOptions = apply {
        this.colors = colors
    }

    fun sizes(sizes: List<String>): GetPetTagOptions = apply {
        this.sizes = sizes
    }

    fun filter(filter: String): GetPetTagOptions = apply {
        this.filter = filter
    }

}
