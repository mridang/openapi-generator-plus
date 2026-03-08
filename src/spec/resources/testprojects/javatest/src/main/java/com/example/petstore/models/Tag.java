package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

public class Tag {

  @JsonProperty("id")
  public Long id;

  @JsonProperty("name")
  public String name;
}
