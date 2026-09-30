
package com.example.petstore.api.options

/**
 * Options for the getPetTag operation.
 */
class GetPetTagOptions(
    val colors: List<String>? = null,
    val sizes: List<String>? = null,
    val filter: String? = null,
    /** Query value whose RFC 3986 reserved characters must be sent literally (OAS allowReserved), for example a version expression such as v1.0/beta:rc1 keeping the slash and colon. */
    val revision: String? = null,
)
