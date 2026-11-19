# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add the gem to your `Gemfile` and install it with Bundler:

```bash
bundle add petstore_client
```

## Quick Start

```ruby statements
require 'petstore_client'

host = ENV.fetch('SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL')
token = ENV.fetch('SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN')
client = Petstore::Client::Petstore.with_token(host, token)
begin
  result = client.store.get_defaults
  pp result
ensure
  client.close
end
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```ruby statements
require 'petstore_client'

authenticator = Petstore::Client::Auth::BearerAuthenticator.new('https://api.example.com', 'your-token')
client = Petstore::Client::Petstore.new(authenticator)
```

### Credentials per security scheme

`Petstore.new` takes one optional credential for each security scheme of the API, named after the scheme: `pet_store_basic:`, `admin_basic:`, `pet_store_bearer:`, `service_token:`, `api_key_header:`, `internal_api_key:`, `api_key_query:`, `session_cookie:`, `machine_auth:`, `legacy_auth:`, `user_auth:`, `browser_auth:`, `sso_auth:`. Each operation uses the credential of the first scheme in its own security requirement that you configured; there is no per-call override. An optional `authenticator` (the first argument) serves any operation no scheme credential matches, and an operation that declares no security sends no credential of its own. The client is refused, with an `ArgumentError`, when it gets no credential at all.

```ruby file
require 'petstore_client'

# credential is the Auth::Authenticator you built for that scheme
def client_with_scheme_credential(credential)
  Petstore::Client::Petstore.new(nil, nil, pet_store_basic: credential)
end
```

### Credentials from the environment

A credential you leave out is read, once, when the authenticator is built, from the environment variable of its security scheme; an empty variable counts as unset. The value you pass wins, and a Bearer token falls back to `token_provider:` (called for every request) when neither is set. A credential still missing raises `ArgumentError` before the client is built.

| Security scheme | Credential | Environment variable |
|---|---|---|
| `adminBasic` | `USERNAME` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` |
| `adminBasic` | `PASSWORD` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` |
| `apiKeyHeader` | `API_KEY` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY` |
| `apiKeyQuery` | `API_KEY` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY` |
| `browserAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID` |
| `internalApiKey` | `API_KEY` | `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY` |
| `legacyAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID` |
| `legacyAuth` | `CLIENT_SECRET` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET` |
| `legacyAuth` | `USERNAME` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME` |
| `legacyAuth` | `PASSWORD` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD` |
| `machineAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID` |
| `machineAuth` | `CLIENT_SECRET` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET` |
| `petStoreBasic` | `USERNAME` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME` |
| `petStoreBasic` | `PASSWORD` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD` |
| `petStoreBearer` | `TOKEN` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN` |
| `serviceToken` | `TOKEN` | `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN` |
| `sessionCookie` | `API_KEY` | `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY` |
| `ssoAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID` |
| `ssoAuth` | `CLIENT_SECRET` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET` |
| `ssoAuth` | `REDIRECT_URI` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI` |
| `userAuth` | `CLIENT_ID` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID` |
| `userAuth` | `CLIENT_SECRET` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET` |
| `userAuth` | `REDIRECT_URI` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI` |

```ruby statements
require 'petstore_client'

authenticator = Petstore::Client::Auth::BearerAuthenticator.new('https://api.example.com')
client = Petstore::Client::Petstore.new(authenticator)
```

### Basic Auth

```ruby statements
require 'petstore_client'

authenticator = Petstore::Client::Auth::BasicAuthenticator.new('https://api.example.com', 'username', 'password')
client = Petstore::Client::Petstore.new(authenticator)
```

### API Key

```ruby statements
require 'petstore_client'

authenticator = Petstore::Client::Auth::ApiKeyAuthenticator.new(
  'https://api.example.com', 'key-name', 'key-value', Petstore::Client::Auth::ApiKeyLocation::HEADER)
client = Petstore::Client::Petstore.new(authenticator)
```

### OAuth2 Client Credentials

```ruby statements
require 'petstore_client'

authenticator = Petstore::Client::Auth::OAuth::OAuth2ClientCredentialsAuthenticator.new(
  'https://api.example.com', 'client-id', 'client-secret', 'https://auth.example.com/token', ['read'])
client = Petstore::Client::Petstore.new(authenticator)
```

### OAuth2 Authorization Code

```ruby statements
require 'petstore_client'

authenticator = Petstore::Client::Auth::OAuth::OAuth2AuthorizationCodeAuthenticator.new(
  'https://api.example.com', 'client-id', 'client-secret',
  'https://auth.example.com/authorize', 'https://auth.example.com/token',
  'https://app.example.com/callback', ['read'])
