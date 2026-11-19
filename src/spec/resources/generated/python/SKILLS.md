# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

```bash
pip install petstore-client
```

## Quick Start

```python statements
import os

from petstore_client.client import Client

host = os.environ["SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL"]
token = os.environ["SWAGGER_PETSTORE_OPEN_API_3_0_ACCESS_TOKEN"]
with Client.with_token(host, token) as client:
    result = await client.store.export_orders()
    print(result)
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Credentials per security scheme

`Client` takes one optional credential for each security scheme of the API, as a keyword argument named after the scheme: `pet_store_basic`, `admin_basic`, `pet_store_bearer`, `service_token`, `api_key_header`, `internal_api_key`, `api_key_query`, `session_cookie`, `machine_auth`, `legacy_auth`, `user_auth`, `browser_auth`, `sso_auth`. Each operation uses the credential of the first scheme its spec lists that you configured; there is no per-call override. A single `authenticator` is the default for an operation none of whose schemes you configured, and a call with no matching credential and no default sends none. The client is refused (`ValueError`) when it gets no credential at all.

### Bearer Token

```python file
from petstore_client.auth.bearer_authenticator import BearerAuthenticator
from petstore_client.client import Client

def make_client() -> Client:
    authenticator = BearerAuthenticator("https://api.example.com", "your-token")
    return Client(authenticator)
```

### Basic Auth

```python file
from petstore_client.auth.basic_authenticator import BasicAuthenticator
from petstore_client.client import Client

def make_client() -> Client:
    authenticator = BasicAuthenticator("https://api.example.com", "username", "password")
    return Client(authenticator)
```

### API Key

```python file
from petstore_client.auth.api_key_authenticator import ApiKeyAuthenticator
from petstore_client.auth.api_key_location import ApiKeyLocation
from petstore_client.client import Client

def make_client() -> Client:
    authenticator = ApiKeyAuthenticator("https://api.example.com", "key-name", "key-value", ApiKeyLocation.HEADER)
    return Client(authenticator)
```

### OAuth2 Client Credentials

```python file
from petstore_client.auth.oauth.oauth2_client_credentials_authenticator import OAuth2ClientCredentialsAuthenticator
from petstore_client.client import Client

def make_client() -> Client:
    authenticator = OAuth2ClientCredentialsAuthenticator(
        "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token")
    return Client(authenticator)
```

### OAuth2 Authorization Code

```python file
from petstore_client.auth.oauth.oauth2_authorization_code_authenticator import OAuth2AuthorizationCodeAuthenticator
from petstore_client.client import Client

def make_client() -> Client:
    authenticator = OAuth2AuthorizationCodeAuthenticator(
        "https://api.example.com", "client-id", "client-secret",
        "https://auth.example.com/authorize", "https://auth.example.com/token",
        "https://app.example.com/callback", ["read"])
    return Client(authenticator)
```

### OAuth2 Password

```python file
from petstore_client.auth.oauth.oauth2_password_authenticator import OAuth2PasswordAuthenticator
from petstore_client.client import Client

def make_client() -> Client:
    authenticator = OAuth2PasswordAuthenticator(
        "https://api.example.com", "client-id", "client-secret",
        "https://auth.example.com/token", "username", "password")
    return Client(authenticator)
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```python file
from petstore_client.auth.oauth.oauth2_implicit_authenticator import OAuth2ImplicitAuthenticator
from petstore_client.client import Client

def make_client() -> Client:
    authenticator = OAuth2ImplicitAuthenticator(
        "https://api.example.com", "client-id", "https://auth.example.com/authorize", ["read"])
    authenticator.set_access_token("your-access-token")
    return Client(authenticator)
```

### OpenID Connect

```python file
from petstore_client.auth.oauth.openid_connect_authenticator import OpenIdConnectAuthenticator
from petstore_client.client import Client

def make_client() -> Client:
    authenticator = OpenIdConnectAuthenticator(
        "https://api.example.com", "https://auth.example.com/.well-known/openid-configuration",
        "client-id", "client-secret", "https://app.example.com/callback", ["openid"])
    return Client(authenticator)
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators resolve the access token by making an HTTP call to the token endpoint, which happens before the request carrying it is sent.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed; tune your authorization server's `expires_in` if it is too tight.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethod.BODY` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod.BASIC` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```python file
from petstore_client.auth.oauth.client_auth_method import ClientAuthMethod
from petstore_client.auth.oauth.oauth2_client_credentials_authenticator import OAuth2ClientCredentialsAuthenticator

def make_authenticator() -> OAuth2ClientCredentialsAuthenticator:
    return OAuth2ClientCredentialsAuthenticator(
        "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token",
        client_auth_method=ClientAuthMethod.BASIC)
