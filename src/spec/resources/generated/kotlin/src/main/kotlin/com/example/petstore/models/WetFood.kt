package com.example.petstore.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WetFood(
    /** Example: `null` */
    @SerialName("foodType")
    val foodType: String,
    /** Example: `null` */
    @SerialName("volumeMl")
    val volumeMl: Int,
)
