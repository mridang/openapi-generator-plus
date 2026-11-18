# Kotlin

The Kotlin generator (`kotlin-plus`, class `BetterKotlinCodegen`) produces a Gradle project that uses the Kotlin Multiplatform plugin with a single `jvm()` target. It sends requests with Ktor and serializes with kotlinx.serialization. This page lists the supported versions, the type mapping, the dependencies, the generator options and the idioms. The shared pipeline is in [how-it-works.md](../how-it-works.md) and the cross-language type table is in [types.md](../types.md).

## Supported versions

| What | Version | Where it comes from |
|---|---|---|
| Kotlin | 2.2.0 | `kotlin("multiplatform") version "2.2.0"` in `build.gradle.kts` |
| JVM | 17 or newer | the generated README; detekt's `jvmTarget` is `17` |
| Gradle | 8.10 or newer, or 9.x | the generated README; the SDK ships no Gradle wrapper |
| Ktor | 3.5.0 | `build.gradle.kts` |

The build does not pin a JVM toolchain: it uses whichever JDK is on the `PATH`, and only the static analysis targets 17. The floors are written once, in the manifest above. The support policy is the oldest non-EOL runtime, but the JVM 17 floor is an open owner decision: the generator's notes record that JDK 17 is not the oldest supported JDK, and that lowering it would change the toolchain and the pinned plugins, so nothing has been changed.

## Type mapping

| OpenAPI | Kotlin type | Notes |
|---|---|---|
| `string`, `format: date` | `java.time.LocalDate` | JVM only |
| `string`, `format: date-time` | `java.time.OffsetDateTime` | keeps the offset |
| `string`, `format: time` | `java.time.LocalTime` | |
| `string`, `format: duration` | `java.time.Duration` | sent and read as a protobuf-JSON duration string such as `3600s` |
| `string`, `format: uuid` | `kotlin.uuid.Uuid` | experimental in Kotlin 2.2, so generated files opt in with `ExperimentalUuidApi` |
| `string`, `format: byte` | `ByteArray` | base64 on the wire |
| `string`, `format: binary` | `ByteArray` | the whole body is held in memory |
| `string`, `format: uri` | `io.ktor.http.Url` | Ktor's parser normalises some input, for example `https://example.com` becomes `https://example.com/`; `uri-reference` and `uri-template` stay `String` |
| `string`, `format: email`, `hostname`, `ipv4`, `ipv6`, and plain `string` | `String` | no dedicated type |
| `integer`, `format: int32` | `Int` | |
| `integer`, `format: int64` | `Long` | |
| `integer`, no format | `Int` | 32 bits; see Under review |
| `number`, `format: float` | `Float` | |
| `number`, `format: double` | `Double` | binary floating point, so not for money |
| `number`, no format, or `format: decimal` | `java.math.BigDecimal` | exact; serialized through a precision-preserving serializer |
| `boolean` | `Boolean` | |
| `array` | `List` | |
| `array` with `uniqueItems` | `Set` | built as a `LinkedHashSet`; the order on the wire is the set's iteration order and should not be relied on |
| `object` with `additionalProperties` | `Map` | `T` is the declared value type; a free-form bucket keeps the any-type |
| free-form `object` | `@Contextual Any?` | |
| nullable or optional property | `T?` | an absent key and an explicit `null` read the same; a required and nullable property keeps its key and is written as `null` when unset |
| `enum` | `enum class` | an unknown value fails with `SerializationException`; a `$ref` to an enum with a `default` next to it starts at that member, whether the model is built or decoded without the key |
| `oneOf`, `anyOf` | a wrapper class | see Idioms |
| file upload and download | `ByteArray` | |

A schema named `Error` is generated as `ModelError`.

## Dependencies

| Dependency | Why |
|---|---|
| `ktor-client-core`, `ktor-http` 3.5.0 | the HTTP client API and the `Url` type |
| `ktor-client-cio` 3.5.0 (JVM source set) | the default HTTP engine |
| `kotlinx-serialization-json` 1.11.0 | JSON encoding and decoding |
| `kotlinx-coroutines-core` 1.11.0 | every operation is a `suspend` function |
| `kotlinx-datetime` 0.7.0 | declared in the manifest but not imported by any generated source file today |
| `opentelemetry-api` 1.62.0 (compile only) | W3C trace context when OpenTelemetry is on the class path |
| `org.brotli:dec` and `zstd-jni` (optional at run time) | decoding `br` and `zstd` responses; they are not declared in the main dependencies, so add them yourself to enable those encodings |

