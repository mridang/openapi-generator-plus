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

Every operation takes the per-call settings `:timeout` (milliseconds), `:headers` (a map) and `:max_retries` in its trailing `opts` keyword list, and the options struct of an operation that has one carries the same three fields. A setting replaces the client's own for that call only; the `opts` key wins over the options struct. `headers` are merged after the default headers and before the authentication headers, and replace a header of the same name in any casing.

```elixir
PetstoreClient.Api.ItemsApi.get_item(api, 7, timeout: 5_000, headers: %{"X-Trace" => "1"}, max_retries: 0)
```

### Credentials

An authenticator takes its credentials as arguments. An argument that is `nil` is read once, when the authenticator is built, from the environment variable `OPEN_API_<FIELD>` (`OPEN_API_TOKEN`, `OPEN_API_API_KEY`, `OPEN_API_USERNAME`, `OPEN_API_PASSWORD`, `OPEN_API_CLIENT_ID`, `OPEN_API_CLIENT_SECRET`); an empty variable counts as unset, and an argument you pass always wins. A required credential found nowhere raises `ArgumentError` when the authenticator, and so the client, is built. A bearer token or an API key may also be a zero-argument function, called for every request, so a token that rotates is read fresh each time.

### Cancellation

There is no cancellation argument. A caller stops a call by terminating the process that made it (a process exit signal, the idiom here), and `timeout` on `TransportOptions` ends a call with `NetworkTimeoutError`.

### Closing the client

`PetstoreClient.Client.close/1` releases the transport the client created and the processes its authenticator started. It is idempotent.

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

- An optional property that is absent and a property that is `null` both decode to `nil`; the model cannot tell them apart. A required property that is also nullable must be present, and an explicit `null` is kept.
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
SDK enforces none of them: the model layer only checks that required
properties are present and that values have the declared type. The server is
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
