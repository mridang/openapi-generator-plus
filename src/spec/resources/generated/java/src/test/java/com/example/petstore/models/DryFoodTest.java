package com.example.petstore.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.petstore.ObjectSerializer;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class DryFoodTest {

  private static final TypeReference<DryFood> DRY_FOOD_TYPE = new TypeReference<>() {};
  private final ObjectSerializer serializer = new ObjectSerializer();

  @Test
  void testRequiresFoodTypeField() {
    assertThatThrownBy(
            () -> serializer.deserialize("{\"weightKg\":2.5}", DRY_FOOD_TYPE))
        .describedAs("DryFood should reject when required field 'foodType' is missing");
  }

  @Test
  void testRequiresWeightKgField() {
    assertThatThrownBy(
            () -> serializer.deserialize("{\"foodType\":\"dry\"}", DRY_FOOD_TYPE))
        .describedAs("DryFood should reject when required field 'weightKg' is missing");
  }

  @Test
  void testSerializesToJson() {
    DryFood food = new DryFood("dry", 2.5);

    String json = serializer.serialize(food);
    DryFood deserialized = Objects.requireNonNull(serializer.deserialize(json, DRY_FOOD_TYPE));

    assertThat(deserialized.foodType).isEqualTo("dry");
    assertThat(deserialized.weightKg).isEqualTo(2.5);
  }
}
