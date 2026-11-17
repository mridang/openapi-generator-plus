# C# SDK generator

This generator (`csharp-plus`, class `BetterCSharpCodegen`) emits a NuGet package from an OpenAPI document: a `Client` facade, `Configuration` and `TransportOptions` builders, one API class per tag with an Options record per operation, System.Text.Json models, an authenticator per security scheme, the error hierarchy, and the xunit suite, README, SKILLS.md, docfx config and analyzer config that ship with it. HTTP goes through `System.Net.Http.HttpClient` (`DefaultApiClient` on top, with manual redirects and decompression), JSON through System.Text.Json with a pinned `DefaultJsonTypeInfoResolver` and custom converters. Tests run on xunit.v3 3.2.2 over the Microsoft Testing Platform (no VSTest), with Testcontainers 4.12.0 for the container-backed fixtures. The floor is `net8.0`, `LangVersion` 12, nullable and implicit usings on, the SDK image is `mcr.microsoft.com/dotnet/sdk:10.0-alpine`, the analyzers are `Microsoft.CodeAnalysis.NetAnalyzers` 10.0.100 at `AnalysisLevel=latest-all`, `AnalysisMode=All`, warnings as errors; the formatter is CSharpier 1.2.6 and the docs tool docfx 2.78.5 (`dotnet_tools.mustache`). The codegen class javadoc says ".NET 9+"; the templates say net8.0 and the templates win.

## Read first

- Shared cross-language rules: [../AGENTS.md](../AGENTS.md)
- Repository root: [../../../../../../../../../AGENTS.md](../../../../../../../../../AGENTS.md)
- Codegen class: [BetterCSharpCodegen.java](BetterCSharpCodegen.java)
- Templates: [../../../../../../../resources/templates/csharp/](../../../../../../../resources/templates/csharp/)
- Spec classes: [../../../../../../../spec/java/io/github/mridang/codegen/spec/csharp/](../../../../../../../spec/java/io/github/mridang/codegen/spec/csharp/) (`CSharpSpec` is the shared interface; `CSharpBuildSpec` compiles, `CSharpStaticAnalysisSpec` builds SDK and tests with `--warnaserror`, `CSharpClientSpec` runs the xunit suite, `CSharpFormattingSpec` runs CSharpier and `dotnet format`, `CSharpTrimmedSpec` publishes a trimmed consumer, `CSharpReservedWordsSpec` checks the keyword list)
- Golden: [../../../../../../../spec/resources/generated/csharp/](../../../../../../../spec/resources/generated/csharp/) (never hand-edit; regenerate)
- API-surface allow rules for csharp: `etc/api-surface-allow.json` (each states its reason); canonical surface: `API_SURFACE.md`

## Layout

| Path (under `src/main/resources/templates/csharp/` unless noted) | What lives there | Edit this to change |
|---|---|---|
| `errors/*.mustache`, `api_error.mustache`, `root_exception.mustache` | Exception hierarchy, one class per file | Error classes and messages |
| `auth/*.mustache`, `auth/oauth/*.mustache` | Authenticator base, api-key, basic, bearer, OAuth2 flows, token manager, OIDC | Auth behaviour; per-scheme wrappers are rendered by `renderSchemeAuthenticator` and scopes by `formatCSharpScopes` in the codegen |
| `default_api_client.mustache`, `transport_options.mustache`, `trace_context_util.mustache` | HttpClient transport: TLS, proxy, timeouts, redirects, decompression, multipart | Transport options and wire behaviour |
| `base_api.mustache`, `object_serializer.mustache`, `value_serializer.mustache`, `header_selector.mustache` | Parameter styles, query, form and multipart encoding, JSON converters, content negotiation | Anything about bytes on the wire |
| `api/api.mustache`, `api/options.mustache` | Per-tag API classes and per-operation Options (also built by `generateOptionsFileContent`) | Operation signatures and doc comments |
| `models/model.mustache`, `integer_enum.mustache` | Models, enums, oneOf/anyOf unions | Models and unions |
| `configuration.mustache`, `servers.mustache`, `server_configuration.mustache`, `client.mustache` | Builder, servers, facade | Public entry surface |
| `csproj.mustache`, `editorconfig.mustache`, `illink_descriptors.mustache`, `dotnet_tools.mustache`, `makefile.mustache`, `docfx*.mustache` | Project file, analyzer policy, trimmer keep-list, tool manifest, make targets, docs | Dependencies, analyzer rules, build |
| `readme.mustache`, `skills.mustache` | README and SKILLS.md | Docs |
| `test/*.mustache`, `test/Api/` | The generated xunit suite (extend these, never add a new file); `test/tests_csproj.mustache` is the test project | Tests |
| `src/main/java/.../generators/csharp/BetterCSharpCodegen.java` | Type mapping, naming, enum and default literals, supporting files, formatter commands | Type mapping, escaping, file layout |
| `src/main/resources/reserved-words/csharp.txt` | Keyword list (checked against the compiler by `CSharpReservedWordsSpec`) | Reserved words |

## Idioms (deliberate differences from the other eleven)

| Topic | What this language does | Why |
|---|---|---|
| Cleanup | `IDisposable.Dispose()` on `Client` and `DefaultApiClient`, not `close()` | `using` requires the contract; allow rule in `etc/api-surface-allow.json`. See T2-11 below |
| Async | `...Async` methods returning `Task`, plus a sync `GetAuthHeaders` beside `GetAuthHeadersAsync` | TAP naming convention for value-wrapped async |
| Names | PascalCase members, camelCase parameters, `[JsonPropertyName]` keeps the wire name, keywords are not escaped in PascalCase | `Class` is a legal identifier; `_Class` would trip CA1707 |
| Exceptions | `(message, innerException)` constructor on every exception, `serializationerror.cause` is `InnerException` | CA1032 and the .NET base class carry the cause |
| Union wrappers | No `ActualInstance`: the deserialized value is the variant (polymorphic base class) | Same as java; allow rule |
| Types | `decimal` for bare `number`, `DateOnly`, `DateTimeOffset`, `TimeOnly`, `TimeSpan`, `Guid`, `Uri`, `HashSet<T>` for `uniqueItems`, `byte[]` for `format: byte`, `System.IO.Stream` for `format: binary` | BCL has each type; `decimal` keeps full precision (D83) |
| `params` | Token manager arguments are named `parameters` | `params` is a C# keyword |
| `ServerVariable` | Optional description and values come from overloaded constructors | Record parameter defaults must be compile-time constants |
| Trace context | `System.Diagnostics.DistributedContextPropagator` (W3C), not OpenTelemetry | In the BCL; no-ops when `Activity.Current` is null |
| Doc comments | XML `///`; prose with XML metacharacters is wrapped in CDATA by the `escapeXml` lambda | CS1570 is an error under warnings as errors and named entities are banned |
| Trimming | `ILLink.Descriptors.xml` embedded keep-list, parameterless model constructors with `required` init accessors | System.Text.Json reflection; proven by `CSharpTrimmedSpec` |
| OIDC endpoints | Typed as `System.Uri`, so a non-absolute endpoint is rejected | Benign extra guard; the other eleven take any non-empty string |

## Settled decisions (do not re-open in review)

Cross-language rules are written once in [../AGENTS.md](../AGENTS.md); this section states how csharp realises them. Decision ids are the D numbers of `PARITY_DECISIONS.md`, the F numbers of `PARITY_FINDINGS.json`, the T and R numbers of `PARITY_TODO.md`, and the S numbers of the 2026-11-16 session. Where a rule is decided but the csharp code does not yet match, the gap is listed under "Known limitations".

### Strict containers (S1, F006, 2026-11-16)
Rule: a container of primitives is checked element by element like a single primitive; int32 and int64 reject fractional, quoted and out-of-range values.
Why: one rule for all twelve; csharp already behaves this way (D54).
Where: `object_serializer.mustache`.
Test: `ObjectSerializerTest.mustache` (`Int32OverflowThrowsSerializationException`, `FractionalNumberForAnIntegerThrowsSerializationException`, `Int64AboveTwoToThe53IsKeptExactly`).

