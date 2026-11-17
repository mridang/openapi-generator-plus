# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated Rust SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- Rust **1.85+** (MSRV; Rust edition 2021)
- `cargo` (ships with Rust)

### Tooling

| Tool | Component | Invoke |
| --- | --- | --- |
| Formatter | `rustfmt` (`rustup component add rustfmt`) | `cargo fmt` (write) / `cargo fmt -- --check` (verify) |
| Linter and static analyser | `clippy` (`rustup component add clippy`) | `cargo clippy --all-targets --all-features -- -D warnings` |
| Dependency audit | `cargo-deny` (`cargo install --locked cargo-deny`) | `cargo deny check` |
| Edition migration | built in | `cargo fix --edition` |

## Installation

Add the dependency to your `Cargo.toml`:

```toml
[dependencies]
petstore = "1.0.0"
```

## Usage

```rust
use petstore::Client;

let client = Client::with_token(Some("https://api.example.com"), Some("your-token"), None)?;
```

Every operation is an `async` method on a typed API group, for example
`client.pet`; call it with `.await`. Errors are
`Box<dyn std::error::Error + Send + Sync>` values whose root is an SDK error:
the `OpenAPIErrorExt` trait gives `as_api_error()`, `is_api_error()` and
`api_error_kind()`. `NetworkError` and `NetworkTimeoutError` mean no HTTP
response arrived, and `TooManyRequestsError` is HTTP 429. See
[SKILLS.md](SKILLS.md) for authentication, servers and error handling.

### Raw responses

Every operation has a `..._with_http_info` variant returning an `ApiResult<T>`
with `status_code()`, `data()`, `raw_body()` and the lower-cased `headers()`.
It is the way to read a response header.

### Timeouts

`TransportOptions::builder().timeout(Some(5000))` sets one end-to-end budget in
milliseconds for the whole call, redirects and retries included. A call that
exceeds it fails with `NetworkTimeoutError`. The default is 10000 ms.

### Automatic retries

A failed call is sent again up to `max_retries` times, two by default;
`TransportOptions::builder().max_retries(0)` turns retries off. The call is
retried after a network error that happened before a response, and after a
response with status 408, 409, 429 or 500-599 (not 501). The wait starts at
0.5 s, doubles up to 8 s, and carries up to 25% jitter. A `Retry-After` header
(seconds or an HTTP date) replaces the wait, up to 60 s. Every retry is a fresh
request with its authentication headers applied again, and all of them share
the one end-to-end `timeout`: a retry that would not fit in what is left is not
made, and the last failure is returned. A call with a multipart body is never
retried, and dropping the future cancels the wait between attempts. Retries
apply to the generated operations; a request sent straight through
`DefaultApiClient` is not retried.

### Expired tokens

When an OAuth2 or OpenID Connect call is answered with 401, the cached access
token is dropped (the refresh token is kept) and the request is sent once more
with fresh headers. A second 401 is reported as `UnauthorizedError`. A multipart
body is not replayed, and API key, basic and bearer authenticators never replay.

### Request id

`TransportOptions::builder().inject_request_id(true)` adds a unique
`X-Request-ID` header to every request that does not already carry one (header
names are matched case-insensitively). Quote it when you report a problem with
a call.

### Cancellation

The SDK adds no cancellation parameter and no client signal: a call is cancelled
the way every async Rust call is, by dropping its future, or by wrapping it in
`tokio::time::timeout` or `tokio::select!`. To bound how long a call can take
without that, set the request timeout on `TransportOptions`; a call that
exceeds it fails with `NetworkTimeoutError`. Cancellation is never reported as
`NetworkTimeoutError`.

### Custom HTTP client

The client's `with_http_client(authenticator, client, transport_options)` constructor sends
every request through a `reqwest::Client` you built yourself (a custom
connector, a shared pool, a test double). The SDK defaults (timeout,
`User-Agent`, request ids, default headers, redirect handling) still apply on
top. Build the client with `reqwest::redirect::Policy::none()` so the SDK's own
redirect handling stays in charge. You own the client's lifecycle: the SDK never
shuts it down. Setting a proxy, a CA certificate or `verify_ssl(false)` on
`TransportOptions` together with a supplied client fails with
`ConfigurationError::InvalidArgument`, because those belong to the client you
built.

