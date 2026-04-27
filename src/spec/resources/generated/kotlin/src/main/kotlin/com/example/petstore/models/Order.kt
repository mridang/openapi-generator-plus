package com.example.petstore.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class Order(
    /** Example: `10` */
    @SerialName("id")
    val id: Long? = null,
    /** Example: `198772` */
    @SerialName("petId")
    val petId: Long? = null,
    /** Example: `7` */
    @SerialName("quantity")
    val quantity: Int? = null,
    /** Example: `null` */
    @SerialName("shipDate")
    @Contextual
    val shipDate: OffsetDateTime? = null,
    /**
     * Order Status
     *
     * Example: `approved`
     */
    @SerialName("status")
    val status: StatusEnum? = null,
    /** Example: `null` */
    @SerialName("complete")
    val complete: Boolean? = null,
) {
    @Serializable
    enum class StatusEnum(
        val value: String,
    ) {
        @SerialName("placed")
        PLACED("placed"),

        @SerialName("approved")
        APPROVED("approved"),

        @SerialName("delivered")
        DELIVERED("delivered"),
        ;

        companion object {
            fun fromValue(value: String): StatusEnum =
                entries.firstOrNull { it.value == value }
                    ?: throw IllegalArgumentException("Unexpected value '$value'")
        }
    }
}
