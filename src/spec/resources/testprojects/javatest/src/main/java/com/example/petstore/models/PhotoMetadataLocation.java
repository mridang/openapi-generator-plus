package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

public class PhotoMetadataLocation {

  @JsonProperty("lat")
  @Nullable
  public Double lat;

  @JsonProperty("lng")
  @Nullable
  public Double lng;
}
