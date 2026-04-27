package com.example.petstore.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Photo(
    /** Example: `null` */
    @SerialName("id")
    val id: Long? = null,
    /** Example: `null` */
    @SerialName("caption")
    val caption: String? = null,
    /** Example: `null` */
    @SerialName("isPrimary")
    val isPrimary: Boolean? = null,
    /** Example: `null` */
    @SerialName("url")
    val url: String? = null,
)
