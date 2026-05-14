# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add to your `Package.swift`:

```swift
dependencies: [
    .package(url: "https://github.com/your-org/PetstoreClient", from: "1.0.0")
]
```

Then add `PetstoreClient` as a dependency of your target.

## Quick Start

```swift
import PetstoreClient

let client = Client(host: "https://api.example.com", accessToken: "your-token")
```

## Authentication

All authentication is handled via `Authenticator` protocol implementations passed to the client initializer.

### Bearer Token

```swift
let authenticator = BearerAuthenticator(host: "https://api.example.com", token: "your-token")
let client = Client(authenticator: authenticator)
```

### Basic Auth

```swift
let authenticator = BasicAuthenticator(host: "https://api.example.com", username: "username", password: "password")
let client = Client(authenticator: authenticator)
```

### API Key

```swift
let authenticator = ApiKeyAuthenticator(host: "https://api.example.com", keyName: "key-name", keyValue: "key-value", location: .header)
let client = Client(authenticator: authenticator)
```

### OAuth2 Client Credentials

```swift
let authenticator = OAuth2ClientCredentialsAuthenticator(
    host: "https://api.example.com",
    clientId: "client-id",
    clientSecret: "client-secret",
    tokenUrl: "https://auth.example.com/token")
let client = Client(authenticator: authenticator)
```

## Error Handling

All API errors conform to the `Error` protocol. The error hierarchy is:

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

```swift
do {
    let pet = try await client.petApi.getPetById(petId: petId)
} catch let error as NotFoundError {
    print("Not found: \(error)")
} catch let error as ClientError {
    print("Client error \(error.statusCode): \(error)")
} catch let error as ServerError {
    print("Server error: \(error)")
} catch let error as ApiError {
    print("API error: \(error)")
}
```

## Configuration

### Custom Transport Options

```swift
let transport = TransportOptionsBuilder()
    .proxy("http://proxy:3128")
    .timeout(5)
    .build()

let client = Client(authenticator: authenticator, transportOptions: transport)
```

## API Methods

Each API group is exposed as a typed property on the client (e.g., `client.petApi`). API classes have async methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are async and should be called with `try await`.

## Models

Models are generated as Swift structs conforming to `Codable` in the `Models` directory.

```swift
let pet = Pet(name: "Fido", status: "available")
```

## Binary / File Uploads

File upload parameters are typed as `Data`. Binary response bodies are returned as `Data`.

## Comment Style

Never use inline comments (`//`). Always use block comments (`/* ... */`). Doc comments (`///`) are fine.

```good
/* This explains the logic */
let x = 1
```

```bad
// This explains the logic
let x = 1
```
