package com.example.petstore.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Tags are deprecated, use categories instead
 *
 * @deprecated This schema is deprecated.
 */
@Deprecated("This schema is deprecated.")
@Serializable
data class Tag(
    /** Example: `null` */
    @SerialName("id")
    val id: Long? = null,
    /** Example: `null` */
    @SerialName("name")
    val name: String? = null,
)
