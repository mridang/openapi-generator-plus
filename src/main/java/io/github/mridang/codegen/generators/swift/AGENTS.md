# Swift SDK generator

Generates a Swift package (`swift-plus`, class `BetterSwiftCodegen`): `Sources/<Package>/` with models as `Codable` structs, an `async throws` API class per tag, `DefaultApiClient` over `URLSession` (FoundationNetworking on Linux, with a bundled zlib inflater for gzip and deflate), a hand-written `ObjectSerializer`/`ValueSerializer` pair on top of Foundation's `JSONDecoder`/`JSONEncoder`, and the shared error, auth, configuration and transport-options surface. Generated tests use Swift Testing (`import Testing`, run with `swift test --enable-swift-testing --disable-xctest`) plus testcontainers-swift against the shared petstore runtime container; the package also pulls opentelemetry-swift as a test-only dependency. Floor: `swift-tools-version: 6.0`, `swiftLanguageModes: [.v5]` (the authenticator and `AnyCodable` classes are not Sendable-clean), platforms macOS 12, iOS 15, tvOS 15, watchOS 8, built and linted in the `swift:6.2` image pinned by digest in `SwiftSpec`.

## Read first

- Shared contract: [generators/AGENTS.md](../AGENTS.md)
- Repository rules: [AGENTS.md](../../../../../../../../../AGENTS.md)
- Codegen class: [BetterSwiftCodegen.java](BetterSwiftCodegen.java), its unit test `src/test/java/io/github/mridang/codegen/generators/swift/BetterSwiftCodegenTest.java`
- Templates: [src/main/resources/templates/swift/](../../../../../../../resources/templates/swift/)
- Spec classes: `src/spec/java/io/github/mridang/codegen/spec/swift/` (`SwiftSpec`, `SwiftClientSpec`, `SwiftFormattingSpec`, `SwiftLintingSpec`, `SwiftReservedWordsSpec`)
- Golden: `src/spec/resources/generated/swift/`

## Layout

| Path | What lives there | Edit this to change |
|---|---|---|
| `BetterSwiftCodegen.java` | type mappings, reserved words (`/reserved-words/swift.txt`), `Error`/`Result` model prefix, recursive-property flagging, content-parameter flag, server-before-options ordering, supporting-file list, formatter commands | type mapping, file list, model naming, argument order |
| `templates/swift/errors/*.mustache` | `ApiError` hierarchy, `NetworkError`, `NetworkTimeoutError`, `SerializationError`, `ConfigurationError`, `OAuth2*Error` | error classes and messages |
| `templates/swift/auth/`, `auth/oauth/` | scheme authenticators, OAuth2 token manager, OIDC discovery | auth |
| `templates/swift/transport_options.mustache`, `default_api_client.mustache` | `TransportOptions` (proxy, TLS, timeout, redirects, injected `URLSession`) and the `URLSession` transport with its redirect delegate | transport options, redirects, decompression |
| `templates/swift/base_api.mustache`, `api/api.mustache`, `api/options.mustache` | request building (query, form, multipart, body), response decoding, per-operation methods | operation shape and encoding |
| `templates/swift/object_serializer.mustache`, `value_serializer.mustache` | JSON serialisation and parameter styles | wire encoding |
| `templates/swift/models/model.mustache` | structs, unions, `init(from:)`/`encode(to:)` | models |
| `templates/swift/test/**` | generated Swift Testing sources | generated tests |
| `templates/swift/readme.mustache`, `skills.mustache` | README and SKILLS.md | docs |
| `templates/swift/package_swift.mustache`, `makefile.mustache`, `swift_format_json.mustache`, `swiftlint_yml.mustache` | package manifest and tooling config | build and lint settings |

## Idioms (deliberate differences from the other eleven)

