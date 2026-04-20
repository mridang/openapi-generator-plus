package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import javax.annotation.Nullable;

@SuppressWarnings("deprecation")
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
