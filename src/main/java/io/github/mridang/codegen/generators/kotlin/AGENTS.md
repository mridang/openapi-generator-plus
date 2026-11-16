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
| allOf with a discriminator | sealed interface plus flattened data classes | a data class cannot be `open`; see S4 |
| Required and nullable | `T?` with no default | the absence of a default is what makes the key required; see S5 |
| Decimals | bare `format: number` is `java.math.BigDecimal`, precision-preserving | root AGENTS.md "format: number" entry; java and csharp do the same |
| Dates and ids | `OffsetDateTime`, `LocalDate`, `LocalTime`, `Duration`, `kotlin.uuid.Uuid`, `io.ktor.http.Url` through contextual serializers | the platform types; only absolute `format: uri` maps to `Url`, `uri-reference` and `uri-template` stay `String` |
| Exceptions | unchecked, cause supported natively | `Throwable.cause` is the platform mechanism, so `SerializationError.cause` and `OAuth2TokenError` constructors are allowed to differ in the API surface check |
| Free-form `Any` | `@Contextual Any` | kotlinx-serialization has no built-in serializer for `Any`; the codegen annotates it |
| Soft keywords | `file` is escaped to `_file` | repository policy, see AGENTS:file-keyword |
| Resource management | `AutoCloseable`, used with `use { }` | JVM idiom for `close()` |
| Multipart | a hand-written `MultipartWriter` buffers the body | Ktor's `formData()` injects a per-part `Content-Length`; see D89 |

## Settled decisions (do not re-open in review)

Status words: "implemented" means the template or codegen already does it; "pending" means the owner decided it and the kotlin templates have not caught up, so a diff that moves toward it is correct and a diff that moves away is not.

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

### Strict containers (S1, F006, 2026-11-16) - implemented
Rule: a response that is a container of primitives (`Map<String, Int>`, `List<List<Int>>`) is checked element by element like a single primitive.
Why: container leaves had bypassed the strict primitive checks in six SDKs. Kotlin walks `serializer<T>().descriptor` against the parsed element before decoding and throws a `SerializationException`; annotating type arguments with `@Serializable(with = ...)` was rejected as fragile with reified serializers. This is D54.
Where: `object_serializer.mustache` (`deserialize`, the `Strict*Serializer` objects).
Test: generated `ObjectSerializerTest` (`getInventory` and `getMatrix` rows).

### Content parameters use the SDK serializer in every location (S2, F054, D80, 2026-11-16) - implemented
Rule: `content:` parameters are encoded through `objectSerializer.serialize` in query, header, path and cookie, with identical bytes in all twelve.
Why: twelve different JSON encoders were in use. Query, header, path and cookie all go through `ValueSerializer.serializeContent`, which calls the SDK serializer. See also D50.
Where: `api/api.mustache`, `object_serializer.mustache`.
Test: generated `ValueSerializerTest` (`serializeContent` rows for header, path and cookie).

### Schema constraints are not enforced (S3, F056, D55, 2026-11-16) - implemented
Rule: the client does not promise to enforce pattern, minLength, maxLength, minimum, maximum, multipleOf, minItems or maxItems, and kotlin adds no enforcement.
Why: the server owns validation; only python enforces, because pydantic does it natively and cheaply. The README section is titled "Schema constraints" and sits under Caveats; `dependentRequired` stays under Not supported.
Where: `readme.mustache`.
Test: none (absence); the petstore declares no such keyword.

### allOf with a discriminator is a sealed interface plus flattened data classes (S4, F057, F802, 2026-11-16) - implemented
Rule: the discriminated parent becomes a sealed interface, each subtype is a flattened data class that lists the common fields, and the subtype carries the discriminator. A model reused in two unions must not be re-parented: inheritance (here, the sealed interface) is only for the allOf-with-discriminator parent, and plain unions use interfaces or sealed types.
Why: an `open data class` does not compile, which is why kotlin cannot inherit like java, csharp, node, python, ruby, php and dart. `model.mustache` renders the discriminated parent (`isDiscriminatedBase`) as a sealed interface with a discriminator-reading serializer, and each subtype lists the common fields and implements it. Discriminated oneOf keeps its hand-written wrapper (`actualInstance` plus a serializer reading the discriminator).
Where: `models/model.mustache`.
Test: the discriminated fixtures in the petstore (`DryFood`, `WetFood`, `PetFood`) through `ComposedSchemaTest` and `ObjectSerializerTest`.

