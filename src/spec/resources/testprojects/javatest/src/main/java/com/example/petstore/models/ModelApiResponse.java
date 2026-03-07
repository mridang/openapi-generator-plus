package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;

public class ModelApiResponse {

  @JsonProperty("code")
  public Integer code;

  @JsonProperty("type")
  public String type;

  @JsonProperty("message")
  public String message;
}