| Topic | What swift does | Why |
|---|---|---|
| Errors | error types end in `Error`; caller mistakes throw `ConfigurationError` and `OAuth2AuthorizationCodeError` (plain `Error`, outside the `ApiError` hierarchy); `OAuth2TokenError` is an enum | Swift error idiom; see the shared error contract |
| Wrapped error | `underlyingError` | Foundation's `NSUnderlyingErrorKey` naming |
| Default configuration | `Configuration.default` | `Default`-style static, as in rust |
| Initialisms | `URL`, `ID`, `HTTP` upper case | Swift API Design Guidelines |
| Union accessor | `actualInstance` plus the generic `actualInstance(as:)` overload | the type witness is a metatype |
| Async | `async throws` folds sync and async into one method | keyword-coloured async |
| Cancellation | native `Task` cancellation; a cancelled request throws `CancellationError`, never a timeout error | S7, no new parameter |
| `retryAfter` | `TimeInterval` seconds, wire form `"3600s"` via `ISO8601Duration` helpers | Foundation has no protobuf-duration type |
| Decimal and `format: number` | `Double`, documented lossy in the README | `JSONDecoder` parses numbers to `Double` |
| `format: date`, `time`, `uri` | `String` | Foundation has no civil date or time type |
| Recursive models | `@Indirect` wrapper (`Indirect.swift`) | structs are value types |
| `object` and any | `AnyCodable` | `Codable` has no `Any` |
| Content-Encoding | never advertised, never raised on an unknown coding | URLSession negotiates and decodes |
| Injected transport | `TransportOptions.session(_ URLSession?)` | S9 |
| Forbidden model names | `Error`, `Result` become `ModelError`, `ModelResult` | they shadow `Swift.Error` and `Swift.Result` |
| Property `type` | stays `type`, not `_type` | the reserved list holds the capital-T metatype member only |
| Comments | `/* */` for implementation comments, `///` for documentation | repository rule `NoBlockComments` |

## Settled decisions (do not re-open in review)

### Server before options (S8, F803, 2026-11-16)
Rule: operation arguments are required parameters, then server, then the options object, always, whether or not the operation has security.
Why: adding security must never move an argument; owner confirmation is still pending, so treat as settled-in-code only.
Where: `BetterSwiftCodegen.populateOperationDecorators` and `serverBeforeOptions`.
Test: `BetterSwiftCodegenTest` (`serverComesBeforeOptions`, `listsWithoutBothAreUnchanged`, `alreadyOrderedListIsUnchanged`).

### Injected URLSession (S9, F405, 2026-11-16)
Rule: `TransportOptions.session(_:)` takes a caller-owned `URLSession`; the SDK keeps applying its defaults, never invalidates it, and fails fast on conflicts with its own proxy, TLS and pool settings.
Why: every major generated SDK accepts the platform's standard client.
Where: `transport_options.mustache`, `default_api_client.mustache` (`init(transportOptions:session:)`).
Test: `TransportOptionsTests`, `DefaultApiClientUnitTests` templates.

### Content parameters through the object serializer (S2, F054, F068, D50, D80, 2026-11-16)
Rule: a parameter declared with a JSON `content:` entry is encoded with `try ObjectSerializer.serialize` in path, query, header and cookie, and a failure throws instead of being dropped.
Why: one set of bytes in all twelve; the old `try?` silently omitted the parameter.
Where: `markContentParameters` sets `vendorExtensions.swiftContent`; `api/api.mustache`.
Test: `ValueSerializerTests` and `PetApiTests` templates.

### Batch 5 (F824, F831, F833, F839)
Rule: the `Client` facade has `close()`, which closes the `DefaultApiClient` it built and never a session supplied through `TransportOptions.session`; HTTP 429 maps to `TooManyRequestsError` (a `ClientError`); a `content:` cookie is percent-encoded byte-wise keeping only `A-Za-z0-9-._~` (`ValueSerializer.encodeCookieValue`); a redirect sets its method by `DefaultApiClient.redirectMethod` (RFC 9110 15.4) and restores the body for a kept method.
Test: `ClientTests`, `BaseApiTests`, `ApiErrorTests`, `ValueSerializerTests`, `DefaultApiClientUnitTests` templates.

### Cancellation (S7, F228, 2026-11-16)
Rule: no cancellation parameter; cancelling the calling `Task` aborts the request and surfaces `CancellationError`, which the SDK never rewraps.
Why: `Task` cancellation is the native handle in Swift.
Where: `default_api_client.mustache` (`classify` and the `CancellationError` rethrow).
Test: `DefaultApiClientUnitTests` template.

