package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public class SetPetAvatarThumbnailRequest {

  private Object actualInstance;

  @JsonCreator
  public SetPetAvatarThumbnailRequest(Object value) {
    this.actualInstance = value;
  }

  @JsonValue
  public Object getActualInstance() {
    return actualInstance;
  }
}
