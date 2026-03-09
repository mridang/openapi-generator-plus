package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

public class WetFood {

  @JsonProperty("foodType")
  @Nullable
  public String foodType;

  @JsonProperty("volumeMl")
  @Nullable
  public Integer volumeMl;

  public WetFood() {}

  public WetFood(String foodType, Integer volumeMl) {
    this.foodType = foodType;
    this.volumeMl = volumeMl;
  }
}
