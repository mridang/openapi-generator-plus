package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation")
public class PhotoMetadataLocation {

  /** Example: {@code null} */
  @JsonProperty("lat")
  @Nullable
  public Double lat;

  /** Example: {@code null} */
  @JsonProperty("lng")
  @Nullable
  public Double lng;
}
