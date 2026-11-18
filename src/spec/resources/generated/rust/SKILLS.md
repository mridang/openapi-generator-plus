# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add to your `Cargo.toml`:

```toml
[dependencies]
petstore = "1.0.0"
```

## Quick Start

```rust
use petstore::Client;

let client = Client::with_token(Some("https://api.example.com"), Some("your-token"), None)?;
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```rust
use petstore::auth::BearerAuthenticator;
use petstore::Client;

let authenticator = BearerAuthenticator::new("https://api.example.com", "your-token")?;
let client = Client::new(Box::new(authenticator), None);
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

```rust
use petstore::auth::BasicAuthenticator;
use petstore::Client;

let authenticator = BasicAuthenticator::new("https://api.example.com", "username", "password")?;
let client = Client::new(Box::new(authenticator), None);
```

### API Key

```rust
use petstore::auth::{ApiKeyAuthenticator, ApiKeyLocation};
use petstore::Client;

let authenticator = ApiKeyAuthenticator::new("https://api.example.com", "key-name", "key-value", ApiKeyLocation::Header)?;
let client = Client::new(Box::new(authenticator), None);
```

### OAuth2 Client Credentials

```rust
use petstore::auth::oauth::OAuth2ClientCredentialsAuthenticator;
use petstore::Client;

let authenticator = OAuth2ClientCredentialsAuthenticator::new(
    "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token", vec![]);
let client = Client::new(Box::new(authenticator), None);
```

### OAuth2 Authorization Code

```rust
use petstore::auth::oauth::OAuth2AuthorizationCodeAuthenticator;
use petstore::Client;

let authenticator = OAuth2AuthorizationCodeAuthenticator::new(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/authorize", "https://auth.example.com/token",
    "https://app.example.com/callback", vec![], "");
let client = Client::new(Box::new(authenticator), None);
```

### OAuth2 Password

```rust
use petstore::auth::oauth::OAuth2PasswordAuthenticator;
use petstore::Client;

let authenticator = OAuth2PasswordAuthenticator::new(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", "username", "password", vec![], "");
let client = Client::new(Box::new(authenticator), None);
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```rust
use petstore::auth::oauth::OAuth2ImplicitAuthenticator;
use petstore::Client;

let authenticator = OAuth2ImplicitAuthenticator::new(
    "https://api.example.com", "client-id", "https://auth.example.com/authorize", vec![]);
authenticator.set_access_token("your-access-token")?;
let client = Client::new(Box::new(authenticator), None);
```

### OpenID Connect

```rust
use petstore::auth::oauth::OpenIdConnectAuthenticator;
use petstore::Client;

let authenticator = OpenIdConnectAuthenticator::new(
    "https://api.example.com", "https://auth.example.com/.well-known/openid-configuration",
    "client-id", "client-secret", "https://app.example.com/callback", vec![]);
let client = Client::new(Box::new(authenticator), None);
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

```rust
use petstore::auth::oauth::{ClientAuthMethod, OAuth2ClientCredentialsAuthenticator};

let authenticator = OAuth2ClientCredentialsAuthenticator::new(
    "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token", vec![])
    .with_client_auth_method(ClientAuthMethod::Basic);
```

### Hooks and telemetry

One optional hook runs around every call: `client.with_call_hook(Arc::new(hook))` (or `Configuration::builder().call_hook(hook)`). `CallHook::start(info, headers)` runs once per call after the headers are final and may edit them; it returns a `CallObserver` (or `None`) whose `finish(outcome)` runs exactly once with the status (`0` when no response arrived), the lower-cased headers (`None` when none arrived) and the error. A panic in `start` reaches the caller; a panic in `finish` is swallowed. `call_hook::chain(vec![a, b])` starts in order and finishes in reverse. `OpenTelemetryCallHook::new()` (feature `opentelemetry`) records one span, `http.client.request.count` and `http.client.request.duration` per call.

## Servers

If the OpenAPI spec defines multiple servers, the generated `petstore::servers` module exposes each as a `server_N()` function returning a `ServerConfiguration`, plus an `all_servers()` function returning a `Vec` of them. Pass the desired server's URL to the client:

```rust
use std::collections::HashMap;

use petstore::servers::server_0;
use petstore::Client;

let client = Client::with_token(Some(&server_0().url(&HashMap::new())?), Some("your-token"), None)?;
```

## Testing

The `Authenticator` trait is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```rust
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

let client = Client::new(Box::new(FakeAuthenticator), None);
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

```rust
use std::error::Error;

use petstore::{ClientError, NotFoundError, ServerError};

/// Whether `err` is a `T`. Rust has no inheritance: each error's `source()` is
/// its parent in the hierarchy above, so the check walks that chain.
fn is_a<T: Error + 'static>(err: &(dyn Error + 'static)) -> bool {
    std::iter::successors(Some(err), |&e| e.source()).any(|e| e.is::<T>())
}

match client.pet.add_pet(pet, None).await {
    Ok(value) => println!("Found: {:?}", value),
    Err(e) if is_a::<NotFoundError>(&*e) => println!("Not found: {}", e),
    Err(e) if is_a::<ClientError>(&*e) => println!("Client error: {}", e),
    Err(e) if is_a::<ServerError>(&*e) => println!("Server error: {}", e),
    Err(e) => println!("Error: {}", e),
}
```

## Configuration

### Custom Transport Options

```rust
use petstore::TransportOptions;
use petstore::Client;

let transport = TransportOptions::builder()
    .proxy("http://proxy:3128")
    .timeout(Some(5000))
    .build()?;

let client = Client::new(Box::new(authenticator), Some(transport));
```

### Retries

A call is retried up to `max_retries` times (default 2; `.max_retries(0)` disables it) after a network error before a response, and after a status of 408, 409, 429 or 500-599 except 501. The wait is 0.5 s doubling to 8 s with 25% jitter, or the `Retry-After` header (at most 60 s). Each retry is a fresh request with its authentication headers rebuilt, all inside the one `timeout`; a multipart body is never retried.

```rust
let transport = TransportOptions::builder().max_retries(0).build()?;
```

### Custom HTTP client

```rust
let http = reqwest::Client::builder()
    .redirect(reqwest::redirect::Policy::none())
    .build()?;
let client = Client::with_http_client(Box::new(authenticator), http, None)?;
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

```rust
use petstore::models::Account;

let model = Account {
    ..Default::default()
};
```

## Binary / File Uploads

File upload parameters are typed as `Vec<u8>`. Binary response bodies are returned as `Vec<u8>`.

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
