# Swift SDK generator

Generates a Swift package (`swift-plus`, class `BetterSwiftCodegen`): `Sources/<Package>/` with models as `Codable` structs, an `async throws` API class per tag, `DefaultApiClient` over `URLSession` (FoundationNetworking on Linux, with a bundled zlib inflater for gzip and deflate), a hand-written `ObjectSerializer`/`ValueSerializer` pair on top of Foundation's `JSONDecoder`/`JSONEncoder`, and the shared error, auth, configuration and transport-options surface. Generated tests use Swift Testing (`import Testing`, run with `swift test --enable-swift-testing --disable-xctest`) plus testcontainers-swift against the shared petstore runtime container; the package also pulls opentelemetry-swift as a test-only dependency. Floor: `swift-tools-version: 6.0`, `swiftLanguageModes: [.v5]` (the authenticator and `AnyCodable` classes are not Sendable-clean), platforms macOS 13, iOS 16, tvOS 16, watchOS 9 (the first with the standard library `Duration`, D145), built and linted in the `swift:6.2` image pinned by digest in `SwiftSpec`.

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
| `templates/swift/byte_stream.mustache`, `api_stream_response.mustache`, `stream_transfer.mustache`, `errors/stream_error.mustache` | `ByteStream` (the binary response carrier), `ApiStreamResponse` (the transport result with an unread body), `StreamTransfer` (the `URLSessionDataDelegate` that feeds it, with its idle timer and flow control), `StreamError` | streaming types |
| `templates/swift/event_stream.mustache`, `json_lines_stream.mustache`, `sse_stream.mustache`, `server_sent_event.mustache`, `stream_options.mustache` | The line reader and SSE parser, `JsonLinesStream<T>`, `SseStream<T>`, `ServerSentEvent<T>`, `StreamOptions` | JSON Lines and server-sent events |
| `templates/swift/object_serializer.mustache`, `value_serializer.mustache` | JSON serialisation and parameter styles | wire encoding |
| `templates/swift/models/model.mustache` | structs, unions, `init(from:)`/`encode(to:)` | models |
| `templates/swift/test/**` | generated Swift Testing sources | generated tests |
| `templates/swift/call_hook.mustache`, `call_info.mustache`, `call_outcome.mustache`, `call_hook_recipe.mustache` | The call hook types, and the OpenTelemetry recipe partial shared by the README and `test/CallHookRecipeTests.mustache` | the hook API, the recipe |
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
| `format: duration` (`retryAfter`) | standard library `Duration`, wire form `"3600s"` via the `ISO8601Duration` helpers; `stringify` and the form and multipart encoders treat it as a scalar | D145: the native type, exact to the nanosecond |
| Decimal and `format: number` | `Double`, documented lossy in the README | `JSONDecoder` parses numbers to `Double` |
| `format: date`, `time`, `uri` | `String` | Foundation has no civil date or time type |
| Recursive models | `@Indirect` wrapper (`Indirect.swift`) | structs are value types |
| `object` and any | `AnyCodable` | `Codable` has no `Any` |
| Content-Encoding | never advertised, never raised on an unknown coding | URLSession negotiates and decodes |
| Injected transport | `TransportOptions.session(_ URLSession?)` | S9 |
| Forbidden model names | `Error`, `Result` become `ModelError`, `ModelResult` | they shadow `Swift.Error` and `Swift.Result` |
| Property `type` | stays `type`, not `_type` | the reserved list holds the capital-T metatype member only |
| Integer-enum members | `number1`, `numberMinus1` | lowerCamel like the rest of Swift; string-enum cases stay verbatim |
| Model copy | `copy(...)` with `T??` for an optional property, `.some(nil)` clears | structs have no builder; a bare `nil` also means keep |
| Call hook and telemetry | `CallHook`, `CallObserver` (both `Sendable` protocols), `CallInfo`, `CallOutcome` and `CallHooks.chain` (`call_hook`, `call_info`, `call_outcome`), run by a private `CallRun` in `base_api.mustache`; the hook is `Configuration.callHook` and the `callHook:` argument of every `Client` initializer and factory; no OpenTelemetry adapter, only a README recipe (`call_hook_recipe.mustache`) that `test/CallHookRecipeTests.mustache` includes, so the text a user copies is the text under test | `start` and `finish` are `throws` (an error from `start` must reach the caller, one from `finish` is swallowed); SwiftPM has no optional dependency and the opentelemetry-swift metrics API (generic, with no unit) cannot name `{request}` or `s`, so the recipe passes the count and the duration to closures the user supplies. The headers dictionary is built once per call and reused by every send and the replay after a 401, whose fresh authentication headers replace a hook header of the same name, so no delta is kept |
| Comments | `/* */` for implementation comments, `///` for documentation | repository rule `NoBlockComments` |

## Settled decisions (do not re-open in review)

### Server before options (S8, 2026-11-16)
Rule: operation arguments are required parameters, then server, then the options object, always, whether or not the operation has security.
Why: adding security must never move an argument; owner confirmation is still pending, so treat as settled-in-code only.
Where: `BetterSwiftCodegen.populateOperationDecorators` and `serverBeforeOptions`.
Test: `BetterSwiftCodegenTest` (`serverComesBeforeOptions`, `listsWithoutBothAreUnchanged`, `alreadyOrderedListIsUnchanged`).

### Injected URLSession (S9, 2026-11-16)
Rule: `TransportOptions.session(_:)` takes a caller-owned `URLSession`; the SDK keeps applying its defaults, never invalidates it, and fails fast on conflicts with its own proxy, TLS and pool settings.
Why: every major generated SDK accepts the platform's standard client.
Where: `transport_options.mustache`, `default_api_client.mustache` (`init(transportOptions:session:)`).
Test: `TransportOptionsTests`, `DefaultApiClientUnitTests` templates.

### Content parameters through the object serializer (S2, D50, D80, 2026-11-16)
Rule: a parameter declared with a JSON `content:` entry is encoded with `try ObjectSerializer.serialize` in path, query, header and cookie, and a failure throws instead of being dropped.
Why: one set of bytes in all twelve; the old `try?` silently omitted the parameter.
Where: `markContentParameters` sets `vendorExtensions.swiftContent`; `api/api.mustache`.
Test: `ValueSerializerTests` and `PetApiTests` templates.

### Batch 5
Rule: the `Client` facade has `close()`, which closes the `DefaultApiClient` it built and never a session supplied through `TransportOptions.session`; HTTP 429 maps to `TooManyRequestsError` (a `ClientError`); a `content:` cookie is percent-encoded byte-wise keeping only `A-Za-z0-9-._~` (`ValueSerializer.encodeCookieValue`); a redirect sets its method by `DefaultApiClient.redirectMethod` (RFC 9110 15.4) and restores the body for a kept method.
Test: `ClientTests`, `BaseApiTests`, `ApiErrorTests`, `ValueSerializerTests`, `DefaultApiClientUnitTests` templates.

### Cancellation (S7, 2026-11-16)
Rule: no cancellation parameter; cancelling the calling `Task` aborts the request and surfaces `CancellationError`, which the SDK never rewraps.
Why: `Task` cancellation is the native handle in Swift.
Where: `default_api_client.mustache` (`classify` and the `CancellationError` rethrow).
Test: `DefaultApiClientUnitTests` template.

### Required plus nullable (S5, D66, 2026-11-16)
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

### Timeouts, redirects, CA path (S16, D73, D76, 2026-11-16)
Rule: one `timeout` arms both `timeoutIntervalForRequest` and `timeoutIntervalForResource` (the end-to-end budget); either expiry is a `NetworkTimeoutError`; a caller stream is never closed; `caCertPath` is validated even with `verifySsl` false.
Why: URLSession has two deadlines and only the pair gives one budget.
Where: `default_api_client.mustache` (`buildSession`).
Test: `DefaultApiClientUnitTests` template (asserts both deadlines and the timeout classification). Proxy URL validation in `build()` rather than in the setter is intended for swift and not confirmed.

### Strict containers (S1, 2026-11-16)
Rule: a response that is a container of primitives is checked element by element like a single primitive.
Why: one behaviour in all twelve. `JSONDecoder` already decodes a container element by element with the scalar rules, so the rule holds without extra code; see wave 2.
Where: `object_serializer.mustache`, `base_api.mustache`.
Test: `ObjectSerializerTests`, `BaseApiTests` templates (to add).

