# Java SDK generator

The java generator (`java-plus`) emits a Maven project for Java 21 or newer (the pom compiles with `<release>21</release>`; the spec container is `maven:3.9-eclipse-temurin-25-alpine`, and the pom's OpenRewrite recipe is `UpgradeToJava25`). The HTTP stack is the JDK's own `java.net.http.HttpClient` (`DefaultApiClient`), with Jackson 2.19 (`jackson-databind`, `jsr310`, `jackson-databind-nullable`) for JSON, optional `dec` (brotli) and `zstd-jni` codecs for response decoding, and an optional OpenTelemetry API for trace context. Tests run under JUnit 6 with AssertJ, JUnit parallelism enabled through `junit-platform.properties`, and Testcontainers for the chasm and squid fixtures. Compilation runs with `-Xlint:all`, `failOnWarning`, Error Prone and NullAway at error level; the pom also wires Spotless (google-java-format 1.28.0), Checkstyle (the bundled `google_checks.xml`), SpotBugs, JaCoCo, javadoc with `failOnWarnings` and OpenRewrite. The codegen class javadoc still says "Apache HttpClient"; that is stale, nothing in the templates imports it.

## Read first

- [Shared generator rules](../AGENTS.md), where every cross-language decision is written once.
- [Repository root rules](../../../../../../../../../AGENTS.md).
- Codegen class: [BetterJavaCodegen.java](BetterJavaCodegen.java).
- Templates: `src/main/resources/templates/java/` (the pointer file there leads back to this one).
- Spec classes: `src/spec/java/io/github/mridang/codegen/spec/java/` (`JavaSpec`, `JavaBuildSpec`, `JavaClientSpec`, `JavaStaticAnalysisSpec`, `JavaFormattingSpec`, `JavaReservedWordsSpec`).
- Golden: `src/spec/resources/generated/java/` (never hand-edit; regenerate).

## Layout

| Path | What lives there | Edit this to change |
|---|---|---|
| `generators/java/BetterJavaCodegen.java` | type mapping (`BigDecimal`, `java.time`, `InputStream`, `byte[]`), reserved words, `Error` to `ModelError`, spec-derived `artifactId` and User-Agent, supporting-file list, `hasMultipleConsumes` flag | type mapping, which files are emitted, Maven coordinates |
| `templates/java/errors/*.mustache`, `api_error.mustache`, `root_exception.mustache` | the error tree (`*Exception` suffix), `ApiException` and `fromResponse` | error classes and messages |
| `templates/java/auth/**`, `authenticator.mustache` | Basic, Bearer, ApiKey authenticators, the composed per-scheme wrappers (`scheme_authenticator.mustache`), and `auth/oauth/*` token manager, flows and OIDC | auth behaviour |
| `templates/java/default_api_client.mustache`, `transport_options.mustache`, `configuration.mustache`, `client.mustache` | redirects, deadline, decompression, proxy and TLS, builders, the `Client` facade | transport options and wire behaviour |
| `templates/java/object_serializer.mustache`, `value_serializer.mustache`, `base_api.mustache`, `header_selector.mustache` | Jackson mapper, path, query, header and form encoding, multipart, response routing | serialization |
| `templates/java/models/model.mustache` | plain models, enums, oneOf/anyOf unions, allOf parents | models |
| `templates/java/api/*.mustache` | per-tag API classes, per-operation `*Options` classes, server variants | operation surface |
| `templates/java/test/**` | the emitted JUnit tests (extend these; never add a test file) | generated tests |
| `templates/java/pom.mustache`, `checkstyle_suppressions.mustache`, `dev_dependencies.mustache`, `editorconfig.mustache`, `makefile.mustache`, `reflect_config.mustache` | build file, named Checkstyle suppressions, dev-dependency list, GraalVM reflection config | dependencies, compiler and lint flags |
| `templates/java/readme.mustache`, `skills.mustache`, `skills_example_arg.mustache` | README and SKILLS.md | docs |
| `src/main/resources/reserved-words/java.txt`, `src/test/resources/scripts/JavaKeywords.java` | reserved words, pinned by `JavaReservedWordsSpec` | escaped identifiers |

## Idioms (deliberate differences from the other eleven)

| Topic | What java does | Why |
|---|---|---|
| Error suffix and caller mistakes | `*Exception`; caller mistakes raise `IllegalArgumentException` or `IllegalStateException` | the JDK's own exception classes |
| Cancellation | a thread interrupt surfaces as `java.util.concurrent.CancellationException`, never the timeout error (S7) | the platform primitive; the SDK adds no cancellation API |
| Lifecycle | `AutoCloseable.close()`, graceful and idempotent | try-with-resources |
| Client credentials per scheme | `Client.builder(host)` with one setter per scheme, `.transportOptions(...)`, `.build()` | java has no named or optional constructor arguments; a builder is how it spells one optional argument per scheme |
| Optional arguments | `@Nullable` parameters plus overloads; the caller passes `null` | java has no default arguments |
| Per-operation optionals | a generated `<Operation>Options` class passed last | no named or optional arguments |
| `ServerVariable` | its own file `ServerVariable.java` | one public top-level class per `.java` file |
| Union wrappers | no `actualInstance` member: the decoded value is itself the variant through a polymorphic base class | Jackson polymorphic deserialization |
| `ObjectSerializer`, `ValueSerializer`, `HeaderSelector` | package-private | hides Jackson from the public surface |
| `parseJson` | no separate helper; Jackson parses and applies the depth limit | standard library parser |
| Cause chaining | no `cause` member on `SerializationException`; `OAuth2TokenException` takes an optional cause in its constructors | `Throwable.getCause` already exists |
| Auth-header accessor | one synchronous `getAuthHeaders` | the HTTP client is synchronous |
| Per-scheme authenticator wrappers | composed (hold a base authenticator), not subclasses; `instanceof` the base is false | the three base authenticators are `final` so SpotBugs `CT_CONSTRUCTOR_THROW` stays quiet |
| Model named `Error` | generated as `ModelError` | would shadow `java.lang.Error` (Error Prone `JavaLangClash`) |
| Reserved words | `true`, `false`, `null` and `_` are escaped too | they are illegal identifiers though not keywords (D44) |
| `format: number`, `decimal` | `BigDecimal` | precision-preserving, like kotlin and csharp |
| `format: binary` | `InputStream` (request and response) | stream-typed; changing to `byte[]` is a public API change (parked) |
| Doc comments | `///` markdown doc-comments allowed; `//` line comments rejected in `src/main` | Java 23+ markdown doc-comments and `JavaFormattingSpec` |

## Settled decisions (do not re-open in review)

The 2026-11-16 session decisions come first. The rules are written once in the shared file; each entry here says how java realises it. Where a decision is not landed in java yet, the entry says so.

### Injected transport is `java.net.http.HttpClient` (S9, 2026-11-16)
Rule: the client builder accepts a caller-supplied `java.net.http.HttpClient`, keeps the SDK defaults applied, never closes the caller's client and fails fast when it conflicts with the SDK's own proxy, TLS or pool settings.
Why: every SDK generator compared (OpenAI, Stainless, Speakeasy, Fern, Azure, Stripe) accepts the standard client object.
Where: `client.mustache`, `default_api_client.mustache`, `transport_options.mustache` (`TransportOptions.builder().httpClient(...)`). Landed.
Test: `ClientTest`, `DefaultApiClientUnitTest` (add rows there).

### Cancellation is the platform's interrupt (S7, 2026-11-16)
Rule: java has no new parameter; a thread interrupt during a request surfaces as `CancellationException` and is never rewrapped as `NetworkTimeoutException`.
Why: the interrupt is what Java callers already use; a shared `RequestOptions` wrapper is less natural than the native handle.
Where: `default_api_client.mustache` (the `InterruptedException` branch builds the `CancellationException`).
Test: `DefaultApiClientUnitTest`.

### Parameter order is fixed (S8, 2026-11-16)
Rule: required parameters, then the server, then the `*Options` object, always, whether or not the operation has security; adding security never moves an argument.
Why: a positional options parameter inserted before the server silently broke existing server-only call sites.
Where: `api/api.mustache`, `BetterJavaCodegen.populateOperationDecorators` (`serverBeforeOptions`, `callArgsNullServer`). Landed.
Test: `test/api/*ApiTest.mustache` (golden `PetApiTest`).

### allOf with a discriminator uses class inheritance, once (S4, 2026-11-16)
Rule: only the allOf-plus-discriminator parent is a superclass (`extends {{parent}}` in `model.mustache`); a model reused in two unions is not re-parented, unions use the polymorphic base and `@JsonTypeInfo`/`@JsonSubTypes`.
Why: a Java class has one parent. F802 (the `DeprecatedFood` union re-parents `DryFood` and `WetFood`) is the bug this closes.
Where: `models/model.mustache`. Not landed: F802 needs a shared-Java fix (`AbstractBetterCodegen.setParentOnChild` keeps the first union that claims a child).
Landed (#104): the shared pass `markClassicDiscriminatorInheritance` marks the base (`x-allof-base`), its children (`x-allof-child`) and the inherited properties (`x-inherited`). The base is an abstract class with `@JsonTypeInfo`/`@JsonSubTypes`, the common fields and one protected constructor and no builder; a child declares only its own fields, passes the common ones through `super(...)` (`superArgs`, set by `BetterJavaCodegen.decorateClassicDiscriminatorChild`) and repeats the type info with `defaultImpl` so it still decodes as itself. Why no builder on the base: a static `builder()` or `toBuilder()` in both classes would clash on the return type. An unknown value fails with Jackson's invalid-type-id error naming it.
Test: `test/ComposedSchemaTest.mustache` (`AllOfDiscriminatorTests`, bhv unions 301-304, bhv serializationobject 301).

### Required plus nullable keeps the key (S5, D66, 2026-11-16)
Rule: a required nullable property must be present on decode (missing is an error), an explicit null is a legal value and is kept, and encode always writes the key, null when unset.
Why: OpenAPI separates required from nullable; java used to accept a missing key. D66 puts `@JsonInclude(ALWAYS)` and `required = true` on the creator parameter without `requireNonNull`.
Where: `models/model.mustache`, `object_serializer.mustache`. F215 is open for the decode side.
Test: golden model tests and `ObjectSerializerTest`.

### Strict containers (S1, 2026-11-16)
Rule: a response that is a container of primitives (`Map<String,Integer>`, `List<List<Integer>>`) is checked element by element like a single primitive.
Why: container returns bypassed the strict primitive checks in seven SDKs, java among them.
Where: `object_serializer.mustache`, `base_api.mustache`. Landed in wave 2: the mapper's coercion rules already apply to every container element (see Decisions taken in wave 2).
Test: `ObjectSerializerTest`, `BaseApiTest`.

### Content parameters use the SDK serializer (S2, D50, D80, 2026-11-16)
Rule: a `content:` parameter is encoded through `ObjectSerializer.serialize` in every location (query, header, path, cookie), the same bytes as the other eleven.
Why: twelve different JSON encoders produced twelve byte forms. The query branch is already canonical; header, path and cookie are open.
Where: `api/api.mustache`, `value_serializer.mustache`.
Test: `ValueSerializerTest`, golden `PetApiTest`.

### Schema constraints are not enforced (S3, D55, 2026-11-16)
Rule: `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems` and `maxItems` are never checked client-side; the server owns validation. The README documents it in the section "Schema constraints".
Why: only python enforces them (natively through pydantic); java would need Bean Validation at the cost of a dependency and a second source of truth.
Where: `models/model.mustache`, `readme.mustache`.
Test: none by design; do not add enforcement.

### `uniqueItems` order is left unspecified (S6, 2026-11-16)
Rule: a `Set` is sent in the collection's natural order (`LinkedHashSet`, insertion order); do not sort. This supersedes D69, which proposed sorting.
Why: the order carries no meaning and sorting is work with no contract behind it. The READMEs say so.
Where: `object_serializer.mustache` (no sorting module), `readme.mustache`. Landed in batch 6.
Test: `ObjectSerializerTest` (`uniqueScalarArrayKeepsInsertionOrder`).

### Dependencies: pure JVM libraries only (S10, 2026-11-16)
Rule: a small pure library or a version bump is acceptable when it standardises behaviour; a dependency that needs a native binary is not.
Why: native binaries break the build matrix. The one exception already present is `zstd-jni`, an optional codec: without it a `zstd` response raises (`ClassNotFoundException` becomes `IOException`).
Where: `pom.mustache`, `dev_dependencies.mustache`.
Test: `etc/ci-local.sh check Java` (`JavaBuildSpec`, dependency analysis in the client's CI).

### Secrets are redacted in string forms (S15, 2026-11-16)
Rule: the `toString` of `Configuration` and `TransportOptions` redacts the proxy password and any default header named `Authorization`, `Proxy-Authorization`, `Cookie` or containing key, token or secret, as `***`.
Why: string forms end up in logs.
Where: `configuration.mustache`, `transport_options.mustache`. Not landed in java: neither template renders `***` yet.
Test: `ConfigurationTest`, `TransportOptionsTest`.

### Timeouts, redirects and proxy validation (S16, D76, D71, D73, D74, D75, 2026-11-16)
Rule: `timeout` is one end-to-end deadline: a `System.nanoTime()` deadline is taken before the first send and each redirect hop gets the remaining budget, throwing `NetworkTimeoutException` at zero (D76). An unparseable `Location` behaves like a missing one: the 3xx is returned (D71). `caCertPath` is validated whenever set, even with `verifySsl=false` (D73). Ambient proxy settings are ignored: without `TransportOptions.proxy` the client uses `ProxySelector.of(null)` (D74). The SDK's own `Proxy-Authorization` survives redirect hops (D75). A caller-supplied stream or file handle is never closed by the SDK; proxy URL validation happens in `build()`.
Why: byte-identical behaviour across the twelve; the JDK client otherwise honours `http.proxyHost` and the system proxy.
Where: `default_api_client.mustache`, `transport_options.mustache`.
Test: `DefaultApiClientUnitTest`, `DefaultApiClientTest`, `TransportOptionsTest`.

### Scope order, UUID case, blank keys, error cause (S14, D04, D19, D67, 2026-11-16)
Rule: OAuth scopes keep the spec's declared order (one generator fix, `SchemeAuthSpec` uses a `LinkedHashMap`); a UUID is written lowercase and validated; an api key or bearer token is blank only when empty or ASCII whitespace (`isAsciiBlank`, never `String.isBlank`), and `Bearer ` is stripped once in the constructor (D19); every status-specific exception accepts an optional cause.
Why: `String.isBlank` treats Unicode whitespace as blank, which no other SDK does.
Where: `AbstractBetterCodegen` (`formatJavaScopes`), `auth/bearer_authenticator.mustache`, `auth/api_key_authenticator.mustache`, `errors/*.mustache`.
Test: `BearerAuthenticatorTest`, `ApiKeyAuthenticatorTest`, `ApiExceptionTest`.

### OIDC rediscovery keeps the session, and the exchange replaces it (D01, D02, D03)
Rule: the exchanged flag lives in `OAuth2TokenManager` (`markExchanged`, `isExchanged`); the OIDC authenticator rebuilds its delegate around the previous delegate's manager under `synchronized`. `exchange(tokenUrl, params, extraHeaders)` always POSTs the authorization_code grant, restores the previous state on failure and replaces the session on success. The refresh grant carries client authentication and the password authenticator no longer branches on a stored refresh token.
Why: RFC 6749 sections 3.2.1, 4.1.3 and 6; rediscovery used to drop the session and `exchangeCode` could skip the request.
Where: `auth/oauth/{oauth2_token_manager,oauth2_auth_code_authenticator,oauth2_password_authenticator,openid_connect_authenticator}.mustache`.
Test: `OAuth2TokenManagerTest`, `OAuth2AuthCodeAuthenticatorTest`, `OAuth2PasswordAuthenticatorTest`, `OpenIdConnectAuthenticatorTest`.

### OAuth request details (D05, D06, D08, D10, D11)
Rule: OIDC always requests `openid` first (the configured scopes are deduplicated against it); an empty implicit-flow token is "unset" and `getAuthHeaders` throws `IllegalStateException`; `expires_in` and discovery max-age are clamped to one year (31 536 000 s) before date arithmetic; token-request and authorize-URL parameters use `LinkedHashMap` in the fixed order; an empty `state` is omitted. The token manager requires a JSON string `refresh_token` (`JsonNode.isTextual()`) and ignores a numeric or boolean one.
Why: OIDC Core 3.1.2.1, RFC 6749 sections 4.1.1 and 5.1, and byte-identical wire output.
Where: `auth/oauth/*.mustache`.
Test: the per-flow `OAuth2*AuthenticatorTest`, `OAuth2TokenManagerTest`.

### A decode failure carries the response (errors205)
Rule: a response body that fails to decode into its return type (JSON parse, a schema mismatch, a Content-Type that is not JSON for a non-string return, the decoder of an operation with several 2xx schemas) raises the same `SerializationException` as before, now carrying the status code, the lower-cased headers, the raw body, and the request method and the path with its query stripped. A `SerializationException` with no response behind it has status `0` and null fields. It stays a `SerializationException` and not an `ApiException` because decisions.md promises the serialization error for a decode failure (unknown enum values included).
Why: a 200 whose body does not parse is otherwise an unexplained failure; the status and raw body are what a caller logs.
Where: `errors/SerializationException.mustache` (new constructor and getters; `ApiException.stripQuery` is package-private and shared), `base_api.mustache` (the success decode of `invokeApiForResult` is wrapped and rethrown with the response), `api_error.mustache`.
Not covered: a Content-Encoding that cannot be decompressed is raised by `default_api_client.mustache` as `ApiException` with a null body and no method or path (D13); the catalogue promise names the raw body, so the owner decides whether that path changes.
Test: `BaseApiTest` (`bhvErrors205ADecodeFailureCarriesTheStatusHeadersRawBodyAndRequestWithoutItsQuery`), `ApiExceptionTest` (`serializationExceptionWithoutAResponseHasNoResponseContextAndStripsTheQuery`, unnumbered).

### The error carries the real response (D13)
Rule: an `ApiException` for a response that arrived but was unusable (decompress failure, redirect refusals) carries the real status and the lower-cased response header map, null body, and the failure as cause; `ApiException(int, String, Map, Throwable)` replaces the old three-argument constructor. `responseHeaders` null means no HTTP response arrived and is never defaulted to an empty map (see the shared "Forced divergences").
Why: callers branch on null headers to tell a transport failure from a bad response.
Where: `api_error.mustache`, `default_api_client.mustache`.
Test: `ApiExceptionTest`, `DefaultApiClientUnitTest`.

### Header injection is case-insensitive and the caller wins (D21, D22)
Rule: `User-Agent` and `X-Request-ID` are injected only when no `equalsIgnoreCase` match exists in the merged headers, and the caller's key is left as written. The default User-Agent product token is the spec-derived Maven `artifactId` (the invoker package's last segment plus `-client`), not the invoker package.
Why: RFC 9110 5.1; the published package name is the identifier users can look up. An explicit `artifactId` option still overrides the derived one.
Where: `default_api_client.mustache`, `transport_options.mustache`, `BetterJavaCodegen` (`artifactId`, `userAgentDefault`).
Test: `DefaultApiClientTest`, `TransportOptionsTest`.

### Models: string form, equality, any-properties (D23, D24, D63)
Rule: `toString` dumps `ClassName{prop=value, ...}` over all vars plus the additionalProperties bucket, a top-level `byte[]` through `Arrays.toString` and containers through `ObjectSerializer.structuralToString`; oneOf/anyOf wrappers delegate equality and hash to the held variant with `Objects.deepEquals`; the `@JsonAnyGetter` omits keys that equal declared property names so the declared value wins and the key is written once; a composed scheme wrapper prints the redacted base repr under its own class name.
Why: identity hashes and type names in logs look like data; equality on a decoded union must not be identity.
Where: `models/model.mustache`, `auth/scheme_authenticator.mustache`.
Test: `ComposedSchemaTest`, `MetadataTest`, `ObjectSerializerTest`. F321 is parked: java renders nested containers of byte arrays raw in places.

### Response routing: binary, byte, text (D30, D31, D32, D34, D35)
Rule: a `format: binary` return (`InputStream`) is never JSON-parsed. A `format: byte` return with an empty or JSON content type is JSON-parsed and decoded with the strict base64 decoder; every failure is a `SerializationException`. A non-JSON success type on a non-binary return gives a `String` return the text only for text-class types and otherwise throws a `SerializationException` naming the content type. A binary request body is sent verbatim under any selected content type through `rawBody`. An empty Content-Type is text-class in both transport and the `BaseApi` mirror.
Why: the transport hands text-class media types over as decoded text; every other outcome used to be an unchecked cast.
Where: `base_api.mustache`, `api/api.mustache`, `default_api_client.mustache`.
Test: `BaseApiTest`, `DefaultApiClientTest`, `ApiResultTest`.

### Multipart and form encoding (D36, D39, D53, D90)
Rule: a multipart primitive is a plain text part (`ObjectSerializer.toFormValue`, no part Content-Type), only models and maps are `application/json` parts; a form-urlencoded nested object or map is rejected with `SerializationException`; query, form and multipart parts use `LinkedHashMap` in declaration order; the multipart filename directive replaces each non-ASCII or control code point with one `_` (iterating `codePoints()`).
Why: one stringify form and one deterministic order across twelve SDKs.
Where: `default_api_client.mustache` (`addMultipartField`, `buildFilenameDirective`), `base_api.mustache`, `api/api.mustache`.
Test: `BaseApiTest`, `DefaultApiClientTest`, golden `PetApiTest`.

### Value serialisation (D48, D49, D51, D58, D62, D81)
Rule: a path value of exactly `.` or `..` throws `IllegalArgumentException`; `allowReserved` leaves literal only the RFC 3986 query set and always encodes `#`, `[` and `]`; `format: byte` parameters are base64 with padding (`ObjectSerializer.stringify`) and decoded with a strict RFC 4648 standard-alphabet pattern before `Base64.getDecoder()`; `format: uri` must be absolute (`URI.create` alone accepts relative); `format: time` is `HH:mm:ss`, plus `.SSS` only when the millisecond part is non-zero.
Why: byte-identical wire output across the twelve.
Where: `value_serializer.mustache`, `object_serializer.mustache`.
Test: `ValueSerializerTest`, `ObjectSerializerTest`.

### Required parameters, strings and the missing-parameter message (D28, D29, AGENTS Resolved)
Rule: a required string parameter in any location must be non-empty; the message is `Missing the required parameter '<name>' when calling <Class>.<operation>`, naming the caller-facing identifier (`{{paramName}}`, so `X-Correlation-Id` reads `xCorrelationId`); a multi-consumes selector rejects an unknown content type with `IllegalArgumentException`; a deprecated operation carries one `@Deprecated` before the `WithHttpInfo` method, so every overload that delegates to it carries the marker once.
Why: a second `@Deprecated` on one method is a compile error ("not a repeatable annotation interface"); a uniform message is the error contract.
Where: `api/api.mustache`, `default_api_client.mustache`. Do not add a per-branch guard.
Test: golden `PetApiTest`, `StoreApiTest`.

### Codegen escaping and names (D40, D41, D42, D43, D44, D46)
Rule: spec strings in code literals go through one escaper (`escapeText`; quote and backslash escaped, tab, newline and CR become a space, `*/` becomes `*_/`); doc prose is HTML-unescaped once in `preprocessOpenAPI` and kept `*/`-safe; the pom description uses `lambda.escapeXml`; per-operation server enums are emitted once from `serverEnums`; a digit-leading name gets an `n` prefix before casing; `java.txt` plus `null`, `true`, `false`, `_` are reserved; enum string literals escape backslash and quote, and the javadoc `{@code ...}` sink uses a comment-safe value.
Why: a literal must evaluate to exactly the spec's bytes; a keyword or literal as an identifier fails the compile.
Where: `AbstractBetterCodegen`, `BetterJavaCodegen`, `api/api.mustache`, `servers.mustache`, `pom.mustache`, `models/model.mustache`, `reserved-words/java.txt`.
Test: `JavaReservedWordsSpec`, `MetadataTest`, `FixtureVocabularyLeakTest`.

### Models and unions on the wire (D56, D64, D65, D66)
Rule: `readOnly` and `writeOnly` have no wire effect (java's `access =` Jackson modes are removed; a plain `@JsonProperty` plus the javadoc remark); non-discriminated oneOf/anyOf variants are tried in declaration order; a null element in a container of models is rejected unless the element schema is nullable.
Why: the OAS keywords only document; ordering and null handling must match the other eleven.
Where: `models/model.mustache` (`ONE_OF_SCHEMAS`), `object_serializer.mustache`.
Test: `ObjectSerializerTest`, `ComposedSchemaTest`.

### Decompression and response headers (D92, AGENTS Resolved)
Rule: an unrecognised response `Content-Encoding` raises (`IOException`, wrapped as `ApiException` with the status); an empty body passes through for any declared encoding (`data.length == 0`); `deflate` is zlib-wrapped (`InflaterInputStream`); a missing optional codec (`dec`, `zstd-jni`) raises instead of returning still-encoded bytes; after a successful manual decompress `Content-Encoding` and `Content-Length` are removed from the returned headers.
Why: never hand the caller bytes that are still encoded; the removed headers describe a body that no longer exists.
Where: `default_api_client.mustache`.
Test: `DefaultApiClientUnitTest`, `DefaultApiClientTest`.

### README and package metadata (AGENTS Resolved)
Rule: `Version` is `{{artifactVersion}}`, not the empty `{{packageVersion}}`; the decimal caveat says `BigDecimal` is precision-preserving and limits the IEEE-754 loss warning to `format: float` and `double`; `artifactId` defaults to the invoker package's last segment plus `-client`.
Why: the mismatched variable rendered blank fields, and the lossy-double caveat sent users to a workaround they do not need.
Where: `readme.mustache`, `BetterJavaCodegen`.
Test: `etc/ci-local.sh check Java` (golden diff).

### `ServerVariable` is its own file and is not folded back (Forced divergences)
Rule: `ServerVariable.java` stays separate from `ServerConfiguration.java`.
Why: java allows one public top-level class per file; the other nine co-locate it.
Where: `server_variable.mustache`, `server_configuration.mustache`.
Test: `ServerVariableTest`, `ServerConfigurationTest`.

### Generated code never disables a tool (Guardrails, Linting)
Rule: no generated file disables a rule set. Findings generated code cannot satisfy are named once, with a reason, in `checkstyle-suppressions.xml` (`SummaryJavadoc`, `AbbreviationAsWordInName`, `LineLength`, `MemberName`, `OverloadMethodsDeclarationOrder`, `VariableDeclarationUsageDistance`, and `MethodName`/`ParameterName` for `*Options`), in the pom's `-Xlint:-unchecked`, `-Xlint:-deprecation`, `-Xlint:-this-escape`, `-Xlint:-dangling-doc-comments`, `-Xep:InlineMeSuggester:OFF` and `-Xep:TypeParameterUnusedInFormals:OFF`, or by a class-level `@SuppressFBWarnings(justification = "generated code")` on `Client`, `DefaultApiClient` and models.
Why: suppressions scattered through sources hide real defects; the house rule forbids inline `@SuppressWarnings`.
Where: `pom.mustache`, `checkstyle_suppressions.mustache`, `client.mustache`, `default_api_client.mustache`.
Test: `JavaBuildSpec`, `JavaStaticAnalysisSpec`.

### Test layout and the dev-dependency list (Guardrails 1)
Rule: `.openapi-generator/DEV-DEPENDENCIES` lists only dependencies of tests a client actually receives (`emitUnitTests()`), not those of golden-only `generateTests` tests (`assertj-core` in `MetadataTest`/`ComposedSchemaTest`, `junit-jupiter-params` in `BaseApiTest`/`OAuth2TokenManagerTest`, `testcontainers` in the containers). Add a unit test to an existing template in `templates/java/test/`, never a new file.
Why: a client that declares an unused dependency fails `mvn dependency:analyze`.
Where: `dev_dependencies.mustache`, `templates/java/test/**`.
Test: a real client's CI gates; the golden does not run them.

### Mock seam (mocking001 to 009, issue #122 and the Invoker line of #131)
Rule: every API group has an interface and the client has one where the language can state it, additive next to the existing classes, using only SDK and standard-library types. `<Group>ApiInterface` (a `...Interface.java` file per group, registered in `BetterJavaCodegen` with the suffix `Interface.java`) and `ClientInterface extends AutoCloseable` with `get<Group>()` accessors beside the public fields. The convenience overloads are `default` methods that delegate to the full form, so a fake implements one form per operation; every class method carries `@Override` because Error Prone's `MissingOverride` is an error at `failOnWarning`. Tests: ClientTest.mustache (001, 002, 007, 008), api/PetApiTest.mustache (003 to 006, a `Proxy` fake that runs default methods with `InvocationHandler.invokeDefault`), ReadmeTest.mustache (009). Where: the templates named above, `BetterJavaCodegen`, the README template (`Testing your code`), `docs/languages/java.md`.

## Known limitations and out of scope

Callbacks, webhooks, XML bodies, Link Objects and mutual TLS are out of scope (#53, #70, #78, #88, #90). OpenAPI 3.1 and 3.2 are tracked as epics #96 and #49; the pinned swagger-parser rejects 3.2 documents (#50).

These java gaps are known:

- An optional request body can be omitted only on an operation that takes nothing else (`placeOrder()`), because an overload that drops the body from a longer signature makes a literal `null` ambiguous (D26, wave 2). With path, options, server or content-type arguments the body stays `@Nullable`.
- A model with required properties has no no-argument constructor: it is built with its required-argument constructor or `builder()`, and a missing required property fails in `build()` (D68, wave 2).
- `refreshUrl` keeps its position in the public OAuth2 constructors; moving it breaks positional callers.
- `format: binary` stays `InputStream` rather than `byte[]`.
- Only a model named `Error` is prefixed (`ModelError`); other collisions with `java.lang` or SDK type names are unguarded (D45, open).
- Object-valued query and header parameters are unsupported (D47, parked); `Set` order is unspecified (S6).
- `BigDecimal` is written with `toString`, so a value such as `1E+3` may appear in exponent notation; `WRITE_BIGDECIMAL_AS_PLAIN` is not enabled (D83 proposed it).
- The `FormattingSpec` and static-analysis specs do not gate tests as strictly as sources: SpotBugs does not run over generated tests (about 140 findings), javadoc with `failOnWarnings` and the OpenRewrite recipe are in the pom but run by no spec.
- OAuth2 authenticator repr field sets and the per-model oneOf/anyOf no-match wording differ from other languages; not harmonised.

## Build-breaking rules

- `mvn compile` must pass with `-Xlint:all`, `failOnWarning`, Error Prone and NullAway at ERROR over `{{invokerPackage}}`; fix the template, never add an inline `@SuppressWarnings`.
- `spotless:check` (google-java-format 1.28.0) must pass on `src/main` and `src/test`; a `//` line comment in `src/main` fails `JavaFormattingSpec` (use `/* */`, `/** */` or `///`), and file header comments are exempt.
- Checkstyle runs the unmodified `google_checks.xml` with `violationSeverity=warning` over sources and tests; only the named suppressions apply.
- SpotBugs runs at threshold Low with no exclude filter; the base authenticators stay `final` and wrappers compose them.
- `JavaReservedWordsSpec` pins `reserved-words/java.txt` against `JavaKeywords.java`.
- The spec runs in `maven:3.9-eclipse-temurin-25-alpine` with the Maven repository under `/root/.cache/java/m2`; a `ContainerLaunchException` or exit 137 is infrastructure, re-run once.
- Regenerate all twelve goldens together; the formatters run in Docker and local regeneration can drift.
- Test sample data must not use petstore vocabulary (`FixtureVocabularyLeakTest`).

## Verify

```
etc/ci-local.sh check Java
etc/ci-local.sh mvn -B test
```

Language-specific: `mvn verify -Dgroups=java`; inside a generated SDK `mvn compile`, `mvn test`, `mvn spotless:check`, `mvn spotbugs:check checkstyle:check`.

### Batch 5
Rule: the `Client` facade is `AutoCloseable` and closes the `DefaultApiClient` it built, gracefully (see round 4; a caller-supplied `HttpClient` is never closed; a closed client throws `IllegalStateException`). HTTP 429 maps to `TooManyRequestsException` (a `ClientException`). The "Unexpected response Content-Type" error names `Class.operationId`, passed as the first argument of `invokeApi`/`invokeApiForResult`. A `content:` cookie is encoded by `BaseApi.encodeContentCookie` (only `A-Z a-z 0-9 - . _ ~` literal, UTF-8 percent-encoded). Redirects follow RFC 9110 15.4: 301/302 rewrite only POST to GET, 303 rewrites everything but HEAD, 307/308 keep method and body.
Where: `client.mustache`, `errors/TooManyRequestsException.mustache`, `api_error.mustache`, `base_api.mustache`, `api/api.mustache`, `default_api_client.mustache`.
Test: `ClientTest`, `ApiExceptionTest`, `BaseApiTest`, `DefaultApiClientUnitTest`.

## Decisions taken in batch 6

Where an owner decision was needed, java took the audit's own proposal. Each rule is scoped to java and written once.

### Token expiry uses one margin
Rule: `OAuth2TokenManager` stores `expiry - min(60 s, ceil(expires_in / 2))` and serves the token while `now` is before it, so a 60 s token is reused for 30 s and a 1 s or 0 s token is never reused. A grant response without `expires_in` clears the expiry (cached until `invalidateAccessToken`), so a refresh answer without it no longer keeps the lapsed expiry. A `token_type` other than `bearer` (any case) raises `OAuth2TokenException("Unsupported token_type in token response")`; an absent one is accepted.
Where: `auth/oauth/oauth2_token_manager.mustache`. Test: `OAuth2TokenManagerTest` (`expiresInAppliesOneMarginCappedAtHalfTheLifetime`, `refreshResponseWithoutExpiresInKeepsNoExpiry`, `tokenTypeOtherThanBearerIsRejected`).

### OIDC discovery is checked
Rule: a discovery document whose `issuer` (when present) differs from the discovery URL minus `/.well-known/openid-configuration` (trailing slash ignored) raises `SerializationException`; `authorization_endpoint`, `token_endpoint` and the discovery URL must be https unless the host is `localhost`, `127.0.0.1` or `[::1]`. The base OAuth2 constructors do not check their URLs yet.
Where: `auth/oauth/openid_connect_authenticator.mustache`. Test: `OpenIdConnectAuthenticatorTest`.

### One end-to-end budget for the stale-connection retry (D76)
Rule: `sendWithRetry` takes the deadline; the one replay after "header parser received no bytes" gets the remaining nanos (rebuilt with `HttpRequest.newBuilder(request, ...)`) and raises `NetworkTimeoutException` when the budget is spent. `QUERY` counts as idempotent (partial: the shared classification is not done).
Where: `default_api_client.mustache`. Test: `DefaultApiClientUnitTest` (`staleConnectionRetryDrawsFromTheRemainingBudget`, `queryIsReplayedAsAnIdempotentMethodAndPostIsNot`).

### Redirect budget, wording and header case (D16, D29, D70, D77)
Rule: "too many redirects" fires only when the budget is exhausted and a usable `Location` is present; a bare 3xx is returned. Messages are `Too many redirects (exceeded <N>)`, `Refusing to replay request body across HTTPS->HTTP redirect: <current> -> <next>`, `Refusing to follow redirect to non-HTTP(S) URL: <next>` and `ApiClient has been closed and can no longer send requests`. `maxRedirects` below zero and `timeout <= 0` are rejected in the builder (`Invalid maxRedirects: must not be negative (got N)`, `Invalid timeout: must be a positive number of milliseconds (got N)`). A caller's `Accept-Encoding` in any case suppresses the SDK's. Proxy userinfo is percent-decoded without turning `+` into a space.
Where: `default_api_client.mustache`, `transport_options.mustache`. Test: `DefaultApiClientUnitTest`, `TransportOptionsTest`. The HTTPS-to-HTTP message is not pinned end to end (it needs a TLS fixture).

### Error and serializer messages (D15, D17)
Rule: `ApiException.toString` renders `Response headers: {"a":"b"}` as compact JSON with sorted keys; the serializer says `Failed to serialize object to JSON: <detail>`, `Failed to deserialize JSON: <detail>` and `JSON nesting depth <N> exceeds limit 1000`.
Where: `api_error.mustache`, `object_serializer.mustache`. Test: `ApiExceptionTest`, `ObjectSerializerTest`.

### Models
Rule: additional properties are written with the builder (`putAdditionalProperty`, `putAllAdditionalProperties`, `removeAdditionalProperty`, `additionalProperties(map)`), `setAdditionalProperty` is gone and the read accessor is unmodifiable (wave 2); the string form of an enum constant is its wire value; a `format: password` field prints as `***` in the model's `toString` (the wire still carries the value); a schema named like a `java.lang` type (`Error`, `Exception`, `Throwable`, `String`, `Object`, the boxed numbers, `Class`, `Enum`, `Record`, `Override`, `Deprecated`, `Thread`, `System`) is prefixed `Model`; apiKey header names are escaped as Java literals in the redirect strip list.
Where: `models/model.mustache`, `BetterJavaCodegen` (`JAVA_LANG_CLASHES`, `processOpenAPI`). Test: `MetadataTest`, `ObjectSerializerTest`, `BetterJavaCodegenTest`.

### Per-call authenticators get the client
Rule: `BaseApi` hands its `ApiClient` to a per-call `HttpAwareAuthenticator` before using it, so a per-call OAuth2 or OIDC authenticator works.
Where: `base_api.mustache`. Test: `BaseApiTest` (`perCallHttpAwareAuthenticatorReceivesTheClient`).

### README and SKILLS
Rule: the README order is Requirements, Installation, Usage (quick start, raw responses, timeouts, request id, cancellation, custom HTTP client), Build, Test, Package, Caveats, Not supported. Cancellation is documented as the thread interrupt raising `CancellationException`; "Not supported" lists retries, XML bodies and `nodeType`, Link Objects and mutual TLS. SKILLS gains custom HTTP client, cancellation and raw-response sections.
Where: `readme.mustache`, `skills.mustache`.

### Already conforming in batch 6
Already conforming: F803 (server before options), F054 (content parameters in every location), F149 and F832 (`Class.operationId` in the Content-Type error), F215 (required plus nullable keeps the key), F824, F831, F833, F839, F868 (superseded in wave 2: final fields and a builder). F929 (java has one synchronous accessor, so D12 does not apply).


## Decisions taken in batch 7

- F1086 (D79): proxy URL validation messages replace any `user:password@` userinfo with `***@` (`TransportOptions.Builder.redactUserInfo`). Test: `TransportOptionsTest` (`unusableProxyUrlIsRejectedWithTheExactMessage` rows).
- F860: a no-redirect request (the OAuth2 token POST) carries no `TransportOptions.defaultHeaders`; OIDC discovery still merges them (not changed). Test: `DefaultApiClientUnitTest`.
- F1162 (D83): a `BigDecimal` is written in plain notation in bodies (`StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN`) and in `stringify` (`toPlainString`). Test: `ObjectSerializerTest` (`FixtureWireValueTests`).
- F1080: the rendered `ApiException` caps the body at 1 KiB with a `... (N more bytes)` suffix, escapes CR, LF and control characters, and masks `Authorization`, `Proxy-Authorization`, `Cookie`, `Set-Cookie` and any header containing key, token or secret as `***`. The fields keep the real values. Test: `ApiExceptionTest`.
- F1093: a cross-origin redirect strips the static list plus any header whose name contains key, token or secret (the S15 heuristic). Test: `DefaultApiClientUnitTest` (`proxyAuthorizationSurvivesACrossOriginHttpRedirect`).
- F1165 (D92): a decoded response drops `content-encoding` and `content-length` from the returned headers; an identity body keeps them. Test: `DefaultApiClientUnitTest` (`zlibWrappedDeflateBodyIsDecompressed`).
- F1100: no pagination is generated. When added, it lives in a separate `<operation>Pages`-style method returning an `Iterator` or `Stream`, driven by an `x-pagination` extension or a generator option, never by changing the base method's return type. Nothing to test yet.
- F1084: SKILLS.md documents the whole error tree (`{{errorPrefix}}Exception` root, `SerializationException`, `OAuth2ServerException`, `OAuth2TokenException`, `ApiException`).
- Test additions with no source change: F1063 (`DefaultApiClientUnitTest`), F1067, F1114, F1115, F1116, F1124, F1130, F1144, F1146, F1148, F1149, F1163, F1164, F1175 (`ObjectSerializerTest`, `FixtureWireValueTests`, `ServerConfigurationTest`), F1068, F1113, F1127, F1142, F1143, F1155, F1158, F1161 (`PetApiTest`), F1069, F1070 (`StoreApiTest`), F1075 (`OAuth2TokenManagerTest`), F1121, F1169 (`BaseApiTest`), F1133 (`DefaultApiClientTest`).

## Decisions taken in batch 9

### A 401 on an OAuth2 or OIDC call drops the token and replays once
Rule: `HttpAwareAuthenticator` gains `default void invalidateAccessToken()` (a no-op), overridden by the client-credentials, password, auth-code and OIDC authenticators, which drop the cached access token and keep the refresh token (OIDC drops through its current delegate and never triggers discovery). `BaseApi.sendReplayingOnUnauthorized` calls it on a 401 from an `HttpAwareAuthenticator`, rebuilds the auth headers and sends the request once more; a second 401 is the usual `UnauthorizedException`. The request is not replayed when its body is not null, a `String` or a `byte[]` (stream, `File` and multipart are not), or when the fresh auth headers equal the ones just sent (the implicit flow has no fresher credential). API key, basic and bearer authenticators are not `HttpAwareAuthenticator`, so they never replay.
Where: `auth/http_aware_authenticator.mustache`, `auth/oauth/*`, `base_api.mustache`. Test: `BaseApiTest` (`UnauthorizedReplay`), the four `OAuth2*AuthenticatorTest`/`OpenIdConnectAuthenticatorTest` `invalidatingTheToken...` rows.

### PKCE S256 on the authorization-code flow
Rule: `buildAuthorizationUrl` always appends `code_challenge` and `code_challenge_method=S256` after `state`, from a fresh 43-character verifier (32 `SecureRandom` bytes, unpadded base64url) held in the authenticator; `exchangeCode` sends it once as `code_verifier` and forgets it (no verifier when no URL was built first). The challenge is `BASE64URL(SHA-256(verifier))` through `MessageDigest` (JDK only). OIDC rediscovery with new endpoints builds a new delegate and hands it the pending verifier (`adoptPkceVerifierFrom`). No nonce is sent.
Where: `auth/oauth/oauth2_auth_code_authenticator.mustache`, `auth/oauth/openid_connect_authenticator.mustache`. Test: `OAuth2AuthCodeAuthenticatorTest` (RFC 7636 appendix B vector, verifier shape, challenge proved by the exchange, single use), `OpenIdConnectAuthenticatorTest` (`rediscoveryKeepsThePkceVerifierOfTheAuthorizationUrl`).

### Automatic retries with backoff
Rule: `TransportOptions.Builder.maxRetries(int)` (default 2, 0 disables, negative rejected) and `getMaxRetries()`. `DefaultApiClient.sendWithBackoff` wraps every send (first hop and redirect hops) and retries an `IOException` before a response (never a timeout, a `SSLHandshakeException` or an interrupt) and the statuses 408, 409, 429 and 500 to 599 except 501. The delay is `min(8 s, 0.5 s * 2^n)` reduced by up to 25% jitter, or `Retry-After` (seconds, or an HTTP date through `RFC_1123_DATE_TIME`, capped at 60 s). The wait and the retry draw from the single end-to-end deadline: when the remaining budget cannot cover the wait, the last response is returned (or the last error thrown) instead of retrying, so retrying never extends the timeout; an interrupt during the wait is the `CancellationException`. A stream body, or a multipart map holding a stream, is never retried (`isReplayableBody`). A retry is a fresh `HttpRequest` built from the first one, so it carries the same headers, authentication headers included (the transport never sees an authenticator; an expired token is replaced by the 401 replay above). The one stale-connection replay of `sendWithRetry` stays inside each attempt. README "Automatic retries" and SKILLS "Retries" replace the old "Not supported" entry.
Where: `transport_options.mustache`, `default_api_client.mustache`, `readme.mustache`, `skills.mustache`. Test: `DefaultApiClientUnitTest` (retry count, retryable statuses, `Retry-After`, budget, stream, fresh request, cancellation), `TransportOptionsTest`. Existing tests that expect one failing request now build the client with `maxRetries(0)` (`BaseApiTest.TestableApi`, `PetApiTest` typed routes, the dropped-connection rows).

### Rendered error text (already conforming, two gaps closed)
Rule: the rendered `ApiException` already capped the body at 1 KiB with `... (N more bytes)`, escaped CR, LF and control characters, and masked authorization, proxy-authorization, cookie, set-cookie and any name containing key, token or secret. Added `www-authenticate` and `proxy-authenticate` to the masked list and a `\t` escape (the same `\n`, `\r`, `\t`, `\uNNNN` forms node and rust render; java does not use `\xNN`). The fields keep the real values.
Where: `api_error.mustache`. Test: `ApiExceptionTest`.

## Decisions taken in batch 10

### Fixed (code and test)
- F1103 (D79): a CA certificate that cannot be read says `failed to read CA certificate from "<path>": <cause>`, one that holds no certificate or does not parse says `failed to parse CA certificate from "<path>": <cause>` (an empty or non-PEM file gives `no PEM blocks found or unparseable`). Where: `default_api_client.mustache`. Test: `DefaultApiClientUnitTest` (`nonexistentCaCertPathFailsFast`, `garbagePemCaCertPathThrowsIllegalArgumentException`, `emptyCaCertFileIsReportedAsNoPemBlocks`).
- F1118: `ServerConfiguration.getUrl` validates every variable, then substitutes in one pass over the template, so a value that contains `{other}` is never expanded again and an unknown placeholder stays literal. Where: `server_configuration.mustache`. Test: `ServerConfigurationTest`.
- F1088: `make lint` runs `mvn spotless:check checkstyle:check spotbugs:check`. Where: `makefile.mustache`.
- F1094: the jar manifest carries `Automatic-Module-Name` set to the invoker package (maven-jar-plugin 3.4.2). No `module-info.java`. Where: `pom.mustache`.
- F1096 (half): `jackson-jaxrs-json-provider` is gone (nothing used it). `jsr305` stays a compile dependency because the public types carry `javax.annotation.Nullable`; moving it to `provided` is a decision for the owner.
- F1055: `BetterJavaCodegen.getInjectedArgumentNames` is `options`, `server` and `requestContentType`, so a path or body parameter of that name becomes `optionsParam` and so on. Test: `BetterJavaCodegenTest.renamesAParameterNamedLikeAnInjectedArgument`.
- F893 (java half): the named examples of an optional parameter are rendered on its Options setter (`Example — <summary>: {@code <value>}`). Where: `api/options.mustache`, `BetterJavaCodegen.generateOptionsFileContent`. Test: `JavaFormattingSpec.pluralExamplesShouldBeAttributedToParameter` (required `petId` examples on the operation); the optional-parameter rendering shows in the regenerated golden.
- F1072: `BetterJavaCodegenTypeMappingTest` is now `BetterJavaCodegenTest` (mirrors its source) and covers the generator identity, naming conventions, collection templates, discriminator flags and the injected names.

### Tests added for behaviour that already conformed
F901 (`proxyPasswordKeepsALiteralPlusSign`), F926 (`unusableTokenResponseHasTheCanonicalMessageAndNoCause`, five rows), F927 (`redirectThatDowngradesHttpsToHttpRefusesToReplayTheBody`, driven by a scripted `HttpClient`, so no TLS fixture), F1065 (IPv6 proxy rows in `TransportOptionsTest`), F1073 and F1074 (`BearerAuthenticatorTest`), F1119 (`ReadmeTest.socksNoteNamesTheJavaProxySetter`), F1123 (`ClientTest.customTransportWithoutCloseCanBeClosedTwice`), F1135 (`identityEncodedBodyPassesThroughUndecodedAndKeepsItsHeaders`), F1139 (`ServerConfigurationTest`, map and enum list copies), F1129 (`ComposedSchemaTest.TreatmentRecordTests`), F1161 (`StoreApiTest` typed status routes, 400 to 502), F1170 (`BaseApiTest` `sendsNoBodyWhenNull` now asserts a null body and no `Content-Type` or `Content-Length`), F1107 (the four remaining snake_case test names renamed).

### Already conforming
F215 (`ObjectSerializerTest` `requiredNullableKeepsExplicitNull`, `requiredNullableRejectsMissingKey`), F855 and F858 (batch 9), F1124 (the `o'neil` server, `ZZZZZZZZZZ` nickname and the slug rows are in the java tests), F1080 (batch 9).

### Not done in java, with the reason
- Binary response type: the stream-versus-bytes question for all twelve is settled together with streaming in #114.
- A vulnerability and unused-dependency audit is #105.
- F1140: java reads each redirect body whole (`BodyHandlers.ofByteArray`), so a hop holds no connection to release and there is nothing to count.

## Gate lessons (what broke the gate and how to avoid it)

Generated Java is verified only by the Docker specs (`JavaBuildSpec`, `JavaClientSpec`, `JavaStaticAnalysisSpec`, `JavaFormattingSpec`, plus the lint and type specs). `JavaClientSpec` alone proves little. Run the whole set with `etc/ci-local.sh check Java` before any push; a compile error hides later Checkstyle and SpotBugs findings, so re-run everything after fixing one.
- Keep the generator class at Java 11 syntax: no `record`, no pattern-matching `instanceof`, no switch expressions, no `Stream.toList()`. Example: `AbstractBetterCodegen` failed with "switch expressions are not supported in -source 11". Catch it with `etc/ci-local.sh mvn -o compile` after every codegen edit.
- Pattern matching is the opposite inside generated Java: Error Prone `PatternMatchingInstanceof` fires on `getClass()`/cast equals, and `EqualsGetClass` fires on the other form. Use `instanceof X other` directly in `model.mustache`. Catch it with `JavaBuildSpec`.
- Generated code and tests run with `-Werror` plus Error Prone, so any warning is a build error. Examples: `CanonicalDuration` (`Duration.ofMinutes(2)`, not `ofSeconds(120)`), `GetClassOnEnum`, `EmptyCatch`, `DuplicateBranches`, `OperatorPrecedence` (parenthesise mixed operators), `BooleanLiteral`, an unchecked `<T> T deserialize(...)` assigned to a concrete type. Catch with `JavaBuildSpec`; re-run without `-q`, which hides the warning text, and grep `WARNING.*\.java`.
- NullAway covers generated tests: never pass `null` to a `@NonNull` parameter, never dereference `getMessage()` or `getActualInstance()` (use `assertThat(ex).hasMessageContaining(...)`), and never narrow a `@Nullable` parameter in an override or mock. Example: `OAuth2TokenManagerTest` mock `sendRequest(@Nullable Object body)`. Catch with `JavaStaticAnalysisSpec`.
- NullAway also covers the generator: wrap a nullable value in `Objects.toString(x, "")` before it reaches a non-null helper. Example: `spec.keyParamName()` broke 8 codegens at once. Catch with `etc/ci-local.sh mvn -o test-compile` before launching language gates.
- SpotBugs gates the generator and the generated code: `IMPROPER_UNICODE` (`ContentEncodingRule`), `MODIFICATION_AFTER_VALIDATION` (compute the replaced value before the `matcher`), `NP_NULL_PARAM_DEREF`, `DE_MIGHT_IGNORE`, and `EI_EXPOSE_REP` on models and options with mutable collections (keep their `@SuppressFBWarnings`). Fix the hit in the generator, never call it pre-existing. Catch with `mvn verify` (not `compile`) and `mvn spotbugs:check`.
- Checkstyle (google_checks) rejects method names whose second letter is uppercase: `X-Swatch` becomes `xSwatch`, `X-Report-Date` becomes `xReportDate`. Name fixture parameters so the camelCase form has a lowercase second letter. Suppress `AbbreviationAsWordInName` for spec-derived acronyms (`OpenAPIException`, `BetaOIDCService`). Never use `\uXXXX` escapes (`AvoidEscapedUnicodeCharacters`); write the character or a named constant. Keep local variable names lowerCamel, static factories out of the middle of a constructor group, and a javadoc glued to its method.
- `LineLength` cannot be fixed in generated Javadoc because google-java-format does not wrap spec prose; it is disabled in the checkstyle config. Lint a client regenerated from a real spec, since the petstore golden has short descriptions.
- Never use `//` comments in Java templates (`FormattingSpec` rejects `^\s*//($|[^/])`); use a `{{! }}` Mustache comment or a block comment. A `{{! }}` comment ends at the first `}}`, so never put a tag inside it. Catch with `grep -rnE '^\s*//([^/]|$)' src/main/resources/templates/java`, then regenerate.
- Never subclass what a template made `final` and never reorder constructor parameters without fixing every `super(...)` call. Example: `PetStoreBasicAuthenticator extends BasicAuthenticator`. Catch with `grep -rn "extends <Class>"` before adding `final`, then compile.
- Imports in generated tests are checked only by compiling (`java.util.Map`, `OffsetDateTime` in the Options class); a type that moves into the same package leaves an unused import that `-Werror` rejects. Catch with `mvn -q compile` on the regenerated client right after a move.
- Never format numbers with the default locale; use `Locale.ROOT` (`HeaderSelector` produced `q=0,9`). Catch with the unit test under `-Duser.language=de`.
- Keep the pins: Error Prone 2.41 (2.49 needs `--add-exports` and breaks the forked javac), the pinned google-java-format, `<release>` 21 for `HttpClient.close()`. Do not swap `jsr305` for `org.jspecify` or add `module-info.java` without a full spec pass.
- Keep `mvn dependency:analyze` in the local gate: the generated pom must list exactly what shipped files import (CI "Lint Dependencies" job). Surefire reports live in `target/reports`; a unit failure skips the failsafe specs, so run unit tests first.
- Use neutral names in templates (`FixtureVocabularyLeakTest`, `mvn -q test -Dtest=FixtureVocabularyLeakTest`) and build test password and PKCE literals at runtime (GitGuardian). Regenerate and commit all 12 goldens with the template change; a stale golden passes locally and fails in CI.

## Decisions taken in wave 2

Owner decisions of 2026-11-17 and the triaged parity findings for java. Test names are generated-test names (templates under `templates/java/test/`) unless a generator test class is named.

### Error context on every API error
Rule: `ApiException` and every subclass carry `getRequestMethod()` and `getRequestPath()` (the path without scheme, host, query string or fragment). `BaseApi` sets them through `ApiException.fromResponse(method, path, status, headers, body)`; the older three-argument `fromResponse` leaves them `null`. `toString()` adds a `Request: GET /items/7` line after the status line when both are set; `getMessage()` is unchanged (`API returned status code N`), as the class comment already requires. The OpenID Connect discovery failure sets them too.
Where: `api_error.mustache`, `base_api.mustache`, `auth/oauth/openid_connect_authenticator.mustache`. Test: `ApiExceptionTest` (`fromResponseRecordsTheRequestMethodAndPathWithoutTheQueryString`, `everyStatusExceptionCarriesTheRequestContext`, `anAbsoluteRequestUrlIsReducedToItsPath`, `anErrorWithoutARequestRendersNoRequestLine`), `OpenIdConnectAuthenticatorTest` (`aFailedDiscoveryNamesTheRequestInTheError`). The canonical line wording is `Request: <METHOD> <path>`; the integrator reconciles it with the other eleven.

### `forUnusableResponse` is gone
Rule: the public factory is removed; `DefaultApiClient` builds the error with the public six-argument constructor. The F1177 and F1178 allow rows are dropped.
Test: the existing `ApiExceptionTest` constructor rows and `DefaultApiClientUnitTest` (`zlibWrappedDeflateBodyIsDecompressed`).

### Per-call options
Rule: every per-operation Options object carries `timeout(Integer millis)` (positive), `maxRetries(Integer)` (not negative) and `headers(Map)`, each with a getter. `timeout` and `maxRetries` replace the transport's value for that call (`TransportOptions` stays the default); `headers` are merged after the default headers and before the authentication headers. `ApiClient` gains two default methods, `sendRequest(..., Integer timeoutMillis, Integer maxRetries)` and `sendRequest(..., boolean noRedirect, boolean noDefaultHeaders)`; a transport that ignores them keeps working. `BaseApi` calls the four-argument method when no override is set. A parameter named `timeout`, `maxRetries` or `headers` becomes `timeoutParam` and so on (`getInjectedArgumentNames`; `auth` left the list when the per-call authenticator did).
Limit: superseded by wave 3: `mintsOptionsForEveryOperation()` is true, so an operation with no query, header, form or cookie parameter and no security has an Options object too (see "Every operation has an Options object" below).
Where: `api/options.mustache`, `api/api.mustache`, `base_api.mustache`, `api_client.mustache`, `default_api_client.mustache`, `BetterJavaCodegen`. Test: `BaseApiTest` (`PerCallOptions`), `DefaultApiClientUnitTest` (`aPerCallRetryCountReplacesTheTransportsForThatCallOnly`, `aPerCallRetryCountCanRaiseWhatTheTransportSet`, `aPerCallTimeoutAppliesWhenTheTransportHasNone`), `BetterJavaCodegenTest` (`renamesAParameterNamedLikeAPerCallSetting`).

### OIDC discovery keeps the default headers off the identity provider
Rule: discovery calls `sendRequest(..., false, true)`, a request that follows redirects but carries none of the transport's default headers. The token POST already skipped them through `noRedirect`.
Test: `DefaultApiClientUnitTest` (`aRequestWithoutDefaultHeadersSendsOnlyItsOwn`), `OpenIdConnectAuthenticatorTest` (`discoveryKeepsTheTransportsDefaultHeadersOffTheIdentityProvider`).

### Models are immutable with `toBuilder` (D68)
Rule: a model's fields are `public final`; `Model.builder()` and `model.toBuilder()` build and copy; `build()` (and the required-argument constructor) check the required properties, so a missing one fails with `<name> is required` whether it comes from code or from JSON. Jackson decodes through the nested `Builder` (`@JsonDeserialize(builder)`, `@JsonPOJOBuilder(withPrefix = "")`, auto-detection off on the builder so only annotated methods are properties). A required nullable property needs an explicit set (`<name>IsSet`), which keeps the D66 rule. A collection is copied into an unmodifiable one when the instance is built (`copyKind` and `copyImpl` come from `postProcessModelProperty`); a `byte[]` is cloned. A defaulted property keeps its default in the builder. A model with additional properties writes them through `putAdditionalProperty` (the any-setter), `putAllAdditionalProperties`, `removeAdditionalProperty` and `additionalProperties(map)`, reads them through the unmodifiable `getAdditionalProperties()`, and has no public `setAdditionalProperty`.
Where: `models/model.mustache`, `BetterJavaCodegen.postProcessModelProperty`. Test: `MetadataTest` (additional-property rows, `testModelFieldsAreFinal`, `testToBuilderCopiesWithOnePropertyChanged`, `testCollectionPropertyIsCopiedAndUnmodifiable`, `testBuilderRejectsAMissingRequiredProperty`, `testBuilderMethodsAreNotJsonProperties`), `BetterJavaCodegenTest` (`recordsHowACollectionPropertyIsCopiedIntoTheImmutableModel`).

### Typed union factories and accessors
Rule: a `oneOf` or `anyOf` wrapper gains `ofVariant(v)` per variant and, for a variant that is a bare class name, `isVariant()` and `asVariant()` (an `Optional`); a generic or array variant has only the factory because `instanceof` is not legal for it. The variant name is the Java type read as words (`ListOfString`, `ByteArray`). The `(Object)` constructor stays, so nothing breaks; java does not reject a non-variant in the constructor.
Where: `models/model.mustache`, `BetterJavaCodegen.nameUnionVariants`. Test: `ComposedSchemaTest` (`TypedVariantTests`), `BetterJavaCodegenTest` (`namesTheVariantsOfAUnionForTheTypedFactories`).

### Several 2xx responses decode by the status that arrived
Rule: for an operation with `hasMultiple2xx` the generated method passes `BaseApi` a decoder that decodes the body as the schema of the actual status (a `2XX` range last) and wraps it with the union's `(Object)` constructor; the other statuses and the empty body are unchanged. A body that does not match the schema of its status is a `SerializationException`, never a fall-through to another variant.
Where: `api/api.mustache`, `base_api.mustache` (`decodeVariant`). Test: `StoreApiTest` (`testReserveStockDecodesTheBodyAsTheSchemaOfTheStatusThatArrived`, `testReserveStockRejectsABodyThatDoesNotMatchTheSchemaOfItsStatus`).

### Optional body with no other argument
Rule: an operation whose only argument is an optional request body also has `op()` and `opWithHttpInfo()` (`hasOptionalBodyOnly`, computed after the shared pass because that is what removes inherited global security).
Test: `StoreApiTest` (`testPlaceOrderWithoutAnArgumentSendsNoBody`), `BetterJavaCodegenTest` (`onlyAnOperationWithNothingButAnOptionalBodyGetsTheNoArgumentOverload`).

### Non-finite decode and strict containers
Rule: a `float` or `double` literal that overflows to infinity (`1e400`, `1e40` for a float) fails the decode with `Number is out of range for Double`; the stock deserializer still does everything else, so every coercion rule stays. F006: the mapper's strict coercion (`ALLOW_COERCION_OF_SCALARS` off, `ACCEPT_FLOAT_AS_INT` off) already applies to each element of a `Map` or `List`, so no extra walk is needed; the new rows pin a fractional, a quoted and an out-of-range map value.
Where: `object_serializer.mustache` (`FiniteNumberDeserializer`). Test: `ObjectSerializerTest` (`overflowingNumberLiteralIsRejected`, `wrongTypedMapValueThrows`, the matrix row).

### Package metadata and the sources jar
Rule: the options `repositoryUrl`, `homepageUrl` (defaults to `repositoryUrl`), `authors` (comma separated, one pom `developer` each), `documentationUrl` and `issueTrackerUrl` are wired into `pom.xml` (`url`, `scm`, `developers`, `issueManagement`) and the README Package list; `licenseId` already existed. The pom builds a sources jar (`maven-source-plugin` `jar-no-fork`). Nothing is rendered for an option that is not given.
Where: `BetterJavaCodegen` (`cliOptions`, `processOpts`), `pom.mustache`, `readme.mustache`. Test: `BetterJavaCodegenTest` (`homepageDefaultsToTheRepositoryAndAuthorsBecomeAList`), the generated pom in the golden.

### Named examples reach the Options setter
Rule: batch 10 rendered the examples in `options.mustache` but the shared pass captures them only after the Options files are written, so the golden showed none. `generateOptionsFileContent` now captures them first (`capturePluralExamplesForParameters`), so `FindPetsByStatusOptions.status` carries `Example — Available only: {@code available}`.
Where: `BetterJavaCodegen`. Test: the generated Options javadoc in the golden (`JavaFormattingSpec.pluralExamplesShouldBeAttributedToParameter` covers the required ones).

### README notes
Rule: the README Caveats say that optional and nullable are one `null` (except required plus nullable), that an unknown enum or discriminator value fails, that repeated response headers are joined with `, `, which compression is negotiated, that the SDK has no logging hook, that streaming is not supported, that `Configuration` and `TransportOptions` stay separate, and what the version means; a Usage section covers per-call options, error context, immutable models and unions.
Where: `readme.mustache`, `skills.mustache`.

### Recorded, not implemented in wave 2
- F862 (credential environment variables and token provider): landed in wave 3 (see below).
- F1087 (runtime floor): java stays at 21. The floor is forced by `HttpClient.close()` (JDK 21), which `DefaultApiClient.close()` and the injected-transport rule depend on; the code otherwise needs 17 (sealed interfaces, records, pattern matching). 21 is a current LTS, so lowering it to 17 would mean giving up `close()` on the built-in client.
- F1060 and F1061 (warning for dropped OAuth2 flows and unnamed HTTP schemes): the warning is emitted by `AbstractBetterCodegen.buildOAuthSpec` and the security mapping, shared code.
- F866 ($ref sibling default): landed in wave 3 (see below).
- F1157 (`petstore_auth` in the petstore spec): the spec is shared by all twelve.
- F889 (compression, logging, versioning): done for java in the README Caveats above.

## Decisions taken in wave 3

The shared generator now hands every language the data for F827, F862 and F866; java adopts it in its own templates only.

### Every operation has an Options object
Rule: `BetterJavaCodegen.mintsOptionsForEveryOperation()` returns true, so each operation takes a trailing `<Operation>Options` (nullable, with an overload that omits it) carrying `timeout`, `maxRetries` and `headers`, even with no query, header, form or cookie parameter and no security. Such an Options object has a public no-argument constructor; no Options object has an `auth` field any more (see "Decisions taken in round 4"). The operation javadoc documents `@param options` for it (`vendorExtensions.op.optionsMinted`).
Where: `BetterJavaCodegen`, `api/api.mustache`, `api/operation_doc*.mustache`. Test: `BaseApiTest` (`PerCallOptions`: `anOperationWithNothingElseTakesThePerCallSettings`, `anOperationWithNothingElseWorksWithoutOptions`, `anInvalidTimeoutOrRetryCountIsRefused`), `BetterJavaCodegenTest` (`everyOperationGetsAnOptionsObject`).

### Credentials default from the environment
Rule: the generated per-scheme authenticator reads each credential it is given as `null` or empty from its `{PREFIX}_{FIELD}` variable (`securitySchemeEnvVars`, computed once for all twelve languages): the caller's value wins, then the variable, read once when the authenticator is built from `System.getenv()`; an empty variable counts as unset; a credential found nowhere throws `IllegalArgumentException` that names the scheme, the field and the variable. The package-private constructor `(…, Map<String, String> environment)` is the seam tests use, because a JVM cannot change its own environment; the public constructors pass `System.getenv()`. The general `BasicAuthenticator`, `BearerAuthenticator` and `ApiKeyAuthenticator` never read the environment. `BearerAuthenticator` gains `(host, tokenProvider)` and `(host, token, tokenProvider)` where the `Supplier<String>` runs before every request, each result validated like a fixed token; exactly one of token and provider is allowed. The per-scheme wrappers do not take the callback: a rotating token uses `BearerAuthenticator` directly, so the wrapper surface stays the majority `(host, token)`.
Where: `auth/scheme_authenticator.mustache`, `auth/bearer_authenticator.mustache`, `BetterJavaCodegen.renderSchemeAuthenticator`. Test: `SchemeAuthenticatorTest` (the wrappers of the Basic, Bearer and API key schemes: precedence, empty value, missing), `OAuth2ClientCredentialsAuthenticatorTest` (precedence, empty string, missing), `BetterJavaCodegenTest` (the three `...Wrapper...` rows).

### A `$ref` with a sibling default starts at the default
Rule: a property that is a `$ref` to a string enum with a `default` written next to it is initialised in the builder to `Enum.MEMBER` (`vendorExtensions.siblingDefaultIsEnum`, `siblingDefaultEnumType`, `siblingDefaultEnumVar`), so both the required-argument constructor and a decode that omits the key carry the default. A sibling default on a `$ref` to something other than an enum is still ignored.
Where: `models/model.mustache`. Test: `ObjectSerializerTest` (`refPropertyWithSiblingDefaultStartsAtTheDefault`).

### Error constructors take the request (surface)
Rule: `ApiException(statusCode, message, headers, body, errorBody, cause, requestMethod, requestPath)` is the full constructor (the path loses its query string there), the request fields are `final`, and each status exception has the matching `(message, headers, body, errorBody, cause, requestMethod, requestPath)` constructor, as the other eleven take them.
Where: `api_error.mustache`, `errors/*Exception.mustache`. Test: `ApiExceptionTest` (`everyExceptionConstructorTakesTheRequestMethodAndPath`).

## Decisions taken in round 4

Owner decisions of 2026-11-17 that the shared generator now backs with data (`clientSchemes`, `hasClientSchemes` on the additional properties; `securitySchemeOrder`, `hasSecuritySchemes` and `securityNone` on each operation's `vendorExtensions.op`). java adopts them in its own templates only.

### The client takes one optional credential per security scheme
Rule: `Client.builder(host)` returns a `Client.Builder` with one setter per distinct security scheme, named after the scheme in camel case (`petStoreBasic`, `apiKeyHeader`; a name that is a Java reserved word or one of the builder's own methods gets `Credential` appended, `BetterJavaCodegen.javaClientSchemes`), each typed as that scheme's generated authenticator (OAuth 2 and OpenID Connect ones live in `auth.oauth`), plus `transportOptions(...)` and `build()`. A `null` argument leaves the scheme unset. `new Client(authenticator[, transportOptions])` stays for one credential that applies to every operation. Each operation resolves its credential with `BaseApi.resolveSchemeAuth("a", "b")`, passing the schemes of its own security list in the order the spec lists them: the first one the client holds wins, and `null` falls back to the client-level authenticator and then to no credential. `security: []` passes the `NO_AUTH` sentinel. The API classes take the per-scheme map as an optional fourth constructor argument (`(apiClient, config, authenticator, schemeAuthenticators)`). An OAuth 2 or OpenID Connect scheme credential is handed the shared `ApiClient` like the single authenticator. There is no per-call scheme override: no Options object has an `auth` field, `injectAuthFieldContext` is no longer called, `auth` is no longer an injected argument name, and the `Internal` method split that existed only to read `options.auth()` is gone. Every per-scheme wrapper also gains a `(String host)` constructor that reads every credential from its environment variable, which is how java expresses the canonical "all credentials optional" shape.
Where: `BetterJavaCodegen` (`processOpenAPI`, `javaClientSchemes`, `generateOptionsFileContent`, `getInjectedArgumentNames`), `client.mustache`, `base_api.mustache`, `api/api.mustache`, `api/options.mustache`, `api/operation_doc*.mustache`, `auth/scheme_authenticator.mustache`, `readme.mustache`, `skills.mustache`. Idiom differences: the extra fourth API-class constructor argument, the missing `*options.auth`, and the `PetFood` and `ReserveStockResponse` member differences (S4).
Test: golden `PetApiTest` (`testAddPetUsesTheCredentialOfTheSchemeTheSpecRequires`, `testAddPetUsesTheFirstDeclaredSchemeTheClientHoldsACredentialFor`, `testAddPetIgnoresACredentialHeldForAnotherScheme`, `testAddPetFallsBackToTheClientLevelAuthenticatorWhenNoSchemeMatches`, `testTheClientRoutesEachSchemeCredentialToTheOperationThatRequiresIt`, `testAClientWithNoCredentialSendsNoAuthenticationHeader`, `testNoOperationOptionsOffersAPerCallAuthenticator`), `ClientTest` (`builderTakesOneSetterPerSecuritySchemeNamedAfterIt`), `BetterJavaCodegenTest` (`clientSchemesCarryTheAuthenticatorImportAndTheBuilderSetterName`, `everyWrapperCanBeBuiltFromTheHostAlone`, `aParameterNamedAuthKeepsItsNameBecauseNoOptionsObjectHasAnAuthSetting`).

### Cookie parameters are sent raw or refused
Rule: a cookie parameter is sent exactly as given when every character is a legal RFC 6265 cookie-octet (`%` is one, so a value the caller percent-encoded stays as written); any other value (space, quote, comma, semicolon, backslash, control or non-ASCII character) throws `IllegalArgumentException` before the request, with the message `Cookie value for '<name>' contains characters outside the RFC 6265 cookie-octet set; percent-encode the value before passing it`. The SDK never encodes a cookie value (a `content:` cookie is the exception: the SDK serialises it and encodes it itself, batch 5). The check already existed (`BaseApi.isValidCookieValue`); round 4 added the message hint and the tests.
Where: `api/api.mustache`. Test: golden `PetApiTest` (`deletePetRefusesACookieValueWithAnIllegalCookieOctet`, `deletePetSendsALegalCookieValueRaw`).

### Closing is graceful and idempotent
Rule: `DefaultApiClient.close()` marks the client closed and returns at once. A request is counted in (`beginRequest`) before it starts and out (`endRequest`) when it ends, both under one lock; the SDK-created `HttpClient` is released when the last in-flight request completes (immediately when none is), exactly once. A request after closing throws `IllegalStateException("ApiClient has been closed and can no longer send requests")`; a second `close()` does nothing; a caller-supplied `HttpClient` is never closed. `HttpClient.close()` on the JDK blocks until its own requests finish, so releasing only after the last one means `close()` itself never waits. `isTransportReleased()` is package-private, for tests.
Where: `default_api_client.mustache`, `client.mustache`. Test: `DefaultApiClientUnitTest` (`closingTheClientLetsARunningRequestFinish`, `closingTwiceIsANoOpAndReleasesTheTransportOnce`, `closingNeverReleasesACallerSuppliedTransport`), `ClientTest` (`closedClientRejectsFurtherRequests` pins the message, `closingTheClientLeavesASuppliedHttpClientOpenWhenBuiltFromTheBuilder`).

### Models are not validated when built
Rule: java is statically typed, so a model's builder and constructors cannot be handed a wrong type or an enum string that is not a constant, and they check no schema constraint (S3). Nothing in them raises on a value. A payload is validated only when decoded: a wrong-typed value, an unknown enum string and a missing or `null` required property fail with `SerializationException`. A model built in code without a required property still fails in `build()` (the settled D68 and  rule, `<name> is required`); the owner decision does not name it and java has not changed it, because turning the check off needs the required fields to hold `null` under NullAway, which the null checker forbids without relaxing it. Raise it with the owner before changing it.
Test: `ObjectSerializerTest` (`constructionNeverValidatesTheValuesItIsGiven`, `constructionAcceptsEveryDeclaredEnumConstant`, `decodingAWrongTypedValueFailsLoudly`, `unknownEnumStringIsRefusedByTheEnumDecodeFactory`, beside the existing `unknownEnumValueThrowsSerializationException` and `missingRequiredFieldThrowsSerializationException`).

### Not adopted in round 4
F1085 (generated TLS test material) is shared and out of scope for java.
