import asyncio
import inspect
import threading
from typing import Any

import pytest
import urllib3
from urllib3._collections import HTTPHeaderDict

from petstore_client.auth.api_key_authenticator import ApiKeyAuthenticator
from petstore_client.auth.api_key_location import ApiKeyLocation
from petstore_client.auth.bearer_authenticator import BearerAuthenticator
from petstore_client.auth.http_aware_authenticator import HttpAwareAuthenticator
from petstore_client.client import Client
from petstore_client.transport_options import TransportOptions


class TestClient:
    def test_bearer_rejects_crlf(self) -> None:
        with pytest.raises(ValueError):
            BearerAuthenticator("/api/v3", "tok\r\nInjected: yes")

    def test_bhv_lifecycle_006_bearer_rejects_non_ascii(self) -> None:
        with pytest.raises(ValueError):
            BearerAuthenticator("/api/v3", "ñoño")

    def test_bhv_lifecycle_005_bearer_rejects_empty_token(self) -> None:
        # bearer-no-empty-token-guard: an empty / whitespace-only token would
        # emit a bare "Authorization: Bearer " header and send the request
        # unauthenticated, so construction must reject it.
        with pytest.raises(ValueError):
            BearerAuthenticator("/api/v3", "")
        with pytest.raises(ValueError):
            BearerAuthenticator("/api/v3", "   ")

    def test_bhv_lifecycle_002_api_key_header_rejects_crlf(self) -> None:
        # RFC 7230 §3.2.6 — HEADER location must reject anything outside
        # printable ASCII + TAB to prevent header injection (\r\n) and
        # silent UTF-8 mangling that varies per HTTP lib.
        with pytest.raises(ValueError):
            ApiKeyAuthenticator(
                "/api/v3", "X-Api-Key", "abc\r\nInjected: yes", ApiKeyLocation.HEADER
            )

    def test_bhv_lifecycle_004_api_key_header_rejects_non_ascii(self) -> None:
        with pytest.raises(ValueError):
            ApiKeyAuthenticator("/api/v3", "X-Api-Key", "kéy", ApiKeyLocation.HEADER)

    def test_bhv_lifecycle_003_api_key_query_accepts_non_ascii(self) -> None:
        # Non-header locations accept arbitrary chars.
        auth = ApiKeyAuthenticator("/api/v3", "api_key", "kéy", ApiKeyLocation.QUERY)
        assert auth.get_query_params() == {"api_key": "kéy"}

    def test_bhv_lifecycle_012_construct_with_authenticator_only(self) -> None:
        authenticator = BearerAuthenticator("/api/v3", "test-token")

        client = Client(authenticator)

        assert client is not None

    def test_bhv_lifecycle_010_construct_with_authenticator_and_none_transport_options(
        self,
    ) -> None:
        authenticator = BearerAuthenticator("/api/v3", "test-token")

        client = Client(authenticator, None)

        assert client is not None

    def test_bhv_lifecycle_011_construct_with_authenticator_and_transport_options(
        self,
    ) -> None:
        authenticator = BearerAuthenticator("/api/v3", "test-token")
        transport = TransportOptions.builder().build()

        client = Client(authenticator, transport)

        assert client is not None

    def test_bhv_lifecycle_001_api_groups_are_accessible(self) -> None:
        authenticator = BearerAuthenticator("/api/v3", "test-token")

        client = Client(authenticator)

        assert client.pet is not None
        assert client.problems is not None
        assert client.store is not None


def _authorization(client: Client) -> str:
    api = client.pet
    assert api._authenticator is not None
    return api._authenticator.get_auth_headers()["Authorization"]


