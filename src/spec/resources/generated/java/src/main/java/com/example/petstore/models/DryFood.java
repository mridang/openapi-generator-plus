package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("deprecation")
public class DryFood {

  /** Example: {@code null} */
  @JsonProperty("foodType")
  public String foodType;

  /** Example: {@code null} */
  @JsonProperty("weightKg")
  public Double weightKg;

  @SuppressWarnings("NullAway.Init")
  public DryFood() {}

  @com.fasterxml.jackson.annotation.JsonCreator
  public DryFood(
      @JsonProperty(value = "foodType", required = true) String foodType,
      @JsonProperty(value = "weightKg", required = true) Double weightKg) {
    this.foodType = java.util.Objects.requireNonNull(foodType, "foodType is required");
    this.weightKg = java.util.Objects.requireNonNull(weightKg, "weightKg is required");
  }
}
