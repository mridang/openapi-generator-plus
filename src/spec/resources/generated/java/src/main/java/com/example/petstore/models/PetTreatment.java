package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** A treatment that can match a medication, a surgery, or both */
@SuppressWarnings("deprecation")
public class PetTreatment {

  private Object actualInstance;

  @JsonCreator
  public PetTreatment(Object value) {
    this.actualInstance = value;
  }

  @JsonValue
  public Object getActualInstance() {
    return actualInstance;
  }
}
