package com.example.petstore.models

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
class SetPetAvatarThumbnailRequest(
    @Contextual
    val actualInstance: Any? = null,
)
