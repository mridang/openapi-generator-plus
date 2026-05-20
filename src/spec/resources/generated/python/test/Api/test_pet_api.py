"""Integration tests for the Pet API endpoints."""

import pytest
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from typing import Any
from petstore_client.api.pet_api import PetApi
from petstore_client.api.options.add_pet_photos_options import AddPetPhotosOptions
from petstore_client.api.options.find_pets_by_status_options import FindPetsByStatusOptions
from petstore_client.api.options.get_pet_tag_options import GetPetTagOptions
from petstore_client.api.options.upload_pet_certificate_options import UploadPetCertificateOptions
from petstore_client.api.options.upload_pet_document_options import UploadPetDocumentOptions
from petstore_client.auth.bearer_authenticator import BearerAuthenticator
from petstore_client.configuration import Configuration
from petstore_client.models.api_response import ApiResponse
from petstore_client.models.pet import Pet, PetStatusEnum
from petstore_client.models.pet_passport import PetPassport
from petstore_client.models.photo_metadata import PhotoMetadata
from petstore_client.models.set_pet_avatar_thumbnail_request import SetPetAvatarThumbnailRequest

class TestPetApi:
    """Test suite for PetApi operations."""

    @pytest.fixture(autouse=True)
    def setup(self, api_base_url: Any) -> None:
        config = Configuration.builder() \
            .base_url(api_base_url) \
            .default_header('Authorization', 'Bearer test-token') \
            .build()
        self.api = PetApi(config=config)
        self.auth = BearerAuthenticator(api_base_url, 'test-token')

    async def test_add_pet(self) -> None:
        pet = Pet(
            id=12345,
            name='TestDog',
            photoUrls={'http://example.com/photo.jpg'},
            status=PetStatusEnum.AVAILABLE
        )

        result = await self.api.add_pet(self.auth, pet)

        assert result is not None
        assert result.name is not None

    async def test_find_pets_by_status(self) -> None:
        result = await self.api.find_pets_by_status(FindPetsByStatusOptions(status='available'))

        assert isinstance(result, list)
        assert len(result) > 0
        assert isinstance(result[0], Pet)

    async def test_get_pet_by_id(self) -> None:
        result = await self.api.get_pet_by_id(1)

        assert result is not None
        assert result.id is not None
        assert result.name is not None

    async def test_update_pet(self) -> None:
        pet = Pet(
            id=1,
            name='UpdatedDog',
            photoUrls={'http://example.com/updated.jpg'},
            status=PetStatusEnum.PENDING
        )

        result = await self.api.update_pet(1, pet)

        assert result is not None

    async def test_delete_pet(self) -> None:
        await self.api.delete_pet(self.auth, 1, None)

        assert True

    async def test_set_pet_avatar(self) -> None:
        await self.api.set_pet_avatar(1, b'\xFF\xD8\xFF')

        assert True

    async def test_get_pet_avatar(self) -> None:
        result = await self.api.get_pet_avatar(1)

        assert result is not None

    async def test_get_pet_avatar_thumbnail(self) -> None:
        result = await self.api.get_pet_avatar_thumbnail(1)

        assert result is not None

    async def test_set_pet_avatar_thumbnail(self) -> None:
        request = SetPetAvatarThumbnailRequest(actual_instance=b'\x89PNG')

        await self.api.set_pet_avatar_thumbnail(1, request)

        assert True

    async def test_upload_pet_certificate(self) -> None:
        result = await self.api.upload_pet_certificate(1, UploadPetCertificateOptions(file=b'cert-data'))

        assert result is not None
        assert isinstance(result, ApiResponse)

    async def test_upload_pet_document(self) -> None:
        result = await self.api.upload_pet_document(1, UploadPetDocumentOptions(file=b'doc-data', document_type='vaccination_record', notes='Annual checkup'))

        assert result is not None
        assert isinstance(result, ApiResponse)

    @pytest.mark.skip(reason='Prism does not validate multipart array fields correctly')
    async def test_add_pet_photos(self) -> None:
        metadata = PhotoMetadata(caption='Test photo', isPrimary=True)

        result = await self.api.add_pet_photos(1, AddPetPhotosOptions(files=[b'photo1'], metadata=metadata))

        assert result is not None
        assert isinstance(result, list)

    async def test_download_pet_document(self) -> None:
        result = await self.api.download_pet_document(1, 1)

        assert result is not None

    @pytest.mark.skip(reason='Prism returns JSON for image content type')
    async def test_get_pet_photo(self) -> None:
        result = await self.api.get_pet_photo(1, 1)

        assert result is not None

    async def test_get_pet_passport(self) -> None:
        result = await self.api.get_pet_passport(1)

        assert result is not None
        assert isinstance(result, PetPassport)

    @pytest.mark.skip(reason='Styled params require compatible mock server')
    async def test_get_pet_tag_styled_params(self) -> None:
        result = await self.api.get_pet_tag(5, 'cute', GetPetTagOptions(colors=['blue', 'black'], sizes=['S', 'M']))

        assert result is not None

    @pytest.mark.skip(reason='Per-operation server points to external URL')
    async def test_get_external_pet_info_uses_per_operation_server(self) -> None:
        result = await self.api.get_external_pet_info(1)

        assert result is not None

