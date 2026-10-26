
package com.example.petstore.api.options

import java.time.LocalDate

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
    /** Only return pets born on or after this date */
    val bornAfter: LocalDate? = null,
    /** Reference date for the report */
    val reportDate: LocalDate? = null,
)
