package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation")
public class Photo {

  @JsonProperty("id")
  @Nullable
  public Long id;

  @JsonProperty("caption")
  @Nullable
  public String caption;

  @JsonProperty("isPrimary")
  @Nullable
  public Boolean isPrimary;

  @JsonProperty("url")
  @Nullable
  public String url;
}