### Required and nullable (S5, F215, D66, 2026-11-16) - implemented
Rule: a required nullable property is `T?` with no default; the key must be present on decode (missing is an error), an explicit null is legal and kept, and encode always writes the key as null when unset.
Why: kotlin's `explicitNulls` and `encodeDefaults` are Json-wide, so the behaviour comes from the property having no default plus the Json configuration in `createDefaultJson`, not from a per-property annotation.
Where: `models/model.mustache` (constructor property line), `object_serializer.mustache`.
Test: `ObjectSerializerTest.requiredNullablePropertyKeepsItsKey`. The API surface check reads the `?` as optional, which `etc/api-surface-allow.json` allows for `requirednullable.note` on kotlin.

### uniqueItems order is unspecified; do not sort (S6, F319, F871, 2026-11-16) - implemented
Rule: sets go on the wire in the language's natural collection order (`LinkedHashSet` insertion order), documented in the README. Kotlin must not sort them.
Why: this supersedes D69, which proposed ascending scalar order. The sort (`sortScalarSets`, `SCALAR_ORDER`) is removed from `object_serializer.mustache`; the README says the order is unspecified.
Where: `object_serializer.mustache`, `BetterKotlinCodegen.getUniqueItemsSetType`.
Test: generated `ObjectSerializerTest` (`scalarSetKeepsItsInsertionOrderOnTheWire`).

### Cancellation is coroutine cancellation (S7, F228, F885, 2026-11-16) - implemented
Rule: no new parameter. Cancelling the calling coroutine aborts the request and raises `CancellationException`, never the timeout error; `NetworkTimeoutException` is only for the SDK's timeout.
Why: structured concurrency is what Kotlin callers already use (`withTimeout`, scope cancellation); a wrapper options object would be less natural. The transport rethrows `kotlinx.coroutines.CancellationException` untouched. The README has a top-level Cancellation section naming coroutine cancellation and `withTimeout`, and SKILLS has the same under Configuration.
Where: `default_api_client.mustache` (the catch that rethrows cancellation), `readme.mustache`, `skills.mustache`.
Test: generated `DefaultApiClientTest`.

### Parameter order (S8, F803, 2026-11-16, owner confirmation pending) - implemented
Rule: required parameters, then server, then the optional-parameters Options object; kotlin has no per-call cancellation handle because the coroutine context carries it. Adding security never moves an argument.
Why: adding a security scheme must not break existing server-only call sites.
Where: `api/api.mustache`, `api/options.mustache`.
Test: generated `PetApiTest`.

### Injected transport (S9, F405, 2026-11-16) - implemented
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

### Multipart parts carry only Content-Disposition and Content-Type (D89, F252)
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

### OAuth2 surface differences that are allowed (api-surface-allow)
Rule: the token manager is internal support code, so `oauth2tokenmanager.exchanged` is internal and `oauth2tokenmanager.setapiclient` is an internal property; `OAuth2TokenError` constructors take an optional cause; `sendRequest` gets its per-request redirect option by inheriting the interface default (`noRedirect: Boolean = false`); `objectserializer`, `valueserializer` and `headerselector` are `internal`; `apierror.gettypederrorbody` takes a reified type parameter with no value argument; `serializationerror.cause` is the platform `Throwable.cause`.
Why: each is the idiomatic kotlin spelling of the same behaviour; the surface check reads them as differences otherwise.
Where: `etc/api-surface-allow.json`.
Test: the API surface check (`API_SURFACE.md`).

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