### Content parameters use the SDK serializer (S2, F054, D50, D80, 2026-11-16)
Rule: a `content:` parameter is encoded by the `BaseApi` ObjectSerializer instance, never by bare `JsonSerializer`, and a failure is a `SerializationException` with no request sent.
Why: identical bytes in all twelve. The query location is done (D50, `Serializer` made `private protected`); header, path and cookie are not yet (see limitations).
Where: `base_api.mustache`, `api/api.mustache`.
Test: `Test/Api/StoreApiTest.mustache` (`searchStore.q`).

### Schema constraints are not enforced (S3, F056, D55, 2026-11-16)
Rule: csharp does not enforce pattern, length, range, multipleOf or item-count keywords; the server owns validation. The README has a "Schema constraints" section saying so.
Why: only python enforces them natively and cheaply; csharp has no such model layer and DataAnnotations would be a second validation path.
Where: `models/model.mustache`, `readme.mustache`.
Test: none possible beyond the golden README.

### allOf with a discriminator uses inheritance (S4, F057, F802, 2026-11-16)
Rule: the allOf parent with a discriminator is a base class and the subtypes extend it; a model reused in two unions must not be re-parented, so unions use only the parent's inheritance and nothing else.
Why: a class has one parent. `setParentOnChild` keeping the first union that claims a child is the F802 defect (DryFood and WetFood extend `DeprecatedFood` instead of `PetFood`).
Where: `BetterCSharpCodegen.setsDiscriminatorParent`, `models/model.mustache`.
Test: `ComposedSchemaTest.mustache`.

### Required and nullable (S5, F215, D66, 2026-11-16)
Rule: the key must be present on decode (missing is an error), an explicit null is kept, encode always writes the key. Target realisation: `[JsonRequired]` plus `[JsonIgnore(Condition = JsonIgnoreCondition.Never)]`, after checking current output first.
Why: spec-faithful and the same in all twelve.
Where: `models/model.mustache`.
Test: `ObjectSerializerTest.mustache`, `MetadataTest.mustache`.

### uniqueItems keeps the natural collection order (S6, F319, 2026-11-16)
Rule: `HashSet<T>` goes on the wire in enumeration order, not sorted; the README says the order is unspecified.
Why: the D69 proposal to sort was rejected; the order of a set carries no meaning and sorting costs a converter per scalar type. Do not add a sorting converter.
Where: `BetterCSharpCodegen.getUniqueItemsSetType`, `readme.mustache`.
Test: `ValueSerializerTest.mustache` (a set expands like an array, D82).

### Cancellation by CancellationToken (S7, F228, 2026-11-16)
Rule: every operation takes a trailing `CancellationToken cancellationToken = default`, and the builder accepts a token that is linked with it. Cancellation raises `OperationCanceledException`, never `NetworkTimeoutException`; the SDK never rewraps it.
Why: the Azure SDK guideline, and the native handle callers already pass; a shared RequestOptions wrapper was rejected as less natural in every language.
Where: `api/api.mustache`, `base_api.mustache`, `default_api_client.mustache`, `client.mustache`.
Test: `DefaultApiClientUnitTest.mustache`, `PetApiTest.mustache`.

### Parameter order (S8, F803, 2026-11-16, owner confirmation pending)
Rule: required parameters, then server, then the Options argument, then the cancellation token; the same whether or not the operation has security, and adding security never moves an argument.
Why: adding a security scheme must not break existing call sites.
Where: `api/api.mustache`.
Test: `PetApiTest.mustache`, `StoreApiTest.mustache`.

### Injected HttpClient (S9, F405, 2026-11-16)
Rule: the client builder accepts a caller-supplied `HttpClient`, keeps the SDK defaults applied, never disposes a client it did not create, and fails fast when it conflicts with the SDK's proxy, TLS or pool settings.
Why: the OpenAI, Stainless, Speakeasy, Fern, Azure and Stripe SDKs all accept the standard client. Today only the `internal` seams `DefaultApiClient(HttpClient[, TransportOptions])` exist.
Where: `client.mustache`, `configuration.mustache`, `default_api_client.mustache`.
Test: `ClientTest.mustache`, `DefaultApiClientUnitTest.mustache`.

### Dependencies (S10, 2026-11-16)
Rule: a small pure library or a version bump is fine when it standardises behaviour; never a dependency that needs a native binary.
Why: the SDK is built and trimmed on Alpine and published trimmed. The SDK project references only the NetAnalyzers package; everything else is the BCL.
Where: `csproj.mustache`, `test/tests_csproj.mustache`.
Test: `CSharpTrimmedSpec`.

### Out of scope features (S11, 2026-11-16)
Rule: callbacks and webhooks, XML bodies and nodeType, Link Objects and mutual TLS are not generated and the README says so ("Webhooks and callbacks").
Why: backlog issues #53, #70, #78, #88, #90 were closed as out of scope.
Where: `readme.mustache`.
Test: golden README.

### Test runner and caches (S13, 2026-11-16)
Rule: spec parallelism is 3 (`junit-platform.properties`); the NuGet cache lives at `/root/.cache/csharp/nuget` (`NUGET_PACKAGES`); do not touch `.github` workflows from this repository.
Why: the CI test job is CPU-bound; workflow issues are filed in mridang/action-runner-common #47-#50.
Where: `CSharpSpec.getCacheEnv`, `src/spec/resources/junit-platform.properties`.
Test: CI.

### Scope order, uuid, blank keys, error cause (S14, F274, F265, 2026-11-16)
Rule: OAuth scopes keep the spec's declared order (`formatCSharpScopes`); a `Guid` is written lowercase hyphenated and validated; an api key or bearer token is blank only when empty or ASCII whitespace (not `IsNullOrWhiteSpace`); every status-specific exception has a `(statusCode, message, innerException)` constructor.
Why: one behaviour in all twelve; `IsNullOrWhiteSpace` accepts Unicode whitespace.
Where: `BetterCSharpCodegen.formatCSharpScopes`, `auth/*_authenticator.mustache`, `errors/*.mustache`.
Test: `ApiExceptionTest.mustache` (`NamedErrorWithInnerExceptionKeepsItsStatus`), `BearerAuthenticatorTest.mustache`, `ApiKeyAuthenticatorTest.mustache`.

### Secrets are redacted in string forms (S15, 2026-11-16)
Rule: `Configuration` and `TransportOptions` string forms show the proxy password and any default header named Authorization, Proxy-Authorization, Cookie, or containing key, token or secret as `***`.
Why: logs and exception text must not leak credentials. The authenticator `ToString` already masks secrets (F356).
Where: `configuration.mustache`, `transport_options.mustache`.
Test: `ConfigurationTest.mustache`, `TransportOptionsTest.mustache`.

### Timeouts, redirects and proxy validation (S16, D76, D73, D77, 2026-11-16)
Rule: one end-to-end timeout budget across all hops and the body read (one linked `CancellationTokenSource`, `HttpClient.Timeout` infinite); a caller-supplied stream is never closed by the SDK; proxy URL validation happens in `Build()`, not in the builder setter; `caCertPath` is validated even when `verifySsl` is false; a timeout of zero or less is `ArgumentOutOfRangeException`.
Why: the budget cannot be split per hop; the setter-versus-build rule keeps builders order independent.
Where: `default_api_client.mustache`, `transport_options.mustache`.
Test: `DefaultApiClientUnitTest.mustache`, `TransportOptionsTest.mustache` (`GarbagePemCaCertPathThrowsArgumentExceptionAtConstruction`).

