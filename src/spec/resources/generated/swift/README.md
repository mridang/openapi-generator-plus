# PetstoreClient SDK

Auto-generated Swift SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- **Swift 6.0+** (toolchain available at <https://swift.org/download/>)
- **macOS 14+** or a Linux distribution supported by the Swift toolchain
- [`swift-format`](https://github.com/apple/swift-format) (bundled with the
  Swift 6.0 toolchain) — formatter and basic linter
- [`swiftlint`](https://github.com/realm/SwiftLint) — opinionated linter
  used in addition to `swift-format`
- The Swift 6 compiler is invoked with `-warnings-as-errors` (see the
  `analyse` target) to provide static-analysis coverage on every build

## Build

```bash
swift build
```

## Test

```bash
swift test
```

## Format, lint and analyse

```bash
# Apply formatting in-place
make format

# Fail the build on any formatting or lint violation
make lint

# Static-analyse with strict concurrency and warnings-as-errors
make analyse
```

`swift-format` is configured via `.swift-format` and `swiftlint` via
`.swiftlint.yml` at the package root. Both files ship alongside the
generated sources and can be customised in place.

## Package

- Name: `PetstoreClient`
- Version: `1.0.0`

## Caveats

### Decimal / `format: number` precision

Foundation's `JSONDecoder` parses JSON numbers into `Double` for
`Decodable` model fields typed as `Double`/`Float`. `format: decimal`
/ `format: number` values are therefore stored as `Double`, so
monetary values lose exact decimal representation. `0.1 + 0.2` in
Swift is `0.30000000000000004`.

Do not do arithmetic on prices, balances, or other money-typed
fields. If you need exact decimal arithmetic, parse the raw response
body yourself with `JSONDecoder().userInfo[.useDecimal] = true` and
map the field to Foundation's `Decimal`.

`format: int64` is unaffected — Swift's `Int` is 64-bit on all
supported platforms and represents the full range without loss.

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
