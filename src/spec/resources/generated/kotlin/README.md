# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated Kotlin SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- **Kotlin** 2.2 (Kotlin Multiplatform — `jvm` target)
- **JVM** 17 or newer (Gradle toolchain pins to 17)
- **Gradle** 8.x (8.10+) or Gradle 9.x

## Build

```bash
./gradlew build
```

## Test

```bash
./gradlew test
```

## Lint, format & static analysis

The SDK ships with three configured tools:

- **ktlint** — formatter / style enforcer (`org.jlleitschuh.gradle.ktlint`)
- **detekt** — linter + static analyser with the `detekt-formatting`
  and `detekt-rules-libraries` rulesets enabled

Run them together:

```bash
./gradlew ktlintCheck detekt
```

To auto-fix formatting issues:

```bash
./gradlew ktlintFormat
```

## Package

- Group: `com.example.petstore`
- Version: ``

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
