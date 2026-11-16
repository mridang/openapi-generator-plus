# Java SDK generator

The java generator (`java-plus`) emits a Maven project for Java 21 or newer (the pom compiles with `<release>21</release>`; the spec container is `maven:3.9-eclipse-temurin-25-alpine`, and the pom's OpenRewrite recipe is `UpgradeToJava25`). The HTTP stack is the JDK's own `java.net.http.HttpClient` (`DefaultApiClient`), with Jackson 2.19 (`jackson-databind`, `jsr310`, `jackson-databind-nullable`) for JSON, optional `dec` (brotli) and `zstd-jni` codecs for response decoding, and an optional OpenTelemetry API for trace context. Tests run under JUnit 6 with AssertJ, JUnit parallelism enabled through `junit-platform.properties`, and Testcontainers for the chasm and squid fixtures. Compilation runs with `-Xlint:all`, `failOnWarning`, Error Prone and NullAway at error level; the pom also wires Spotless (google-java-format 1.28.0), Checkstyle (the bundled `google_checks.xml`), SpotBugs, JaCoCo, javadoc with `failOnWarnings` and OpenRewrite. The codegen class javadoc still says "Apache HttpClient"; that is stale, nothing in the templates imports it.

## Read first

- [Shared generator rules](../AGENTS.md), where every cross-language decision is written once.
- [Repository root rules](../../../../../../../../AGENTS.md).
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
| Lifecycle | `AutoCloseable.close()` | try-with-resources |
| Optional arguments | `@Nullable` parameters plus overloads; the caller passes `null` | java has no default arguments (allow rule `optional`) |
| Per-operation optionals | a generated `<Operation>Options` class passed last | no named or optional arguments |
| `ServerVariable` | its own file `ServerVariable.java` | one public top-level class per `.java` file |
| Union wrappers | no `actualInstance` member: the decoded value is itself the variant through a polymorphic base class | Jackson polymorphic deserialization (allow rule `*.actualinstance`) |
| `ObjectSerializer`, `ValueSerializer`, `HeaderSelector` | package-private | hides Jackson from the public surface (allow rule `type`) |
| `parseJson` | no separate helper; Jackson parses and applies the depth limit | standard library parser (allow rule `objectserializer.parsejson`) |
| Cause chaining | no `cause` member on `SerializationException`; `OAuth2TokenException` takes an optional cause in its constructors | `Throwable.getCause` already exists |
| Auth-header accessor | one synchronous `getAuthHeaders` | the HTTP client is synchronous |
| Per-scheme authenticator wrappers | composed (hold a base authenticator), not subclasses; `instanceof` the base is false (F400) | the three base authenticators are `final` so SpotBugs `CT_CONSTRUCTOR_THROW` stays quiet |
| Model named `Error` | generated as `ModelError` | would shadow `java.lang.Error` (Error Prone `JavaLangClash`); allow rule `error` |
| Reserved words | `true`, `false`, `null` and `_` are escaped too | they are illegal identifiers though not keywords (D44) |
| `format: number`, `decimal` | `BigDecimal` | precision-preserving, like kotlin and csharp |
| `format: binary` | `InputStream` (request and response) | stream-typed; changing to `byte[]` is a public API change (F332, parked) |
| Doc comments | `///` markdown doc-comments allowed; `//` line comments rejected in `src/main` | Java 23+ markdown doc-comments and `JavaFormattingSpec` |

## Settled decisions (do not re-open in review)

The 2026-11-16 session decisions come first. The rules are written once in the shared file; each entry here says how java realises it. Where the ledger item is still open, java has not landed the change yet and the entry says so.

### Injected transport is `java.net.http.HttpClient` (S9, F405, 2026-11-16)
Rule: the client builder accepts a caller-supplied `java.net.http.HttpClient`, keeps the SDK defaults applied, never closes the caller's client and fails fast when it conflicts with the SDK's own proxy, TLS or pool settings.
Why: every SDK generator compared (OpenAI, Stainless, Speakeasy, Fern, Azure, Stripe) accepts the standard client object.
Where: `client.mustache`, `default_api_client.mustache`, `transport_options.mustache` (`TransportOptions.builder().httpClient(...)`). Landed.
Test: `ClientTest`, `DefaultApiClientUnitTest` (add rows there).

### Cancellation is the platform's interrupt (S7, F228, 2026-11-16)
Rule: java has no new parameter; a thread interrupt during a request surfaces as `CancellationException` and is never rewrapped as `NetworkTimeoutException`.
Why: the interrupt is what Java callers already use; a shared `RequestOptions` wrapper is less natural than the native handle.
Where: `default_api_client.mustache` (the `InterruptedException` branch builds the `CancellationException`).
Test: `DefaultApiClientUnitTest`.

### Parameter order is fixed (S8, F803, 2026-11-16)
Rule: required parameters, then the server, then the `*Options` object, always, whether or not the operation has security; adding security never moves an argument.
Why: a positional options parameter inserted before the server silently broke existing server-only call sites.
Where: `api/api.mustache`, `BetterJavaCodegen.populateOperationDecorators` (`serverBeforeOptions`, `callArgsNullServer`). Landed.
Test: `test/api/*ApiTest.mustache` (golden `PetApiTest`).

### allOf with a discriminator uses class inheritance, once (S4, F057, F802, 2026-11-16)
Rule: only the allOf-plus-discriminator parent is a superclass (`extends {{parent}}` in `model.mustache`); a model reused in two unions is not re-parented, unions use the polymorphic base and `@JsonTypeInfo`/`@JsonSubTypes`.
Why: a Java class has one parent. F802 (the `DeprecatedFood` union re-parents `DryFood` and `WetFood`) is the bug this closes.
Where: `models/model.mustache`. Not landed: F802 needs a shared-Java fix (`AbstractBetterCodegen.setParentOnChild` keeps the first union that claims a child).
Test: `test/ComposedSchemaTest.mustache`.

### Required plus nullable keeps the key (S5, F215, D66, 2026-11-16)
Rule: a required nullable property must be present on decode (missing is an error), an explicit null is a legal value and is kept, and encode always writes the key, null when unset.
Why: OpenAPI separates required from nullable; java used to accept a missing key. D66 puts `@JsonInclude(ALWAYS)` and `required = true` on the creator parameter without `requireNonNull`.
Where: `models/model.mustache`, `object_serializer.mustache`. F215 is open for the decode side.
Test: golden model tests and `ObjectSerializerTest`.

### Strict containers (S1, F006, 2026-11-16)
Rule: a response that is a container of primitives (`Map<String,Integer>`, `List<List<Integer>>`) is checked element by element like a single primitive.
Why: container returns bypassed the strict primitive checks in seven SDKs, java among them.
Where: `object_serializer.mustache`, `base_api.mustache`. F006 is parked, not landed.
Test: `ObjectSerializerTest`, `BaseApiTest`.

### Content parameters use the SDK serializer (S2, F054, D50, D80, 2026-11-16)
Rule: a `content:` parameter is encoded through `ObjectSerializer.serialize` in every location (query, header, path, cookie), the same bytes as the other eleven.
Why: twelve different JSON encoders produced twelve byte forms. The query branch is already canonical; header, path and cookie are open (F054).
Where: `api/api.mustache`, `value_serializer.mustache`.
Test: `ValueSerializerTest`, golden `PetApiTest`.

### Schema constraints are not enforced (S3, D55, F056, 2026-11-16)
Rule: `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems` and `maxItems` are never checked client-side; the server owns validation. The README documents it in the section "Schema constraints".
Why: only python enforces them (natively through pydantic); java would need Bean Validation at the cost of a dependency and a second source of truth.
Where: `models/model.mustache`, `readme.mustache`.
Test: none by design; do not add enforcement.

### `uniqueItems` order is left unspecified (S6, F319, 2026-11-16)
Rule: a `Set` is sent in the collection's natural order (`LinkedHashSet`, insertion order); do not sort. This supersedes D69, which proposed sorting.
Why: the order carries no meaning and sorting is work with no contract behind it. The READMEs say so.
Where: `object_serializer.mustache` (no sorting module), `readme.mustache`. Landed in batch 6 (F871, F892).
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

### The error carries the real response (D13, F099)
Rule: an `ApiException` for a response that arrived but was unusable (decompress failure, redirect refusals) carries the real status and the lower-cased response header map, null body, and the failure as cause; `ApiException(int, String, Map, Throwable)` replaces the old three-argument constructor. `responseHeaders` null means no HTTP response arrived and is never defaulted to an empty map (see the shared "Forced divergences").
Why: callers branch on null headers to tell a transport failure from a bad response.
Where: `api_error.mustache`, `default_api_client.mustache`.
Test: `ApiExceptionTest`, `DefaultApiClientUnitTest`.

### Header injection is case-insensitive and the caller wins (D21, D22)
Rule: `User-Agent` and `X-Request-ID` are injected only when no `equalsIgnoreCase` match exists in the merged headers, and the caller's key is left as written. The default User-Agent product token is the spec-derived Maven `artifactId` (the invoker package's last segment plus `-client`), not the invoker package.
Why: RFC 9110 5.1; the published package name is the identifier users can look up. An explicit `artifactId` option still overrides the derived one.
Where: `default_api_client.mustache`, `transport_options.mustache`, `BetterJavaCodegen` (`artifactId`, `userAgentDefault`).
Test: `DefaultApiClientTest`, `TransportOptionsTest`.

