# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add to your `Cargo.toml`:

```toml
[dependencies]
petstore = "1.0.0"
```

## Quick Start

```rust statements
use petstore::Client;

let host = std::env::var("PETSTORE_BASE_URL")?;
let token = std::env::var("PETSTORE_ACCESS_TOKEN")?;
let client = Client::with_token(Some(&host), Some(&token), None)?;
let result = client.store.get_defaults(None).await;
client.close();
let value = result?;
println!("{value:?}");
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```rust file
use petstore::auth::BearerAuthenticator;
use petstore::Client;
use petstore::ConfigurationError;

fn new_client() -> Result<Client, ConfigurationError> {
    let authenticator = BearerAuthenticator::new("https://api.example.com", "your-token")?;
    Ok(Client::new(Box::new(authenticator), None))
}
```

`with_token(host, access_token, transport_options)` takes `Option<&str>` values: an argument wins, then `PETSTORE_BASE_URL` and `PETSTORE_ACCESS_TOKEN` are read once at construction (an empty variable is unset), and a value that is neither passed nor set fails with `ConfigurationError::InvalidArgument`. `with_token_provider(host, provider, transport_options)` takes an `Arc<dyn Fn() -> String + Send + Sync>` that is called for every request.

### Credentials from the environment

The authenticator written for each security scheme takes every credential as an `Option<&str>`. The value you pass wins; with `None` the variable below is read once, when the authenticator is built (an empty variable is unset). A credential found in neither place fails `new` with `ConfigurationError::InvalidArgument` naming the variable, before the client exists. A Bearer scheme's authenticator also has `with_token_provider(host, provider)`, which reads no variable.

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

### Per-call options

Every operation takes `options: Option<&...Options>`, including one with no parameters and no security. Besides the operation's own optional parameters it carries `timeout(Duration)`, `max_retries(u32)` and `header(name, value)` for that one call; they replace the client's timeout and retry budget and win over its default headers.

### Errors with request context

`ApiError::request_method()` and `request_path()` (also on `ApiErrorFields`, and through `api_error()` on every status wrapper) name the request that failed, without its query string.

### Compression, logging and versioning

Responses are decoded from gzip, deflate, br and zstd. The crate does not log; with the `opentelemetry` feature the W3C trace context is injected. The minimum supported Rust version is 1.85.

### Basic Auth

```rust file
use petstore::auth::BasicAuthenticator;
use petstore::Client;
use petstore::ConfigurationError;

fn new_client() -> Result<Client, ConfigurationError> {
    let authenticator = BasicAuthenticator::new("https://api.example.com", "username", "password")?;
    Ok(Client::new(Box::new(authenticator), None))
}
```

### API Key

```rust file
use petstore::auth::{ApiKeyAuthenticator, ApiKeyLocation};
use petstore::Client;
use petstore::ConfigurationError;

fn new_client() -> Result<Client, ConfigurationError> {
    let authenticator = ApiKeyAuthenticator::new("https://api.example.com", "key-name", "key-value", ApiKeyLocation::Header)?;
    Ok(Client::new(Box::new(authenticator), None))
}
```

### OAuth2 Client Credentials

```rust file
use petstore::auth::oauth::OAuth2ClientCredentialsAuthenticator;
use petstore::Client;

fn new_client() -> Client {
    let authenticator = OAuth2ClientCredentialsAuthenticator::new(
        "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token", vec![]);
    Client::new(Box::new(authenticator), None)
}
```

### OAuth2 Authorization Code

```rust file
use petstore::auth::oauth::OAuth2AuthorizationCodeAuthenticator;
use petstore::Client;

fn new_client() -> Client {
    let authenticator = OAuth2AuthorizationCodeAuthenticator::new(
        "https://api.example.com", "client-id", "client-secret",
        "https://auth.example.com/authorize", "https://auth.example.com/token",
        "https://app.example.com/callback", vec![], "");
    Client::new(Box::new(authenticator), None)
}
```

### OAuth2 Password

```rust file
use petstore::auth::oauth::OAuth2PasswordAuthenticator;
use petstore::Client;

fn new_client() -> Client {
    let authenticator = OAuth2PasswordAuthenticator::new(
        "https://api.example.com", "client-id", "client-secret",
        "https://auth.example.com/token", "username", "password", vec![], "");
    Client::new(Box::new(authenticator), None)
}
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```rust file
use petstore::auth::oauth::OAuth2ImplicitAuthenticator;
use petstore::Client;
use petstore::ConfigurationError;

fn new_client() -> Result<Client, ConfigurationError> {
    let authenticator = OAuth2ImplicitAuthenticator::new(
        "https://api.example.com", "client-id", "https://auth.example.com/authorize", vec![]);
    authenticator.set_access_token("your-access-token")?;
    Ok(Client::new(Box::new(authenticator), None))
}
```

