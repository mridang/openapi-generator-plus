package com.example.petstore.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SetPetAvatarRequest(
    /**
     * Base64-encoded image data
     *
     * Example: `null`
     */
    @SerialName("data")
    val _data: ByteArray,
    /** Example: `image/jpeg` */
    @SerialName("mimeType")
    val mimeType: String,
)
