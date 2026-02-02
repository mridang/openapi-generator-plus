"""Integration tests for the Pet API endpoints."""

import pytest
from petstore_client.api.pet_api import PetApi
from petstore_client.api_client import ApiClient
from petstore_client.configuration import Configuration
from petstore_client.models.pet import Pet


class TestPetApi:
    """Test suite for PetApi operations."""

    @pytest.fixture(autouse=True)
    def setup(self, api_base_url):
        config = Configuration(host=api_base_url)
        client = ApiClient(config)
        self.api = PetApi(client)

    def test_add_pet(self):
        pet = Pet(
            id=12345,
            name='TestDog',
            photo_urls=['http://example.com/photo.jpg'],
            status='available'
        )

        result = self.api.add_pet(pet)

        assert result is not None
        assert result.name is not None

    def test_find_pets_by_status(self):
        result = self.api.find_pets_by_status('available')

        assert isinstance(result, list)
        assert len(result) > 0
        assert isinstance(result[0], Pet)

    def test_get_pet_by_id(self):
        result = self.api.get_pet_by_id(1)

        assert result is not None
        assert result.id is not None
        assert result.name is not None

    def test_update_pet(self):
        pet = Pet(
            id=1,
            name='UpdatedDog',
            photo_urls=['http://example.com/updated.jpg'],
            status='pending'
        )

        result = self.api.update_pet(1, pet)

        assert result is not None

    def test_delete_pet(self):
        self.api.delete_pet(1)

        assert True