def _create_mock_server(status: int, content_type: str, body: str) -> tuple[PetApi, HTTPServer]:
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self) -> None:
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.end_headers()
            self.wfile.write(body.encode('utf-8'))

        def do_POST(self) -> None:
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.end_headers()
            self.wfile.write(body.encode('utf-8'))

        def log_message(self, format: str, *args: object) -> None:  # noqa: A002
            pass

    server = HTTPServer(('127.0.0.1', 0), Handler)
    port = server.server_address[1]
    thread = threading.Thread(target=server.handle_request)
    thread.daemon = True
    thread.start()

    config = Configuration.builder() \
        .base_url(f'http://127.0.0.1:{port}') \
        .build()
    api = PetApi(config=config)
    return api, server

class TestPetApiErrorHandling:
    """Test suite for PetApi error handling."""

    async def test_error_handling_not_found(self) -> None:
        api, server = _create_mock_server(404, 'application/json', '{"message":"Pet not found"}')
        with pytest.raises(Exception):
            await api.get_pet_by_id(99999)

    async def test_error_handling_server_error(self) -> None:
        api, server = _create_mock_server(500, 'application/json', '{"message":"Internal server error"}')
        with pytest.raises(Exception):
            await api.get_pet_by_id(1)

    async def test_download_binary_mock(self) -> None:
        api, server = _create_mock_server(200, 'application/octet-stream', 'FAKE_BINARY_DATA')
        result = await api.get_pet_avatar(1)
        assert result is not None

    async def test_upload_multipart_mock(self) -> None:
        api, server = _create_mock_server(200, 'application/json', '{"code":200,"type":"","message":"success"}')
        result = await api.upload_pet_certificate(1, UploadPetCertificateOptions(file=b'fake-cert-data'))
        assert result is not None

class TestPetApiWithHttpInfo:
    """Test suite for PetApi with_http_info methods."""

    @pytest.fixture(autouse=True)
    def setup(self, api_base_url: Any) -> None:
        config = Configuration.builder() \
            .base_url(api_base_url) \
            .default_header('Authorization', 'Bearer test-token') \
            .build()
        self.api = PetApi(config=config)
        self.auth = BearerAuthenticator(api_base_url, 'test-token')

    async def test_get_pet_by_id_with_http_info(self) -> None:
        result = await self.api.get_pet_by_id_with_http_info(1)

        assert result is not None
        assert result.status_code == 200
        assert result.data is not None
        assert result.raw_body is not None

    async def test_add_pet_with_http_info(self) -> None:
        pet = Pet(
            id=99,
            name='HttpInfoDog',
            photoUrls={'http://example.com/photo.jpg'},
            status=PetStatusEnum.AVAILABLE
        )

        result = await self.api.add_pet_with_http_info(self.auth, pet)

        assert result is not None
        assert 200 <= result.status_code < 300
        assert result.data is not None
