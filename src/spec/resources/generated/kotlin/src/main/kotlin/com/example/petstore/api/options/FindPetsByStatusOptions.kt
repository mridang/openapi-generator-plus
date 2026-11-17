
package com.example.petstore.api.options

import java.time.LocalDate

/**
 * Options for the findPetsByStatus operation.
 */
class FindPetsByStatusOptions(
    /**
     * Status values that need to be considered for filter
     * Example — Available only: `available`
     * Example — Sold pets: `sold`
     *
     * @deprecated This parameter is deprecated.
     */
    @Deprecated("This parameter is deprecated.")
    val status: String? = null,
    /** Filter criteria as key-value pairs */
    val filter: Map<String, String>? = null,
    /** Only return pets born on or after this date */
    val bornAfter: LocalDate? = null,
    val n2ndPage: Int? = null,
    /** Reference date for the report */
    val reportDate: LocalDate? = null,
    /**
     * The end-to-end timeout of this call in milliseconds, in place of the client's
     * `TransportOptions.timeout`; null keeps the client's setting.
     */
    val timeout: Long? = null,
    /**
     * Headers sent with this call only; one replaces any header of the same name set
     * elsewhere. Null sends none.
     */
    val headers: Map<String, String>? = null,
    /**
     * How many times this call is sent again after a retryable failure, in place of the
     * client's `TransportOptions.maxRetries`; `0` disables retries and null keeps the
     * client's setting.
     */
    val maxRetries: Int? = null,
)
