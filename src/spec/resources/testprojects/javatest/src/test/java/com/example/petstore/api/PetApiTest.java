package com.example.petstore.api;

import com.example.petstore.Configuration;
import com.example.petstore.DefaultApiClient;
import com.example.petstore.models.Pet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the Pet API endpoints.
 */
class PetApiTest {

    private PetApi api;

    @BeforeEach
    void setUp() {
        String baseUrl = System.getenv("API_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:4010";
        }
        Configuration config = new Configuration();
        config.setBaseUrl(baseUrl);
        api = new PetApi(new DefaultApiClient(), config);
    }

    @Test
    void testAddPet() throws Exception {
        Pet pet = new Pet();
        pet.id = 12345L;
        pet.name = "TestDog";
        pet.photoUrls = List.of("http://example.com/photo.jpg");
        pet.status = Pet.StatusEnum.AVAILABLE;

        Pet result = api.addPet(pet);

        assertThat(result).isNotNull();
        assertThat(result.name).isNotNull();
    }

    @Test
    void testFindPetsByStatus() throws Exception {
        List<Pet> result = api.findPetsByStatus("available");

        assertThat(result).isNotNull();
        assertThat(result).isNotEmpty();
        assertThat(result.get(0)).isInstanceOf(Pet.class);
    }

    @Test
    void testGetPetById() throws Exception {
        Pet result = api.getPetById(1L);

        assertThat(result).isNotNull();
        assertThat(result.id).isNotNull();
        assertThat(result.name).isNotNull();
    }

    @Test
    void testUpdatePet() throws Exception {
        Pet pet = new Pet();
        pet.id = 1L;
        pet.name = "UpdatedDog";
        pet.photoUrls = List.of("http://example.com/updated.jpg");
        pet.status = Pet.StatusEnum.PENDING;

        Pet result = api.updatePet(1L, pet);

        assertThat(result).isNotNull();
    }

    @Test
    void testDeletePet() throws Exception {
        api.deletePet(1L);

        assertThat(true).isTrue();
    }
}
