# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add to your `mix.exs`:

```elixir
defp deps do
  [
    {:petstore_client, "~> 1.0.0"}
  ]
end
```

Then run:

```bash
mix deps.get
```

## Quick Start

```elixir
client = PetstoreClient.Client.with_token("https://api.example.com", "your-token")
```

## Authentication

All authentication is handled via authenticator structs passed to the client constructor.

### Bearer Token

```elixir
authenticator = PetstoreClient.Auth.BearerAuthenticator.new("https://api.example.com", "your-token")
client = PetstoreClient.Client.new(authenticator)
```

### Basic Auth

```elixir
authenticator = PetstoreClient.Auth.BasicAuthenticator.new("https://api.example.com", "username", "password")
client = PetstoreClient.Client.new(authenticator)
```

### API Key

```elixir
authenticator = PetstoreClient.Auth.ApiKeyAuthenticator.new(
  "https://api.example.com", "key-name", "key-value", :header)
client = PetstoreClient.Client.new(authenticator)
```

### OAuth2 Client Credentials

```elixir
authenticator = PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.new(
  "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token")
client = PetstoreClient.Client.new(authenticator)
```

### OAuth2 Authorization Code

```elixir
authenticator = PetstoreClient.Auth.OAuth.OAuth2AuthCodeAuthenticator.new(
  "https://api.example.com", "client-id", "client-secret",
  "https://auth.example.com/token", "authorization-code", "https://app.example.com/callback")
client = PetstoreClient.Client.new(authenticator)
```

### OAuth2 Password

```elixir
authenticator = PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
  "https://api.example.com", "client-id", "client-secret",
  "https://auth.example.com/token", "username", "password")
client = PetstoreClient.Client.new(authenticator)
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```elixir
authenticator = PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.new("https://api.example.com", "your-access-token")
client = PetstoreClient.Client.new(authenticator)
```

### OpenID Connect

```elixir
authenticator = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.new(
  "https://api.example.com", "client-id", "client-secret",
  "https://auth.example.com/.well-known/openid-configuration")
client = PetstoreClient.Client.new(authenticator)
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators resolve the access token by making an HTTP call to the token endpoint the first time `auth_headers/1` is invoked (and again whenever the cached token has expired). That token fetch runs synchronously inside `auth_headers/1`, and the generated API functions always resolve the auth headers before sending the request. Because the fetch can perform network I/O and raise, call OAuth-backed operations from a process that can tolerate the blocking call (or wrap them in a `Task`) rather than from a latency-sensitive hot path.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed; tune your authorization server's `expires_in` if it is too tight.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `:body` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `:basic` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```elixir
authenticator = PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.new(
  "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token",
  client_auth_method: :basic)
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `PetstoreClient.Servers` module exposes each as a `server_N/0` function returning a `ServerConfiguration`. Resolve the URL via `ServerConfiguration.url/1`:

```elixir
url = PetstoreClient.Servers.server_0() |> PetstoreClient.ServerConfiguration.url()
client = PetstoreClient.Client.with_token(url, "your-token")
```

## Testing

The authenticator behaviour is the seam for tests: substitute a fake struct that returns a known header map.

```elixir
defmodule FakeAuthenticator do
  defstruct []
  def get_auth_headers(_), do: {:ok, %{"Authorization" => "Bearer test-token"}}
  def host(_), do: "https://api.example.com"
end

client = PetstoreClient.Client.new(%FakeAuthenticator{})
```

## Error Handling

All API errors are represented as exception structs. The error hierarchy is:

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

```elixir
case PetstoreClient.Api.PetApi.get_pet_by_id(client.pet_api, pet_id) do
  {:ok, pet} ->
    IO.inspect(pet)

  {:error, %PetstoreClient.Errors.NotFoundError{} = e} ->
    IO.puts("Not found: #{e.message}")

  {:error, %PetstoreClient.Errors.ClientError{} = e} ->
    IO.puts("Client error #{e.status_code}: #{e.message}")

  {:error, %PetstoreClient.Errors.ServerError{} = e} ->
    IO.puts("Server error: #{e.message}")
end
```

## Configuration

### Custom Transport Options

```elixir
transport = PetstoreClient.TransportOptions.new(
  proxy: "http://proxy:3128",
  timeout: 5000
)

client = PetstoreClient.Client.new(authenticator, transport)
```

## API Methods

Each API group is exposed as a typed field on the client struct (e.g., `client.pet_api`). API modules have functions that correspond to OpenAPI operations, accepting the API struct and typed request parameters and returning `{:ok, result}` or `{:error, reason}` tuples.

## Models

Models are generated as Elixir structs under the `PetstoreClient.Models` namespace.

```elixir
pet = %PetstoreClient.Models.Pet{name: "Fido", status: "available"}
```

## Binary / File Uploads

File upload parameters accept `binary()` data. Binary response bodies are returned as `binary()`.

## Comment Style

Use `#` comments and `@moduledoc`/`@doc` attributes for documentation. Place comments on their own line.

```good
# This explains the logic
x = 1
```

```bad
x = 1  # This explains the logic
```
