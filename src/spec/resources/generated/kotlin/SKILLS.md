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
    "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token")
val client = Client(authenticator)
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

```kotlin
import com.example.petstore.errors.*

try {
    val result = client.petApi.getPetById(petId)
} catch (e: NotFoundError) {
    println("Not found: ${e.message}")
} catch (e: ClientError) {
    println("Client error ${e.statusCode}: ${e.message}")
} catch (e: ServerError) {
    println("Server error: ${e.message}")
} catch (e: ApiError) {
    println("API error: ${e.message}")
}
```

## Configuration

### Custom Transport Options

```kotlin
import com.example.petstore.TransportOptions

val transport = TransportOptions.builder()
    .proxy("http://proxy:3128")
    .timeout(5000)
    .build()

val client = Client(authenticator, transport)
```

## API Methods

Each API group is exposed as a typed property on the client. API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as Kotlin data classes. They are located in the `com.example.petstore.models` package.

```kotlin
import com.example.petstore.models.*

val pet = Pet(name = "Fido", status = "available")
```

## Binary / File Uploads

File upload parameters are typed as `java.io.File`. Binary response bodies are returned as `ByteArray`.
