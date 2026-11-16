# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated Kotlin SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- **Kotlin** 2.2 (Kotlin Multiplatform — `jvm` target)
- **JVM** 17 or newer (the build uses whichever JDK is on the `PATH`; static analysis targets 17)
- **Gradle** 8.x (8.10+) or Gradle 9.x, installed on the `PATH` (the SDK ships no Gradle wrapper)

## Installation

```kotlin
implementation("com.example.petstore:petstore-client:1.0.0")
```

## Usage

Create a client from an authenticator and call the operations from a coroutine
(every operation is a `suspend` function). `SKILLS.md` has the full reference:
every authentication scheme, transport options, error handling and models.

```kotlin
import com.example.petstore.Client

val client = Client.withToken("https://api.example.com", "your-token")
```

## Build

```bash
gradle build
```

## Test

```bash
gradle test
```

## Lint, format & static analysis

The SDK ships with two configured tools:

- **ktlint** — formatter / style enforcer (`org.jlleitschuh.gradle.ktlint`)
- **detekt** — linter + static analyser with the `detekt-formatting`
  and `detekt-rules-libraries` rulesets enabled

Run them together:

```bash
gradle ktlintCheck detekt
```

To auto-fix formatting issues:

```bash
gradle ktlintFormat
```

## Package

- Group: `com.example.petstore`
- Artifact: `petstore-client`
- Version: `1.0.0`

## Cancellation

Operations are `suspend` functions, so cancellation is coroutine cancellation:
cancel the calling coroutine (or use `withTimeout`) and the request is aborted.
The call then raises `kotlinx.coroutines.CancellationException`, never a
`NetworkTimeoutException`; that one is only raised when the SDK's own
`TransportOptions` timeout elapses. The SDK has no cancellation API of its own
and needs none.

```kotlin
import kotlinx.coroutines.withTimeout

withTimeout(2_000) { client.someApi.someOperation() }
```

## Custom HTTP client

`TransportOptions.builder().httpClient(myKtorClient)` sends every request
through your own Ktor `HttpClient`. The SDK keeps its timeout, redirect
policy, user agent and header defaults applied on top of it, and never closes
it: you own its lifecycle. It cannot be combined with `proxy`, `caCertPath` or
`verifySsl(false)`; `build()` rejects the combination, so configure proxy and
TLS on the client you supply.

## Raw responses, timeouts and request ids

Every operation has a `*WithHttpInfo` variant that returns an `ApiResult`
holding the status code, the response headers (lower-cased names) and the
decoded body; use it when you need to read a header.

`TransportOptions.timeout` is in milliseconds (default 10000) and is one
end-to-end budget for the whole call: connection, every redirect hop and the
response body. When it elapses the call raises `NetworkTimeoutException`.
Unless `injectRequestId(false)` is set, the SDK sends a generated
`X-Request-ID` header on every request, and a header of the same name that you
supply always wins.

## Retries

A failed request is sent again up to twice (`TransportOptions.maxRetries`,
default 2; `0` turns retries off). A request is retried after a network error
that happened before any response arrived, and after a 408, 409, 429 or 5xx
answer except 501. The wait starts at 0.5 seconds, doubles up to 8 seconds and
loses up to 25% of its length at random; a `Retry-After` header (seconds or an
HTTP date) replaces it, capped at 60 seconds. Retries share the one end-to-end
`timeout`: a wait that does not fit in what is left of it ends the retrying and
the last answer or error is reported. A timeout is never retried, and
cancelling the calling coroutine stops the wait. Every retry is a new request:
its headers are built again (a new `X-Request-ID`), and the body is sent again.

```kotlin
val transport = TransportOptions.builder().maxRetries(0).build()
```

## Caveats

### Decimal / `format: number` precision

`format: number` / `format: decimal` fields are typed as
`java.math.BigDecimal` and serialized through a precision-preserving
serializer that round-trips the JSON number as a string, so exact
decimal values survive deserialization unchanged — unlike languages
that decode JSON numbers into a binary `float64`. You can safely do
arithmetic on prices, balances, and other money-typed fields using
`BigDecimal`'s exact operators.

The IEEE-754 binary types are a different story: `format: float` and
`format: double` map to Kotlin's `Float` / `Double`, which **cannot**
represent every decimal fraction exactly (`0.1 + 0.2` is
`0.30000000000000004`). Do not use a `double`/`float`-typed field for
money. If a monetary value is typed as `format: double` in the spec,
read it from the raw response body and parse it into a `BigDecimal`
yourself.

`format: int64` is unaffected — Kotlin's `Long` natively represents the
full 64-bit range without precision loss.

### Schema constraints

The constraints `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`,
`multipleOf`, `minItems` and `maxItems` are validated by the server, not by
this SDK. The generated models are kotlinx.serialization data classes: their
model layer checks only the JSON type of each field (an integer must be an
in-range integer, a string must be a string, a required field must be
present), so a value that breaks one of these constraints is sent as given and
the server decides. The server is the authoritative validator; if you want a
fast failure before the network round trip, plug in a JSON Schema validator.

`uniqueItems` arrays are `Set` properties (`LinkedHashSet`). The order of a set
on the wire is unspecified: elements are written in the set's own iteration
order, which for a `LinkedHashSet` is insertion order. Do not depend on it.

## Not supported

### Webhooks and callbacks (#53)

This SDK is **client → server** only. Spec entries describing
server-initiated calls — OAS 3.1 top-level `webhooks` and OAS 3.0
per-operation `callbacks` — are intentionally skipped during code
generation. If you need to receive webhook deliveries, write the
handler yourself and use this SDK only to deserialize the incoming
payload (e.g. by reusing the relevant request-body model).

### XML bodies and `nodeType` (#70, #88)

Only JSON, form, multipart and binary bodies are generated. An operation
that offers only `application/xml` has no usable request or response
body; encode and decode the XML yourself.

### Link Objects (#78)

Response `links` are ignored; build the follow-up call yourself.

### Mutual TLS security schemes (#90)

A `mutualTLS` security scheme is not generated. Configure the client
certificate on the Ktor `HttpClient` you pass to
`TransportOptions.builder().httpClient(...)`.

### Conditional-required validation (`dependentRequired` / `dependentSchemas`)

JSON Schema 2019-09 keywords for "if field X is present, field Y is
also required" are **not enforced** by this SDK. No mainstream
OpenAPI client codegen implements them. The server is the authoritative
validator; if you want client-side checking, plug in a JSON Schema
validator library for your language.

### SOCKS proxies

`TransportOptions.builder().proxy(...)` accepts only `http://` and `https://` URLs.
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

### URI normalization

OAS `format: uri` properties are typed as `io.ktor.http.Url`. Ktor's
`Url` parser is lenient and round-trips through `toString()`, but it
also normalises some inputs — most notably it appends a trailing `/`
to the authority-only form (`"https://example.com"` becomes
`"https://example.com/"`). If you need byte-perfect URI preservation
or accept relative refs, use `format: uri-reference` in your spec
instead — `uri-reference` and `uri-template` stay as plain `String`.
