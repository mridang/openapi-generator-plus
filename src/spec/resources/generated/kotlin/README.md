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
through your own Ktor `HttpClient`, which must be built with `followRedirects = false`
(`build()` refuses one that follows redirects, since the SDK manages redirects itself). The SDK keeps its timeout, redirect
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

## Credentials

A credential you pass to an authenticator always wins. When you pass none, the
authenticator reads an environment variable once, when it is built, and an empty
value counts as not set. `BearerAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN`,
`ApiKeyAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY`, and `BasicAuthenticator` reads
`SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME` and `SWAGGER_PETSTORE_OPEN_API_3_0_PASSWORD`. A credential that is neither
passed nor set fails right there with an `IllegalArgumentException` that names the
variable, before any request is made.

The authenticator generated for a security scheme reads the variable of its own
scheme, so two schemes never read each other's secret:

| Variable | Security scheme | Credential |
|---|---|---|
| `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` | `adminBasic` | `username` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` | `adminBasic` | `password` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY` | `apiKeyHeader` | `apiKey` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY` | `apiKeyQuery` | `apiKey` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID` | `browserAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY` | `internalApiKey` | `apiKey` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID` | `legacyAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET` | `legacyAuth` | `clientSecret` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME` | `legacyAuth` | `username` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD` | `legacyAuth` | `password` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID` | `machineAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET` | `machineAuth` | `clientSecret` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME` | `petStoreBasic` | `username` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD` | `petStoreBasic` | `password` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN` | `petStoreBearer` | `token` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN` | `serviceToken` | `token` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY` | `sessionCookie` | `apiKey` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID` | `ssoAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET` | `ssoAuth` | `clientSecret` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI` | `ssoAuth` | `redirectUri` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID` | `userAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET` | `userAuth` | `clientSecret` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI` | `userAuth` | `redirectUri` |

A Bearer token or an API key that rotates takes a provider as well: the lambda is
called on every request and its result is checked like a static value. The order is
the argument, then the environment variable, then the provider, so a provider is
used only when neither of the other two is set.

```kotlin
val auth = BearerAuthenticator("https://api.example.com", tokenProvider = { vault.currentToken() })
```

## Credentials per security scheme

`Client(host, <scheme> = credential, transportOptions)` takes one optional
credential for each security scheme of the API, named after the scheme: `petStoreBasic`, `adminBasic`, `petStoreBearer`, `serviceToken`, `apiKeyHeader`, `internalApiKey`, `apiKeyQuery`, `sessionCookie`, `machineAuth`, `legacyAuth`, `userAuth`, `browserAuth`, `ssoAuth`.
Each operation uses the credential of the first scheme its spec lists that you passed.
There is no per-call override, so an operation's options object carries no authenticator.
An operation none of whose schemes you passed a credential for uses the authenticator of
`Client(authenticator)` when you gave one and is otherwise sent without a
credential; an operation declared with no security never sends one.

## Closing the client

`close()` is graceful and idempotent: a request already in flight finishes, the transport
the SDK created is released after the last one, and a request issued afterwards fails with
`IllegalStateException`. A Ktor client you supplied is never closed.

## Cookie parameters

A cookie parameter is sent as you give it when every character is a legal RFC 6265
cookie-octet, and otherwise fails with `IllegalArgumentException` before anything is sent.
Percent-encode a value that needs it first; the SDK does not encode cookie values.

## Per-call options

Every operation takes an options object, also an operation with no parameters and no
security. Besides the operation's own parameters it carries `timeout` (milliseconds,
one end-to-end budget for this call), `headers` (sent with this call only; a header
of the same name set anywhere else is replaced) and `maxRetries` (`0` turns the
retries of this call off). Each replaces the client-wide setting for that call
and leaves it as it was for every other.

```kotlin
client.someApi.someOperation(options = SomeOperationOptions(timeout = 2_000, maxRetries = 0))
```

## Errors

Every `ApiException` has `requestMethod` and `requestPath` (the path of the
request, without its query string) next to `statusCode`, the headers and the
body, and its string form shows them on a `Request:` line. They are null for an
exception you built yourself.

When an operation declares several `2xx` responses with different schemas, its
result is a union and the body is decoded by the status code the server sent, not
by guessing from its shape.

## Response compression

The SDK sends `Accept-Encoding: gzip, deflate` and decodes the answer itself;
`br` and `zstd` are offered too when `org.brotli:dec` and `com.github.luben:zstd-jni` are on the
classpath. A body in any other encoding is an error, never returned still encoded.
After decoding, `Content-Encoding` and `Content-Length` are dropped from the returned
headers.

## Logging

The SDK writes no logs. To see requests and responses, pass your own Ktor
`HttpClient` with the `Logging` plugin installed through
`TransportOptions.builder().httpClient(...)`; headers that carry credentials are
masked in the string form of an exception.

## Versioning

The version in the build file (`1.0.0`) comes from the `artifactVersion`
generator option; publish releases with semantic versioning so that callers can
rely on the major number. The generated code needs the JVM 17 or newer, the floor
the build and the static analysis target.

## Caveats

### Optional and nullable

A property that is optional and a property that is nullable are both a nullable type
(`T?`), so an absent key and an explicit `null` read the same once decoded. A required
property that is nullable keeps its key: it is written as `null` when unset, and must be
present when decoding. A required nullable query parameter is kept the same way: when its value is `null` the key is still sent, with an empty value (`?key=`).

### Unknown enum values and discriminators

A value the SDK does not know fails loudly: an enum value outside the declared set, and
a discriminator value with no matching subtype, both raise `SerializationException`
rather than falling back to a default or dropping the field.

### Repeated response headers

`ApiResult.headers` and `ApiException.responseHeaders` hold one string per header
name (lower-cased). A header the server sent more than once is joined with `, `.

### Streaming

The SDK holds every request and response body in memory. It has no streaming upload or
download; if you need one, send the request through the Ktor `HttpClient` you inject with
`TransportOptions.builder().httpClient(...)`.

### Configuration and transport options

`Configuration` (base URL and default headers of an API class) and `TransportOptions`
(proxy, TLS, timeout, retries, redirects of the HTTP client) are separate on purpose: the
first says what is sent, the second how. `Client` builds the `Configuration` from the
authenticator's host, and you pass `TransportOptions` next to it.

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

### Ambient proxy settings

`TransportOptions.builder().proxy(...)` is the way to configure a proxy, but it is not the
only source on the JVM: when no proxy is configured, the Ktor CIO engine asks the JVM's
default `ProxySelector`, so `http.proxyHost` and the system proxy settings apply. The engine
offers no way to ask for a direct connection, so the SDKs' rule that ambient proxy settings
are ignored cannot hold here; set a `ProxySelector` of your own if you need to override it.

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
