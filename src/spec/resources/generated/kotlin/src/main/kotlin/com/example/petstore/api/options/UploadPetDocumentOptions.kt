
package com.example.petstore.api.options

import com.example.petstore.models.Swatch
import java.time.OffsetDateTime

/**
 * Options for the uploadPetDocument operation.
 */
class UploadPetDocumentOptions(
    val _file: ByteArray,
    val documentType: String? = null,
    val notes: String? = null,
    val isPrimary: Boolean? = null,
    val pageCount: Long? = null,
    val takenAt: OffsetDateTime? = null,
    val swatch: Swatch? = null,
)
