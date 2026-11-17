# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.example.petstore</groupId>
    <artifactId>petstore-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

Or with Gradle:

```groovy
implementation 'com.example.petstore:petstore-client:1.0.0'
```

## Quick Start

```java
import com.example.petstore.Client;
import com.example.petstore.auth.BearerAuthenticator;

Client client = Client.withToken("https://api.example.com", "your-token");
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```java
import com.example.petstore.auth.BearerAuthenticator;

var authenticator = new BearerAuthenticator("https://api.example.com", "your-token");
var client = new Client(authenticator);
```

A token that rotates comes from a callback that runs before every request (pass either a token or a callback, never both):

```java
var authenticator = new BearerAuthenticator("https://api.example.com", () -> secretStore.currentToken());
```

### Basic Auth

```java
import com.example.petstore.auth.BasicAuthenticator;

var authenticator = new BasicAuthenticator("https://api.example.com", "username", "password");
var client = new Client(authenticator);
```

### API Key

```java
import com.example.petstore.auth.ApiKeyAuthenticator;
import com.example.petstore.auth.ApiKeyLocation;

var authenticator = new ApiKeyAuthenticator("https://api.example.com", "key-name", "key-value", ApiKeyLocation.HEADER);
var client = new Client(authenticator);
```

### OAuth2 Client Credentials

```java
import com.example.petstore.auth.oauth.OAuth2ClientCredentialsAuthenticator;
import java.util.List;

var authenticator = new OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", List.of());
var client = new Client(authenticator);
```

### OAuth2 Authorization Code

```java
import com.example.petstore.auth.oauth.OAuth2AuthorizationCodeAuthenticator;
import java.util.List;

var authenticator = new OAuth2AuthorizationCodeAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/authorize", "https://auth.example.com/token",
    "https://app.example.com/callback", List.of());
var client = new Client(authenticator);
authenticator.exchangeCode("authorization-code");
```

`buildAuthorizationUrl(state)` adds a PKCE S256 `code_challenge` (RFC 7636) and keeps the matching 43-character verifier, which `exchangeCode` sends as `code_verifier`; call it before `exchangeCode`.

### OAuth2 Password

```java
import com.example.petstore.auth.oauth.OAuth2PasswordAuthenticator;
import java.util.List;

var authenticator = new OAuth2PasswordAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", "username", "password", List.of());
var client = new Client(authenticator);
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```java
import com.example.petstore.auth.oauth.OAuth2ImplicitAuthenticator;
import java.util.List;

var authenticator = new OAuth2ImplicitAuthenticator(
    "https://api.example.com", "client-id", "https://auth.example.com/authorize", List.of());
authenticator.setAccessToken("your-access-token");
var client = new Client(authenticator);
```

### OpenID Connect

```java
import com.example.petstore.auth.oauth.OpenIdConnectAuthenticator;
import java.util.List;

var authenticator = new OpenIdConnectAuthenticator(
    "https://api.example.com", "https://auth.example.com/.well-known/openid-configuration",
    "client-id", "client-secret", "https://app.example.com/callback", List.of());
var client = new Client(authenticator);
authenticator.exchangeCode("authorization-code");
```

### Credentials from the environment

The authenticator generated for a security scheme (in the `auth` package, named after the scheme) reads a credential you pass as `null` or an empty string from an environment variable, once, when it is built. A value you pass wins. A credential found in neither place throws `IllegalArgumentException` naming the variable.

| Scheme | Credential | Variable |
| --- | --- | --- |
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

The base `BearerAuthenticator`, `BasicAuthenticator` and `ApiKeyAuthenticator` never read the environment; A rotating token comes from a callback instead: `new BearerAuthenticator(host, tokenProvider)`.

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators resolve the access token by making an HTTP call to the token endpoint, which happens before the request carrying it is sent.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Expired tokens

When the API answers an OAuth2 or OpenID Connect request with `401`, the authenticator drops the cached access token (`invalidateAccessToken()`, the refresh token is kept) and the request is sent once more with fresh headers. A second `401` raises `UnauthorizedException`. Stream and multipart bodies are not replayed; API key, basic and bearer authenticators never replay.

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed; tune your authorization server's `expires_in` if it is too tight.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethod.BODY` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod.BASIC` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```java
import com.example.petstore.auth.oauth.ClientAuthMethod;
import com.example.petstore.auth.oauth.OAuth2ClientCredentialsAuthenticator;

var authenticator = new OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", java.util.List.of(), ClientAuthMethod.BASIC);
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `Servers` class exposes each as a `ServerConfiguration` constant (e.g., `Servers.SERVER_0`, `Servers.SERVER_1`, ...) plus a `Servers.ALL` list. Pass the desired server's URL to the client:

```java
import com.example.petstore.Servers;

var client = Client.withToken(Servers.SERVER_0.getUrl(), "your-token");
```

## Testing

The `Authenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```java
import com.example.petstore.auth.Authenticator;

