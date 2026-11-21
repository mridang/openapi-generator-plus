# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add to your `Package.swift`:

```text
dependencies: [
    .package(url: "https://github.com/your-org/PetstoreClient", from: "1.0.0")
]
```

Then add `PetstoreClient` as a dependency of your target.

## Quick Start

```swift statements
import Foundation
import PetstoreClient

let environment = ProcessInfo.processInfo.environment
guard let host = environment["SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL"] else {
    throw ConfigurationError.invalidArgument("SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL is not set")
}
guard let token = environment["SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN"] else {
    throw ConfigurationError.invalidArgument("SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN is not set")
}
let client = try Client.withToken(host: host, accessToken: token)
defer { client.close() }
let result = try await client.store.getDefaults()
print(result)
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```swift file
import PetstoreClient

func makeClient() throws -> Client {
    let authenticator = try BearerAuthenticator(host: "https://api.example.com", token: "your-token")
    return try Client(authenticator: authenticator)
}
```

### Basic Auth

```swift file
import PetstoreClient

func makeClient() throws -> Client {
    let authenticator = try BasicAuthenticator(host: "https://api.example.com", username: "username", password: "password")
    return try Client(authenticator: authenticator)
}
```

### API Key

```swift file
import PetstoreClient

func makeClient() throws -> Client {
    let authenticator = try ApiKeyAuthenticator(
        host: "https://api.example.com", keyParamName: "key-name", apiKey: "key-value", location: .header)
    return try Client(authenticator: authenticator)
}
```

### OAuth2 Client Credentials

```swift file
import PetstoreClient

func makeClient() throws -> Client {
    let authenticator = OAuth2ClientCredentialsAuthenticator(
        host: "https://api.example.com",
        clientID: "client-id",
        clientSecret: "client-secret",
        tokenURL: "https://auth.example.com/token")
    return try Client(authenticator: authenticator)
}
```

### OAuth2 Authorization Code

```swift file
import PetstoreClient

func makeClient() throws -> Client {
    let authenticator = OAuth2AuthorizationCodeAuthenticator(
        host: "https://api.example.com",
        clientID: "client-id",
        clientSecret: "client-secret",
        authorizationURL: "https://auth.example.com/authorize",
        tokenURL: "https://auth.example.com/token",
        redirectURI: "https://app.example.com/callback")
    return try Client(authenticator: authenticator)
}
```

### OAuth2 Password

```swift file
import PetstoreClient

func makeClient() throws -> Client {
    let authenticator = OAuth2PasswordAuthenticator(
        host: "https://api.example.com",
        clientID: "client-id",
        clientSecret: "client-secret",
        tokenURL: "https://auth.example.com/token",
        username: "username",
        password: "password")
    return try Client(authenticator: authenticator)
}
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```swift file
import PetstoreClient

func makeClient() throws -> Client {
    let authenticator = OAuth2ImplicitAuthenticator(
        host: "https://api.example.com",
        clientID: "client-id",
        authorizationURL: "https://auth.example.com/authorize")
    try authenticator.setAccessToken("your-access-token")
    return try Client(authenticator: authenticator)
}
```

### OpenID Connect

```swift file
import PetstoreClient

func makeClient() throws -> Client {
    let authenticator = OpenIdConnectAuthenticator(
        host: "https://api.example.com",
        openIDConnectURL: "https://auth.example.com/.well-known/openid-configuration",
        clientID: "client-id",
        clientSecret: "client-secret",
        redirectURI: "https://app.example.com/callback")
    return try Client(authenticator: authenticator)
}
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators resolve the access token by making an HTTP call to the token endpoint, which happens before the request carrying it is sent.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Token caching

The token manager caches the access token in memory and refreshes it shortly before its declared expiry: one safety margin of `min(60 seconds, expires_in / 2)` is taken off the lifetime when the token is stored, so a token with a 60 second lifetime is reused for 30 seconds. The margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. A token response without `expires_in` is cached until the token is invalidated.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethod.body` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod.basic` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```swift file
import PetstoreClient