client = Petstore::Client::Petstore.new(authenticator)
```

`build_authorization_url` adds a PKCE `S256` challenge (RFC 7636); the authenticator keeps its 43-character verifier and `exchange_code` sends it as `code_verifier`.

### OAuth2 Password

```ruby statements
require 'petstore_client'

authenticator = Petstore::Client::Auth::OAuth::OAuth2PasswordAuthenticator.new(
  'https://api.example.com', 'client-id', 'client-secret',
  'https://auth.example.com/token', 'username', 'password', ['read'])
client = Petstore::Client::Petstore.new(authenticator)
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```ruby statements
require 'petstore_client'

authenticator = Petstore::Client::Auth::OAuth::OAuth2ImplicitAuthenticator.new(
  'https://api.example.com', 'client-id', 'https://auth.example.com/authorize', ['read']
)
authenticator.access_token = 'your-access-token'
client = Petstore::Client::Petstore.new(authenticator)
```

### OpenID Connect

```ruby statements
require 'petstore_client'

authenticator = Petstore::Client::Auth::OAuth::OpenIdConnectAuthenticator.new(
  'https://api.example.com', 'https://auth.example.com/.well-known/openid-configuration',
  'client-id', 'client-secret', 'https://app.example.com/callback', ['openid'])
client = Petstore::Client::Petstore.new(authenticator)
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators resolve the access token by making an HTTP call to the token endpoint, which happens before the request carrying it is sent.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Expired or revoked tokens

When an API call answers `401` the cached access token is dropped (`invalidate_access_token`; the refresh token is kept), a new one is obtained and the request is sent once more. A second `401` raises `UnauthorizedError`. A multipart or stream body is not sent again, and API key, basic and bearer authenticators never replay.

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed; tune your authorization server's `expires_in` if it is too tight.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethod::BODY` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod::BASIC` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```ruby statements
require 'petstore_client'

authenticator = Petstore::Client::Auth::OAuth::OAuth2ClientCredentialsAuthenticator.new(
  'https://api.example.com', 'client-id', 'client-secret', 'https://auth.example.com/token', ['read'],
  client_auth_method: Petstore::Client::Auth::OAuth::ClientAuthMethod::BASIC)
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `Petstore::Client::Servers` module exposes each as a `ServerConfiguration` constant (e.g., `SERVER_0`, `SERVER_1`, ...) plus an `ALL` array. Pass the desired server's URL to the client:

```ruby statements
require 'petstore_client'

client = Petstore::Client::Petstore.with_token(Petstore::Client::Servers::SERVER_0.url, 'your-token')
```

## Testing

The `Authenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```ruby statements
require 'petstore_client'

fake_authenticator = Class.new do
  def host = 'https://api.example.com'
  def auth_headers = { 'Authorization' => 'Bearer test-token' }
  def query_params = {}
  def cookie_params = {}
end.new

client = Petstore::Client::Petstore.new(fake_authenticator)
```

## Error Handling

Every error the SDK raises derives from `::Petstore::Client::Errors::OpenAPIError`, so one `rescue ::Petstore::Client::Errors::OpenAPIError` catches them all. An HTTP failure is an `ApiError`; a body that cannot be encoded or decoded is a `SerializationError`; an OAuth2 token request that failed or answered unusably is an `OAuth2ServerError` or `OAuth2TokenError`. The error hierarchy is:

- `::Petstore::Client::Errors::OpenAPIError` (root)
  - `ApiError` (an HTTP response or a transport failure)
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
  - `SerializationError` (a request or response body could not be encoded or decoded)
  - `OAuth2ServerError` (the token endpoint answered with an error status)
  - `OAuth2TokenError` (the token response was unusable)

```ruby file
require 'petstore_client'

def add_pet_or_report(client, pet)
  client.pet.add_pet(pet)
rescue Petstore::Client::Errors::NotFoundError => e
  puts "Not found: #{e.message}"
rescue Petstore::Client::Errors::ClientError => e
  puts "Client error #{e.status_code}: #{e.message}"
rescue Petstore::Client::Errors::ServerError => e
  puts "Server error: #{e.message}"
rescue Petstore::Client::Errors::ApiError => e
  puts "API error: #{e.message}"
rescue Petstore::Client::Errors::SerializationError => e
  puts "Serialization error: #{e.message}"
rescue ::Petstore::Client::Errors::OpenAPIError => e
  puts "SDK error: #{e.message}"
end
```

## Configuration

### Custom Transport Options

```ruby file
require 'petstore_client'

def client_with_transport(authenticator)
  transport = Petstore::Client::TransportOptions.builder
    .proxy('http://proxy:3128')
    .timeout(5000)
    .build

  Petstore::Client::Petstore.new(authenticator, transport)
