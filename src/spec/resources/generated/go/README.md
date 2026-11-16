# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated Go SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- Go 1.26+
- [`gofumpt`](https://github.com/mvdan/gofumpt) — stricter formatter (superset of `gofmt`)
- [`golangci-lint`](https://golangci-lint.run/) — linter aggregator (the generated `.golangci.yml` runs its default linters: `errcheck`, `govet`, `ineffassign`, `staticcheck`, `unused`, over the generated sources as well)

### Tooling

```bash
# Format (gofmt is built into the Go toolchain)
go fmt ./...
gofumpt -w .

# Static analysis
go vet ./...

# Lint (runs errcheck, govet, ineffassign, staticcheck and unused)
golangci-lint run
```

## Installation

```bash
go get petstore
```

## Usage

```go
import "petstore/pkg"

client, err := petstore.NewClientWithToken("https://api.example.com", "your-token", nil)
if err != nil {
    return err
}
defer client.Close()
```

Close the client to release its connections and abort requests still in flight; a closed client returns `ErrClientClosed`. Errors satisfy the `OpenAPIError` interface; `NetworkError` and `NetworkTimeoutError` mean no HTTP response arrived, and `TooManyRequestsError` is HTTP 429. See [SKILLS.md](SKILLS.md) for authentication, servers and error handling.

## Configuration

### Raw responses

Every operation has a `...WithHTTPInfo` variant returning an `*ApiResult[T]` with `StatusCode`, `Data`, `RawBody` and the lower-cased `Headers`. It is the way to read a response header.

### Timeouts

`NewTransportOptionsBuilder().Timeout(&ms)` sets one end-to-end budget in milliseconds (a positive integer) for the whole call, redirects and retries included. A call that exceeds it fails with `NetworkTimeoutError`. The default is 10000 ms; `Timeout(nil)` asks for none.

### Retries

A failed request is retried automatically, up to `MaxRetries` times (default 2; `MaxRetries(0)` disables retries). The retry applies to a network error that happened before a response and to the statuses 408, 409, 429 and 500-599 except 501. The wait starts at 0.5 seconds, doubles up to 8 seconds and loses up to 25% to jitter; a `Retry-After` header (seconds or an HTTP date, capped at 60 seconds) replaces it. The retries never extend the end-to-end `Timeout`: a wait that would outlast it ends the retries and the last failure is returned. Cancelling the context stops them. Every retry is a fresh request, so the authentication headers are applied again.

```go
transport, err := petstore.NewTransportOptionsBuilder().
    MaxRetries(5).
    Build()
```

### Expired tokens

A 401 on a request authenticated by an OAuth2 or OpenID Connect authenticator is handled separately: the cached access token is dropped (`InvalidateAccessToken`, the refresh token is kept) and the request is replayed once with new headers; a second 401 is returned as an `UnauthorizedError`. API key, basic and bearer authenticators never replay.

### Request id

The SDK adds a unique `X-Request-ID` header to every request unless you set your own (header names are matched case-insensitively). Quote it when you report a problem with a call. `InjectRequestID(false)` turns it off.

### Cancellation

Every operation takes a `ctx context.Context` as its first parameter.
Cancelling it aborts the request, and the error satisfies
`errors.Is(err, context.Canceled)`; it is distinct from the
`NetworkTimeoutError` raised when the transport timeout elapses. The client
also holds a base context that `Close()` cancels, which aborts every request
still in flight; either context cancels a request.

### Custom HTTP client

`TransportOptionsBuilder.HttpClient(doer)` accepts anything with a
`Do(*http.Request) (*http.Response, error)` method (an `*http.Client`
qualifies) and `TransportOptionsBuilder.RoundTripper(rt)` wraps your
`http.RoundTripper`. The SDK keeps its defaults on top (user agent, headers,
timeout and, around a RoundTripper or an `*http.Client`'s transport, its
redirect policy) and never closes what you supply. Combining a supplied
transport with `Proxy`, `CACertPath` or `VerifySsl(false)` is rejected by
`Build()` with `ErrTransportConflict`: configure those on your own transport.

## Build

```bash
go build ./...
```

## Test

```bash
go test ./...
```

## Module

- Module: `petstore`

## Caveats

### Decimal / `format: number` precision

Go's `encoding/json` parses JSON numbers into `float64` by default
when the target type is `any`, and into `float64` for `float`
typed fields. `format: decimal` / `format: number` values are
therefore stored as `float64`, so monetary values lose exact decimal
representation. `0.1 + 0.2` in Go is `0.30000000000000004`.

Do not do arithmetic on prices, balances, or other money-typed
fields. If you need exact decimal arithmetic, parse the raw response
body yourself and use `math/big.Rat` or a third-party decimal
library.

`format: int64` is unaffected — Go's `int64` natively represents the
full 64-bit range without precision loss.

### Schema constraints

The constraints `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`,
`multipleOf`, `minItems` and `maxItems` are validated by the server, and by
the generated models only where Go's model layer does it natively. Go's
`encoding/json` validates types, and the generated models additionally check
that required properties are present, that enum values are declared members
and that `format: uri` and `format: byte` values are well formed. The numeric,
string and array constraints above are not checked on the client; if you want
fast-fail validation before the network round trip, plug in a JSON Schema
validator library.

`uniqueItems` arrays are sent in the order the slice holds them, which is
Go's natural collection order: they are not sorted.

## Not supported

### Webhooks and callbacks

This SDK is **client → server** only. Spec entries describing
server-initiated calls — OAS 3.1 top-level `webhooks` and OAS 3.0
per-operation `callbacks` — are intentionally skipped during code
generation. If you need to receive webhook deliveries, write the
handler yourself and use this SDK only to deserialize the incoming
payload (e.g. by reusing the relevant request-body model).

### XML bodies and `nodeType`

Request and response bodies in `application/xml` and the XML `nodeType`
keyword are not generated (#70, #88). An operation that only declares XML has
no usable body in this SDK.

### Link Objects

OpenAPI Link Objects are ignored (#78).

### Mutual TLS security schemes

`mutualTLS` security schemes produce no authenticator (#90). Configure a client
certificate on your own `http.Client` or `http.RoundTripper` and pass it with
`TransportOptionsBuilder.HttpClient(...)` or `TransportOptionsBuilder.RoundTripper(...)`.

### Conditional-required validation (`dependentRequired` / `dependentSchemas`)

JSON Schema 2019-09 keywords for "if field X is present, field Y is
also required" are **not enforced** by this SDK. No mainstream
OpenAPI client codegen implements them. The server is the authoritative
validator; if you want client-side checking, plug in a JSON Schema
validator library for your language.

### SOCKS proxies

`TransportOptionsBuilder.Proxy(...)` accepts only `http://` and `https://` URLs.
Passing a `socks://`, `socks4://`, or `socks5://` scheme is rejected
at construction time with a clear error. SOCKS support would
require enabling extra dependencies / feature flags on the underlying
HTTP library in every one of the 12 SDKs we generate, with non-trivial
API divergence; we explicitly chose not to. If you need SOCKS, route
through a local HTTP-CONNECT bridge or configure it at the OS level.

### `LICENSE` file is not auto-emitted

The package manifest declares MIT, but no `LICENSE` / `LICENSE.md` file
is generated alongside the sources. Drop the appropriate license text
into the generated tree as part of your release pipeline before
publishing to a registry — most registries warn or block on a missing
file, and the GitHub license auto-detect cannot pick up a manifest-only
declaration.
