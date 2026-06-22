@file:Suppress("detekt:all")

package com.example.petstore.api.options

import com.example.petstore.models.Swatch

/**
 * Options for the getBySwatch operation.
 */
class GetBySwatchOptions(
    val querySwatch: Swatch? = null,
    val preferredSwatch: Swatch? = null,
)
