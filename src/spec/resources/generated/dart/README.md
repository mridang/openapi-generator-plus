# petstore_client SDK

Auto-generated Dart SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- **Dart SDK:** `>=3.6.0 <4.0.0`

## Platform support

The HTTP transport ships in two variants selected automatically at compile time
by a conditional export (`default_api_client.dart`); both share a single
`AbstractApiClient` built on `package:http`, so redirect handling, multipart
encoding, decompression, charset decoding, and header injection are identical
everywhere:

- **Native** (Dart VM, Flutter mobile/desktop, server) uses a `dart:io`
  `HttpClient` via `IOClient`, with full support for proxy routing, custom CA
  certificates, and TLS-verification toggling.
- **Flutter Web** (and any `dart2js`/`wasm` target) uses `package:http`'s
  `BrowserClient`, importing no `dart:io`, so the package compiles and runs in
  the browser. Proxy / custom-CA / TLS-off are not expressible through the
  browser's `fetch`, so requesting one throws a typed `ApiError` at construction
  rather than being silently ignored; everything else works unchanged. The
  gzip-lie response hardening is a native-only extra (the browser already
  decodes `Content-Encoding`).

The Dart toolchain bundles the formatter, linter, static analyser, and
upgrade tool. No additional tools need to be installed.

| Tool        | Command                | Notes                                                |
|-------------|------------------------|------------------------------------------------------|
| Formatter   | `dart format .`        | Built into the Dart SDK                              |
| Linter      | `dart analyze`         | Uses `package:lints/recommended.yaml`                |
| Analyser    | `dart analyze`         | Strict rules via `package:very_good_analysis`        |
| Auto-fix    | `dart fix --apply`     | Applies analyser-suggested fixes (Rector-equivalent) |

## Build

```bash
dart pub get
```

## Format

```bash
dart format .
```

## Analyse

```bash
dart analyze
```

## Auto-fix

```bash
dart fix --apply
```

## Test

```bash
dart test
```

## Package

- Name: `petstore_client`
- Version: `1.0.0`

## Caveats

### Integer and decimal precision

Dart's `jsonDecode` returns a `num` (the union of `int` and `double`).
For JSON numbers above 2^53 the parser silently picks `double`, so
`format: int64` values larger than 9 007 199 254 740 991 — Twitter
/ X IDs, Snowflake IDs, Discord IDs, and similar 64-bit auto-
increment IDs — lose precision.

`format: decimal` / `format: number` values are likewise stored as
`double`, so monetary values lose exact decimal representation.
`0.1 + 0.2` is `0.30000000000000004`. Do not do arithmetic on
prices, balances, or other money-typed fields; use the `decimal`
package or pass values as strings.

Fixing this end-to-end would require switching generated model
fields to `BigInt` and a custom JSON parser, which breaks the
arithmetic operators on every existing consumer. Documented as a
known limitation.

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
