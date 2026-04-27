package com.example.petstore.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DryFood(
    /** Example: `null` */
    @SerialName("foodType")
    val foodType: String,
    /** Example: `null` */
    @SerialName("weightKg")
    val weightKg: Double,
)