### allOf with a discriminator (S4, 2026-11-16)
Rule: a protocol for the common surface plus an enum with associated values and a hand-written decoder that reads the discriminator; no re-parenting of a model reused in two unions.
Why: structs cannot inherit. Status (#104): the classic base is realised as the discriminated union wrapper (`BetterSwiftCodegen.postProcessAllModels` gives the base its mapped subtypes as oneOf and typed variants, and the union's `DiscriminatorHelper` branch dispatches and rejects unknown or missing values), with no protocol; subtypes stay flat structs whose discriminator defaults to its value. Tests: `unions301`-`unions304`, `serializationobject301`.
Where: `models/model.mustache`, `BetterSwiftCodegen.postProcessAllModels`.
Test: `ComposedSchemaTests` template.

### No schema-constraint enforcement (S3); uniqueItems order is sorted (S6 reversed)
Rule: the client does not enforce `pattern`, length, range, `multipleOf` or item-count constraints (README section on constraint validation); `Set` members of strings or numbers are written sorted, as the other SDKs do (`ObjectSerializer.ordered`).
Why: the server owns validation; a hash order is seeded per process, so it would put different bytes on the wire for one value.
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
Rule: `expires_in` and discovery max-age are capped at one year (`31_536_000`) before date arithmetic; a response without `expires_in` clears the expiry (see Decisions taken in batch 7).
Why: a refresh without `expires_in` leaves the token cached until it is invalidated, exactly like the first fetch.
Where: `oauth2_token_manager.mustache`, `openid_connect_authenticator.mustache`.
Test: `OAuth2TokenManagerTests` template.

### Request body, multipart and form encoding (D35, D36, D39, D48,  partial)
Rule: a `format: binary` body (`rawBody`) is sent verbatim whatever the Content-Type; multipart primitives (Bool, numbers, Date, UUID, enums) are plain text parts using `ObjectSerializer.stringify` and only models become JSON parts; a form-urlencoded field that is a model, map or array of objects throws `SerializationError`; a simple-style path value of `.` or `..` throws `ConfigurationError.invalidArgument`. D165: a form-urlencoded field whose Encoding Object sets `style` or `explode` (`formStyled`) is styled in the operation prelude by `ValueSerializer.serializeStyled` as for a query parameter, then form-encoded (a joined value is one field, an exploded list repeats the key). D166: a multipart field whose Encoding Object declares a part Content-Type or part headers (`partEncoded`) is wrapped in `ValueSerializer.EncodedPart`, and `BaseApi.appendMultipartField` writes the declared Content-Type in place of the chosen one and then each header in declaration order; every other reader of the form map unwraps it through `ValueSerializer.unwrapFormField`.
Why: one rule in all twelve.
Where: `base_api.mustache` (`isMultipartPrimitive`, `buildContentDisposition`), `value_serializer.mustache` (`requirePathParam`), `api/api.mustache`.
Test: `BaseApiTests`, `ValueSerializerTests` templates; D165 and D166 in `PetApiTests` (`serializationwire502`, `serializationwire503`).

### Per-operation servers and request headers (D21, D42)
Rule: a server variable name used by two or more variants of one operation is prefixed with the variant name (petstore: `RegionalRegion`, `MirrorRegion`, `Channel`); a caller's `User-Agent` or `X-Request-ID` in any case suppresses injection.
Why: unique types; the caller's header wins.
Where: `api/api.mustache`, `BetterSwiftCodegen`/`AbstractBetterCodegen.enrichOperationServers`, `default_api_client.mustache` (`caseInsensitiveCompare`).
Test: `DefaultApiClientUnitTests` template (the proxy and TLS tests that need Apple's URLSession sit in `DefaultApiClientAppleStubTests`, `#if !os(Linux)`).

### Redirect rules are the SDK's on every platform (swift-corelibs differences)
Rule: the SDK follows only 301, 302, 303, 307 and 308 (a 300 or 305 is the response), resolves the `Location` itself against the URL of the response (RFC 3986) instead of taking the request URLSession proposes, and drops the transport default headers on a cross-origin hop together with the credential headers. The body of each request is kept per task, so a redirect that keeps its method sends it again. On Linux a 307 or 308 that keeps its method and has a body is not followed by URLSession: the delegate records the follow-up and `sendOnce` sends it as a new task that carries the hop count.
Why: swift-corelibs follows a 305, resolves a relative `Location` against the host only (`../x` and `?page=2` lose their path), turns a non-HTTP(S) `Location` into a path on the same host, and clears the body of the task when it follows a redirect (the next transfer starts from the task's known body, not from the request the delegate returns), so a followed 307 sent an empty body. It also reads a redirect's whole body before it asks the delegate.
Where: `default_api_client.mustache` (`SessionDelegate`, `sendOnce`, `performDataTask`).
Test: `DefaultApiClientRedirectTests` template, over `LoopbackServer`; a change of scheme between https and http is put to the delegate directly, because the loopback server speaks plain HTTP.

### Ambient proxy and Linux proxy (D74)
Rule: on Apple platforms `connectionProxyDictionary = [:]` disables system proxies unless `TransportOptions.proxy` is set; on Linux setting a proxy throws `ConfigurationError.invalidProxy` (text `Proxy configuration is not supported on this platform`) because FoundationNetworking cannot apply one.
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

### Non-string token fields (batch 7, supersedes the old hard-fail)
Rule: the token response is parsed dynamically; a numeric `access_token` is a missing one and a numeric `refresh_token` is skipped.
Why: the same reading as the dynamic-parse SDKs.
Where: `oauth2_token_manager.mustache`.
Test: `OAuth2TokenManagerTests` template (`testTokenFailuresCarryTheCanonicalMessage`).

### allowReserved and `[` `]` (D49, D78, forced exception)
Rule: the always-literal set is `:/?@!$&'()*+,;=` plus `~`; `#`, `[` and `]` are percent-encoded by the SDK, and Foundation additionally rewrites `[` and `]` to `%5B` `%5D`, which is documented in `ValueSerializer.AllowReservedValue`.
Why: Foundation `URL` re-encodes; the encoder output is the contract.
Where: `base_api.mustache`, `value_serializer.mustache`.
Test: `ValueSerializerTests`, `BaseApiTests` templates (Linux wire test).

### JSON encoding and decoding limits (D82, D84, D85, D86, D87, forced)
Rule: request JSON keys of a model are written in declaration order (the encoder sorts them, so a position prefix on each `CodingKeys` key carries the order and `removeOrderMarks` strips it; a map's keys stay sorted) and a `Set` of strings or numbers is written sorted, slashes are not escaped, `format: double` is judged by JSON number equality not bytes, `JSONDecoder` refuses nesting beyond about 512 levels and keeps the first duplicate key, and a `Set` parameter expands like an array.
Why: `JSONEncoder` has no declared-order mode and `JSONDecoder` is not configurable; the SDK's own 1000-level cap still applies first.
Where: `object_serializer.mustache` (comments at the top), `value_serializer.mustache`.
Test: `ObjectSerializerTests` template.

### Date-time leniency (D57, open)
Rule: `date-time` decoding accepts RFC 3339 with an offset and also a bare `yyyy-MM-dd`; this is kept, listed as an exception, and not forced by Foundation.
Why: removing the `parseDate` fallback is possible but has not been decided, so swift is the one lenient language.
Where: `object_serializer.mustache` (`parseDate`).
Test: `ObjectSerializerTests` template.

### Dual multipart builder (forced)
Rule: `BaseApi` and `DefaultApiClient` both carry `buildMultipartBody`/`appendMultipartField` and share `BaseApi.isMultipartPrimitive`; do not dedupe.
Why: the helpers are interdependent, the tests call `DefaultApiClient`'s, and `BaseApi`'s are `private`; no wire impact.
Where: `base_api.mustache`, `default_api_client.mustache`.
Test: `BaseApiTests`, `DefaultApiClientUnitTests` templates.

### Metadata-only differences kept as is (forced)
Rule: `ServerConfiguration`/`ServerVariable` optional `description` is a non-nullable empty string; `responseHeaders` is `nil` when no response arrived and an empty map when one arrived without headers; `swiftlint` is committed but not run by CI.
Why: no wire effect; the null versus empty distinction is the transport-failure signal; there is no Linux SwiftLint binary, and the gate is `swift-format lint --strict` plus `swift build -warnings-as-errors`.
Where: `servers.mustache`, `errors/api_error.mustache`, `swiftlint_yml.mustache`, `SwiftLintingSpec`.
Test: `ApiErrorTests` template, `SwiftLintingSpec`.

### Generated tests and readme (R7)
Rule: `README` floor tracks `Package.swift`; `TESTCONTAINERS_RYUK_DISABLED=true` is set by the shared runtime container; a `{{! }}` mustache comment never contains a literal tag.
Why: an understated floor advertises a version the manifest rejects; a missing Ryuk image failed swift once.
Where: `readme.mustache`, `package_swift.mustache`, `SharedRuntimeContainer`.
Test: `SwiftClientSpec`.

### Decisions taken in batch 6
Rule: every `ApiError` for a response that arrived carries the real lower-cased headers: the three redirect refusals (hop cap, non-HTTP(S) Location, HTTPS to HTTP body replay) and the undecodable-body error use `DefaultApiClient.lowercasedHeaders(of:)`, the same helper the normal path uses (D13); `nil` still means no response arrived. `OAuth2AuthorizationCodeError` is a `LocalizedError` whose `emptyCode` text is `Authorization code must not be empty` (D09); the use-after-close text is pinned.
Why: audit proposals, taken as the default; no wire effect.
Where: `default_api_client.mustache`, `errors/oauth2_authorization_code_error.mustache`.
Test: `DefaultApiClientRedirectTests` (hop-cap and scheme refusals assert the `location` header; the HTTPS to HTTP downgrade is put to the delegate directly), `OAuth2AuthCodeAuthenticatorTests`.
Generated tests: a comparison of a non-optional value with `nil` is written `_ = value` or dropped, an `is` check that is always true is a typed assignment (`let _: ClientError = error`), and `try` is not written before the non-throwing `authHeaders()` of the API key, bearer and basic authenticators. Measure with `swift build --build-tests` and look for warnings in `Tests/`.

### Decisions taken in batch 7
Rule: token expiry is stored as `now + expires_in - min(60 s, expires_in / 2)` and read without a second margin, so a 60 s token is reused for 30 s; a grant response (first fetch, refresh or exchange) without `expires_in` clears the expiry, and a stale token in a test uses `expires_in: 0`.
Rule: request headers merge case-insensitively, layer by layer (transport defaults, config defaults, operation headers, authenticator headers, multipart Content-Type and Content-Length), through `BaseApi.mergeHeaders`; two spellings in one layer resolve in sorted-name order so the result is the same on every run.
Rule: `ConfigurationError` has the cases `invalidArgument`, `invalidCACertificate`, `invalidProxy`, `invalidServerVariable`, `invalidState`, `invalidTimeout`, `unsupported` (a transport that cannot stream, see Streaming); the former `proxyUnsupported` and `tlsOptionsUnsupported` are `invalidProxy` and `invalidCACertificate` with the platform text (D76). `invalidProxy` and `invalidCACertificate` carry the whole message, proxy text per D79 with `user:password@` shown as `***@`; a timeout of zero or less is `invalidTimeout` when the options are built (D77).
Rule: the redirect refusals read `Too many redirects (exceeded N)`, `Refusing to follow redirect to non-HTTP(S) URL: <next>` and `Refusing to replay request body across HTTPS->HTTP redirect: <current> -> <next>` (D16); token failures read `Token response is not valid JSON`, `...is not a JSON object`, `...missing or empty access_token field` (D07); `ApiError` renders `Response headers: {"a":"b"}` as sorted compact JSON (D17); lists in messages render `[a, b, c]` (D20).
Rule: a supplied `URLSession` keeps the SDK redirect rules: the SDK `SessionDelegate` is attached to each task (`URLSessionTask.delegate`, macOS 13 and iOS 16 are the floor), so credential stripping, the scheme and downgrade refusals and the hop cap still run. A cross-origin hop also drops every header whose name contains `key`, `token` or `secret` (the S15 rule), so default headers and custom authenticator headers are covered without being listed.
Rule: object-valued query and header parameters (a model or a map, D47) expand by style: form exploded is one `name=value` pair per field, form not exploded and simple are `name,value,name,value`, deepObject is `param[name]=value`; fields are the wire names, sorted by name (`JSONEncoder` has no declared-order mode), nulls dropped, and a nested field is a `SerializationError`. A per-call `HttpAwareAuthenticator` receives the API's client. The file header prints the info description through `unescapeDocComment`, and an apiKey name goes into the strip-list through `swiftString`.
Rule (owner default, ported from the php, kotlin and go branches): a 401 on a request authenticated by an OAuth2 or OIDC authenticator calls `AccessTokenInvalidating.invalidateAccessToken()` (public protocol in `http_aware_authenticator.mustache`), fetches the headers again (a refresh grant first) and replays the request once; a failed re-authentication leaves the 401. The authorization-code URL carries a PKCE S256 `code_challenge` and the code exchange sends the matching `code_verifier` once (SHA-256 is hand-written in `PKCE`, because CryptoKit is not on Linux). The OIDC authorization URL ends with a fresh 43-character `nonce` (D140), held in the OIDC authenticator and spent by the exchange, which validates the `id_token` claims (nonce, discovered issuer, client ID in the audience, expiry with 60 s skew) through the `idTokenCheck` hook of `OAuth2TokenManager.exchange` and raises `OAuth2TokenError.invalidResponse`, keeping the previous session; the signature is not verified. OIDC discovery rejects an `issuer` that differs from the discovery URL minus `/.well-known/openid-configuration` (a document without an issuer is accepted) and an authorization or token endpoint that is not https unless it is http on a loopback host. A `noRedirect` request (the token POST) omits `TransportOptions.defaultHeaders`; OIDC discovery still carries them, because it must follow redirects. Models with a `format: password` property are `CustomReflectable` and show it as `***` in `print`, `dump` and `String(describing:)` (`hasPasswordVars` is set in `postProcessModels`). `ApiError.errorDescription` shows at most 1024 body bytes then `... (N more bytes)`, control characters as `\xNN`, and `***` for authorization, cookie, set-cookie, challenge headers and any header named like key, token or secret. The date-only fallback in `parseDate` takes exactly `yyyy-MM-dd`, so a date-time without an offset is rejected.

### Decisions taken in batch 9
Rule: F855 (invalidate the cached token on a 401 and replay once), F858 (PKCE S256) and F1080 (rendered `ApiError` capped at 1 KiB, control characters as `\xNN`, credential headers masked) were already in place from batch 7 and are unchanged; F825 is rust only.
Rule: `TransportOptionsBuilder.maxRetries(_:)` (default 2, 0 disables, negative rejected by `build()`) turns on automatic retries in `DefaultApiClient.sendRequest(...refreshHeaders:)`: network errors before a response (a `NetworkError` that is not a `NetworkTimeoutError`) and statuses 408, 409, 429 and 500 to 599 except 501; the wait is `Retry-After` (seconds or HTTP date, capped at 60 s) or 0.5 s doubling to 8 s with up to 25% taken off by jitter (`DefaultApiClient.retryDelay`); a wait that does not fit in what is left of the one `timeout` budget is skipped and the last outcome stands, and each attempt gets only the remaining time as its deadline; an `InputStream` body and a `noRedirect` (OAuth2 token) request are never retried; `BaseApi` passes the authenticator's `authHeaders()` as `refreshHeaders`, so each retry merges fresh authentication headers over the original ones, and the request is rebuilt each time (new request id). A caller-supplied `ApiClient` is called once.
Why: Stainless-style default; the transport owns the loop so the deadline and cancellation (`Task.sleep` throws `CancellationError`) are enforced in one place.
Where: `transport_options.mustache`, `default_api_client.mustache` (`sendRequest`, `retryDelay`, `sendOnce`), `base_api.mustache` (`send`), `readme.mustache`, `skills.mustache`.
Test: `DefaultApiClientUnitTests` (retry count, `Retry-After`, 400/404/501 not retried, budget, `maxRetries(0)`, stream body, token request, auth refresh, backoff and jitter), `TransportOptionsTests`. Tests that expect one failing request through `DefaultApiClient` set `maxRetries(0)`.

### Decisions taken in batch 10
Rule: `ServerConfiguration.url(variables:)` substitutes every variable in one pass over the template, so a placeholder introduced by a substituted value is never expanded again, whatever the dictionary order. Authenticator cookies go out sorted by name through `BaseApi.cookieHeaderValue`. A token response whose `token_type` is not `bearer` (any case) is an `OAuth2TokenError.invalidResponse("Unsupported token_type in token response")`; an absent or null one is accepted. `OAuth2AuthorizationCodeError.codeNotExchanged` reads `Must call exchangeCode() before making API requests` (D09).
Rule: token expiry and the OIDC discovery expiry are read off `MonotonicClock` (`CLOCK_MONOTONIC` on Apple platforms, which counts sleep; `systemUptime` elsewhere), so a wall-clock step does not keep a token too long or drop it at once (supersedes the batch 7 "not taken"). A caller cancelled while waiting for a shared token refresh stops waiting at once with `CancellationError` (`OAuth2TokenManager.wait(for:)`), and the refresh still finishes and caches for the others.
Rule: README and SKILLS follow the java and kotlin layout: Installation and Usage after Requirements, then Cancellation, Custom HTTP client, Raw responses, timeouts and request ids, and Retries as sections of their own above Caveats, and XML bodies, Link Objects and mutual TLS under Not supported. The SKILLS Models sample builds the first model with its required fields. Every test is named `testCamelCase`.
Why: audit proposals, taken as the default; only the token-type, cookie order, server-variable and expiry changes touch behaviour, none of them the wire for a conforming server.
Where: `server_configuration.mustache`, `base_api.mustache`, `auth/oauth/oauth2_token_manager.mustache`, `auth/oauth/openid_connect_authenticator.mustache`, `errors/oauth2_authorization_code_error.mustache`, `readme.mustache`, `skills.mustache`. No public API is added (`MonotonicClock` and `cookieHeaderValue` are internal).
Test: `ServerConfigurationTests`, `BaseApiTests` (cookie order, MIME table, non-BMP filename), `OAuth2TokenManagerTests` (token type, monotonic clock, cancellation), `OAuth2AuthCodeAuthenticatorTests`, `BearerAuthenticatorTests` (Unicode-space token), `TransportOptionsTests` (default User-Agent, IPv6 proxy), `DefaultApiClientTests` (3xx without a usable Location), `DefaultApiClientUnitTests` (MIME table, non-BMP filename, `close()` default), `ObjectSerializerTests` (failure text carries the detail, constraint keywords, byte-array equality), `ComposedSchemaTests` (anyOf declaration order).

### Decisions taken in wave 2 (-)

Implemented:
- F830: `ApiError` (so every subclass, `NetworkError` and `NetworkTimeoutError` included) has `requestMethod` and `requestPath` (`public internal(set)`, the path without its query string or fragment), accepted by every initializer and by `ApiError.fromResponse(statusCode:headers:body:requestMethod:requestPath:)`, which builds the canonical message `API returned status code 404 for GET /items/7` (unchanged when no request is given). `BaseApi.invokeAPI` passes the method and the request URL's path (base path included, query left out); a failure that arrived without a response is given the same context by `ApiError.attachRequest`, which appends ` for GET /items/7` to its message once. OIDC discovery passes `GET` and the discovery path. Test: `ApiErrorTests`, `BaseApiTests`, `DefaultApiClientUnitTests` (discovery), `MetadataTests` (README).
- Typed errors (issue 123): one class per distinct declared error schema, `<Model>Error` in `Errors/<Model>Error.swift` (`errors/schema_error.mustache`, written once per `errorSchemas` entry through `errorSchemaTemplate`), subclassing the status class of its status; a parent that fixes the status (`NotFoundError`) gives an initializer without `statusCode`, a family parent (`ApiError`, `ClientError`, `ServerError`) takes it. Swift keeps class inheritance and `ApiError` stays non-generic; `errorBody` stays `Any?` (a Swift override cannot narrow a stored `let`), so the typed accessor is `typedErrorBody` on the schema class, next to the existing `getTypedErrorBody(_:)`. Each operation passes `errorSchemas: [String: ErrorFactory]` in `InvokeAPIParams`; `ApiError.fromResponse(...errorSchemas:)` picks the exact status, then `4XX`/`5XX`, then `default`; a body that does not decode returns the status error with the failure as `underlyingError` (now `internal(set) var`). `isRetryable` reads `DefaultApiClient.isRetryableStatus`, so the client and the error share one rule; `requestId` reads `x-request-id` in any case. Errors 201 to 205 are tested in `ApiErrorTests` and `BaseApiTests`.
- Decode failure (errors205): `SerializationError` has `statusCode`, `responseHeaders`, `responseBody`, `requestMethod` and `requestPath` (query cut); `BaseApi.invokeAPIForResult` (both overloads) attaches them with `withResponse` to any `SerializationError` raised while reading a response, including the unexpected Content-Type one. `message` is unchanged.
- F827: every per-operation options struct has `timeout: Int?` (milliseconds, greater than zero), `headers: [String: String]?` (applied after the authentication headers and replacing a header of the same name in any casing) and `maxRetries: Int?` (0 turns retries off), ahead of `auth` in the initializer. `BaseApi` validates them (`ConfigurationError.invalidTimeout`, `invalidArgument`) before any request is built; `DefaultApiClient.sendRequest(..., refreshHeaders:timeout:maxRetries:sendDefaultHeaders:)` applies them (the per-call timeout is the end-to-end budget; on a session the SDK built itself it also reaches URLSession through a one-off copy of the session, released when the call ends, because the session's own deadlines would otherwise cut a longer call short). A parameter named `timeout`, `headers`, `maxRetries`, `auth`, `options`, `server` or `requestContentType` gets the `Param` suffix (`getInjectedArgumentNames`). An operation with no query, header, form or cookie parameter and no security requirement also gets an options struct, through `mintsOptionsForEveryOperation()` (see the adoption section below). Test: `BaseApiTests`, `DefaultApiClientUnitTests`.
- F868: model properties are `let` (`public private(set) var` for an `@Indirect` property, which a property wrapper cannot make `let`), `additionalProperties` too, and every model has `copy(...)`: a property left out keeps its value, and because a bare `nil` also means "keep", an optional property is cleared with `.some(nil)` (the parameter type is `T??`). The copy is shallow. A oneOf wrapper was already immutable. Test: `MetadataTests`.
- F1062: for an operation with `hasMultiple2xx`, `BaseApi.invokeAPIForResult(_:decodingByStatus:)` reads the body as the schema of the status that arrived (a `2XX` range, or the last row, is the default) and wraps it in the union. Test: `BaseApiTests`.
- F878: a oneOf wrapper has `fromVariant(_:)`, `isVariant` and `asVariant` per variant; the variant name is the variant type without punctuation (`[Data]` is `DataList`, `[String: Int]` ends in `Map`), set by `nameUnionVariants` on `composedSchemas.oneOf`. `encode` can only write a held variant, because the initializers are typed, so there is nothing for it to reject. Test: `ComposedSchemaTests`, `BetterSwiftCodegenTest`.
- F894: generator options `repositoryUrl`, `homepageUrl`, `authors`, `documentationUrl`, `issueTrackerUrl` (and the existing `licenseId`) are listed in the README Package section, with the semantic-versioning and platform policy; they are read from `additionalProperties`, so no shared change was needed. SwiftPM has no manifest fields for them.
- F862: `Client.fromEnvironment(host:token:tokenProvider:transportOptions:)`: the explicit argument wins, then `{PREFIX}_HOST` and `{PREFIX}_TOKEN` read once when the client is built (empty is unset); a missing host or credential throws `ConfigurationError.invalidArgument` there. `BearerAuthenticator(host:tokenProvider:)` calls the callback before every request and validates each token, so `BearerAuthenticator.authHeaders()` is now `async throws` (it already was on the `Authenticator` protocol). `{PREFIX}` is the shared `envVarPrefix` (see the adoption section below). Superseded: the scheme authenticators now read the environment too. Test: `ClientTests`, `BearerAuthenticatorTests`.
- F860: OIDC discovery is sent with `sendDefaultHeaders: false`, so the API's default headers never reach the identity provider; redirects are still followed. Test: `DefaultApiClientUnitTests`.
- F308 and F869: an integer-enum member is lowerCamel (`number1`, `numberMinus1`) through `toEnumVarName`; every generated enum conforms to `CustomStringConvertible` and prints its wire value. Test: `MetadataTests`, `BetterSwiftCodegenTest`.
- F1095: `Package.swift` carries `unsafeFlags` only while `SWIFT_SDK_STRICT_WARNINGS` is set (the `Makefile` exports it, and `SwiftClientSpec` and `SwiftLintingSpec` set it), so a consumer resolving the package from a version tag no longer meets them; the three test-only packages stay at package level because SwiftPM has no test-only dependency scope (forced).
- F889, F834, F835, F836, F837, F828: README and SKILLS sections (per-call options, error details, immutable models, several success responses, credentials from the environment, compression, logging, versioning, optional versus nullable, unknown enum and discriminator, repeated response headers, streaming, Configuration versus TransportOptions), pinned by `MetadataTests`.
- F006: a container of primitives is decoded element by element by `JSONDecoder` itself, and the quoted, fractional and null element rows already existed; an out-of-range row was added.

Already satisfied, no change: F1104 (`SerializationError.errorDescription` appends the decoder detail; `testBhvSerializationobject200SerializeFailureTextCarriesTheEncoderDetail` and `testDeserializeFailureTextCarriesTheDecoderDetail`), F1053 (the swift part, a caller cancelled while waiting for a shared token refresh stops at once, landed in batch 10), F1150 (the SDK's own proxy credential answers the proxy's challenge in the session delegate and is never a request header, so no hop can drop it; `proxyChallengeDisposition` and the strip-list are pinned in `DefaultApiClientUnitTests`).

Forced: F872 (a reserved property name stays underscore-prefixed: Mustache HTML-escapes a backtick in every `{{name}}` into `&#x60;`, which is not a Swift identifier and an entity the formatting spec bans, so backticks need `escapeHTML` turned off in the shared template engine setup, or triple braces at several hundred template sites; `reserved-words/swift.txt` is pinned to the compiler's keyword list by `SwiftReservedWordsSpec` either way); F1140 (URLSession releases an intermediate response itself and a `URLProtocol` stub cannot observe it); F1087 (the floor stays `swift-tools-version: 6.0`: Swift has no end-of-life policy for toolchains, and 6.0 is the oldest the generated code and Swift Testing need).


### Adoption of the shared generator work (2026-11-17)

Rule: every operation gets an options struct (`mintsOptionsForEveryOperation()` returns true), so an operation with no query, header, form or cookie parameter and no security carries only `timeout`, `headers` and `maxRetries` and has no `auth` field. Each per-scheme authenticator takes its credentials as `String?` and reads the variable named by the shared `securitySchemeEnvVars` (`{PREFIX}_{FIELD}`, or `{PREFIX}_{SCHEME}_{FIELD}` where two schemes share a field, `{PREFIX}` the shared `envVarPrefix`): the argument wins (an explicit empty string still fails the base authenticator's own check), then the variable, read once when the authenticator is built (empty is unset), and a credential found in neither place throws `ConfigurationError.invalidArgument` naming the variable. Because it can throw, every scheme initializer is `throws`, the OAuth and OpenID Connect ones included. `Client.fromEnvironment` reads `{PREFIX}_HOST` and `{PREFIX}_TOKEN` and still prefers an explicit `tokenProvider`, then `token`, then the variable. A `$ref` to a string enum with a `default` beside it initialises the optional property to that member (`vendorExtensions.siblingDefaultIsEnum`, `siblingDefaultEnumType`, `siblingDefaultEnumVar`) in the initializer and when the key is absent on decode; an explicit `null` stays `nil`; a sibling default on a `$ref` to anything but a string enum is still not applied.
Why: the same variable names, precedence and failure in all twelve SDKs, and a slot for per-call settings on every call so adding a parameter later never moves one.
Where: `BetterSwiftCodegen` (`mintsOptionsForEveryOperation`, `renderSchemeAuthenticator`, `perSchemeAuthEntries`), `auth/scheme_authenticator.mustache`, `auth/base_authenticator.mustache` (`BaseAuthenticator.credential`, internal), `client.mustache`, `models/model.mustache`. The swift-only `envPrefix` option is gone: `envVarPrefix` replaces it.
Test: `BetterSwiftCodegenTest` (options for every operation, signature builders), `ClientTests` (precedence, empty, missing, one block per scheme authenticator), `BearerAuthenticatorTests` and `BasicAuthenticatorTests` (the header that results), `BaseApiTests` (per-call settings on an operation with no parameters), `ObjectSerializerTests` (sibling default on construction and decode), `MetadataTests` (README names every variable).

### Owner decisions of 2026-11-17, round 4

Rule: the client takes one optional credential per distinct security scheme, named after the scheme: `Client(host:petStoreBearer:apiKeyHeader:...transportOptions:)`, each argument any `Authenticator` (`Authenticator?`, not the per-scheme class, so a plain `BearerAuthenticator` serves a bearer scheme). The names come from the shared `clientSchemes` (`schemeId`, `paramName`, `className`), and `BetterSwiftCodegen.processOpenAPI` adds `argName` (the name in backticks when Swift reserves it, `clientArgumentName`). The client hands each credential its transport (`HttpAwareAuthenticator`), keeps them as `[String: Authenticator]` by scheme id and passes them to every API class (`BaseApi.schemeAuthenticators`). An operation passes `schemeAuthenticator([...])` over its own `vendorExtensions.op.securitySchemeOrder` as `auth`: the credential of the first scheme in that order the client holds, else `nil`, which leaves the call to the client-level authenticator of `Client(authenticator:transportOptions:)` and to no credential when there is none; `securityNone` still passes the no-auth sentinel. The per-operation `auth` field is gone from every Options struct (`generateOptionsFileContent` no longer calls `injectAuthFieldContext`, `auth` is no longer an injected argument name), so there is no per-call scheme override.
Rule: a cookie parameter is sent raw when every character is an RFC 6265 cookie-octet, otherwise `ConfigurationError.invalidArgument` (`Cookie value for '<name>' contains characters outside the RFC 6265 cookie-octet set; percent-encode the value before passing it`) before any request. This already held (`BaseApi.isValidCookieValue`); the message and the tests are new. A `content:` cookie is a typed value the caller cannot pre-encode, so its JSON stays percent-encoded by `ValueSerializer.encodeCookieValue` (batch 5).
Rule: `DefaultApiClient.close()` is graceful and idempotent. `ClientLifecycle` counts a logical request (`sendRequest(...refreshHeaders:...)`, retries and token refreshes included) in under a lock before it starts and out when it ends; `close()` marks the client closed and releases the session the SDK built (`finishTasksAndInvalidate()`, and the no-redirect session) at once only when nothing is in flight, otherwise the last request to end releases it. A request after `close()` throws `DefaultApiClient.closedError` (`ConfigurationError.invalidState("ApiClient has been closed and can no longer send requests")`); a session supplied through `TransportOptions.session(_:)` is never released. The old per-attempt closed check in `sendOnce` is gone, since it made a retry after `close()` fail. A token fetch that starts after `close()` is a new request and is refused.
Rule: nothing validates at model construction (it already held: the initializers are plain assignments, and the types plus the closed enums leave nothing to check); the wrong-value failures (`SerializationError`) happen when the SDK decodes: an unknown enum string, a missing required key, a `format: uri` that is not absolute. Only tests were added.
Also landed for the api surface: an anyOf wrapper has typed `is<Variant>` and `as<Variant>` like a oneOf one (`nameUnionVariants` now names anyOf variants too).
Where: `BetterSwiftCodegen` (`processOpenAPI`, `clientArgumentName`, `generateOptionsFileContent`, `getInjectedArgumentNames`, `nameUnionVariants`), `client.mustache`, `base_api.mustache`, `api/api.mustache`, `api/options.mustache`, `default_api_client.mustache`, `models/model.mustache`, `readme.mustache`, `skills.mustache`, `docs/languages/swift.md`.
Test: `ClientTests` (credentials per scheme through a stub session, scheme order, none, security none, transport injection, canonical closed error), `BaseApiTests` (resolution, order, fallback, inherited and `security: []` operations, no `auth` on Options), `PetApiTests` (scheme credential beats the default header, scheme order, cookie octets, doc line of `options`), `DefaultApiClientUnitTests` (in-flight request and its retries finish after `close()`, idle close releases at once, second close is a no-op, a supplied session is untouched), `ObjectSerializerTests` (construction never validates, decode refuses), `BetterSwiftCodegenTest` (`clientArgumentName`, anyOf variant names, `auth` not injected).
Superseded: wave 2 F827 said the per-call settings come "ahead of `auth` in the initializer", and the adoption section above says an operation with security has an `auth` field; neither holds now.

### Response size limit (D138, issue #112)
Rule: `TransportOptionsBuilder.maxResponseBytes(_ val: Int?)` (default `nil`, no limit; zero or negative throws `ConfigurationError.invalidMaxResponseBytes`, text `Invalid maxResponseBytes: must be a positive number of bytes (got N)`) bounds every buffered body `DefaultApiClient.sendAttempt` reads, redirect hops and token requests included. With the limit set, the body is read by `BoundedBody` (a `URLSessionDataDelegate` at the end of `default_api_client.mustache`) instead of the completion handler: a `Content-Length` above the limit cancels at the headers, and each chunk adds to the decoded count (URLSession hands over the decoded bytes of a gzip or deflate body) while `countOfBytesReceived` is checked as the received count; the task is cancelled as soon as either passes, so the connection is dropped, not drained. A session the SDK built (the main one or the no-redirect one) is copied per call with `BoundedBody` as its delegate, like the per-call timeout copy; a supplied session gets it as the task delegate, like `StreamTransfer`. A gzip body URLSession left encoded is inflated by `decodeGzipBody(_:limit:)`, which stops as soon as the output passes the limit. The failure is `ResponseTooLargeError` (a direct `ApiError` subclass in `errors/response_too_large_error.mustache`, registered in `BetterSwiftCodegen`): real status, lower-cased headers, empty body, `limit`, message `Response body exceeds the limit of N bytes`, `isRetryable` always false, and the retry loop never retries it because it is not a `NetworkError`. Streams (`sendStreaming`) are not bounded.
Why: owner decision D138; URLSession exposes no wire count beyond `countOfBytesReceived` and decodes transparently, so the decoded count is the one the delegate sees per chunk.
Where: `transport_options.mustache`, `errors/configuration_error.mustache`, `errors/response_too_large_error.mustache`, `default_api_client.mustache`, `readme.mustache`, `skills.mustache`.
Test: `TransportOptionsTests` (`testBhvOptions069`), `DefaultApiClientUnitTests` (`testBhvTransport095`, `testBhvTransport096`, whose gzip body is a hand-built fixed-Huffman deflate stream because Linux has no zlib module), `ApiErrorTests` (errors044, errors094, misc026 rows).

### Mock seam (mocking001 to 009, issue #122 and the Invoker line of #131)
Rule: every API group has an interface and the client has one where the language can state it, additive next to the existing classes, using only SDK and standard-library types. `<Group>ApiProtocol` (file `<Group>ApiProtocol.swift`) and `ClientProtocol` with an associated type per group, so `Client` keeps its concrete group classes. A requirement cannot carry default arguments, so the protocol lists the full form of each operation and an extension adds the form with only the required parameters (`vendorExtensions.op.signatureArgsRequired`); a call through the protocol that passes some but not all optional parameters must pass the others as `nil`. Tests: test/ClientTests.mustache (001, 002, 007, 008), test/api/PetApiTests.mustache (003 to 006), test/FakeGroup.mustache (the shared fake). Where: the templates named above, `BetterSwiftCodegen`, the README template (`Testing your code`), `docs/languages/swift.md`.

## Streaming: binary responses (capability 1 of #114, D118 to D122)

### The transport returns an unread body
Rule: `ApiClient.sendStreaming(method:url:headers:body:timeout:maxRetries:idleTimeout:)` returns an `ApiStreamResponse` (a `final` class: status, lower-cased headers, an `AsyncThrowingStream<Data, Error>` body, an idempotent `close()`, `readBounded(maxBytes:)`, a `description` of the status only). The default implementation, in the protocol extension that already holds the `noRedirect` default, throws `ConfigurationError.unsupported` whose text is `<transport type> cannot stream: it does not implement sendStreaming, ...`, so an injected `ApiClient` keeps working for buffered calls and never falls back to buffering. `ConfigurationError.unsupported(String)` is a new case (Swift has no built-in unsupported error). A non-2xx response is returned like any other (open body); `BaseApi.invokeAPIForStream` reads up to 64 KiB of it, closes it and throws the typed error with the operation's error schemas. `BaseApi` split the buffered call into `prepareCall` (URL, authentication, headers, body, hook start) and the send, so the stream call sends the same request and replays a 401 once exactly like the buffered one.
Where: `api_client.mustache`, `api_stream_response.mustache`, `base_api.mustache`, `default_api_client.mustache`.
Test: `DefaultApiClientUnitTests` (`bhvStreaming001` and the rest), `BaseApiTests` (008, 017, 018, 030), `ClientTests` (021), golden `PetApiTests` (009).

### One request builder and one retry loop for both paths
Rule: `DefaultApiClient.buildRequest` merges the headers and builds the request for both paths (a stream asks for `Accept-Encoding: identity` unless the caller set one, because URLSession decodes a compressed body itself and a short body could then not be told by its length), and `withRetries` is the one retry loop (`sendRequest` and `sendStreaming` pass the status, the `Retry-After` and a `discard` that closes a retried stream). The buffered path is behaviourally unchanged.
Where: `default_api_client.mustache`.
Test: every existing `DefaultApiClientUnitTests` retry row, `bhvStreaming014`.

### The body: delegate, idle timeout, truncation, cancellation
Rule: `StreamTransfer` is a `URLSessionDataDelegate` (`URLSession.AsyncBytes` is absent on Linux) that yields each chunk to an `AsyncThrowingStream`. A session the SDK built is copied per stream with no deadline of its own (the call timeout and the idle timeout are the SDK's: a timer that stops at the headers, and a watchdog that fires when the reader has waited `idleTimeout` for data, never while the transfer is held back by a slow reader); a supplied session is used as it is, with the transfer as the task delegate. Redirect, challenge and body-replay rules are the buffered path's `SessionDelegate`, called through the transfer, and the Linux follow-up is sent from the opening loop. The transfer is suspended while more than 1 MiB is unread and resumed below 256 KiB. A failure after the headers is `StreamError` (reset, or fewer bytes than `Content-Length` when the length can be told), a stall is `NetworkTimeoutError`, and a cancelled reading task gets `CancellationError`, never a timeout. The stream keeps no reference to its own `AsyncThrowingStream` once it is handed out, so a stream dropped unread is cancelled and its connection is released.
Where: `stream_transfer.mustache`, `byte_stream.mustache`, `transport_options.mustache` (`idleTimeout`, default 60000, `0` or `nil` disables, negative rejected), `errors/stream_error.mustache`.
Test: `DefaultApiClientUnitTests` (`bhvStreaming010`, `011`, `012`, `013`, `015`, `019`, `020`, `031`, `032`, `033`), `TransportOptionsTests`.

### Closing the client cancels open streams
Rule: `DefaultApiClient` keeps the streams it handed out in `OpenStreams`; `close()` marks the client closed, cancels every open stream with the closed-client `ConfigurationError.invalidState`, and a stream whose headers arrive after `close()` is cancelled and throws it. The next read of a cancelled stream throws the error before it hands out any chunk still buffered.
Where: `default_api_client.mustache`, `stream_transfer.mustache`.
Test: `ClientTests` (`bhvStreaming021`).

### Binary operations return `ByteStream`
Rule: `BetterSwiftCodegen.markBinaryStream` sets `streamsBinary` on `vendorExtensions.op` and rewrites `returnType` to `ByteStream` for an operation whose `streamKind` is `binary`, so every template (operation, protocol, docs, `WithHTTPInfo`) says `ByteStream` and `api.mustache` calls `invokeAPIForStream`. `ByteStream` is a `final` class and an `AsyncSequence` of `Data` (`statusCode`, `headers`, `readAll()` that reads everything and closes, an idempotent `close()`, a `description` of status and bytes delivered); reading it after `close()` throws `ConfigurationError.invalidState`; `ApiResult<ByteStream>` has an empty `rawBody`. `postProcessSupportingFileData` hands the README and SKILLS the first such operation (`binaryStreamOperation`) for the Streaming example. A `format: byte` response is not a stream.
Where: `BetterSwiftCodegen`, `api/api.mustache`, `byte_stream.mustache`, `readme.mustache`, `skills.mustache`.
Test: golden `PetApiTests` (`bhvStreaming009`), `DefaultApiClientUnitTests` (`bhvStreaming029`).

## Streaming: JSON Lines and server-sent events (capability 2 of #114, D120 to D122)

### The operation methods
Rule: `BetterSwiftCodegen.markBinaryStream` also sets `streamsSse` and `streamsJsonLines` for an operation whose `streamOnly` decorator is true, rewrites `returnType` to `SseStream<T>` or `JsonLinesStream<T>` and adds `streamTyped` and `streamItemSwift`. `api/api.mustache` renders such an operation as `<op>Stream(params, options:, streamOptions:)` and nothing else (no buffered method, no `WithHTTPInfo`, in the class, the protocol and the test fake); the shared preamble (parameter checks, path, query, header, cookie and body building, `InvokeAPIParams`) moved to `api/operation_prelude.mustache` so both shapes use it. The protocol requires `streamOptions` and its extension offers the required-only form. A schema item type is decoded with `BaseApi.decodeStreamItem` (the `ObjectSerializer`); a string item hands over the text. `postProcessSupportingFileData` gives the README and SKILLS the first operation of each kind (`jsonLinesStreamOperation`, `sseStreamOperation` and their groups).
Where: `BetterSwiftCodegen`, `api/api.mustache`, `api/api_protocol.mustache`, `api/operation_prelude.mustache`, `readme.mustache`, `skills.mustache`.
Test: golden `StoreApiTests` (028), `ClientTests` (mocking 001 and 002 list the stream methods).

### Parser and streams
Rule: `LineReader` and `SseParser` (`event_stream.mustache`, internal) implement the WHATWG grammar over a `ByteStream` with a skip-LF flag for CR; a cut-off last line is dropped by the SSE parser and decoded by JSON Lines. `JsonLinesStream` and `SseStream` are `final` classes that are an `AsyncSequence` whose iterator holds only the owner, so the read position lives in the stream and a stream is read once, by one task at a time. `close()` aborts the connection at once and ends a read or a reconnect wait that is in progress. `BaseApi.invokeAPIForSse` builds each connection with `invokeAPIForStream` (the hook, the headers, the credential and the retries are the buffered code, rebuilt on every reconnect) and adds `Cache-Control: no-cache` and `Accept-Encoding: identity` unless the caller or the configuration set them; `Last-Event-ID` goes through the per-call headers. The pause before a reconnect is a continuation that `close()` and task cancellation resume. A clean end counts as a failure against `maxReconnects` (a `StreamError` when exhausted, a quiet end for 0); in-band `error` events raise `ApiError` with the real status and are never reconnected. `DefaultApiClient.parseRetryAfter` is the one `Retry-After` parser of the retry loop and the reconnect wait.
Where: `event_stream.mustache`, `json_lines_stream.mustache`, `sse_stream.mustache`, `server_sent_event.mustache`, `stream_options.mustache`, `base_api.mustache` (`invokeAPIForJsonLines`, `invokeAPIForSse`), `default_api_client.mustache`.
Test: `EventStreamTests` (the shared parser table: streaming 002 to 005, sse 001 to 005, 008 to 011, 014, 016, 017, 019, 021 to 023), `DefaultApiClientUnitTests` (streaming 006, 007, sse 007, 015, 018), `BaseApiTests` (sse 006, 012, 013, 020).

### Compressed JSON Lines
Rule: `InvokeAPIParams.acceptCompression` (set by `invokeAPIForJsonLines`) reaches `DefaultApiClient.openStreaming`, which then leaves `Accept-Encoding` unset so URLSession negotiates and decodes the body as it arrives, on Apple and on Linux (libcurl); a binary and an SSE stream still ask for identity. A body URLSession cannot decode ends the stream with an `ApiError` carrying the real status and the `URLError` as `underlyingError` (`StreamTransfer`), not a `StreamError`, and is not retried. libcurl names that failure `URLError.unknown` with `Error while processing content unencoding` in its text (`StreamTransfer.isUndecodable`); Apple's URLSession never fails on it, so `bhvStreaming007` runs on Linux only.
Where: `base_api.mustache`, `default_api_client.mustache`, `stream_transfer.mustache`.
Test: `DefaultApiClientUnitTests` (streaming 006 sends a gzip body in two parts and reads the first item before the second part is sent, 007).

## Streaming: uploads (capability 3 of #114, D121 item 6)

### `BodySource` is the body type of a raw binary request
Rule: `BetterSwiftCodegen.markBinaryUpload` (run from `postProcessOperationsWithModels` before the shared pass, which copies the body type into the signature arguments) rewrites the body parameter of an operation whose body is `format: binary` and neither multipart nor a form to `BodySource`, in every signature (operation, protocol, `WithHTTPInfo`, the client facade and its test fake); every other body kind keeps its type. `BodySource` (`body_source.mustache`, a `final` class) has the replayable constructors `of(_ data:)` (length known), `ofFile(_ url:)` (length is the file size when sent, the file is opened again for every send) and `fromFactory(length:_:)` (a closure that makes a fresh `AsyncSequence` of `Data` for every send), and the single-use `once(_ stream: InputStream, length:)` and `once(_ sequence:, length:)`; `isReplayable`, `length` (nil when unknown) and a `description` of the kind and the length, never the content. The S16 rule: the SDK opens a caller `InputStream` when it is not open, reads it to its end and never closes it; a file stream it opened itself is closed by the SDK.
Where: `BetterSwiftCodegen`, `body_source.mustache`, `api/api.mustache`.
Test: golden `PetApiTests` (operations 070 to 073, 076, 093), `DefaultApiClientUnitTests` (misc 163).

### The transport sends it as it is read
Rule: `DefaultApiClient.buildRequest` puts a `BodySource` on the request as `httpBody` (bytes) or as `httpBodyStream` (everything else), with `Content-Length` when the length is known and chunked otherwise; nothing is buffered. The stream is a `BodyPump`: a thread that pulls blocks from the source and writes them into a stream that holds at most 64 KiB, so the source is read no faster than the connection takes it. Apple uses `Stream.getBoundStreams`; Linux has none, so `PumpInputStream` (an `InputStream` subclass that blocks a read until the pump has delivered) stands in, and a known length also sets a blank `Transfer-Encoding` there, because libcurl chunks every stream body next to the `Content-Length` otherwise. A failure of the source ends the read with an error (Linux) and is raised after the attempt as `NetworkError` (`DefaultApiClient.sending`), which also releases the pumps of the attempt. `DefaultApiClient.isReplayable` asks the source, so `withRetries` retries a replayable body and never a single-use one (the original answer or error stands), and `BaseApi`'s 401 replay follows the same rule (`PreparedCall.isReplayable`). `SessionDelegate` keeps the source of each task: a redirect that keeps the method (307, 308) opens a fresh stream for a replayable one (the Linux follow-up is sent by the call side with it) and is refused with `ApiError` carrying the redirect status and `Refusing to replay a streamed request body` for a single-use one, after the HTTPS-to-HTTP check; `urlSession(_:task:needNewBodyStream:)` serves a replayable source the same way. A custom `ApiClient` receives the `BodySource` as the `body` argument.
Where: `default_api_client.mustache`, `stream_transfer.mustache`, `base_api.mustache`, `body_source.mustache`.
Test: `DefaultApiClientUnitTests` (`Streaming uploads`: streaming 023, 024, 025, 026, 027), `RawConnection.readUpload` in `TestContainersHelper`.

### An event stream request with a single-use body never reconnects
Rule: `SseStream.open` takes `singleUseBody`; when it is true `maxReconnects` is 0 whatever the options say. `invokeAPIForSse` passes `body is BodySource && !isReplayable`.
Where: `sse_stream.mustache`, `base_api.mustache`.
Test: `EventStreamTests` (sse 016).

## Pagination: `<op>Pages` (capability 4 of #114, D123, D124)

### The methods and the options copy
Rule: the shared detector (`AbstractBetterCodegen.populatePaginationDecorators`) sets `vendorExtensions.op.pagination`; `BetterSwiftCodegen.markPaginator` adds `paginatorType` (`Paginator<Item, Cursor>`, `Paginator<Item, Int32>` for an offset or a page number, `Paginator<Item, Void>` for a next URL list, whether the `Link` header or the body names it). `api/api.mustache` renders `<op>Pages(...)` through `api/operation_pages.mustache` right after the base operation, in the class, the protocol (`api_protocol.mustache`, with a no-options form in the extension) and the test fake (`test/FakeGroup.mustache`). The method has the base method's parameters in the base order and returns a `Paginator`; every page is a call of the operation's `WithHTTPInfo`, so retries, timeouts, redirects, authentication, redaction and per-call options apply to each page. A cursor, id, offset or page-number operation's `Options` struct gets `withCursor(_:)` (`options.mustache`, `hasCursorCopy`), so each page is sent with its own value and every other field unchanged. A `Link` or next-URL operation's `WithHTTPInfo` calls an internal `<op>Page(..., pageURL:)` that carries the prelude (`isUrlPaged` selects it, `isToken` and `isLink` no longer mean a URL list), and `InvokeAPIParams` takes the next URL as its path with no query parameters of its own.
Where: `BetterSwiftCodegen`, `api/api.mustache`, `api/operation_pages.mustache`, `api/api_protocol.mustache`, `api/options.mustache`, `api/operation_prelude.mustache`, `test/FakeGroup.mustache`.
Test: `BaseApiTests` (pagination 004, 005, 006, 024, 025, 026, 029, 030, 032), `ClientTests` (mocking 001, 002).

### The sequences
Rule: `Paginator<T, C>` (`paginator.mustache`, a `Sendable` struct) is an `AsyncSequence` of its items with `items()` and `pages()` (`Items` and `Pages`, also `AsyncSequence`s). Each `makeAsyncIterator()` makes a fresh `PageWalker` (`CursorWalker` for token and id cursors, `CountWalker` for offsets and page numbers, `LinkWalker` for `Link` and body URLs), which fetches a page only when `next()` needs one (no `AsyncStream`, so there is no read-ahead), checks `Task.checkCancellation()` before each request, finishes after an error and reports a repeated cursor or a refused link from the read after the page that showed it. `Page<T, C>` holds `items`, `nextCursor` and `result: ApiResult<any Sendable>` (the body type is erased, as in the other languages). `LinkHeader` (`link_header.mustache`, internal) reads RFC 8288; a next URL is resolved against the page just read and refused unless it has the client's scheme, host and port (`DefaultApiClient.sameOrigin`). `PaginationError` (`errors/pagination_error.mustache`) conforms to the root error protocol only.
Where: `paginator.mustache`, `page.mustache`, `link_header.mustache`, `errors/pagination_error.mustache`.
Test: `BaseApiTests` (pagination 001 to 023, 027, 028, 031).
Where the id, offset and page-number runtime lives: `ofCursor(..., hasMore:)`, `ofOffset`, `ofPage` and `ofNextUrl` in `paginator.mustache`. No language impossibility applies: every style is expressible in Swift.

## Compiled examples (#111)

- `examples/quickstart.swift` (`templates/swift/examples/quickstart.mustache`, registered by `BetterSwiftCodegen.getSupportingFileSpecs` when `generateExamples` is on) is an `@main struct Quickstart` whose `static func main() async throws` holds the statements of `usage_statements.mustache`, so it can `throw`. The README Usage fence and the SKILLS Quick Start fence include the same partial (`langswift088`). `Package.swift` declares the directory as `.executableTarget(name: "Quickstart", path: "examples")` under the same `strictSettings` as the library, so `make build` and `make examples` build it with `-warnings-as-errors`. SwiftPM adds `-parse-as-library` to a single-file executable target that holds `@main`.
- `SwiftExamplesSpec` writes every `swift` fence to `.out/fences/<file>_<nn>.swift`: a `file` fence as written, a `statements` fence with its leading imports kept on top and the rest inside `func fence() async throws`. It runs `swift build --build-tests`, then type-checks each file alone with `swiftc -typecheck -swift-version 5` against `$(swift build --show-bin-path)/Modules`; the test build is needed so that the OpenTelemetry recipe resolves `import OpenTelemetryApi`. Fences are not compiled with warnings as errors (an unused local in a statements fence is a warning), errors only.
- A fence that calls an operation is a function taking the client and the operation's own arguments (`<operation>OrReport(client:...)`, `<exampleOperation>Cancelled(client:)`), and the model fence is `copyOf(_:)` calling `copy()`, so no fence builds a model from nothing. The Package.swift manifest snippet in Installation is tagged `text`, since a manifest is not a source file of the package. The OpenTelemetry recipe and the function that passes it as the `callHook` are one `swift file` fence. Behaviours `langswift086` to `langswift091` are in `test/MetadataTests.mustache`, under `{{generateExamples}}`.
- `make examples` is `swift build --product Quickstart` (the Makefile exports `SWIFT_SDK_STRICT_WARNINGS`, so the manifest applies `-warnings-as-errors` with deprecation kept a warning; a command-line `-Xswiftc -warnings-as-errors` would override that carve-out and fail on the deprecated fixtures); `make examples-run` maps `API_BASE_URL` to `{PREFIX}_BASE_URL`, passes a placeholder `{PREFIX}_TOKEN` and runs the product with `swift run --skip-build`. `swift format` covers `examples` as well as `Sources` and `Tests` (the formatter step of the generator and `make format`/`make lint`), because partial indentation leaves the quickstart raw; `swiftlint` still covers `Sources` and `Tests` only.

## Known limitations and out of scope

- Linux has no proxy support (`ConfigurationError.invalidProxy`) and no CA pin (`ConfigurationError.invalidCACertificate`); SOCKS proxies are rejected by design (README). Reason: FoundationNetworking.
- `int` and `integer` map to `Int`, not `Int32`, so a value above `Int32` range is accepted for `int32` fields and an integral-looking `1.0` is accepted (D54, the API-breaking `Int32` mapping was proposed and not applied).
- Query, form and multipart parameters are held in `[String: ...]` dictionaries, so their wire order is not the spec order and multipart order is nondeterministic (D53, parked).
- Not applied yet: duplicate-key and required-default handling in `init(from:)` for D62 and D63, and the `Content-Encoding` header removal of D92.
- Declared-charset decoding is lenient UTF-8 with a fallback; windows-1252, shift_jis and iso-8859-x bodies can decode as mojibake (open for swift).
- The required date parameter guard has no petstore fixture (needs a spec change). Generated tests no longer compare non-optional values to `nil` or test always-true casts. `swift build -strict-concurrency=complete` (`make analyse`) is run by no spec; test sources get weaker strictness than sources (deferred).
- The API surface harmonisation of `ConfigurationError` is parked.
- Out of scope: webhooks, callbacks, XML, Link Objects, mutual TLS (S11). OpenAPI 3.1 and 3.2 are tracked by epics #96 and #49; 3.2 documents load through the shared parser extension (D150).

## Build-breaking rules

- Generated sources must not contain `//` implementation comments; use `/* */`, and `///` for documentation (the formatting spec fails the build otherwise).
- A mustache `{{! }}` comment must not contain a literal `{{tag}}`: jmustache ends it at the first `}}`.
- `swift build -warnings-as-errors` is on in debug (deprecation warnings are downgraded with `-Wwarning DeprecatedDeclaration` because models reference their own deprecated members).
- `swift-format lint --strict --recursive Sources Tests` must pass; the formatter needs `swift:6.2` (6.0 cannot parse the trailing commas the templates emit) and a git repository in the working directory, which `getFormatterCommands` creates and removes.
- Do not read private `BaseApi` helpers from tests. Transport behaviour is tested over real sockets on every platform: against the shared mock server where it serves the case, otherwise against `LoopbackServer` (in `TestContainersHelper`, plain HTTP on 127.0.0.1, one request per connection). Never stub `URLProtocol` for a redirect: swift-corelibs traps when a stub reports one.
- Golden regeneration runs Docker formatters; commit all twelve goldens current, never a subset.

## Verify

- `etc/ci-local.sh check Swift`
- `etc/ci-local.sh check --quick Swift` (no integration specs)
- `mvn verify -Dgroups=swift`
- Inside a generated client: `swift build`, `swift-format lint --strict --recursive Sources Tests`, `swift test --parallel --enable-swift-testing --disable-xctest`.

## Gate lessons (what broke the gate and how to avoid it)

- Generated code is verified only by the Docker specs (Linux, Swift Testing), so a template edit is unproven until `etc/ci-local.sh check Swift` is green (SwiftFormattingSpec, SwiftLintingSpec, SwiftClientSpec). The quick mode and a local macOS build miss Linux-only failures.
- No `//` comments in generated Swift (SwiftFormattingSpec regex `^\s*//($|[^/])`): `///` for docs, `/* */` for implementation notes. A Mustache `{{! }}` comment must never contain tag text (`{{#example}}` leaks and the formatter reports `unexpected code before modifier`). Catch: `grep -rn '{{!.*{{' src/main/resources/templates/swift`.
- Never wrap `Task { await }` in a `DispatchSemaphore`; it deadlocks the cooperative pool under Swift Testing, so `authHeaders()` is async. Catch: `grep -rn DispatchSemaphore src/main/resources/templates/swift` (must be empty); diagnose hangs with `lldb -p <pid> --batch -o "thread backtrace all"`.
- Linux Foundation differs from macOS: import `FoundationNetworking` behind `#if canImport(FoundationNetworking)`, URL parsing and percent-encoding are stricter, a 3xx redirect may arrive with an empty body, redirect and timer callbacks fire differently, and URLSession decompresses but keeps `Content-Encoding`, so never require that header to be absent. Never set `Accept-Encoding` yourself (URLSession then skips decompression). Catch: SwiftClientSpec integration tests, not unit tests.
- Assert timeouts and decode-depth limits with margin: compare exact floating point `TimeInterval` values with a tolerance, and keep nesting depth assertions below Foundation's cap (1000 fails, 50 passes).
- Test files use Swift Testing (`import Testing`, add `import Foundation`; XCTest re-exported it, Testing does not). Test suites that start Docker-in-Docker can hang with 0 percent CPU: run with a timeout and `swift test --filter`, write output to a file. Guard shared setup with an actor; `StubURLProtocol` is file-private, so use unique names per file.
- Swift 6 strictness: no force-unwrap or `try!` (NeverForceUnwrap, NeverUseForceTry), no mutable static state on actors, mutable `Sendable` properties warn. Recursive value types need `@Indirect`; a subclass calling `super.init` needs its own full designated initializer; template tests must call initializers that exist. Never strip `@available(*, deprecated)`; filter the log instead.
- `format: date` maps to `String`, so check the type mapping before adding `{{#isDate}}` branches. Decode base64 binary bodies (never `data(using: .utf8)`), and keep `Data` in a oneOf union out of integer-array encoding. Integer or non-lowercase enum fixtures need the `{{#isInteger}}` branch.
- Infrastructure flakes: `ServiceContextModule` or DocC plugin errors and `no such module` after a swiftc crash (rerun once, read the first compiler error); `--build-system swiftbuild` splits file names at `+` (use the default); the arm64 image on Apple Silicon (an x86 image ran 4x slower and OOMed); `rm -rf reports` must be retry-safe. Local `swift test` has no XCTest; call the toolchain binary directly, not through `devbox run`.

- Trace context from the caller (#136): the hook runs in the operation's own task after the credential await, so a task-local context is intact; the base-api tests prove this with a test-owned `@TaskLocal` (D95).

- **F15 (typed `additionalProperties`, #103).** The bucket is `[String: V]` (`V?` when nullable) and the decode loop uses `V.self`, so `Codable` rejects a wrong type and a null.
