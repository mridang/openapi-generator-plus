# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add to your `mix.exs`:

```text
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

```elixir statements
host = System.fetch_env!("SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL")
token = System.fetch_env!("SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN")
client = PetstoreClient.Client.with_token(host, token)

try do
  {:ok, result} =
    PetstoreClient.Api.StoreApi.get_defaults(client.store)

  IO.inspect(result)
after
  :ok = PetstoreClient.Client.close(client)
end
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```elixir file
defmodule Authentication do
  def client do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("https://api.example.com", "your-token")

    PetstoreClient.Client.new(authenticator)
  end
end
```

### Basic Auth

```elixir file
defmodule Authentication do
  def client do
    authenticator = PetstoreClient.Auth.BasicAuthenticator.new("https://api.example.com", "username", "password")

    PetstoreClient.Client.new(authenticator)
  end
end
```

### API Key

```elixir file
defmodule Authentication do
  def client do
    authenticator = PetstoreClient.Auth.ApiKeyAuthenticator.new(
      "https://api.example.com", "key-name", "key-value", :header)

    PetstoreClient.Client.new(authenticator)
  end
end
```

### OAuth2 Client Credentials

```elixir file
defmodule Authentication do
  def client do
    authenticator = PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.new(
      "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token", [])

    PetstoreClient.Client.new(authenticator)
  end
end
```

### OAuth2 Authorization Code

```elixir file
defmodule Authentication do
  def client do
    authenticator = PetstoreClient.Auth.OAuth.OAuth2AuthorizationCodeAuthenticator.new(
      "https://api.example.com", "client-id", "client-secret",
      "https://auth.example.com/authorize", "https://auth.example.com/token",
      "https://app.example.com/callback", [])

    PetstoreClient.Client.new(authenticator)
  end
end
```

### OAuth2 Password

```elixir file
defmodule Authentication do
  def client do
    authenticator = PetstoreClient.Auth.OAuth.OAuth2PasswordAuthenticator.new(
      "https://api.example.com", "client-id", "client-secret",
      "https://auth.example.com/token", "username", "password", [])

    PetstoreClient.Client.new(authenticator)
  end
end
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```elixir file
defmodule Authentication do
  def client do
    authenticator =
      PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.new(
        "https://api.example.com", "client-id", "https://auth.example.com/authorize", [])
      |> PetstoreClient.Auth.OAuth.OAuth2ImplicitAuthenticator.set_access_token("your-access-token")

    PetstoreClient.Client.new(authenticator)
  end
end
```

### OpenID Connect

```elixir file
defmodule Authentication do
  def client do
    authenticator = PetstoreClient.Auth.OAuth.OpenIdConnectAuthenticator.new(
      "https://api.example.com", "https://auth.example.com/.well-known/openid-configuration",
      "client-id", "client-secret", "https://app.example.com/callback", [])

    PetstoreClient.Client.new(authenticator)
  end
end
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

```elixir file
defmodule BasicClientAuthentication do
  def authenticator do
    PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.new(
      "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token", [],
      client_auth_method: :basic)
  end
end
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `PetstoreClient.Servers` module exposes each as a `server_N/0` function returning a `ServerConfiguration`, plus a `PetstoreClient.Servers.all/0` list. Pass the desired server's URL to the client:

```elixir file
defmodule ServerClient do
  def client do
    url = PetstoreClient.Servers.server_0() |> PetstoreClient.ServerConfiguration.url()
    PetstoreClient.Client.with_token(url, "your-token")
  end
end
```

## Testing

The `Authenticator` behaviour is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```elixir file
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

defmodule FakeClient do
  def client, do: PetstoreClient.Client.new(%FakeAuthenticator{})
end
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
      - `PaymentRequiredError` (402)
      - `MethodNotAllowedError` (405)
      - `RequestTimeoutError` (408)
      - `GoneError` (410)
      - `PayloadTooLargeError` (413)
      - `UnsupportedMediaTypeError` (415)
      - `LockedError` (423)
    - `ServerError` (5xx)
      - `InternalServerError` (500)
      - `NotImplementedError` (501)
      - `BadGatewayError` (502)
      - `ServiceUnavailableError` (503)
      - `GatewayTimeoutError` (504)
    - `NetworkError` (no HTTP response, status 0)
      - `NetworkTimeoutError` (the request timed out, status 0)

```elixir file
defmodule ErrorHandling do
  def add_pet_or_report(client, pet, options \\ nil) do
    case PetstoreClient.Api.PetApi.add_pet(client.pet, pet, options) do
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
  end
end
```

An error that answers a request carries `request_method` (`"GET"`) and `request_path` (the path without the query string); its message has a `Request: GET /path` line after the status line.