### Equality and string form of models (D23, D24, F321, F455)
Rule: models with a `ByteArray`, `Array`, `List<ByteArray>`, `Map<String, ByteArray>` or `BigDecimal` property, or an additionalProperties bucket, get a hand-written `equals`/`hashCode` (`contentEquals`, per-key comparison for maps, numeric comparison for `BigDecimal`, the `@Transient` bucket folded in); such models also get a field-dumping `toString` that is element-wise for byte arrays and appends the bucket; union wrappers get `equals`/`hashCode` too.
Why: the synthesized data-class `equals` compares arrays by identity and excludes the `@Transient` bucket (which is now a constructor parameter, see batch 6, so `copy()` carries it). `List<BigDecimal>` and `Map<String, BigDecimal>` fields still compare scale-sensitively because `eqDecimal` covers only a direct `BigDecimal` property; that is open (F455, needs a spec), see Known limitations.
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

### Free-form null retention is not aligned (F314, closed audit item)
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

### Inline comments are allowed in kotlin (F195, deferred)
Rule: `KotlinFormattingSpec.getInlineCommentPattern()` returns null, so the shared inline-comment ban is off for kotlin; doc comments are separated from line comments in the templates.
Why: not forced, but deferred; do not flag the absence of the ban.
Where: `KotlinFormattingSpec.java`.
Test: `KotlinFormattingSpec`.

### Decisions taken in batch 6

Each follows the audit's own proposal, because the owner had not decided; reopen only with new evidence.

- **Redirect edge cases (F904, F902, F861).** A 3xx without a usable `Location` is returned unchanged even when the redirect budget is spent (the too-many-redirects error needs a followable `Location`); `maxRedirects` must not be negative (`Invalid maxRedirects: must not be negative (got N)`); the HTTPS-to-HTTP refusal carries the response headers; URLs in refusal messages drop their query and fragment, so a query-parameter API key never reaches a message (`redactUrl`).
- **Timeout (D77, F924).** `timeout` must be null or greater than zero: `Invalid timeout: must be a positive number of milliseconds (got N)`. Zero is no longer accepted.
- **Header and charset parsing (F905, F906, F901).** `Accept-Encoding` presence is case-insensitive like User-Agent; the response charset is read from a parameter named `charset` in any letter case with optional spaces around `=`, never from a name that only ends in it; a literal `+` in proxy userinfo is kept (`%2B` before `URLDecoder`).
- **OAuth tokens (F854, F895, F863).** One expiry margin of min(60 s, expires_in / 2) is applied when the expiry is stored and none when it is read, so a token valid for 60 s is reused for 30 s and one valid for 90 s for 45 s; `expires_in` 0 is the way to get an already-stale token (batch 7). A `token_type` other than `Bearer` (any letter case) is refused with `Unsupported token_type in token response`; a missing or null one is accepted. The empty-code message is `Authorization code must not be empty` (blank is still rejected).
- **Per-call authenticators (F856).** `BaseApi.invokeApi` hands the API's own transport to an `HttpAwareAuthenticator` passed per call, so a per-call OAuth2 or OpenID Connect authenticator no longer fails with the not-injected error. It does so on every call, which is idempotent.
- **Models (F864, F867, F869).** `additionalProperties` is a `@Transient` constructor parameter (default empty), so a caller can supply it and `copy()` carries it (the copy shares the same map instance); a `format: password` property prints as `***` in `toString` (such a model gets the hand-written `equals`/`hashCode`/`toString`); every enum's `toString()` is its wire value.
- **Names (F173).** `getModelNameCollisionPrefix` is `Model`, so a schema named `String`, `Any`, `Int` and the other language primitives becomes `ModelString` and so on.
- **Docs (F883, F884, F885, F887, F888, F889, F890).** README has Installation and Usage, then Cancellation, Custom HTTP client and Raw responses/timeouts/request ids, then Caveats (decimal precision, Schema constraints) and Not supported (webhooks and callbacks, XML bodies, Link Objects, mutualTLS, automatic retries, `dependentRequired`, SOCKS, LICENSE, URI normalization). SKILLS has the Custom HTTP client and Cancellation sections, a model sample that sets its required properties, and the min(60 s, half-lifetime) token margin.
- **Not applicable to kotlin.** F929: the token-flow authenticators are `suspend`-only, so there is no sync accessor to guard. F149, F832, F839, F054, F057, F803: already conforming (the code and tests existed; only this file was stale).