func makeAuthenticator() -> OAuth2ClientCredentialsAuthenticator {
    OAuth2ClientCredentialsAuthenticator(
        host: "https://api.example.com",
        clientID: "client-id",
        clientSecret: "client-secret",
        tokenURL: "https://auth.example.com/token",
        clientAuthMethod: .basic)
}
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `Servers` enum exposes each as a `ServerConfiguration` static property (e.g., `Servers.server0`, `Servers.server1`, ...) plus a `Servers.all` array. Pass the desired server's URL to the client:

```swift file
import PetstoreClient

func makeClient() throws -> Client {
    try Client(host: Servers.server0.url(), accessToken: "your-token")
}
```

## Testing

The `Authenticator` protocol is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```swift file
import PetstoreClient

struct FakeAuthenticator: Authenticator {
    func host() -> String { "https://api.example.com" }
    func authHeaders() async throws -> [String: String] {
        ["Authorization": "Bearer test-token"]
    }
    func queryParams() -> [String: String] { [:] }
    func cookieParams() -> [String: String] { [:] }
}

func makeClient() throws -> Client {
    try Client(authenticator: FakeAuthenticator())
}
```

## Error Handling

Every error the SDK raises for a call derives from the root error `OpenAPIError`. The error hierarchy is:

- `OpenAPIError` (root)
  - `SerializationError` (a request or response body could not be encoded or decoded)
  - `OAuth2ServerError` (the token endpoint answered with a non-2xx status)
  - `OAuth2TokenError` (the token endpoint answered 2xx with a body that is not a token response)
  - `ApiError` (the API answered with an error, or the request could not be sent)
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

```swift file
import PetstoreClient

func addPetOrReport(client: Client, pet: Pet, options: AddPetOptions?) async throws {
    do {
        let result = try await client.pet.addPet(pet: pet, options: options)
        print(result)
    } catch let error as NotFoundError {
        print("Not found: \(error)")
    } catch let error as ClientError {
        print("Client error \(error.statusCode): \(error)")
    } catch let error as ServerError {
        print("Server error: \(error)")
    } catch let error as ApiError {
        print("API error: \(error)")
    } catch let error as SerializationError {
        print("Body could not be encoded or decoded: \(error)")
    } catch let error as OAuth2ServerError {
        print("Token request refused with status \(error.statusCode): \(error)")
    } catch let error as OAuth2TokenError {
        print("Unusable token response: \(error)")
    }
}
```

Every `ApiError` has `requestMethod` and `requestPath` (no query string), and its message reads `API returned status code 404 for GET /items/7`.

A mistake in how the SDK is configured or called (a bad proxy URL, a missing required parameter, a closed client) is a `ConfigurationError`, which is not part of this hierarchy.

## Configuration

### Custom Transport Options

```swift file
import PetstoreClient

func makeClient(authenticator: any Authenticator) throws -> Client {
    let transport = try TransportOptions.builder()
        .proxy("http://proxy:3128")
        .timeout(5000)
        .build()

    return try Client(authenticator: authenticator, transportOptions: transport)
}
```

### Streaming

An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns a `ByteStream` instead of the whole body in memory, so a large download never has to fit in memory. The call returns as soon as the response headers arrive, and the body is read as you consume it. A `ByteStream` has the status code, the lower-cased headers and an idempotent `close()` that aborts the connection instead of draining it, so close it when you stop early (`defer`). `readAll()` reads the rest into memory and leaves the stream closed.

```swift file
import Foundation
import PetstoreClient

func saveDownload(client: Client, petId: Int64, documentId: Int64, to target: URL) async throws {
    let stream = try await client.pet.downloadPetDocument(petId: petId, documentId: documentId)
    defer { stream.close() }
    print(stream.statusCode)
    _ = FileManager.default.createFile(atPath: target.path, contents: nil)
    let handle = try FileHandle(forWritingTo: target)
    defer { try? handle.close() }
    for try await chunk in stream {
        try handle.write(contentsOf: chunk)
    }
}
```

