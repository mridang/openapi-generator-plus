@file:Suppress("detekt:all")

package com.example.petstore.api.options

import java.time.OffsetDateTime

/**
 * Options for the getStockItem operation.
 */
class GetStockItemOptions(
    val asOf: OffsetDateTime? = null,
)