```

### Credentials from the environment

The authenticator written for each security scheme takes every credential as an optional argument. The argument you pass wins, then the environment variable named below, read once when the authenticator is built (an empty variable counts as unset). A credential found in neither place raises `ValueError` there, before the client exists. A bearer scheme also takes `token_provider=`, a callable returning the current token that runs for every request; it wins over the environment variable and cannot be combined with `token`.

| Scheme | Field | Environment variable |
|---|---|---|
| `adminBasic` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` |
| `adminBasic` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` |
| `apiKeyHeader` | `apiKey` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY` |
| `apiKeyQuery` | `apiKey` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY` |
| `browserAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID` |
| `internalApiKey` | `apiKey` | `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY` |
| `legacyAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID` |
| `legacyAuth` | `clientSecret` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET` |
| `legacyAuth` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME` |
| `legacyAuth` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD` |
| `machineAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID` |
| `machineAuth` | `clientSecret` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET` |
| `petStoreBasic` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME` |
| `petStoreBasic` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD` |
| `petStoreBearer` | `token` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN` |
| `serviceToken` | `token` | `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN` |
| `sessionCookie` | `apiKey` | `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY` |
| `ssoAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID` |
| `ssoAuth` | `clientSecret` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET` |
| `ssoAuth` | `redirectUri` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI` |
| `userAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID` |
| `userAuth` | `clientSecret` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET` |
| `userAuth` | `redirectUri` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI` |

## Per-call options

Every operation takes an optional trailing `options` argument, an Options object that carries three settings for that one call: `timeout` (milliseconds, at least 1), `headers` (they replace a default header of the same name) and `max_retries` (at least 0; `0` turns retrying off). `None` keeps the client's setting. This holds for an operation with no parameters too, so any call can be given a timeout or its own headers.

## Servers

If the OpenAPI spec defines multiple servers, the generated `petstore_client.servers` module exposes each as a `ServerConfiguration` (e.g., `SERVER_0`, `SERVER_1`, ...) plus an `ALL` list. Pass the desired server's URL to the client:

```python file
from petstore_client.servers import SERVER_0
from petstore_client.client import Client

def make_client() -> Client:
    return Client.with_token(SERVER_0.get_url(), "your-token")
```

## Testing

The `Authenticator` abstract base class is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```python file
from petstore_client.auth.authenticator import Authenticator
from petstore_client.client import Client

class FakeAuthenticator(Authenticator):
    def get_host(self) -> str:
        return "https://api.example.com"

    def get_auth_headers(self) -> dict[str, str]:
        return {"Authorization": "Bearer test-token"}

def make_client() -> Client:
    return Client(FakeAuthenticator())
```

## Error Handling

Every error the SDK raises derives from `OpenAPIException`; catching it handles them all. `ApiException` is only the HTTP branch: a response body that cannot be decoded raises `SerializationException`, and a failed OAuth2 token exchange raises `OAuth2ServerException` or `OAuth2TokenException`, all siblings of `ApiException`. The error hierarchy is:

- `OpenAPIException` (root)
  - `SerializationException` (a body that cannot be encoded or decoded)
  - `OAuth2ServerException` (the token endpoint answered with an OAuth2 error)
  - `OAuth2TokenException` (the token response was unusable)
  - `ApiException`
    - `ClientException` (4xx)
      - `BadRequestException` (400)
      - `UnauthorizedException` (401)
      - `ForbiddenException` (403)
      - `NotFoundException` (404)
      - `ConflictException` (409)
      - `UnprocessableEntityException` (422)
      - `TooManyRequestsException` (429)
      - `PaymentRequiredException` (402)
      - `MethodNotAllowedException` (405)
      - `RequestTimeoutException` (408)
      - `GoneException` (410)
      - `PayloadTooLargeException` (413)
      - `UnsupportedMediaTypeException` (415)
      - `LockedException` (423)
    - `ServerException` (5xx)
      - `InternalServerErrorException` (500)
      - `NotImplementedException` (501)
      - `BadGatewayException` (502)
      - `ServiceUnavailableException` (503)
      - `GatewayTimeoutException` (504)
    - `NetworkException` (no HTTP response, status 0)
      - `NetworkTimeoutException` (the request timed out, status 0)

```python file
from typing import Optional

from petstore_client.client import Client
from petstore_client.api.options import AddPetOptions
from petstore_client.errors import (
    NotFoundException,
    ClientException,
    ServerException,
    ApiException,
    OpenAPIException,
)
from petstore_client.models import Pet

async def add_pet_or_report(client: Client, pet: Pet, options: Optional[AddPetOptions] = None) -> None:
    try:
        result = await client.pet.add_pet(pet, options=options)
        print(result)
    except NotFoundException as e:
        print(f"Not found: {e}")
    except ClientException as e:
        print(f"Client error {e.status_code}: {e}")
    except ServerException as e:
        print(f"Server error: {e}")
    except ApiException as e:
        print(f"API error: {e}")
    except OpenAPIException as e:
        print(f"SDK error (serialization or OAuth2): {e}")
```

