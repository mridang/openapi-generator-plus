# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated Python SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- Python 3.10+
- `pip`

## Installation

```bash
pip install petstore-client
```

## Usage

```python
import asyncio

from petstore_client.client import Client
from petstore_client.models import Pet

async def main() -> None:
    with Client.with_token("https://api.example.com", "your-token") as client:
        result = await client.pet.add_pet(Pet.model_construct())
        print(result)

asyncio.run(main())
```

See [SKILLS.md](SKILLS.md) for authentication, errors, transport options and a worked example.

## Configuration

`TransportOptions.builder()` sets `timeout` (milliseconds, one end-to-end budget across redirects and retries; `None` or at least 1), `proxy`, `verify_ssl`, `ca_cert_path`, `follow_redirects`, `max_redirects`, `user_agent`, `default_headers`, `inject_request_id` and `max_retries`. The proxy is exactly what you configure: `HTTP_PROXY` and `NO_PROXY` are ignored. With `inject_request_id(True)` every request carries an `X-Request-ID` header unless you send one.

### Custom HTTP client

Pass a `urllib3.PoolManager` you built to the client
(`Client(authenticator, pool_manager=pool)`; `with_token` and
`with_authenticator` take it too). The SDK defaults (timeout, redirect policy,
user agent, request id, default headers) are applied on top of it, and the SDK
never closes it: you own its lifecycle. Combining it with a proxy, a CA bundle
or `verify_ssl(False)` in `TransportOptions` raises `ValueError`, because those
settings belong to the pool manager you built.

### Automatic retries

A request that fails with a transient error is retried by the SDK. `TransportOptions.builder().max_retries(n)` sets how many times (default 2, so up to three attempts); `max_retries(0)` disables retrying.

```python
transport = TransportOptions.builder().max_retries(4).build()
```

A request is retried after a network error that happened before a response arrived, and after a 408, 409, 429 or 5xx response (not 501). The wait starts at 0.5 s, doubles up to 8 s and is shortened by up to 25% at random; a `Retry-After` header (seconds or an HTTP date, at most 60 s) replaces it. Retries never extend the request timeout: when the next wait would outlast what is left of it, the last failure is reported, and a timeout itself is not retried. A stream or multipart body is never retried, and every retry is a new request with freshly built authentication headers.

### OAuth2 and OpenID Connect

When the API answers 401 to a call authenticated through an OAuth2 or OpenID Connect authenticator, the SDK drops the cached access token (the refresh token is kept), obtains a new one and sends the call once more; a second 401 raises `UnauthorizedException`. A stream or multipart body is not sent again. `invalidate_access_token()` on the authenticator does the same by hand.

The authorization-code flow uses PKCE (`S256`): `build_authorization_url()` creates the code verifier, keeps it in the authenticator and puts the `code_challenge` on the URL, and `exchange_code()` sends the `code_verifier`. Build the URL and exchange the code on the same authenticator.

A token response whose `token_type` is not `bearer` (any case) raises `OAuth2TokenException`, and an OpenID Connect discovery document whose `issuer` differs from the discovery URL, or whose endpoints are not https (loopback hosts excepted), raises `SerializationException`.

### Response compression

The client sends an `Accept-Encoding` header naming the codecs it can decode (`gzip` and `deflate` always; `br` and `zstd` when the optional extra is installed with `pip install petstore-client[compression]`) and decodes the body for you. A decoded response drops `content-encoding` and `content-length` from `headers`; a body sent with `Content-Encoding: identity` is passed through untouched.

### Raw responses

Every operation has a `<operation>_with_http_info` variant returning an `ApiResult` with `status_code`, `headers` (a read-only copy), `raw_body` and the typed `data`.

### Cancellation

Cancelling the asyncio task that awaits an operation aborts that request
itself: its socket is shut down and the connection dropped, instead of the
call running on in a worker thread. To bound how long a call can take, set the
request timeout on `TransportOptions`; a call that exceeds it fails with
`NetworkTimeoutException`.

### Closing the client

`Client.close()` (also `with Client(...)`) is graceful and safe to call again.
It marks the client closed at once; calls already in flight, retries included,
run to their end, and the transport the SDK created is released when the last
of them completes. A call started afterwards raises `RuntimeError`
(`ApiClient has been closed and can no longer send requests`). A
`urllib3.PoolManager` you passed in is never closed.

### Credentials

`Client.with_token(host, access_token)` builds a client with a Bearer token. Each credential is resolved once, when the client is built: the argument you pass wins, then the environment variable (`SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL` for the host, `SWAGGER_PETSTORE_OPEN_API_3_0_ACCESS_TOKEN` for the token); an empty value counts as unset. A host or token that cannot be resolved raises `ValueError` there and then, not on the first request. Pass `token_provider=` (a callable returning the current token, run for every request) instead of a static token when the token rotates. 

The authenticator written for each security scheme in the API does the same for its own credentials: the argument you pass wins, then its environment variable, read once when the authenticator is built (an empty variable counts as unset), and a credential found in neither place raises `ValueError` there and then. A bearer scheme also accepts `token_provider=` in place of `token`. The variables are:

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

