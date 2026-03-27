package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import javax.annotation.Nullable;

@SuppressWarnings({"deprecation", "serial"})
public class Metadata {

  /** Example: {@code null} */
  @JsonProperty("createdAt")
  @Nullable
  public OffsetDateTime createdAt;

  private java.util.Map<String, Object> additionalProperties = new java.util.LinkedHashMap<>();

  @com.fasterxml.jackson.annotation.JsonAnySetter
  public void setAdditionalProperty(String key, Object value) {
    this.additionalProperties.put(key, value);
  }

  @com.fasterxml.jackson.annotation.JsonAnyGetter
  public java.util.Map<String, Object> getAdditionalProperties() {
    return this.additionalProperties;
  }
}
