
package com.example.petstore.api.options

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Options for the setPetPreferences operation.
 */
class SetPetPreferencesOptions(
    val nickname: String,
    val tags: List<String>? = null,
    val note: String? = null,
    val renewalDate: LocalDate? = null,
    val reminder: OffsetDateTime? = null,
    val intervalDays: Int? = null,
    val ratio: Double? = null,
    val urgent: Boolean? = null,
    val snooze: Duration? = null,
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