### Models: string form, equality, any-properties (D23, D24, D63, F321, F322, F400)
Rule: `toString` dumps `ClassName{prop=value, ...}` over all vars plus the additionalProperties bucket, a top-level `byte[]` through `Arrays.toString` and containers through `ObjectSerializer.structuralToString`; oneOf/anyOf wrappers delegate equality and hash to the held variant with `Objects.deepEquals`; the `@JsonAnyGetter` omits keys that equal declared property names so the declared value wins and the key is written once; a composed scheme wrapper prints the redacted base repr under its own class name.
Why: identity hashes and type names in logs look like data; equality on a decoded union must not be identity.
Where: `models/model.mustache`, `auth/scheme_authenticator.mustache`.
Test: `ComposedSchemaTest`, `MetadataTest`, `ObjectSerializerTest`. F321 is parked: java renders nested containers of byte arrays raw in places.

### Response routing: binary, byte, text (D30, D31, D32, D34, D35)
Rule: a `format: binary` return (`InputStream`) is never JSON-parsed. A `format: byte` return with an empty or JSON content type is JSON-parsed and decoded with the strict base64 decoder; every failure is a `SerializationException`. A non-JSON success type on a non-binary return gives a `String` return the text only for text-class types and otherwise throws a `SerializationException` naming the content type. A binary request body is sent verbatim under any selected content type through `rawBody`. An empty Content-Type is text-class in both transport and the `BaseApi` mirror.
Why: the transport hands text-class media types over as decoded text; every other outcome used to be an unchecked cast.
Where: `base_api.mustache`, `api/api.mustache`, `default_api_client.mustache`.
Test: `BaseApiTest`, `DefaultApiClientTest`, `ApiResultTest`.

