package com.example.petstore.api.options

import com.example.petstore.models.PhotoMetadata

/**
 * Options for the addPetPhotos operation.
 */
class AddPetPhotosOptions(
    val files: List<ByteArray>,
    val metadata: PhotoMetadata,
)
