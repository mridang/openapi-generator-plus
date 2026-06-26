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

let client = Client::with_token("https://api.example.com", "your-token", None);
```

## Authentication

All authentication is handled via `Authenticator` trait implementations passed to the client constructor.

### Bearer Token

```rust
use petstore::auth::bearer_authenticator::BearerAuthenticator;
use petstore::Client;

let authenticator = BearerAuthenticator::new("https://api.example.com", "your-token");
let client = Client::new(Box::new(authenticator), None);
```

### Basic Auth

```rust
use petstore::auth::basic_authenticator::BasicAuthenticator;

let authenticator = BasicAuthenticator::new("https://api.example.com", "username", "password");
let client = Client::new(Box::new(authenticator), None);
```

### API Key

```rust
use petstore::auth::api_key_authenticator::ApiKeyAuthenticator;
use petstore::auth::api_key_location::ApiKeyLocation;

let authenticator = ApiKeyAuthenticator::new("https://api.example.com", "key-name", "key-value", ApiKeyLocation::Header);
let client = Client::new(Box::new(authenticator), None);
```

### OAuth2 Client Credentials

```rust
use petstore::auth::oauth::oauth2_client_credentials_authenticator::OAuth2ClientCredentialsAuthenticator;

let authenticator = OAuth2ClientCredentialsAuthenticator::new(
    "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token");
let client = Client::new(Box::new(authenticator), None);
```

### OAuth2 Authorization Code

```rust
use petstore::auth::oauth::oauth2_auth_code_authenticator::OAuth2AuthCodeAuthenticator;

let authenticator = OAuth2AuthCodeAuthenticator::new(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", "authorization-code", "https://app.example.com/callback");
let client = Client::new(Box::new(authenticator), None);
```

### OAuth2 Password

```rust
use petstore::auth::oauth::oauth2_password_authenticator::OAuth2PasswordAuthenticator;

let authenticator = OAuth2PasswordAuthenticator::new(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", "username", "password");
let client = Client::new(Box::new(authenticator), None);
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```rust
use petstore::auth::oauth::oauth2_implicit_authenticator::OAuth2ImplicitAuthenticator;

let authenticator = OAuth2ImplicitAuthenticator::new("https://api.example.com", "your-access-token");
let client = Client::new(Box::new(authenticator), None);
```

### OpenID Connect

```rust
use petstore::auth::oauth::openid_connect_authenticator::OpenIdConnectAuthenticator;

let authenticator = OpenIdConnectAuthenticator::new(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/.well-known/openid-configuration");
let client = Client::new(Box::new(authenticator), None);
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators implement `async fn get_auth_headers(&self, request: &RequestContext) -> Result<HashMap<String, String>, AuthError>` because resolving the access token requires an HTTP call to the token endpoint. The generated API methods always `.await` this call before sending the request. A synchronous helper is preserved for back-compat but the OAuth flows require the async path.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed; tune your authorization server's `expires_in` if it is too tight.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethod::Body` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod::Basic` sends them as an HTTP Basic `Authorization` header.

Override the default with the `with_client_auth_method` builder:

```rust
use petstore::auth::oauth::client_auth_method::ClientAuthMethod;
use petstore::auth::oauth::oauth2_client_credentials_authenticator::OAuth2ClientCredentialsAuthenticator;

let authenticator = OAuth2ClientCredentialsAuthenticator::new(
    "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token")
    .with_client_auth_method(ClientAuthMethod::Basic);
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `petstore::servers` module exposes each as a `server_N()` function returning a `ServerConfiguration`. Resolve the URL and pass it to the client:

```rust
use petstore::servers::server_0;

let client = Client::with_token(&server_0().url(), "your-token", None);
```

## Testing

The `Authenticator` trait is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```rust
use async_trait::async_trait;
use std::collections::HashMap;
use petstore::auth::authenticator::{Authenticator, AuthError, RequestContext};

struct FakeAuthenticator;

#[async_trait]
impl Authenticator for FakeAuthenticator {
    async fn get_auth_headers(&self, _req: &RequestContext) -> Result<HashMap<String, String>, AuthError> {
        Ok(HashMap::from([("Authorization".to_string(), "Bearer test-token".to_string())]))
    }
    fn host(&self) -> &str { "https://api.example.com" }
}

let client = Client::new(Box::new(FakeAuthenticator), None);
```

## Error Handling

All API errors are represented by the `ApiError` enum. The error hierarchy is:

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

```rust
use petstore::errors::*;

match client.pet_api.get_pet_by_id(pet_id).await {
    Ok(pet) => println!("Found: {:?}", pet),
    Err(ApiError::NotFound(e)) => println!("Not found: {}", e),
    Err(ApiError::Client(e)) => println!("Client error {}: {}", e.status_code(), e),
    Err(ApiError::Server(e)) => println!("Server error: {}", e),
    Err(e) => println!("Error: {}", e),
}
```

## Configuration

### Custom Transport Options

```rust
use petstore::transport_options::TransportOptionsBuilder;
use std::time::Duration;

let transport = TransportOptionsBuilder::new()
    .proxy("http://proxy:3128")
    .timeout(Duration::from_secs(5))
    .build();

let client = Client::new(Box::new(authenticator), Some(transport));
```

## API Methods

Each API group is exposed as a typed field on the client struct (e.g., `client.pet_api`). API structs have async methods that correspond to OpenAPI operations, accepting typed request parameters and returning `Result<T, ApiError>`.

All API methods are async and should be called with `.await`.

## Models

Models are generated as Rust structs with `serde::Serialize` and `serde::Deserialize` derives in the `models` module.

```rust
use petstore::models::Pet;

let pet = Pet {
    name: "Fido".to_string(),
    status: Some("available".to_string()),
    ..Default::default()
};
```

## Binary / File Uploads

File upload parameters accept `Vec<u8>`. Binary response bodies are returned as `Vec<u8>`.

## Comment Style

Never use inline comments (`//`). Always use block comments (`/* ... */`). Doc comments (`///`) are fine.

```good
/* This explains the logic */
let x = 1;
```

```bad
// This explains the logic
let x = 1;
```