### Multipart and form encoding (D36, D39, D53, D90, F253)
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
Rule: a required string parameter in any location must be non-empty; the message is `Missing the required parameter '<name>' when calling <Class>.<operation>`, naming the caller-facing identifier (`{{paramName}}`, so `X-Correlation-Id` reads `xCorrelationId`); a multi-consumes selector rejects an unknown content type with `IllegalArgumentException`; a deprecated operation carries one `@Deprecated` before the `{{#hasAuthMethods}}` split, which covers every `WithHttpInfo` branch.
Why: a second `@Deprecated` per branch is a compile error ("not a repeatable annotation interface"); a uniform message is the error contract.
Where: `api/api.mustache`, `default_api_client.mustache`. Do not add a per-branch guard.
Test: golden `PetApiTest`, `StoreApiTest`.

### Codegen escaping and names (D40, D41, D42, D43, D44, D46, F309)
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

## Known limitations and out of scope

Callbacks, webhooks, XML bodies, Link Objects and mutual TLS are out of scope (#53, #70, #78, #88, #90). OpenAPI 3.1 and 3.2 are tracked as epics #96 and #49; the pinned swagger-parser rejects 3.2 documents (#50).

These java gaps are known and not hidden by the allow list:

- An optional request body cannot be omitted: `placeOrder(@Nullable Order)` takes `null`, because the no-argument overload needs every data, `WithHttpInfo`, server and content-type variant and risks `null` ambiguity (D26, F324, partial, too big).
- A model can be built without its required properties because the public no-argument constructor is still emitted (D68, F227, deferred); removing it needs a model API redesign.
- `refreshUrl` keeps its position in the public OAuth2 constructors (F287); moving it breaks positional callers.
- `format: binary` stays `InputStream` rather than `byte[]` (F332).
- Only a model named `Error` is prefixed (`ModelError`); other collisions with `java.lang` or SDK type names are unguarded (D45, F173, open).
- Object-valued query and header parameters are unsupported (D47, F063, parked); `Set` order is unspecified (S6).
- `BigDecimal` is written with `toString`, so a value such as `1E+3` may appear in exponent notation; `WRITE_BIGDECIMAL_AS_PLAIN` is not enabled (D83 proposed it).
- The `FormattingSpec` and static-analysis specs do not gate tests as strictly as sources: SpotBugs does not run over generated tests (F193, about 140 findings), javadoc with `failOnWarnings` and the OpenRewrite recipe are in the pom but run by no spec (F191, F196).
- OAuth2 authenticator repr field sets and the per-model oneOf/anyOf no-match wording differ from other languages (F285, F396); not harmonised.

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
python3 etc/api-surface.py check
```

Language-specific: `mvn verify -Dgroups=java`; inside a generated SDK `mvn compile`, `mvn test`, `mvn spotless:check`, `mvn spotbugs:check checkstyle:check`.

### Batch 5 (F824, F831, F832, F833, F839)
Rule: the `Client` facade is `AutoCloseable` and closes the `DefaultApiClient` it built (a caller-supplied `HttpClient` is never closed; a closed client throws `IllegalStateException`). HTTP 429 maps to `TooManyRequestsException` (a `ClientException`). The "Unexpected response Content-Type" error names `Class.operationId`, passed as the first argument of `invokeApi`/`invokeApiForResult`. A `content:` cookie is encoded by `BaseApi.encodeContentCookie` (only `A-Z a-z 0-9 - . _ ~` literal, UTF-8 percent-encoded). Redirects follow RFC 9110 15.4: 301/302 rewrite only POST to GET, 303 rewrites everything but HEAD, 307/308 keep method and body.
Where: `client.mustache`, `errors/TooManyRequestsException.mustache`, `api_error.mustache`, `base_api.mustache`, `api/api.mustache`, `default_api_client.mustache`.
Test: `ClientTest`, `ApiExceptionTest`, `BaseApiTest`, `DefaultApiClientUnitTest`.

## Decisions taken in batch 6

Where the ledger needed an owner decision, java took the audit's own proposal. Each rule is scoped to java and written once.

### Token expiry uses one margin (F854, F895, F1045, F1046, F896)
Rule: `OAuth2TokenManager` stores `expiry - min(60 s, ceil(expires_in / 2))` and serves the token while `now` is before it, so a 60 s token is reused for 30 s and a 1 s or 0 s token is never reused. A grant response without `expires_in` clears the expiry (cached until `invalidateAccessToken`), so a refresh answer without it no longer keeps the lapsed expiry. A `token_type` other than `bearer` (any case) raises `OAuth2TokenException("Unsupported token_type in token response")`; an absent one is accepted (F863).
Where: `auth/oauth/oauth2_token_manager.mustache`. Test: `OAuth2TokenManagerTest` (`expiresInAppliesOneMarginCappedAtHalfTheLifetime`, `refreshResponseWithoutExpiresInKeepsNoExpiry`, `tokenTypeOtherThanBearerIsRejected`).

### OIDC discovery is checked (F859)
Rule: a discovery document whose `issuer` (when present) differs from the discovery URL minus `/.well-known/openid-configuration` (trailing slash ignored) raises `SerializationException`; `authorization_endpoint`, `token_endpoint` and the discovery URL must be https unless the host is `localhost`, `127.0.0.1` or `[::1]`. The base OAuth2 constructors do not check their URLs yet.
Where: `auth/oauth/openid_connect_authenticator.mustache`. Test: `OpenIdConnectAuthenticatorTest`.

### One end-to-end budget for the stale-connection retry (F907, D76)
Rule: `sendWithRetry` takes the deadline; the one replay after "header parser received no bytes" gets the remaining nanos (rebuilt with `HttpRequest.newBuilder(request, ...)`) and raises `NetworkTimeoutException` when the budget is spent. `QUERY` counts as idempotent (F838, partial: the shared classification is not done).
Where: `default_api_client.mustache`. Test: `DefaultApiClientUnitTest` (`staleConnectionRetryDrawsFromTheRemainingBudget`, `queryIsReplayedAsAnIdempotentMethodAndPostIsNot`).

### Redirect budget, wording and header case (F902, F904, F905, F901, D16, D29, D70, D77)
Rule: "too many redirects" fires only when the budget is exhausted and a usable `Location` is present; a bare 3xx is returned. Messages are `Too many redirects (exceeded <N>)`, `Refusing to replay request body across HTTPS->HTTP redirect: <current> -> <next>`, `Refusing to follow redirect to non-HTTP(S) URL: <next>` and `ApiClient has been closed and can no longer send requests`. `maxRedirects` below zero and `timeout <= 0` are rejected in the builder (`Invalid maxRedirects: must not be negative (got N)`, `Invalid timeout: must be a positive number of milliseconds (got N)`). A caller's `Accept-Encoding` in any case suppresses the SDK's. Proxy userinfo is percent-decoded without turning `+` into a space.
Where: `default_api_client.mustache`, `transport_options.mustache`. Test: `DefaultApiClientUnitTest`, `TransportOptionsTest`. The HTTPS-to-HTTP message is not pinned end to end (it needs a TLS fixture).

### Error and serializer messages (F922, F933, D15, D17)
Rule: `ApiException.toString` renders `Response headers: {"a":"b"}` as compact JSON with sorted keys; the serializer says `Failed to serialize object to JSON: <detail>`, `Failed to deserialize JSON: <detail>` and `JSON nesting depth <N> exceeds limit 1000`.
Where: `api_error.mustache`, `object_serializer.mustache`. Test: `ApiExceptionTest`, `ObjectSerializerTest`.

### Models (F864, F867, F869, F173, F278)
Rule: `setAdditionalProperty` is public so callers can send extra keys; the string form of an enum constant is its wire value; a `format: password` field prints as `***` in the model's `toString` (the wire still carries the value); a schema named like a `java.lang` type (`Error`, `Exception`, `Throwable`, `String`, `Object`, the boxed numbers, `Class`, `Enum`, `Record`, `Override`, `Deprecated`, `Thread`, `System`) is prefixed `Model`; apiKey header names are escaped as Java literals in the redirect strip list.
Where: `models/model.mustache`, `BetterJavaCodegen` (`JAVA_LANG_CLASHES`, `processOpenAPI`). Test: `MetadataTest`, `ObjectSerializerTest`, `BetterJavaCodegenTypeMappingTest`.

### Per-call authenticators get the client (F856)
Rule: `BaseApi` hands its `ApiClient` to a per-call `HttpAwareAuthenticator` before using it, so a per-call OAuth2 or OIDC authenticator works.
Where: `base_api.mustache`. Test: `BaseApiTest` (`perCallHttpAwareAuthenticatorReceivesTheClient`).

### README and SKILLS (F883, F884, F885, F888, F889, F890)
Rule: the README order is Requirements, Installation, Usage (quick start, raw responses, timeouts, request id, cancellation, custom HTTP client), Build, Test, Package, Caveats, Not supported. Cancellation is documented as the thread interrupt raising `CancellationException`; "Not supported" lists retries, XML bodies and `nodeType`, Link Objects and mutual TLS. SKILLS gains custom HTTP client, cancellation and raw-response sections.
Where: `readme.mustache`, `skills.mustache`.

### Already conforming or skipped in batch 6
Already conforming: F803 (server before options), F054 (content parameters in every location), F149 and F832 (`Class.operationId` in the Content-Type error), F215 (required plus nullable keeps the key), F824, F831, F833, F839, F868 (public fields and setters). Skipped, needing a shared or cross-language decision: F802, F823, F826, F827, F828, F830, F834 to F838, F840, F843, F855, F857, F858, F860, F862, F865, F866, F893, F894, F908, F929 (java has one synchronous accessor, so D12 does not apply).


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

### A 401 on an OAuth2 or OIDC call drops the token and replays once (F855)
Rule: `HttpAwareAuthenticator` gains `default void invalidateAccessToken()` (a no-op), overridden by the client-credentials, password, auth-code and OIDC authenticators, which drop the cached access token and keep the refresh token (OIDC drops through its current delegate and never triggers discovery). `BaseApi.sendReplayingOnUnauthorized` calls it on a 401 from an `HttpAwareAuthenticator`, rebuilds the auth headers and sends the request once more; a second 401 is the usual `UnauthorizedException`. The request is not replayed when its body is not null, a `String` or a `byte[]` (stream, `File` and multipart are not), or when the fresh auth headers equal the ones just sent (the implicit flow has no fresher credential). API key, basic and bearer authenticators are not `HttpAwareAuthenticator`, so they never replay.
Where: `auth/http_aware_authenticator.mustache`, `auth/oauth/*`, `base_api.mustache`. Test: `BaseApiTest` (`UnauthorizedReplay`), the four `OAuth2*AuthenticatorTest`/`OpenIdConnectAuthenticatorTest` `invalidatingTheToken...` rows.

### PKCE S256 on the authorization-code flow (F858)
Rule: `buildAuthorizationUrl` always appends `code_challenge` and `code_challenge_method=S256` after `state`, from a fresh 43-character verifier (32 `SecureRandom` bytes, unpadded base64url) held in the authenticator; `exchangeCode` sends it once as `code_verifier` and forgets it (no verifier when no URL was built first). The challenge is `BASE64URL(SHA-256(verifier))` through `MessageDigest` (JDK only). OIDC rediscovery with new endpoints builds a new delegate and hands it the pending verifier (`adoptPkceVerifierFrom`). No nonce is sent.
Where: `auth/oauth/oauth2_auth_code_authenticator.mustache`, `auth/oauth/openid_connect_authenticator.mustache`. Test: `OAuth2AuthCodeAuthenticatorTest` (RFC 7636 appendix B vector, verifier shape, challenge proved by the exchange, single use), `OpenIdConnectAuthenticatorTest` (`rediscoveryKeepsThePkceVerifierOfTheAuthorizationUrl`).

### Automatic retries with backoff (F823)
Rule: `TransportOptions.Builder.maxRetries(int)` (default 2, 0 disables, negative rejected) and `getMaxRetries()`. `DefaultApiClient.sendWithBackoff` wraps every send (first hop and redirect hops) and retries an `IOException` before a response (never a timeout, a `SSLHandshakeException` or an interrupt) and the statuses 408, 409, 429 and 500 to 599 except 501. The delay is `min(8 s, 0.5 s * 2^n)` reduced by up to 25% jitter, or `Retry-After` (seconds, or an HTTP date through `RFC_1123_DATE_TIME`, capped at 60 s). The wait and the retry draw from the single end-to-end deadline: when the remaining budget cannot cover the wait, the last response is returned (or the last error thrown) instead of retrying, so retrying never extends the timeout; an interrupt during the wait is the `CancellationException`. A stream body, or a multipart map holding a stream, is never retried (`isReplayableBody`). A retry is a fresh `HttpRequest` built from the first one, so it carries the same headers, authentication headers included (the transport never sees an authenticator; an expired token is replaced by the 401 replay above). The one stale-connection replay of `sendWithRetry` stays inside each attempt. README "Automatic retries" and SKILLS "Retries" replace the old "Not supported" entry.
Where: `transport_options.mustache`, `default_api_client.mustache`, `readme.mustache`, `skills.mustache`. Test: `DefaultApiClientUnitTest` (retry count, retryable statuses, `Retry-After`, budget, stream, fresh request, cancellation), `TransportOptionsTest`. Existing tests that expect one failing request now build the client with `maxRetries(0)` (`BaseApiTest.TestableApi`, `PetApiTest` typed routes, the dropped-connection rows).

### Rendered error text (F1080, already conforming, two gaps closed)
Rule: the rendered `ApiException` already capped the body at 1 KiB with `... (N more bytes)`, escaped CR, LF and control characters, and masked authorization, proxy-authorization, cookie, set-cookie and any name containing key, token or secret. Added `www-authenticate` and `proxy-authenticate` to the masked list and a `\t` escape (the same `\n`, `\r`, `\t`, `\uNNNN` forms node and rust render; java does not use `\xNN`). The fields keep the real values.
Where: `api_error.mustache`. Test: `ApiExceptionTest`.

### F825 skipped
Rust only.