## Known limitations and out of scope

- **Ambient proxy (D74) is a forced divergence on the JVM.** Ktor's CIO engine falls back to `ProxySelector.getDefault()` (the `http.proxyHost` and `https.proxyHost` JVM properties, and the OS proxy) whenever `TransportOptions.proxy` is null, and `HttpClientEngineConfig` has no way to switch that off per client (a `DIRECT` proxy is rejected by `CIOEngine`). The SDK therefore cannot ignore ambient proxy settings; a test that sets the properties and expects a direct connection fails, so none is generated.
- `List<BigDecimal>` and `Map<String, BigDecimal>` fields compare scale-sensitively (F455): the petstore declares no such field, so no test can lock a fix. Needs `weightHistory: {type: array, items: {type: number}}` and `weightByDate: {type: object, additionalProperties: {type: number}}` on Pet, then `eqListDecimal`/`eqMapDecimal` in `postProcessModels` and the model template.
- Skipped in batch 6 because they need a cross-language decision or a shared-Java change: automatic retries (F823), per-call request options (F827), one configuration object (F828), request context on errors (F830), a `retryAfter` accessor on `TooManyRequestsException` (F831), tri-state optional nullable fields (F834), open enums and unions (F835), multi-value headers (F836), the streaming transport contract (F837), shared method classification (F838), form-style cookie encoding (F840), the 3.1 fallback wrappers (F843), 401 token invalidation (F855), per-scheme authenticators (F857), PKCE and nonce (F858), OIDC issuer and https checks (F859), default headers on token requests (F860), credential providers (F862), typed additionalProperties (F865), the `$ref` sibling default (F866), the binary request type (F826), the response size cap (F908), package metadata (F894), parameter example docs (F893), environment proxies (F090).
- Strict-key `unevaluatedProperties` is not on the production deserialize path (second audit tail).
- Multipart tests are structural, not byte-level, because Ktor's body is opaque to the test.
- Overflowing double literals such as `1e400` decode to Infinity in kotlin (F390, parked: the stated no-non-finite contract is unmet in java, kotlin, python, node, ruby, php, dart).
- OAuth percent-encoding of `~ * ! ' ( )` in the token body, the Basic header and the authorize URL still follows the stdlib encoder in kotlin (D52 covers query and form bodies only); that is a latent, deferred item.
- The per-model oneOf/anyOf no-match message for kotlin is `No anyOf variant of <X> matched the response body`; it differs from the other eleven and is documented, not harmonised (F396).
- The debug string field sets of the OAuth2 authenticators differ per language (F285), not harmonised.
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
- **Test parity added (F1144, F1146, F1113/F1114, F1148, F1162, F1127, F1069).** `ObjectSerializerTest` pins the hostile Availability members, the EdgeCases.phase wire values, keyword and digit-leading wire keys, literal U+2028/2029/DEL, and exact plain-notation decimals; `StoreApiTest` pins the lowercase uuid path; `PetApiTest` pins the header and cookie guards.
- **F864.** Already conforming since batch 6: `additionalProperties` is a constructor parameter, so `copy()` carries it.
- **F859.** OIDC discovery must carry an `issuer` equal to the discovery URL (minus `/.well-known/openid-configuration`, trailing slash ignored) and, unless the discovery URL is itself `http`, https endpoints.
- **F855.** A 401 answer calls `HttpAwareAuthenticator.invalidateAccessToken()` (default no-op, overridden by the OAuth2 and OIDC authenticators), so a revoked token is dropped; there is no automatic retry.
- **F860.** A `noRedirect` request (the token POST) does not receive `TransportOptions.defaultHeaders`; header layering is case-insensitive, the later layer replaces the earlier one. The OIDC discovery GET still receives them.
- **D92 (F1165).** A decoded response body drops `Content-Encoding` and `Content-Length` from the returned headers.
- **F861.** `NetworkException` and `NetworkTimeoutException` messages drop the query and fragment of any URL in the Ktor message (`redactUrlsInMessage`).
- **F1084.** SKILLS.md lists `{{errorPrefix}}Exception` with its `SerializationException`, `OAuth2ServerException` and `OAuth2TokenException` siblings.

