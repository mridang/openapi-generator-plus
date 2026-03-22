from typing import Any, Dict, List, Optional, Union
from urllib.parse import quote

from petstore_client.models.api_response import ApiResponse
from petstore_client.models.pet import Pet
from petstore_client.models.pet_passport import PetPassport
from petstore_client.models.photo import Photo
from petstore_client.models.photo_metadata import PhotoMetadata
from petstore_client.models.set_pet_avatar_thumbnail_request import SetPetAvatarThumbnailRequest

from ..api_client import ApiClient
from ..default_api_client import DefaultApiClient
from ..configuration import Configuration
from .base_api import BaseApi
from ..value_serializer import ValueSerializer
from ..auth.authenticator import Authenticator


class PetApi(BaseApi):
    """PetApi provides methods for the pet API group.
    Everything about your Pets

    .. seealso::
        `Find out more about pets <https://example.com/docs/pets>`_
    """

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
            raise ValueError("Missing the required parameter 'pet'")

        path = '/pet'
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = pet

        return self._invoke_api(  # type: ignore[no-any-return]
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

    def add_pet_photos(
        self,
        pet_id: int,
        *,
        files: List[bytes],
        metadata: PhotoMetadata,
    ) -> List[Photo]:
        """Add photos to the pet&#39;s gallery
        Uploads one or more photos with structured metadata. The metadata part is serialised as JSON within the multipart body.
        :param pet_id:  (required)
        :param files:  (required)
        :param metadata:  (required)
        :return: List[Photo]
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id'")

        if files is None:
            raise ValueError("Missing the required parameter 'files'")

        if metadata is None:
            raise ValueError("Missing the required parameter 'metadata'")

        path = '/pet/{petId}/photos'
        path = path.replace('{' + 'petId' + '}', str(ValueSerializer.serialize(pet_id, 'path', 'int')))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body: Dict[str, Any] = {}
        body['files'] = files
        body['metadata'] = metadata

        return self._invoke_api(  # type: ignore[no-any-return]
            'POST',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'multipart/form-data',
            'List[Photo]',
            None,
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
            raise ValueError("Missing the required parameter 'pet_id'")

        path = '/pet/{petId}'
        path = path.replace('{' + 'petId' + '}', str(ValueSerializer.serialize(pet_id, 'path', 'int')))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(  # type: ignore[no-any-return]
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

    def download_pet_document(
        self,
        pet_id: int,
        document_id: int,
    ) -> bytes:
        """Download a vet document
        Returns the raw document bytes as an octet-stream. The original MIME type is communicated via the Content-Type response header.
        :param pet_id:  (required)
        :param document_id:  (required)
        :return: bytes
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id'")

        if document_id is None:
            raise ValueError("Missing the required parameter 'document_id'")

        path = '/pet/{petId}/documents/{documentId}'
        path = path.replace('{' + 'petId' + '}', str(ValueSerializer.serialize(pet_id, 'path', 'int')))
        path = path.replace('{' + 'documentId' + '}', str(ValueSerializer.serialize(document_id, 'path', 'int')))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(  # type: ignore[no-any-return]
            'GET',
            path,
            query_params,
            header_params,
            body,
            ['application/octet-stream'],
            'application/json',
            'bytes',
            None,
        )

    def find_pets_by_status(
        self,
        *,
        status: Optional[str] = None,
    ) -> List[Pet]:
        """Finds Pets by status
        :param status: Status values that need to be considered for filter (optional, default to available) (deprecated)
        :return: List[Pet]
        .. deprecated::
            This operation is deprecated.
        .. seealso::
            `Find out more about filtering <https://example.com/docs/filtering>`_
        """
        path = '/pet/findByStatus'
        query_params: Dict[str, Any] = {}
        if status is not None:
            query_params['status'] = ValueSerializer.serialize(status, 'query', 'str')
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(  # type: ignore[no-any-return]
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

    def get_pet_avatar(
        self,
        pet_id: int,
    ) -> bytes:
        """Get the pet&#39;s profile photo
        Returns the raw image bytes of the pet&#39;s current avatar.
        :param pet_id:  (required)
        :return: bytes
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id'")

        path = '/pet/{petId}/avatar'
        path = path.replace('{' + 'petId' + '}', str(ValueSerializer.serialize(pet_id, 'path', 'int')))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(  # type: ignore[no-any-return]
            'GET',
            path,
            query_params,
            header_params,
            body,
            ['image/jpeg', 'image/png'],
            'application/json',
            'bytes',
            None,
        )

    def get_pet_avatar_thumbnail(
        self,
        pet_id: int,
    ) -> bytes:
        """Get the pet&#39;s avatar thumbnail as base64
        Returns a compact base64-encoded thumbnail suitable for embedding directly in mobile UI without a separate image request.
        :param pet_id:  (required)
        :return: bytes
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id'")

        path = '/pet/{petId}/avatar/thumbnail'
        path = path.replace('{' + 'petId' + '}', str(ValueSerializer.serialize(pet_id, 'path', 'int')))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(  # type: ignore[no-any-return]
            'GET',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'application/json',
            'bytes',
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
        .. deprecated::
            This operation is deprecated.
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id'")

        path = '/pet/{petId}'
        path = path.replace('{' + 'petId' + '}', str(ValueSerializer.serialize(pet_id, 'path', 'int')))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(  # type: ignore[no-any-return]
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

    def get_pet_passport(
        self,
        pet_id: int,
    ) -> PetPassport:
        """Get the pet&#39;s passport
        Returns a single JSON document combining the pet&#39;s profile with an embedded base64 thumbnail and base64-encoded scans of each passport page, suitable for mobile clients that prefer a single-request workflow.
        :param pet_id:  (required)
        :return: PetPassport
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id'")

        path = '/pet/{petId}/passport'
        path = path.replace('{' + 'petId' + '}', str(ValueSerializer.serialize(pet_id, 'path', 'int')))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(  # type: ignore[no-any-return]
            'GET',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'application/json',
            'PetPassport',
            None,
        )

    def get_pet_photo(
        self,
        pet_id: int,
        photo_id: int,
    ) -> bytes:
        """Get a photo or its metadata
        Returns the raw image bytes or JSON metadata depending on the Accept header sent by the client.
        :param pet_id:  (required)
        :param photo_id:  (required)
        :return: bytes
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id'")

        if photo_id is None:
            raise ValueError("Missing the required parameter 'photo_id'")

        path = '/pet/{petId}/photos/{photoId}'
        path = path.replace('{' + 'petId' + '}', str(ValueSerializer.serialize(pet_id, 'path', 'int')))
        path = path.replace('{' + 'photoId' + '}', str(ValueSerializer.serialize(photo_id, 'path', 'int')))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = None

        return self._invoke_api(  # type: ignore[no-any-return]
            'GET',
            path,
            query_params,
            header_params,
            body,
            ['image/jpeg', 'image/png', 'application/json'],
            'application/json',
            'bytes',
            None,
        )

    def set_pet_avatar(
        self,
        pet_id: int,
        body: bytes,
    ) -> None:
        """Set the pet&#39;s profile photo
        Accepts either raw image bytes (image/jpeg or image/png) or a JSON envelope carrying a base64-encoded image for clients that prefer a JSON-only workflow.
        :param pet_id:  (required)
        :param body:  (required)
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id'")

        if body is None:
            raise ValueError("Missing the required parameter 'body'")

        path = '/pet/{petId}/avatar'
        path = path.replace('{' + 'petId' + '}', str(ValueSerializer.serialize(pet_id, 'path', 'int')))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = body

        return self._invoke_api(  # type: ignore[no-any-return]
            'PUT',
            path,
            query_params,
            header_params,
            body,
            [],
            'image/jpeg',
            None,
            None,
        )

    def set_pet_avatar_thumbnail(
        self,
        pet_id: int,
        set_pet_avatar_thumbnail_request: SetPetAvatarThumbnailRequest,
    ) -> None:
        """Set the pet&#39;s avatar thumbnail as base64
        Accepts either a single base64-encoded thumbnail or an array of candidates; the server selects the most suitable one.
        :param pet_id:  (required)
        :param set_pet_avatar_thumbnail_request:  (required)
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id'")

        if set_pet_avatar_thumbnail_request is None:
            raise ValueError("Missing the required parameter 'set_pet_avatar_thumbnail_request'")

        path = '/pet/{petId}/avatar/thumbnail'
        path = path.replace('{' + 'petId' + '}', str(ValueSerializer.serialize(pet_id, 'path', 'int')))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = set_pet_avatar_thumbnail_request

        return self._invoke_api(  # type: ignore[no-any-return]
            'PUT',
            path,
            query_params,
            header_params,
            body,
            [],
            'application/json',
            None,
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
            raise ValueError("Missing the required parameter 'pet_id'")

        if pet is None:
            raise ValueError("Missing the required parameter 'pet'")

        path = '/pet/{petId}'
        path = path.replace('{' + 'petId' + '}', str(ValueSerializer.serialize(pet_id, 'path', 'int')))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body = pet

        return self._invoke_api(  # type: ignore[no-any-return]
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

    def upload_pet_certificate(
        self,
        pet_id: int,
        *,
        file: bytes,
    ) -> ApiResponse:
        """Upload the pet&#39;s adoption certificate
        Attaches a single adoption certificate document. No metadata fields are required alongside the file.
        :param pet_id:  (required)
        :param file:  (required)
        :return: ApiResponse
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id'")

        if file is None:
            raise ValueError("Missing the required parameter 'file'")

        path = '/pet/{petId}/certificate'
        path = path.replace('{' + 'petId' + '}', str(ValueSerializer.serialize(pet_id, 'path', 'int')))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body: Dict[str, Any] = {}
        body['file'] = file

        return self._invoke_api(  # type: ignore[no-any-return]
            'POST',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'multipart/form-data',
            'ApiResponse',
            None,
        )

    def upload_pet_document(
        self,
        pet_id: int,
        *,
        file: bytes,
        document_type: Optional[str] = None,
        notes: Optional[str] = None,
    ) -> ApiResponse:
        """Attach a vet document or health record
        Accepts either a multipart upload with document classification fields, or a raw octet-stream for server-to-server and CLI clients that prefer to stream bytes directly.
        :param pet_id:  (required)
        :param file:  (required)
        :param document_type:  (optional)
        :param notes:  (optional)
        :return: ApiResponse
        """
        if pet_id is None:
            raise ValueError("Missing the required parameter 'pet_id'")

        if file is None:
            raise ValueError("Missing the required parameter 'file'")

        path = '/pet/{petId}/documents'
        path = path.replace('{' + 'petId' + '}', str(ValueSerializer.serialize(pet_id, 'path', 'int')))
        query_params: Dict[str, Any] = {}
        header_params: Dict[str, str] = {}
        body: Dict[str, Any] = {}
        body['file'] = file
        if document_type is not None:
            body['documentType'] = document_type
        if notes is not None:
            body['notes'] = notes

        return self._invoke_api(  # type: ignore[no-any-return]
            'POST',
            path,
            query_params,
            header_params,
            body,
            ['application/json'],
            'multipart/form-data',
            'ApiResponse',
            None,
        )
