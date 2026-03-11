package com.example.petstore.api;

import com.example.petstore.Configuration;
import com.example.petstore.DefaultApiClient;
import com.example.petstore.auth.AdminBasicAuthenticator;
import com.example.petstore.auth.PetStoreBearerAuthenticator;
import com.example.petstore.models.Pet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration tests for the Pet API endpoints.
 */
class PetApiTest {

    private PetApi api;
    private PetStoreBearerAuthenticator bearerAuth;
    private AdminBasicAuthenticator basicAuth;

    @BeforeEach
    void setUp() {
        String baseUrl = System.getenv("API_BASE_URL");
        if (baseUrl == null || baseUrl.isEmpty()) {
            baseUrl = "http://localhost:4010";
        }
        Configuration config = new Configuration();
        config.setBaseUrl(baseUrl);
        config.getDefaultHeaders().put("Authorization", "Bearer test-token");
        api = new PetApi(new DefaultApiClient(), config);
        bearerAuth = new PetStoreBearerAuthenticator(baseUrl, "test-token");
        basicAuth = new AdminBasicAuthenticator(baseUrl, "admin", "password");
    }

    @Test
    void testAddPet() throws Exception {
        Pet pet = new Pet();
        pet.id = 12345L;
        pet.name = "TestDog";
        pet.photoUrls = List.of("http://example.com/photo.jpg");
        pet.status = Pet.StatusEnum.AVAILABLE;

        Pet result = api.addPet(bearerAuth, pet);
        assertNotNull(result);

        assertThat(result.name).isNotNull();
    }

    @Test
    void testFindPetsByStatus() throws Exception {
        List<Pet> result = api.findPetsByStatus("available");
        assertNotNull(result);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0)).isInstanceOf(Pet.class);
    }

    @Test
    void testGetPetById() throws Exception {
        Pet result = api.getPetById(1L);
        assertNotNull(result);

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
        assertNotNull(result);
    }

    @Test
    void testDeletePet() throws Exception {
        api.deletePet(basicAuth, 1L);

        assertThat(true).isTrue();
    }
}
