package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation")
public class Category {

  @JsonProperty("id")
  @Nullable
  public Long id;

  @JsonProperty("name")
  @Nullable
  public String name;
}