### Required plus nullable (S5, F215, D66, 2026-11-16)
Rule: a required nullable property decodes with `decode(T?.self)` so a missing key is an error and an explicit `null` is kept; encode writes the key as `null`.
Why: it is the only reading that honours both `required` and `nullable`.
Where: `models/model.mustache`.
Test: `MetadataTests` and `ComposedSchemaTests` templates. Intended: the encode half is not visible for every property shape; confirm against the golden before relying on it.

### Credential blankness, scope order, UUID case, error cause (S14, 2026-11-16)
Rule: an API key or bearer token is blank only when empty or made of the six ASCII whitespace bytes (`0x20 0x09 0x0A 0x0B 0x0C 0x0D`); declared OAuth scope order is kept (`formatSwiftScopes`); UUIDs are written lowercase (`uuidString.lowercased()`); every status error accepts an optional `cause`.
Why: one behaviour in all twelve.
Where: `auth/api_key_authenticator.mustache`, `auth/bearer_authenticator.mustache`, `BetterSwiftCodegen.formatSwiftScopes`, `models/model.mustache`, `object_serializer.mustache`, `errors/api_error.mustache`.
Test: `ApiKeyAuthenticatorTests`, `BearerAuthenticatorTests`, `ObjectSerializerTests` templates. The ordering of scopes is intended; no swift-specific test was confirmed.

### Secret redaction in string forms (S15, 2026-11-16)
Rule: the string forms of `Configuration` and `TransportOptions` must show the proxy password and sensitive default headers as `***`.
Why: logging a configuration must not leak credentials.
Where: `configuration.mustache`, `transport_options.mustache`. Intended: no redaction code was found in the templates when this file was written.
Test: none yet.

### Timeouts, redirects, CA path (S16, D73, F295, D76, F094, 2026-11-16)
Rule: one `timeout` arms both `timeoutIntervalForRequest` and `timeoutIntervalForResource` (the end-to-end budget); either expiry is a `NetworkTimeoutError`; a caller stream is never closed; `caCertPath` is validated even with `verifySsl` false.
Why: URLSession has two deadlines and only the pair gives one budget.
Where: `default_api_client.mustache` (`buildSession`).
Test: `DefaultApiClientUnitTests` template (asserts both deadlines and the timeout classification). Proxy URL validation in `build()` rather than in the setter is intended for swift and not confirmed.

### Strict containers (S1, F006, 2026-11-16)
Rule: a response that is a container of primitives is checked element by element like a single primitive.
Why: one behaviour in all twelve. Intended: not confirmed in `object_serializer.mustache`; F006 is still parked.
Where: `object_serializer.mustache`, `base_api.mustache`.
Test: `ObjectSerializerTests`, `BaseApiTests` templates (to add).

### allOf with a discriminator (S4, F057, F802, 2026-11-16)
Rule: a protocol for the common surface plus an enum with associated values and a hand-written decoder that reads the discriminator; no re-parenting of a model reused in two unions.
Why: structs cannot inherit. Intended: the template still has the older discriminator helper (`DiscriminatorHelper`) and no protocol; this is not yet landed.
Where: `models/model.mustache`.
Test: `ComposedSchemaTests` template.

### No schema-constraint enforcement and unspecified uniqueItems order (S3 F056, S6 F319)
Rule: the client does not enforce `pattern`, length, range, `multipleOf` or item-count constraints (README section on constraint validation); `Set` members go out in Swift's natural order and are not sorted.
Why: the server owns validation; sorting a hash set would invent an order the schema never promised. This supersedes the earlier proposal D69 (sorted) and D55.
Where: `readme.mustache`, `models/model.mustache`.
Test: none by design.

### Out of scope features (S11, closed issues #53, #70, #78, #88, #90)
Rule: callbacks and webhooks, XML bodies, Link Objects and mutual TLS are not generated, and the README says so (webhooks and callbacks section; SOCKS section).
Why: closed backlog decisions.
Where: `readme.mustache`.
Test: none.

