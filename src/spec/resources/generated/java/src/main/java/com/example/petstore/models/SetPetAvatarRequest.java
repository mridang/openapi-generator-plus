package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;

@SuppressWarnings("deprecation")
public class SetPetAvatarRequest {

  /**
   * Base64-encoded image data
   *
   * <p>Example: {@code null}
   */
  @JsonProperty("data")
  public byte[] data;

  /** Example: {@code image/jpeg} */
  @JsonProperty("mimeType")
  public String mimeType;

  @SuppressWarnings("NullAway.Init")
  public SetPetAvatarRequest() {}

  @com.fasterxml.jackson.annotation.JsonCreator
  public SetPetAvatarRequest(
      @JsonProperty(value = "data", required = true) byte[] data,
      @JsonProperty(value = "mimeType", required = true) String mimeType) {
    this.data = java.util.Objects.requireNonNull(data, "data is required");
    this.mimeType = java.util.Objects.requireNonNull(mimeType, "mimeType is required");
  }
}
