package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation")
public class DryFood {

  @JsonProperty("foodType")
  @Nullable
  public String foodType;

  @JsonProperty("weightKg")
  @Nullable
  public Double weightKg;

  public DryFood() {}

  public DryFood(String foodType, Double weightKg) {
    this.foodType = foodType;
    this.weightKg = weightKg;
  }
}
