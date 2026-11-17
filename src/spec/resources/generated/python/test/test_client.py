import pytest
import urllib3

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


def _authorization(client: Client) -> str:
    api = client.pet
    assert api._authenticator is not None
    return api._authenticator.get_auth_headers()["Authorization"]


def _host(client: Client) -> str:
    api = client.pet
    return api._config.base_url


class TestClientCredentials:
    """F862: an argument wins, then the environment variable read once at construction."""

    def test_an_explicit_host_and_token_win_over_the_environment(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.setenv(
            "SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL", "https://env.example.com"
        )
        monkeypatch.setenv("SWAGGER_PETSTORE_OPEN_API_3_0_ACCESS_TOKEN", "env-token")

        client = Client.with_token("https://arg.example.com", "arg-token")

        assert _host(client) == "https://arg.example.com"
        assert _authorization(client) == "Bearer arg-token"

    def test_the_environment_fills_what_was_not_passed(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.setenv(
            "SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL", "https://env.example.com"
        )
        monkeypatch.setenv("SWAGGER_PETSTORE_OPEN_API_3_0_ACCESS_TOKEN", "env-token")

        client = Client.with_token()

        assert _host(client) == "https://env.example.com"
        assert _authorization(client) == "Bearer env-token"

    def test_the_environment_is_read_once_at_construction(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.setenv("SWAGGER_PETSTORE_OPEN_API_3_0_ACCESS_TOKEN", "first")
        client = Client.with_token("https://arg.example.com")

        monkeypatch.setenv("SWAGGER_PETSTORE_OPEN_API_3_0_ACCESS_TOKEN", "second")

        assert _authorization(client) == "Bearer first"

    def test_an_empty_environment_value_counts_as_unset(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.setenv("SWAGGER_PETSTORE_OPEN_API_3_0_ACCESS_TOKEN", "")

        with pytest.raises(
            ValueError, match="SWAGGER_PETSTORE_OPEN_API_3_0_ACCESS_TOKEN"
        ):
            Client.with_token("https://arg.example.com")

    def test_a_missing_host_fails_when_the_client_is_built(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.delenv("SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL", raising=False)

        with pytest.raises(ValueError, match="SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL"):
            Client.with_token(access_token="arg-token")

    def test_a_token_provider_runs_for_every_request(self) -> None:
        tokens = iter(["one", "two"])
        client = Client.with_token(
            "https://arg.example.com", token_provider=lambda: next(tokens)
        )

        assert _authorization(client) == "Bearer one"
        assert _authorization(client) == "Bearer two"

    def test_a_token_provider_needs_no_environment_token(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.delenv("SWAGGER_PETSTORE_OPEN_API_3_0_ACCESS_TOKEN", raising=False)

        client = Client.with_token(
            "https://arg.example.com", token_provider=lambda: "live"
        )

        assert _authorization(client) == "Bearer live"

    def test_a_token_and_a_token_provider_together_are_rejected(self) -> None:
        with pytest.raises(ValueError, match="not both"):
            Client.with_token(
                "https://arg.example.com", "arg-token", token_provider=lambda: "live"
            )


class _OwnedPool(urllib3.PoolManager):
    cleared = False

    def clear(self) -> None:
        self.cleared = True
        super().clear()


class TestClientPoolManager:
    def test_client_never_closes_a_supplied_pool_manager(self) -> None:
        pool = _OwnedPool()
        client = Client(BearerAuthenticator("/api/v3", "test-token"), pool_manager=pool)

        client.close()

        assert pool.cleared is False

    def test_closed_client_rejects_further_requests(self) -> None:
        client = Client(BearerAuthenticator("/api/v3", "test-token"))

        client.close()

        with pytest.raises(RuntimeError, match="closed"):
            client._api_client.send_request("GET", "/api/v3/x", {}, None)

    def test_supplied_pool_manager_stays_usable_after_close(self) -> None:
        pool = _OwnedPool()
        client = Client(BearerAuthenticator("/api/v3", "test-token"), pool_manager=pool)

        client.close()

        assert pool.cleared is False

    def test_with_token_accepts_a_pool_manager(self) -> None:
        pool = _OwnedPool()

        with Client.with_token("/api/v3", "test-token", pool_manager=pool):
            pass

        assert pool.cleared is False

    def test_supplied_pool_manager_conflicts_with_a_proxy(self) -> None:
        transport = (
            TransportOptions.builder().proxy("http://proxy.example.com:3128").build()
        )

        with pytest.raises(ValueError, match="pool_manager"):
            Client(
                BearerAuthenticator("/api/v3", "test-token"), transport, _OwnedPool()
            )
