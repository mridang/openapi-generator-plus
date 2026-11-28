
package com.example.petstore.api.options

import com.example.petstore.errors.SerializationException

/**
 * The headers the success response of the listPetFeed operation declares, read in their
 * declared types. Build one from the headers of an `ApiResult` with [from].
 */
class ListPetFeedResponseHeaders(
    /** The next page as rel="next", when there is one */
    val link: String? = null,
) {
    companion object {
        /**
         * Reads the declared headers from the response headers of a call, matching each name in
         * any letter case. A header the response does not carry stays null; an array header is
         * split on commas and each item trimmed.
         *
         * @param headers the response headers, as `ApiResult.headers` holds them
         * @return the declared headers in their declared types
         * @throws SerializationException when a declared header does not parse as its type
         */
        fun from(headers: Map<String, String>): ListPetFeedResponseHeaders =
            ListPetFeedResponseHeaders(
                link = header(headers, "Link"),
            )

        private fun header(
            headers: Map<String, String>,
            name: String,
        ): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }
}
