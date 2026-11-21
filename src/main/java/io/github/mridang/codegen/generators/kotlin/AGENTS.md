# Kotlin SDK generator

This generator emits a Kotlin Multiplatform client (`kotlin("multiplatform")` 2.2.0 with a `jvm()` target and the serialization plugin) whose HTTP stack is Ktor 3.5.0 (`ktor-client-core` and `ktor-http` in common code, the CIO engine in `jvmMain`), with kotlinx-serialization-json 1.11.0 for the wire format, kotlinx-coroutines 1.11.0 for the `suspend` API and kotlinx-datetime 0.7.0. The generated tests run on JUnit 5 through `kotlin-test-junit5` with `ktor-client-mock`, and the JVM target is 17. The tooling floor is Gradle (the specs use the `gradle:9-jdk21` image), ktlint 12.1.2 plugin pinned to ktlint 1.5.0 in `ktlint_official` style, detekt 1.23.7 with the formatting and library-author rulesets, and Kover 0.9.1 for coverage. Every operation is a `suspend` function, models are `@Serializable` data classes, and errors are unchecked exceptions rooted at one SDK exception class.

## Read first

- [Shared cross-language rules](../AGENTS.md): each cross-language decision is written once there; this file only states how kotlin realises it.
- [Repository root AGENTS.md](../../../../../../../../../AGENTS.md): parity workflow, error contract, guardrails, forced divergences.
- [Codegen class](BetterKotlinCodegen.java).
- [Template folder](../../../../../../../resources/templates/kotlin/).
- Spec classes: [src/spec/java/io/github/mridang/codegen/spec/kotlin/](../../../../../../../../spec/java/io/github/mridang/codegen/spec/kotlin/) (`KotlinBuildSpec`, `KotlinFormattingSpec`, `KotlinLintingSpec`, `KotlinReservedWordsSpec`, `KotlinClientSpec`, shared interface `KotlinSpec`).
- Codegen unit test: [BetterKotlinCodegenTypeMappingTest](../../../../../../../../test/java/io/github/mridang/codegen/generators/kotlin/BetterKotlinCodegenTypeMappingTest.java) and the `GenerateKotlinClientTest` that regenerates the golden.
- Golden folder: [src/spec/resources/generated/kotlin/](../../../../../../../../spec/resources/generated/kotlin/).
- Reserved words: `src/main/resources/reserved-words/kotlin.txt`, pinned by `KotlinReservedWordsSpec`.

## Layout

| Path (under `src/main/resources/templates/kotlin/`) | What lives there | Edit this to change |
|---|---|---|
| `models/model.mustache` | data classes, enums, discriminated oneOf wrappers, hand-written `equals`/`hashCode`/`toString` | model shape, required and nullable handling, defaults, equality |
| `api/api.mustache`, `api/options.mustache` | one API class per tag, the per-operation Options classes, server variants, query/header/path/cookie building | operation signatures, parameter order, parameter encoding, deprecation markers |
| `errors/*.mustache`, `root_exception.mustache`, `api_error.mustache` | the exception hierarchy and the rendered error text | error classes, messages, headers on errors |
| `auth/*.mustache`, `auth/oauth/*.mustache` | Basic, Bearer, ApiKey authenticators and the OAuth2 and OpenID Connect flows with the token manager | auth, scopes, token handling, OIDC discovery |
| `default_api_client.mustache`, `api_client.mustache`, `base_api.mustache`, `http_client_factory_jvm.mustache` | the Ktor transport, redirects, decompression, multipart writer, response decoding | transport behaviour, timeouts, redirects, proxy, TLS |
| `transport_options.mustache`, `configuration.mustache`, `client.mustache`, `servers.mustache`, `server_configuration.mustache` | builders for transport options, configuration and the client facade | transport options, builder surface |
| `object_serializer.mustache`, `value_serializer.mustache`, `header_selector.mustache` | JSON (de)serialization, strict primitive serializers, parameter stringification and encoding | wire encoding of values, date/uuid/url/bytes handling |
| `byte_stream.mustache`, `api_stream_response.mustache`, `errors/StreamException.mustache` | `ByteStream` (the binary response carrier), `ApiStreamBody` and `ApiStreamResponse` (the transport result with an unread body), `StreamException` | streaming types |
| `test/**` | the generated unit tests and the Chasm and Squid container fixtures | generated tests (mirror the source file: `Foo.kt` is tested by `FooTest.kt`) |
| `readme.mustache`, `skills.mustache` | README and SKILLS.md of the client | user documentation |
| `build_gradle.mustache`, `settings_gradle.mustache`, `gradle_properties.mustache`, `detekt_yml.mustache`, `editorconfig.mustache`, `makefile.mustache`, `gitignore.mustache` | build, lint and format configuration | dependency versions, analyzer switches |

The codegen class `BetterKotlinCodegen.java` owns type mapping, reserved words, default-value rendering, string-literal and KDoc escaping, supporting-file registration, the artifactId default and the equality flags for models.

## Idioms (deliberate differences from the other eleven)

| Topic | What kotlin does | Why |
|---|---|---|
| Async model | every operation is `suspend`; cancellation is coroutine cancellation | Kotlin's native concurrency; see S7 |
| Transport | Ktor `HttpClient` with the CIO engine | the Ktor ecosystem standard; the client type is what S9 accepts |
| Models | `@Serializable` data classes; a model needing value semantics for `ByteArray`, `BigDecimal` or the additionalProperties bucket gets a hand-written `equals`/`hashCode` | a data class compares arrays by identity and `BigDecimal` by scale; see D24, D23 |
| `format: binary` | `BodySource` for a raw request body (D121), `ByteArray` for a multipart file part, `ByteStream` for a success response (D119): `suspend fun read(buffer)`, a cold `chunks(): Flow<ByteArray>`, `suspend fun readAll()` that reads everything and closes, an idempotent `close()` | the response is read as consumed; the carrier is coroutine-native, which is what design 6.1 asks for kotlin |
| allOf with a discriminator | sealed interface plus flattened data classes | a data class cannot be `open`; see S4 |
| Required and nullable | `T?` with no default | the absence of a default is what makes the key required; see S5 |
| Decimals | bare `format: number` is `java.math.BigDecimal`, precision-preserving | root AGENTS.md "format: number" entry; java and csharp do the same |
| Dates and ids | `OffsetDateTime`, `LocalDate`, `LocalTime`, `Duration`, `kotlin.uuid.Uuid`, `java.net.URI` through contextual serializers (D97) | the platform types; only absolute `format: uri` maps to `Url`, `uri-reference` and `uri-template` stay `String` |
| Exceptions | unchecked, cause supported natively | `Throwable.cause` is the platform mechanism, so `SerializationError.cause` and `OAuth2TokenError` constructors are allowed to differ in the API surface check |
| Free-form `Any` | `@Contextual Any` | kotlinx-serialization has no built-in serializer for `Any`; the codegen annotates it |
| Soft keywords | `file` is escaped to `_file` | repository policy, see AGENTS:file-keyword |
| Resource management | `AutoCloseable`, used with `use { }` | JVM idiom for `close()` |
| Scheme credentials | one `Client(host, <scheme>: <Authenticator>? = null, ..., transportOptions)` constructor, a scheme named like a Kotlin hard keyword back-quoted | named and default arguments give one argument per scheme without an overload per combination; see F857 |
| Multipart | a hand-written `MultipartWriter` buffers the body | Ktor's `formData()` injects a per-part `Content-Length`; see D89 |

## Settled decisions (do not re-open in review)

Status words: "implemented" means the template or codegen already does it; "pending" means the owner decided it and the kotlin templates have not caught up, so a diff that moves toward it is correct and a diff that moves away is not.

### Typed errors per declared schema (issue 123, issue 131 item F03)
Rule: kotlin generates one `open class <Model>Exception` per distinct declared error schema in the errors package, extending the status class of its status (every status class is `open`); each operation passes its own `{status -> ErrorFactory}` map as `errorSchemas` to `invokeApiForResult`, and `ApiException.fromResponse` picks the exact status, then the range, then `default`. The schema class overrides `errorBody` covariantly (`ApiException` is not generic); a body that does not decode gives the status class with the raw body and the failure as `cause`. `isRetryable` and `requestId` are properties of `ApiException`. A response body that cannot be decoded raises a `SerializationException` carrying `statusCode`, `responseHeaders`, `responseBody`, `requestMethod` and `requestPath` (no query).
Where: `api_error.mustache`, `errors/SchemaException.mustache`, `errors/ErrorFactory.mustache`, `errors/SerializationException.mustache`, `base_api.mustache`, `api/api.mustache`.
Test: generated `ApiExceptionTest` (errors201 to errors204) and `BaseApiTest` (errors205).