### OpenID Connect

```rust file
use petstore::auth::oauth::OpenIdConnectAuthenticator;
use petstore::Client;

fn new_client() -> Client {
    let authenticator = OpenIdConnectAuthenticator::new(
        "https://api.example.com", "https://auth.example.com/.well-known/openid-configuration",
        "client-id", "client-secret", "https://app.example.com/callback", vec![]);
    Client::new(Box::new(authenticator), None)
}
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators resolve the access token by making an HTTP call to the token endpoint, which happens before the request carrying it is sent.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Expired tokens

When the API answers an OAuth2 or OpenID Connect request with `401`, the authenticator drops the cached access token (`invalidate_access_token()`, the refresh token is kept) and the request is sent once more with fresh headers. A second `401` is reported as `UnauthorizedError`. A multipart body is not replayed; API key, basic and bearer authenticators never replay.

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry, or half the lifetime for a token that lives less than two minutes (a 60 second token is reused for 30 seconds). The margin is applied once. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. A token response without `expires_in` is cached until you invalidate it.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethod::Body` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod::Basic` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```rust file
use petstore::auth::oauth::{ClientAuthMethod, OAuth2ClientCredentialsAuthenticator};

fn new_authenticator() -> OAuth2ClientCredentialsAuthenticator {
    OAuth2ClientCredentialsAuthenticator::new(
        "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token", vec![])
        .with_client_auth_method(ClientAuthMethod::Basic)
}
```

### Hooks and telemetry

One optional hook runs around every call: `client.with_call_hook(Arc::new(hook))` (or `Configuration::builder().call_hook(hook)`). `CallHook::start(info, headers)` runs once per call after the headers are final and may edit them; it returns a `CallObserver` (or `None`) whose `finish(outcome)` runs exactly once with the status (`0` when no response arrived), the lower-cased headers (`None` when none arrived) and the error. A panic in `start` reaches the caller; a panic in `finish` is swallowed. `call_hook::chain(vec![a, b])` starts in order and finishes in reverse. `OpenTelemetryCallHook::new()` (feature `opentelemetry`) records one span, `http.client.request.count` and `http.client.request.duration` per call.

## Servers

If the OpenAPI spec defines multiple servers, the generated `petstore::servers` module exposes each as a `server_N()` function returning a `ServerConfiguration`, plus an `all_servers()` function returning a `Vec` of them. Pass the desired server's URL to the client:

```rust file
use std::collections::HashMap;

use petstore::servers::server_0;
use petstore::Client;
use petstore::ConfigurationError;

fn new_client() -> Result<Client, ConfigurationError> {
    let url = server_0().url(&HashMap::new())?;
    Client::with_token(Some(&url), Some("your-token"), None)
}
```

## Testing

The `Authenticator` trait is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```rust file
use std::collections::HashMap;
use std::future::Future;
use std::pin::Pin;

use petstore::auth::Authenticator;
use petstore::Client;

struct FakeAuthenticator;

impl Authenticator for FakeAuthenticator {
    fn host(&self) -> &str { "https://api.example.com" }

    fn auth_headers<'a>(
        &'a self,
    ) -> Pin<Box<dyn Future<Output = HashMap<String, String>> + Send + 'a>> {
        Box::pin(async {
            HashMap::from([("Authorization".to_string(), "Bearer test-token".to_string())])
        })
    }
}

