# ruff: noqa
# mypy: ignore-errors
import pytest

from petstore_client.auth.api_key_authenticator import ApiKeyAuthenticator
from petstore_client.auth.api_key_location import ApiKeyLocation
from petstore_client.auth.bearer_authenticator import BearerAuthenticator
from petstore_client.client import Client
from petstore_client.transport_options import TransportOptions


class TestClient:
    def test_bearer_rejects_crlf(self) -> None:
        with pytest.raises(ValueError):
            BearerAuthenticator("/api/v3", "tok\r\nInjected: yes")

    def test_bearer_rejects_non_ascii(self) -> None:
        with pytest.raises(ValueError):
            BearerAuthenticator("/api/v3", "ñoño")

    def test_bearer_rejects_empty_token(self) -> None:
        # bearer-no-empty-token-guard: an empty / whitespace-only token would
        # emit a bare "Authorization: Bearer " header and send the request
        # unauthenticated, so construction must reject it.
        with pytest.raises(ValueError):
            BearerAuthenticator("/api/v3", "")
        with pytest.raises(ValueError):
            BearerAuthenticator("/api/v3", "   ")

    def test_api_key_header_rejects_crlf(self) -> None:
        # RFC 7230 §3.2.6 — HEADER location must reject anything outside
        # printable ASCII + TAB to prevent header injection (\r\n) and
        # silent UTF-8 mangling that varies per HTTP lib.
        with pytest.raises(ValueError):
            ApiKeyAuthenticator(
                "/api/v3", "X-Api-Key", "abc\r\nInjected: yes", ApiKeyLocation.HEADER
            )

    def test_api_key_header_rejects_non_ascii(self) -> None:
        with pytest.raises(ValueError):
            ApiKeyAuthenticator("/api/v3", "X-Api-Key", "kéy", ApiKeyLocation.HEADER)

    def test_api_key_query_accepts_non_ascii(self) -> None:
        # Non-header locations accept arbitrary chars.
        auth = ApiKeyAuthenticator("/api/v3", "api_key", "kéy", ApiKeyLocation.QUERY)
        assert auth.get_query_params() == {"api_key": "kéy"}

    def test_construct_with_authenticator_only(self) -> None:
        authenticator = BearerAuthenticator("/api/v3", "test-token")

        client = Client(authenticator)

        assert client is not None

    def test_construct_with_authenticator_and_none_transport_options(self) -> None:
        authenticator = BearerAuthenticator("/api/v3", "test-token")

        client = Client(authenticator, None)

        assert client is not None

    def test_construct_with_authenticator_and_transport_options(self) -> None:
        authenticator = BearerAuthenticator("/api/v3", "test-token")
        transport = TransportOptions.builder().build()

        client = Client(authenticator, transport)

        assert client is not None

    def test_api_groups_are_accessible(self) -> None:
        authenticator = BearerAuthenticator("/api/v3", "test-token")

        client = Client(authenticator)

        assert client.pet is not None
        assert client.store is not None
