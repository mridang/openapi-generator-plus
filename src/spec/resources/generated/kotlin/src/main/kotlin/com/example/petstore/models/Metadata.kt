package com.example.petstore.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class Metadata(
    /** Example: `null` */
    @SerialName("createdAt")
    @Contextual
    val createdAt: OffsetDateTime? = null,
) {
    @kotlinx.serialization.Transient
    val additionalProperties: MutableMap<String, @Contextual Any?> = mutableMapOf()
}