def _schemes(client: Client) -> dict[str, Any]:
    api = client.pet
    return dict(api._scheme_authenticators)


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

    def test_bhv_lifecycle_014_the_environment_is_read_once_at_construction(
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

    def test_bhv_lifecycle_013_a_missing_host_fails_when_the_client_is_built(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.delenv("SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL", raising=False)

        with pytest.raises(ValueError, match="SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL"):
            Client.with_token(access_token="arg-token")

    def test_bhv_lifecycle_017_a_token_provider_runs_for_every_request(self) -> None:
        tokens = iter(["one", "two"])
        client = Client.with_token(
            "https://arg.example.com", token_provider=lambda: next(tokens)
        )

        assert _authorization(client) == "Bearer one"
        assert _authorization(client) == "Bearer two"

    def test_bhv_lifecycle_016_a_token_provider_needs_no_environment_token(
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


class TestClientSchemeCredentials:
    """F857: one optional credential per security scheme, named after the scheme."""

    def test_a_client_built_with_no_credential_is_refused(self) -> None:
        with pytest.raises(ValueError, match="At least one credential is required"):
            Client()

    def test_the_default_authenticator_alone_is_enough(self) -> None:
        client = Client(
            authenticator=BearerAuthenticator("https://default.example.com", "tok")
        )

        assert _host(client) == "https://default.example.com"

    def test_bhv_lifecycle_015_the_client_takes_one_keyword_argument_per_security_scheme(
        self,
    ) -> None:
        parameters = inspect.signature(Client.__init__).parameters

        assert list(parameters) == [
            "self",
            "authenticator",
            "transport_options",
            "pool_manager",
            "pet_store_basic",
            "admin_basic",
            "pet_store_bearer",
            "service_token",
            "api_key_header",
            "internal_api_key",
            "api_key_query",
            "session_cookie",
            "machine_auth",
            "legacy_auth",
            "user_auth",
            "browser_auth",
            "sso_auth",
        ]

    def test_the_scheme_credentials_are_keyword_only(self) -> None:
        parameters = inspect.signature(Client.__init__).parameters

        for name in [
            "pet_store_basic",
            "admin_basic",
            "pet_store_bearer",
            "service_token",
            "api_key_header",
            "internal_api_key",
            "api_key_query",
            "session_cookie",
            "machine_auth",
            "legacy_auth",
            "user_auth",
            "browser_auth",
            "sso_auth",
        ]:
            assert parameters[name].kind is inspect.Parameter.KEYWORD_ONLY
            assert parameters[name].default is None

    def test_each_scheme_names_the_scheme_it_is_the_credential_of(self) -> None:
        held = BearerAuthenticator("https://pet_store_basic.example.com", "tok")

        client = Client(pet_store_basic=held)

        assert _schemes(client) == {"petStoreBasic": held}
        held = BearerAuthenticator("https://admin_basic.example.com", "tok")

        client = Client(admin_basic=held)

        assert _schemes(client) == {"adminBasic": held}
        held = BearerAuthenticator("https://pet_store_bearer.example.com", "tok")

        client = Client(pet_store_bearer=held)

        assert _schemes(client) == {"petStoreBearer": held}
        held = BearerAuthenticator("https://service_token.example.com", "tok")

        client = Client(service_token=held)

        assert _schemes(client) == {"serviceToken": held}
        held = BearerAuthenticator("https://api_key_header.example.com", "tok")

        client = Client(api_key_header=held)

        assert _schemes(client) == {"apiKeyHeader": held}
        held = BearerAuthenticator("https://internal_api_key.example.com", "tok")

        client = Client(internal_api_key=held)

        assert _schemes(client) == {"internalApiKey": held}
        held = BearerAuthenticator("https://api_key_query.example.com", "tok")

        client = Client(api_key_query=held)

        assert _schemes(client) == {"apiKeyQuery": held}
        held = BearerAuthenticator("https://session_cookie.example.com", "tok")

        client = Client(session_cookie=held)

        assert _schemes(client) == {"sessionCookie": held}
        held = BearerAuthenticator("https://machine_auth.example.com", "tok")

        client = Client(machine_auth=held)

        assert _schemes(client) == {"machineAuth": held}
        held = BearerAuthenticator("https://legacy_auth.example.com", "tok")

        client = Client(legacy_auth=held)

        assert _schemes(client) == {"legacyAuth": held}
        held = BearerAuthenticator("https://user_auth.example.com", "tok")

        client = Client(user_auth=held)

        assert _schemes(client) == {"userAuth": held}
        held = BearerAuthenticator("https://browser_auth.example.com", "tok")

        client = Client(browser_auth=held)

        assert _schemes(client) == {"browserAuth": held}
        held = BearerAuthenticator("https://sso_auth.example.com", "tok")

        client = Client(sso_auth=held)

        assert _schemes(client) == {"ssoAuth": held}

    def test_the_base_url_comes_from_the_first_credential_the_authenticator_first(
        self,
    ) -> None:
        scheme = BearerAuthenticator("https://scheme.example.com", "tok")
        default = BearerAuthenticator("https://default.example.com", "tok")

        with_default = Client(default, pet_store_basic=scheme)
        scheme_only = Client(pet_store_basic=scheme)

        assert _host(with_default) == "https://default.example.com"
        assert _host(scheme_only) == "https://scheme.example.com"

    def test_every_api_group_holds_the_same_credentials(self) -> None:
        held = BearerAuthenticator("https://scheme.example.com", "tok")
        default = BearerAuthenticator("https://default.example.com", "tok")

        client = Client(default, pet_store_basic=held)

        assert client.pet._authenticator is default
        assert list(client.pet._scheme_authenticators.values()) == [held]
        assert client.problems._authenticator is default
        assert list(client.problems._scheme_authenticators.values()) == [held]
        assert client.store._authenticator is default
        assert list(client.store._scheme_authenticators.values()) == [held]

    def test_a_credential_that_needs_the_transport_is_given_it(self) -> None:
        class _NeedsTransport(HttpAwareAuthenticator):
            def __init__(self) -> None:
                self.api_client: Any = None

            def get_host(self) -> str:
                return "https://needs.example.com"

            def get_auth_headers(self) -> dict[str, str]:
                return {}

            def get_query_params(self) -> dict[str, str]:
                return {}

            def get_cookie_params(self) -> dict[str, str]:
                return {}

            def set_api_client(self, api_client: Any) -> None:
                self.api_client = api_client

        credential = _NeedsTransport()

        client = Client(pet_store_basic=credential)

        assert credential.api_client is client._api_client


class _Reply:
    """The response a urllib3 pool manager hands back."""

    status = 200
    headers = HTTPHeaderDict({"content-type": "application/json"})

    def read(self) -> bytes:
        return b"{}"

    def release_conn(self) -> None:
        pass


class _SlowPool:
    """A pool manager double whose request waits until the test lets it finish."""

    def __init__(self) -> None:
        self.started = threading.Event()
        self.finish = threading.Event()
        self.requests = 0
        self.clears = 0

    def request(self, method: str, url: str, **kwargs: Any) -> _Reply:
        self.requests += 1
        self.started.set()
        self.finish.wait(10)
        return _Reply()

    def clear(self) -> None:
        self.clears += 1


def _owning(pool: Any) -> Client:
    """A client that owns ``pool`` as the transport it built itself."""
    client = Client(BearerAuthenticator("/api/v3", "test-token"))
    client._api_client._pool_manager = pool
    client._api_client._owns_pool = True
    return client


async def _send(client: Client) -> Any:
    return await asyncio.to_thread(
        client._api_client.send_request, "GET", "/api/v3/x", {}, None
    )


class TestClientClose:
    """F1078: closing is graceful and idempotent; requests in flight finish."""

    async def test_a_request_in_flight_finishes_and_the_transport_is_released_after_it(
        self,
    ) -> None:
        pool = _SlowPool()
        client = _owning(pool)
        request = asyncio.ensure_future(_send(client))
        await asyncio.to_thread(pool.started.wait, 5)

        client.close()

        assert pool.clears == 0
        pool.finish.set()
        assert (await request).status_code == 200
        assert pool.clears == 1

    async def test_the_transport_is_released_when_the_last_of_several_requests_ends(
        self,
    ) -> None:
        pool = _SlowPool()
        client = _owning(pool)
        first = asyncio.ensure_future(_send(client))
        second = asyncio.ensure_future(_send(client))
        while pool.requests < 2:
            await asyncio.sleep(0.01)

        client.close()
        pool.finish.set()
        await asyncio.gather(first, second)

        assert pool.clears == 1

    def test_closing_with_nothing_in_flight_releases_the_transport_at_once(
        self,
    ) -> None:
        pool = _SlowPool()

        _owning(pool).close()

        assert pool.clears == 1

    async def test_a_second_close_does_nothing(self) -> None:
        pool = _SlowPool()
        client = _owning(pool)
        request = asyncio.ensure_future(_send(client))
        await asyncio.to_thread(pool.started.wait, 5)

        client.close()
        client.close()
        pool.finish.set()
        await request
        client.close()

        assert pool.clears == 1

    async def test_a_closed_client_rejects_a_new_request_with_the_client_closed_error(
        self,
    ) -> None:
        pool = _SlowPool()
        client = _owning(pool)
        client.close()

        with pytest.raises(RuntimeError) as error:
            await _send(client)

        assert (
            str(error.value)
            == "ApiClient has been closed and can no longer send requests"
        )
        assert pool.requests == 0

    async def test_a_request_in_flight_is_not_aborted_by_close(self) -> None:
        pool = _SlowPool()
        client = _owning(pool)
        request = asyncio.ensure_future(_send(client))
        await asyncio.to_thread(pool.started.wait, 5)

        client.close()
        await asyncio.sleep(0.2)

        assert not request.done()
        pool.finish.set()
        await request

    async def test_bhv_lifecycle_009_a_pool_manager_the_caller_supplied_is_never_cleared(
        self,
    ) -> None:
        pool = _SlowPool()
        client = Client(BearerAuthenticator("/api/v3", "test-token"), pool_manager=pool)
        request = asyncio.ensure_future(_send(client))
        await asyncio.to_thread(pool.started.wait, 5)

        client.close()
        client.close()
        pool.finish.set()
        await request

        assert pool.clears == 0

    def test_leaving_the_with_block_closes_the_client(self) -> None:
        pool = _SlowPool()

        with _owning(pool):
            assert pool.clears == 0

        assert pool.clears == 1


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

    def test_bhv_lifecycle_008_closed_client_rejects_further_requests(self) -> None:
        client = Client(BearerAuthenticator("/api/v3", "test-token"))

        client.close()

        with pytest.raises(RuntimeError, match="closed"):
            client._api_client.send_request("GET", "/api/v3/x", {}, None)

    def test_bhv_lifecycle_007_supplied_pool_manager_stays_usable_after_close(
        self,
    ) -> None:
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
