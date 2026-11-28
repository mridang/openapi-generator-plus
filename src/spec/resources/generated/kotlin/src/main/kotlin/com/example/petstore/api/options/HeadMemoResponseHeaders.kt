
package com.example.petstore.api.options

import com.example.petstore.errors.SerializationException

/**
 * The headers the success response of the headMemo operation declares, read in their
 * declared types. Build one from the headers of an `ApiResult` with [from].
 */
class HeadMemoResponseHeaders(
    /** The revision of the memo */
    val xRevision: Int? = null,
) {
    companion object {
        private val INTEGER = Regex("[+-]?[0-9]+")

        /**
         * Reads the declared headers from the response headers of a call, matching each name in
         * any letter case. A header the response does not carry stays null; an array header is
         * split on commas and each item trimmed.
         *
         * @param headers the response headers, as `ApiResult.headers` holds them
         * @return the declared headers in their declared types
         * @throws SerializationException when a declared header does not parse as its type
         */
        fun from(headers: Map<String, String>): HeadMemoResponseHeaders =
            HeadMemoResponseHeaders(
                xRevision = header(headers, "X-Revision")?.let { parseInteger("X-Revision", it) },
            )

        private fun header(
            headers: Map<String, String>,
            name: String,
        ): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

        private fun invalid(
            name: String,
            kind: String,
            value: String,
        ): SerializationException = SerializationException("Response header '$name' is not a valid $kind: '$value'")

        private fun parseInteger(
            name: String,
            value: String,
        ): Int = value.takeIf { INTEGER.matches(it) }?.toIntOrNull() ?: throw invalid(name, "integer", value)
    }
}