### Out of scope: callbacks, webhooks, XML, Link Objects, mutual TLS (S11, 2026-11-16)
Rule: kotlin does not generate callbacks and webhooks (#53), XML bodies and `nodeType` (#70), Link Objects (#78) or mutual TLS (#90), and the README says so.
Why: owner closed those backlog issues as out of scope for all twelve; do not flag their absence.
Where: `readme.mustache`.
Test: none (absence).

### Scope order, lowercase uuid, ASCII blank keys, error cause (S14, 2026-11-16)
Rule: OAuth scopes keep the spec's declared order, uuid is written lowercase and validated, an api key or bearer token counts as blank only when empty or ASCII whitespace (space, `\t`, `\n`, `\r`, `\f`, `\v`), and every status-specific error accepts an optional cause.
Why: one byte-identical behaviour across the twelve; see D04, D19, D67.
Where: `BetterKotlinCodegen.formatKotlinScopes`, `auth/bearer_authenticator.mustache`, `auth/api_key_authenticator.mustache`, `errors/*.mustache`, `object_serializer.mustache`.
Test: generated `BearerAuthenticatorTest`, `ApiKeyAuthenticatorTest`, `ApiExceptionTest`, `ObjectSerializerTest`.

### Secrets and redaction (S15, S16, 2026-11-16)
Rule: the string forms of `Configuration` and `TransportOptions` print the proxy password and any default header named Authorization, Proxy-Authorization, Cookie or containing key, token or secret as `***`. Proxy URL validation happens in `build()`, not in the builder setter. `caCertPath` is validated even when `verifySsl` is false. A caller-supplied stream or file handle is never closed by the SDK.
Why: one policy for all twelve; see D73, D79.
Where: `transport_options.mustache`, `configuration.mustache`, `http_client_factory_jvm.mustache`.
Test: generated `TransportOptionsTest`, `ConfigurationTest`.

### Strict containers (S1, 2026-11-16) - implemented
Rule: a response that is a container of primitives (`Map<String, Int>`, `List<List<Int>>`) is checked element by element like a single primitive.
Why: container leaves had bypassed the strict primitive checks in six SDKs. Kotlin walks `serializer<T>().descriptor` against the parsed element before decoding and throws a `SerializationException`; annotating type arguments with `@Serializable(with = ...)` was rejected as fragile with reified serializers. This is D54.
Where: `object_serializer.mustache` (`deserialize`, the `Strict*Serializer` objects).
Test: generated `ObjectSerializerTest` (`getInventory` and `getMatrix` rows).

### Content parameters use the SDK serializer in every location (S2, D80, 2026-11-16) - implemented
Rule: `content:` parameters are encoded through `objectSerializer.serialize` in query, header, path and cookie, with identical bytes in all twelve.
Why: twelve different JSON encoders were in use. Query, header, path and cookie all go through `ValueSerializer.serializeContent`, which calls the SDK serializer. See also D50.
Where: `api/api.mustache`, `object_serializer.mustache`.
Test: generated `ValueSerializerTest` (`serializeContent` rows for header, path and cookie).

### Schema constraints are not enforced (S3, D55, 2026-11-16) - implemented
Rule: the client does not promise to enforce pattern, minLength, maxLength, minimum, maximum, multipleOf, minItems or maxItems, and kotlin adds no enforcement.
Why: the server owns validation; only python enforces, because pydantic does it natively and cheaply. The README section is titled "Schema constraints" and sits under Caveats; `dependentRequired` stays under Not supported.
Where: `readme.mustache`.
Test: none (absence); the petstore declares no such keyword.

### allOf with a discriminator is a sealed interface plus flattened data classes (S4, 2026-11-16) - implemented
Rule: the discriminated parent becomes a sealed interface, each subtype is a flattened data class that lists the common fields, and the subtype carries the discriminator. A model reused in two unions must not be re-parented: inheritance (here, the sealed interface) is only for the allOf-with-discriminator parent, and plain unions use interfaces or sealed types.
Why: an `open data class` does not compile, which is why kotlin cannot inherit like java, csharp, node, python, ruby, php and dart. `model.mustache` renders the discriminated parent (`isDiscriminatedBase`) as a sealed interface with a discriminator-reading serializer, and each subtype lists the common fields and implements it. Discriminated oneOf keeps its hand-written wrapper (`actualInstance` plus a serializer reading the discriminator).
Where: `models/model.mustache`.
Test: the discriminated fixtures in the petstore (`DryFood`, `WetFood`, `PetFood`) through `ComposedSchemaTest` and `ObjectSerializerTest`; the allOf fixtures `Vehicle` and `Shape` (bhv unions 301-304 in `ComposedSchemaTest`, bhv serializationobject 301 in `ObjectSerializerTest`).

### Required and nullable (S5, D66, 2026-11-16) - implemented
Rule: a required nullable property is `T?` with no default; the key must be present on decode (missing is an error), an explicit null is legal and kept, and encode always writes the key as null when unset.
Why: kotlin's `explicitNulls` and `encodeDefaults` are Json-wide, so the behaviour comes from the property having no default plus the Json configuration in `createDefaultJson`, not from a per-property annotation.
Where: `models/model.mustache` (constructor property line), `object_serializer.mustache`.
Test: `ObjectSerializerTest.requiredNullablePropertyKeepsItsKey`.

### uniqueItems order is unspecified; do not sort (S6, 2026-11-16) - implemented
Rule: sets go on the wire in the language's natural collection order (`LinkedHashSet` insertion order), documented in the README. Kotlin must not sort them.
Why: this supersedes D69, which proposed ascending scalar order. The sort (`sortScalarSets`, `SCALAR_ORDER`) is removed from `object_serializer.mustache`; the README says the order is unspecified.
Where: `object_serializer.mustache`, `BetterKotlinCodegen.getUniqueItemsSetType`.
Test: generated `ObjectSerializerTest` (`scalarSetKeepsItsInsertionOrderOnTheWire`).

### Cancellation is coroutine cancellation (S7, 2026-11-16) - implemented
Rule: no new parameter. Cancelling the calling coroutine aborts the request and raises `CancellationException`, never the timeout error; `NetworkTimeoutException` is only for the SDK's timeout.
Why: structured concurrency is what Kotlin callers already use (`withTimeout`, scope cancellation); a wrapper options object would be less natural. The transport rethrows `kotlinx.coroutines.CancellationException` untouched. The README has a top-level Cancellation section naming coroutine cancellation and `withTimeout`, and SKILLS has the same under Configuration.
Where: `default_api_client.mustache` (the catch that rethrows cancellation), `readme.mustache`, `skills.mustache`.
Test: generated `DefaultApiClientTest`.

### Parameter order (S8, 2026-11-16, owner confirmation pending) - implemented
Rule: required parameters, then server, then the optional-parameters Options object; kotlin has no per-call cancellation handle because the coroutine context carries it. Adding security never moves an argument.
Why: adding a security scheme must not break existing server-only call sites.
Where: `api/api.mustache`, `api/options.mustache`.
Test: generated `PetApiTest`.

### Injected transport (S9, 2026-11-16) - implemented
Rule: the client builder accepts a Ktor `HttpClient`, keeps the SDK defaults applied, never closes a caller-supplied client, and fails fast on conflicts with the SDK's own proxy, TLS or pool settings.
Why: OpenAI, Stainless, Speakeasy, Fern, Azure and Stripe SDKs all accept the standard client. `TransportOptions.builder().httpClient(...)` is the public path, `build()` rejects the conflicting settings, and `Client` is `AutoCloseable` and closes only a transport it created.
Where: `client.mustache`, `configuration.mustache`, `default_api_client.mustache`, `http_client_factory_jvm.mustache`.
Test: generated `ClientTest`, `DefaultApiClientTest`.

### Dependencies (S10, 2026-11-16)
Rule: a small pure library or a version bump is acceptable when it standardises behaviour cleanly; never a dependency that needs a native binary.
Why: native binaries break the container builds and the parity of the twelve.
Where: `build_gradle.mustache`.
Test: `KotlinBuildSpec`.

### Test runner speed and caches (S13, 2026-11-16)
Rule: Gradle's user home and build cache live under `/root/.cache/kotlin/gradle` with `org.gradle.caching=true`; spec parallelism is 3; do not touch `.github` workflows from this repository.
Why: the CI test job is CPU-bound; workflow issues are filed in mridang/action-runner-common (#47 to #50).
Where: `KotlinSpec.java`.
Test: the spec run itself.

### Multipart parts carry only Content-Disposition and Content-Type (D89)
Rule: no `Content-Transfer-Encoding` and no per-part `Content-Length`; the field name is `name="..."` with `\` and `"` backslash-escaped; filename directive follows the one rule of D90 and iterates code points.
Why: Ktor's `formData()` and `FormBuilder.append` inject a per-part Content-Length, so kotlin writes the body itself in `MultipartWriter` and buffers it so the request has one Content-Length. This replaces the earlier D38 plan to declare the Content-Length forced; it is not a forced divergence any more.
Where: `default_api_client.mustache` (`MultipartWriter`, `buildFilenameDirective`).
Test: generated `DefaultApiClientTest`. The test sees the body structurally, not byte for byte, because Ktor's multipart object is opaque; this depth limit is accepted (root AGENTS.md second audit tail).

### Multipart scalar parts and form values (D36, D39)
Rule: every primitive multipart part is a plain text part using `objectSerializer.stringify`; only objects and models are `application/json` parts. Form-urlencoded values use the same stringify; nested objects and maps are rejected.
Why: one stringify form across the twelve.
Where: `default_api_client.mustache` (`appendFormField`), `base_api.mustache`.
Test: generated `DefaultApiClientTest`, `BaseApiTest`.

### Content-Type rules for responses (D30, D31, D32)
Rule: a `format: binary` response is raw bytes and never JSON-parsed (text content types are returned as UTF-8 bytes, anything else is strict Base64 wrapped in `SerializationException`); a `format: byte` response is JSON by default with the strict decoder; a non-JSON success type on a non-binary return gives the text for `String` and a `SerializationException` naming the Content-Type for everything else.
Why: one rule per case. Kotlin carries a defaulted `binaryReturn` flag on `invokeApiForResult`, emitted from `returnProperty.isBinary`.
Where: `base_api.mustache`, `api/api.mustache`.
Test: generated `BaseApiTest`.

### Redirect, timeout and trust rules (D71, D73, D75, D76, D77)
Rule: an unparseable `Location` behaves like a missing one and returns the 3xx before the body is read; `caCertPath` is validated even with `verifySsl=false`; the SDK's own `Proxy-Authorization` is re-added after the redirect strip; `timeout` is one monotonic deadline across all hops and the body read (a per-request Ktor timeout of the remaining time, `NetworkTimeoutException` at zero); `timeout` must be null or positive and the builder `require`s it.
Why: one end-to-end budget and one validation point in all twelve.
Where: `default_api_client.mustache`, `http_client_factory_jvm.mustache`, `transport_options.mustache`.
Test: generated `DefaultApiClientTest`, `TransportOptionsTest`.

### Response and error shape (D13, D15, D16, D17, D18, D27, D92)
Rule: an `ApiException` for an unusable response carries the real response headers (every redirect refusal included); serializer messages are `Failed to serialize object to JSON: ...` and `Failed to deserialize JSON: ...`; the too-many-redirects message is `Too many redirects (exceeded N)`; the rendered `Response headers:` line is compact JSON with sorted keys (`ApiException.toString`); a leading BOM is stripped inside `parseToJsonElement` so the error-body path matches success; `ApiResult.headers` is a defensive copy (`response.headers.toMap()` in `invokeApiForResult`); headers returned to the caller drop `Content-Encoding` and `Content-Length` after a decode.
Why: one message grammar and one header contract in all twelve.
Where: `api_error.mustache`, `object_serializer.mustache`, `default_api_client.mustache`, `api_result.mustache`.
Test: generated `ApiExceptionTest`, `BaseApiTest`, `ObjectSerializerTest`, `DefaultApiClientUnitTest`.

### Request-header and User-Agent handling (D21, D22)
Rule: the User-Agent and X-Request-ID injection checks are case-insensitive (`keys.none { it.equals(name, ignoreCase = true) }`) and the caller's header always wins; the default User-Agent product token is the artifactId, which defaults to the invoker package's last segment plus `-client`, so `com.example.petstore` gives `petstore-client`.
Why: published package name as the product token, like the other SDKs; an explicit `artifactId` still overrides.
Where: `default_api_client.mustache`, `BetterKotlinCodegen.processOpts` (`userAgentDefault`).
Test: generated `DefaultApiClientTest`; `GenerateKotlinClientTest`.

### OAuth2 and OpenID Connect behaviour (D01, D02, D03, D05, D06, D07, D08, D09, D11)
Rule: OIDC rediscovery refreshes only the discovery document and keeps the exchanged session (the token manager is passed to the rebuilt delegate through an internal constructor, the exchanged flag lives in the manager, and `resolveDelegate` runs under a `kotlinx.coroutines.sync.Mutex` with an expiry re-check inside); the refresh grant carries client authentication; `exchangeCode` always POSTs the authorization_code grant inside `refreshMutex.withLock`; OIDC always requests `openid`; an empty implicit access token or empty `state` counts as unset; token and discovery error messages use one wording set without parser detail; `expires_in` and `max-age` use the `^[0-9]+(\.[0-9]+)?$` grammar with a one-year ceiling applied before date arithmetic; the empty authorization code message is "Authorization code must not be empty".
Why: the session must not be discarded by a cache refresh, and one wording across the twelve. Only a JSON string `refresh_token` is accepted (`JsonPrimitive.isString`), a numeric one is ignored.
Where: `auth/oauth/*.mustache`.
Test: generated `OAuth2TokenManagerTest`, `OAuth2AuthCodeAuthenticatorTest`, `OAuth2ImplicitAuthenticatorTest`, `OpenIdConnectAuthenticatorTest`.

### OAuth2 surface differences that are allowed
Rule: the token manager is internal support code, so `oauth2tokenmanager.exchanged` is internal and `oauth2tokenmanager.setapiclient` is an internal property; `OAuth2TokenError` constructors take an optional cause; `sendRequest` gets its per-request redirect option by inheriting the interface default (`noRedirect: Boolean = false`); `objectserializer`, `valueserializer` and `headerselector` are `internal`; `apierror.gettypederrorbody` takes a reified type parameter with no value argument; `serializationerror.cause` is the platform `Throwable.cause`.
Why: each is the idiomatic kotlin spelling of the same behaviour.

### Escaping and literals (D40, D41, D46, D45, D43)
Rule: spec strings written into code literals go through one escaper (`escapeKotlinString`: backslash, quote, `$`, newline, carriage return, tab); enum literals add the `$` to `\$` rule through `getEnumStringEscapes`; KDoc prose goes through `escapeKdoc`, which neutralises both `*/` and `/*` because Kotlin block comments nest; long literals are chunked to 80 columns by `kotlinChunked` without splitting an escape; the Gradle description uses `lambda.kotlinDollar`; colliding model names get the Model prefix from the kotlin reserved set.
Why: value-preserving literals, and a `$` in a spec must not become a string template.
Where: `BetterKotlinCodegen.java`, `servers.mustache`, `api/api.mustache`, `build_gradle.mustache`, `models/model.mustache`.
Test: `BetterKotlinCodegenTypeMappingTest.escapesApiKeyNamesForAKotlinStringLiteral` and the formatting and build specs.

### Default values (D59, D60, D61)
Rule: a numeric default is an exact literal of the field type (`Long` gets `L`, `Float` gets `f` and a decimal point, `Double` a decimal point, bare number `java.math.BigDecimal("1.5")`); string defaults are quoted and escaped; a default on a `$ref` enum is the typed member (`Availability.ON_HOLD`), kept by the no-op `fixEnumDefaultValue`; arrays default to `mutableListOf()` or `mutableSetOf()` and maps to `mutableMapOf()`; a required property with a default is filled when absent.
Why: the default must compile for its type and must apply when the key is missing while an explicit null is preserved.
Where: `BetterKotlinCodegen.toDefaultValue`, `fixEnumDefaultValue`.
Test: `GenerateKotlinClientTest` golden and generated `ObjectSerializerTest`.

### Parameter encoding (D47, D48, D49, D51, D81)
Rule: object-valued parameters are converted to a wire-keyed map through `ObjectSerializer.toWireMap` and expanded by the shared expanders; a path value of exactly `.` or `..` is rejected with `IllegalArgumentException`; `allowReserved` leaves the RFC 3986 query-legal reserved set literal and percent-encodes `#`, `[` and `]`; `format: byte` parameters are Base64-encoded in `stringify`; `format: time` is `HH:mm:ss` plus `.SSS` only when the millisecond part is non-zero, through a `LocalTime` serializer.
Why: one encoding across the twelve.
Where: `value_serializer.mustache`, `object_serializer.mustache`, `api/api.mustache`.
Test: generated `ValueSerializerTest`, `ObjectSerializerTest`.

### Decoding rules (D58, D62, D64, D83)
Rule: `format: byte` decode is strict RFC 4648 standard alphabet with required padding in `Base64ByteArraySerializer`; `format: uri` is checked as an absolute RFC 3986 URI before `Url(...)` and the normalisation is recorded as forced; non-discriminated oneOf and anyOf variants are tried in declaration order in the per-model build path; `format: number` decodes to `BigDecimal` text-exact.
Why: one decode contract.
Where: `object_serializer.mustache`, `models/model.mustache`.
Test: generated `ObjectSerializerTest`.

### Equality and string form of models (D23, D24)
Rule: models with a `ByteArray`, `Array`, `List<ByteArray>`, `Map<String, ByteArray>` or `BigDecimal` property, or an additionalProperties bucket, get a hand-written `equals`/`hashCode` (`contentEquals`, per-key comparison for maps, numeric comparison for `BigDecimal`, the `@Transient` bucket folded in); such models also get a field-dumping `toString` that is element-wise for byte arrays and appends the bucket; union wrappers get `equals`/`hashCode` too.
Why: the synthesized data-class `equals` compares arrays by identity and excludes the `@Transient` bucket (which is now a constructor parameter, see batch 6, so `copy()` carries it). `List<BigDecimal>` and `Map<String, BigDecimal>` fields still compare scale-sensitively because `eqDecimal` covers only a direct `BigDecimal` property; that is open (needs a spec), see Known limitations.
Where: `BetterKotlinCodegen.postProcessModels`, `models/model.mustache`.
Test: generated `ObjectSerializerTest` and the model tests.

### Deprecation markers on every public variant (root AGENTS.md Round 7)
Rule: every public variant of a deprecated operation carries `@Deprecated`: the data method, `*WithHttpInfo`, and each server-override or content-type overload, each under its own `{{#isDeprecated}}` guard.
Why: one floating `@Deprecated` before the first overload left the other variants bare. Do not consolidate them.
Where: `api/api.mustache`.
Test: generated `PetApiTest`.

### Required string parameters must not be empty (root AGENTS.md Round 7)
Rule: a required string parameter in any location throws `Missing the required parameter '<name>' when calling <Class>.<operation>` before dispatch, except a query parameter declared `allowEmptyValue: true`.
Why: an empty string is not a present value; one message in all twelve.
Where: `api/api.mustache`.
Test: generated `PetApiTest`.

### Response decompression (root AGENTS.md Round 7)
Rule: an unsupported `Content-Encoding` raises `IOException` from the manual decompressor (wrapped by the transport into the SDK exception with the status), a missing br or zstd codec raises instead of returning still-encoded bytes (`decodeViaInputStream`), an empty body passes through for any declared encoding (`data.isEmpty()`), and a chained coding is treated as unknown.
Why: never return still-encoded bytes; java and kotlin earlier returned `data` on `ClassNotFoundException`, which was a defect.
Where: `default_api_client.mustache`.
Test: generated `DefaultApiClientTest`.

### Multi-consumes content-type selector rejects unknown values (root AGENTS.md Round 6)
Rule: an unrecognised request content type throws `IllegalArgumentException`; null defaults to the first declared type.
Why: a typo never reaches the wire as an undeclared Content-Type.
Where: `api/api.mustache`, `header_selector.mustache`.
Test: generated `HeaderSelectorTest`.

### Doc comments and README metadata (root AGENTS.md Round 7)
Rule: model doc comments render descriptions through `{{#lambda.unescapeDocComment}}`; README `Version` uses `{{artifactVersion}}`, not `{{packageVersion}}`; the java/kotlin README decimal caveat says `BigDecimal` preserves precision and scopes the loss warning to `float` and `double`.
Why: the empty variable rendered a blank version, and the decimal caveat must match the generated code.
Where: `models/model.mustache`, `readme.mustache`.
Test: `GenerateKotlinClientTest` golden.

### The soft keyword `file` is escaped to `_file` (AGENTS:file-keyword)
Rule: `file` stays in `reserved-words/kotlin.txt` and is escaped, although it is only a soft keyword.
Why: repository policy is to escape every Kotlin keyword, soft included; the round-2 audit mislabelled this as unforced.
Where: `src/main/resources/reserved-words/kotlin.txt`.
Test: `KotlinReservedWordsSpec`.

### Free-form null retention is not aligned (closed audit item)
Rule: kotlin and ruby strip null entries nested inside free-form model values while java retains them; this is closed as forced, not a defect.
Why: aligning needs per-field metadata the schema does not carry, and it is reachable only through a petstore free-form-map edge.
Where: `object_serializer.mustache`.
Test: fixture only.

### Strict-key `unevaluatedProperties: false` is fixture-only (second audit tail)
Rule: the strict-deserialize path is exercised only by the petstore model `StrictTag`; do not force it onto the production deserialize path.
Why: it would need a marker interface and reflection routing against kotlinx-serialization, with risk and no wire benefit.
Where: `object_serializer.mustache`.
Test: `ObjectSerializerTest`.

### Warnings-as-errors is deferred (R8-F4)
Rule: do not add `allWarningsAsErrors` to `build_gradle.mustache` until two cleanups land: a required-parameter always-true null check and the Kotlin 2.2 value-based `equals()` warning on date-time models. The suppressions use the modern `-Xwarning-level=UNCHECKED_CAST:disabled` and `DEPRECATION:disabled` syntax, because the old flag is itself deprecated and fatal under `-Werror`.
Why: it is the one compiled-SDK gate still missing; dart armed its analyzer strictness, kotlin has not.
Where: `build_gradle.mustache` (the NOTE near line 50).
Test: `KotlinBuildSpec`.

### Inline comments are allowed in kotlin (deferred)
Rule: `KotlinFormattingSpec.getInlineCommentPattern()` returns null, so the shared inline-comment ban is off for kotlin; doc comments are separated from line comments in the templates.
Why: not forced, but deferred; do not flag the absence of the ban.
Where: `KotlinFormattingSpec.java`.
Test: `KotlinFormattingSpec`.

### Decisions taken in batch 6

Each follows the audit's own proposal, because the owner had not decided; reopen only with new evidence.

- **Redirect edge cases.** A 3xx without a usable `Location` is returned unchanged even when the redirect budget is spent (the too-many-redirects error needs a followable `Location`); `maxRedirects` must not be negative (`Invalid maxRedirects: must not be negative (got N)`); the HTTPS-to-HTTP refusal carries the response headers; URLs in refusal messages drop their query and fragment, so a query-parameter API key never reaches a message (`redactUrl`).
- **Timeout (D77).** `timeout` must be null or greater than zero: `Invalid timeout: must be a positive number of milliseconds (got N)`. Zero is no longer accepted.
- **Header and charset parsing.** `Accept-Encoding` presence is case-insensitive like User-Agent; the response charset is read from a parameter named `charset` in any letter case with optional spaces around `=`, never from a name that only ends in it; a literal `+` in proxy userinfo is kept (`%2B` before `URLDecoder`).
- **OAuth tokens.** One expiry margin of min(60 s, expires_in / 2) is applied when the expiry is stored and none when it is read, so a token valid for 60 s is reused for 30 s and one valid for 90 s for 45 s; `expires_in` 0 is the way to get an already-stale token (batch 7). A `token_type` other than `Bearer` (any letter case) is refused with `Unsupported token_type in token response`; a missing or null one is accepted. The empty-code message is `Authorization code must not be empty` (blank is still rejected).
- **Per-call authenticators.** `BaseApi.invokeApi` hands the API's own transport to an `HttpAwareAuthenticator` passed per call, so a per-call OAuth2 or OpenID Connect authenticator no longer fails with the not-injected error. It does so on every call, which is idempotent.
- **Models.** `additionalProperties` is a `@Transient` constructor parameter (default empty), so a caller can supply it and `copy()` carries it (the copy shares the same map instance); a `format: password` property prints as `***` in `toString` (such a model gets the hand-written `equals`/`hashCode`/`toString`); every enum's `toString()` is its wire value.
- **Names.** `getModelNameCollisionPrefix` is `Model`, so a schema named `String`, `Any`, `Int` and the other language primitives becomes `ModelString` and so on.
- **Docs.** README has Installation and Usage, then Cancellation, Custom HTTP client and Raw responses/timeouts/request ids, then Caveats (decimal precision, Schema constraints) and Not supported (webhooks and callbacks, XML bodies, Link Objects, mutualTLS, automatic retries, `dependentRequired`, SOCKS, LICENSE, URI normalization). SKILLS has the Custom HTTP client and Cancellation sections, a model sample that sets its required properties, and the min(60 s, half-lifetime) token margin.
- **Not applicable to kotlin.** F929: the token-flow authenticators are `suspend`-only, so there is no sync accessor to guard. F149, F832, F839, F054, F057, F803: already conforming (the code and tests existed; only this file was stale).

### Mock seam (mocking001 to 009, issue #122 and the Invoker line of #131)
Rule: every API group has an interface and the client has one where the language can state it, additive next to the existing classes, using only SDK and standard-library types. `<Group>ApiInterface` (`...Interface.kt` per group) and `ClientInterface : AutoCloseable` with `val` per group; `Client` keeps the concrete type through a covariant `override val`. The default values of the parameters live on the interface, so the class overrides drop `= null`, and the forms without the server live on the interface as methods with bodies, not on the class. The tests rely on Kotlin 2.2 compiling interface bodies as JVM default methods so a `Proxy` can run them. Tests: ClientTest.mustache (001, 002, 007, 008), api/PetApiTest.mustache (003 to 006). Where: the templates named above, `BetterKotlinCodegen`, the README template (`Testing your code`), `docs/languages/kotlin.md`.

## Known limitations and out of scope

- **Ambient proxy (D74) is a forced divergence on the JVM.** Ktor's CIO engine falls back to `ProxySelector.getDefault()` (the `http.proxyHost` and `https.proxyHost` JVM properties, and the OS proxy) whenever `TransportOptions.proxy` is null, and `HttpClientEngineConfig` has no way to switch that off per client (a `DIRECT` proxy is rejected by `CIOEngine`). The SDK therefore cannot ignore ambient proxy settings; a test that sets the properties and expects a direct connection fails, so none is generated.
- **TLS host name check is a forced divergence on the JVM (transport062).** `verifySsl=false` turns off certificate validation, but the Ktor CIO engine (`TLSClientHandshake.handleCertificatesAndKeys`) still calls `verifyHostnameInCertificate` whenever `serverName` is set, and CIO always sets it, so a certificate whose host name does not match is refused. No trust manager or `TLSConfigBuilder` setting skips that check, so the `transport062` test is not generated and `test-catalogue-exceptions.txt` records it.
- Strict-key `unevaluatedProperties` is not on the production deserialize path (second audit tail).
- Multipart tests are structural, not byte-level, because Ktor's body is opaque to the test.
- Overflowing double literals such as `1e400` decode to Infinity in kotlin (parked: the stated no-non-finite contract is unmet in java, kotlin, python, node, ruby, php, dart).
- OAuth percent-encoding of `~ * ! ' ( )` in the token body, the Basic header and the authorize URL still follows the stdlib encoder in kotlin (D52 covers query and form bodies only); that is a latent, deferred item.
- The per-model oneOf/anyOf no-match message for kotlin is `No anyOf variant of <X> matched the response body`; it differs from the other eleven and is documented, not harmonised.
- The debug string field sets of the OAuth2 authenticators differ per language, not harmonised.
- OpenAPI 3.1 and 3.2 features follow the epics #96 (3.1) and #49 (3.2); loading 3.2 documents is #50 because the pinned swagger-parser rejects it. SOCKS proxies are not supported (the README says so).
- F287 (constructor shape drift, `refreshUrl` position) is done for kotlin; F332 (binary responses stream-typed) is done for kotlin.
- Redirect refusals, the too-many-redirects message, `Invalid timeout` and the serializer messages now use the D16, D77 and D15 wording.
- Not classified: D68 (a model missing required properties cannot be built) is marked "Needs user" for java, node and rust and "already canonical" for kotlin; the owner has not decided it.

## Build-breaking rules

- ktlint 1.5.0 in `ktlint_official` style runs as `gradle ktlintCheck` with `ignoreFailures=false`; `.editorconfig` is the formatter configuration and any template change must produce ktlint-clean output.
- ktlint's `no-consecutive-comments` rejects a block comment that follows a KDoc, so implementation notes go above the class doc with a blank line, and in templates they are written as mustache comments `{{! ... }}`.
- detekt 1.23.7 runs as `gradle detekt --console=plain` with `buildUponDefaultConfig` and `maxIssues: 0`; every switched-off rule in `detekt_yml.mustache` is named with its reason, and no generated file carries a whole-tool or rule-set suppression (named single-rule suppressions are the only allowed kind).
- HTML entities are banned in generated text everywhere (generator comment rules); use numeric references such as `&#38;` where an ampersand is needed in prose.
- Generated sources must not import siblings of their own package (`dropSamePackageImports`), since ktlint's `no-unused-imports` reports them; wildcard imports are allowed only for the SDK's own packages and JUnit's Assertions.
- A `$` from a spec must be escaped to `\$` in every literal and enum value; `/*` and `*/` in prose must be neutralised in KDoc.
- Gradle runs in the `gradle:9-jdk21` container with `GRADLE_USER_HOME=/root/.cache/kotlin/gradle`; a build that needs the network for the ktlint binary (the formatter step downloads ktlint 1.5.0 with retries) can flake locally, so rerun before treating it as a regression.
- Regenerating the golden runs Docker-based formatters per language; commit all twelve goldens current, never a subset.

## Verify

- `etc/ci-local.sh check Kotlin` runs spotless, spotbugs and the kotlin generator and specs (the argument is the spelling of the `GenerateKotlinClientTest` class).
- One language end to end with Maven: `mvn verify -Dgroups=kotlin`.
- The kotlin specs individually: `KotlinBuildSpec` (`gradle assemble compileTestKotlinJvm`), `KotlinFormattingSpec` (`gradle ktlintCheck`), `KotlinLintingSpec` (`gradle detekt`), `KotlinReservedWordsSpec` (dumps the real keyword list in the Gradle container and compares it to `reserved-words/kotlin.txt`), `KotlinClientSpec` (generated client structure and the petstore run).
- After any template change, regenerate the golden and review the diff in `src/spec/resources/generated/kotlin/`, then run the generated client's own `make` gates, not only the golden's tests.

## Decisions taken in batch 7

- **F854/F895.** The stored expiry margin is min(60 s, expires_in / 2); `expires_in` 0 is how tests get an already-stale token.
- **F1156.** `api_info.mustache` renders the info description through `unescapeDocComment`; `MetadataTest` asserts no generated header line holds a backslash-escaped quote.
- **F1086.** Proxy URL configuration errors print the URL with its user-info replaced by `***@` (D79); `TransportOptionsTest` pins it.
- **F1079.** Already conforming: `timeout(0)` is refused with the D77 message.
- **Test parity added (/).** `ObjectSerializerTest` pins the hostile Availability members, the EdgeCases.phase wire values, keyword and digit-leading wire keys, literal U+2028/2029/DEL, and exact plain-notation decimals; `StoreApiTest` pins the lowercase uuid path; `PetApiTest` pins the header and cookie guards.
- **F864.** Already conforming since batch 6: `additionalProperties` is a constructor parameter, so `copy()` carries it.
- **F859.** OIDC discovery must carry an `issuer` equal to the discovery URL (minus `/.well-known/openid-configuration`, trailing slash ignored) and, unless the discovery URL is itself `http`, https endpoints.
- **F855.** A 401 answer calls `HttpAwareAuthenticator.invalidateAccessToken()` (default no-op, overridden by the OAuth2 and OIDC authenticators), so a revoked token is dropped; there is no automatic retry.
- **F860.** A `noRedirect` request (the token POST) does not receive `TransportOptions.defaultHeaders`; header layering is case-insensitive, the later layer replaces the earlier one. The OIDC discovery GET still receives them.
- **D92.** A decoded response body drops `Content-Encoding` and `Content-Length` from the returned headers.
- **F861.** `NetworkException` and `NetworkTimeoutException` messages drop the query and fragment of any URL in the Ktor message (`redactUrlsInMessage`).
- **F1084.** SKILLS.md lists `{{errorPrefix}}Exception` with its `SerializationException`, `OAuth2ServerException` and `OAuth2TokenException` siblings.

## Decisions taken in batch 9

- **F855 (replay after 401).** `BaseApi.invokeApi` still calls `HttpAwareAuthenticator.invalidateAccessToken()` on a 401 (the OAuth2 client-credentials, password, auth-code and OIDC authenticators override it publicly; the refresh token is kept), and now fetches fresh auth headers and sends the request once more. A second 401 is the usual `UnauthorizedException`. A `Map` body (multipart) is not replayed, a re-authentication that throws the SDK's error root leaves the 401 as the answer, and Basic, Bearer and API key authenticators are not `HttpAwareAuthenticator`s, so they never replay. Kotlin has no stream body type: every other body is a `String` or `ByteArray`.
- **F858 (PKCE).** `buildAuthorizationUrl` adds `code_challenge` (S256) and `code_challenge_method=S256` and keeps a 43-character verifier (32 `SecureRandom` bytes, unpadded base64url) in the authenticator's internal `pkceVerifier`; `exchangeCode` sends it as `code_verifier` and clears it after a successful exchange. OIDC rediscovery copies the verifier to the rebuilt delegate. No nonce. The JDK's `MessageDigest` is the only dependency.
- **F823 (automatic retries).** `TransportOptions.builder().maxRetries(n)` (default 2, `0` disables, negative is refused). `DefaultApiClient.sendRequest` loops over `sendOnce`: it retries `NetworkException` (never `NetworkTimeoutException`) and the statuses 408, 409, 429 and 500-599 except 501, waiting 0.5 s doubling to 8 s minus up to 25% jitter, or `Retry-After` (seconds or HTTP date, capped at 60 s). One `TimeoutBudget` spans every attempt and wait; a wait that does not fit in what is left ends the retrying and the last answer or error is reported. `kotlinx.coroutines.delay` makes the wait cancellable. Every attempt merges its headers again (new `X-Request-ID`), but the caller's headers, including the auth header fetched for the call, are the same map; a token that expires during the 60 s ceiling is covered by the 401 replay above. A multipart `Map` body and an in-memory body are rebuilt for each attempt and retried; a replayable `BodySource` is read again and a single-use one is never retried (see "Streaming: uploads"). The internal test seams `sleeper` and `jitterSource` keep the tests from waiting; the internal `DefaultApiClient(httpClient)` constructor now sets `maxRetries(0)`, so a test through it sees one request per call. `BaseApiTest.TestableApi` and the real-network failure tests set `maxRetries(0)` so they stay fast. The README "Automatic retries" entry moved from Not supported to a Retries section.
- **F825.** Skipped: rust only.
- **F1080 (rendered error body).** `ApiException.toString()` shows at most 1024 UTF-8 bytes of the body (never cutting a character) followed by `... (N more bytes)`, writes control characters (below 0x20 and 0x7F) as `\xNN`, and masks the headers authorization, proxy-authorization, cookie, set-cookie, www-authenticate, proxy-authenticate and any name containing key, token or secret as `***`. `responseBody` and `responseHeaders` keep the full values.

## Decisions taken in batch 10

- **F173 (more names).** `toModelName` also prefixes `Model` for the standard-library names a model package uses unqualified: `Error`, `Exception`, `Throwable`, `Result`, `Nothing`, `Number`, `Array`, `List`, `Map`, `Set`, `Pair`, `Triple`, `Comparable` (the petstore `Error` and `Result` schemas become `ModelError` and `ModelResult`). The primitive names keep the batch 6 rule. Tested in `BetterKotlinCodegenTest`.
- **F1055.** `getInjectedArgumentNames` lists what an operation injects or declares as a local (`options`, `server`, `requestContentType`, `auth`, `path`, `url`, `headerParams`, `queryParams`, `formBody`, `serverUrl`, `declaredConsumes`, `effectiveRequestContentType`); a parameter with one of those names gets the `Param` suffix (`optionsParam`), the wire name is unchanged.
- **F1072.** `BetterKotlinCodegenTest` is the mirror unit test of `BetterKotlinCodegen`; the type-mapping test stays beside it.
- **F1105 (D07).** The three 2xx token failures are `Token response is not valid JSON` (the body does not parse), `Token response is not a JSON object` (it parses to an array or a primitive) and `Token response missing or empty access_token field`; none carries a cause or parser detail. `OAuth2TokenManagerTest` pins all three in one table.
- **F1103 (D79).** A CA bundle that cannot be read says `failed to read CA certificate from "<path>": <cause>`; one that has no PEM blocks or does not parse says `failed to parse CA certificate from "<path>": <cause or no PEM blocks found>`. The file is read first, so the two cases never share a sentence. Tested in `DefaultApiClientUnitTest`.
- **F1118.** `ServerConfiguration.getUrl` substitutes in one pass over the template, so a placeholder that a substituted value introduces is never expanded, whatever the variable order.
- **F1093 (S15).** A header whose name contains `key`, `token` or `secret` is stripped on a cross-origin redirect as well as the fixed list (`DefaultApiClient.isSensitiveHeader`).
- **F1094.** `build.gradle.kts` sets `Automatic-Module-Name` to the invoker package on the JVM jar; `ClientTest` reads the file to pin it.
- **F893.** The named examples of an optional (options-object) parameter are written into the property's KDoc on the options class, as `Example — <summary>: <value>`; a comment terminator or a line break in a summary or value is neutralised. `KotlinFormattingSpec` pins it.
- **F1071.** `KotlinFormattingSpec.pluralExamplesShouldBeAttributedToParameter` pins the two `petId` examples under their `@param`.
- **Tests added.** F1073 and F1074 (`BearerAuthenticatorTest`), F926 (`OAuth2TokenManagerTest`), F1119 (`TransportOptionsTest`), F1123 and the module name (`ClientTest`), F1170 (`BaseApiTest.sendsNoBodyWhenNull` now asserts the transport saw no body), F1124 (slug, nickname, quantity 500 in `ObjectSerializerTest`; the third global server in `ServerConfigurationTest`), F1129 (`ComposedSchemaTest.TreatmentRecordTests`), F1141 (`getStockItem` in `StoreApiTest`), F1161 (`getOrderById` against the typed status routes in `StoreApiTest`; `/test/response-headers` in `DefaultApiClientTest`), F1093 (`DefaultApiClientUnitTest`).
- **F1107.** The 25 snake_case test functions are camelCase now.
- **Already conforming.** F325 and F1160 (`BaseApi.invokeApiForResult` copies the headers with `toMap()`; `BaseApiTest.resultHeadersAreACopy`), F1080 (batch 9, `ApiExceptionTest`), F1146 (`ObjectSerializerTest.inlineEnumWireValuesRoundTrip`), F823 (batch 9).

## Decisions taken in wave 2

- **F830 (request context on errors).** `ApiException` and every subclass have `requestMethod` and `requestPath` (query and fragment stripped; the path of the URL, `/` when empty). `ApiException.fromResponse(status, headers, body, requestMethod, requestPath)` and the full constructor and every status subclass take them as trailing optional arguments; `BaseApi.invokeApi` passes them where it builds the error and also sets them (internal setter) on an exception the transport raised, so `NetworkException` and `NetworkTimeoutException` carry them too. `toString()` writes one `Request: GET /pet/3` line between the status line and the headers; `message` is unchanged. The OIDC discovery failure reports `GET <discovery path>`. Tested in `ApiExceptionTest` and `BaseApiTest.RequestContextAndPerCallSettings`.
- **F827 (per-call options).** Every generated Options class has `timeout` (Long, milliseconds, one end-to-end budget), `headers` (Map) and `maxRetries` (Int, `0` disables) (an `auth` field no longer exists, see); the three names are injected-argument names, so a spec parameter called `timeout`, `headers` or `maxRetries` becomes `...Param`. `BaseApi.invokeApi` and `invokeApiForResult` take them as `callTimeout`, `callHeaders`, `callMaxRetries`: the headers replace a header of the same name (any case) as the last layer, and the timeout and retry count travel to `DefaultApiClient` in the coroutine context as `CallSettings`, so the `ApiClient` interface is unchanged (another implementation keeps its own settings). The Ktor `HttpTimeout` plugin is always installed so a per-call timeout works when the client has none. An invalid value is an `IllegalArgumentException` before anything is sent. LIMIT: only operations that already have an Options object (an optional query, header, form or cookie parameter, or a security requirement) have the slot; giving every operation one is a change to `AbstractBetterCodegen` (`generateOptionsFilesForOps`, `computeSignatureArgs`) and is listed as a shared need.
- **F1062 (several 2xx responses).** For an operation with `hasMultiple2xx`, `api.mustache` passes `decodeSuccess` to `invokeApiForResult`: the body is decoded as the variant of the status the server sent (`200 -> Union(deserialize<A>)`, a `2XX` range last), and an undeclared 2xx falls back to the union's own try-each decoding. A body that does not fit its status is a `SerializationException`. Fixture: `reserveStock`; tested in `StoreApiTest`.
- **F325, F1160 (`ApiResult.headers`).** `BaseApi` hands the result `Collections.unmodifiableMap(LinkedHashMap(headers))`, so it is a read-only copy; `BaseApiTest.resultHeadersAreACopy` mutates the source and tries to mutate the result. The `ApiResult` constructor stays a plain data class (a data class parameter cannot be transformed), so a caller who builds one keeps their own map.
- **F860.** The OIDC discovery GET is sent as a no-redirect request, so it carries no `TransportOptions.defaultHeaders` and follows no redirect, like the token POST. Tested in `OpenIdConnectAuthenticatorTest`.
- **F390.** `StrictDoubleSerializer` and `StrictFloatSerializer` refuse a literal that overflows to Infinity (`1e400`). Tested in `ObjectSerializerTest`.
- **F455.** `List<BigDecimal>`, `Set<BigDecimal>` (`LinkedHashSet`) and `Map<String, BigDecimal>` properties compare and hash their elements by numeric value (`stripTrailingZeros`). The classification is unit-tested in `BetterKotlinCodegenTest`; the petstore has no such property, so no golden compiles the template branch (the spec change is a shared need).
- **F1166 (D74 is a forced limit in kotlin).** The test the finding asked for found that the rule cannot hold: with no `TransportOptions.proxy` the Ktor CIO engine asks `ProxySelector.getDefault()` (`lookupGlobalProxy` in ktor-client-cio 3.5.0), and it has no way to say direct (`Proxy.NO_PROXY` is rejected with `CIO engine does not currently support UNKNOWN proxies`, which broke every request when tried). So the JVM's `http.proxyHost` and the system proxy settings are honoured when the caller configures no proxy. `DefaultApiClientUnitTest.RealSocket.ambientProxySelectorIsConsultedWhenNoProxyIsConfigured` pins that behaviour with a dead default selector, so a Ktor release that lifts the limit is noticed. The README says so. Closing it needs another engine or a Ktor change.
- **F894.** Options `repositoryUrl`, `homepageUrl` (defaults to the repository), `authors` (comma separated), `documentationUrl`, `issueTrackerUrl` are read in `BetterKotlinCodegen.resolvePackageLinks`, written to the POM (`url`, `developers`, `scm`, `issueManagement`) and to the README package list; the SPDX licence is the existing `licenseId`. They are registered as CLI options of the kotlin generator only: moving them to `AbstractBetterCodegen` is a shared need.
- **F862 (credentials).** The variable names come from the shared generator (`securitySchemeEnvVars`, `envVarPrefix`; `EnvVarNames`), not from the error prefix, and every credential is the first of the argument, then the environment variable read once at construction (an empty value is unset), then, for a bearer token or an API key, the `tokenProvider` or `apiKeyProvider` suspend lambda that is called on every request and validated like a static value. A token passed together with a tokenProvider is refused (`IllegalArgumentException`, bhv authbearer 026). A missing one throws `IllegalArgumentException` naming the variable. `BearerAuthenticator`, `ApiKeyAuthenticator` and `BasicAuthenticator` keep their public constructors and read `{PREFIX}_TOKEN`, `{PREFIX}_API_KEY`, `{PREFIX}_USERNAME` and `{PREFIX}_PASSWORD` through an internal constructor that also takes the variable name (the extractor ignores an internal primary constructor, so the public surface is the public one); the per-scheme class (`scheme_authenticator.mustache`, arguments built in `renderSchemeAuthenticator` and `buildKotlinSuperArgs`) takes every credential as an optional argument and passes its own `envName` (`{PREFIX}_{FIELD}`, qualified by the scheme id when two schemes share a field). The OAuth 2 and OpenID Connect classes keep their required constructors; their per-scheme classes resolve `clientId`, `clientSecret`, `username`, `password` and `redirectUri` through `CredentialEnvironment.require(argument, variable, field)`. `BaseApi` fetches the auth headers before the query values so a provider-backed key reaches the query and cookie locations. `Client.withToken(host, accessToken = null)`. Tests: `BearerAuthenticatorTest`, `BasicAuthenticatorTest`, `ApiKeyAuthenticatorTest` (precedence, empty value, missing, per-scheme variables) and the client-credentials and password OAuth tests; unit test `BetterKotlinCodegenTest.credentialVariablesAreReadByFieldName`.
- **F827 (an Options object for every operation).** `BetterKotlinCodegen.mintsOptionsForEveryOperation()` is true, so an operation with no parameter and no security takes `options: XOptions? = null` holding only `timeout`, `headers` and `maxRetries` (no `auth` field); `api.mustache` reads the three settings from it whenever it is present. Tested in `StoreApiTest` (`getInventory`) and `BetterKotlinCodegenTest.everyOperationGetsAnOptionsObject`.
- **F866 (sibling default on a `$ref`).** `model.mustache` initialises a property from `vendorExtensions.siblingDefaultEnumType` and `siblingDefaultEnumVar` when the shared generator sets `siblingDefaultIsEnum` and there is no `defaultValue` (fixture `StockItem.availability` is `Availability.ON_HOLD`). A sibling default on a `$ref` to a non-enum schema is not applied. Tested in `ObjectSerializerTest.refSiblingDefaultAppliesOnConstructionAndDecode`.
- **F878 (typed union accessors).** Every non-generic variant of a `oneOf` or `anyOf` wrapper has an `is<Variant>` property and an `as<Variant>()` function returning the variant or null; every variant has a `from<Variant>(value)` factory on the companion object. `BetterKotlinCodegen.nameUnionVariants` names them (`variantName`, `isPlainVariant`; a name two variants would share gets a number). A generic variant (`List<String>`) has no `is` or `as`, because the type is erased. An `anyOf` wrapper answers over everything it retained (`isMedication` is true when any matched instance is one, `asMedication()` returns the first). Tested in `ComposedSchemaTest.ReserveStockResponseAccessorTests` and `PetTreatmentAccessorTests` and `BetterKotlinCodegenTest`.
- **F889, F834, F835, F836, F837, F828.** README sections: credentials, per-call options, errors and the multi-2xx union, response compression, logging (inject a Ktor client with `Logging`), versioning, optional versus nullable, unknown enum or discriminator values failing loudly, joined repeated response headers, a binary response being a stream (the Streaming section), `Configuration` versus `TransportOptions`.
- **F868.** Already conforming: models are immutable data classes with `copy()` (`additionalProperties` is a constructor parameter, so `copy()` carries it, F864).
- **F1124.** Already conforming: `ObjectSerializerTest` asserts the slug `12/ab` and the nickname `ZZZZZZZZZZ`.
- **F1092 (forced).** Ktor CIO builds the CONNECT request of an https tunnel from the request's own `Proxy-Authorization` header (`UtilsKt.startTunnel` in ktor-client-cio 3.5.0 reads it), and has no other place for proxy credentials, so dropping the header for https targets would remove proxy authentication; the first hop keeps it. Redirect hops already omit it for https.
- **F1140 (skipped).** Ktor's release of an intermediate response is not observable from `MockEngine`.
- **F1087.** The floor stays JVM 17, the version the README, the build and the detekt target are written for. The rule says to keep a floor unless it is newer than the oldest non-EOL release, and JDK 17 is newer than the oldest still-supported JDK 8 and 11, so the owner has to say whether kotlin should drop to a lower target (that changes the toolchain and the pinned plugins, not a template line); nothing was changed.

## Gate lessons (what broke the gate and how to avoid it)

Generated Kotlin is verified only by the Docker specs (`KotlinBuildSpec`, `KotlinClientSpec`, `KotlinLintingSpec`, `KotlinStaticAnalysisSpec`, `KotlinFormattingSpec`, `KotlinTypeCheckSpec`). Run the whole set with `etc/ci-local.sh check Kotlin` before any push. `KotlinClientSpec` alone misses detekt and ktlint, and test-compile errors (`compileTestKotlinJvm FAILED`) can show only in CI, so also run `gradle compileTestKotlin` in the golden.
- Compiler warnings as errors (`allWarningsAsErrors`) is the deferred gate (see `build_gradle.mustache`): arming it surfaces always-true null checks on required query params and the `OffsetDateTime` identity warning in `equals()`. When you arm it, unused variables (`opts`) and the deprecation warning about the old `-Xsuppress-warning` flag become fatal, so run `KotlinBuildSpec` in the same step.
- Test templates must track the interface they implement: `override suspend fun sendRequest` when `ApiClient.sendRequest` is `suspend` (`test/BaseApiTest.mustache`, `CapturingApiClient`). A public `inline` function must not call protected members; make helpers `internal`. Catch with `gradle compileTestKotlin`.
- ktlint_official runs on generated and test code. Keep the filename equal to the single class it holds (a rename needs a file rename), never put a `/* */` block straight after a KDoc (`no-consecutive-comments`; put the rationale inside the KDoc), and let `ktlint --format` remove duplicate imports instead of hand-editing. Pin the ktlint version: an unpinned bump flipped trailing commas in 88 files. Catch with `KotlinFormattingSpec`; a second regeneration must show a zero git diff.
- detekt runs with strict defaults on generated code (`TooGenericExceptionThrown`, `TooGenericExceptionCaught`, `EmptyFunctionBlock`, always-true conditions after a rename). Configure rules once in `detekt_yml.mustache` with a written reason; never use a file-level `@file:Suppress("detekt:all", ...)` (it hid findings in 76 files). Catch with `KotlinStaticAnalysisSpec` or `gradle detekt`, and `grep -rl 'detekt:all'` over the goldens.
- Ktor engine limits: `ContentEncoding` has only `gzip()` and `deflate()` (no `brotli()` or `zstd()`; making codecs `compileOnly` needs reflective decode or `NoClassDefFoundError`); set `followRedirects = false` before installing `HttpRedirect`; proxies use `ProxyBuilder.http(Url(..))`; `@Synchronized` is JVM only. Never add a Ktor DSL call without checking the pinned version, and run `KotlinClientSpec`.
- Test HTTP clients must disable auto-redirect or the redirect and header-strip logic is bypassed. Catch with a redirect test asserting `Authorization` is absent on hop 2.
- Kotlin 2 null intrinsics: a non-nullable `KSerializer<T>` cannot return null, so test unknown discriminators with `runCatching{}.getOrNull()`. Never decode arbitrary JSON with `decodeFromString<Any>`; use `json.parseToJsonElement`. Never deserialize a void response (`deletePet` returned `null` and kotlinx threw `JsonDecodingException`).
- Never let a library exception escape the SDK (`SerializationException`, coroutine cancellation turned into `ApiException`). Catch with an error-path test that catches the SDK root error.
- Model-name prefix: a schema named `Error` or `Result` becomes `ModelError` or `ModelResult` (`toModelName`, `BetterKotlinCodegenTest`), and references in generated code (`PetApi.kt`, options) must follow the rename. A parameter that clashes with an injected name gets the `Param` suffix.
- `encode()` serves both the query path and the form-body path; check every caller before changing `spaceToPlus` (form-body `full+name=` tests broke 653 tests). Catch with `grep -n "encode(" templates/kotlin` then `KotlinClientSpec`.
- commonMain code must not use `java.*`, `Dispatchers.IO` or Jackson; put JVM code in the `_jvm` templates. `kotlin.time.Clock` needs `@OptIn(kotlin.time.ExperimentalTime::class)`. Catch with `grep -rn 'import java\.' templates/kotlin` and `gradle compileKotlin` after a dependency bump.
- A `data class` needs `val` or `var` constructor parameters (the `ApiResult` revert), and no raw NUL bytes in templates (use `'\u0000'`; `grep -rlP '\x00' templates/kotlin`).
- Never use `//` comments or HTML entities in templates (`FormattingSpec`); `{{x}}` escapes quotes, so use `{{{x}}}`. A `{{! }}` comment ends at its first `}}`.
- A lint check that treats a build directory existing as success hides compile failures; assert the Gradle exit code. Do not pass `--warning-mode=all` in Gradle specs. Gradle "daemon has disappeared" or exit 137 is memory exhaustion from parallel specs: re-run one spec alone before reading code.
- Use neutral names in templates (`FixtureVocabularyLeakTest`), skip mock-server-limited tests (Prism base64 binary) as the sibling languages do, and regenerate and commit all 12 goldens with the template change.
- **F1078 (graceful close).** `DefaultApiClient` counts admitted requests: `close()` marks the client closed, a request in flight finishes, and the Ktor client the SDK built is released once by the last one out (`released` guard), so closing twice is harmless; a later `sendRequest` raises `IllegalStateException`. Tested in `DefaultApiClientUnitTest` (`closeLetsAnInFlightRequestFinishThenReleasesTheTransport`: still open while a request is in flight, released after the last one; `closeIsIdempotent`: nothing in flight releases at once, a second close does nothing, a later request is rejected), `ClientTest` (a closed client rejects, a caller-supplied transport is never closed).
- **F316.** Kotlin constructors never validate (data classes with plain defaults, no `init` block, no `require`): an out-of-range number, an out-of-pattern string and an empty collection are accepted, and a wrong type or an unknown enum member cannot even be written because the property types are static. Everything is checked at decode: a null or missing required property and an enum string outside the set raise `SerializationException`. `Enum.fromValue(unknownString)` still raises `IllegalArgumentException`: it is the explicit string-to-enum conversion, not a model constructor. Tested in `ObjectSerializerTest` (`constructionNeverValidatesAndDecodeFailsOnAnUnknownEnum`, `constructionAcceptsAnyValue`, `decodeRejectsANullOrMissingRequiredProperty`).
- **F840.** A cookie parameter is sent raw when every character is an RFC 6265 cookie-octet (`%` and `=` are legal, so a value the caller percent-encoded goes out unchanged) and otherwise fails with `IllegalArgumentException` before anything is sent, the message saying to percent-encode the value (`isValidCookieValue` in `BaseApi`, used by the generated cookie code in `api.mustache` and by an authenticator's cookie). The SDK never encodes a plain cookie value; a `content` cookie is the exception, because the caller passes an object and the SDK serialises it, so `ValueSerializer.serializeContent` percent-encodes that JSON (as csharp does). Tested in `PetApiTest` (`testLegalCookieValueIsSentRaw`, `testIllegalCookieValueFailsBeforeSending`, the CR/LF case) and `ValueSerializerTest`.
- **F857 (a credential per security scheme).** The shared generator hands over `clientSchemes` (`schemeId`, `paramName`, `className`) and, per operation, `vendorExtensions.op.securitySchemeOrder` and `hasSecuritySchemes`; `BetterKotlinCodegen.processOpenAPI` adds `argName` (a Kotlin hard keyword is back-quoted) and `authPackage` (`auth` or `auth.oauth`). `client.mustache` emits `Client(host, <scheme>: <Authenticator>? = null, ..., transportOptions: TransportOptions? = null)` beside `Client(authenticator[, transportOptions])`, hands every scheme credential the shared `ApiClient`, and passes the map (keyed by scheme id) to each API class as a fourth constructor argument. `BaseApi.schemeAuthenticator(vararg schemeIds)` returns the credential of the first listed scheme the client holds, or null, and `api.mustache` passes it to `invokeApi` for an operation with `hasSecuritySchemes`; null falls back to the client-level authenticator, and `NoAuth` (`security: []`) sends none. The Options classes have no `auth` field and `invokeApi` has no per-call scheme override. Tested in `PetApiTest` (scheme routing, first scheme in the operation's order wins, a credential for another scheme is ignored, client-level fallback, `security: []` sends none, no `auth` field on any Options, routing through the real `Client`), `BaseApiTest` (`schemeAuthenticator`, the defensive copy, the fallback), `ClientTest` (the scheme constructor, no credential sends no header) and `BetterKotlinCodegenTest` (`argName`, `authPackage`).
- **F1085 not done, needs a shared change** (kotlin's `ChasmContainer` mounts the committed `certs/server-key.pem` that the shared Chasm fixture ships, so removing the keys needs the shared fixture to generate them).

- Trace context from the caller (#136): reads the thread-local `Context.current()`; across a coroutine thread change the caller must install the span with a `ThreadContextElement` (`Context.asContextElement()`); the base-api test defines its own element, so no dependency is added (D95).

- **F15 (typed `additionalProperties`, #103).** The `@Transient` bucket is `MutableMap<String, V>`. `ObjectSerializer` reads the value type from the getter's generic return type, decodes each undeclared key through the strict scalar serializers and the configured module (`additionalValueSerializer`), and encodes it the same way; a companion `ADDITIONAL_PROPERTIES_NULLABLE` marks a nullable value schema.

## Call hook and OpenTelemetry adapter (issues 158 and 108)

Rule: `CallHook`, `CallObserver`, `CallInfo` and `CallOutcome` (`call_hook.mustache`, common source set) follow design 3.1 of `docs/design/hook-and-telemetry.md`: `start` runs once per call right after the headers are final (after the per-call headers, before the body is encoded), `finish` runs exactly once on every exit path after `start`, a `start` error propagates and a `finish` error is swallowed. `operationId` is `operationIdOriginal` (fallback `operationId`) and `urlTemplate` is `path`, both written with `lambda.kotlinString`. The hook is on `Configuration` (`callHook`) and is passed through the `callHook` argument of the `Client` constructors, `withToken` and `withAuthenticator`; the client has no builder, so an optional trailing argument is the idiom here.
Why: `BaseApi.invokeApi` is split in a wrapper and `invokeApiTracked`, which takes the `CallTracker` (`@PublishedApi internal`); `invokeApiForResult` keeps the tracker so the call ends after decoding and a decode failure is observed. The headers map is built once and the 401 replay copies it, so the hook's edits are on every send and the fresh authentication headers still replace a header of the same name.
Adapter: `OpenTelemetryCallHook` (`open_telemetry_call_hook.mustache`, JVM source set only) uses `opentelemetry-api` (`compileOnly`) and injects `traceparent` itself with the propagator of the instance it was given; its scope is `artifactId` and `artifactVersion`. Never import it from the common source set.
Where: `call_hook.mustache`, `open_telemetry_call_hook.mustache`, `base_api.mustache`, `api/api.mustache`, `configuration.mustache`, `client.mustache`, `readme.mustache`, `skills.mustache`, `BetterKotlinCodegen` (`hasOpenTelemetryAdapter`).
Test: generated `BaseApiTest` (`bhvHooks001` to `bhvHooks019`, `bhvHooks021`), `ClientTest` (`bhvHooks010`), `CallHookTest` (`bhvHooks020`), `OpenTelemetryCallHookTest` (`bhvTelemetry001` to `bhvTelemetry014`).

## Compiled examples (#111)

- `src/examples/kotlin/<package>/examples/Quickstart.kt` (`templates/kotlin/examples/Quickstart.mustache`, registered by `BetterKotlinCodegen.getSupportingFileSpecs` when `generateExamples` is on) puts the README Usage statements in `fun main()`. The statements live once in `usage_statements.mustache`, included by the README Usage fence, the SKILLS Quick Start fence and the quickstart (`langkotlin057`); `use { }` closes the client and `runBlocking` calls the suspend operation; with no example operation the client is built and closed. The per-call options fence is one partial, `per_call_options.mustache`, shared by README and SKILLS, and uses the example operation, whose options class has no required field.
- `build.gradle.kts` adds `src/examples/kotlin` to the `jvmTest` source directories and the detekt sources, and registers a `runQuickstart` `JavaExec` task over the test compilation's outputs and runtime class path, all under `{{#generateExamples}}`. `make examples` is `gradle compileTestKotlinJvm`; `make examples-run` maps `API_BASE_URL` to `{PREFIX}_BASE_URL`, passes a placeholder `{PREFIX}_TOKEN` (the variable `Client.withToken` documents) and runs `gradle runQuickstart`. The ktlint formatter that runs at generation covers the example, so the partial need not carry the indentation of `main`.
- `KotlinExamplesSpec` writes every fence to `src/spec/kotlin/fences/<file>_<nn>/Fence.kt` in its own package `fences.<file>_<nn>`, so top-level names never clash, and builds them with `gradle compileTestKotlinJvm` (the working copy is restored before every command batch, so the files never reach the golden). A `file` fence is the file after the package clause; a `statements` fence has its `import` lines hoisted and the rest placed in a plain `fun fence()`, which is not `suspend`, so a statements fence that calls an operation opens its own `runBlocking`. Warnings are not errors there (unused locals in statement fences warn), matching the deferred `allWarningsAsErrors`.
- The Gradle dependency line is tagged `kts`, which renders as Kotlin but is not compiled, since `implementation(...)` exists only in a build script. A fence that calls an operation is a function taking the client and the operation's own arguments (`signatureArgsNoServer`) as typed parameters; the models fence copies one property with `copy` instead of building a model with `TODO()`. Behaviours `langkotlin055` to `langkotlin060` are in `test/MetadataTest.mustache`, under `{{#generateExamples}}`.

## Streaming: binary responses (capability 1 of #114, D118 to D122)

### The transport returns an unread body
Rule: `ApiClient.sendStreaming(method, url, headers, body)` is a `suspend` interface method with a default implementation, and returns an `ApiStreamResponse` (status, lower-cased headers, the open `ApiStreamBody`, `readBounded(maxBytes)`, `close()`, a `toString()` of the status only). The per-call `timeout` and `maxRetries` travel in the `CallSettings` coroutine-context element exactly as for `sendRequest`, so the signature has no timeout parameters; `idleTimeout` is a `TransportOptions` setting only. The default implementation throws `UnsupportedOperationException` whose message is `<transport class name> cannot stream: it does not implement sendStreaming, ...`, so an injected `ApiClient` keeps working for buffered calls and never falls back to buffering. A non-2xx response is returned like any other (open body); `BaseApi` reads up to 64 KiB of it, closes it and raises the typed error with the operation's error schemas.
Where: `api_client.mustache`, `api_stream_response.mustache`, `base_api.mustache` (`runCall` is the one pipeline of `invokeApiTracked` and `invokeApiForStream`, parameterised by a `CallTransport`, so the hook, the headers, the authentication and the 401 replay are the buffered code), `default_api_client.mustache`.
Test: `DefaultApiClientUnitTest` (`bhvStreaming001` and the rest), `BaseApiTest` (008, 017, 018, 030), `ClientTest` (021), golden `PetApiTest` (009).

### One header phase for both paths
Rule: `DefaultApiClient.sendWithRetries` and `sendOnce` run the retries, the redirect loop and the end-to-end budget for both paths and return a `Sent` (a buffered response or a stream). A stream hop goes through `execute(stream = true, ...)`: Ktor frees a response when `HttpStatement.execute { }` returns, so the call runs in a coroutine of the client's own scope that completes a deferred with the response and then waits to be released; closing the stream completes the release and cancels that coroutine, which closes the connection instead of draining it. `TimeoutBudget.applyToStream` leaves only the connect timer to the engine (the request and socket timers would cut the body) and the wait for the headers is a `withTimeout` of the remaining budget, so `timeout` bounds the header phase and never the body. A response that is retried or followed is discarded (`Hop.discardBody`, `Sent.discard`). The buffered path is behaviourally unchanged.
Where: `default_api_client.mustache`.
Test: every existing `DefaultApiClientUnitTest` row, `bhvStreaming010`, `014`, `016`.

### The body: idle timeout, truncation, decoding, cancellation
Rule: the body is a private `StreamBody` that counts the wire bytes, remembers a connection failure, and raises when the body ends before its declared `Content-Length`. A read is bounded by `withTimeoutOrNull(idleTimeout)`; expiry raises `NetworkTimeoutException`. An encoded body (gzip, deflate, and br or zstd when the codec is on the class path) is decoded by the JDK or codec `InputStream` on `Dispatchers.IO` over a blocking view of the wire bytes, opened on the first byte so the call returns before the body starts. A failed read is raised as `StreamException` (reset or truncation, status 0), `ApiException` with the real status and the cause (a decode failure), the caller's `CancellationException` (never a timeout; the stream is aborted), or `IllegalStateException` with the closed-client message (the client was closed). `close()` aborts instead of draining and is idempotent; end of body releases the connection. `TransportOptions.idleTimeout` is `Long?` milliseconds, default 60000, `0` or `null` disables it and the property is `null` when disabled.
Where: `default_api_client.mustache`, `transport_options.mustache`, `errors/StreamException.mustache`.
Test: `DefaultApiClientUnitTest` (`bhvStreaming011`, `012`, `013`, `015`, `019`, `020`, `031`, `032`, `033`).

### Closing the client cancels open streams
Rule: `DefaultApiClient` counts an open stream as an in-flight request until it is closed or ends, and tracks it. `close()` marks the client closed, cancels every open stream (each frees its count, the last one releases the Ktor client and the stream scope), and returns; requests in flight that are not streams finish as before. A stream whose headers arrive after `close()` is aborted and raises the closed-client error. The next read of a cancelled stream raises `IllegalStateException("ApiClient has been closed and can no longer send requests")`.
Where: `default_api_client.mustache`.
Test: `ClientTest` (`bhvStreaming021`).

### Binary operations return `ByteStream`
Rule: `BetterKotlinCodegen.markBinaryStream` sets `streamsBinary` on `vendorExtensions.op` and rewrites `returnType` to `ByteStream` for an operation whose `streamKind` is `binary`, so every template (operation, interface, docs, `WithHttpInfo`) says `ByteStream` and `api.mustache` calls `invokeApiForStream`. `ApiResult<ByteStream>` has an empty `rawBody`. `postProcessSupportingFileData` hands the README and SKILLS the first such operation (`binaryStreamOperation`) for the Streaming example. An injected transport without `sendStreaming` makes a binary operation fail with the unsupported error: this is the accepted breaking edge of D119.
Where: `BetterKotlinCodegen`, `api/api.mustache`, `api/api_interface.mustache`, `byte_stream.mustache`, `readme.mustache`, `skills.mustache`.
Test: golden `PetApiTest` (`bhvStreaming009`, the existing avatar, document and photo rows now close their stream), `DefaultApiClientUnitTest` (`bhvStreaming029`).

## Streaming: JSON Lines and server-sent events (capability 2 of #114, D120 to D122, D125)

### The stream method and the types
Rule: an operation with `streamOnly` (decorators `streamsSse` or `streamsJsonLines`, set by `BetterKotlinCodegen.markBinaryStream`) has no buffered method and no `WithHttpInfo` variant in the class or the interface; `api.mustache` and `api_interface.mustache` emit `suspend fun <op>Stream(...signatureArgs, [requestContentType,] streamOptions: StreamOptions?)` (the `api/operation_prelude` partial builds the path, query, headers and options exactly as the buffered method does). The return type is `JsonLinesStream<T>` or `SseStream<T>` (`T` is `streamItemKotlin`: the schema type, or `String` for a plain payload); the item decoder is a lambda over `objectSerializer.deserialize<T>`, `null` for a string. `ServerSentEvent<T>` is a data class, `StreamOptions` a class with nullable `Long` milliseconds and `Int` bytes (`null` keeps the default), `EventStream.kt` (internal) holds `LineReader` (CRLF, LF, CR with a skip-LF flag, a cut-off last line reported through `wasTerminated`, UTF-8, BOM dropped, `TooLongException` past the limit) and `SseParser` (`RawEvent`, `lastEventId`, `retry`). The carriers are classes with a cold `items()` or `events()` `Flow` (closed when its collection ends) and a `suspend fun next()`; they do not implement `Flow`, which is not stable for inheritance.
Where: `event_stream.mustache`, `server_sent_event.mustache`, `stream_options.mustache`, `json_lines_stream.mustache`, `sse_stream.mustache`, `base_api.mustache` (`invokeApiForJsonLines`, `invokeApiForSse`), `api/*.mustache`, `BetterKotlinCodegen`.
Test: `EventStreamTest` (the one mirror file, `streaming002` to `005` and `sse001` to `sse023` parser and engine rows, each parsed whole and one byte at a time), `DefaultApiClientUnitTest` (`streaming006`, `007`, `sse007`, `015`, `018` over the raw server), `BaseApiTest$EventStreams` (`streaming028`, `sse006`, `012`, `013`, `020`), `ClientTest` (`bhvMocking001` lists the stream method of a stream-only operation).

### The engine
Rule: `SseStream.open` sends the first request (its failure is raised from the operation method) and checks the answer; each reconnect is `opener(lastEventId)`, which `BaseApi` implements as a fresh `invokeApiForStream` (so the headers and the credential are built again, and the call hook runs again) with `Last-Event-ID` added only when the id is not empty. `Cache-Control: no-cache` and `Accept-Encoding: identity` are added unless the caller, the call headers or the default headers set them. A lost connection is a clean end, a `NetworkException` (a `StreamException`, an idle `NetworkTimeoutException`) or, on a reconnect, a 408, 429 or 5xx status; it counts one consecutive failure, reset by an event; a clean end that exhausts the budget raises a `StreamException`. `reconnectWaitMillis` (internal, tested) is the server `retry` value, else `Retry-After`, else the delay doubling to 30 s less up to 25% jitter; the wait is a coroutine `delay`. An event named `error` raises `ApiException` with the real status and the data as the body; the terminator comes from the generator option `sseTerminator`; the SSE content type is checked on every connection and a mismatch is a `SerializationException`. A read after `close()` ends the stream quietly; a read after the client was closed raises the closed-client `IllegalStateException`, which is never reconnected. The per-call `idleTimeout` of `StreamOptions` travels in `CallSettings` (a third field) and `DefaultApiClient.openStream` prefers it to the transport's.
Where: `sse_stream.mustache`, `base_api.mustache`, `default_api_client.mustache`.
Test: `EventStreamTest`, `BaseApiTest$EventStreams`.

## Streaming: uploads (capability 3 of #114, D121)

### `BodySource` is one class with replayable and single-use constructors
Rule: `BodySource` (`body_source.mustache`) is a class in the SDK package. `of(bytes)`, `ofFile(path)`, `fromSupplier(length) { InputStream }` and `fromFlow(length) { Flow<ByteArray> }` are replayable; `once(InputStream)`, `once(ByteReadChannel)` and `once(Flow<ByteArray>)` are single use, each with an optional `length`. Every source is a producer of a cold `Flow<ByteArray>` (`chunks()`); a single-use one raises `IllegalStateException` when collected a second time. An `InputStream` is read on `Dispatchers.IO`; the SDK closes the stream it opened from a file or a factory and never closes the one passed to `once`, nor cancels a channel. `length()` is the file size, the value passed, or `null` (chunked); an unreadable file is an `IllegalArgumentException`. `toString()` prints `BodySource[<kind>, <n> bytes]` or `BodySource[<kind>, length unknown]`.
Why: the shape of Java's `BodySource` with the carriers Kotlin callers already hold (design 6.2). A `Flow` that is cold can be replayed, but a flow passed to `once` is trusted by nobody, so the replayable flow takes a factory.
Where: `body_source.mustache`, `default_api_client.mustache` (`BodySourceContent`).
Test: `DefaultApiClientUnitTest` (`bhvStreaming023` to `027`).

### The operation takes a `BodySource`, the transport writes it as it is read
Rule: `BetterKotlinCodegen.markBinaryUpload` retypes the body of an operation whose body is raw binary (not multipart, no form parameters) to `BodySource` before the shared pass, so every signature gets it. `DefaultApiClient.buildRequestBody` sends an in-memory source like a `ByteArray` and any other as `BodySourceContent`, an `OutgoingContent.WriteChannelContent` with `contentLength` set when the length is known (Content-Length) and absent otherwise (Ktor CIO then sends chunked); each piece is flushed so a gated stream proves the body is not buffered. `BaseApi` passes a `BodySource` through whatever the declared content type.
Where: `BetterKotlinCodegen.java`, `default_api_client.mustache`, `base_api.mustache`, `api/api.mustache`, `api/api_interface.mustache`.
Test: `PetApiTest` (`bhvOperations070` to `073`), `BaseApiTest` (`bhvMisc217`), `DefaultApiClientUnitTest`.

### Retry, redirect and 401 rules for a single-use body
Rule: `sendWithRetries` sets `maxRetries` to 0 for a single-use body, so the first answer or error is reported unchanged; the redirect loop raises `ApiException` (the real status, `Refusing to replay a streamed request body`) when the body that would be sent next is single use, and never buffers it; `BaseApi` does not replay a 401 whose body is single use; `SseStream.open` has an overload with `singleUseBody` that turns reconnecting off whatever the options say.
Where: `default_api_client.mustache`, `base_api.mustache`, `sse_stream.mustache`.
Test: `DefaultApiClientUnitTest` (`bhvStreaming026`, `027`), `EventStreamTest` (`bhvSse016`).