The text of an `ApiError` (`Exception.message/1`) cuts the response body at 1 KiB with a `... (N more bytes)` suffix, writes control characters as `\xNN`, and masks credential response headers (`Authorization`, `Cookie`, `Set-Cookie`, `WWW-Authenticate` and any name containing key, token or secret). The full body stays on `response_body` and the full headers on `response_headers`.

## Configuration

### Custom Transport Options

```elixir file
defmodule ProxiedClient do
  def client do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("https://api.example.com", "your-token")

    transport =
      PetstoreClient.TransportOptions.new(
        proxy: "http://proxy:3128",
        timeout: 5000
      )

    PetstoreClient.Client.new(authenticator, transport)
  end
end
```

### Retries

`max_retries` on `TransportOptions` is how many times a failed call is tried again (default `2`; `0` turns retries off). A call is retried after a network error that happened before any response, and after a response with status 408, 409, 429 or 500 to 599 other than 501 (a timeout, 400, 401, 403, 404, 422 and 501 are not retried). The wait starts at 0.5 s and doubles up to 8 s, less up to 25% jitter; a `Retry-After` header (seconds or an HTTP date) replaces it, capped at 60 s. Every retry is a fresh request: headers, authentication included, are rebuilt. Retries share the single end-to-end `timeout` and never extend it.

```elixir file
defmodule NoRetriesClient do
  def client do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("https://api.example.com", "your-token")
    transport = PetstoreClient.TransportOptions.new(max_retries: 0)
    PetstoreClient.Client.new(authenticator, transport)
  end
end
```

### Custom HTTP client

Pass Req options such as an `:adapter` function or a `:plug` through `req_options`. The SDK's own settings (timeouts, redirect policy, proxy, TLS, user agent) are applied on top, and the SDK never closes anything you supply. Combining `:connect_options` or `:finch` with `proxy`, `ca_cert_path` or `verify_ssl: false` raises `ArgumentError`.

```elixir file
defmodule AdapterClient do
  def client do
    authenticator = PetstoreClient.Auth.BearerAuthenticator.new("https://api.example.com", "your-token")
    adapter = fn request -> {request, Req.Response.new(status: 200)} end
    transport = PetstoreClient.TransportOptions.new(req_options: [adapter: adapter])
    PetstoreClient.Client.new(authenticator, transport)
  end
end
```

### Per-call options

Every operation takes an optional options struct, even one with no query, header, form or cookie parameter. It carries `timeout` (milliseconds), `headers` (a map) and `max_retries`; there is no per-call credential. The same three settings can be passed as `:timeout`, `:headers` and `:max_retries` in the trailing `opts` keyword list, which wins over the struct. Each replaces the client's own setting for that one call.

```elixir file
defmodule PerCall do
  def call(client) do
    options = %PetstoreClient.Api.Options.GetDefaultsOptions{timeout: 5_000, headers: %{"X-Trace" => "1"}, max_retries: 0}
    PetstoreClient.Api.StoreApi.get_defaults(client.store, options)
    PetstoreClient.Api.StoreApi.get_defaults(client.store, nil, timeout: 5_000)
  end
end
```

### Credentials

The client takes one optional credential for each security scheme, named after the scheme, in the keyword list that is its third argument: `pet_store_basic`, `admin_basic`, `pet_store_bearer`, `service_token`, `api_key_header`, `internal_api_key`, `api_key_query`, `session_cookie`, `machine_auth`, `legacy_auth`, `user_auth`, `browser_auth`, `sso_auth`. An operation uses the credential of the first scheme its own security requirement lists that the client holds; the authenticator in the first argument serves an operation that none of them matches, and an operation declared `security: []` sends no credential. There is no per-call override, and the client is refused when it gets no credential at all.

```elixir file
defmodule SchemeCredential do
  def client(credential) do
    PetstoreClient.Client.new(nil, nil, pet_store_basic: credential)
  end
end
```

