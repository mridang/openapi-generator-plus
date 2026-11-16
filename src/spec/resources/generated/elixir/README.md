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

## Package

- Name: `petstore_client`
- Version: `1.0.0`

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

### Cancellation

There is no cancellation argument. A caller stops a call by terminating the process that made it (a process exit signal, the idiom here), and `timeout` on `TransportOptions` ends a call with `NetworkTimeoutError`.

### Closing the client

`PetstoreClient.Client.close/1` releases the transport the client created and the processes its authenticator started. It is idempotent.

### Raw responses, request id and logging

Every operation has a `_with_http_info` variant that returns the status, headers and body as an `ApiResult`. With `inject_request_id: true` on `TransportOptions`, the SDK sends an `X-Request-ID` it generates, unless the caller already set one. The SDK sends `Accept-Encoding` for the codecs it can decode and decodes them. It does not log.

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

### Per-call options

There is no per-call timeout or header override. Set `timeout` and
`default_headers` on `TransportOptions` for the whole client.

### `LICENSE` file is not auto-emitted

The package manifest declares MIT, but no `LICENSE` / `LICENSE.md` file
is generated alongside the sources. Drop the appropriate license text
into the generated tree as part of your release pipeline before
publishing to a registry — most registries warn or block on a missing
file, and the GitHub license auto-detect cannot pick up a manifest-only
declaration.