### OIDC and OAuth token flow (D01, D02, D03, D05, D10)
Rule: the token manager owns the exchanged flag, `exchange(tokenURL:params:extraHeaders:)` replaces the whole session, `getAccessToken(...refreshURL:)` redeems a stored refresh token with client authentication, OIDC always requests `openid`, and token-request bodies are ordered `grant_type, code, refresh_token, username, password, client_id, client_secret, redirect_uri, scope`, then other keys alphabetically.
Why: identical wire bytes across the twelve; an `NSLock` cannot be held across `await`, so discovery single-flight is a lock-guarded in-flight `Task`.
Where: `auth/oauth/oauth2_token_manager.mustache`, `openid_connect_authenticator.mustache`, `oauth2_auth_code_authenticator.mustache`.
Test: `OAuth2TokenManagerTests`, `OAuth2AuthCodeAuthenticatorTests`, `OpenIdConnectAuthenticatorTests` templates. Intended for D01: the manager still holds only `inflightRefresh`; discovery single-flight and the exchanged flag in the manager were not confirmed.

### Token numbers and expiry (D08, R8 sweep)
Rule: `expires_in` and discovery max-age are capped at one year (`31_536_000`) before date arithmetic; a refresh response without `expires_in` keeps the known expiry.
Why: a refresh without `expires_in` must not cache a token forever.
Where: `oauth2_token_manager.mustache`, `openid_connect_authenticator.mustache`.
Test: `OAuth2TokenManagerTests` template.

### Request body, multipart and form encoding (D35, D36, D39, D48, F087 partial)
Rule: a `format: binary` body (`rawBody`) is sent verbatim whatever the Content-Type; multipart primitives (Bool, numbers, Date, UUID, enums) are plain text parts using `ObjectSerializer.stringify` and only models become JSON parts; a form-urlencoded field that is a model, map or array of objects throws `SerializationError`; a simple-style path value of `.` or `..` throws `ConfigurationError.invalidArgument`.
Why: one rule in all twelve.
Where: `base_api.mustache` (`isMultipartPrimitive`, `buildContentDisposition`), `value_serializer.mustache` (`requirePathParam`), `api/api.mustache`.
Test: `BaseApiTests`, `ValueSerializerTests` templates.

### Per-operation servers and request headers (D21, D42)
Rule: a server variable name used by two or more variants of one operation is prefixed with the variant name (petstore: `RegionalRegion`, `MirrorRegion`, `Channel`); a caller's `User-Agent` or `X-Request-ID` in any case suppresses injection.
Why: unique types; the caller's header wins.
Where: `api/api.mustache`, `BetterSwiftCodegen`/`AbstractBetterCodegen.enrichOperationServers`, `default_api_client.mustache` (`caseInsensitiveCompare`).
Test: `DefaultApiClientUnitTests` template (`DefaultApiClientTests` is `#if !os(Linux)` and does not run in the spec).

### Ambient proxy and Linux proxy (D74, F090)
Rule: on Apple platforms `connectionProxyDictionary = [:]` disables system proxies unless `TransportOptions.proxy` is set; on Linux setting a proxy throws `ConfigurationError.proxyUnsupported` because FoundationNetworking cannot apply one.
Why: one proxy policy; the Linux half is forced by the platform.
Where: `default_api_client.mustache` (`buildSession`).
Test: `DefaultApiClientUnitTests`, `TransportOptionsTests` templates.

### Model naming and reserved words (D45, D44, D43)
Rule: `Error` and `Result` get the `Model` prefix in the model name and in `$ref` types; `type` is never escaped; a digit-leading name gets an `n` prefix (shared base).
Why: a public `Error` struct would shadow `Swift.Error` for the whole module and break every error type.
Where: `BetterSwiftCodegen.toModelName`, `getSchemaType`, `shouldEscapeReservedVarName`.
Test: `SwiftReservedWordsSpec`.

### Equality on unions and additionalProperties init (D24, R7)
Rule: models and unions are `Equatable, Hashable, Sendable`; the memberwise init of an additionalProperties model includes the declared optional fields.
Why: same value equality as plain models.
Where: `models/model.mustache`.
Test: `MetadataTests`, `ComposedSchemaTests` templates.

