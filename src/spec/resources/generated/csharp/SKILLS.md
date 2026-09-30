# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

```bash
dotnet add package PetstoreClient
```

## Quick Start

```csharp
using PetstoreClient;
using PetstoreClient.Auth;

var client = global::PetstoreClient.Client.WithToken("https://api.example.com", "your-token");
```

## Authentication

All authentication is handled via `IAuthenticator` implementations passed to the client constructor.

### Bearer Token

```csharp
using PetstoreClient.Auth;

var authenticator = new BearerAuthenticator("https://api.example.com", "your-token");
var client = new global::PetstoreClient.Client(authenticator);
```

### Basic Auth

```csharp
using PetstoreClient.Auth;

var authenticator = new BasicAuthenticator("https://api.example.com", "username", "password");
var client = new global::PetstoreClient.Client(authenticator);
```

### API Key

```csharp
using PetstoreClient.Auth;

var authenticator = new ApiKeyAuthenticator("https://api.example.com", "key-name", "key-value", ApiKeyLocation.Header);
var client = new global::PetstoreClient.Client(authenticator);
```

### OAuth2 Client Credentials

```csharp
using PetstoreClient.Auth.OAuth;

var authenticator = new OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    new Uri("https://auth.example.com/token"), []);
var client = new global::PetstoreClient.Client(authenticator);
```

### OAuth2 Authorization Code

```csharp
using PetstoreClient.Auth.OAuth;

var authenticator = new OAuth2AuthorizationCodeAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    new Uri("https://auth.example.com/authorize"), new Uri("https://auth.example.com/token"),
    null, new Uri("https://app.example.com/callback"), []);
var client = new global::PetstoreClient.Client(authenticator);
await authenticator.ExchangeCodeAsync("authorization-code");
```

### OAuth2 Password

```csharp
using PetstoreClient.Auth.OAuth;

var authenticator = new OAuth2PasswordAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    new Uri("https://auth.example.com/token"), null, "username", "password", []);
var client = new global::PetstoreClient.Client(authenticator);
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```csharp
using PetstoreClient.Auth.OAuth;

var authenticator = new OAuth2ImplicitAuthenticator(
    "https://api.example.com", "client-id", new Uri("https://auth.example.com/authorize"), []);
authenticator.SetAccessToken("your-access-token");
var client = new global::PetstoreClient.Client(authenticator);
```

### OpenID Connect

```csharp
using PetstoreClient.Auth.OAuth;

var authenticator = new OpenIdConnectAuthenticator(
    "https://api.example.com", new Uri("https://auth.example.com/.well-known/openid-configuration"),
    "client-id", "client-secret", new Uri("https://app.example.com/callback"), []);
var client = new global::PetstoreClient.Client(authenticator);
await authenticator.ExchangeCodeAsync("authorization-code");
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

- `ClientAuthMethod.Body` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod.Basic` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```csharp
using PetstoreClient.Auth.OAuth;

var authenticator = new OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    new Uri("https://auth.example.com/token"), [], clientAuthMethod: ClientAuthMethod.Basic);
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `Servers` class exposes each as a `ServerConfiguration` field (e.g., `Servers.Server0`, `Servers.Server1`, ...) plus a `Servers.All` collection. Pass the desired server's URL to the client:

```csharp
using PetstoreClient;

var client = global::PetstoreClient.Client.WithToken(Servers.Server0.GetUrl(), "your-token");
```

## Testing

The `IAuthenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```csharp
using PetstoreClient.Auth;

var client = new global::PetstoreClient.Client(new FakeAuthenticator());

public sealed class FakeAuthenticator : IAuthenticator
{
    public string GetHost() => "https://api.example.com";

    public Dictionary<string, string> GetAuthHeaders() =>
        new() { ["Authorization"] = "Bearer test-token" };
}
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

```csharp
using PetstoreClient;
using PetstoreClient.Errors;
using PetstoreClient.Models;

async Task AddPetOrReportAsync(global::PetstoreClient.Client client, Pet pet)
{
    try
    {
        await client.Pet.AddPetAsync(pet);
    }
    catch (NotFoundException e)
    {
        Console.WriteLine($"Not found: {e.Message}");
    }
    catch (ClientException e)
    {
        Console.WriteLine($"Client error {e.StatusCode}: {e.Message}");
    }
    catch (ServerException e)
    {
        Console.WriteLine($"Server error: {e.Message}");
    }
    catch (ApiException e)
    {
        Console.WriteLine($"API error: {e.Message}");
    }
}
```

## Configuration

### Custom Transport Options

```csharp
using PetstoreClient;

var transport = TransportOptions.Builder()
    .Proxy("http://proxy:3128")
    .Timeout(5000)
    .Build();

var client = new global::PetstoreClient.Client(authenticator, transport);
```

The client implements `IDisposable`. Use `using` statements or call `Dispose()` when done.

## API Methods

Each API group is exposed as a typed property on the client (e.g., `client.Pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are asynchronous; invoke them with `await`.

## Models

Models are generated as C# classes under the `PetstoreClient.Models` namespace.

```csharp
using PetstoreClient.Models;

var model = new ApiResponse();
```

## Binary / File Uploads

File upload parameters are typed as `Stream`. Binary response bodies are returned as `byte[]`.

## Comment Style

Never place a comment on the same line as code. Use block comments (`/* ... */`); XML doc comments (`///`) are fine.

```good
/* This explains the logic */
var x = 1;
```

```bad
// This explains the logic
var x = 1;
```
