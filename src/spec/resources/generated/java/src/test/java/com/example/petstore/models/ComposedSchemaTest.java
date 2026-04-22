package com.example.petstore.models;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.petstore.ObjectSerializer;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class ComposedSchemaTest {

  private static final TypeReference<PetWithOwner> PET_WITH_OWNER_TYPE = new TypeReference<>() {};
  private static final TypeReference<PetFood> PET_FOOD_TYPE = new TypeReference<>() {};
  private static final TypeReference<PetTreatment> PET_TREATMENT_TYPE = new TypeReference<>() {};
  private final ObjectSerializer serializer = new ObjectSerializer();

  @Test
  void testAllOfDeserializesPetWithOwner() {
    String json =
        "{\"name\":\"doggie\",\"photoUrls\":[\"http://example.com/photo.jpg\"],\"ownerName\":\"John\",\"ownerEmail\":\"john@example.com\"}";
    PetWithOwner result = Objects.requireNonNull(serializer.deserialize(json, PET_WITH_OWNER_TYPE));

    assertThat(result.name).isEqualTo("doggie");
    assertThat(result.ownerName).isEqualTo("John");
    assertThat(result.ownerEmail).isEqualTo("john@example.com");
  }

  @Test
  void testOneOfWithDiscriminatorDeserializesDryFood() {
    String json = "{\"foodType\":\"dry\",\"weightKg\":2.5}";
    Object result = Objects.requireNonNull(serializer.deserialize(json, PET_FOOD_TYPE));

    assertThat(result).isInstanceOf(DryFood.class);
    assertThat(((DryFood) result).foodType).isEqualTo("dry");
  }

  @Test
  void testAnyOfDeserializesMedication() {
    String json = "{\"drugName\":\"Amoxicillin\",\"dosage\":\"500mg\"}";
    PetTreatment result = Objects.requireNonNull(serializer.deserialize(json, PET_TREATMENT_TYPE));

    assertThat(result.getActualInstance()).isInstanceOf(Medication.class);
    assertThat(((Medication) result.getActualInstance()).drugName).isEqualTo("Amoxicillin");
  }
}
