package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
@SuppressWarnings({"deprecation", "serial"})
public class WetFood {

  /** Example: {@code null} */
  @JsonProperty("foodType")
  public String foodType;

  /** Example: {@code null} */
  @JsonProperty("volumeMl")
  public Integer volumeMl;

  @SuppressWarnings("NullAway.Init")
  public WetFood() {}

  @com.fasterxml.jackson.annotation.JsonCreator
  public WetFood(
      @JsonProperty(value = "foodType", required = true) String foodType,
      @JsonProperty(value = "volumeMl", required = true) Integer volumeMl) {
    this.foodType = java.util.Objects.requireNonNull(foodType, "foodType is required");
    this.volumeMl = java.util.Objects.requireNonNull(volumeMl, "volumeMl is required");
  }
}
