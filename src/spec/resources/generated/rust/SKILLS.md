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
