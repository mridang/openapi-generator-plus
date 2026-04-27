package com.example.petstore.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PhotoMetadataLocation(
    /** Example: `null` */
    @SerialName("lat")
    val lat: Double? = null,
    /** Example: `null` */
    @SerialName("lng")
    val lng: Double? = null,
)