var fake = new Authenticator() {
    public java.util.Map<String, String> getAuthHeaders() {
        return java.util.Map.of("Authorization", "Bearer test-token");
    }
    public String getHost() { return "https://api.example.com"; }
};

var client = new Client(fake);
```

## Error Handling

Every error the SDK raises derives from `OpenAPIException` (unchecked). Catch it to handle everything, or catch the more specific branches below. The error hierarchy is:

- `OpenAPIException` (root)
  - `SerializationException` (a request or response body could not be encoded or decoded)
  - `OAuth2ServerException` (the token endpoint answered with an OAuth2 error response)
  - `OAuth2TokenException` (the token response was unusable, for example no `access_token`)
  - `ApiException` (an HTTP response or its absence)
    - `ClientException` (4xx)
      - `BadRequestException` (400)
      - `UnauthorizedException` (401)
      - `ForbiddenException` (403)
      - `NotFoundException` (404)
      - `ConflictException` (409)
      - `UnprocessableEntityException` (422)
      - `TooManyRequestsException` (429)
    - `ServerException` (5xx)
      - `InternalServerErrorException` (500)
    - `NetworkException` (no HTTP response, status 0)
      - `NetworkTimeoutException` (the request timed out, status 0)

```java
import com.example.petstore.errors.*;
import com.example.petstore.models.Pet;

try {
    client.pet.addPet(new Pet());
} catch (NotFoundException e) {
    System.out.println("Not found: " + e.getMessage());
} catch (ClientException e) {
    System.out.println("Client error " + e.getStatusCode() + ": " + e.getMessage());
} catch (ServerException e) {
    System.out.println("Server error: " + e.getMessage());
} catch (ApiException e) {
    System.out.println("API error: " + e.getMessage());
}
```

An `ApiException` also carries `getRequestMethod()` and `getRequestPath()` (no query string), which `toString()` renders as a `Request: GET /path` line.

## Configuration

### Custom Transport Options

```java
import com.example.petstore.TransportOptions;

var transport = TransportOptions.builder()
    .proxy("http://proxy:3128")
    .timeout(5000)
    .maxRetries(3)
    .build();

var client = new Client(authenticator, transport);
```

### Retries

`maxRetries` (default `2`, `0` disables) retries a network error that happened before any response, and HTTP 408, 409, 429 and 500-599 except 501. The wait is 0.5 s doubling to 8 s with 25% jitter, or the `Retry-After` header (seconds or an HTTP date, capped at 60 s). Retries share the single end-to-end `timeout` and never extend it, a thread interrupt cancels the wait, and a stream body is never retried.

### Custom HTTP client

```java
var httpClient = java.net.http.HttpClient.newBuilder()
    .followRedirects(java.net.http.HttpClient.Redirect.NEVER)
    .build();
var transport = TransportOptions.builder().httpClient(httpClient).build();
```

The SDK sends every request through your client and never closes it. Combining it with `proxy`, `caCertPath` or `verifySsl(false)` fails at `build()`.

### Cancellation

Interrupt the calling thread; the call throws `java.util.concurrent.CancellationException` (the `InterruptedException` is its cause) and is never reported as `NetworkTimeoutException`.

```java
var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
var task = executor.submit(() -> client.pet.addPet(new Pet()));
task.cancel(true);
```

### Per-call options

Every operation takes an optional trailing `<Operation>Options` object, even one with no parameters of its own and no security. It also takes `timeout(millis)`, `maxRetries(n)` and `headers(map)`, which apply to that call only; leave it out or pass `null` to use the client's settings.

### Raw responses, timeouts and the request id

Each operation has a `...WithHttpInfo` variant returning `ApiResult<T>` (`statusCode()`, `data()`, `rawBody()`, `headers()`). `timeout` is one end-to-end budget in positive milliseconds. An `X-Request-ID` header is added to each request unless you set one.

## API Methods

Each API group is exposed as a typed field on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as immutable Java classes in the `com.example.petstore.models` package: the fields are `final`, a model is made with its `builder()` (or its constructor when it has required properties) and copied with `toBuilder()`. A required property that is missing fails when the model is built.

```java
var changed = model.toBuilder().someProperty(value).build();
```

A model that accepts extra keys reads them through `getAdditionalProperties()` (unmodifiable) and writes them with the builder's `putAdditionalProperty`, `putAllAdditionalProperties`, `removeAdditionalProperty` and `additionalProperties(map)`. A `oneOf` or `anyOf` model has `ofVariant(v)`, and for a plain class variant `isVariant()` and `asVariant()`.

## Binary / File Uploads

File upload parameters are typed as `java.io.InputStream` (`List<InputStream>` for a multi-file field). Binary response bodies are returned as `java.io.InputStream`.

## Comment Style

Never place a comment on the same line as code. Use block comments (`/* ... */`); Javadoc (`/** ... */`) is fine.

```good
/* This explains the logic */
int x = 1;
```

```bad
// This explains the logic
int x = 1;
```