### Deprecated parameters use a doc remark, not [Obsolete] (T2-3, forced)
Rule: a deprecated Options parameter gets `/// <remarks>This parameter is deprecated.</remarks>`; deprecated models and operations get `[Obsolete]`.
Why: the generated API reads the parameter itself, and `dotnet build --warnaserror` turns CS0618 into an error that `dotnet_diagnostic.CS0618.severity = none` does not override; `[Obsolete]` broke the build. Models and tests carry the CS0618 suppression in `.editorconfig` (`[**/Models/*.cs]`, `[Test/**.cs]`) and files that reference an obsolete member get a file-level pragma from `postProcessAllModels`.
Where: `api/options.mustache`, `BetterCSharpCodegen.postProcessAllModels`, `editorconfig.mustache`.
Test: `CSharpStaticAnalysisSpec`.

### Deprecation marker on every variant (T2-5)
Rule: a deprecated operation carries `[Obsolete]` on the data method, the `WithHttpInfoAsync` variant and every server or content-type overload. Do not collapse them to one marker.
Why: the obsolete-within-obsolete rule keeps self-consumption warning free under warnings as errors.
Where: `api/api.mustache`.
Test: `CSharpStaticAnalysisSpec`.

### Dispose and facade lifetime (T2-11, T2-10)
Rule: only the facade and `DefaultApiClient` are `IDisposable`; `Configuration` keeps its public constructor while `TransportOptions` is builder-only.
Why: CA1001 requires `IDisposable` on an owner; the transport has validated invariants, `Configuration` is a plain value used directly by tests. Making `TransportOptions` internal would remove public API.
Where: `client.mustache`, `default_api_client.mustache`, `configuration.mustache`, `transport_options.mustache`.
Test: `ClientTest.mustache`, `ConfigurationTest.mustache`.

### Certificate revocation checking stays on (F262, forced)
Rule: `HttpClientHandler.CheckCertificateRevocationList = true` stays; the `caCertPath` branch uses `RevocationMode.NoCheck`.
Why: removing it fails every build with CA5400 under `--warnaserror`, and no generated file may carry a suppression. csharp is therefore the only SDK with online revocation checking.
Where: `default_api_client.mustache`.
Test: `CSharpStaticAnalysisSpec`.

### Response headers drop Content-Encoding and Content-Length (F261, D92, forced)
Rule: on the automatic path, the response headers returned to the caller omit both headers; the manual decoder path also strips them.
Why: `AutomaticDecompression` removes them from `Content.Headers` itself, so the wire values are not recoverable.
Where: `default_api_client.mustache`.
Test: `DefaultApiClientUnitTest.mustache`.

### JSON string escaping residual (F198, D84, forced)
Rule: request bodies keep `UnsafeRelaxedJsonEscaping`; `Utf8JsonWriter` still escapes supplementary-plane characters.
Why: avoiding it needs a custom writer or post-processing, not a small template change; the finding is marked borderline forced.
Where: `object_serializer.mustache`.
Test: `ObjectSerializerTest.mustache`.

### Content-Encoding handling (R7-4, R5-4, F157, F160)
Rule: `deflate` is zlib (`ZLibStream`), `br` is `BrotliStream`, an unknown or chained coding raises `ApiException` with the response status, an empty body passes through, and the default `Accept-Encoding` is `gzip, deflate, br` (no zstd; there is no .NET zstd). A caller-supplied value replaces it.
Why: HTTP `deflate` is RFC 1950; the other eleven decode it that way. A raw RFC 1951 body is an error.
Where: `default_api_client.mustache` (`DecodeContentEncoding`).
Test: `DefaultApiClientUnitTest.mustache` (`ZlibWrappedDeflateBodyIsDecoded`, `RawDeflateBodySurfaces...`, `AdvertisesGzipDeflateAndBrotliByDefault`, `DecodesBareUtf16BodyWithLittleEndianBom...`).

### Request content types (T1-7, F343)
Rule: an unrecognised multi-consumes content type throws `ArgumentException` before dispatch, and a text, JSON or form body is sent with the bare declared Content-Type, no `; charset=utf-8` (`CreateStringContent`).
Why: a typo must never ride the wire as an undeclared type; the other eleven send no charset and no ruling forced one.
Where: `default_api_client.mustache`, `base_api.mustache`.
Test: `DefaultApiClientUnitTest.mustache` (`TextBodySendsTheDeclaredContentTypeWithoutCharset`).

### Body encoding (D35, D36, D38, D39, F011, F144)
Rule: a binary body under a non-binary Content-Type is sent verbatim (`rawBody: true`); multipart scalars are text parts through `ObjectSerializer.Stringify` (`true`, invariant numbers); Content-Disposition is written before Content-Type on every part; a form-urlencoded field that is not a scalar or array of scalars is a `SerializationException`; multipart and Stream bodies are buffered so a 307 or 308 replays them; a JSON `null` body on a body-returning operation is the canonical "Expected a response body for ..." `ApiException`.
Why: each is the canonical behaviour in D35 to D39; the buffering holds the whole body in memory (F413, see limitations).
Where: `base_api.mustache`, `object_serializer.mustache`, `default_api_client.mustache`.
Test: `DefaultApiClientUnitTest.mustache` (`Redirect307ReplaysMultipartBodyWithItsBoundary`), `BaseApiTest.mustache`, `Test/Api/PetApiTest.mustache`.

### Binary and byte responses (D30, D31)
Rule: a `format: binary` response is never JSON-parsed, a `format: byte` response is JSON by default decoded with one strict decoder, and every failure is a `SerializationException`.
Why: one rule for JSON, absent, text and binary Content-Types in all twelve.
Where: `base_api.mustache` (`DecodeBinaryBody`), `object_serializer.mustache`.
Test: `Test/Api/PetApiTest.mustache`, `ObjectSerializerTest.mustache`.

### Parameter encoding (D48, D49, D51, D82, F348, F350)
Rule: a path value of exactly `.` or `..` is an `ArgumentException` before dispatch; `allowReserved` keeps only the RFC 3986 query set literal (`#`, `[`, `]` are encoded); `byte[]` parameters are padded base64; a `HashSet` expands like an array; a null element is an empty slot in csv, ssv, tsv, pipes, multi, header and form arrays; path segments keep sub-delimiters and unreserved characters literal.
Why: canonical in all twelve.
Where: `value_serializer.mustache`, `base_api.mustache`.
Test: `ValueSerializerTest.mustache` (`QueryArrayKeepsAnEmptySlotForANullElement`, `PathSegmentKeepsEachSubDelimiterAndUnreservedCharacterLiteral`).

### Serializer wire rules (D57, D58, D62, D63, D65, D64, D61, D24, F141)
Rule: `date-time` needs an RFC 3339 offset (`DateTimeOffsetJsonConverter`); `byte[]` uses `StrictBase64Converter`; `format: uri` is an absolute URI (`AbsoluteUriConverter`, scheme regex because Unix .NET treats `/relative` as absolute); an additionalProperties key that collides with a declared property is dropped on write by a type-info modifier; a null element in a top-level list or dictionary of models is rejected; oneOf variants are tried in declaration order; numeric defaults render as `1.5m` and `0.25f`; union wrappers have value equality; a non-string discriminator is a `JsonException`.
Why: canonical in all twelve; the converters run inside System.Text.Json, so a failure surfaces as `SerializationException`. Nested collections inside models are not covered by the null-element post-pass.
Where: `object_serializer.mustache`, `models/model.mustache`, `BetterCSharpCodegen.toDefaultValue`.
Test: `ObjectSerializerTest.mustache`, `ComposedSchemaTest.mustache`, `MetadataTest.mustache`.

