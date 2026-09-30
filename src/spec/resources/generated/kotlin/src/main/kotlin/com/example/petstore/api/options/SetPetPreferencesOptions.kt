
package com.example.petstore.api.options

import java.time.LocalDate

/**
 * Options for the setPetPreferences operation.
 */
class SetPetPreferencesOptions(
    val nickname: String,
    val tags: List<String>? = null,
    val note: String? = null,
    val renewalDate: LocalDate? = null,
)
