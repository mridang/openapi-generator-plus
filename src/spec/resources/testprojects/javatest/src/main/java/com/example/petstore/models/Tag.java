package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

public class Tag {

  @JsonProperty("id")
  @Nullable
  public Long id;

  @JsonProperty("name")
  @Nullable
  public String name;
}
