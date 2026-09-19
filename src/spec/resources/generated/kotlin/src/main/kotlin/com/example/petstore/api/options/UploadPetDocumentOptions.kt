@file:Suppress("detekt:all")

package com.example.petstore.api.options

/**
 * Options for the uploadPetDocument operation.
 */
class UploadPetDocumentOptions(
    val _file: ByteArray,
    val documentType: String? = null,
    val notes: String? = null,
)