The generated project also configures ktlint, detekt, Kover and Dokka, and tests run on `kotlin-test-junit5` with Ktor's mock engine; none of those are runtime dependencies.

## Generator options

| Option | Default | Effect |
|---|---|---|
| `invokerPackage` | `org.openapitools` | root Kotlin package |
| `sourceFolder` | `src/main/kotlin` | source directory |
| `groupId` | the invoker package | Gradle group |
| `artifactId` | last segment of the invoker package plus `-client` | project and artifact name |
| `artifactVersion` | `1.0.0` | project version; not derived from the spec |
| `repositoryUrl` | none | written to the POM `scm` and the README |
| `homepageUrl` | the repository URL | project home page |
| `authors` | none | comma-separated names, written to the POM developers |
| `documentationUrl` | none | linked from the README |
| `issueTrackerUrl` | none | POM `issueManagement` and the README |
| `clientClassName` | `Client` | name of the client facade |
| `errorPrefix` | `OpenAPI` | prefix of the root error type |
| `envVarPrefix` | the API title in upper snake case without a trailing `SDK`, else `API` | prefix of the credential environment variables, see Authentication |
| `generateTests` | `true` | emit the full test suite |
| `generateUnitTests` | `false` | emit only the spec-independent unit tests |

## Idioms

- **Errors.** Failures derive from the root exception and, for HTTP failures, from `ApiException` with `statusCode`, the headers, the body, `requestMethod` and `requestPath` (without the query string). `NetworkException` and `NetworkTimeoutException` mean no response arrived. Caller mistakes raise `IllegalArgumentException`.
- **Retries.** Up to two retries by default (`TransportOptions.maxRetries`, `0` turns them off), after a network error before any response and after 408, 409, 429 and 5xx except 501. The wait starts at 0.5 seconds, doubles up to 8 seconds, loses up to 25% to jitter and is replaced by `Retry-After` (at most 60 seconds). Retries share the end-to-end timeout, which defaults to 10 000 ms, and a timeout itself is never retried.
- **Cancellation.** Every operation is a `suspend` function, so cancelling the coroutine (or using `withTimeout`) aborts the request and raises `kotlinx.coroutines.CancellationException`, never `NetworkTimeoutException`.
- **Authentication.** Basic, bearer and API key authenticators, plus OAuth 2 and OpenID Connect. Every credential is the first of three: the argument you pass, then an environment variable read once when the authenticator is built (an empty value counts as unset), then, for a bearer token or an API key, a `tokenProvider` or `apiKeyProvider` lambda that is called on every request. A credential found in none of them fails with `IllegalArgumentException` naming the variable, where the authenticator is built. `BearerAuthenticator`, `ApiKeyAuthenticator` and `BasicAuthenticator` read `{PREFIX}_TOKEN`, `{PREFIX}_API_KEY`, and `{PREFIX}_USERNAME` with `{PREFIX}_PASSWORD`. The authenticator generated for each security scheme reads the variable of its own scheme, `{PREFIX}_{FIELD}`, or `{PREFIX}_{SCHEME}_{FIELD}` when two schemes have a field of the same name, for every field including an OAuth 2 client id and secret; the generated README lists them. `{PREFIX}` is the `envVarPrefix` option. `Client.withToken(host, accessToken = null)` reads `{PREFIX}_TOKEN`. **Credentials per scheme.** `Client(host, <scheme> = ..., transportOptions)` takes one optional credential per distinct security scheme, named after the scheme in camel case (a Kotlin keyword is back-quoted) and typed as that scheme's authenticator. Each operation uses the credential of the first scheme its spec lists that you passed, and an operation none of whose schemes you passed falls back to the authenticator of `Client(authenticator)` when there is one and is otherwise sent without a credential; an operation declared `security: []` never sends one. There is no per-call scheme override, so the `<Operation>Options` classes carry no `auth`.
- **Immutability.** Models are `data class`es with `val` properties; copy them with `copy(...)`.
- **Unions.** A `oneOf` or `anyOf` model is a wrapper class holding the decoded variant in `actualInstance`, with a hand-written serializer that dispatches on the discriminator when there is one. Equality compares the held variants. Each variant has a `from<Variant>(value)` factory on the companion object, an `is<Variant>` property and an `as<Variant>()` function that returns the variant or `null` (a variant that is a generic type, such as `List<String>`, has only the factory, because the type is erased at run time). An operation with several `2xx` responses returns a union decoded by the status that arrived.
- **Optional arguments.** Optional parameters use Kotlin default arguments. Every operation takes an options object, also one with no parameters and no security, and per-call `timeout`, `headers` and `maxRetries` go in it; an operation with nothing of its own gets an options object that holds only those three.
- **Raw responses.** Every operation has a `*WithHttpInfo` variant returning an `ApiResult`.
- **Closing.** `Client.close()` is graceful and idempotent: a request already in flight finishes, the transport the SDK created is released after the last one, a later request fails with `IllegalStateException` ("ApiClient has been closed and can no longer send requests"), and a Ktor client you supplied is never closed.
- **Construction and decoding.** A model constructor never validates or raises, whatever the value (an out-of-range number, an out-of-pattern string, an empty collection). A payload is checked only when it is decoded: a required property that arrives as `null` or is missing, and an enum string outside the declared set, fail with `SerializationException`.
- **Cookie parameters.** A cookie value is sent as given when every character is a legal RFC 6265 cookie-octet, and otherwise the call fails with `IllegalArgumentException` before anything is sent. The message says to percent-encode the value first, which is yours to do: `%` is a legal cookie-octet, so a value you encoded goes out unchanged. A cookie carried as `content` is serialised by the SDK and percent-encoded, since you pass an object rather than text.
- **Custom transport.** `TransportOptions.builder().httpClient(myKtorClient)` sends requests through your own Ktor `HttpClient`; combining it with `proxy`, `caCertPath` or `verifySsl(false)` fails at `build()`.

