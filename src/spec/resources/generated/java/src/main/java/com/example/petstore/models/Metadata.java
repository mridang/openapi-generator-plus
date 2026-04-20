package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.OffsetDateTime;
import javax.annotation.Nullable;

@SuppressFBWarnings({"EI_EXPOSE_REP", "EI_EXPOSE_REP2", "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD"})
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