### OAuth refresh, exchange and rediscovery (D01, D02, D03, F287)
Rule: `GetAccessTokenAsync` takes an optional trailing `refreshUrl`; a held refresh token is sent as `grant_type=refresh_token` to it with client credentials and the same extra headers, and a failure re-runs the original grant at `tokenUrl`. `ExchangeAsync` always POSTs the given params, replaces the session on success and restores the previous one on failure. OIDC rediscovery keeps the token manager and discovery is single-flight under a `SemaphoreSlim(1,1)` with a re-check inside. `refreshUrl` is a trailing optional constructor argument.
Why: RFC 6749 sections 6 and 3.2.1; a cache refresh must never drop the session.
Where: `auth/oauth/oauth2_token_manager.mustache`, `oauth2_auth_code_authenticator.mustache`, `oauth2_password_authenticator.mustache`, `openid_connect_authenticator.mustache`.
Test: `OAuth2TokenManagerTest.mustache`, `OAuth2AuthCodeAuthenticatorTest.mustache` (`RefreshGrantGoesToTheOptionalTrailingRefreshUrl`), `OpenIdConnectAuthenticatorTest.mustache` (`DiscoveryIsReusedOnlyWhileItsMaxAgeHolds`).

### OAuth URLs and tokens (D04, D05, D06, D08, D11, F117, F412, F408, F167)
Rule: the authorize URL and the code-exchange body use `Uri.OriginalString` for `redirect_uri` and the base (no trailing slash or port rewriting); an empty `state` is absent; OIDC always requests `openid`; an empty implicit access token throws `InvalidOperationException` and `SetAccessToken` rejects CR, LF, NUL and non-ASCII; `expires_in` is capped at one year (`MaxExpiresInSeconds`); `_accessToken` and `_tokenExchanged` are `volatile`; a non-string `refresh_token` is ignored, not coerced.
Why: canonical OAuth rules in the shared file; `System.Uri.ToString()` normalises and changes the bytes.
Where: `auth/oauth/*.mustache`.
Test: `OAuth2AuthCodeAuthenticatorTest.mustache`, `OAuth2ImplicitAuthenticatorTest.mustache`, `OAuth2TokenManagerTest.mustache` (`NonStringRefreshTokenIsIgnoredNotCoerced`).

### Error contract (D13, D14, D18, F100, F265)
Rule: `ApiException` carries the response headers (a read-only copy) when a response arrived but was unusable; the error body is parsed with the BOM stripped by the shared helper; an over-deep body keeps the status type with a null body; `OAuth2ServerException` has the three-form message.
Why: one contract for all twelve; null headers mean no response arrived.
Where: `api_error.mustache`, `errors/*.mustache`, `object_serializer.mustache`.
Test: `ApiExceptionTest.mustache`, `OAuth2TokenManagerTest.mustache` (`OAuth2ServerErrorMessageHasThreeForms`).

