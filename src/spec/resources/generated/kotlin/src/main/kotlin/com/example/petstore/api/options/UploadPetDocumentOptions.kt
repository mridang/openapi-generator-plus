
package com.example.petstore.api.options

import com.example.petstore.models.Swatch
import java.time.OffsetDateTime

/**
 * Options for the uploadPetDocument operation.
 */
class UploadPetDocumentOptions(
    val _file: ByteArray,
    val documentType: String? = null,
    val notes: String? = null,
    val isPrimary: Boolean? = null,
    val pageCount: Long? = null,
    val takenAt: OffsetDateTime? = null,
    val swatch: Swatch? = null,
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
