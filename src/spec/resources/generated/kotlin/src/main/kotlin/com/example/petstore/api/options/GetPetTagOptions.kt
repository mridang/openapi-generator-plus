
package com.example.petstore.api.options

import com.example.petstore.models.PetCriteria

/**
 * Options for the getPetTag operation.
 */
class GetPetTagOptions(
    val colors: List<String>? = null,
    val sizes: List<String>? = null,
    val filter: String? = null,
    /** Query value whose RFC 3986 reserved characters must be sent literally (OAS allowReserved), for example a version expression such as v1.0/beta:rc1 keeping the slash and colon. */
    val revision: String? = null,
    /** Object-valued query parameter, form style exploded (the OAS default for query) */
    val criteria: PetCriteria? = null,
    val criteriaCsv: PetCriteria? = null,
    val criteriaDeep: PetCriteria? = null,
    /** Opaque bytes, base64 on the wire (OAS format byte) */
    val fingerprint: ByteArray? = null,
    val xCriteria: PetCriteria? = null,
    val xFingerprint: ByteArray? = null,
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
