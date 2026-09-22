# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add to your `build.gradle.kts`:

```kotlin
implementation("com.example.petstore:openapi-kotlin-client:1.0.0")
```

Or with Maven:

```xml
<dependency>
    <groupId>com.example.petstore</groupId>
    <artifactId>openapi-kotlin-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

```kotlin
import com.example.petstore.Client
import com.example.petstore.auth.BearerAuthenticator

val client = Client.withToken("https://api.example.com", "your-token")
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```kotlin
import com.example.petstore.auth.BearerAuthenticator

val authenticator = BearerAuthenticator("https://api.example.com", "your-token")
val client = Client(authenticator)
```

### Basic Auth

```kotlin
import com.example.petstore.auth.BasicAuthenticator

val authenticator = BasicAuthenticator("https://api.example.com", "username", "password")
val client = Client(authenticator)
```

### API Key

```kotlin
import com.example.petstore.auth.ApiKeyAuthenticator
import com.example.petstore.auth.ApiKeyLocation

val authenticator = ApiKeyAuthenticator("https://api.example.com", "key-name", "key-value", ApiKeyLocation.HEADER)
val client = Client(authenticator)
```

### OAuth2 Client Credentials

```kotlin
import com.example.petstore.auth.oauth.OAuth2ClientCredentialsAuthenticator

val authenticator = OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", listOf())
val client = Client(authenticator)
```

### OAuth2 Authorization Code

```kotlin
import com.example.petstore.auth.oauth.OAuth2AuthorizationCodeAuthenticator
import kotlinx.coroutines.runBlocking

val authenticator = OAuth2AuthorizationCodeAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/authorize", "https://auth.example.com/token",
    "https://app.example.com/callback", listOf())
val client = Client(authenticator)
runBlocking { authenticator.exchangeCode("authorization-code") }
```

### OAuth2 Password

```kotlin
import com.example.petstore.auth.oauth.OAuth2PasswordAuthenticator

val authenticator = OAuth2PasswordAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", null, "username", "password", listOf())
val client = Client(authenticator)
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```kotlin
import com.example.petstore.auth.oauth.OAuth2ImplicitAuthenticator

val authenticator = OAuth2ImplicitAuthenticator(
    "https://api.example.com", "client-id", "https://auth.example.com/authorize", listOf())
authenticator.setAccessToken("your-access-token")
val client = Client(authenticator)
```

### OpenID Connect

```kotlin
import com.example.petstore.auth.oauth.OpenIdConnectAuthenticator
import kotlinx.coroutines.runBlocking

val authenticator = OpenIdConnectAuthenticator(
    "https://api.example.com", "https://auth.example.com/.well-known/openid-configuration",
    "client-id", "client-secret", "https://app.example.com/callback", listOf())
val client = Client(authenticator)
runBlocking { authenticator.exchangeCode("authorization-code") }
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

- `ClientAuthMethod.BODY` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod.BASIC` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```kotlin
import com.example.petstore.auth.oauth.ClientAuthMethod
import com.example.petstore.auth.oauth.OAuth2ClientCredentialsAuthenticator

val authenticator = OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", listOf(),
    clientAuthMethod = ClientAuthMethod.BASIC)
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `Servers` object exposes each as a `ServerConfiguration` property (e.g., `Servers.SERVER_0`, `Servers.SERVER_1`, ...) plus a `Servers.ALL` list. Pass the desired server's URL to the client:

```kotlin
import com.example.petstore.Servers

val client = Client.withToken(Servers.SERVER_0.getUrl(), "your-token")
```

## Testing

The `Authenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```kotlin
import com.example.petstore.auth.Authenticator

val fake = object : Authenticator {
    override fun getHost() = "https://api.example.com"
    override suspend fun getAuthHeaders() = mapOf("Authorization" to "Bearer test-token")
}

val client = Client(fake)
```

## Error Handling

All API errors derive from `ApiException`. The error hierarchy is:

- `ApiException` (base)
  - `ClientException` (4xx)
    - `BadRequestException` (400)
    - `UnauthorizedException` (401)
    - `ForbiddenException` (403)
    - `NotFoundException` (404)
    - `ConflictException` (409)
    - `UnprocessableEntityException` (422)
  - `ServerException` (5xx)
    - `InternalServerErrorException` (500)
  - `NetworkException` (no HTTP response, status 0)
    - `NetworkTimeoutException` (the request timed out, status 0)

```kotlin
import com.example.petstore.Client
import com.example.petstore.errors.*
import com.example.petstore.models.Pet

// Operations are suspend functions, so they must be called from a coroutine.
suspend fun addPetOrReport(client: Client, pet: Pet) {
    try {
        client.pet.addPet(pet)
    } catch (e: NotFoundException) {
        println("Not found: ${e.message}")
    } catch (e: ClientException) {
        println("Client error ${e.statusCode}: ${e.message}")
    } catch (e: ServerException) {
        println("Server error: ${e.message}")
    } catch (e: ApiException) {
        println("API error: ${e.message}")
    }
}
```

## Configuration

### Custom Transport Options

```kotlin
import com.example.petstore.TransportOptions

val transport = TransportOptions.builder()
    .proxy("http://proxy:3128")
    .timeout(5000L)
    .build()

val client = Client(authenticator, transport)
```

## API Methods

Each API group is exposed as a typed property on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are asynchronous; call them from a coroutine.

## Models

Models are generated as Kotlin data classes in the `com.example.petstore.models` package.

```kotlin
import com.example.petstore.models.*

val model = ApiResponse(/* properties */)
```

## Binary / File Uploads

File upload parameters are typed as `java.io.File`. Binary response bodies are returned as `ByteArray`.

## Comment Style

Never place a comment on the same line as code. Use block comments (`/* ... */`); KDoc (`/** ... */`) is fine.

```good
/* This explains the logic */
val x = 1
```

```bad
// This explains the logic
val x = 1
```
