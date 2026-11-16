# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

```bash
pip install petstore-client
```

## Quick Start

```python
from petstore_client.client import Client

client = Client.with_token("https://api.example.com", "your-token")
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```python
from petstore_client.auth.bearer_authenticator import BearerAuthenticator

authenticator = BearerAuthenticator("https://api.example.com", "your-token")
client = Client(authenticator)
```

### Basic Auth

```python
from petstore_client.auth.basic_authenticator import BasicAuthenticator

authenticator = BasicAuthenticator("https://api.example.com", "username", "password")
client = Client(authenticator)
```

### API Key

```python
from petstore_client.auth.api_key_authenticator import ApiKeyAuthenticator
from petstore_client.auth.api_key_location import ApiKeyLocation

authenticator = ApiKeyAuthenticator("https://api.example.com", "key-name", "key-value", ApiKeyLocation.HEADER)
client = Client(authenticator)
```

### OAuth2 Client Credentials

```python
from petstore_client.auth.oauth.oauth2_client_credentials_authenticator import OAuth2ClientCredentialsAuthenticator

authenticator = OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token")
client = Client(authenticator)
```

### OAuth2 Authorization Code

```python
from petstore_client.auth.oauth.oauth2_authorization_code_authenticator import OAuth2AuthorizationCodeAuthenticator

authenticator = OAuth2AuthorizationCodeAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/authorize", "https://auth.example.com/token",
    "https://app.example.com/callback", ["read"])
client = Client(authenticator)
```

### OAuth2 Password

```python
from petstore_client.auth.oauth.oauth2_password_authenticator import OAuth2PasswordAuthenticator

authenticator = OAuth2PasswordAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", "username", "password")
client = Client(authenticator)
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```python
from petstore_client.auth.oauth.oauth2_implicit_authenticator import OAuth2ImplicitAuthenticator

authenticator = OAuth2ImplicitAuthenticator(
    "https://api.example.com", "client-id", "https://auth.example.com/authorize", ["read"])
authenticator.set_access_token("your-access-token")
client = Client(authenticator)
```

### OpenID Connect

```python
from petstore_client.auth.oauth.openid_connect_authenticator import OpenIdConnectAuthenticator

authenticator = OpenIdConnectAuthenticator(
    "https://api.example.com", "https://auth.example.com/.well-known/openid-configuration",
    "client-id", "client-secret", "https://app.example.com/callback", ["openid"])
client = Client(authenticator)
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

```python
from petstore_client.auth.oauth.client_auth_method import ClientAuthMethod
from petstore_client.auth.oauth.oauth2_client_credentials_authenticator import OAuth2ClientCredentialsAuthenticator

authenticator = OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token",
    client_auth_method=ClientAuthMethod.BASIC)
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `petstore_client.servers` module exposes each as a `ServerConfiguration` (e.g., `SERVER_0`, `SERVER_1`, ...) plus an `ALL` list. Pass the desired server's URL to the client:

```python
from petstore_client.servers import SERVER_0

client = Client.with_token(SERVER_0.get_url(), "your-token")
```

## Testing

The `Authenticator` abstract base class is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```python
from petstore_client.auth.authenticator import Authenticator

class FakeAuthenticator(Authenticator):
    def get_host(self) -> str:
        return "https://api.example.com"

    def get_auth_headers(self) -> dict[str, str]:
        return {"Authorization": "Bearer test-token"}

client = Client(FakeAuthenticator())
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
    - `ServerException` (5xx)
      - `InternalServerErrorException` (500)
    - `NetworkException` (no HTTP response, status 0)
      - `NetworkTimeoutException` (the request timed out, status 0)

```python
from petstore_client.errors import (
    NotFoundException,
    ClientException,
    ServerException,
    ApiException,
    OpenAPIException,
)
from petstore_client.models import Pet

async def main():
    try:
        result = await client.pet.add_pet(Pet.model_construct())
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

```python
from petstore_client.transport_options import TransportOptions

transport = (
    TransportOptions.builder()
    .proxy("http://proxy:3128")
    .timeout(5000)
    .build()
)

client = Client(authenticator, transport)
```

### Automatic retries

`TransportOptions.builder().max_retries(n)` sets the retries after a transient failure (default 2; 0 disables). Network errors before a response and 408, 409, 429 and 5xx (not 501) are retried with a 0.5 s to 8 s backoff and `Retry-After` honoured (at most 60 s). The retries stay inside the request timeout, a stream or multipart body is never retried, and each retry rebuilds the headers, authentication included.

```python
transport = TransportOptions.builder().max_retries(0).build()
```

### OAuth2 re-authentication and PKCE

A 401 on a call made with an OAuth2 or OpenID Connect authenticator drops the cached access token (the refresh token is kept) and sends the call once more with fresh headers; a second 401 raises `UnauthorizedException`. `authenticator.invalidate_access_token()` does it by hand. The authorization-code flow adds a PKCE `S256` challenge to the URL from `build_authorization_url()` and sends the verifier in `exchange_code()`, so use the same authenticator for both.

### Custom HTTP client

```python
import urllib3

pool = urllib3.PoolManager()
client = Client(authenticator, pool_manager=pool)
```

The SDK applies its timeout, user agent, redirect policy and request id on top of the pool manager, and never closes it: you own its lifecycle. Combining it with `proxy`, `ca_cert_path` or `verify_ssl(False)` raises `ValueError`; configure those on the pool manager instead.

### Timeouts

`timeout` is in milliseconds (default 10000) and is one end-to-end budget for the whole call, redirects and retries included. `None` removes it; a value below 1 raises `ValueError`; an exceeded budget raises `NetworkTimeoutException`.

### Request id

With `inject_request_id(True)` every request carries an `X-Request-ID` header unless you set one yourself.

### Response compression

Responses are decoded for you (`gzip` and `deflate`; `br` and `zstd` with `pip install petstore-client[compression]`). A decoded response drops `content-encoding` and `content-length` from its headers.

### Cancellation

```python
client.close()
```

Cancelling the asyncio task awaiting an operation aborts its request, and `client.close()` cancels every request still in flight (those calls raise `RuntimeError`). It never closes a pool manager you supplied.

## API Methods

Each API group is exposed as a typed attribute on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are asynchronous; call them with `await`.

Every operation also has a `<operation>_with_http_info` variant that returns an `ApiResult` carrying `status_code`, `headers` (a read-only copy), `raw_body` and the typed `data`.

## Models

Models are generated as pydantic models in `petstore_client.models`.

```python
from petstore_client.models.account import Account

model = Account(username="example")
model.username
```

Required fields must be passed to the constructor; a missing one raises `pydantic.ValidationError`.

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