### Credentials

`Client::with_token(host, access_token, transport_options)` takes both
values as `Option<&str>`. The value you pass wins; when you pass `None` the
client reads the environment variable once, while it is built:
`PETSTORE_BASE_URL` for the host and `PETSTORE_ACCESS_TOKEN` for the token. An
empty variable counts as unset. A value that is neither passed nor set fails
the constructor with `ConfigurationError::InvalidArgument`, so a missing
credential never surfaces on the first request.

To renew a token without rebuilding the client, pass a callback instead:
`Client::with_token_provider(host, provider, transport_options)` takes an
`Arc<dyn Fn() -> String + Send + Sync>` (`TokenProvider`) that is called for
every request, and `PETSTORE_ACCESS_TOKEN` is not read.

The authenticator written for each security scheme in the API does the same for
its own credentials. Every credential is an `Option<&str>` argument: the value
you pass wins, even an empty one (the underlying authenticator then rejects it);
when you pass `None` the variable below is read once, while the authenticator
is built, and an empty variable counts as unset. A credential that is neither
passed nor set fails `new` with `ConfigurationError::InvalidArgument` naming the
variable, so the failure comes before the client exists. A Bearer scheme's
authenticator also has `with_token_provider(host, provider)`, which takes the
token from a callback instead and reads no variable.

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

### Per-call options

Every operation takes an `options` argument (`Option<&...Options>`), including an
operation with no parameters and no security. Besides the operation's own
optional parameters it carries three settings for that one call: `timeout(Duration)` replaces the client's end-to-end timeout (zero is
refused with `ConfigurationError::InvalidTimeout`), `max_retries(u32)` replaces
its retry budget (`0` turns retries off) and `header(name, value)` adds a header
that wins over the client's default headers and the operation's header
parameters. Authentication headers are applied after it.

### Errors

`ApiError` carries `request_method()` and `request_path()`, and so does the
`ApiErrorFields` of `ApiErrorKind`; the status wrappers (`NotFoundError`,
`ServerError`, `NetworkError`, ...) reach them through `api_error()`. The path
is the operation path without scheme, host, query string or fragment, so a
credential in a query string never reaches an error. The message names them:
`API returned status code 404 for GET /items/7`.

### Operations with several success responses

When an operation declares several 2xx responses with different schemas, it
returns the generated union (an enum with one variant per schema) and the body
is decoded as the variant the actual status code declares, so a `201` is never
read as the schema of the `200`. A 2xx status the operation does not declare is
decoded by the union itself.

### Models

A model is a plain value: bind it without `mut` and it cannot change. Every
property has a consuming `with_<property>(value)` method that returns the
model with that one field replaced, so `model.clone().with_name("x".to_string())`
is a changed copy and the original stays as it was. An optional property takes
the inner type and stores `Some(value)`.

### Behaviours to know

- **Optional versus nullable.** An optional property is an `Option<T>` whether or
  not its schema also allows `null`: an absent key and an explicit `null` both
  decode to `None`, and `None` is left out when the model is written. Only a
  required nullable property keeps the difference: its key must be present when
  decoding and is written as `null` when the value is `None`.
- **Unknown values fail loudly.** A string that is not one of an enum's values,
  and a discriminator value that is not in the mapping, make decoding fail with a
  `SerializationError` that names the value; they are never replaced by a
  default.
- **Repeated response headers.** `ApiResult::headers()` is a map with one entry
  per lower-cased name, and a header the server sent more than once is joined
  into that entry with `, ` in wire order (RFC 9110 section 5.3).
- **No streaming.** A response body is read into memory, and a request body is a
  value, not a stream. To stream, send the request through your own
  `reqwest::Client` (see Custom HTTP client) or through the injected transport.
- **`Configuration` and `TransportOptions` are separate on purpose.**
  `Configuration` describes the API the operations talk to (the base URL and the
  headers an API group adds to its calls); `TransportOptions` configures the HTTP
  transport underneath (timeout, retries, proxy, TLS, redirects, request ids).
  The client builds the first from the authenticator and takes the second as an
  argument.

