package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

public class WetFood {

  @JsonProperty("foodType")
  public String foodType;

  @JsonProperty("volumeMl")
  public Integer volumeMl;

  public WetFood() {}

  public WetFood(String foodType, Integer volumeMl) {
    this.foodType = foodType;
    this.volumeMl = volumeMl;
  }
}
