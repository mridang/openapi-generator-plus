
package com.example.petstore.api.options

/**
 * Options for the listPetOffsets operation.
 */
class ListPetOffsetsOptions(
    /** The offset of the first pet on the page */
    val offset: Int? = null,
    /** The number of pets on a page */
    val limit: Int? = null,
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
) {
    /**
     * Returns a copy of these options with the given values: every parameter, the timeout, the retry
     * count and the headers keep the value they have here unless a new one is passed. A paged list
     * uses it to send each page with its own cursor and leaves the caller's options untouched.
     */
    internal fun copy(
        offset: Int? = this.offset,
        limit: Int? = this.limit,
        timeout: Long? = this.timeout,
        headers: Map<String, String>? = this.headers,
        maxRetries: Int? = this.maxRetries,
    ): ListPetOffsetsOptions =
        ListPetOffsetsOptions(
            offset = offset,
            limit = limit,
            timeout = timeout,
            headers = headers,
            maxRetries = maxRetries,
        )
}