### Compression

Requests send `Accept-Encoding: gzip, deflate, br, zstd` unless you set that
header, and the response body is decoded from `gzip`, `deflate`, `br` or `zstd`
before it reaches you; `Content-Encoding: identity` passes through.

### Logging and tracing

The crate does not log. With the `opentelemetry` feature each request carries the
W3C `traceparent` of the current span; otherwise inject your own through
`TransportOptions` default headers, or wrap the calls in your own spans.

### Versioning and supported Rust

The crate version is `1.0.0`. The minimum supported Rust version is
1.85, and the crate builds with the 2021 edition.

## Build

```bash
cargo build
```

## Test

```bash
cargo test
```

## Format

```bash
cargo fmt
```

## Lint / static analysis

```bash
cargo fmt -- --check
cargo clippy --all-targets --all-features -- -D warnings
```

## Dependency audit

```bash
cargo deny check
```

## Crate

- Name: `petstore`
- Version: `1.0.0`
- Edition: `2021`
- MSRV: `1.85`
- License: MIT

## Caveats

### Decimal / `format: number` precision

`serde_json` parses JSON numbers into `f64` by default, and this SDK
maps `format: number` / `format: decimal` schema fields to `f64`.
Decimal values are therefore stored as IEEE-754 binary floating point,
so monetary amounts lose exact decimal representation: `0.1 + 0.2` in
Rust is `0.30000000000000004`.

Do not do arithmetic on prices, balances, or other money-typed fields.
If you need exact decimal arithmetic, deserialize the raw response body
yourself (e.g. with `serde_json::value::RawValue`) and use a decimal
crate such as `rust_decimal` or `bigdecimal`.

`format: int64` is unaffected — Rust's `i64` natively represents the
full 64-bit range without precision loss.

## Not supported

### Webhooks and callbacks

This SDK is **client → server** only. Spec entries describing
server-initiated calls — OAS 3.1 top-level `webhooks` and OAS 3.0
per-operation `callbacks` — are intentionally skipped during code
generation. If you need to receive webhook deliveries, write the
handler yourself and use this SDK only to deserialize the incoming
payload (e.g. by reusing the relevant request-body model).

### XML bodies and `xml.nodeType`

Request and response bodies are JSON, form-urlencoded, multipart, text or
binary. An operation that declares only `application/xml` has no typed body,
and the `xml` object (`name`, `attribute`, `wrapped`, `nodeType`) is ignored.
Encode and decode XML yourself and pass the bytes through a binary or text body.

### Link Objects

OpenAPI `links` between operations are not generated: there is no helper that
turns a response value into the next call. Make the follow-up call yourself.

### Mutual TLS security schemes

A `mutualTLS` security scheme (OAS 3.1) produces no authenticator. To present a
client certificate, build the `reqwest::Client` yourself (with an identity) and
pass it to `with_http_client`.

### Conditional-required validation (`dependentRequired` / `dependentSchemas`)

JSON Schema 2019-09 keywords for "if field X is present, field Y is
also required" are **not enforced** by this SDK. No mainstream
OpenAPI client codegen implements them. The server is the authoritative
validator; if you want client-side checking, plug in a JSON Schema
validator library for your language.

### Schema constraints

The constraints `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`,
`multipleOf`, `minItems` and `maxItems` are validated by the server. The
generated models do not enforce them: the Rust model layer is plain `serde`
structs, which check types and required keys but have no native support for
these keywords, so a value outside a constraint is sent as given and the
server rejects it. If you want fast-fail validation before the network round
trip, plug in a JSON Schema validator crate.

`uniqueItems` arrays are modelled as `HashSet`, which has no collection order
of its own. The SDK does not promise any particular order for them on the
wire; when every member is a number, string or boolean it writes them in
ascending order only so that repeated runs produce the same bytes.

### SOCKS proxies

`TransportOptionsBuilder::proxy()` accepts only `http://` and `https://` URLs.
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