## Decisions taken in batch 9

- **F855 (replay after 401).** `BaseApi.invokeApi` still calls `HttpAwareAuthenticator.invalidateAccessToken()` on a 401 (the OAuth2 client-credentials, password, auth-code and OIDC authenticators override it publicly; the refresh token is kept), and now fetches fresh auth headers and sends the request once more. A second 401 is the usual `UnauthorizedException`. A `Map` body (multipart) is not replayed, a re-authentication that throws the SDK's error root leaves the 401 as the answer, and Basic, Bearer and API key authenticators are not `HttpAwareAuthenticator`s, so they never replay. Kotlin has no stream body type: every other body is a `String` or `ByteArray`.
- **F858 (PKCE).** `buildAuthorizationUrl` adds `code_challenge` (S256) and `code_challenge_method=S256` and keeps a 43-character verifier (32 `SecureRandom` bytes, unpadded base64url) in the authenticator's internal `pkceVerifier`; `exchangeCode` sends it as `code_verifier` and clears it after a successful exchange. OIDC rediscovery copies the verifier to the rebuilt delegate. No nonce. The JDK's `MessageDigest` is the only dependency.
- **F823 (automatic retries).** `TransportOptions.builder().maxRetries(n)` (default 2, `0` disables, negative is refused). `DefaultApiClient.sendRequest` loops over `sendOnce`: it retries `NetworkException` (never `NetworkTimeoutException`) and the statuses 408, 409, 429 and 500-599 except 501, waiting 0.5 s doubling to 8 s minus up to 25% jitter, or `Retry-After` (seconds or HTTP date, capped at 60 s). One `TimeoutBudget` spans every attempt and wait; a wait that does not fit in what is left ends the retrying and the last answer or error is reported. `kotlinx.coroutines.delay` makes the wait cancellable. Every attempt merges its headers again (new `X-Request-ID`), but the caller's headers, including the auth header fetched for the call, are the same map; a token that expires during the 60 s ceiling is covered by the 401 replay above. All bodies are in memory, so a multipart `Map` body is rebuilt for each attempt and retried. The internal test seams `sleeper` and `jitterSource` keep the tests from waiting; the internal `DefaultApiClient(httpClient)` constructor now sets `maxRetries(0)`, so a test through it sees one request per call. `BaseApiTest.TestableApi` and the real-network failure tests set `maxRetries(0)` so they stay fast. The README "Automatic retries" entry moved from Not supported to a Retries section.
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
- **Skipped.** F455 (needs a spec change: `weightHistory` and `weightByDate` on the pet schema); F831 `retryAfter` accessor, F860 discovery default headers, F858 nonce, F862, F865, F866, F908, F894, F1077, F1081 (cross-language decisions not taken); F1056, F1061 (shared Java in `AbstractBetterCodegen`); F1085, F1157, F1172 (shared fixtures, spec and gates); F1092 (whether Ktor CIO copies `Proxy-Authorization` onto the CONNECT request or only the tunnelled request needs a live proxy run, and dropping the header for https would break proxy authentication); F1140 (Ktor's intermediate response release is not observable from `MockEngine`); F1151 (kotlin has no stream upload type, every upload body is a `ByteArray`); F1166 (forced divergence, see Known limitations).

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
