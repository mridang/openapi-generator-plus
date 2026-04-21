package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/** Food for pets, discriminated by foodType */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "foodType",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = DryFood.class, name = "dry"),
  @JsonSubTypes.Type(value = WetFood.class, name = "wet")
})
@SuppressWarnings("deprecation")
public abstract class PetFood {}
