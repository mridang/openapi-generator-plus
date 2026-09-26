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

let client = Client::with_token("https://api.example.com", "your-token", None)?;
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

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed; tune your authorization server's `expires_in` if it is too tight.

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

## Servers

If the OpenAPI spec defines multiple servers, the generated `petstore::servers` module exposes each as a `server_N()` function returning a `ServerConfiguration`. Pass the desired server's URL to the client:

```rust
use std::collections::HashMap;

use petstore::servers::server_0;
use petstore::Client;

let client = Client::with_token(&server_0().url(&HashMap::new())?, "your-token", None)?;
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

All API errors derive from `ApiError`. The error hierarchy is:

- `ApiError` (base)
  - `ClientError` (4xx)
    - `BadRequestError` (400)
    - `UnauthorizedError` (401)
    - `ForbiddenError` (403)
    - `NotFoundError` (404)
    - `ConflictError` (409)
    - `UnprocessableEntityError` (422)
  - `ServerError` (5xx)
    - `InternalServerError` (500)
  - `NetworkError` (no HTTP response, status 0)
    - `NetworkTimeoutError` (the request timed out, status 0)

```rust
use std::error::Error;

use petstore::{ClientError, NetworkTimeoutError, NotFoundError, ServerError};

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
    Err(e) if is_a::<NetworkTimeoutError>(&*e) => println!("Timed out: {}", e),
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

## API Methods

Each API group is exposed as a typed field on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are asynchronous; call them with `.await`.

## Models

Models are generated as Rust structs in the `models` module.

```rust
use petstore::models::ApiResponse;

let model = ApiResponse {
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
