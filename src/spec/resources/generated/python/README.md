# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated Python SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- Python 3.13+
- `pip`

Install runtime and development dependencies:

```bash
pip install -e . --group dev
```

## Format

```bash
ruff format .
```

## Lint

```bash
ruff check .
```

## Type-check

```bash
mypy .
```

## Install

```bash
pip install -e .
```

## Test

```bash
pytest
```

## Package

- Name: `petstore-client`
- Version: `1.0.0`

## Configuration

### Automatic retries

A request that fails with a transient error is retried by the SDK. `TransportOptions.builder().max_retries(n)` sets how many times (default 2, so up to three attempts); `max_retries(0)` disables retrying.

```python
transport = TransportOptions.builder().max_retries(4).build()
```

A request is retried after a network error that happened before a response arrived, and after a 408, 409, 429 or 5xx response (not 501). The wait starts at 0.5 s, doubles up to 8 s and is shortened by up to 25% at random; a `Retry-After` header (seconds or an HTTP date, at most 60 s) replaces it. Retries never extend the request timeout: when the next wait would outlast what is left of it, the last failure is reported, and a timeout itself is not retried. A stream or multipart body is never retried, and every retry is a new request with freshly built authentication headers.

### OAuth2 and OpenID Connect

When the API answers 401 to a call authenticated through an OAuth2 or OpenID Connect authenticator, the SDK drops the cached access token (the refresh token is kept), obtains a new one and sends the call once more; a second 401 raises `UnauthorizedException`. A stream or multipart body is not sent again. `invalidate_access_token()` on the authenticator does the same by hand.

The authorization-code flow uses PKCE (`S256`): `build_authorization_url()` creates the code verifier, keeps it in the authenticator and puts the `code_challenge` on the URL, and `exchange_code()` sends the `code_verifier`. Build the URL and exchange the code on the same authenticator.

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
pydantic does so natively (`Field(...)` constraints): constructing a model that
violates one raises `pydantic.ValidationError`, and decoding a response that
violates one raises `SerializationException`.
Request parameters outside a model are not checked client-side. `uniqueItems`
arrays are sent in the collection's natural order (a Python `set` in iteration
order), never sorted.

### Your own HTTP transport

Pass a `urllib3.PoolManager` you built to the client
(`Client(authenticator, pool_manager=pool)`; `with_token` and
`with_authenticator` take it too). The SDK defaults (timeout, redirect policy,
user agent, request id, default headers) are applied on top of it, and the SDK
never closes it: you own its lifecycle. Combining it with a proxy, a CA bundle
or `verify_ssl(False)` in `TransportOptions` raises `ValueError`, because those
settings belong to the pool manager you built.

### Per-call cancellation

Cancelling the asyncio task that awaits an operation aborts the request
itself: its socket is shut down and the connection dropped, instead of the
call running on in a worker thread. `Client.close()` (also `with Client(...)`)
cancels every request still in flight; those calls raise `RuntimeError`. To
bound how long a call can take, set the request timeout on `TransportOptions`;
a call that exceeds it fails with `NetworkTimeoutException`.

## Not supported

### Webhooks and callbacks

This SDK is **client → server** only. Spec entries describing
server-initiated calls — OAS 3.1 top-level `webhooks` and OAS 3.0
per-operation `callbacks` — are intentionally skipped during code
generation. If you need to receive webhook deliveries, write the
handler yourself and use this SDK only to deserialize the incoming
payload (e.g. by reusing the relevant request-body model).

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
