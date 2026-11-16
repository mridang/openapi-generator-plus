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
Rule: `expires_in` and discovery max-age are capped at one year (`31_536_000`) before date arithmetic; a response without `expires_in` clears the expiry (see Decisions taken in batch 7).
Why: a refresh without `expires_in` leaves the token cached until it is invalidated, exactly like the first fetch.
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

### Decisions taken in batch 6
Rule: every `ApiError` for a response that arrived carries the real lower-cased headers: the three redirect refusals (hop cap, non-HTTP(S) Location, HTTPS to HTTP body replay) and the undecodable-body error use `DefaultApiClient.lowercasedHeaders(of:)`, the same helper the normal path uses (F1050, D13); `nil` still means no response arrived. `OAuth2AuthorizationCodeError` is a `LocalizedError` whose `emptyCode` text is `Authorization code must not be empty` (F931, D09); the use-after-close text is pinned (F925).
Why: audit proposals, taken as the default; no wire effect.
Where: `default_api_client.mustache`, `errors/oauth2_authorization_code_error.mustache`.
Test: `DefaultApiClientTests` (hop-cap and scheme refusals assert the `location` header; the HTTPS to HTTP downgrade test is not asserted because the stub ends in URLSession's own "too many HTTP redirects" before the delegate refuses), `OAuth2AuthCodeAuthenticatorTests`.
Generated tests: a comparison of a non-optional value with `nil` is written `_ = value` or dropped, an `is` check that is always true is a typed assignment (`let _: ClientError = error`), and `try` is not written before the non-throwing `authHeaders()` of the API key, bearer and basic authenticators (F525). Measure with `swift build --build-tests` and look for warnings in `Tests/`.
Not taken in batch 6, left open then: the single expiry margin and the no-`expires_in` rule, the redirect guard on a supplied session, and the canonical D16, D77 and D79 wording (all taken in batch 7), monotonic expiry clocks (F1054, still open) and swift reserved names with backticks (F872; changes goldens and the reserved-word spec).

### Decisions taken in batch 7
Rule: token expiry is stored as `now + expires_in - min(60 s, expires_in / 2)` and read without a second margin, so a 60 s token is reused for 30 s; a grant response (first fetch, refresh or exchange) without `expires_in` clears the expiry, and a stale token in a test uses `expires_in: 0` (F854, F1045, F1046).
Rule: request headers merge case-insensitively, layer by layer (transport defaults, config defaults, operation headers, authenticator headers, multipart Content-Type and Content-Length), through `BaseApi.mergeHeaders`; two spellings in one layer resolve in sorted-name order so the result is the same on every run (F1044).
Rule: `ConfigurationError` has the cases `invalidArgument`, `invalidCACertificate`, `invalidProxy`, `invalidServerVariable`, `invalidState`, `invalidTimeout`; the former `proxyUnsupported` and `tlsOptionsUnsupported` are `invalidProxy` and `invalidCACertificate` with the platform text (D76). `invalidProxy` and `invalidCACertificate` carry the whole message, proxy text per D79 with `user:password@` shown as `***@` (F932, F1086); a timeout of zero or less is `invalidTimeout` when the options are built (D77, F924, F1079).
Rule: the redirect refusals read `Too many redirects (exceeded N)`, `Refusing to follow redirect to non-HTTP(S) URL: <next>` and `Refusing to replay request body across HTTPS->HTTP redirect: <current> -> <next>` (D16, F927); token failures read `Token response is not valid JSON`, `...is not a JSON object`, `...missing or empty access_token field` (D07, F926); `ApiError` renders `Response headers: {"a":"b"}` as sorted compact JSON (D17, F922); lists in messages render `[a, b, c]` (D20, F928).
Rule: a supplied `URLSession` keeps the SDK redirect rules: the SDK `SessionDelegate` is attached to each task (`URLSessionTask.delegate`, macOS 12 and iOS 15 are the floor), so credential stripping, the scheme and downgrade refusals and the hop cap still run (F1047). A cross-origin hop also drops every header whose name contains `key`, `token` or `secret` (the S15 rule), so default headers and custom authenticator headers are covered without being listed (F1093).
Rule: object-valued query and header parameters (a model or a map, D47) expand by style: form exploded is one `name=value` pair per field, form not exploded and simple are `name,value,name,value`, deepObject is `param[name]=value`; fields are the wire names, sorted by name (`JSONEncoder` has no declared-order mode), nulls dropped, and a nested field is a `SerializationError` (F1143). A per-call `HttpAwareAuthenticator` receives the API's client (F856). The file header prints the info description through `unescapeDocComment` (F1156), and an apiKey name goes into the strip-list through `swiftString` (F1154).
Rule (owner default, ported from the php, kotlin and go branches): a 401 on a request authenticated by an OAuth2 or OIDC authenticator calls `AccessTokenInvalidating.invalidateAccessToken()` (public protocol in `http_aware_authenticator.mustache`), fetches the headers again (a refresh grant first) and replays the request once; a failed re-authentication leaves the 401 (F855). The authorization-code URL carries a PKCE S256 `code_challenge` and the code exchange sends the matching `code_verifier` once (SHA-256 is hand-written in `PKCE`, because CryptoKit is not on Linux; no OIDC nonce, since no ID token is validated) (F858). OIDC discovery rejects an `issuer` that differs from the discovery URL minus `/.well-known/openid-configuration` (a document without an issuer is accepted) and an authorization or token endpoint that is not https unless it is http on a loopback host (F859). A `noRedirect` request (the token POST) omits `TransportOptions.defaultHeaders`; OIDC discovery still carries them, because it must follow redirects (F860). Models with a `format: password` property are `CustomReflectable` and show it as `***` in `print`, `dump` and `String(describing:)` (F867; `hasPasswordVars` is set in `postProcessModels`). `ApiError.errorDescription` shows at most 1024 body bytes then `... (N more bytes)`, control characters as `\xNN`, and `***` for authorization, cookie, set-cookie, challenge headers and any header named like key, token or secret (F1080). The date-only fallback in `parseDate` takes exactly `yyyy-MM-dd`, so a date-time without an offset is rejected (F1163, F1164).
Not taken in batch 7: monotonic expiry clocks (F1054; `ContinuousClock` needs macOS 13 and `systemUptime` stops during sleep), per-operation security schemes (F857), a response size limit (F1048, a new transport option), the allOf discriminator protocol (F057, S4 still not landed), and the `.unsafeFlags` in `Package.swift` (F1095: SwiftPM 6.2 `treatWarning` would raise the tools-version, and the flags are also what the spec build relies on).

### Decisions taken in batch 9
Rule: F855 (invalidate the cached token on a 401 and replay once), F858 (PKCE S256) and F1080 (rendered `ApiError` capped at 1 KiB, control characters as `\xNN`, credential headers masked) were already in place from batch 7 and are unchanged; F825 is rust only.
Rule: `TransportOptionsBuilder.maxRetries(_:)` (default 2, 0 disables, negative rejected by `build()`) turns on automatic retries in `DefaultApiClient.sendRequest(...refreshHeaders:)`: network errors before a response (a `NetworkError` that is not a `NetworkTimeoutError`) and statuses 408, 409, 429 and 500 to 599 except 501; the wait is `Retry-After` (seconds or HTTP date, capped at 60 s) or 0.5 s doubling to 8 s with up to 25% taken off by jitter (`DefaultApiClient.retryDelay`); a wait that does not fit in what is left of the one `timeout` budget is skipped and the last outcome stands, and each attempt gets only the remaining time as its deadline; an `InputStream` body and a `noRedirect` (OAuth2 token) request are never retried; `BaseApi` passes the authenticator's `authHeaders()` as `refreshHeaders`, so each retry merges fresh authentication headers over the original ones, and the request is rebuilt each time (new request id). A caller-supplied `ApiClient` is called once (F823).
Why: Stainless-style default; the transport owns the loop so the deadline and cancellation (`Task.sleep` throws `CancellationError`) are enforced in one place.
Where: `transport_options.mustache`, `default_api_client.mustache` (`sendRequest`, `retryDelay`, `sendOnce`), `base_api.mustache` (`send`), `readme.mustache`, `skills.mustache`.
Test: `DefaultApiClientUnitTests` (retry count, `Retry-After`, 400/404/501 not retried, budget, `maxRetries(0)`, stream body, token request, auth refresh, backoff and jitter), `TransportOptionsTests`. Tests that expect one failing request through `DefaultApiClient` set `maxRetries(0)`.

### Decisions taken in batch 10
Rule: `ServerConfiguration.url(variables:)` substitutes every variable in one pass over the template, so a placeholder introduced by a substituted value is never expanded again, whatever the dictionary order (F1118). Authenticator cookies go out sorted by name through `BaseApi.cookieHeaderValue` (F1089). A token response whose `token_type` is not `bearer` (any case) is an `OAuth2TokenError.invalidResponse("Unsupported token_type in token response")`; an absent or null one is accepted (F863). `OAuth2AuthorizationCodeError.codeNotExchanged` reads `Must call exchangeCode() before making API requests` (F1101, D09).
Rule: token expiry and the OIDC discovery expiry are read off `MonotonicClock` (`CLOCK_MONOTONIC` on Apple platforms, which counts sleep; `systemUptime` elsewhere), so a wall-clock step does not keep a token too long or drop it at once (F1054; supersedes the batch 7 "not taken"). A caller cancelled while waiting for a shared token refresh stops waiting at once with `CancellationError` (`OAuth2TokenManager.wait(for:)`), and the refresh still finishes and caches for the others (F1053).
Rule: README and SKILLS follow the java and kotlin layout: Installation and Usage after Requirements, then Cancellation, Custom HTTP client, Raw responses, timeouts and request ids, and Retries as sections of their own above Caveats, and XML bodies, Link Objects and mutual TLS under Not supported (F883, F884, F888, F889, F890). The SKILLS Models sample builds the first model with its required fields (F887). Every test is named `testCamelCase` (F1108).
Why: audit proposals, taken as the default; only the token-type, cookie order, server-variable and expiry changes touch behaviour, none of them the wire for a conforming server.
Where: `server_configuration.mustache`, `base_api.mustache`, `auth/oauth/oauth2_token_manager.mustache`, `auth/oauth/openid_connect_authenticator.mustache`, `errors/oauth2_authorization_code_error.mustache`, `readme.mustache`, `skills.mustache`. No public API is added (`MonotonicClock` and `cookieHeaderValue` are internal).
Test: `ServerConfigurationTests`, `BaseApiTests` (cookie order, MIME table, non-BMP filename), `OAuth2TokenManagerTests` (token type, monotonic clock, cancellation), `OAuth2AuthCodeAuthenticatorTests`, `BearerAuthenticatorTests` (Unicode-space token), `TransportOptionsTests` (default User-Agent, IPv6 proxy), `DefaultApiClientTests` (3xx without a usable Location), `DefaultApiClientUnitTests` (MIME table, non-BMP filename, `close()` default), `ObjectSerializerTests` (failure text carries the detail, constraint keywords, byte-array equality), `ComposedSchemaTests` (anyOf declaration order).
Not taken in batch 10: the allOf discriminator protocol (F057, S4 still not landed); reserved names with backticks (F872, changes goldens and the reserved-word spec); `.unsafeFlags` and test-only packages in `Package.swift` (F1095, as in batch 7); a response size limit (F1048, a new transport option); per-operation security (F857); default headers on OIDC discovery (F860: discovery shares the redirect-following request, and the public `ApiClient` has no flag to drop default headers without also refusing redirects); a released intermediate redirect response (F1140) and a caller stream left open (F1151), which the stubbed protocol cannot observe; the required-date-parameter guard (F433), which needs a spec parameter; F1150 holds by design, the SDK's own proxy credential answers the proxy challenge and is never a request header.

## Known limitations and out of scope

- Linux has no proxy support (`ConfigurationError.invalidProxy`) and no CA pin (`ConfigurationError.invalidCACertificate`); SOCKS proxies are rejected by design (README). Reason: FoundationNetworking.
- `int` and `integer` map to `Int`, not `Int32`, so a value above `Int32` range is accepted for `int32` fields and an integral-looking `1.0` is accepted (D54, F006, F076; the API-breaking `Int32` mapping was proposed and not applied).
- Query, form and multipart parameters are held in `[String: ...]` dictionaries, so their wire order is not the spec order and multipart order is nondeterministic (D53, F087, parked).
- Not applied yet, intended per the ledger: duplicate-key and required-default handling in `init(from:)` for D62 and D63, and the `Content-Encoding` header removal of D92.
- Declared-charset decoding is lenient UTF-8 with a fallback; windows-1252, shift_jis and iso-8859-x bodies can decode as mojibake (F258, open for swift).
- The required date parameter guard has no petstore fixture (F433, needs a spec change). Generated tests no longer compare non-optional values to `nil` or test always-true casts (F525). `swift build -strict-concurrency=complete` (`make analyse`) is run by no spec (F382); test sources get weaker strictness than sources (F193, deferred).
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
