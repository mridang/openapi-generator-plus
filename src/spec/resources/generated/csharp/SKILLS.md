# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

```bash
dotnet add package PetstoreClient
```

## Quick Start

```csharp statements
using PetstoreClient;

var host =
    Environment.GetEnvironmentVariable("SWAGGER_PETSTORE_OPEN_API_3_0_HOST")
    ?? throw new InvalidOperationException("SWAGGER_PETSTORE_OPEN_API_3_0_HOST is not set");
var token =
    Environment.GetEnvironmentVariable("SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN")
    ?? throw new InvalidOperationException("SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN is not set");
using var client = Client.WithToken(host, token);
var result = await client.Store.GetDefaultsAsync();
Console.WriteLine(result);
```

## Authentication

All authentication is handled via `IAuthenticator` implementations passed to the client constructor.

### Bearer Token

```csharp statements
using PetstoreClient.Auth;

var authenticator = new BearerAuthenticator("https://api.example.com", "your-token");
var client = new global::PetstoreClient.Client(authenticator);
```

### Basic Auth

```csharp statements
using PetstoreClient.Auth;

var authenticator = new BasicAuthenticator("https://api.example.com", "username", "password");
var client = new global::PetstoreClient.Client(authenticator);
```

### API Key

```csharp statements
using PetstoreClient.Auth;

var authenticator = new ApiKeyAuthenticator("https://api.example.com", "key-name", "key-value", ApiKeyLocation.Header);
var client = new global::PetstoreClient.Client(authenticator);
```

### OAuth2 Client Credentials

```csharp statements
using PetstoreClient.Auth.OAuth;

var authenticator = new OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    new Uri("https://auth.example.com/token"), []);
var client = new global::PetstoreClient.Client(authenticator);
```

### OAuth2 Authorization Code

```csharp statements
using PetstoreClient.Auth.OAuth;

var authenticator = new OAuth2AuthorizationCodeAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    new Uri("https://auth.example.com/authorize"), new Uri("https://auth.example.com/token"),
    new Uri("https://app.example.com/callback"), []);
var client = new global::PetstoreClient.Client(authenticator);
await authenticator.ExchangeCodeAsync("authorization-code");
```

### OAuth2 Password

```csharp statements
using PetstoreClient.Auth.OAuth;

var authenticator = new OAuth2PasswordAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    new Uri("https://auth.example.com/token"), "username", "password", []);
var client = new global::PetstoreClient.Client(authenticator);
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```csharp statements
using PetstoreClient.Auth.OAuth;

var authenticator = new OAuth2ImplicitAuthenticator(
    "https://api.example.com", "client-id", new Uri("https://auth.example.com/authorize"), []);
authenticator.SetAccessToken("your-access-token");
var client = new global::PetstoreClient.Client(authenticator);
```

### OpenID Connect

```csharp statements
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

```csharp statements
using PetstoreClient.Auth.OAuth;

var authenticator = new OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    new Uri("https://auth.example.com/token"), [], clientAuthMethod: ClientAuthMethod.Basic);
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `Servers` class exposes each as a `ServerConfiguration` field (e.g., `Servers.Server0`, `Servers.Server1`, ...) plus a `Servers.All` collection. Pass the desired server's URL to the client:

```csharp statements
using PetstoreClient;

var client = global::PetstoreClient.Client.WithToken(Servers.Server0.GetUrl(), "your-token");
```

## Testing

The `IAuthenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```csharp file
using PetstoreClient.Auth;

public sealed class FakeAuthenticator : IAuthenticator
{
    public string GetHost() => "https://api.example.com";

    public Dictionary<string, string> GetAuthHeaders() =>
        new() { ["Authorization"] = "Bearer test-token" };

    public static global::PetstoreClient.Client CreateClient() =>
        new(new FakeAuthenticator());
}
```

## Error Handling

Every error the SDK raises derives from `OpenAPIException`, so one `catch (OpenAPIException)` covers them all. The hierarchy is:

- `OpenAPIException` (root)
  - `ApiException` (an HTTP response or transport failure)
    - `ClientException` (4xx)
      - `BadRequestException` (400)
      - `UnauthorizedException` (401)
      - `ForbiddenException` (403)
      - `NotFoundException` (404)
      - `ConflictException` (409)
      - `UnprocessableEntityException` (422)
      - `TooManyRequestsException` (429)
      - `PaymentRequiredException` (402)
      - `MethodNotAllowedException` (405)
      - `RequestTimeoutException` (408)
      - `GoneException` (410)
      - `PayloadTooLargeException` (413)
      - `UnsupportedMediaTypeException` (415)
      - `LockedException` (423)
    - `ServerException` (5xx)
      - `InternalServerErrorException` (500)
      - `NotImplementedStatusException` (501)
      - `BadGatewayException` (502)
      - `ServiceUnavailableException` (503)
      - `GatewayTimeoutException` (504)
    - `NetworkException` (no HTTP response, status 0)
      - `NetworkTimeoutException` (the request timed out, status 0)
  - `SerializationException` (a request or response body that cannot be encoded or decoded)
  - `OAuth2ServerException` (the token endpoint answered with an OAuth2 error)
  - `OAuth2TokenException` (the token endpoint answered with an unusable body)

