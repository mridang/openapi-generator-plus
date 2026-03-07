package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;

public class DryFood {

  @JsonProperty("foodType")
  public String foodType;

  @JsonProperty("weightKg")
  public Double weightKg;

  public DryFood() {}

  public DryFood(String foodType, Double weightKg) {
    this.foodType = foodType;
    this.weightKg = weightKg;
  }
}
