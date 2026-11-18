# Java

The Java generator (`java-plus`, class `BetterJavaCodegen`) produces a Maven project. This page lists the Java versions it supports, how OpenAPI types map to Java types, what the generated project depends on, which generator options it reads, and where it differs from the other eleven SDKs. The shared pipeline is in [how-it-works.md](../how-it-works.md) and the cross-language type table is in [types.md](../types.md).

## Supported versions

| What | Version | Where it comes from |
|---|---|---|
| Minimum Java | 21 | `<release>21</release>` in the generated `pom.xml` |
| Build tool | Maven 3.9 or newer | the generated README |
| JDK the generator's own checks run on | Temurin 25 | the spec container image |

Java 21 is a long-term-support release, and it is the floor because the generated code is compiled with `-Xlint:all`, `failOnWarning`, Error Prone and NullAway, and is written for the language features of 21. The floor is written once, in the pom. The support policy is to keep the floor at the oldest Java release that still receives updates and to move it up when that release reaches end of life; the floor only moves together with a regenerated golden and an update of this page.

## Type mapping

| OpenAPI | Java type | Notes |
|---|---|---|
| `string`, `format: date` | `java.time.LocalDate` | |
| `string`, `format: date-time` | `java.time.OffsetDateTime` | keeps the offset |
| `string`, `format: time` | `java.time.LocalTime` | no offset |
| `string`, `format: duration` | `java.time.Duration` | sent and read as a protobuf-JSON duration string such as `3600s`, the same as the other SDKs; calendar durations (months, years) cannot be held |
| `string`, `format: uuid` | `java.util.UUID` | written in lower case |
| `string`, `format: byte` | `byte[]` | base64 on the wire |
| `string`, `format: binary` | `java.io.InputStream` | request and response; the body is still read whole into memory |
| `string`, `format: uri` | `java.net.URI` | `uri-reference` and `uri-template` stay `String` |
| `string`, `format: email`, `hostname`, `ipv4`, `ipv6`, and plain `string` | `String` | no dedicated type; the JDK has no address parser that accepts only a literal before Java 22 |
| `string`, `format: decimal` | `BigDecimal` | read from a JSON string only and written as a JSON string |
| `integer`, `format: int32` | `Integer` | |
| `integer`, `format: int64` | `Long` | |
| `integer`, no format | `Integer` | 32 bits; see Under review |
| `number`, `format: float` | `Float` | |
| `number`, `format: double` | `Double` | binary floating point, so not for money |
| `number`, no format, or `format: decimal` | `java.math.BigDecimal` | exact; the serializer round-trips the JSON number so no precision is lost |
| `boolean` | `Boolean` | |
| `array` | `java.util.List` | built as an `ArrayList`, copied into an unmodifiable list when the model is built |
| `array` with `uniqueItems` | `java.util.Set` | built as a `LinkedHashSet`, so it keeps insertion order; the SDK does not sort it |
| `object` with `additionalProperties` | `java.util.Map` | built as a `HashMap`. `T` is the declared value type; a free-form bucket keeps the any-type |
| free-form `object` | `Object` | whatever Jackson decodes: maps, lists and scalars |
| nullable property | the boxed type | no separate "absent" state; a required and nullable property must be present when decoding and is always written, `null` included |
| `enum` | nested Java `enum` | an unknown value fails with `SerializationException` |
| `oneOf`, `anyOf` | a wrapper class | see Idioms |
| file upload and download | `InputStream` | same as `binary` |

A schema named `Error` is generated as `ModelError`, because `Error` would shadow `java.lang.Error`. `true`, `false`, `null` and `_` are escaped when used as identifiers.

## Dependencies

| Dependency | Why |
|---|---|
| `jackson-core`, `jackson-annotations`, `jackson-databind` 2.19.4 | JSON encoding and decoding |
| `jackson-datatype-jsr310` 2.19.4 | `java.time` support in Jackson |
| `jackson-databind-nullable` 0.2.6 | the nullable support type used by the models |
| `jsr305` 3.0.2 | `@Nullable` annotations, checked by NullAway at build time |
| `opentelemetry-api` 1.62.0 (optional) | W3C trace context on outgoing requests when OpenTelemetry is on the class path |
| `org.brotli:dec` 0.1.2 and `zstd-jni` 1.5.6-4 (optional) | decoding `br` and `zstd` responses; without them only `gzip` and `deflate` are advertised |
| `spotbugs-annotations`, `jakarta.annotation-api` (provided scope) | compile-time annotations only |

