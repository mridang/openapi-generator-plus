
package com.example.petstore.api.options

import com.example.petstore.models.PhotoMetadata
import java.time.LocalTime

/**
 * Options for the searchStore operation.
 */
class SearchStoreOptions(
    /** JSON-encoded query parameter (OAS `content`, not `schema`) */
    val q: PhotoMetadata? = null,
    val labels: Set<String>? = null,
    val at: LocalTime? = null,
    val xFilter: Map<String, String>? = null,
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
