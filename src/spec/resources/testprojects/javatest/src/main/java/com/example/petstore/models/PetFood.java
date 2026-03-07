package com.example.petstore.models;

import com.example.petstore.models.DryFood;
import com.example.petstore.models.WetFood;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;

/** Food for pets, discriminated by foodType */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "foodType",
    visible = true)
@JsonSubTypes({
  @JsonSubTypes.Type(value = DryFood.class, name = "dry"),
  @JsonSubTypes.Type(value = WetFood.class, name = "wet"),
  @JsonSubTypes.Type(value = DryFood.class, name = "DryFood"),
  @JsonSubTypes.Type(value = WetFood.class, name = "WetFood")
})
public abstract class PetFood {}