HTTP is the JDK's own `java.net.http.HttpClient`, so there is no HTTP library to depend on. The generated project also wires Spotless (google-java-format), Checkstyle, SpotBugs, JaCoCo, Error Prone and NullAway, and tests run on JUnit with AssertJ and Testcontainers; none of those are runtime dependencies. The generator only accepts dependencies that are pure JVM libraries: `zstd-jni` is the one native-code exception and is optional.

## Generator options

| Option | Default | Effect |
|---|---|---|
| `invokerPackage` | `org.openapitools` | root Java package of the SDK |
| `sourceFolder` | `src/main/java` | source directory inside the project |
| `groupId` | the invoker package | Maven group |
| `artifactId` | last segment of the invoker package plus `-client` | Maven artifact |
| `artifactVersion` | `1.0.0` | Maven version; not derived from the spec |
| `repositoryUrl` | none | written to the pom `scm` and `url` and to the README |
| `homepageUrl` | the repository URL | project home page |
| `authors` | none | comma-separated names, one pom developer each |
| `documentationUrl` | none | linked from the README |
| `issueTrackerUrl` | none | pom `issueManagement` and the README |
| `clientClassName` | `Client` | name of the client facade |
| `errorPrefix` | `OpenAPI` | prefix of the root error type; Java appends `Exception` |
| `envVarPrefix` | the API title without a trailing `SDK`, in upper snake case, else `API` | prefix of the environment variables the per-scheme authenticators read their credentials from |
| `generateTests` | `true` | emit the full test suite, including the spec-coupled tests |
| `generateUnitTests` | `false` | emit only the spec-independent unit tests |

## Idioms

- **Errors.** Every failure derives from the root `...Exception` and, for HTTP failures, from `ApiException`, which carries the status, the lower-cased response headers, the body, the request method and the request path without its query string; every error constructor, from `ApiException` down to each status exception, takes the method and path as its last two arguments. `NetworkException` and `NetworkTimeoutException` mean no response arrived; `TooManyRequestsException` is HTTP 429. A response body that does not decode raises `SerializationException`, which carries the status code, the headers, the raw body and the request method and path without its query string, so you can log the response that could not be read. `SerializationException` raised with no response behind it, such as a bad request body, has status `0` and none of those fields. Mistakes by the caller raise `IllegalArgumentException` or `IllegalStateException`.
- **Retries.** Two retries by default (`TransportOptions.builder().maxRetries(n)`, `0` turns them off), after a network error before any response and after 408, 409, 429 and 5xx except 501. The wait starts at 0.5 seconds, doubles up to 8 seconds, loses up to 25% to jitter and is replaced by `Retry-After` (at most 60 seconds). Retries share the one end-to-end timeout, and a request with a stream body is never retried.
- **Timeouts and cancellation.** `timeout` is one end-to-end budget in milliseconds with no default. A thread interrupt raises `java.util.concurrent.CancellationException`, never the timeout error.
- **Authentication.** Basic, bearer and API key authenticators, plus OAuth 2 (client credentials, password, authorization code, implicit) and OpenID Connect. After a 401 from an OAuth 2 or OpenID Connect call the cached access token is dropped and the request is sent once more. The authenticator generated for each security scheme reads a credential that is left out (`null` or empty) from an environment variable, read once when the authenticator is built: `{PREFIX}_{FIELD}` in upper snake case, such as `PETSTORE_TOKEN`, where the prefix is the `envVarPrefix` option or the API title, and the scheme id is added to the name when two schemes share a field name. A value you pass wins, an empty variable counts as unset, and a credential found nowhere fails the constructor with `IllegalArgumentException`. `BearerAuthenticator(host, tokenProvider)` takes a `Supplier<String>` instead of a token, which runs before every request, and `BearerAuthenticator(host, token, tokenProvider)` takes exactly one of the two; passing both is refused. The three general authenticators never read the environment, and the generated README lists the variable of every scheme. **Credentials per scheme.** `Client.builder(host)` takes one optional credential per distinct security scheme, a setter named after the scheme in camel case (`petStoreBasic`, `apiKeyHeader`) typed as that scheme's authenticator, and `transportOptions(...)`. Each operation uses the credential of the first scheme in its own security list that the client holds; an operation none of whose schemes the client holds falls back to the single authenticator of `new Client(authenticator)` when there is one and is sent without a credential otherwise, and an operation that declares `security: []` never sends one. There is no per-call scheme override, so a `<Operation>Options` object carries no authenticator.
- **Immutability.** Model fields are `final`, built with `Model.builder()` and copied with `toBuilder()`. Collections are copied into unmodifiable ones. A property that is a `$ref` to a string enum with a `default` written next to the `$ref` starts at that default, so a model built without it and a payload that omits it both carry it; a `$ref` to anything other than an enum with a sibling default is not initialised.
- **Unions.** A `oneOf` or `anyOf` model is a wrapper with a typed factory per variant (`Union.ofVariant(v)`), `isVariant()`, and `asVariant()` returning an `Optional`; the wrapper also exposes `getActualInstance()`. A discriminated parent is an abstract base class with Jackson subtype annotations. An operation with several `2xx` responses returns a union decoded by the status that arrived.
- **Optional arguments.** Java has no default arguments, so optional parameters are `@Nullable` and per-operation settings go in a generated `<Operation>Options` object passed last. Every operation has one, even an operation with no query, header, form or cookie parameter and no security. Per-call `timeout`, `maxRetries` and `headers` live there, and omitting the object (or passing `null`) uses the transport's settings.
- **Lifecycle.** The client is `AutoCloseable`, and `close()` is graceful and idempotent. The first call marks the client closed and returns at once; requests already in flight finish, and the transport the SDK created is released when the last of them completes (immediately when none is in flight). A later `close()` does nothing. A request issued after closing throws `IllegalStateException` with the message `ApiClient has been closed and can no longer send requests`. A `java.net.http.HttpClient` you passed in through `TransportOptions` is never closed.
- **Cookie parameters.** A cookie parameter is sent exactly as given when every character is a legal RFC 6265 cookie-octet (that includes `%`, so a value you percent-encoded yourself goes out unchanged). A value with a space, double quote, comma, semicolon, backslash, control character or non-ASCII character throws `IllegalArgumentException` before any request, and you percent-encode it first. The SDK never encodes a cookie value for you.
- **Models are not validated when built.** The builder and the constructors never check a value against a schema constraint, and the type system already stops a wrong type or an enum string that is not a constant from reaching them. A payload is checked only when it is decoded: a wrong-typed value, an enum string outside the declared set and a missing or `null` required property fail with `SerializationException`. A model built without a required property still fails in `build()` (the required-argument constructor and the builder are the way to make one).
- **Raw responses.** Every operation has a `...WithHttpInfo` variant returning `ApiResult<T>`.
- **Custom transport.** `TransportOptions.builder().httpClient(client)` sends requests through your own `java.net.http.HttpClient`; combining it with `proxy`, `caCertPath` or `verifySsl(false)` fails at `build()`.
- **GraalVM.** The SDK ships reflection metadata for every model, so it works in a `native-image` build; the optional brotli and zstd decoders do not.

