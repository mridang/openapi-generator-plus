package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation")
public class Photo {

  /** Example: {@code null} */
  @JsonProperty("id")
  @Nullable
  public Long id;

  /** Example: {@code null} */
  @JsonProperty("caption")
  @Nullable
  public String caption;

  /** Example: {@code null} */
  @JsonProperty("isPrimary")
  @Nullable
  public Boolean isPrimary;

  /** Example: {@code null} */
  @JsonProperty("url")
  @Nullable
  public String url;
}
