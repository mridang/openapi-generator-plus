# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add to your `pom.xml`:

```xml
<dependency>
    <groupId>com.example.petstore</groupId>
    <artifactId>openapi-java-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

Or with Gradle:

```groovy
implementation 'com.example.petstore:openapi-java-client:1.0.0'
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

var authenticator = new OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token");
var client = new Client(authenticator);
```

### OAuth2 Authorization Code

```java
import com.example.petstore.auth.oauth.OAuth2AuthCodeAuthenticator;

var authenticator = new OAuth2AuthCodeAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", "authorization-code", "https://app.example.com/callback");
var client = new Client(authenticator);
```

### OAuth2 Password

```java
import com.example.petstore.auth.oauth.OAuth2PasswordAuthenticator;

var authenticator = new OAuth2PasswordAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", "username", "password");
var client = new Client(authenticator);
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```java
import com.example.petstore.auth.oauth.OAuth2ImplicitAuthenticator;

var authenticator = new OAuth2ImplicitAuthenticator("https://api.example.com", "your-access-token");
var client = new Client(authenticator);
```

### OpenID Connect

```java
import com.example.petstore.auth.oauth.OpenIdConnectAuthenticator;

var authenticator = new OpenIdConnectAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/.well-known/openid-configuration");
var client = new Client(authenticator);
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 (and OpenID Connect) authenticators resolve the access token by making an HTTP call to the token endpoint, so they implement `HttpAwareAuthenticator`. The shared `ApiClient` is injected into the authenticator by the `Client` constructor via `setApiClient(...)`, so the token exchange reuses the same transport configuration (proxy, TLS, timeouts) as regular API calls. The call is made synchronously inside `getAuthHeaders()` before the request is sent; do not call `getAuthHeaders()` on an OAuth2/OIDC authenticator before the `ApiClient` has been injected.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

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

If the OpenAPI spec defines multiple servers, the generated `Servers` class exposes each as a `ServerConfiguration` constant (e.g., `Servers.SERVER_0`, `Servers.SERVER_1`, ...) plus an `Servers.ALL` list. Pass the desired server's URL to the client:

```java
import com.example.petstore.Servers;

var client = Client.withToken(Servers.SERVER_0.url(), "your-token");
```

## Testing

The `Authenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```java
import com.example.petstore.auth.Authenticator;

var fake = new Authenticator() {
    public java.util.Map<String, String> getAuthHeaders(RequestContext req) {
        return java.util.Map.of("Authorization", "Bearer test-token");
    }
    public String getHost() { return "https://api.example.com"; }
};

var client = new Client(fake);
```

## Error Handling

All API errors extend `ApiError`. The error hierarchy is:

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

```java
import com.example.petstore.errors.*;

try {
    var result = client.petApi.getPetById(petId);
} catch (NotFoundError e) {
    System.out.println("Not found: " + e.getMessage());
} catch (ClientError e) {
    System.out.println("Client error " + e.getStatusCode() + ": " + e.getMessage());
} catch (ServerError e) {
    System.out.println("Server error: " + e.getMessage());
} catch (ApiError e) {
    System.out.println("API error: " + e.getMessage());
}
```

## Configuration

### Custom Transport Options

```java
import com.example.petstore.TransportOptions;

var transport = TransportOptions.builder()
    .proxy("http://proxy:3128")
    .timeout(5000)
    .build();

var client = new Client(authenticator, transport);
```

## API Methods

Each API group is exposed as a typed field on the client. API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as Java classes with builder patterns. They are located in `com.example.petstore.api` and model packages.

```java
import com.example.petstore.models.*;

var pet = new Pet();
pet.setName("Fido");
pet.setStatus("available");
```

## Binary / File Uploads

File upload parameters are typed as `File`. Binary response bodies are returned as `byte[]`.

## Comment Style

Never use inline comments (`//`). Always use block comments (`/* ... */`).

```good
/* This explains the logic */
int x = 1;
```

```bad
// This explains the logic
int x = 1;
```