Pass `nil` for a credential (or leave it out of a security scheme's authenticator) and the authenticator reads it once, when it is built, from an environment variable. A value you pass wins, an empty variable counts as unset, and a missing required credential raises `ArgumentError` when the client is built. A bearer token or an API key may be a zero-argument function that returns it, called for every request.

Each security scheme reads its own variables, named `SWAGGER_PETSTORE_OPEN_API_3_0_<FIELD>`, or `SWAGGER_PETSTORE_OPEN_API_3_0_<SCHEME>_<FIELD>` for every scheme that shares a field name with another:

| Security scheme | Credential | Environment variable |
|---|---|---|
| `adminBasic` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` |
| `adminBasic` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` |
| `apiKeyHeader` | `api_key` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY` |
| `apiKeyQuery` | `api_key` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY` |
| `browserAuth` | `client_id` | `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID` |
| `internalApiKey` | `api_key` | `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY` |
| `legacyAuth` | `client_id` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID` |
| `legacyAuth` | `client_secret` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET` |
| `legacyAuth` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME` |
| `legacyAuth` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD` |
| `machineAuth` | `client_id` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID` |
| `machineAuth` | `client_secret` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET` |
| `petStoreBasic` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME` |
| `petStoreBasic` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD` |
| `petStoreBearer` | `token` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN` |
| `serviceToken` | `token` | `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN` |
| `sessionCookie` | `api_key` | `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY` |
| `ssoAuth` | `client_id` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID` |
| `ssoAuth` | `client_secret` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET` |
| `ssoAuth` | `redirect_uri` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI` |
| `userAuth` | `client_id` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID` |
| `userAuth` | `client_secret` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET` |
| `userAuth` | `redirect_uri` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI` |

An authenticator you build without a scheme (`BearerAuthenticator`, `BasicAuthenticator`, `ApiKeyAuthenticator`) reads `SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN`, `SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_PASSWORD` or `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY`. Set `envVarPrefix` when generating to change the `SWAGGER_PETSTORE_OPEN_API_3_0` prefix.

```elixir file
defmodule RotatingToken do
  def authenticator do
    PetstoreClient.Auth.BearerAuthenticator.new("https://api.example.com", fn -> System.fetch_env!("SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN") end)
  end
end
```

### Cancellation

There is no cancellation argument. The SDK uses the platform's own mechanism: stop the process that is making the call, or set `timeout` on `TransportOptions`, which ends the call with `NetworkTimeoutError`.

### Hooks and telemetry

Every operation calls one small, optional hook around the call. The hook can edit the request headers before the request is sent and observe the status or the error afterwards, and without a hook set the call does nothing extra.

A hook is a module that implements the `PetstoreClient.CallHook` behaviour, or `{module, opts}`. `start(info, headers)` returns the headers to send and a state, or `nil`. `info` is a `CallInfo` (`operation_id`, `method`, `url_template`, `url`, `server_address`), `headers` is the final request header map and `outcome` is a `CallOutcome` (`status_code`, `headers`, `error`). `start` runs once per call, `finish(state, outcome)` exactly once on every way the call can end, an error raised by `start` reaches the caller and an error raised by `finish` is swallowed. `CallHook.chain([first, second])` runs several hooks in order.

```elixir file
defmodule TenantHook do
  @behaviour PetstoreClient.CallHook

  @impl true
  def start(_info, headers), do: {Map.put(headers, "X-Tenant", "a"), nil}

  @impl true
  def finish(_state, _outcome), do: :ok
end

defmodule TenantClient do
  def build do
    PetstoreClient.Client.with_token("https://api.example.com", "token", nil, call_hook: TenantHook)
  end
end
```

For OpenTelemetry the SDK ships no adapter, and the README shows a `TelemetryCallHook` recipe that records one span, one request count and one duration per call.

### Closing the client

`PetstoreClient.Client.close/1` closes the client and is idempotent. A call already in flight finishes, and a call that starts after it raises `RuntimeError` ("ApiClient has been closed and can no longer send requests"). The processes the SDK started (OAuth2 token managers, OpenID Connect discovery) are stopped when the last call in flight is over, or at once when none is. A transport you supply through `req_options` is never closed by the SDK.

```elixir file
defmodule Closing do
  def close(client), do: :ok = PetstoreClient.Client.close(client)
end
```

### Cookie parameters

A cookie parameter is sent raw when every character is a legal RFC 6265 cookie-octet (printable ASCII except space, double quote, comma, semicolon and backslash). Any other character raises `ArgumentError` before the request is sent: percent-encode the value yourself first.

### Raw responses, timeouts and the request id

Each operation has a `_with_http_info` variant returning an `ApiResult` (`status_code`, `data`, `raw_body`, `headers`). `timeout` on `TransportOptions` is one end-to-end budget in positive milliseconds; a call that exceeds it ends with `NetworkTimeoutError`. With `inject_request_id: true` an `X-Request-ID` header is added to each request unless you set one.

## API Methods

Each API group is exposed as a typed field on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as Elixir structs under the `PetstoreClient.Models` namespace. A property the spec marks required (and that has no default) is an enforced key: the struct cannot be built without it.

```elixir file
defmodule ModelCopy do
  def copy_of(%PetstoreClient.Models.Account{} = model) do
    %{model | id: model.id}
  end
end
```

Models never change in place. Make a changed copy with the struct update syntax: `%{model | name: "new"}`.

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