The call `timeout` covers the wait for the response headers and stops there. `idleTimeout` (in `TransportOptions`, default 60 seconds, `0` or `nil` disables it) bounds the gap between two reads of the body and raises `NetworkTimeoutError`. Retries, redirects, token refresh and the error contract apply up to the headers exactly as for a buffered call; a request is never retried after the first body byte, and a non-2xx status raises the usual typed error with its body read up to 64 KiB and never returns a stream. A connection reset, or a body that ends before its declared `Content-Length`, raises `StreamError`, a `NetworkError` with status 0. `client.close()` cancels the streams that are still open, and their next read raises the closed-client error. A custom transport that does not implement `sendStreaming` fails a stream call with `ConfigurationError.unsupported` naming it, and its buffered calls keep working.

### Custom HTTP client

To send requests through your own `URLSession`, pass it to `TransportOptions.builder().session(mySession)`. The SDK keeps its timeout, user agent and headers on top and never invalidates the session; combining it with `proxy`, `caCertPath`, `verifySsl(false)`, `followRedirects(false)` or `maxRedirects` throws a `ConfigurationError`.

### Retries

Failed calls are retried automatically: `TransportOptions.builder().maxRetries(n)` sets the count (default 2, `0` disables). The SDK retries network errors that happened before a response and statuses 408, 409, 429 and 500 to 599 except 501, waiting 0.5 s doubling to 8 s with 25% jitter or the server's `Retry-After` (seconds or HTTP date, capped at 60 s). The `timeout` stays the one end-to-end budget, a stream body is never retried, and each retry re-applies the authentication headers.

### Cancellation

Cancel the Swift `Task` that made the call and the call throws `CancellationError`; the SDK has no cancellation API of its own. `NetworkTimeoutError` is only for the SDK's own `TransportOptions` timeout.

### Per-call options

An operation's options struct also takes `timeout` (milliseconds, greater than zero), `maxRetries` (`0` turns retries off) and `headers` for that call alone; a `nil` keeps the transport's setting. Every operation takes an options struct as its last argument, even one with no parameters and no security requirement, so each call can carry them.

### One credential per security scheme

`Client(host:...)` takes one optional ``Authenticator`` per security scheme, named after the scheme: `petStoreBasic`, `adminBasic`, `petStoreBearer`, `serviceToken`, `apiKeyHeader`, `internalApiKey`, `apiKeyQuery`, `sessionCookie`, `machineAuth`, `legacyAuth`, `userAuth`, `browserAuth`, `ssoAuth`. Each operation uses the credential of the first scheme its own spec lists that you gave the client; there is no per-call authenticator, an operation declared with `security: []` carries none, and an operation none of whose schemes has a credential uses the authenticator of `Client(authenticator:)` if the client was built from one and sends none otherwise.

### Closing the client

`close()` is graceful and idempotent: a request in flight finishes (retries included), the `URLSession` the SDK built is released when the last one ends, a request made afterwards throws `ConfigurationError.invalidState` (`ApiClient has been closed and can no longer send requests`), a second call does nothing and a `URLSession` you supplied is never invalidated. Streams that are still open are cancelled.

### Cookie parameters

A cookie parameter goes out exactly as passed when every character is an RFC 6265 cookie-octet (printable ASCII except space, double quote, comma, semicolon and backslash; `%` is allowed). Any other character throws `ConfigurationError.invalidArgument` before the request is sent: percent-encode it yourself first.

### Credentials from the environment

`Client.fromEnvironment()` reads `SWAGGER_PETSTORE_OPEN_API_3_0_HOST` and `SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN` once, when the client is built (an empty value is unset); the `host`, `token` and `tokenProvider` arguments win, and a missing host or credential throws `ConfigurationError.invalidArgument`.

Each scheme authenticator takes its credentials as optional arguments and reads the variable below when one is left out (an empty value is unset); a credential found in neither place throws `ConfigurationError.invalidArgument` when the authenticator is built.

