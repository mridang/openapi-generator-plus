package com.example.petstore.models;

import com.fasterxml.jackson.annotation.JsonValue;

/** A treatment that can match a medication, a surgery, or both */
@com.fasterxml.jackson.databind.annotation.JsonDeserialize(
    using = PetTreatment.PetTreatmentDeserializer.class)
@SuppressWarnings("deprecation")
public class PetTreatment {

  private static final Class<?>[] ANY_OF_SCHEMAS = {Medication.class, Surgery.class};

  private Object actualInstance;

  public PetTreatment(Object value) {
    this.actualInstance = value;
  }

  @JsonValue
  public Object getActualInstance() {
    return actualInstance;
  }

  static class PetTreatmentDeserializer
      extends com.fasterxml.jackson.databind.JsonDeserializer<PetTreatment> {
    @Override
    public PetTreatment deserialize(
        com.fasterxml.jackson.core.JsonParser p,
        com.fasterxml.jackson.databind.DeserializationContext ctxt)
        throws java.io.IOException {
      com.fasterxml.jackson.databind.JsonNode node = p.readValueAsTree();
      for (Class<?> schema : ANY_OF_SCHEMAS) {
        try {
          Object value = ctxt.readTreeAsValue(node, schema);
          return new PetTreatment(value);
        } catch (Exception ignored) {
          // try next schema
        }
      }
      return new PetTreatment(ctxt.readTreeAsValue(node, Object.class));
    }
  }
}