### Required parameter messages (D25, D26, F323, F324, F460)
Rule: a null required Options object throws `ArgumentNullException` with the canonical "Missing the required parameter '<x>' when calling <Class>.<operation>" (via `BaseApi.RequireOption`), and so does each missing required field; an optional body defaults to `null` unless a required Options argument follows (C# forbids a default before a required parameter).
Why: one message in all twelve; `ThrowIfNull` gave no canonical text.
Where: `api/api.mustache`, `base_api.mustache`.
Test: `Test/Api/PetApiTest.mustache` (`TestGetPetByNameNullOptionsNamesTheFirstRequiredField`), `Test/Api/StoreApiTest.mustache` (`TestPlaceOrderWithoutBodyCanBeCalledWithNoArguments`).

### Per-operation servers and identifiers (D42, D43, D46, D40, D41, F124, F309)
Rule: server variable enum types are prefixed with the operation and, when the variable name occurs in two or more variants, with the variant (`RegionalRegion`, `MirrorRegion`); a digit-leading or empty sanitised name gets a deterministic fallback (`n` prefix, `SPACE_<len>`, `VALUE_<hex>`); enum string literals always escape the quote and the backslash (the template supplies the quotes, so `toEnumValue` only escapes); `shouldEscapeReservedVarName` is false because PascalCase keywords are legal; the csproj description goes through `escapeXmlText`.
Why: canonical in all twelve; cross-operation collisions at namespace level were a real bug.
Where: `BetterCSharpCodegen`, `api/api.mustache`, `servers.mustache`, `csproj.mustache`.
Test: `Test/Api/PetApiTest.mustache` (`TestPerOperationServerTypesAreScopedToTheirOperation`), `CSharpReservedWordsSpec`.

### Transport TLS and redirects (D71, D72, F036, F037, F351)
Rule: an unparseable Location is treated like a missing one and the 3xx is returned; `caCertPath` replaces the system trust store and a host name mismatch still fails; default-port redirects keep Authorization and a different port strips it; Cookie and Proxy-Authorization are stripped across origins; the HTTPS to HTTP guard anchors on the current hop.
Why: canonical in all twelve.
Where: `default_api_client.mustache`, `transport_options.mustache`.
Test: `DefaultApiClientUnitTest.mustache` (`CrossOriginRedirectStripsEachCredentialHeader`, `RedirectToExplicitDefaultPortPreservesAuthorization`), `DefaultApiClientTest.mustache` (`VerifySslTrueRejectsAnUntrustedCertificate`).

### Analyzer policy (F193, F195, Guardrail 3)
Rule: generated code carries no `// <auto-generated/>` header, so every analyzer and CSharpier applies; the only suppressions are named rules with a reason in `.editorconfig` (`CA1002`, `CA2227`, `CA1819`, `CA1054-6`, `CA1062`, `CA1032`, `CA1008`, `CA1707`, `CA1711`, `CA1724`, `CA1033`, `IL2026`, `CA2000`, plus `CA1716` and `CS0618` for models and `CS0618`, `CA1034`, `CA1515`, `CA2007` for tests); no generated file carries a suppression; the test project uses the same analyzer set and `--warnaserror` as the SDK; inline `//` comments are banned (block comments only) in every file including models.
Why: a container build whose `.dockerignore` excludes `.editorconfig` fails although a local build passes; a rule is suppressed only when generated code cannot satisfy it.
Where: `editorconfig.mustache`, `csproj.mustache`, `test/tests_csproj.mustache`.
Test: `CSharpStaticAnalysisSpec`, `CSharpFormattingSpec`.

### Message wording that is csharp specific
Rule: a oneOf/anyOf no-match error is "JSON did not match any schema in the <X> union: <raw JSON>" from the per-model path (the central helper says "No oneOf/anyOf variant matched the JSON"); `ToString` on a model dumps `ClassName{prop=value}` through a fully qualified `System.Text.StringBuilder`.
Why: unifying the per-model paths would need unrelated code changes (F396).
Where: `models/model.mustache`.
Test: `ComposedSchemaTest.mustache`, `MetadataTest.mustache`.

### Decisions csharp already satisfies (no csharp-specific code)
Rule: these decisions need nothing more from csharp; do not flag them as missing. They are D09, D10, D20, D22, D27, D28, D33, D34, D44, D52, D53, D55, D56, D59, D67, D75, D78, D83 (decimal, plain digits, quoted decimals rejected), D85 to D89, D91 (full encoding registry), D93 and D94 (F257 UTF-16 BOM sniffing is csharp's).
Why: Dictionary insertion order gives the declared order, `Guid` and `decimal` give the wire forms, and the ledger closed the rest as already canonical or false positive.
Where: `base_api.mustache`, `value_serializer.mustache`, `object_serializer.mustache`.
Test: the generated suites listed above.

### API-surface allow rules (etc/api-surface-allow.json)
Rule: twelve csharp rules exist, each with its reason in the file: the `(message, innerException)` constructor, `Dispose` instead of `close`, `parameters` instead of `params`, no `cause` member, no `ActualInstance`, internal `ObjectSerializer`, `ValueSerializer` and `HeaderSelector`, the `exchanged` flag kept on the authenticator, `ServerVariable` constructor overloads, and extra internal helper members. Add a rule only for genuine idiom and state the reason.
Why: a rule without a language feature is a defect hidden.
Where: `etc/api-surface-allow.json`.
Test: `python3 etc/api-surface.py check`.

## Known limitations and out of scope

- Decided, not yet implemented in csharp: parameter order with the token (S8, F803), a public injected `HttpClient` (S9, F405), secret redaction in `Configuration` and `TransportOptions` strings (S15), proxy validation in `Build()` instead of the builder setter (S16; today `TransportOptionsBuilder.Proxy` throws `ArgumentException` and the README says so), the README "Schema constraints" section (S3), `content:` parameters in header, path and cookie locations (S2, F054 is open), required plus nullable (S5, F215 is open), and the F802 re-parenting of `DryFood` and `WetFood`.
- Ambient proxy settings are still honoured because `UseProxy` is not set to false when no proxy is configured (D74, F090 open).
- A non-JSON success Content-Type on a model-returning operation (D32, F149) and the Model prefix for colliding model names (D45, F173) are open.
- Object-valued query and header parameters (D47, F063), `format: time` stringify (D81, F071), a default on a `$ref` enum (D60, F131), an absent required discriminator (F129) and integer-enum member casing, which come out as `NUMBER_1` (F308), are parked.
- Model `ToString` appends collections raw, so it prints the collection type name (F321, parked); `uniqueItems` order is deliberately unspecified (S6).
- `format: binary` responses are `System.IO.Stream` (a `MemoryStream`), where nine SDKs return bytes; changing it is a public API change and a cross-language decision (F332, too big).
- Multipart and Stream request bodies are buffered whole in memory when redirects are followed (F413, too big).
- The docfx build is not run by any spec (F191), and the Makefile target vocabulary is not yet agreed across the twelve (F371).
- A required-property check before dispatch for models missing required fields is deferred (D68, F227).
- OAuth2 authenticator `ToString` omits `refreshUrl` and is not harmonised (F285); the sync `GetAuthHeaders` throws on OAuth flows, which the other languages should copy (F290 is open for node, dart, go and rust).
- OpenAPI 3.1 and 3.2 support is tracked in epics #49 (3.2) and #96 (3.1); loading 3.2 documents is issue #50 because the pinned swagger-parser rejects it. Out of scope: callbacks, webhooks, XML, Link Objects, mutual TLS (issues #53, #70, #78, #88, #90).

## Build-breaking rules

- No `//` line comments in generated code under `src/PetstoreClient/` (the regex `^\s*//(?!/)`); use `///` XML docs or `/* */`.
- No named HTML entities in generated text; only `&#38;`, `&#60;` and `&#62;` are allowed in XML doc comments, and prose with XML metacharacters goes through `escapeXml` (CDATA).
- A mustache `{{! ... }}` comment must not contain a literal `{{tag}}`; jmustache ends it at the first `}}`.
- Never add a suppression, `#pragma` or `[SuppressMessage]` to generated code beyond the model-file CS0618 pragma; change `editorconfig.mustache` with a reason instead.
- Use target-typed `new()` and the numeric references above; CSharpier must report no change, and `dotnet format --verify-no-changes --severity warn` must pass.
- `dotnet build --warnaserror` must pass for both the SDK and the test project; `[Obsolete]` on a self-consumed member breaks it.
- Do not add a native-binary dependency; the SDK has only the NetAnalyzers package.
- Generated tests are emitted only for what a client actually gets: a test that needs `assertj`-style or container-only dependencies belongs in the golden-only set, and `.openapi-generator/DEV-DEPENDENCIES` must list only emitted dependencies.
- The test project is `OutputType=Exe` on Microsoft Testing Platform; do not add `Microsoft.NET.Test.Sdk` or `xunit.runner.visualstudio`, which route to VSTest and silently skip the container specs.
- Container-backed fixtures need `ExposedPorts` for every published port, verified on CI.

## Verify

- `etc/ci-local.sh check CSharp`
- Regenerate the golden: `mvn test -Dtest='Generate*ClientTest,FixtureVocabularyLeakTest' -DforkCount=0 -Djacoco.skip=true -DfailIfNoTests=false` (through `devbox run --`).
- One language end to end: `mvn verify -Dgroups=csharp`
- API surface: `python3 etc/api-surface.py check` (must not get worse)
- Generated client gates: `dotnet build --warnaserror`, `dotnet csharpier check .`, `dotnet format --verify-no-changes --severity warn`, `dotnet run --project PetstoreClient.Test.csproj -- -jUnit .out/reports/junit.xml`

### Batch 5 (F824, F831, F832, F833, F839)
Rule: `Client` is `IDisposable`, disposes the `DefaultApiClient` it built, and a closed client throws `InvalidOperationException` ("ApiClient has been closed and can no longer send requests", D29); a supplied `HttpMessageHandler` is never disposed (F824). HTTP 429 maps to `TooManyRequestsException`, a `ClientException` (F831). The non-JSON Content-Type `SerializationException` names `Class.operationId`, passed as `operationName` from each generated operation (F832). A `content:` cookie is JSON-serialised then `Uri.EscapeDataString`-encoded, already conforming to the unreserved set (F833). Redirects: 301/302 turn only POST into GET, 303 turns everything but HEAD into GET, 307/308 keep method and body; the HTTPS to HTTP body guard covers every hop that would replay a body (F839).

Test: `ClientTest.mustache`, `ApiExceptionTest.mustache`, `BaseApiTest.mustache` (`ContentTypeErrorNamesTheOperationNotTheType`, `ContentCookieKeepsOnlyTheUnreservedSetLiteral`), `DefaultApiClientTest.mustache` (`RedirectRewritesTheMethodPerRfc9110`).

### Decisions taken in batch 6 (F898, F909, F854, F895, F896, F860, F902-F905, F922, D07, D15, D16, D17, D77)
Rule: the OAuth2 token manager keeps the access token and its refresh instant in one immutable snapshot behind one volatile reference, so the lock-free fast path never pairs a token with another token's expiry (F909). The real expiry is stored and one margin is applied: the token is refreshed `min(60 s, expires_in / 2)` before it expires, so `expires_in` of 60 is reused for 30 s (F854, F895); a response without `expires_in` carries no expiry and is cached until invalidated, including after a refresh (F896); token response errors are the three D07 messages with no cause and no parser detail (F903).

Rule: the redirect loop carries the credential-filtered header map from hop to hop and compares each hop with the previous URL, so a header dropped on a cross-origin hop stays dropped when the chain returns to the original origin (F898). The three refusals use the D16 text and carry the real lower-cased response headers; a 3xx without a Location is returned unchanged even when the budget is spent (D70, F904); `MaxRedirects` rejects a negative value at configuration. The header merge is case-insensitive, so a caller `authorization` replaces a default `Authorization` and `Accept-Encoding` is not added beside a caller's lower-case one (F860 merge part, F905).

Rule: `ApiException.ToString()` renders headers as compact JSON with sorted keys and unescaped slashes (D17); `ObjectSerializer` messages are `Failed to serialize object to JSON: <detail>`, `Failed to deserialize JSON: <detail>` and a pre-scan `JSON nesting depth N exceeds limit 1000` (D15); the timeout message is the D77 text. `ArgumentException` appends `(Parameter 'x')` to `Message`, so tests pin these two with `StartsWith`; this is the language feature forcing the difference.

Skipped (cross-language or shared Java): F908 (`maxResponseBytes` is a new public option in all four transports), F290 (csharp already fails loud; the other languages decide), F860 stripping defaults from token requests (needs an `IApiClient` contract change), F887 (needs a typed example literal per property from shared Java), F802, F054, F056, F057 and the other open API-shape findings.

Test: `OAuth2TokenManagerTest.mustache` (`ShortLivedTokenIsReusedUnderASingleMargin`, `RefreshResponseWithoutExpiresInClearsTheStaleExpiry`, `UnusableTokenResponseHasTheCanonicalMessageAndNoCause`, `ConcurrentReadersNeverSeeATokenOtherThanOneThatWasSet`), `DefaultApiClientUnitTest.mustache` (`CredentialsDroppedOnACrossOriginHopStayDroppedWhenTheChainReturns`, `ReturnsAFinalRedirectWithoutLocationUnchanged`, `LowerCaseCallerAcceptEncodingIsNotSentAlongsideTheDefault`), `TransportOptionsTest.mustache`, `ApiExceptionTest.mustache`, `ObjectSerializerTest.mustache` (`NestingBeyondTheDepthLimitIsRefusedBeforeParsing`, `UuidIsWrittenLowercaseHyphenated`), `OAuth2AuthCodeAuthenticatorTest.mustache`, `Test/Api/PetApiTest.mustache` (`TestNonJsonSuccessContentTypeNamesTheOperation`).

### Decisions taken in batch 7 (F1076, F1079, F1080, F1086, F1093, F1156, F1177, F856, F859, F867, F1055, F1155, F1169)
Rule: the pinned-CA callback adds the intermediates the server sent in its handshake to the custom chain's `ExtraStore`, so a root-only `caCertPath` validates root, intermediate and leaf (F1076). `TransportOptionsBuilder.Timeout` rejects zero as well as a negative value with the one D77 message at configuration, which supersedes the zero check at `DefaultApiClient` construction that stays as a second guard (F1079). `ApiException.ToString()` caps the rendered body at 1024 characters with a `... (N more bytes)` suffix (the UTF-8 length of the hidden rest), escapes CR, LF and control characters, and shows credential-carrying response headers (Authorization, Proxy-Authorization, WWW-Authenticate, Proxy-Authenticate, Cookie, Set-Cookie and any name containing key, token or secret) as `***`; `ResponseBody` and `ResponseHeaders` keep the full values (F1080). Proxy validation messages show the user info as `***@` (F1086). A cross-origin redirect drops every header in `SensitiveHeaderNames` and any header whose name contains key, token or secret, so default headers and custom authenticator headers are not replayed (F1093). `ApiException.ForUnusableResponse` is `internal` (F1177). The file header description goes through `unescapeDocComment` (F1156).

Rule: `BaseApi` injects its `ApiClient` into a per-call `IHttpAwareAuthenticator` that is not the client-level one, on every call and idempotently, instead of failing with "ApiClient has not been injected" (F856). An OIDC discovery document is rejected with `SerializationException` when its `issuer`, trailing slash ignored, differs from the discovery URL without `/.well-known/openid-configuration`, or when the authorization or token endpoint is not https and not a loopback host; a document with no `issuer` is still accepted (F859). `format: password` model properties render as `***` in the model `ToString` (F867). A parameter named `options`, `server`, `cancellationToken` or `requestContentType` gets a `Param` suffix from `toParamName` (F1055).

Skipped in batch 7: F855 (a 401 retry and a public invalidate on the OAuth authenticators change the cross-language API shape), F840 and F1058 (owner decisions), F865 and F866 (need `additionalPropertiesType` and the `$ref` sibling default from shared Java), F1062 and F802 (shared Java), F057, F1060, F1100.

Test: `ApiExceptionTest.mustache`, `DefaultApiClientTest.mustache` (`CustomCaCertAcceptsAServerThatSendsAnIntermediate`), `TransportOptionsTest.mustache`, `DefaultApiClientUnitTest.mustache`, `BaseApiTest.mustache`, `OpenIdConnectAuthenticatorTest.mustache`, `OAuth2TokenManagerTest.mustache`, `MetadataTest.mustache`, `ObjectSerializerTest.mustache` (`FixtureEdgeCaseTests`), `Test/Api/PetApiTest.mustache`, `Test/Api/StoreApiTest.mustache`, `BetterCSharpCodegenTest`.

### Batch 7 follow-up: fixture tests and pagination (F1143, F1141, F1142, F1161, F1125, F1144, F1148, F1116, F1175, F1067, F1131, F1100)
Rule: the object-valued `getPetTag` parameters, the `addPetTreatment` anyOf body, the non-string `uploadPetDocument` parts, `getDepartment` and `getStockItem`, the four hostile `Availability` members, U+2028, U+2029 and DEL written literally, the `contentEncoding` byte property, the `Pet.location` tuple, last-wins duplicate keys, the `Error` and `Result` schemas and the typed `/test/status/{code}` routes through `getPetById` are all pinned. C# does not prefix the colliding schemas `Error` and `Result` (a namespace separates them from the BCL), so F131 and F173 need no csharp change. `/test/response-headers` stays uncalled until multi-value response headers are decided (F836).

Rule (F1100, proposal taken as the default): pagination, when added, is a separate additively named method per operation (`<Operation>PagesAsync` returning `IAsyncEnumerable<T>`), never a change to the return type of the existing data method. F1060 (one wrapper per OAuth2 flow) lives in shared Java `buildOAuthSpec` and is skipped.

Test: `ObjectSerializerTest.mustache` (`FixtureEdgeCaseTests`), `Test/Api/PetApiTest.mustache`, `Test/Api/StoreApiTest.mustache`.

Rule (batch 7 verification fixes): `ObjectSerializer` registers `LiteralStringConverter`, so request JSON escapes only the quote, the backslash and the C0 controls and writes U+007F, U+2028, U+2029, `/`, `<`, `>`, `&` and `'` literally (System.Text.Json's encoders always escape the first three). `ValueSerializer.SerializeStyled` flattens a generated model by property (`ModelFields`): exploded form and the delimited query styles return `ExplodedFields`, which `BaseApi.BuildQueryString` writes as one `name=value` entry per property; the other styles and headers flatten to `name,value,...` (or `name=value` pairs when exploded). Tests: `Test/Api/PetApiTest.mustache` (`GetPetTagWiresTheObjectValuedParameters`), `ObjectSerializerTest.mustache` (`FixtureEdgeCaseTests`).

### Decisions taken in batch 9 (F855, F858, F823, F1080; F825 is rust only)
Rule (F855): the OAuth2 client-credentials, password, authorization-code and OpenID Connect authenticators implement `IInvalidatableAuthenticator.InvalidateAccessToken()` (declared in `authenticator.mustache`), which drops the cached access token and keeps the refresh token. `BaseApi.InvokeApiForResultAsync` calls it when the response is 401 and the effective authenticator implements the interface, rebuilds the headers and sends the request once more; a second 401 is the usual `UnauthorizedException`. A stream or multipart body is never replayed (only a null, string or byte[] body is). The implicit, API-key, basic and bearer authenticators do not implement the interface and never replay.
Rule (F858): `OAuth2AuthorizationCodeAuthenticator.BuildAuthorizationUrl` makes a fresh 43-character verifier (32 random bytes, base64url) for each URL, keeps it in the authenticator, adds `code_challenge` (SHA-256, base64url, via `System.Security.Cryptography`) and `code_challenge_method=S256` after `state`, and `ExchangeCodeAsync` sends `code_verifier` and then forgets it; an exchange without a built URL sends none. The OIDC rediscovery passes the verifier to the new delegate. There is no nonce.
Rule (F823): `TransportOptionsBuilder.MaxRetries(n)` (default 2, 0 disables, negative throws) drives a retry loop in `DefaultApiClient.SendRequestAsync` around one attempt: network errors before a response (not `NetworkTimeoutException`, not a failed body read) and the statuses 408, 409, 429 and 500-599 except 501 are retried after `RetryDelay` (`Retry-After` seconds or HTTP date capped at 60 s, else 0.5 s doubling to 8 s with 25% jitter taken off). One timeout source and one linked token cover every attempt and every wait, a wait that would not fit the timeout returns the last response (or rethrows) instead of waiting, and cancellation propagates. Only null, string and byte[] bodies are retried. `IApiClient` gets a default-implemented overload with a `refreshHeaders` callback (placed before the token for CA1068) that `BaseApi` fills with its header builder, so every retry carries rebuilt authentication headers. Tests that expect a single failing request against a real client use `MaxRetries(0)`.
Rule (F1080): already conforming apart from the suffix wording, fixed to `... (N more bytes)`; control characters stay escaped as `\uNNNN` like java, node and rust (the brief's `\xNN` is not what the siblings do).
Skipped: F825 (rust only).
Test: `DefaultApiClientUnitTest.mustache` (retry rows and `RetryDelay`), `TransportOptionsTest.mustache`, `BaseApiTest.mustache` (401 replay rows), `OAuth2AuthCodeAuthenticatorTest.mustache`, `OAuth2ClientCredentialsAuthenticatorTest.mustache`, `OAuth2PasswordAuthenticatorTest.mustache`, `OpenIdConnectAuthenticatorTest.mustache`, `ApiExceptionTest.mustache`.

### Decisions taken in batch 10 (F860, F863, F887-F890, F893, F1065, F1071, F1073, F1074, F1077, F1103, F1107, F1109, F1118, F1119, F1124, F1129, F1135, F1139, F1140, F1162, F1167, F1170)
Rule (F1118): `ServerConfiguration.GetUrl` resolves and validates every variable first, then substitutes in one left-to-right pass, so a value that contains a placeholder is never expanded again; an unknown placeholder or a lone brace stays as written. `ServerVariable.EnumValues` is copied into a read-only collection at construction (F1139).
Rule (F863): the token manager refuses a `token_type` that is present, not null and not `bearer` in any case, with `Unsupported token_type in token response` and without echoing the value.
Rule (F860, token part): a no-redirect request (the OAuth2 token POST) carries no `TransportOptions.DefaultHeaders`; User-Agent, X-Request-ID and Accept-Encoding stay. OIDC discovery still merges them. No signature changed.
Rule (F1103): `failed to read CA certificate from "<path>": <cause>` for IO and access errors, `failed to parse CA certificate from "<path>": <cause>` for a malformed file, and `... no PEM blocks found` for a file without certificates.
Rule (F893): the per-parameter named examples are rendered as `<example>` on the properties of the generated options class (`generateOptionsFileContent` passes `pluralExamples`).
Rule (F887-F890): the README gains Installation, Usage, Raw responses, Timeouts, Automatic retries, Expired tokens, Request id, Cancellation and Custom HTTP transport above Build; Not supported lists XML bodies, Link Objects and mutual TLS; the SKILLS model sample passes the required constructor arguments. F869 is documented (an enum's `ToString()` is the member name) rather than changed. F1077: `make audit` runs `dotnet list package --vulnerable --include-transitive`.
Already conforming: F855/F1180 (`IInvalidatableAuthenticator`, authenticator.mustache:65), F1141 and F1161 typed routes (`Test/Api/StoreApiTest.mustache`), F1162 plain notation (`BareNumberIsExactAndWrittenInPlainNotation`), F890 headings, F831 429.
Skipped: F332 and F1081 (cross-language shape), F823 follow-ups, F840, F843, F857, F862, F865, F866, F894, F1056, F1061, F1157, F1172, F1085 (shared or owner decisions), F908 (new public option), F1099 (`GenerateDocumentationFile` turns on CS1591 and doc warnings that `--warnaserror` makes fatal; cannot be verified without dotnet), F1161 `/test/response-headers` (F836), F831 502-504 types, F860 OIDC discovery.
Test: `BearerAuthenticatorTest`, `TransportOptionsTest`, `ServerConfigurationTest`, `ServerVariableTest`, `MetadataTest`, `DefaultApiClientUnitTest` (identity, redirect release, CA messages, token POST headers), `OAuth2TokenManagerTest`, `ComposedSchemaTest`, `ObjectSerializerTest`, `BaseApiTest`, `CSharpFormattingSpec`.

### Decisions taken in wave 2 (F830, F827, F868, F1062, F878, F894, F862, F1099, F308, F860, F889, F834-F837, F828, F1161, F1087, F1119, F1124, F1153, F1172, F1174, F866, F1060, F1061, F1157)

Implemented:
- F830: `ApiException` (so every subclass) has `RequestMethod` and `RequestPath` (get-only, set by `FromResponse`). `FromResponse(statusCode, headers, body, requestMethod = null, requestPath = null)` builds the message `API returned status code 404 for GET /items/7` when both are given and strips any query or fragment from the path; with neither the message is unchanged. `BaseApi` passes the method and the request URI's absolute path (base path included, query left out), and OIDC discovery passes `GET` and the discovery path. The same wording is the canonical one for the other languages.
- F827: every per-operation Options class has `int? Timeout` (ms, greater than zero), `IReadOnlyDictionary<string,string>? Headers` (applied last, replacing a same-named header whatever its casing) and `int? MaxRetries` (0 turns retries off). `BaseApi.InvokeApiForResultAsync` takes `timeout`, `extraHeaders` and `maxRetries` and validates them; `IApiClient` gains a default overload `SendRequestAsync(..., refreshHeaders, timeout, maxRetries, cancellationToken)` that `DefaultApiClient` implements (the end-to-end budget and the retry loop use the per-call values). A parameter named `timeout`, `headers` or `maxRetries` is renamed with the `Param` suffix, like `options`. Adopted from the shared generator: `mintsOptionsForEveryOperation()` returns true, so an operation with no parameters and no security requirement also gets an Options record (only `Timeout`, `Headers` and `MaxRetries`, no `Auth`) and an optional `options` argument; `api.mustache` passes the three settings unconditionally and documents the argument from `vendorExtensions.op.optionsCallSettingsOnly` (the call-settings-only case), and the barrel and `using` come from `optionsImports`. Tests: `StoreApiTest` (`getInventory` is the no-parameter, no-security fixture).
- F868: a plain model is a `record` (the discriminator base is an `abstract record`), every property is `init`-only (the `AdditionalProperties` dictionary too), so `model with { X = 1 }` is the copy method; the copy is shallow. A oneOf wrapper's `ActualInstance` is get-only; the wrapper is built anew rather than copied. The hand-written `Equals(T?)` is `public virtual` (records forbid a user `Equals(object)`, so it is gone), and `GetHashCode` and `ToString` stay hand-written.
- F1062: `InvokeApiForResultAsync` takes `decodeByStatus`; for an operation with `hasMultiple2xx` the api template passes a lambda that reads the body as the schema of the status that arrived (a `2XX` range last) and wraps it in the union.
- F878: a non-discriminator oneOf wrapper has `From<Variant>(value)` (rejects null), `Is<Variant>()` and `As<Variant>()` per variant; the variant name is the variant type without punctuation (`List<byte[]>` is `ListByte`), set in `nameUnionVariants` on `composedSchemas.oneOf`.
- F894: generator options `repositoryUrl`, `homepageUrl`, `authors`, `documentationUrl`, `issueTrackerUrl` (and the existing `licenseId`) feed the csproj (`RepositoryUrl`, `PackageProjectUrl`, `Authors`, SourceLink for a GitHub URL) and the README Package list. They are read from `additionalProperties`, so no shared change was needed.
- F862: per-scheme credentials come from the environment. Every credential argument of a per-scheme authenticator is optional (`string? username = null`, `Uri? redirectUri = null`): an argument that is not null wins, else the constructor reads the scheme's variable once (`BaseAuthenticator.ResolveCredential` and `ResolveUri`, called from the `base(...)` initializer; an empty variable counts as unset), and a credential that is neither passed nor set throws `InvalidOperationException` that names the variable (the same error `Client.FromEnvironment` raises). The names come from the shared `securitySchemeEnvVars` through the `envVars` of `baseSchemeContext` (`{PREFIX}_{FIELD}`, qualified with the scheme id when two schemes share a field); `{PREFIX}` is the shared `envVarPrefix` option (the old csharp `envPrefix` option is gone). A bearer per-scheme authenticator also has a `(host, tokenProvider)` constructor; a token provider is an explicit argument, so it wins over the environment and no variable is read. `Client.FromEnvironment(host, token, tokenProvider, transportOptions)` is unchanged except for the prefix: `{PREFIX}_HOST` and `{PREFIX}_TOKEN`. The host has no per-scheme variable. Tests: `SchemeAuthenticatorTest` (one set per scheme: reads every variable, arguments win, empty is unset, missing fails naming the variable, provider wins) in the `Environment` collection that `ClientTest` shares, because the process environment is global; `BetterCSharpCodegenTest` for the rendering.
- F866: a property whose `$ref` carries a sibling `default` is initialised from `vendorExtensions.siblingDefaultIsEnum`, `siblingDefaultEnumType` and `siblingDefaultEnumVar` in `models/model.mustache` (both optional-property branches), so `new StockItem(...).Availability` is `OnHold` and an absent JSON key decodes to it. Limit: only a referenced string enum is initialised; a sibling default on a `$ref` to a non-enum schema is still ignored. Test: `ObjectSerializerTest` (`NonLowercaseStringEnumTests`).
- F1099: the csproj sets `GenerateDocumentationFile`, `PackageReadmeFile` (the README is packed from `../../README.md`), `Deterministic`, `ContinuousIntegrationBuild` (under CI), `IncludeSymbols` with `snupkg`. The one added analyzer severity is `CS1591 = none` in the generated `.editorconfig`, with its reason there: members of models, exception constructors and enums carry no prose of their own; the real doc-comment defects (CS1573, CS1734, CS0419) were fixed.
- F308: an integer-enum member is PascalCase (`Number1`, `NumberMinus1`) through `toEnumVarName`.
- F860: OIDC discovery is sent as a no-redirect request, so the transport default headers stay off it like the token POST.
- F889, F834, F835, F836, F837, F828: README sections (compression, logging, versioning, optional versus nullable, unknown enum and discriminator, repeated response headers, streaming, Configuration versus TransportOptions), pinned by `MetadataTest`.
- F1161: `DefaultApiClientTest` calls `/test/response-headers` and reads the repeated `Link` and `Set-Cookie` headers.

Already satisfied: F1119 (`MetadataTest.ReadmeSocksNoteNamesTheBuilderSetter`) and F1124 (`PatternConstrainedStringIsAcceptedAndKeptVerbatim` and `StringBeyondItsLengthAndPatternConstraintsRoundTrips` in `ObjectSerializerTest`).

Forced or recorded: F1087: the floor stays `net8.0` (older than the oldest non-EOL runtime, so it is not raised by this rule); .NET 8 left support on 2026-11-10, so raising it to `net10.0` is an owner decision (the net8.0 floor is why the enum converters are hand-written).

Needs the shared generator or the spec, not csharp: F1153 (rule unit tests are Java tests of shared rules), F1172 (one shared spec over every golden), F1174 (`etc/ci-local.sh` and the workflows), F1060 and F1061 (`AbstractBetterCodegen` warnings), F1157 (the petstore spec).

## Gate lessons (what broke the gate and how to avoid it)

- Generated code is verified only by the Docker specs (CSharpBuildSpec, CSharpClientSpec, CSharpStaticAnalysisSpec, CSharpFormattingSpec). Run them with `etc/ci-local.sh check CSharp` before every push, never only a quick check or the build spec; CI takes about 25 minutes and finds what a partial run missed.
- Regeneration reads templates from `target/classes`; a stale copy keeps the old output (an old `public ObjectSerializer`). Run `mvn -q process-resources` before regenerating and diff the golden.
- Fixing one analyzer often trips another, so rerun CSharpStaticAnalysisSpec after every edit: IDE0039 (local function) then IDE0061 (block body); CA1307 on `Replace` needs `StringComparison`; `IndexOf(char) >= 0` trips CA2249, so use `Contains(char, StringComparison.Ordinal)`. Command: `etc/ci-local.sh check CSharp`.
- CA1068 wants `CancellationToken` as the last parameter (a `refreshHeaders` callback goes before it), xUnit1051 wants the test token passed, CA2016 flags forwarding it where it is not needed. Command: `dotnet build -warnaserror` on the golden.
- Use block bodies, never `=>` members (IDE0022), and brace every `if` (IDE0011). Do not enumerate an `IEnumerable` twice (CA1851): materialize to a `List` once.
- Test templates obey the same analyzers: no underscores in method names (CA1707), no inline constant arrays (CA1861), `ReadAsync(Memory)` override (CA1844), null-check public arguments (CA1062), `GC.SuppressFinalize(this)` in `DisposeAsync` (CA1816), no `Skip=` (xUnit1004), no unawaited `ThrowsAsync` (xUnit2021), no `GetResult` (xUnit1031), `[GeneratedRegex]` (SYSLIB1045). Command: `grep -rnE 'Skip\s*=|void [A-Za-z]+_[A-Za-z]+' src/main/resources/templates/csharp`.
- CS0618 under warnaserror: never mark a type or method `[Obsolete]` that generated code itself uses (`PetApi.cs` reads `options.Status`, so mark deprecated options with a doc marker), never use `[Obsolete]` spec types such as `Tag` in test code, and use the image-argument `ContainerBuilder(image)` in Testcontainers 4.12. Command: CSharpBuildSpec after regeneration.
- Do not write `?? throw` on an unconstrained `T?` (CS0019 when `T` is an enum); use `is { } body`. The fixture keeps a bare-enum operation (`getSwatch`) to catch it. Casts such as `int` to `char` must be explicit (CS0266).
- Never emit `// <auto-generated/>`: Roslyn then skips the compiler, analyzers and trimmer for the file. Command: `grep -rl 'auto-generated' <golden>`.
- No `//` comments in generated sources and no HTML entities or bare `&` or generics in XML docs (`&#38;` is the sanctioned escape). Command: `grep -rnE '^\s*//([^/]|$)|&[a-z]+;' src/main/resources/templates/csharp`.
- Do not use `#pragma warning disable`, `NoWarn` or a relaxed csproj to dodge an analyzer; severities live only in the generated `.editorconfig` (`dotnet_diagnostic.CAxxxx.severity`), which the generator owns and `.dockerignore` must not strip. Command: `grep -rnE 'pragma warning disable|NoWarn|WarningsNotAsErrors' src/main/resources/templates/csharp`.
- csharpier wraps class headers and constructor initializers over several lines, and `dotnet format` must agree with it: use traditional constructors, never `: Base(a, b) { }` on one line. Tools that parse generated source (`etc/api-surface.py`) must read formatted output, not template text. Command: `dotnet csharpier --check` then `dotnet format --verify-no-changes`; a crash in `dotnet format analyzers` is not a green run, check its exit code.
- Trimming and AOT: reflection-based JSON fails under `TrimMode=full`; keep the source generator, a parameterless constructor with `required init`, and do not put `required` or `[JsonRequired]` on a defaulted discriminator. net8.0 cannot work with `JsonStringEnumMemberNameAttribute` (.NET 9+) and the image has only the net10 runtime.
- Format values with an invariant converter, never `kv.Value.ToString()` (`DateOnly` gave `01/01/2024`, booleans `True`). `UnsafeRelaxedJsonEscaping` always escapes U+2028/2029 and DEL; use a custom converter when a fixture needs them literal.
- Token tests: `expires_in:0` means stale on the next call, `1` stays fresh under the margin rule. A test that fails on a new fixture is wrong as often as the code: check margins, mock bodies (`ResponseBody = "[]"` for an object operation) and asserted wording first.
- Namespaces move with folders (`grep -r "namespace .*Exceptions"` after a rename); tests read fixtures from `Test/Resources` (copied to output), never from source via `Directory.GetCurrentDirectory()`; hand-written files are pruned unless keep-listed (`git status --short | grep '^ D'` after `make generate`).
- Container death is not a code failure: a vanished container, exit 137 or port clash is infrastructure, so check `docker ps` and `lsof -i :PORT` and rerun that language alone; a spec that passes in about 300 ms or runs 0 tests under xunit.v3/VSTest is a false green, so check the test count. NU1903 warnings from SSH.NET in Testcontainers are not ours.