## Known limits

- Request and response bodies are read whole into memory. There is no streaming and no server-sent events API.
- Schema constraints (`pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems`, `maxItems`) are not checked on the client; the server validates them.
- `dependentRequired` and `dependentSchemas` are not enforced.
- Webhooks and callbacks, XML bodies, Link Objects and `mutualTLS` security schemes are not generated. For mutual TLS, configure the certificate on the `HttpClient` you pass in.
- Only `http://` and `https://` proxies are accepted; SOCKS is rejected.
- A property that is optional and one that is nullable both read as `null`.
- A header sent on several lines is joined into one string with `, `.
- The SDK writes no logs and has no logging hook.
- No `LICENSE` file is generated next to the pom.

## Under review

These are proposals from a review of the type mapping against current Java. None of them is decided, and none changes what the generator produces today.

- Map `type: integer` with no format to `Long` (or `BigInteger`) instead of `Integer`. This would be a public API change, so it is a single decision for all twelve languages together with Kotlin and C#.
- Model unions as a sealed interface with record variants. The floor of 21 already allows `sealed` types and pattern-matching `switch`, so the floor would not change, but the public shape of every union would.
- Build maps as `LinkedHashMap` instead of `HashMap` so that `additionalProperties` keep their wire order. This is an implementation detail, not a type change.
- Keep `java.time` types as they are. The review found nothing in newer JDKs that replaces `LocalDate`, `OffsetDateTime`, `LocalTime` or `Duration`. Adding `java.time.Period` for calendar durations would need a format decision, not a newer JDK.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. Reads `Context.current()` on the caller's thread (`span.makeCurrent()`); the call is blocking, so no hand-off precedes the read.
