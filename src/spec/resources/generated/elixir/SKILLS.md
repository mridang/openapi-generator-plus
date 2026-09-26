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

All authentication is handled via `Authenticator` implementations passed to the client constructor.

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
  "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token", [])
client = PetstoreClient.Client.new(authenticator)
```

### OAuth2 Authorization Code

```elixir
authenticator = PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.new(
  "https://api.example.com", "client-id", "client-secret",
  "https://auth.example.com/authorize", "https://auth.example.com/token",
  "https://app.example.com/callback", [])
client = PetstoreClient.Client.new(authenticator)
```

### OAuth2 Password

```elixir
authenticator = PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
  "https://api.example.com", "client-id", "client-secret",
  "https://auth.example.com/token", "username", "password", [])
client = PetstoreClient.Client.new(authenticator)
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```elixir
authenticator =
  PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.new(
    "https://api.example.com", "client-id", "https://auth.example.com/authorize", [])
  |> PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.set_access_token("your-access-token")
client = PetstoreClient.Client.new(authenticator)
```

### OpenID Connect

```elixir
authenticator = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.new(
  "https://api.example.com", "https://auth.example.com/.well-known/openid-configuration",
  "client-id", "client-secret", "https://app.example.com/callback", [])
client = PetstoreClient.Client.new(authenticator)
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

- `:body` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `:basic` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```elixir
authenticator = PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.new(
  "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token", [],
  client_auth_method: :basic)
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `PetstoreClient.Servers` module exposes each as a `server_N/0` function returning a `ServerConfiguration`. Pass the desired server's URL to the client:

```elixir
url = PetstoreClient.Servers.server_0() |> PetstoreClient.ServerConfiguration.url()
client = PetstoreClient.Client.with_token(url, "your-token")
```

## Testing

The `Authenticator` behaviour is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```elixir
defmodule FakeAuthenticator do
  @behaviour PetstoreClient.Auth.Authenticator

  defstruct []

  @impl true
  def host(_), do: "https://api.example.com"

  @impl true
  def auth_headers(_), do: %{"Authorization" => "Bearer test-token"}

  @impl true
  def query_params(_), do: %{}

  @impl true
  def cookie_params(_), do: %{}
end

client = PetstoreClient.Client.new(%FakeAuthenticator{})
```

## Error Handling

All API errors derive from `ApiError`. The error hierarchy is:

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
  - `NetworkError` (no HTTP response, status 0)
    - `NetworkTimeoutError` (the request timed out, status 0)

```elixir
case PetstoreClient.Api.PetApi.add_pet(client.pet, pet) do
  {:ok, result} ->
    IO.inspect(result)

  {:error, %PetstoreClient.Errors.NotFoundError{} = e} ->
    IO.puts("Not found: #{e.message}")

  # Elixir has no inheritance: each error in the hierarchy above has a
  # predicate that is true for it and for every error below it.
  {:error, e} ->
    cond do
      PetstoreClient.Errors.ClientError.client_error?(e) ->
        IO.puts("Client error #{e.status_code}: #{e.message}")

      PetstoreClient.Errors.ServerError.server_error?(e) ->
        IO.puts("Server error: #{e.message}")

      true ->
        IO.puts("Error: #{Exception.message(e)}")
    end
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

Each API group is exposed as a typed field on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as Elixir structs under the `PetstoreClient.Models` namespace.

```elixir
model = %PetstoreClient.Models.ApiResponse{}
```

## Binary / File Uploads

File upload parameters are typed as `binary()`. Binary response bodies are returned as `binary()`.

## Comment Style

Never place a comment on the same line as code. Use `#` comments; `@moduledoc` and `@doc` are fine.

```good
# This explains the logic
x = 1
```

```bad
x = 1  # This explains the logic
```