### Deprecation, required-parameter text, field examples
Rule: every public variant of a deprecated operation carries `@available(*, deprecated, ...)`; the missing-parameter message reads `Missing the required parameter '...'`; model field docs carry `/// Example:` and descriptions go through `unescapeDocComment`.
Why: R6 and R7 harmonisation; the synthesised `"null"` example is nulled in the shared base, so do not re-add a guard.
Where: `api/api.mustache`, `value_serializer.mustache`, `models/model.mustache`.
Test: `ValueSerializerTests`, `SwiftFormattingSpec`.

### HTTPS to HTTP downgrade guard anchors on the current hop (R7)
Rule: the body-replay guard compares `task.currentRequest` with the next hop, not `task.originalRequest`; cross-origin sensitive-header stripping still uses the original URL.
Why: an http to https to http chain downgrades on the last hop.
Where: `default_api_client.mustache` (redirect delegate).
Test: `DefaultApiClientUnitTests` template.

### Decompression and Content-Encoding (forced)
Rule: swift advertises no `Accept-Encoding` and never raises on an unknown `Content-Encoding`; an empty body passes through for any declared coding; a residual gzip header is tolerated because Foundation on Linux leaves it after decoding.
Why: URLSession owns negotiation, so an unknown but already-decoded body cannot be told from an encoded one. Do not make swift raise.
Where: `default_api_client.mustache` (decompress block).
Test: `DefaultApiClientUnitTests` template.

### OAuth2 percent-encoding residual (forced, R8)
Rule: the authorize URL is built with `URLComponents`/`URLQueryItem`, so it keeps `* ! ' ( )` literal and a literal `+`; the token body and Basic header use the canonical set.
Why: re-encoding by hand would risk the parsed-query behaviour; latent, no fixture uses those characters.
Where: `auth/oauth/oauth2_auth_code_authenticator.mustache`.
Test: `OAuth2AuthCodeAuthenticatorTests` template.

### Non-string token fields hard-fail (forced, borderline)
Rule: a numeric `access_token` or `refresh_token` fails the whole token decode, where the dynamic-parse SDKs skip it.
Why: the typed `Decodable` response; changing it means dynamic parsing, deferred.
Where: `oauth2_token_manager.mustache`.
Test: none.

### allowReserved and `[` `]` (D49, D78, forced exception)
Rule: the always-literal set is `:/?@!$&'()*+,;=` plus `~`; `#`, `[` and `]` are percent-encoded by the SDK, and Foundation additionally rewrites `[` and `]` to `%5B` `%5D`, which is documented in `ValueSerializer.AllowReservedValue`.
Why: Foundation `URL` re-encodes; the encoder output is the contract.
Where: `base_api.mustache`, `value_serializer.mustache`.
Test: `ValueSerializerTests`, `BaseApiTests` templates (Linux wire test).

### JSON encoding and decoding limits (D82, D84, D85, D86, D87, forced)
Rule: request JSON keys are sorted (`.sortedKeys`), slashes are not escaped, `format: double` is judged by JSON number equality not bytes, `JSONDecoder` refuses nesting beyond about 512 levels and keeps the first duplicate key, and a `Set` parameter expands like an array.
Why: `JSONEncoder` has no declared-order mode and `JSONDecoder` is not configurable; the SDK's own 1000-level cap still applies first.
Where: `object_serializer.mustache` (comments at the top), `value_serializer.mustache`.
Test: `ObjectSerializerTests` template.

### Date-time leniency (D57, F307 open)
Rule: `date-time` decoding accepts RFC 3339 with an offset and also a bare `yyyy-MM-dd`; this is kept, listed as an exception, and not forced by Foundation.
Why: removing the `parseDate` fallback is possible but has not been decided, so swift is the one lenient language.
Where: `object_serializer.mustache` (`parseDate`).
Test: `ObjectSerializerTests` template.

### Dual multipart builder (forced, F485)
Rule: `BaseApi` and `DefaultApiClient` both carry `buildMultipartBody`/`appendMultipartField` and share `BaseApi.isMultipartPrimitive`; do not dedupe.
Why: the helpers are interdependent, the tests call `DefaultApiClient`'s, and `BaseApi`'s are `private`; no wire impact.
Where: `base_api.mustache`, `default_api_client.mustache`.
Test: `BaseApiTests`, `DefaultApiClientUnitTests` templates.

