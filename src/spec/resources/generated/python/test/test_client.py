from petstore_client.auth.bearer_authenticator import BearerAuthenticator
from petstore_client.client import Client
from petstore_client.transport_options import TransportOptions


class TestClient:
    def test_construct_with_authenticator_only(self) -> None:
        authenticator = BearerAuthenticator('/api/v3', 'test-token')

        client = Client(authenticator)

        assert client is not None

    def test_construct_with_authenticator_and_none_transport_options(self) -> None:
        authenticator = BearerAuthenticator('/api/v3', 'test-token')

        client = Client(authenticator, None)

        assert client is not None

    def test_construct_with_authenticator_and_transport_options(self) -> None:
        authenticator = BearerAuthenticator('/api/v3', 'test-token')
        transport = TransportOptions.builder().build()

        client = Client(authenticator, transport)

        assert client is not None

    def test_api_groups_are_accessible(self) -> None:
        authenticator = BearerAuthenticator('/api/v3', 'test-token')

        client = Client(authenticator)

        assert client.pet is not None
        assert client.store is not None