fn new_test_client() -> Client {
    Client::new(Box::new(FakeAuthenticator), None)
}
```

## Error Handling

Every failure the SDK produces implements the `OpenAPIError` marker trait, so a `&dyn OpenAPIError` holds any of them. It has four direct kinds: `ApiError` (an HTTP response or a transport failure), `SerializationError` (a request or response body that cannot be encoded or decoded), `OAuth2ServerError` (the token endpoint answered with an error) and `OAuth2TokenError` (the token response was unusable). The last two surface from the first authenticated call, so they are siblings of `ApiError`, not subtypes of it. Caller mistakes (a bad argument, a closed client) are the separate `ConfigurationError`. The error hierarchy is:

- `OpenAPIError` (root marker trait)
  - `SerializationError`
  - `OAuth2ServerError`
  - `OAuth2TokenError`
  - `ApiError`
    - `ClientError` (4xx)
      - `BadRequestError` (400)
      - `UnauthorizedError` (401)
      - `ForbiddenError` (403)
      - `NotFoundError` (404)
      - `ConflictError` (409)
      - `UnprocessableEntityError` (422)
      - `TooManyRequestsError` (429)
      - `PaymentRequiredError` (402)
      - `MethodNotAllowedError` (405)
      - `RequestTimeoutError` (408)
      - `GoneError` (410)
      - `PayloadTooLargeError` (413)
      - `UnsupportedMediaTypeError` (415)
      - `LockedError` (423)
    - `ServerError` (5xx)
      - `InternalServerError` (500)
      - `NotImplementedError` (501)
      - `BadGatewayError` (502)
      - `ServiceUnavailableError` (503)
      - `GatewayTimeoutError` (504)
    - `NetworkError` (no HTTP response, status 0)
      - `NetworkTimeoutError` (the request timed out, status 0)

```rust file
use std::error::Error;

use petstore::api::options::AddPetOptions;
use petstore::models::Pet;
use petstore::Client;
use petstore::{ClientError, NotFoundError, ServerError};

/// Whether `err` is a `T`. Rust has no inheritance: each error's `source()` is
/// its parent in the hierarchy above, so the check walks that chain.
fn is_a<T: Error + 'static>(err: &(dyn Error + 'static)) -> bool {
    std::iter::successors(Some(err), |&e| e.source()).any(|e| e.is::<T>())
}

async fn add_pet_or_report(
    client: &Client,
    pet: Pet,
    options: Option<&AddPetOptions>,
) {
    match client.pet.add_pet(pet, options).await {
        Ok(value) => println!("Found: {value:?}"),
        Err(e) if is_a::<NotFoundError>(&*e) => println!("Not found: {e}"),
        Err(e) if is_a::<ClientError>(&*e) => println!("Client error: {e}"),
        Err(e) if is_a::<ServerError>(&*e) => println!("Server error: {e}"),
        Err(e) => println!("Error: {e}"),
    }
}
```

## Configuration

### Custom Transport Options

```rust file
use petstore::auth::Authenticator;
use petstore::Client;
use petstore::{ConfigurationError, TransportOptions};

fn new_client(authenticator: Box<dyn Authenticator>) -> Result<Client, ConfigurationError> {
    let transport = TransportOptions::builder()
        .proxy("http://proxy:3128")
        .timeout(Some(5000))
        .build()?;

    Ok(Client::new(authenticator, Some(transport)))
}
```

### Retries

A call is retried up to `max_retries` times (default 2; `.max_retries(0)` disables it) after a network error before a response, and after a status of 408, 409, 429 or 500-599 except 501. The wait is 0.5 s doubling to 8 s with 25% jitter, or the `Retry-After` header (at most 60 s). Each retry is a fresh request with its authentication headers rebuilt, all inside the one `timeout`; a multipart body is never retried.

```rust file
use petstore::{ConfigurationError, TransportOptions};

fn new_transport() -> Result<TransportOptions, ConfigurationError> {
    TransportOptions::builder().max_retries(0).build()
}
```

### Streaming

An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns a `ByteStream` instead of the whole body in memory, so a large download never has to fit in memory. The call returns as soon as the response headers arrive, and the body is read as you consume it. A `ByteStream` has the status code, the lower-cased headers and an idempotent `close()` that aborts the connection instead of draining it, so close it when you stop early (dropping it does the same). `read_all()` reads the rest into memory and leaves the stream closed.

```rust file
use futures_util::StreamExt as _;

use petstore::Client;

