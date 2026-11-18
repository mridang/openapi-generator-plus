# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated Elixir SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- **Elixir** 1.17+
- **Erlang/OTP** 27+
- **mix** (ships with Elixir)

## Build

```bash
mix deps.get && mix compile
```

## Test

```bash
mix test
```

## Format

```bash
mix format
```

## Lint

```bash
mix credo --strict
```

## Static analysis

```bash
mix dialyzer
```

## Dependency audit

```bash
mix hex.audit
```

## Package

- Name: `petstore_client`
- Version: `1.0.0`
- License: MIT
- Authors: Ada Lovelace, Grace Hopper
- Repository: <https://example.com/acme/petstore-client>
- Homepage: <https://example.com/petstore>
- Documentation: <https://example.com/petstore/docs>
- Issue tracker: <https://example.com/acme/petstore-client/issues>

## Usage

Add `{:petstore_client, "~> 1.0.0"}` to the `deps` in `mix.exs`, then:

```elixir
client = PetstoreClient.Client.with_token("https://api.example.com", "your-token")
```

[SKILLS.md](SKILLS.md) covers authentication, error handling, transport options and models.

### Custom HTTP client

Pass Req options such as an `:adapter` or a `:plug` through `TransportOptions.new(req_options: [...])`. The SDK's own settings win and the SDK never closes a transport you supply.

### Retries

A failed call is retried up to `max_retries` times (default `2`; `0` turns retries off): after a network error that happened before any response, and after a response with status 408, 409, 429 or 500 to 599 other than 501. The wait starts at 0.5 s and doubles up to 8 s, less up to 25% jitter, and a `Retry-After` header (seconds or an HTTP date, capped at 60 s) replaces it. Every retry is a fresh request with its authentication headers rebuilt, and no retry runs past the end-to-end `timeout`. A 401 on a call authenticated by OAuth2 or OpenID Connect drops the cached access token (the refresh token is kept) and replays the request once, rebuilt; a second 401 is the usual `UnauthorizedError`.

```elixir
transport = PetstoreClient.TransportOptions.new(max_retries: 0)
```

### Per-call options

Every operation takes an optional options struct, even one with no query, header, form or cookie parameter. It carries the per-call settings `timeout` (milliseconds), `headers` (a map) and `max_retries`; there is no per-call credential (see Credentials). The same three settings can also be passed as `:timeout`, `:headers` and `:max_retries` in the trailing `opts` keyword list. A setting replaces the client's own for that call only; the `opts` key wins over the options struct. `headers` are merged after the default headers and before the authentication headers, and replace a header of the same name in any casing.

```elixir
options = %PetstoreClient.Api.Options.GetItemOptions{timeout: 5_000, headers: %{"X-Trace" => "1"}, max_retries: 0}
PetstoreClient.Api.ItemsApi.get_item(api, 7, options)
PetstoreClient.Api.ItemsApi.get_item(api, 7, nil, timeout: 5_000)
```

### Credentials

`PetstoreClient.Client.new/3` takes one optional credential for each security scheme of the API, named after the scheme in a keyword list: `pet_store_basic`, `admin_basic`, `pet_store_bearer`, `service_token`, `api_key_header`, `internal_api_key`, `api_key_query`, `session_cookie`, `machine_auth`, `legacy_auth`, `user_auth`, `browser_auth`, `sso_auth`. Each is an authenticator. An operation uses the credential of the first scheme its own security requirement lists that the client holds, and there is no per-call override. The optional first argument, the authenticator, serves an operation that none of the scheme credentials matches. An operation whose spec says `security: []` sends no credential. The base URL comes from the first credential given, the authenticator first. The client is refused (`ArgumentError`) when it gets no credential at all, or a key that names no scheme of this API.

```elixir
client = PetstoreClient.Client.new(nil, nil, pet_store_basic: credential)
```

An authenticator takes its credentials as arguments. A credential you do not pass (a `nil`) is read, once, when the authenticator is built, from an environment variable; an argument you pass always wins, and an empty variable counts as unset. A required credential found in neither place raises `ArgumentError` when the authenticator, and so the client, is built. A bearer token or an API key may also be a zero-argument function, called for every request, so a token that rotates is read fresh each time.

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

An authenticator you build without a scheme (`BearerAuthenticator`, `BasicAuthenticator` and `ApiKeyAuthenticator` on their own) reads `SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN`, `SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_PASSWORD` or `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY`. Set `envVarPrefix` when generating to change the `SWAGGER_PETSTORE_OPEN_API_3_0` prefix.

### Cancellation

There is no cancellation argument. A caller stops a call by terminating the process that made it (a process exit signal, the idiom here), and `timeout` on `TransportOptions` ends a call with `NetworkTimeoutError`.

### Closing the client

`PetstoreClient.Client.close/1` closes the client and is idempotent. A call already in flight finishes; a call that starts after it raises `RuntimeError` with the message "ApiClient has been closed and can no longer send requests". The processes the SDK started for the client (OAuth2 token managers, OpenID Connect discovery) are stopped when the last call in flight is over, or at once when none is. Req and Finch own the HTTP pool, and a transport you supply through `req_options` is never closed by the SDK.

### Cookie parameters

A cookie parameter goes into the `Cookie` header exactly as given when every character is a legal RFC 6265 cookie-octet: printable ASCII except space, double quote, comma, semicolon and backslash. A value with any other character fails with `ArgumentError` before the request is sent, so percent-encode such a value yourself first. The SDK never encodes it for you, because a cookie parser does not decode it.