`SerializationException`, `OAuth2ServerException` and `OAuth2TokenException` are siblings of `ApiException`, not subclasses, so a `catch (ApiException)` alone does not handle them; end with a catch of the root type.

```csharp file
using PetstoreClient.Errors;
using PetstoreClient.Models;
using PetstoreClient.Api.Options;

public static class ErrorHandling
{
    public static async Task AddPetOrReportAsync(global::PetstoreClient.Client client, Pet pet, AddPetOptions? options)
    {
        try
        {
            await client.Pet.AddPetAsync(pet, options);
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
        catch (OpenAPIException e)
        {
            Console.WriteLine($"SDK error: {e.Message}");
        }
    }
}
```

## Configuration

### Custom Transport Options

```csharp file
using PetstoreClient;
using PetstoreClient.Auth;

public static class Transport
{
    public static global::PetstoreClient.Client ClientWithTransport(IAuthenticator authenticator)
    {
        var transport = TransportOptions.Builder()
            .Proxy("http://proxy:3128")
            .Timeout(5000)
            .Build();

        return new global::PetstoreClient.Client(authenticator, transport);
    }
}
```

The client implements `IDisposable`. Use `using` statements or call `Dispose()` when done.

### Retries

`TransportOptions.Builder().MaxRetries(n)` sets the automatic retries (default 2, `0` disables). Retried: a network error before a response and the statuses 408, 409, 429 and 500-599 except 501, after a 0.5 s backoff doubling to 8 s with 25% jitter or the server's `Retry-After` (at most 60 s). The retries never extend the timeout, rebuild the headers on every attempt, and skip stream and multipart bodies. A 401 on an OAuth2 or OpenID Connect call drops the cached access token and replays the request once (`InvalidateAccessToken()` on those authenticators does the same by hand).

### Per-call options

Every operation takes an optional options object (`<Operation>Options`) that also carries `Timeout` (milliseconds), `MaxRetries` and `Headers` for that call only; null keeps the transport's setting, and an operation with no parameters and no security has an options object with just these three. `Client.FromEnvironment()` builds a bearer client from `SWAGGER_PETSTORE_OPEN_API_3_0_HOST` and `SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN` (explicit arguments win, a `tokenProvider` callback replaces the token).

### Credentials from the environment

Each per-scheme authenticator takes optional credential arguments. A value you pass wins, else the environment variable is read once in the constructor (an empty value counts as unset), and a missing credential throws `InvalidOperationException` naming the variable. A bearer authenticator also accepts a `tokenProvider` callback, which wins over the environment.

| Variable | Scheme | Credential |
|---|---|---|
| `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` | `adminBasic` | `username` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` | `adminBasic` | `password` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY` | `apiKeyHeader` | `apiKey` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY` | `apiKeyQuery` | `apiKey` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID` | `browserAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY` | `internalApiKey` | `apiKey` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID` | `legacyAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET` | `legacyAuth` | `clientSecret` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME` | `legacyAuth` | `username` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD` | `legacyAuth` | `password` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID` | `machineAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET` | `machineAuth` | `clientSecret` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME` | `petStoreBasic` | `username` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD` | `petStoreBasic` | `password` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN` | `petStoreBearer` | `token` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN` | `serviceToken` | `token` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY` | `sessionCookie` | `apiKey` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID` | `ssoAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET` | `ssoAuth` | `clientSecret` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI` | `ssoAuth` | `redirectUri` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID` | `userAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET` | `userAuth` | `clientSecret` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI` | `userAuth` | `redirectUri` |

### Cancellation

Every operation takes a trailing `CancellationToken cancellationToken = default`. Cancelling it raises `OperationCanceledException` (not `NetworkTimeoutException`). `TransportOptions.Builder().CancellationToken(token)` adds a client-level token linked with each call's token.

### Custom HTTP transport

`TransportOptions.Builder().HttpClient(httpClient)` or `.HttpMessageHandler(handler)` carries requests on your own transport. SDK defaults (timeout, user agent, default headers, redirect policy) still apply, the SDK never disposes it, and combining it with `Proxy`, `CaCertPath` or `VerifySsl(false)` throws `ArgumentException` in `Build()`.

## API Methods

Each API group is exposed as a typed property on the client (e.g., `client.Pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are asynchronous; call them with `await`.

## Models

Models are generated as immutable C# records under the `PetstoreClient.Models` namespace: properties are `init`-only, and `var next = model with { Property = value };` copies one.

```csharp file
using PetstoreClient.Models;

public static class Copies
{
    public static Account WithSameId(Account model, Account other) =>
        model with { Id = other.Id };
}
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
