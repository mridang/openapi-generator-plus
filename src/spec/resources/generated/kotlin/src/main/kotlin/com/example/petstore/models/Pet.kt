package com.example.petstore.models

import com.example.petstore.models.Category
import com.example.petstore.models.Tag
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.collections.List
import kotlin.collections.Set

/**
 * Pet.
 *
 * @see <a href="https://example.com/docs/pet">Learn more about the Pet model</a>
 */
@Serializable
data class Pet(
    /** Example: `10` */
    @SerialName("id")
    val id: Long? = null,
    /** Example: `doggie` */
    @SerialName("name")
    val name: String,
    /** Example: `null` */
    @SerialName("category")
    val category: Category? = null,
    /** Example: `null` */
    @SerialName("photoUrls")
    val photoUrls: Set<String> = mutableSetOf(),
    /** Example: `null` */
    @SerialName("tags")
    val tags: List<Tag>? = null,
    /**
     * pet status in the store
     *
     * Example: `null`
     *
     * @deprecated This property is deprecated.
     */
    @Deprecated("This property is deprecated.")
    @SerialName("status")
    val status: StatusEnum? = null,
) {
    @Serializable
    enum class StatusEnum(
        val value: String,
    ) {
        @SerialName("available")
        AVAILABLE("available"),

        @SerialName("pending")
        PENDING("pending"),

        @SerialName("sold")
        SOLD("sold"),
        ;

        companion object {
            fun fromValue(value: String): StatusEnum =
                entries.firstOrNull { it.value == value }
                    ?: throw IllegalArgumentException("Unexpected value '$value'")
        }
    }
}
