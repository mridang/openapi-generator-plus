package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

@SuppressWarnings({"deprecation", "serial"})
public class Category {

  /** Example: {@code 1} */
  @JsonProperty("id")
  @Nullable
  public Long id;

  /** Example: {@code Dogs} */
  @JsonProperty("name")
  @Nullable
  public String name;
}