async fn download(
    client: &Client,
    pet_id: i64,
    document_id: i64,
) -> Result<u64, Box<dyn std::error::Error + Send + Sync>> {
    let mut stream = client.pet.download_pet_document(pet_id, document_id, None).await?;
    println!("{}", stream.status_code());
    let mut size = 0;
    while let Some(chunk) = stream.next().await {
        size += chunk?.len() as u64;
    }
    Ok(size)
}
```

The call `timeout` covers the wait for the response headers and stops there. `idle_timeout` (in `TransportOptions`, default 60 seconds, `Some(0)` or `None` disables it) bounds the gap between two reads of the body and raises `NetworkTimeoutError`. Retries, redirects, token refresh and the error contract apply up to the headers exactly as for a buffered call; a request is never retried after the first body byte, and a non-2xx status raises the usual typed error with its body read up to 64 KiB and never returns a stream. A connection reset, or a body that ends before its declared `Content-Length`, raises `StreamError`, a `NetworkError` with status 0. `client.close()` cancels the streams that are still open, and their next read raises the closed-client error. A custom transport that does not implement `send_streaming` fails a stream call with the `Unsupported` I/O error naming it, and its buffered calls keep working.

An operation whose success response is JSON Lines (`application/x-ndjson`, `application/jsonl`, `application/jsonlines`) or server-sent events (`text/event-stream`) has no buffered method. It has a `<operation>_stream` method that takes the operation's parameters, its options object and a trailing `StreamOptions`, and returns a `JsonLinesStream<T>` of the schema type or an `SseStream<T>` of `ServerSentEvent<T>` (`id`, `event`, `data`, `retry` and the raw `data` text; `T` is the schema of the `data`, or a string). Iterate it, and close it when you stop early. A JSON Lines line that does not decode, and a line or event longer than `max_event_bytes` (1 MiB), raise `SerializationError` and end the stream.

```rust file
use futures_util::StreamExt as _;

use petstore::Client;

async fn read_json_lines(
    client: &Client,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let mut feed = client.store.export_orders_stream(None, None).await?;
    while let Some(item) = feed.next().await {
        println!("{:?}", item?);
    }
    Ok(())
}
```

```rust file
use futures_util::StreamExt as _;

use petstore::Client;

async fn read_events(
    client: &Client,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let mut feed = client.store.watch_notices_stream(None, None).await?;
    while let Some(item) = feed.next().await {
        println!("{:?}", item?.data);
    }
    Ok(())
}
```

An event stream reconnects when the connection ends, fails or stays silent for the idle timeout: it asks again with fresh headers and `Last-Event-ID`, waits the server's `retry` value or `reconnect_delay` (1 second, doubling to 30 seconds with jitter), and raises the last failure after `max_reconnects` failures in a row without an event (5; 0 for a request with a body; `-1` is unlimited). It never reconnects after a 204, a status other than 408, 429 and 5xx, a response that is not `text/event-stream`, a cancellation or `client.close()`. An event named `error` raises `ApiError` with the response status and the data as its body, and a comment line such as a heartbeat counts as activity for the idle timeout. The generator option `sseTerminator` names a data value (such as `[DONE]`) that ends the stream and is not delivered. `StreamOptions` also takes `idle_timeout` and `last_event_id`.

### Custom HTTP client

```rust file
use std::error::Error;

use petstore::auth::Authenticator;
use petstore::Client;

fn new_client(authenticator: Box<dyn Authenticator>) -> Result<Client, Box<dyn Error + Send + Sync>> {
    let http = reqwest::Client::builder()
        .redirect(reqwest::redirect::Policy::none())
        .build()?;
    Ok(Client::with_http_client(authenticator, http, None)?)
}
```

The SDK sends every request through your `reqwest::Client` and never shuts it down. Combining it with `proxy`, `ca_cert_path` or `verify_ssl(false)` fails with `ConfigurationError::InvalidArgument`.

### Cancellation

The SDK adds no cancellation parameter: drop the future to cancel a call, or wrap it in `tokio::time::timeout` or `tokio::select!`. `TransportOptions::timeout` bounds a call and fails with `NetworkTimeoutError`; cancellation is never reported as one.

### Raw responses, timeouts and the request id

Each operation has a `..._with_http_info` variant returning `ApiResult<T>` (`status_code()`, `data()`, `raw_body()`, `headers()`). `timeout` is one end-to-end budget in milliseconds (default 10000). `inject_request_id(true)` adds an `X-Request-ID` header to each request unless you set one.

## API Methods

Each API group is exposed as a typed field on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are asynchronous; call them with `.await`.

## Models

Models are generated as Rust structs in the `models` module.

```rust file
use petstore::models::Account;

fn copy_of(model: &Account) -> Account {
    model.clone()
}
```

## Binary / File Uploads

File upload parameters are typed as `Vec<u8>`. A binary response body is returned as a `ByteStream`, which is a `futures_core::Stream` of `Bytes` (see Streaming).

## Comment Style

Never place a comment on the same line as code. Use block comments (`/* ... */`); doc comments (`///`) are fine.

```good
/* This explains the logic */
let x = 1;
```

```bad
// This explains the logic
let x = 1;
```
