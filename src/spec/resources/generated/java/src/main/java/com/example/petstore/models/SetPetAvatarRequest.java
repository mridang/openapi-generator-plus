package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation")
public class SetPetAvatarRequest {

  /**
   * Base64-encoded image data
   *
   * <p>Example: {@code null}
   */
  @JsonProperty("data")
  @Nullable
  public byte[] data;

  /** Example: {@code image/jpeg} */
  @JsonProperty("mimeType")
  @Nullable
  public String mimeType;

  public SetPetAvatarRequest() {}

  public SetPetAvatarRequest(byte[] data, String mimeType) {
    this.data = data;
    this.mimeType = mimeType;
  }
}
