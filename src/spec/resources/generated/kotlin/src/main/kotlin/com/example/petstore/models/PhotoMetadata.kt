package com.example.petstore.models

import com.example.petstore.models.PhotoMetadataLocation
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class PhotoMetadata(
    /** Example: `null` */
    @SerialName("caption")
    val caption: String? = null,
    /** Example: `null` */
    @SerialName("isPrimary")
    val isPrimary: Boolean? = null,
    /** Example: `null` */
    @SerialName("takenAt")
    @Contextual
    val takenAt: OffsetDateTime? = null,
    /** Example: `null` */
    @SerialName("location")
    val location: PhotoMetadataLocation? = null,
)
