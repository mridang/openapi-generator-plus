package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation")
public class ApiResponse {

  /** Example: {@code null} */
  @JsonProperty("code")
  @Nullable
  public Integer code;

  /** Example: {@code null} */
  @JsonProperty("type")
  @Nullable
  public String type;

  /** Example: {@code null} */
  @JsonProperty("message")
  @Nullable
  public String message;
}
