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
    clientID: "client-id",
    clientSecret: "client-secret",
    tokenURL: "https://auth.example.com/token")
let client = Client(authenticator: authenticator)
```

### OAuth2 Authorization Code

```swift
let authenticator = OAuth2AuthCodeAuthenticator(
    host: "https://api.example.com",
    clientID: "client-id",
    clientSecret: "client-secret",
    authorizationURL: "https://auth.example.com/authorize",
    tokenURL: "https://auth.example.com/token",
    redirectURI: "https://app.example.com/callback")
let client = Client(authenticator: authenticator)
```

### OAuth2 Password

```swift
let authenticator = OAuth2PasswordAuthenticator(
    host: "https://api.example.com",
    clientID: "client-id",
    clientSecret: "client-secret",
    tokenURL: "https://auth.example.com/token",
    username: "username",
    password: "password")
let client = Client(authenticator: authenticator)
```

### OAuth2 Implicit

The implicit flow obtains the access token in the browser via the authorization URL. The authenticator wraps the configuration; the access token itself is supplied by your front-end.

```swift
let authenticator = OAuth2ImplicitAuthenticator(
    host: "https://api.example.com",
    clientID: "client-id",
    authorizationURL: "https://auth.example.com/authorize")
let client = Client(authenticator: authenticator)
```

### OpenID Connect

```swift
let authenticator = OpenIdConnectAuthenticator(
    host: "https://api.example.com",
    openIDConnectURL: "https://auth.example.com/.well-known/openid-configuration",
    clientID: "client-id",
    clientSecret: "client-secret",
    redirectURI: "https://app.example.com/callback")
let client = Client(authenticator: authenticator)
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators implement `authHeaders(request:) async throws -> [String: String]` because resolving the access token requires an HTTP call to the token endpoint. The generated API methods always `await` this call before sending the request. A synchronous overload is preserved for back-compat but the OAuth flows require the async path.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed; tune your authorization server's `expires_in` if it is too tight.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethod.body` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod.basic` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```swift
let authenticator = OAuth2ClientCredentialsAuthenticator(
    host: "https://api.example.com",
    clientID: "client-id",
    clientSecret: "client-secret",
    tokenURL: "https://auth.example.com/token",
    clientAuthMethod: .basic)
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `Servers` enum exposes each as a `ServerConfiguration` static property (e.g., `Servers.server0`, `Servers.server1`, ...) plus a `Servers.all` array. Pass the desired server's URL to the client:

```swift
let client = Client(host: Servers.server0.url(), accessToken: "your-token")
```

## Testing

The `Authenticator` protocol is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```swift
struct FakeAuthenticator: Authenticator {
    var host: String { "https://api.example.com" }
    func authHeaders(request: RequestContext) async throws -> [String: String] {
        ["Authorization": "Bearer test-token"]
    }
}

let client = Client(authenticator: FakeAuthenticator())
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
