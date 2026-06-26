@file:Suppress("detekt:all")

package com.example.petstore.api.options

/**
 * Options for the setPetPreferences operation.
 */
class SetPetPreferencesOptions(
    val nickname: String,
    val tags: List<String>? = null,
    val note: String? = null,
)
