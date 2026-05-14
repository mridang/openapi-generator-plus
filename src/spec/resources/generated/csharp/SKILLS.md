# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

```bash
dotnet add package PetstoreClient
```

## Quick Start

```csharp
using PetstoreClient;
using PetstoreClient.Auth;

var client = Client.WithToken("https://api.example.com", "your-token");
```

## Authentication

All authentication is handled via `IAuthenticator` implementations passed to the client constructor.

### Bearer Token

```csharp
using PetstoreClient.Auth;

var authenticator = new BearerAuthenticator("https://api.example.com", "your-token");
var client = new Client(authenticator);
```

### Basic Auth

```csharp
var authenticator = new BasicAuthenticator("https://api.example.com", "username", "password");
var client = new Client(authenticator);
```

### API Key

```csharp
var authenticator = new ApiKeyAuthenticator("https://api.example.com", "key-name", "key-value", ApiKeyLocation.Header);
var client = new Client(authenticator);
```

### OAuth2 Client Credentials

```csharp
using PetstoreClient.Auth.OAuth;

var authenticator = new OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token");
var client = new Client(authenticator);
```

## Error Handling

All API errors inherit from `ApiError`. The error hierarchy is:

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

```csharp
using PetstoreClient.Errors;

try
{
    var result = await client.PetApi.GetPetByIdAsync(petId);
}
catch (NotFoundError e)
{
    Console.WriteLine($"Not found: {e.Message}");
}
catch (ClientError e)
{
    Console.WriteLine($"Client error {e.StatusCode}: {e.Message}");
}
catch (ServerError e)
{
    Console.WriteLine($"Server error: {e.Message}");
}
catch (ApiError e)
{
    Console.WriteLine($"API error: {e.Message}");
}
```

## Configuration

### Custom Transport Options

```csharp
var transport = TransportOptions.Builder()
    .Proxy("http://proxy:3128")
    .Timeout(5000)
    .Build();

var client = new Client(authenticator, transport);
```

The client implements `IDisposable`. Use `using` statements or call `Dispose()` when done.

## API Methods

Each API group is exposed as a typed property on the client (e.g., `client.PetApi`). API classes have async methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are async and return `Task<T>`. Use `await` to invoke them.

## Models

Models are generated as C# classes under the `PetstoreClient.Models` namespace.

```csharp
using PetstoreClient.Models;

var pet = new Pet
{
    Name = "Fido",
    Status = "available"
};
```

## Binary / File Uploads

File upload parameters are typed as `Stream`. Binary response bodies are returned as `byte[]`.

## Comment Style

Never use inline comments (`//`). Always use block comments (`/* ... */`). XML doc comments (`///`) are allowed.

```good
/* This explains the logic */
var x = 1;
```

```bad
// This explains the logic
var x = 1;
```
