# petstore SDK

Auto-generated Go SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- Go 1.24+
- [`gofumpt`](https://github.com/mvdan/gofumpt) — stricter formatter (superset of `gofmt`)
- [`golangci-lint`](https://golangci-lint.run/) — linter aggregator (bundles `staticcheck`, `revive`, `gocritic`, `gosec`, etc.)

### Tooling

```bash
# Format (gofmt is built into the Go toolchain)
go fmt ./...
gofumpt -w .

# Static analysis
go vet ./...

# Lint (runs staticcheck, revive, gocritic, gosec, bodyclose, errcheck, ...)
golangci-lint run
```

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
when the target type is `interface{}`, and into `float64` for `float`
typed fields. `format: decimal` / `format: number` values are
therefore stored as `float64`, so monetary values lose exact decimal
representation. `0.1 + 0.2` in Go is `0.30000000000000004`.

Do not do arithmetic on prices, balances, or other money-typed
fields. If you need exact decimal arithmetic, parse the raw response
body yourself and use `math/big.Rat` or a third-party decimal
library.

`format: int64` is unaffected — Go's `int64` natively represents the
full 64-bit range without precision loss.

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

### Numeric / string constraint validation

OpenAPI keywords like `minLength`, `maxLength`, `minimum`, `maximum`,
`pattern`, `minItems`, `maxItems`, `uniqueItems`, `multipleOf` are
**not enforced** by this SDK. The server is the authoritative
validator; client-side enforcement is a DX nicety, not a correctness
requirement. If you want fast-fail validation before the network
round trip, plug in a JSON Schema validator library for your language.

### SOCKS proxies

`TransportOptions.proxy()` accepts only `http://` and `https://` URLs.
Passing a `socks://`, `socks4://`, or `socks5://` scheme throws (or
panics) at construction time with a clear error. SOCKS support would
require enabling extra dependencies / feature flags on the underlying
HTTP library in every one of the 12 SDKs we generate, with non-trivial
API divergence; we explicitly chose not to. If you need SOCKS, route
through a local HTTP-CONNECT bridge or configure it at the OS level.

### Per-call cancellation

No generated operation method accepts a per-call cancellation handle.
In-flight requests can only be terminated by waiting for the configured
`TransportOptions` request timeout to fire — there is no way to abort
mid-flight from the caller side. If you need fine-grained per-call
cancellation, wrap the SDK call in your language's standard concurrency
primitives (a `Future` you cancel externally, a `Task` you orphan, an
`asyncio` task you cancel, etc.) and rely on the timeout to break the
underlying socket.

### `LICENSE` file is not auto-emitted

The package manifest declares MIT, but no `LICENSE` / `LICENSE.md` file
is generated alongside the sources. Drop the appropriate license text
into the generated tree as part of your release pipeline before
publishing to a registry — most registries warn or block on a missing
file, and the GitHub license auto-detect cannot pick up a manifest-only
declaration.

### Default `User-Agent` is not normalised across SDKs

The default `User-Agent` header value is whatever this SDK's ecosystem
considers idiomatic — it deliberately does not match the other 11 SDKs
in this generator family. Servers that route, throttle, or analyse on
`User-Agent` will see different values depending on which SDK the
request came from. Override per request via
`TransportOptions.defaultHeader("User-Agent", "your/agent")` if you
need a stable value across all your client integrations.
