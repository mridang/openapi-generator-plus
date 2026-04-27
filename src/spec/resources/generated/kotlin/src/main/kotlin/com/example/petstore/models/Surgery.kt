package com.example.petstore.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Surgery(
    /** Example: `null` */
    @SerialName("procedureName")
    val procedureName: String,
    /** Example: `null` */
    @SerialName("durationMinutes")
    val durationMinutes: Int? = null,
)
