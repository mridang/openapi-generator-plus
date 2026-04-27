package com.example.petstore.models

import com.example.petstore.models.Medication
import com.example.petstore.models.Surgery
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

/** A treatment that can match a medication, a surgery, or both */
@Serializable
class PetTreatment(
    @Contextual
    val actualInstance: Any? = null,
)