- `adminBasic` `username`: `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME`
- `adminBasic` `password`: `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD`
- `apiKeyHeader` `apiKey`: `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY`
- `apiKeyQuery` `apiKey`: `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY`
- `browserAuth` `clientId`: `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID`
- `internalApiKey` `apiKey`: `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY`
- `legacyAuth` `clientId`: `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID`
- `legacyAuth` `clientSecret`: `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET`
- `legacyAuth` `username`: `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME`
- `legacyAuth` `password`: `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD`
- `machineAuth` `clientId`: `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID`
- `machineAuth` `clientSecret`: `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET`
- `petStoreBasic` `username`: `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME`
- `petStoreBasic` `password`: `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD`
- `petStoreBearer` `token`: `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN`
- `serviceToken` `token`: `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN`
- `sessionCookie` `apiKey`: `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY`
- `ssoAuth` `clientId`: `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID`
- `ssoAuth` `clientSecret`: `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET`
- `ssoAuth` `redirectUri`: `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI`
- `userAuth` `clientId`: `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID`
- `userAuth` `clientSecret`: `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET`
- `userAuth` `redirectUri`: `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI`

### Raw responses, timeouts and request ids

Every operation has a `*WithHTTPInfo` variant that returns an `ApiResult` with the status code, the response headers, the raw body and the decoded data. `TransportOptions.builder().timeout(_:)` is in milliseconds (default 10000) and covers the whole call. Unless `injectRequestID(false)` is set, every request carries a generated `X-Request-ID` header; one you supply always wins.

## Hooks and telemetry

Every operation calls one small, optional hook around the call: it can edit the request headers before the request is sent and observe the status or the error afterwards, and it does nothing when no hook is set. Set it with `callHook:` on the client or `.callHook(hook)` on the `ConfigurationBuilder`, and combine several with `CallHooks.chain`.

```swift file
import PetstoreClient

struct SourceHook: CallHook {
    func start(info: CallInfo, headers: inout [String: String]) throws -> (any CallObserver)? {
        headers["X-Source"] = "nightly-job"
        return Watcher()
    }
}

struct Watcher: CallObserver {
    func finish(_ outcome: CallOutcome) throws {
        print("\(outcome.statusCode) \(String(describing: outcome.error))")
    }
}

func hookedClient(authenticator: any Authenticator) throws -> Client {
    try Client(
        authenticator: authenticator,
        callHook: SourceHook()
    )
}
```

`start` runs once per call, after the trace context is injected, with the final header set, and `finish` runs exactly once with the status code (0 when no response arrived), the lower-cased response headers (`nil` when no response arrived) and the error the caller receives. An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed. A header the hook sets is on every send of the call. The README shows a recipe that records a span, a request count and a duration with the OpenTelemetry API.

## API Methods

Each API group is exposed as a typed property on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are asynchronous; call them with `try await`.

## Models

Models are generated as Swift structs in the `Models` directory.

```swift file
import PetstoreClient

func copyOf(_ model: Account) -> Account {
    model.copy()
}
```

Building a model never validates or throws (an unknown enum string builds no enum); a payload is checked when it is decoded, an unknown enum string decodes to the catch-all case `.unknownDefaultOpenApi`, and a missing required property or a `format: uri` that is not an absolute URI throws `SerializationError`. A model is immutable: its properties are `let`. `model.copy(name: "x")` returns a copy with the given properties replaced (pass `.some(nil)` to clear an optional one). A `oneOf` union is built with `Union.fromVariant(value)` and read with `isVariant` and `asVariant`; an operation with several 2xx schemas returns such a union, decoded by the status that arrived.

## Binary / File Uploads

File upload parameters are typed as `Data`. A binary response body is returned as a `ByteStream`, an `AsyncSequence` of `Data` (see Streaming).

## Comment Style

Never place a comment on the same line as code. Use block comments (`/* ... */`); doc comments (`///`) are fine.

```good
/* This explains the logic */
let x = 1
```

```bad
// This explains the logic
let x = 1
```
