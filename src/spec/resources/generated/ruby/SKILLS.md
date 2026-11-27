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

When an API call answers `401` the cached access token is dropped (`invalidate_access_token`; the refresh token is kept), a new one is obtained and the request is sent once more. A second `401` raises `UnauthorizedError`. A multipart or single-use body is not sent again, and API key, basic and bearer authenticators never replay.

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

### Streaming

An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns a `ByteStream` instead of the whole body in memory, so a large download never has to fit in memory. The call returns as soon as the response headers arrive, and the body is read as you consume it. A `ByteStream` has the status code, the lower-cased headers and an idempotent `close` that aborts the connection instead of draining it, so close it when you stop early (`ensure`). `read_all` reads the rest into memory and leaves the stream closed.

```ruby file
require 'petstore_client'

def save_to(client, pet_id, document_id, options, path)
  stream = client.pet.download_pet_document(pet_id, document_id, options)
  puts stream.status_code
  File.open(path, 'wb') { |file| stream.each { |chunk| file.write(chunk) } }
ensure
  stream&.close
end
```

The call `timeout` covers the wait for the response headers and stops there. `idle_timeout` (in `TransportOptions`, default 60 seconds, `0` or `nil` disables it) bounds the gap between two reads of the body and raises `NetworkTimeoutError`. Retries, redirects, token refresh and the error contract apply up to the headers exactly as for a buffered call; a request is never retried after the first body byte, and a non-2xx status raises the usual typed error with its body read up to 64 KiB and never returns a stream. A connection reset, or a body that ends before its declared `Content-Length`, raises `StreamError`, a `NetworkError` with status 0. `client.close` cancels the streams that are still open, and their next read raises the closed-client error. A custom transport that does not implement `send_streaming` fails a stream call with `NotImplementedError` naming it, and its buffered calls keep working.

An operation whose request body is raw binary (`type: string, format: binary`) takes a `BodySource` instead of buffering the bytes, so a large upload never has to fit in memory. A `BodySource` is replayable, made with `BodySource.of(bytes)`, `BodySource.of_file(path)` or `BodySource.from_factory(factory)` (a factory that opens a fresh stream each time), or single use, made with `BodySource.once(stream)`. The body is sent as it is read: a known length (bytes, a file, or a length you pass) goes out as `Content-Length` and an unknown length is sent chunked. A replayable body is read again for a retry after a 5xx, a 429 or a network error and for a 307 or 308 redirect. A single-use body is never retried, so the original error is raised unchanged, and a 307 or 308 is refused with `Refusing to replay a streamed request body`; it is never buffered to make a replay possible. The SDK never closes a stream you pass to `once` and closes the streams it opens itself from a file or a factory. Multipart bodies stay buffered. A 401 that refreshes the token follows the same rule, and an event stream request with a single-use body is never reconnected.

An operation whose success response is JSON Lines (`application/x-ndjson`, `application/jsonl`, `application/jsonlines`) or server-sent events (`text/event-stream`) has no buffered method. It has a `<operation>_stream` method that takes the operation's parameters, its options object and a trailing `StreamOptions`, and returns a `JsonLinesStream` of the schema type or an `SseStream` of `ServerSentEvent` (`id`, `event`, `data`, `retry` and the raw `data` text; the `data` is decoded as the schema of the payload, or is a string). Iterate it, and close it when you stop early. A JSON Lines line that does not decode, and a line or event longer than `max_event_bytes` (1 MiB), raise `SerializationError` and end the stream.

```ruby file
require 'petstore_client'

def read_feed(client, options)
  feed = client.store.export_orders_stream(options)
  feed.each { |item| puts item }
ensure
  feed&.close
end
```

```ruby file
require 'petstore_client'

def read_events(client, options)
  events = client.store.watch_notices_stream(options)
  events.each { |event| puts event.data }
ensure
  events&.close
end
```

An event stream reconnects when the connection ends, fails or stays silent for the idle timeout: it asks again with fresh headers and `Last-Event-ID`, waits the server's `retry` value or `reconnect_delay` (1 second, doubling to 30 seconds with jitter), and raises the last failure after `max_reconnects` failures in a row without an event (5; 0 for a request with a body; `-1` is unlimited). It never reconnects after a 204, a status other than 408, 429 and 5xx, a response that is not `text/event-stream`, a cancellation or `client.close`. An event named `error` raises `ApiError` with the response status and the data as its body, and a comment line such as a heartbeat counts as activity for the idle timeout. The generator option `sseTerminator` names a data value (such as `[DONE]`) that ends the stream and is not delivered. `StreamOptions` also takes `idle_timeout` and `last_event_id`.

### Pagination

An operation that returns a list one page at a time has a `<operation>_pages` method next to its plain method. It takes the same parameters in the same order and returns a `Paginator`, an `Enumerable` of the item type. The method sends no request: the first request goes out when you first read an item or a page, and each next request goes out only when you ask for more, so stopping early sends nothing further and leaves nothing to close. Iterate the paginator for the items, call `items` for an `Enumerator` of items, or call `pages` for an `Enumerator` of `Page` values with `items`, `next_cursor` and `result`, the same `ApiResult` that the `_with_http_info` variant returns, so the status and headers stay reachable. Iterating again starts again from the starting position.

```ruby file
require 'petstore_client'

def read_everything(client, options)
  list = client.pet.list_pet_catalog_pages(options)
  list.each { |item| puts item }
  list.pages.each { |page| puts "#{page.items.size} items, status #{page.result.status_code}" }
end
```

