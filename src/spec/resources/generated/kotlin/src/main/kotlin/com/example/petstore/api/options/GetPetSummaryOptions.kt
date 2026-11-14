
package com.example.petstore.api.options

import java.time.LocalDate

/**
 * Options for the getPetSummary operation.
 */
class GetPetSummaryOptions(
    /** Session token: \"a<b>\" & it's required */
    val session: String,
    /** Extra dates to summarise */
    val days: List<LocalDate>? = null,
)
