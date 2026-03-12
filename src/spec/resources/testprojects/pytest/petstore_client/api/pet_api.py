from typing import Any, Dict, List, Optional, Union
from urllib.parse import quote

from petstore_client.models.pet import Pet

from ..api_client import ApiClient
from ..default_api_client import DefaultApiClient
from ..configuration import Configuration
from .base_api import BaseApi
from ..object_serializer import ObjectSerializer
from ..auth.authenticator import Authenticator


class PetApi(BaseApi):
    """PetApi provides methods for the pet API group."""

    def __init__(
        self,
        api_client: Optional[ApiClient] = None,
        config: Optional[Configuration] = None,
    ):
        super().__init__(api_client, config)

    def add_pet(
        self,
        auth: Authenticator,
        pet: Pet,
    ) -> Pet:
        """Add a new pet to the store
        :param auth: authenticator for this operation
        :param pet: Create a new pet in the store (required)
        :return: Pet
        """
        if pet is None:
            raise ValueError("Missing the required parameter 'pet' when calling add_pet")
        path = '/pet'
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = pet

        return self._invoke_api(
            'POST',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'application/json',
            'Pet',
            auth,
        )

    def delete_pet(
        self,
        auth: Authenticator,
        pet_id: int,
    ) -> None:
        """Deletes a pet
        :param auth: authenticator for this operation
        :param pet_id: Pet id to delete (required)
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id' when calling delete_pet")
        path = '/pet/{petId}'
        path = path.replace('{' + 'petId' + '}', quote(ObjectSerializer.to_path_value(pet_id), safe=''))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(
            'DELETE',
            path,
            query_params,
            header_params,
            body,
            [],
            'application/json',
            None,
            auth,
        )

    def find_pets_by_status(
        self,
        status: Optional[str] = None,
    ) -> List[Pet]:
        """Finds Pets by status
        :param status: Status values that need to be considered for filter (optional, default to available)
        :return: List[Pet]
        """
        path = '/pet/findByStatus'
        query_params: Dict[str, Any] = {}
        if status is not None:
            query_params['status'] = ObjectSerializer.to_query_value(status)
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(
            'GET',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'application/json',
            'List[Pet]',
            None,
        )

    def get_pet_by_id(
        self,
        pet_id: int,
    ) -> Pet:
        """Find pet by ID
        Returns a single pet
        :param pet_id: ID of pet to return (required)
        :return: Pet
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id' when calling get_pet_by_id")
        path = '/pet/{petId}'
        path = path.replace('{' + 'petId' + '}', quote(ObjectSerializer.to_path_value(pet_id), safe=''))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(
            'GET',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'application/json',
            'Pet',
            None,
        )

    def update_pet(
        self,
        pet_id: int,
        pet: Pet,
    ) -> Pet:
        """Update an existing pet
        :param pet_id: ID of pet to update (required)
        :param pet: Pet object that needs to be updated (required)
        :return: Pet
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id' when calling update_pet")
        if pet is None:
            raise ValueError("Missing the required parameter 'pet' when calling update_pet")
        path = '/pet/{petId}'
        path = path.replace('{' + 'petId' + '}', quote(ObjectSerializer.to_path_value(pet_id), safe=''))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = pet

        return self._invoke_api(
            'PUT',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'application/json',
            'Pet',
            None,
        )
