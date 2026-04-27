package com.example.petstore.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ApiResponse(
    /** Example: `null` */
    @SerialName("code")
    val code: Int? = null,
    /** Example: `null` */
    @SerialName("type")
    val type: String? = null,
    /** Example: `null` */
    @SerialName("message")
    val message: String? = null,
)
