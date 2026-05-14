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
