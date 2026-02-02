package com.example.petstore;

import com.example.petstore.api.PetApi;
import com.example.petstore.model.Pet;
import com.example.petstore.ApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PetApiTest {

    private PetApi api;

    @BeforeEach
    void setUp() {
        String baseUrl = System.getenv("API_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:4010";
        }
        ApiClient client = new ApiClient();
        client.setBasePath(baseUrl);
        api = new PetApi(client);
    }

    @Test
    void testAddPet() throws Exception {
        Pet pet = new Pet();
        pet.setId(12345L);
        pet.setName("TestDog");
        pet.setPhotoUrls(List.of("http://example.com/photo.jpg"));
        pet.setStatus(Pet.StatusEnum.AVAILABLE);

        Pet result = api.addPet(pet);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isNotNull();
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
        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isNotNull();
    }

    @Test
    void testUpdatePet() throws Exception {
        Pet pet = new Pet();
        pet.setId(1L);
        pet.setName("UpdatedDog");
        pet.setPhotoUrls(List.of("http://example.com/updated.jpg"));
        pet.setStatus(Pet.StatusEnum.PENDING);

        Pet result = api.updatePet(1L, pet);

        assertThat(result).isNotNull();
    }

    @Test
    void testDeletePet() throws Exception {
        // Delete operation - Prism will return success
        api.deletePet(1L);

        // If we get here without exception, the call succeeded
        assertThat(true).isTrue();
    }
}
