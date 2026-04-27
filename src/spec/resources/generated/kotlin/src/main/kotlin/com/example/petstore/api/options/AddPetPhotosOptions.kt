package com.example.petstore.api.options

import com.example.petstore.models.PhotoMetadata

/**
 * Options for the addPetPhotos operation.
 */
class AddPetPhotosOptions(
    files: List<ByteArray>,
    metadata: PhotoMetadata,
) {
    val files: List<ByteArray> = files
    val metadata: PhotoMetadata = metadata
}