Set `envVarPrefix` when generating to change the `SWAGGER_PETSTORE_OPEN_API_3_0` prefix.

### Credentials per security scheme

`Client` takes one optional credential for each security scheme of the API, as a keyword argument named after the scheme: `pet_store_basic`, `admin_basic`, `pet_store_bearer`, `service_token`, `api_key_header`, `internal_api_key`, `api_key_query`, `session_cookie`, `machine_auth`, `legacy_auth`, `user_auth`, `browser_auth`, `sso_auth`. Each operation uses the credential of the first scheme its spec lists that you configured; there is no per-call override. A single `authenticator` is the default for an operation none of whose schemes you configured, and a call with no matching credential and no default sends none. The base URL comes from the first credential given, `authenticator` first, and a client built with no credential at all raises `ValueError`.

```python
client = Client(pet_store_basic=PetStoreBasicAuthenticator())
```

### Cookie parameters

A cookie parameter is sent exactly as you give it when every character is a legal RFC 6265 cookie-octet. A value with any other character (a space, comma, semicolon, double quote, backslash or a non-ASCII character) raises `ValueError` before anything is sent, so percent-encode it yourself first.

### Per-call options

Every operation takes an optional `options` argument, an Options object that also carries three settings for that one call: `timeout` (milliseconds, one end-to-end budget of at least 1), `headers` (they replace a default header of the same name) and `max_retries` (at least 0; `0` turns retrying off). `None` keeps the client's setting.

```python
await client.pet.add_pet(Pet.model_construct(), options=AddPetOptions(timeout=2000, max_retries=0))
```

A custom `ApiClient` owns its own timeout, so only the headers and the retry budget reach it.

### Errors

An API error (`ApiException` and every subclass) carries `status_code`, `response_headers`, `response_body`, `error_body`, and the request it answers: `request_method` and `request_path` (the path without its query string). `str(error)` shows them on a `Request: METHOD /path` line before the status code.

### Operations with several success responses

When an operation declares more than one 2xx response with different schemas, it returns a union (a generated `<Operation>Response` model with the decoded value in `actual_instance`), and the body is decoded by the status code the server answered with, never by guessing from its shape.

### Constructing and decoding models

Constructing a model never validates and never raises on a value: a value that fits its field is coerced as usual (a list given for a set field becomes a set), and one that does not, such as a wrong type, an unknown enum string or a value outside a constraint, is kept exactly as given and sent to the server. Only a required field you leave out raises `TypeError`. Validation happens when a document is decoded, which raises `SerializationException`, and an unknown enum value fails there loudly.

### Models are immutable

Models are frozen pydantic models: assigning a field raises `pydantic.ValidationError`, and they are hashable (a model that holds a list, map or set is hashed by value), so they work as dict keys and set members. Make a changed copy with `model.model_copy(update={"name": "new"})`; the update is not validated, and neither is a constructor, so validate a value that comes from outside with `Model.model_validate(...)`.

### Logging and tracing

The SDK writes no log lines. To log requests, pass your own `urllib3.PoolManager` or wrap `DefaultApiClient`; every `repr` of a configuration, authenticator or error masks credentials. When `opentelemetry` is installed, the current trace context is sent as `traceparent` and `tracestate` request headers.

### Versioning and supported Python

The package version follows semantic versioning; the clients, Options objects, models and errors are the public API, and a name that starts with an underscore is private. Python is supported from the oldest release that has not reached end of life; the floor moves up when a release does.

### Behaviours to know

- Optional and nullable: an optional property that is absent and one that is `null` both read as `None`, and `None` is left out when a model is encoded, so a decoded `null` is not sent again. A required, nullable property is the exception: the key must be present, `null` is kept, and encoding writes it.
- Unknown values fail loudly: an enum value or a discriminator value the SDK does not know raises `SerializationException` and names the value; it is never coerced to a default.
- Repeated response headers are joined into one value with `, ` in `headers` and `response_headers` (so repeated `Set-Cookie` or `Link` lines arrive as one string).
- Streaming is not supported: a response is read whole before it is decoded, so server-sent events and JSON Lines are out of scope. Supply your own `ApiClient` or `urllib3.PoolManager` for those calls.
- `Configuration` (base URL, default headers) and `TransportOptions` (timeout, proxy, TLS, retries) stay separate objects: the client builds the first from the authenticator's host and takes the second as an argument.

## Package

- Name: `petstore-client`
- Version: `1.0.0`
- License: MIT

## OpenAPI type mapping

This SDK leans on pydantic 2's native typed surfaces:

| OAS `type` / `format`              | Python type                       |
| ---------------------------------- | --------------------------------- |
| `string`                           | `pydantic.StrictStr`              |
| `integer`                          | `pydantic.StrictInt`              |
| `number` (no format)               | `decimal.Decimal` (`JsonNumber`)  |
| `number`, `format: float` / `double` | `pydantic.StrictFloat`          |
| `boolean`                          | `pydantic.StrictBool`             |
| `string`, `format: uri`            | URL-validated `str` (round-trips verbatim) |
| `string`, `format: uri-reference`  | `pydantic.StrictStr` (may be relative) |
| `string`, `format: uri-template`   | `pydantic.StrictStr` (RFC 6570)   |
| `string`, `format: password`       | `pydantic.SecretStr`              |
| `string`, `format: date-time`      | `pydantic.AwareDatetime` (tz-aware) |
| `string`, `format: date`           | `datetime.date`                   |
| `string`, `format: time`           | `datetime.time` (written `HH:MM:SS`, plus `.SSS` only when the millisecond part is non-zero) |
| `string`, `format: duration`       | `datetime.timedelta`              |
| `string`, `format: uuid`           | `uuid.UUID`                       |
| `string`, `format: byte/binary`    | `bytes`                           |

`Strict*` types reject lax coercion (`"1"` → `1`, `1.0` → `1`, etc.),
matching the strict-type-coercion contract of the other SDKs.
`AwareDatetime` rejects naive datetimes — RFC 3339 mandates a timezone
offset.

## Caveats

### Decimal / `format: number` precision

A bare `type: number` (no `format`) maps to `decimal.Decimal`, which
preserves exact decimal representation in Python — safe for in-process
arithmetic on monetary values. On the wire it serializes as an UNQUOTED
JSON number (`1.5`, never the quoted string `"1.5"`) because the spec
types it as a JSON number; the exact decimal digits are written, never
rounded through a binary `float`, and the digits of a response number are
read back exactly. A quoted string is rejected for a `type: number` field. `format: float` and `format: double` map to
`pydantic.StrictFloat`, i.e. Python's native `float`, which is a 64-bit
IEEE-754 binary float. Values such fields carry cannot represent every
decimal fraction exactly: `0.1 + 0.2` in Python is `0.30000000000000004`.

Do not do arithmetic on prices, balances, or other money-typed fields
that the spec declares as `format: float` / `format: double`. If you need
exact decimal arithmetic, model the field as a bare `type: number` so it
deserializes to `decimal.Decimal`, or parse the raw response body yourself
and feed the string into `decimal.Decimal`.

`format: int64` is unaffected — Python's `int` is arbitrary-precision and
represents the full 64-bit range (and beyond) without loss.

### Schema constraints

The constraints `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`,
`multipleOf`, `minItems` and `maxItems` are validated by the server, which stays
the authority. This SDK also checks them in the generated models, because
pydantic does so natively (`Field(...)` constraints): decoding a response that
violates one raises `SerializationException`, while constructing a model never
validates and sends the value you gave.
Request parameters outside a model are not checked client-side. `uniqueItems`
arrays are sent in the collection's natural order (a Python `set` in iteration
order), never sorted.

## Not supported

### Webhooks and callbacks

This SDK is **client → server** only. Spec entries describing
server-initiated calls — OAS 3.1 top-level `webhooks` and OAS 3.0
per-operation `callbacks` — are intentionally skipped during code
generation. If you need to receive webhook deliveries, write the
handler yourself and use this SDK only to deserialize the incoming
payload (e.g. by reusing the relevant request-body model).

### XML bodies and `nodeType`

Only JSON, form, multipart and binary bodies are generated. `application/xml` request and response bodies and the `xml` schema keywords (`nodeType`, `attribute`, `wrapped`) are not supported.

### Link Objects

Response Link Objects are ignored; follow links in your own code.

### Mutual TLS security schemes

`mutualTLS` security schemes are not generated. Supply a client certificate through your own `urllib3.PoolManager` (see Custom HTTP client).

### Conditional-required validation (`dependentRequired` / `dependentSchemas`)

JSON Schema 2019-09 keywords for "if field X is present, field Y is
also required" are **not enforced** by this SDK. No mainstream
OpenAPI client codegen implements them. The server is the authoritative
validator; if you want client-side checking, plug in a JSON Schema
validator library for your language.

### SOCKS proxies

`TransportOptions.builder().proxy()` accepts only `http://` and `https://` URLs.
Passing a `socks://`, `socks4://`, or `socks5://` scheme is rejected
at construction time with a clear error. SOCKS support would
require enabling extra dependencies / feature flags on the underlying
HTTP library in every one of the 12 SDKs we generate, with non-trivial
API divergence; we explicitly chose not to. If you need SOCKS, route
through a local HTTP-CONNECT bridge or configure it at the OS level.

### `LICENSE` file is not auto-emitted

The package manifest declares MIT, but no `LICENSE` / `LICENSE.md` file
is generated alongside the sources. Drop the appropriate license text
into the generated tree as part of your release pipeline before
publishing to a registry — most registries warn or block on a missing
file, and the GitHub license auto-detect cannot pick up a manifest-only
declaration.

## Development

### Install

```bash
pip install -e . --group dev
```

### Format

```bash
ruff format .
```

### Lint

```bash
ruff check .
```

### Type-check

```bash
mypy .
```

### Test

```bash
pytest
```