### Metadata-only differences kept as is (forced)
Rule: `ServerConfiguration`/`ServerVariable` optional `description` is a non-nullable empty string; `responseHeaders` is `nil` when no response arrived and an empty map when one arrived without headers; `swiftlint` is committed but not run by CI.
Why: no wire effect; the null versus empty distinction is the transport-failure signal; there is no Linux SwiftLint binary, and the gate is `swift-format lint --strict` plus `swift build -warnings-as-errors`.
Where: `servers.mustache`, `errors/api_error.mustache`, `swiftlint_yml.mustache`, `SwiftLintingSpec`.
Test: `ApiErrorTests` template, `SwiftLintingSpec`.

### Generated tests and readme (R7, F193)
Rule: `README` floor tracks `Package.swift`; `TESTCONTAINERS_RYUK_DISABLED=true` is set by the shared runtime container; a `{{! }}` mustache comment never contains a literal tag.
Why: an understated floor advertises a version the manifest rejects; a missing Ryuk image failed swift once.
Where: `readme.mustache`, `package_swift.mustache`, `SharedRuntimeContainer`.
Test: `SwiftClientSpec`.

## Known limitations and out of scope

- Linux has no proxy support (`ConfigurationError.proxyUnsupported`) and no CA pin; SOCKS proxies are rejected by design (README). Reason: FoundationNetworking.
- `int` and `integer` map to `Int`, not `Int32`, so a value above `Int32` range is accepted for `int32` fields and an integral-looking `1.0` is accepted (D54, F006, F076; the API-breaking `Int32` mapping was proposed and not applied).
- Query, form and multipart parameters are held in `[String: ...]` dictionaries, so their wire order is not the spec order and multipart order is nondeterministic (D53, F087, parked).
- Not applied yet, intended per the ledger: lenient `LocalizedError` text on `OAuth2AuthorizationCodeError` (D09), the redirect-refusal wording of D16, the `timeout(_:) throws` validation (D77, `invalidTimeout`), proxy and CA error message grammar (D79), duplicate-key and required-default handling in `init(from:)` for D62 and D63, and the `Content-Encoding` header removal of D92.
- Declared-charset decoding is lenient UTF-8 with a fallback; windows-1252, shift_jis and iso-8859-x bodies can decode as mojibake (F258, open for swift).
- Generated tests compare some non-optional values to `nil` (about 100 sites, F525) and the required date parameter guard has no petstore fixture (F433). `swift build -strict-concurrency=complete` (`make analyse`) is run by no spec (F382); test sources get weaker strictness than sources (F193, deferred).
- The API surface harmonisation of `ConfigurationError` is parked (F699).
- Out of scope: webhooks, callbacks, XML, Link Objects, mutual TLS (S11). OpenAPI 3.1 and 3.2 are tracked by epics #96 and #49; loading 3.2 is #50.

## Build-breaking rules

- Generated sources must not contain `//` implementation comments; use `/* */`, and `///` for documentation (the formatting spec fails the build otherwise).
- A mustache `{{! }}` comment must not contain a literal `{{tag}}`: jmustache ends it at the first `}}`.
- `swift build -warnings-as-errors` is on in debug (deprecation warnings are downgraded with `-Wwarning DeprecatedDeclaration` because models reference their own deprecated members).
- `swift-format lint --strict --recursive Sources Tests` must pass; the formatter needs `swift:6.2` (6.0 cannot parse the trailing commas the templates emit) and a git repository in the working directory, which `getFormatterCommands` creates and removes.
- Do not read private `BaseApi` helpers from tests; tests that call `DefaultApiClient` helpers must live in `DefaultApiClientUnitTests` because `DefaultApiClientTests` is `#if !os(Linux)`.
- Golden regeneration runs Docker formatters; commit all twelve goldens current, never a subset.

## Verify

- `etc/ci-local.sh check Swift`
- `etc/ci-local.sh check --quick Swift` (no integration specs)
- `mvn verify -Dgroups=swift`
- Inside a generated client: `swift build`, `swift-format lint --strict --recursive Sources Tests`, `swift test --parallel --enable-swift-testing --disable-xctest`.
