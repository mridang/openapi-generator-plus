package com.example.petstore.models

import com.example.petstore.models.Pet
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime
import kotlin.collections.List

@Serializable
data class PetPassport(
    /** Example: `null` */
    @SerialName("pet")
    val pet: Pet? = null,
    /** Base64-encoded primary thumbnail */
    @SerialName("thumbnail")
    val thumbnail: ByteArray? = null,
    /**
     * Base64-encoded scans of each passport page
     *
     * Example: `null`
     */
    @SerialName("scans")
    val scans: List<ByteArray>? = null,
    /** Example: `null` */
    @SerialName("issuedAt")
    @Contextual
    val issuedAt: OffsetDateTime? = null,
)
