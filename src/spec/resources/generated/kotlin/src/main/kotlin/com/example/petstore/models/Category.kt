package com.example.petstore.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Category(
    /** Example: `1` */
    @SerialName("id")
    val id: Long? = null,
    /** Example: `Dogs` */
    @SerialName("name")
    val name: String? = null,
)