### Raw responses, request id and logging

Every operation has a `_with_http_info` variant that returns the status, headers and body as an `ApiResult`. With `inject_request_id: true` on `TransportOptions`, the SDK sends an `X-Request-ID` it generates, unless the caller already set one. It does not log: wrap a call, or give `TransportOptions` a Req `:adapter` or step through `req_options`, to log requests.

An error carries the request it answers: `request_method` and `request_path` (the path without the query string), and its message has a `Request: GET /path` line after the status line.

### Response compression

The SDK sends `Accept-Encoding` for the codecs it can decode (`gzip` and `deflate`, `br` when the optional `:brotli` dependency is present, `zstd` on OTP 28) and decodes the body before it returns; the returned headers no longer carry `content-encoding` or `content-length`. A caller-supplied `Accept-Encoding` header, in any casing, replaces the SDK's.

### Versioning

The package follows semantic versioning: a new operation or field is a minor release, a removed or renamed one is a major release, and a deprecated operation is marked `@deprecated` before it goes. The Elixir floor is the oldest release that still gets security fixes (1.17 today) and moves up when that release reaches end of life.

### Models are immutable

A model is a struct, and a struct never changes in place. Make a changed copy with the update syntax, `%{model | name: "new"}`, which leaves the original as it was.

### Behaviours that differ from what you may expect

- An optional property that is absent and a property that is `null` both decode to `nil`; the model cannot tell them apart. A required property that is also nullable must be present, and an explicit `null` is kept. A required nullable query parameter is kept the same way: when its value is `nil` the key is still sent, with an empty value (`?key=`).
- An enum value or a discriminator value the SDK does not know raises a `SerializationError` instead of passing through, so a spec that is out of date fails loudly.
- A response header that repeats arrives as one entry in `headers`, its values joined with `, ` and its name in lower case.
- Request and response bodies are held in memory. Streaming a body is not supported; to stream, pass your own transport through `req_options`.
- `Configuration` (the base URL and the headers of API calls) and `TransportOptions` (proxy, TLS, timeouts, retries) stay separate structs on purpose: the first describes the API, the second the connection.

## Caveats

### Decimal / `format: number` precision

`format: decimal` / `format: number` values are decoded into Elixir
`float` (IEEE-754 double), so they lose exact decimal representation.
`0.1 + 0.2` in Elixir is `0.30000000000000004`.

Do not do arithmetic on prices, balances, or other money-typed fields.
If you need exact decimal arithmetic, parse the raw response body
yourself (e.g. with `Jason.decode/2`) and use the `Decimal` library.

`format: int64` is unaffected — Elixir integers are arbitrary-precision
and represent the full 64-bit range without loss.

## Not supported

### Webhooks and callbacks

This SDK is **client → server** only. Spec entries describing
server-initiated calls — OAS 3.1 top-level `webhooks` and OAS 3.0
per-operation `callbacks` — are intentionally skipped during code
generation. If you need to receive webhook deliveries, write the
handler yourself and use this SDK only to deserialize the incoming
payload (e.g. by reusing the relevant request-body model).

### Conditional-required validation (`dependentRequired` / `dependentSchemas`)

JSON Schema 2019-09 keywords for "if field X is present, field Y is
also required" are **not enforced** by this SDK. No mainstream
OpenAPI client codegen implements them. The server is the authoritative
validator; if you want client-side checking, plug in a JSON Schema
validator library for your language.

### Schema constraints

Schema constraints (`pattern`, `minLength`, `maxLength`, `minimum`,
`maximum`, `multipleOf`, `minItems`, `maxItems`) are validated by the server
and by the generated models only where the language's model layer does it
natively. Elixir structs and typespecs carry no constraint checking, so this
SDK enforces none of them: the model layer only checks, when a response is decoded,
that required properties are present and that values have the declared type.
Building a struct never validates: a wrong-typed value or an unknown enum
atom builds fine, and the unknown enum string fails loudly only when it is decoded. The server is
the authoritative validator. If you want fast-fail validation before the
network round trip, plug in a JSON Schema validator library.

`uniqueItems` arrays are held as a `MapSet` and sent in the `MapSet`'s own
enumeration order, not sorted.

### SOCKS proxies

The `proxy:` option of `TransportOptions.new/1` accepts only `http://` and `https://` URLs.
Passing a `socks://`, `socks4://`, or `socks5://` scheme is rejected
at construction time with a clear error. SOCKS support would
require enabling extra dependencies / feature flags on the underlying
HTTP library in every one of the 12 SDKs we generate, with non-trivial
API divergence; we explicitly chose not to. If you need SOCKS, route
through a local HTTP-CONNECT bridge or configure it at the OS level.

### XML bodies, Link Objects and mutual TLS

XML request and response bodies (including `xml` `nodeType` annotations),
OpenAPI Link Objects and `mutualTLS` security schemes are not generated.

### `LICENSE` file is not auto-emitted

The package manifest declares MIT, but no `LICENSE` / `LICENSE.md` file
is generated alongside the sources. Drop the appropriate license text
into the generated tree as part of your release pipeline before
publishing to a registry — most registries warn or block on a missing
file, and the GitHub license auto-detect cannot pick up a manifest-only
declaration.
