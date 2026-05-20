package com.example.petstore.api.options

/**
 * Options for the uploadPetDocument operation.
 */
class UploadPetDocumentOptions(_file: ByteArray) {
    val _file: ByteArray = _file
    var documentType: String? = null
        private set
    var notes: String? = null
        private set

    fun documentType(documentType: String): UploadPetDocumentOptions = apply {
        this.documentType = documentType
    }

    fun notes(notes: String): UploadPetDocumentOptions = apply {
        this.notes = notes
    }

}