end
```

### Custom HTTP client

Supply your own `Faraday::Connection` to control the adapter or middleware. The SDK still applies its timeouts, user agent, redirect policy and query encoding on top, and never closes a connection you supplied. Combining it with `proxy`, `ca_cert_path` or `verify_ssl(false)` raises `ArgumentError`; configure those on the connection instead.

```ruby file
require 'petstore_client'

def client_with_connection(authenticator)
  connection = Faraday.new { |f| f.adapter :net_http }
  transport = Petstore::Client::TransportOptions.builder.connection(connection).build

  Petstore::Client::Petstore.new(authenticator, transport)
end
```

### Retries

`max_retries` (default `2`; `0` disables) sets how many times a failed request is sent again: after a network error before any response, and after a `408`, `409`, `429` or `5xx` (not `501`) status. The wait is 0.5 seconds doubling to 8 with up to 25% jitter, or the `Retry-After` header (at most 60 seconds). Retries never extend the `timeout` budget, a TLS failure is not retried, and a multipart or stream body is never sent twice.

```ruby statements
require 'petstore_client'

transport = Petstore::Client::TransportOptions.builder.max_retries(0).build
```

### Per-call options

Every operation takes an optional Options object whose `timeout` (milliseconds), `headers` and `max_retries` apply to that call alone; a member left `nil` keeps the client's value. An operation with no query, header, form or cookie parameter and no security still has one, carrying only these three members.

```ruby file
require 'petstore_client'

def get_defaults_within_two_seconds(client)
  options = Petstore::Client::Api::Options::GetDefaultsOptions.new(timeout: 2_000, max_retries: 0)
  client.store.get_defaults(options)
end
```

### Hooks and telemetry

Every operation calls one small, optional hook around the call. The hook can edit the request headers before the request is sent and observe the status or the error afterwards, and without a hook set the call does nothing extra.

A hook is any object that responds to `start(info, headers)` and returns an observer that responds to `finish(outcome)`, or `nil`. `info` is a `CallInfo` (`operation_id`, `http_method`, `url_template`, `url`, `server_address`), `headers` is the final mutable request header Hash and `outcome` is a `CallOutcome` (`status_code`, `headers`, `error`). `start` runs once per call, `finish` exactly once on every way the call can end, an error raised by `start` reaches the caller and an error raised by `finish` is swallowed. `CallHook.chain(first, second)` runs several hooks in order.

```ruby
class TenantHook
  def start(_info, headers)
    headers['X-Tenant'] = 'a'
    nil
  end
end

client = Petstore::Client::Petstore.with_token('/api/v3', 'token', call_hook: TenantHook.new)
```

For OpenTelemetry the SDK ships no adapter, and the README shows a `TelemetryCallHook` recipe that records one span, one request count and one duration per call.

### Closing the client

`client.close` is graceful and idempotent. The client is marked closed at once and a request started afterwards raises a `RuntimeError`; a request already in flight finishes, and the connection the SDK built is released when the last one completes. A second `close` does nothing, and a `Faraday::Connection` you supplied is never closed.

### Cookie parameters

A cookie parameter is sent as it is, without percent-encoding, when every character of its value is a legal RFC 6265 cookie-octet (printable ASCII except space, double quote, comma, semicolon and backslash). Any other value raises an `ArgumentError` before the request is sent, so encode the value yourself first if it holds one of those characters.

### Cancellation

The SDK has no cancellation API of its own. Ruby's native mechanisms (`Timeout`, killing the thread) apply; the request timeout is the only bound the SDK enforces.

### Timeouts

`timeout` is in milliseconds and is one end-to-end budget for the whole call, redirects and retries included. A non-positive value raises `ArgumentError`; an exceeded budget raises `NetworkTimeoutError`.

### Request id

With `inject_request_id` on (the default) every request carries an `X-Request-ID` header unless you set one yourself.

## API Methods

Each API group is exposed as a typed attribute on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models. Every operation also has a `<operation>_with_http_info` variant that returns an `ApiResult` carrying `status_code`, `headers` (frozen, lower-cased names) and the typed `data`.

## Models

Models are generated as Ruby classes under the `Petstore::Client::Models` namespace.

```ruby statements
require 'petstore_client'

model = Petstore::Client::Models::Account.new(username: 'example')
model.username
```

Required attributes must be passed to `new`; a missing one raises `Dry::Struct::Error`.

## Binary / File Uploads

File upload parameters are typed as `IO`. A `format: binary` response is returned as a `StringIO` over the response bytes.

## Comment Style

Never place a comment on the same line as code. Use `#` comments.

```good
# This explains the logic
x = 1
```

```bad
x = 1  # This explains the logic
```