## Configuration

### Custom Transport Options

```python file
from petstore_client.transport_options import TransportOptions
from petstore_client.client import Client
from petstore_client.auth.authenticator import Authenticator

def make_client(authenticator: Authenticator) -> Client:
    transport = (
        TransportOptions.builder()
        .proxy("http://proxy:3128")
        .timeout(5000)
        .build()
    )

    return Client(authenticator, transport)
```

### Automatic retries

`TransportOptions.builder().max_retries(n)` sets the retries after a transient failure (default 2; 0 disables). Network errors before a response and 408, 409, 429 and 5xx (not 501) are retried with a 0.5 s to 8 s backoff and `Retry-After` honoured (at most 60 s). The retries stay inside the request timeout, a stream or multipart body is never retried, and each retry rebuilds the headers, authentication included.

```python file
from petstore_client.transport_options import TransportOptions

def make_transport() -> TransportOptions:
    return TransportOptions.builder().max_retries(0).build()
```

### OAuth2 re-authentication and PKCE

A 401 on a call made with an OAuth2 or OpenID Connect authenticator drops the cached access token (the refresh token is kept) and sends the call once more with fresh headers; a second 401 raises `UnauthorizedException`. `authenticator.invalidate_access_token()` does it by hand. The authorization-code flow adds a PKCE `S256` challenge to the URL from `build_authorization_url()` and sends the verifier in `exchange_code()`, so use the same authenticator for both.

### Custom HTTP client

```python file
import urllib3
from petstore_client.client import Client
from petstore_client.auth.authenticator import Authenticator

def make_client(authenticator: Authenticator) -> Client:
    pool = urllib3.PoolManager()
    return Client(authenticator, pool_manager=pool)
```

The SDK applies its timeout, user agent, redirect policy and request id on top of the pool manager, and never closes it: you own its lifecycle. Combining it with `proxy`, `ca_cert_path` or `verify_ssl(False)` raises `ValueError`; configure those on the pool manager instead.

### Timeouts

`timeout` is in milliseconds (default 10000) and is one end-to-end budget for the whole call, redirects and retries included. `None` removes it; a value below 1 raises `ValueError`; an exceeded budget raises `NetworkTimeoutException`.

### Request id

With `inject_request_id(True)` every request carries an `X-Request-ID` header unless you set one yourself.

### Response compression

Responses are decoded for you (`gzip` and `deflate`; `br` and `zstd` with `pip install petstore-client[compression]`). A decoded response drops `content-encoding` and `content-length` from its headers.

### Cancellation

Cancelling the asyncio task awaiting an operation aborts that request.

### Closing the client

```python file
from petstore_client.client import Client

def close(client: Client) -> None:
    client.close()
```

`client.close()` is graceful and safe to call again: calls already in flight finish, the transport the SDK created is released when the last of them completes, and a call started afterwards raises `RuntimeError`. It never closes a pool manager you supplied.

### Cookie parameters

A cookie parameter is sent as given when every character is a legal RFC 6265 cookie-octet; any other value raises `ValueError` before anything is sent, so percent-encode it yourself.

### Hooks and telemetry

One optional hook runs around every call: `Client(authenticator, call_hook=hook)` (or `Configuration.builder().call_hook(hook)`). `CallHook.start(info, headers)` runs once per call after the headers are final and may edit them; it returns a `CallObserver` (or `None`) whose `finish(outcome)` runs exactly once with the status (`0` when no response arrived), the lower-cased headers (`None` when none arrived) and the error. An error from `start` reaches the caller; an error from `finish` is swallowed. `chain(a, b)` starts in order and finishes in reverse. `OpenTelemetryCallHook()` from `petstore_client.opentelemetry_call_hook` records one span, `http.client.request.count` and `http.client.request.duration` per call; it needs `opentelemetry-api` (`pip install petstore-client[opentelemetry]`).

## API Methods

Each API group is exposed as a typed attribute on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are asynchronous; call them with `await`.

Every operation also has a `<operation>_with_http_info` variant that returns an `ApiResult` carrying `status_code`, `headers` (a read-only copy), `raw_body` and the typed `data`.

## Models

Models are generated as pydantic models in `petstore_client.models`.

```python file
from petstore_client.models.account import Account

def build() -> Account:
    model = Account(username="example")
    print(model.username)
    return model
```

Required fields must be passed to the constructor; a missing one raises `TypeError`. A constructor never validates: a value that does not fit its field is kept as given. Decoding validates (`SerializationException`).

## Binary / File Uploads

File upload parameters are typed as `bytes`. Binary response bodies are returned as `bytes`.

## Comment Style

Never place a comment on the same line as code. Use `#` comments; `"""` docstrings are fine.

```good
# This explains the logic
x = 1
```

```bad
x = 1  # This explains the logic
```
