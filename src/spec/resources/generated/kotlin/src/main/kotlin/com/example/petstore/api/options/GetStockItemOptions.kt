@file:Suppress("detekt:all")

package com.example.petstore.api.options

import java.time.OffsetDateTime

/**
 * Options for the getStockItem operation.
 */
class GetStockItemOptions(
    /** Only consider stock as of this instant */
    val asOf: OffsetDateTime? = null,
)