## Known limits

- Request and response bodies are held in memory; there is no streaming. To stream, inject your own Ktor `HttpClient`.
- Date, time and decimal types are `java.time` and `java.math` types, so the generated code compiles only for the JVM target even though the project uses the Multiplatform plugin.
- When no proxy is configured, the Ktor CIO engine asks the JVM's default `ProxySelector`, so `http.proxyHost` and the system proxy settings apply. Set your own `ProxySelector` to override this.
- Schema constraints are not checked on the client; the model layer checks only JSON types and required fields.
- `dependentRequired` and `dependentSchemas` are not enforced.
- Webhooks and callbacks, XML bodies, Link Objects and `mutualTLS` security schemes are not generated.
- Only `http://` and `https://` proxies are accepted; SOCKS is rejected.
- A header sent on several lines is joined with `, `.
- The SDK writes no logs; install the Ktor `Logging` plugin on your own `HttpClient`.
- No `LICENSE` file is generated.

## Under review

These are proposals from a review of the type mapping against current Kotlin. None of them is decided, and none changes what the generator produces today.

- Decide between staying JVM-only and becoming multiplatform. Today `kotlinx-datetime` is declared but unused while the code uses `java.time`. Staying JVM-only would mean dropping the dependency; going multiplatform would mean `kotlinx.datetime` types, `kotlin.time.Instant` and `kotlin.time.Duration`.
- Map `format: duration` to `kotlin.time.Duration`, keeping the protobuf-JSON string form.
- Map free-form objects to `kotlinx.serialization.json.JsonElement` instead of `@Contextual Any?`.
- Map `type: integer` with no format to `Long` instead of `Int`, as one decision shared with Java and C#.
- Emit a sealed interface with data class variants for unions that have no discriminator, instead of the `actualInstance` wrapper.
- Offer a streaming type (Ktor `ByteReadChannel` or a kotlinx-io `Source`) for binary bodies as an additional overload.
- Raise the Kotlin floor from 2.2.0 to 2.4.0, which the review says would make `kotlin.uuid.Uuid` stable and remove the opt-in. This is not required, and it is the owner's decision.
