
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
