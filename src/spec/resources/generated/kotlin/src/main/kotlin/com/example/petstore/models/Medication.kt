package com.example.petstore.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Medication(
    /** Example: `null` */
    @SerialName("drugName")
    val drugName: String,
    /** Example: `null` */
    @SerialName("dosage")
    val dosage: String? = null,
)
