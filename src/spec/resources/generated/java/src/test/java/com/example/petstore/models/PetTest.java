package com.example.petstore.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.petstore.ObjectSerializer;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class PetTest {

  private static final TypeReference<Pet> PET_TYPE = new TypeReference<>() {};
  private final ObjectSerializer serializer = new ObjectSerializer();

  @Test
  void testRequiresNameField() {
    assertThatThrownBy(
            () -> serializer.deserialize("{\"photoUrls\":[\"http://photo.jpg\"]}", PET_TYPE))
        .describedAs("Pet should reject when required field 'name' is missing");
  }

  @Test
  void testRequiresPhotoUrlsField() {
    assertThatThrownBy(
            () -> serializer.deserialize("{\"name\":\"doggie\"}", PET_TYPE))
        .describedAs("Pet should reject when required field 'photoUrls' is missing");
  }

  @Test
  void testRejectsInvalidStatusEnum() {
    assertThatThrownBy(
            () ->
                serializer.deserialize(
                    "{\"name\":\"doggie\",\"photoUrls\":[],\"status\":\"invalid\"}", PET_TYPE))
        .describedAs("Invalid status enum value 'invalid' should be rejected");
  }

  @Test
  void testSerializesToJson() {
    Pet pet = new Pet("doggie", Set.of("http://photo.jpg"));
    pet.id = 1L;
    pet.status = Pet.StatusEnum.AVAILABLE;

    String json = serializer.serialize(pet);
    Pet deserialized = Objects.requireNonNull(serializer.deserialize(json, PET_TYPE));

    assertThat(deserialized.name).isEqualTo("doggie");
    assertThat(deserialized.id).isEqualTo(1L);
    assertThat(deserialized.photoUrls).containsExactly("http://photo.jpg");
    assertThat(deserialized.status).isEqualTo(Pet.StatusEnum.AVAILABLE);
  }
}
