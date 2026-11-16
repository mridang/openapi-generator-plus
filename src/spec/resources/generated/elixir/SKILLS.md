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

#### A 401 replays the request once

When an OAuth2 or OpenID Connect call is answered with 401, the SDK drops the cached access token (the refresh token is kept; `invalidate_access_token/1` on the authenticator does the same by hand) and sends the request once more with fresh headers. A second 401 is reported as the usual `UnauthorizedError`. API key, basic and bearer authenticators never replay.

#### PKCE

The Authorization Code and OpenID Connect flows use PKCE with S256 (RFC 7636). `build_authorization_url/2` draws a new 43-character verifier, keeps it, and adds `code_challenge` and `code_challenge_method=S256` to the URL; `exchange_code/2` sends the `code_verifier`. Call `build_authorization_url/2` before `exchange_code/2` with the same authenticator.

#### Token caching

The token manager caches the access token in memory and refreshes it before its declared expiry, by one margin of at most `60` seconds and never more than half of `expires_in` (a 60 second token is reused for 30 seconds). Expiry is measured on the monotonic clock, so a wall clock change does not extend or cut a token's life. A token response without `expires_in` is cached until the API answers 401 or the token is invalidated.

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

If the OpenAPI spec defines multiple servers, the generated `PetstoreClient.Servers` module exposes each as a `server_N/0` function returning a `ServerConfiguration`, plus a `PetstoreClient.Servers.all/0` list. Pass the desired server's URL to the client:

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

Every failure the SDK raises or returns is one of the exceptions listed by `PetstoreClient.Errors.OpenAPIError.exceptions/0`, and `PetstoreClient.Errors.OpenAPIError.open_api_error?/1` is true for each of them: that is the root to catch last. Every error for an HTTP response or a network failure derives from `ApiError`; encoding and decoding failures are `SerializationError`, and the token flows add `OAuth2ServerError` (the token endpoint answered 4xx or 5xx) and `OAuth2TokenError` (it answered unusably). The error hierarchy is:

- `OpenAPIError` (root grouping: `exceptions/0` and `open_api_error?/1`)
  - `SerializationError` (a body could not be encoded or decoded)
  - `OAuth2ServerError` (token endpoint answered 4xx or 5xx)
  - `OAuth2TokenError` (token endpoint answered unusably)
  - `ApiError` (an HTTP response or a network failure)
    - `ClientError` (4xx)
      - `BadRequestError` (400)
      - `UnauthorizedError` (401)
      - `ForbiddenError` (403)
      - `NotFoundError` (404)
      - `ConflictError` (409)
      - `UnprocessableEntityError` (422)
      - `TooManyRequestsError` (429)
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

      PetstoreClient.Errors.OpenAPIError.open_api_error?(e) ->
        IO.puts("SDK error: #{Exception.message(e)}")

      true ->
        IO.puts("Error: #{Exception.message(e)}")
    end
end
```

The text of an `ApiError` (`Exception.message/1`) cuts the response body at 1 KiB with a `... (N more bytes)` suffix, writes control characters as `\xNN`, and masks credential response headers (`Authorization`, `Cookie`, `Set-Cookie`, `WWW-Authenticate` and any name containing key, token or secret). The full body stays on `response_body` and the full headers on `response_headers`.

## Configuration

### Custom Transport Options

```elixir
transport = PetstoreClient.TransportOptions.new(
  proxy: "http://proxy:3128",
  timeout: 5000
)

client = PetstoreClient.Client.new(authenticator, transport)
```

### Retries

`max_retries` on `TransportOptions` is how many times a failed call is tried again (default `2`; `0` turns retries off). A call is retried after a network error that happened before any response, and after a response with status 408, 409, 429 or 500 to 599 other than 501 (a timeout, 400, 401, 403, 404, 422 and 501 are not retried). The wait starts at 0.5 s and doubles up to 8 s, less up to 25% jitter; a `Retry-After` header (seconds or an HTTP date) replaces it, capped at 60 s. Every retry is a fresh request: headers, authentication included, are rebuilt. Retries share the single end-to-end `timeout` and never extend it.

```elixir
transport = PetstoreClient.TransportOptions.new(max_retries: 0)
client = PetstoreClient.Client.new(authenticator, transport)
```

### Custom HTTP client

Pass Req options such as an `:adapter` function or a `:plug` through `req_options`. The SDK's own settings (timeouts, redirect policy, proxy, TLS, user agent) are applied on top, and the SDK never closes anything you supply. Combining `:connect_options` or `:finch` with `proxy`, `ca_cert_path` or `verify_ssl: false` raises `ArgumentError`.

```elixir
transport = PetstoreClient.TransportOptions.new(req_options: [adapter: &MyApp.Http.adapter/1])

client = PetstoreClient.Client.new(authenticator, transport)
```

### Cancellation

There is no cancellation argument. The SDK uses the platform's own mechanism: stop the process that is making the call, or set `timeout` on `TransportOptions`, which ends the call with `NetworkTimeoutError`.

### Closing the client

`PetstoreClient.Client.close/1` releases the transport the client created and stops the processes its authenticator started (OAuth2 token managers, OpenID Connect discovery). It is idempotent, and a call through a closed client raises. A transport you supply through `req_options` is never closed by the SDK.

```elixir
:ok = PetstoreClient.Client.close(client)
```

### Raw responses, timeouts and the request id

Each operation has a `_with_http_info` variant returning an `ApiResult` (`status_code`, `data`, `raw_body`, `headers`). `timeout` on `TransportOptions` is one end-to-end budget in positive milliseconds; a call that exceeds it ends with `NetworkTimeoutError`. With `inject_request_id: true` an `X-Request-ID` header is added to each request unless you set one.

## API Methods

Each API group is exposed as a typed field on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as Elixir structs under the `PetstoreClient.Models` namespace. A property the spec marks required (and that has no default) is an enforced key: the struct cannot be built without it, so the example gives each one; replace `nil` with a value of the field's type.

```elixir
model = %PetstoreClient.Models.Account{
  username: nil,
}
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
