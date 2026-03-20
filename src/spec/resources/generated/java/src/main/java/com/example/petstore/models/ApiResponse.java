package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation")
public class ApiResponse {

  @JsonProperty("code")
  @Nullable
  public Integer code;

  @JsonProperty("type")
  @Nullable
  public String type;

  @JsonProperty("message")
  @Nullable
  public String message;
}
