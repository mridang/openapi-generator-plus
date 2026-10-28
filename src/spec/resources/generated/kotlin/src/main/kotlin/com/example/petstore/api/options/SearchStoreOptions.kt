
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
)
