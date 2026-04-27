package com.example.petstore.models

import com.example.petstore.models.DryFood
import com.example.petstore.models.WetFood
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** Food for pets, discriminated by foodType */
@Serializable
@JsonClassDiscriminator("foodType")
abstract class PetFood