The cursor you pass to the method is the starting position, and the next cursor of a page resumes the list: pass it as the starting cursor of a new call. The page size and every other parameter, option and header are repeated unchanged on each page request, and each page is an ordinary call with its own retries, timeout, redirects, authentication and redaction. An error on a later page is raised from the read that needed it, after the items of the earlier pages were delivered, and ends the iteration; the SDK never skips a page. A list ends when the next cursor is absent, `nil` or empty, and an empty page with a next cursor continues. A page that returns the cursor that was just sent raises `PaginationError`, a direct child of `OpenAPIError`, after its items were delivered, instead of looping. There is no total limit and no collect-all: use `first(n)`.

How each style ends and resumes: an id list passes the `last_id` or `next_cursor` of each page as the starting cursor of the next request and ends when a declared `has_more` is false. An offset list starts at the offset you pass, sends the offset past the items it received on each next request, and ends on an empty page or when a declared `has_more` is false. A page-number list sends the page you pass, or none, and then asks for the page after each one, and ends on an empty page or when a declared `has_more` is false. A next URL list takes the URL in the body of each page, follows it only within the origin of the client, and ends when the property is absent, `null` or empty. A cursor, offset or page that does not advance raises `PaginationError` as a token cursor does.

A list that is followed by a `Link` header (`rel="next"`, RFC 8288) takes the next URL from the header of each response. A next URL that leaves the scheme, host or port of the client is refused with `PaginationError` and nothing, no credentials either, is sent to it; such a method has no starting cursor, so it cannot resume.

A list whose page body carries the next URL in a property (a next URL list) is followed in the same way as a `Link` list: the URL is taken from the body of each response, a next URL that leaves the scheme, host or port of the client is refused with `PaginationError`, nothing is sent to it, and such a method cannot resume from a saved position.

The generator finds a paged operation by its names and shape. The operation must be a GET with one JSON 2xx response, and it must match exactly one of these styles. A token cursor is one query parameter among `cursor`, `after`, `page_token`, `next_token`, `continuation_token` and `marker`, paired with a next-cursor property among `next_cursor`, `next_page_token`, `next_token`, `next_marker`, `next_continuation_token`, `continuation_token` and `next` of the same type. An id cursor is one query parameter among `starting_after`, `after_id` and `after`, paired with a boolean `has_more` and a string `last_id` or `next_cursor` property. A `Link` list has a `Link` header declared on the response. An offset list has an `offset` query parameter with a `limit`, and a page-number list has a `page` query parameter with one of `per_page`, `page_size`, `size` or `limit`. A next URL list has no cursor parameter and a string property with `format: uri` or `uri-reference`, or one named `next_url`, `next_uri`, `nextlink` or `next_link`, that is optional or nullable. An operation that matches more than one style is ambiguous, and one that is streamed is never paged; either keeps only its plain method, and the generation log names it and says why. The `x-fern-pagination` and `x-speakeasy-pagination` (cursor type) extensions are read as hints. The generator option `paginationOverrides` (a JSON object that maps an operation id to `none` or to `{style, requestParam, responseProperty, itemsProperty, hasMoreProperty}`, where the style is `token`, `link`, `id`, `offset`, `page` or `body`) wins over a hint and over inference, and `pagination=off` switches the feature off.

### Custom HTTP client

Supply your own `Faraday::Connection` to control the adapter or middleware. The SDK still applies its timeouts, user agent, redirect policy and query encoding on top, and never closes a connection you supplied. The SDK's own connection keeps up to 100 sockets alive between calls through `faraday-net_http_persistent`, closing each after 5 seconds idle. A connection you supply can use any Faraday adapter, and its pool is yours to manage. Combining it with `proxy`, `ca_cert_path` or `verify_ssl(false)` raises `ArgumentError`; configure those on the connection instead.

```ruby file
require 'petstore_client'

def client_with_connection(authenticator)
  connection = Faraday.new { |f| f.adapter :net_http }
  transport = Petstore::Client::TransportOptions.builder.connection(connection).build

  Petstore::Client::Petstore.new(authenticator, transport)
end
```

### Retries

`max_retries` (default `2`; `0` disables) sets how many times a failed request is sent again: after a network error before any response, and after a `408`, `409`, `429` or `5xx` (not `501`) status. The wait is 0.5 seconds doubling to 8 with up to 25% jitter, or the `Retry-After` header (at most 60 seconds). Retries never extend the `timeout` budget, a TLS failure is not retried, and a multipart body or a single-use `BodySource` is never sent twice.

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

```ruby statements
require 'petstore_client'

tenant_hook = Class.new do
  def start(_info, headers)
    headers['X-Tenant'] = 'a'
    nil
  end
end.new

client = Petstore::Client::Petstore.with_token('/api/v3', 'token', call_hook: tenant_hook)
```

For OpenTelemetry the SDK ships no adapter, and the README shows a `TelemetryCallHook` recipe that records one span, one request count and one duration per call.

### Closing the client

`client.close` is graceful and idempotent. The client is marked closed at once and a request started afterwards raises a `RuntimeError`; a request already in flight finishes, and the connection the SDK built is released when the last one completes. Streams that are still open are cancelled. A second `close` does nothing, and a `Faraday::Connection` you supplied is never closed.

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

A raw binary request body is a `BodySource` (see Streaming). Multipart file parameters are typed as `IO`. A binary response body is returned as a `ByteStream`, which responds to `read`, `each`, `read_all` and `close` (see Streaming).

## Comment Style

Never place a comment on the same line as code. Use `#` comments.

```good
# This explains the logic
x = 1
```

```bad
x = 1  # This explains the logic
```
