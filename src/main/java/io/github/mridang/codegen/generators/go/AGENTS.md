# Go SDK generator

`BetterGoCodegen` (generator name `go-plus`) generates a Go module: one file per API tag, one struct per model, a generic `Set[T]`, an `ApiClient` interface with a `DefaultApiClient`, authenticators, sentinel and typed errors, and a `_test.go` suite. The HTTP stack is the standard library `net/http`; the SDK adds `brotli` and `klauspost/compress` for response decoding and `google/uuid`, and the transport accepts a caller-supplied `HttpDoer` or `http.RoundTripper`. JSON is `encoding/json` with HTML escaping off. Generated tests run with `go test` through `gotestsum` (JUnit XML, `-race`), with `testcontainers-go` for the Squid and Chasm fixtures. The floor is `go 1.25` in `go.mod` (the README states it), the Docker toolchain is `golang:1.26-alpine`, formatting is `gofmt -s` plus `gofumpt`, and `go vet` and `staticcheck` gate the code. The module also builds for `GOOS=wasip1 GOARCH=wasm` (`GoWasmSpec`).

## Read first

- Shared cross-language contract and decisions: `../AGENTS.md` (`src/main/java/io/github/mridang/codegen/generators/AGENTS.md`)
- Repository root: `/AGENTS.md` (workflow, commands, guardrails)
- Codegen class: `BetterGoCodegen.java` (this folder)
- Templates: `src/main/resources/templates/go/`
- Specs: `src/spec/java/io/github/mridang/codegen/spec/go/` (`GoSpec`, `GoBuildSpec`, `GoClientSpec`, `GoFormattingSpec`, `GoLintingSpec`, `GoReservedWordsSpec`, `GoWasmSpec`)
- Golden: `src/spec/resources/generated/go/`
- Parity ledger: `PARITY_DECISIONS.md` / `.json` (the D and F ids below), `PARITY_FINDINGS.json`, `etc/api-surface-allow.json` (the go API-surface allowances)

## Layout

| Path | What lives there | Edit this to change |
|---|---|---|
| `BetterGoCodegen.java` | type mappings, PascalCase naming with upper-case initialisms, default literals, pointer rules for optional and recursive properties, per-scheme authenticator rendering, server enums, `go.mod` and file list | a type mapping, an identifier rule, a default-value literal, the file list |
| `templates/go/errors/*.mustache`, `errors_exports.mustache`, `api_error.mustache` | the `pkg/errors` package (`ApiError` and the status-specific errors, `NetworkError`, `NetworkTimeoutError`, `SerializationError`, `OAuth2ServerError`, `OAuth2TokenError`) and the re-exports | error classes and their rendering |
| `templates/go/auth/` and `auth/oauth/` | `Authenticator` interface, API key, basic, bearer, per-scheme wrappers, OAuth2 token manager and flows, OpenID Connect | authentication |
| `default_api_client.mustache`, `transport_options.mustache` | `net/http` client build, redirect policy, timeout budget, proxy, TLS, decompression, `Close`, injected `HttpDoer` and `RoundTripper` | transport options and their validation |
| `base_api.mustache`, `api/api.mustache`, `api/options.mustache` | request assembly, response decoding, per-operation methods and `opts.*Options` structs | an operation signature or request/response handling |
| `object_serializer.mustache`, `value_serializer.mustache`, `header_selector.mustache`, `path_utils.mustache`, `models/` | JSON and parameter encoding, models, `Set`, `DateTime`, `Duration` | models and wire encoding |
| `configuration.mustache`, `client.mustache`, `servers.mustache`, `server_configuration.mustache` | `Configuration` and builder, `Client` facade, server tables | client construction |
| `readme.mustache`, `skills.mustache`, `go_mod.mustache`, `makefile.mustache`, `golangci.mustache`, `dev_dependencies.mustache` | README, SKILLS.md, module manifest, tool block and lint config | README sections, dependencies, lint |
| `templates/go/test/` and `test/api/` | generated `_test.go` files | generated tests |

## Idioms (deliberate differences from the other eleven)

| Topic | What go does | Why |
|---|---|---|
| Errors | caller mistakes are exported sentinels matched with `errors.Is` (`ErrMissingRequiredParameter`, `ErrInvalidPathParameter`, `ErrInvalidProxyURL`, `ErrInvalidTimeout`, `ErrInvalidCACertificate`, `ErrTransportConflict`, `ErrInvalidServerVariable`, `ErrClientClosed`, ...); SDK errors implement `Error`, `Unwrap` and `As` | no exceptions; the error protocol is the unwrap chain |
| Messages | error strings start lower case (`missing the required parameter`, `too many redirects`) | staticcheck ST1005 forbids a capitalised error string |
| Cancellation | `ctx context.Context` is the first parameter of every operation (S7) | Go convention, the one deliberate shape difference |
| Builders | package-level `NewConfigurationBuilder()` and `NewTransportOptionsBuilder()`, setters named `val` | no static methods |
| Overloads and defaults | `*WithHTTPInfo` result variants, `GetAccessTokenWithRefresh`, `WithClientAuthMethod`, `requestContentType ...string` on multi-consumes operations | no default arguments or overloads |
| Optional parameters | an `opts.<Op>Options` struct per operation; required file or reader fields are pointers or interfaces checked for nil at call time | struct fields cannot be required |
| Inheritance | `BaseAuthenticator` is an embedded empty struct; no implementation inheritance for models | embedding, not subclassing |
| Unions | oneOf is a struct holding an `any` value with `MarshalJSON` and `UnmarshalJSON` that tries each variant in declaration order or dispatches on the discriminator | no sum types |
| Support types | generic `Set[T]`, `DateTime`, `Duration` (ISO-8601 and protobuf forms), `iso8601` helpers, a `TraceContextPropagatorFunc` hook | stdlib gaps; no optional import to detect OpenTelemetry |
| Zero values | a model struct can be built without its required properties; decode enforces them | the zero-value struct is forced (F227) |
| Immutability | `ApiResult.headers` is a `maps.Clone` and documented "do not mutate" | Go has no immutable map |
| Numbers | `format: number` is `float64`, documented lossy | no stdlib decimal |
| Optional description | `ServerConfiguration`/`ServerVariable` description is an empty string when absent | empty-string sentinel shared with rust, swift and dart; metadata only |
| Naming | initialisms in upper case (`URL`, `ID`, `HTTP`) | Effective Go |

## Settled decisions (do not re-open in review)

Each cross-language decision is stated once in the shared file; here only how go realises it. Where the Rule line says "intended" the rule is the ledger's canonical behaviour for go and the templates were not confirmed to implement it when this file was written; check the template before relying on it. Decisions on which go needs no go-specific rule are listed at the end of this section.

### Strict containers (S1, F006, 2026-11-16)
Rule (intended): a container-of-primitives response (`map[string]int32`, `[][]int32`) is checked element by element like a single primitive, so a fractional, quoted or out-of-range leaf is a `*SerializationError`. A single primitive already behaves this way (F006, F076, F205, F213, F310 recorded as conformant in the ledger).
Why: the container path bypassed the primitive check in seven languages; all twelve now behave alike.
Where: `object_serializer.mustache`, `base_api.mustache`.
Test: `test/object_serializer_test.mustache` and the `getInventory`/`getMatrix` rows in `test/api/store_api_test.mustache`.

### Content parameters use the SDK serializer (S2, F054, F068, D50, D80)
Rule: a `content: application/json` parameter is encoded by the package `serialize()` (an `Encoder` with HTML escaping off) and its `*SerializationError` is returned from the operation before any request. The query, options and required arms are landed; header and path arms are intended (F054 is open in the ledger). A `content:` cookie is serialised by `serialize()` and then percent-encoded by `encodeContentCookieValue`, keeping only A-Z a-z 0-9 - . _ ~ literal (F833, D52), and still passes `isValidCookieValue`.
Why: `json.Marshal` escaped `<`, `>`, `&` and its error was discarded, so the bytes differed from the other eleven.
Where: `api/api.mustache`, `object_serializer.mustache`.
Test: `searchStore.q` rows in `test/api/store_api_test.mustache`.

### Schema constraints are not enforced (S3, F056, D55)
Rule: the client does not enforce `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems`, `maxItems`; go has no native enforcement, so the server owns validation. The README must carry a "Schema constraints" section (intended; confirm the heading in `readme.mustache`).
Why: only python gets it natively and cheaply through pydantic; hand-written enforcement would differ per language.
Where: `readme.mustache`.
Test: none: asserting absence is not worth a test; the README text is the contract (`test/readme_test.mustache` pins its sections).

### allOf with a discriminator (S4, F057, F802)
Rule (intended, not implemented): a child embeds a shared base struct, and decoding goes through an interface plus a function that reads the discriminator; a model reused in two unions is never re-parented. Today only oneOf with a discriminator is realised: `models/model.mustache` generates an `UnmarshalJSON` that reads the discriminator, dispatches on `mappedModels`, and returns `unknown discriminator value %q for <Class>` for an unmapped value (the other eleven also reject it).
Why: structs cannot subclass; embedding keeps the shared fields once and the interface gives callers the common surface. F057 is still open in the ledger.
Where: `models/model.mustache`, `BetterGoCodegen.java`.
Test: `test/composed_schema_test.mustache` (oneOf cases only).

### Required and nullable (S5, F215, D66)
Rule (intended): a required nullable property is a pointer with a JSON tag without `omitempty`; decode requires the key (absent is an error), an explicit `null` is kept as `nil`, and encode always writes the key (`null` when `nil`). A required non-nullable property already fails on a missing key or `null` in the generated `UnmarshalJSON`, and a required property with a default (a synthetic discriminator default) is filled when absent (F129, F170, F320, intended).
Why: the canonical behaviour is shared by all twelve; F215 is open in the ledger.
Where: `models/model.mustache`, `models/json_marshal.mustache`.
Test: `test/model_test.mustache`, `test/composed_schema_test.mustache` nullable `label` case.

### uniqueItems order (S6, F319, D69)
Rule: sets are sent in the language's natural order; for go `Set[T].MarshalJSON` sorts a copy of scalar members (string, int, uint, float, bool kinds, enums included) and leaves other members in insertion order. Do not sort elsewhere.
Why: a `map`-backed set iterates in random order, so unsorted bytes would differ per run. `T` stays `comparable`, so there is no public type change.
Where: `models/set.mustache`.
Test: the `Set` tests in `test/model_test.mustache`.

### Cancellation (S7, F228)
Rule: every operation takes `ctx context.Context` as its FIRST parameter, before the path parameters, body, options and server. The client owns a base context created with `context.WithCancel(context.Background())`; `Close()` cancels it, and `mergeContexts` (`context.AfterFunc`) cancels the call when either the call context or the base context ends. Cancellation returns `request cancelled: context.Canceled` (and also wraps `ErrClientClosed` when `Close` caused it), never `*NetworkTimeoutError`. Landed in batch 4.
Why: `ctx` first is the Go convention and what every go caller already passes; a wrapper handle would be less natural. F228 was open for go before batch 4.
Where: `api/api.mustache`, `default_api_client.mustache`, `client.mustache`.
Test: `test/default_api_client_test.mustache`, `test/default_api_client_unit_test.mustache`, `test/api/pet_api_test.mustache`.

### Parameter order (S8, F803, owner confirmation pending)
Rule (intended): required parameters, then `server`, then the options struct, then the trailing `requestContentType ...string` on multi-consumes operations; `ctx` stays first. Adding security never moves an argument.
Why: callers must not break when a spec gains security on an operation. F803 is still open in the ledger.
Where: `api/api.mustache` (the `signatureArgs` loop), `api/options.mustache`.
Test: server-override rows in `test/api/pet_api_test.mustache`.

### Injected transport (S9, F405)
Rule: `TransportOptionsBuilder.HttpClient(HttpDoer)` accepts any value with `Do(*http.Request) (*http.Response, error)` (an `*http.Client` is wrapped), and `TransportOptionsBuilder.RoundTripper(http.RoundTripper)` wraps the caller's round tripper with the SDK defaults. Supplying both, or combining one with the SDK's own proxy, TLS or pool settings, fails `Build()` with `ErrTransportConflict`; the SDK never closes a caller-supplied client. Landed.
Why: OpenAI, Stainless, Speakeasy, Fern, Azure and Stripe SDKs all accept the standard client. Ledger F405 still lists the other languages; go is done.
Where: `transport_options.mustache`, `default_api_client.mustache` (`newPolicyClient`, `locationGuardTransport`), `client.mustache`, `readme.mustache`.
Test: `test/transport_options_test.mustache`, `test/default_api_client_test.mustache`.

### Dependencies (S10)
Rule: a small pure module or a version bump is acceptable when it standardises behaviour; never a module that needs a native binary (cgo). `brotli`, `klauspost/compress` and `google/uuid` are accepted; the tool block pins `gotestsum`, `staticcheck`, `gofumpt` and `gocover-cobertura`.
Why: a native-binary dependency makes the client harder to install and test.
Where: `go_mod.mustache`, `dev_dependencies.mustache`.
Test: `GoBuildSpec`, `GoWasmSpec`.

### Scope order, UUID case, ASCII blank keys, error cause (S14, D04, D19, D67)
Rule: OAuth scopes keep the spec's declared order (`formatGoScopes`, `scope=pets%3Aread+pets%3Awrite`); `uuid` is written lowercase; an API key or bearer token is blank only when `strings.Trim(s, " \t\n\v\f\r") == ""`, not `TrimSpace`, and a leading `Bearer ` is stripped in the constructor (the ASCII set and prefix strip are intended); every status-specific error accepts an optional cause through the `Unwrap` chain.
Why: `TrimSpace` strips Unicode whitespace the other eleven keep.
Where: `BetterGoCodegen.formatGoScopes`, `auth/bearer_authenticator.mustache`, `auth/api_key_authenticator.mustache`, `errors/*.mustache`.
Test: `test/oauth2_client_credentials_authenticator_test.mustache`, `test/bearer_authenticator_test.mustache`, `test/api_key_authenticator_test.mustache`, `test/api_error_test.mustache`.

### Secrets are redacted in string forms (S15)
Rule (intended): the `String()` forms of `Configuration` and `TransportOptions` mask the proxy password and any default header named `Authorization`, `Proxy-Authorization`, `Cookie`, or containing key, token or secret, as `***`.
Why: a logged client must not leak credentials.
Where: `configuration.mustache`, `transport_options.mustache`.
Test: `test/configuration_test.mustache`, `test/transport_options_test.mustache`.

### Timeouts, redirects, validation timing (S16, D76, D77, D79, D72, D73, F121)
Rule: one end-to-end timeout budget; a caller-supplied stream or file handle is never closed; proxy URL validation happens in `Build()`, not in the builder setter (the setter stores `proxyErr`); `caCertPath` is validated even when `verifySsl` is false. `Build()` rejects a zero or negative timeout with `ErrInvalidTimeout` and the canonical text `invalid timeout: must be a positive number of milliseconds (got N)` (D77, F121, F924, batch 6); `nil` is the only way to ask for no timeout.
Why: the budget used to apply per hop; redirects could multiply it.
Where: `default_api_client.mustache`, `transport_options.mustache`.
Test: `test/transport_options_test.mustache`, `test/default_api_client_test.mustache`.

### OIDC rediscovery keeps the session (D01, F016, F230)
Rule (intended): the token manager owns the exchanged flag (an `atomic.Bool`), and `resolveDelegate` builds the replacement authenticator around the previous delegate's `*OAuth2TokenManager`; the mutex is already held across the discovery fetch. The api-surface allowance still lists the flag as kept on the authorization-code authenticator, which is today's shape.
Why: replacing the delegate reset the session, so every call after the discovery max-age failed with "Must call ExchangeCode()".
Where: `auth/oauth/openid_connect_authenticator.mustache`, `oauth2_auth_code_authenticator.mustache`, `oauth2_token_manager.mustache`.
Test: `test/openid_connect_authenticator_test.mustache`.

### Refresh grant and exchange (D02, D03, F017, F119)
Rule: `GetAccessTokenWithRefresh(tokenURL, refreshURL, params, headers)` posts a held refresh token to `refreshURL` with `client_id`/`client_secret` copied from the params and the same extra headers, and falls back to the original grant at `tokenURL`; `Exchange(tokenURL, params)` runs under `m.mu`, saves and clears the session, posts verbatim, and restores the old state on failure; `ExchangeCode` calls only it. The refresh method is landed (batch 6); the exchange is landed with the flag still on the authenticator.
Why: RFC 6749 sec 6 and 3.2.1; go has no default arguments, hence the separate method (an API-surface allowance).
Where: `auth/oauth/oauth2_token_manager.mustache`, `oauth2_password_authenticator.mustache`, `oauth2_auth_code_authenticator.mustache`.
Test: `test/oauth2_token_manager_test.mustache`, `test/oauth2_auth_code_authenticator_test.mustache`.

### OAuth wording, grammar and request order (D05, D07, D08, D09, D10, F292)
Rule: the token body and the authorize URL are emitted in the fixed order grant_type, code, refresh_token, username, password, client_id, client_secret, redirect_uri, scope, then the rest sorted (`encodeTokenForm`, landed in batch 6), escaped with `url.QueryEscape` and never `url.Values.Encode`. Intended: OIDC always requests `openid`; token and discovery errors use the shared wording ("token response is not valid JSON", "is not a JSON object", "missing or empty access_token field") with no parser detail; `expires_in` and `max-age` use one digits-plus-fraction grammar with a one-year ceiling applied before the `int64` conversion; precondition texts are "authorization code must not be empty" and "must call ExchangeCode() before making API requests" (today the not-exchanged sentinel carries an `oauth2 authorization code:` prefix).
Why: map order and per-language wording made bytes and messages differ.
Where: `auth/oauth/*.mustache`, `errors/oauth2_token_error.mustache`.
Test: the `test/oauth2_*_test.mustache` and `test/openid_connect_authenticator_test.mustache` files.

### Implicit flow, empty state, sync auth accessor (D06, D11, D12, F290)
Rule: an empty implicit access token counts as unset (already conformant) and an empty `state` is absent in authorize URLs (already conformant). The infallible `AuthHeaders()` stays on the `Authenticator` interface and returns an empty map when the token cannot be obtained; `AuthHeadersOrError()` is the loud path and is the one the transport calls. Removing the `log.Printf` from the password and auth-code variants so all four are silent is intended.
Why: the interface method cannot return an error. F290 is an open API-shape question, not a settled rule: failing loudly would change the interface signature in every authenticator and in every caller of the interface, and no decision has been taken.
Where: `authenticator.mustache`, `auth/oauth/oauth2_implicit_authenticator.mustache`, `base_api.mustache`.
Test: `test/oauth2_implicit_authenticator_test.mustache`.

### Query, form and multipart order (F087)
Rule: query parameters, `application/x-www-form-urlencoded` pairs and multipart parts are emitted in the spec's declaration order, never in map iteration order: `orderedKeys(m, order)` in `base_api.mustache` sorts the keys by the order the operation passes, array members expand in place, and the form body is joined in order with the same `QueryEscape` rules. Landed.
Why: Go maps iterate in random order, so the wire bytes differed per run. The ledger still shows F087 parked for the shared step; go is done.
Where: `base_api.mustache`, `api/api.mustache`.
Test: `test/base_api_test.mustache`, `searchStore` and `setPetPreferences` rows in the pet and store API tests.

### Unusable responses carry the real headers (D13, F099)
Rule: a response that arrived and could not be used (decompress failure, redirect refusals) is an `ApiError` carrying the real status and the real lower-cased headers; go already passes the response headers on both paths.
Why: a response that arrived is not a "no response" error.
Where: `default_api_client.mustache`.
Test: `test/default_api_client_test.mustache`.

### Error text matrix (D14, D15, D16, D17, D20, D28, D29, F245, F260, F267)
Rule: serializer messages are "failed to serialize object to JSON: ..." and "failed to deserialize JSON: ..." (lower case forced); redirect refusals read "refusing to follow redirect to non-HTTP(S) URL: <next>", "refusing to replay request body across HTTPS->HTTP redirect: <cur> -> <next>" and "too many redirects (exceeded N)" with the `*url.Error` unwrapped by `redirectRefusalMessage` and no `redirect refused:` prefix (landed, batch 6, F927); the "Response headers:" line is compact JSON with sorted keys written by a `json.Encoder` with `SetEscapeHTML(false)` (`compactHeadersJSON`, landed, batch 6, F922); list values render `[a, b, c]` through `strings.Join` (intended); the missing-parameter message names the caller-facing identifier; use-after-close wraps `ErrClientClosed`, whose text is "API client has been closed and can no longer send requests" (the forced "API client" wording).
Why: one message grammar across all twelve; the lower-case first letter is the only forced difference (ST1005).
Where: `api_error.mustache`, `errors/*.mustache`, `object_serializer.mustache`, `default_api_client.mustache`, `server_configuration.mustache`, `api/api.mustache`.
Test: `test/api_error_test.mustache`, `test/default_api_client_test.mustache`, `test/server_configuration_test.mustache`.

### Error bodies are depth-capped and HTML-unescaped
Rule: `pkg/errors` carries its own `errorBodyJSONDepth` and `unmarshalErrorBody`, duplicated because the errors package cannot import the root package; `FromResponse` and `GetTypedErrorBody` never call a raw `json.Unmarshal`. The serializer uses `json.Encoder` with `SetEscapeHTML(false)`, so `<`, `>` and `&` go on the wire literally. A leading BOM is stripped in both the error path and the success path (F268, conformant).
Why: a non-2xx body must not be a stack-overflow vector, and the bytes must match the other eleven.
Where: `errors/*.mustache`, `object_serializer.mustache`.
Test: `test/api_error_test.mustache`, `test/object_serializer_test.mustache`.

### Header injection checks are case-insensitive (D21, F299)
Rule: `hasHeader` compares names with `strings.EqualFold`; if the caller supplied any case variant of `User-Agent` or `X-Request-ID`, even empty, nothing is injected and the caller's key and value stay as written. Landed (batch 5).
Why: RFC 9110 sec 5.1.
Where: `default_api_client.mustache`.
Test: `test/default_api_client_test.mustache`.

### Response body decoding (D30, D31, D32, D33, F033, F085, F149, F150, F151, F155)
Rule (intended, partly): a binary return is raw bytes and never JSON-parsed (an `isBinary` branch before the JSON test in `api/api.mustache`); a `format: byte` return is JSON by default with one strict base64 decoder and every failure a `*SerializationError` (already conformant); a text-class content type on a `*string` return gives the text (branch exists); any other non-JSON content type on a non-binary return is a `*SerializationError` naming the content type (intended; F149 is open); a binary request body is sent unchanged under any selected content type through `invokeApiParams.rawBody` (landed, batch 6).
Why: five different outcomes across the twelve SDKs.
Where: `base_api.mustache`, `api/api.mustache`.
Test: `test/base_api_test.mustache`.

### Form values and multipart scalars (D39, D53, F216, F214, F254)
Rule: a map, struct or array of composites in a form-urlencoded body is a `*SerializationError` ("form-urlencoded field <name> must be a scalar or array of scalars"), values with a `String` method (time, `DateTime`, uuid) count as scalars; in multipart a scalar (including date-time through `isTemporalScalar`) is a plain text part written with `stringify(v)` and only maps and model structs become `application/json` parts. Landed (batches 5 and 6).
Why: `%v` emitted go-specific repr text.
Where: `base_api.mustache`.
Test: `test/base_api_test.mustache`, pet API tests for `setPetPreferences` and `uploadPetDocument`.

### Parameter encoding (D49, D50, F066, F069, F086, F197, F199, F239, F341)
Rule: `format: byte` parameters (and each element of an array of them) are standard base64 with padding (`stringify` and `toStringSlice` treat `[]byte` as a scalar); `allowReserved` keeps only the RFC 3986 query-legal reserved set literal and always encodes `#`, `[` and `]` (`encodeQueryAllowingReserved`); query and form encoding keep exactly `A-Za-z0-9-._~` literal (already conformant, lock test intended).
Why: the same bytes in all twelve.
Where: `value_serializer.mustache`, `base_api.mustache`.
Test: `test/value_serializer_test.mustache`, `test/base_api_test.mustache`.

### Path parameter dot segments (D48, F064)
Rule: a path parameter of exactly `.` or `..` returns `ErrInvalidPathParameter` ("path parameter '<name>' must not be '.' or '..'") from `requirePathParam` before any request; `a..b` and `.hidden` are accepted. Landed (batch 5).
Why: a dot segment would be normalised away from the intended resource.
Where: `value_serializer.mustache`, `path_utils.mustache`.
Test: `test/value_serializer_test.mustache`.

### Models: field remarks, additionalProperties, uri, byte (D56, D58, F025, F055, F132, F138, F081)
Rule: `readOnly` and `writeOnly` properties carry only a doc remark and have no wire effect; `MarshalJSON` passes `AdditionalProperties` through `withoutDeclaredKeys` so a declared property wins and the key is written once; each `isUri` field is checked by `checkAbsoluteURIField` and stays a `string`; `[]byte` fields (`hasByteFields`) are checked by `checkBase64Fields` against a strict RFC 4648 pattern before decoding. Landed (batches 5 and 6).
Why: one wire rule per feature in all twelve.
Where: `models/model.mustache`, `models/json_marshal.mustache`, `BetterGoCodegen.java`.
Test: `test/model_test.mustache`.

### Per-operation server types (D42, F122, F145)
Rule: a server variable gets one type per operation; a name occurring in two or more variants of the operation becomes `<VariantName><Var>`, a variable used once keeps its plain name (`enrichOperationServers`, computed once), so petstore gets `RegionalRegion`, `MirrorRegion` and `Channel`.
Why: duplicate type names failed to compile.
Where: `BetterGoCodegen.java`, `api/api.mustache`.
Test: `getMultiServerPetInfo` rows in `test/api/pet_api_test.mustache`.

### Identifiers, enums and literals (D40, D43, D44, D45, D46, F044, F047, F172, F363, F045)
Rule: a digit-leading or empty sanitised name gets a deterministic letter `n` before casing and never goes through the reserved-word escape; go escapes nothing for variables because PascalCase fields never collide with keywords (F309, F364 conformant); a model named `Error` becomes `ModelError`; enum string values are escaped in order (backslash, CR/LF/TAB, then the enum escape table) and the quote is escaped, never stripped, and the value sits inside a `/* */` comment that must stay comment-safe; every spec string written into a code literal goes through one value-preserving escaper (`formatGoScopes`, `toDefaultValue`; routing control characters through the shared escaper is intended).
Why: stripping or double-escaping changed values; the escape prefix made digit names illegal.
Where: `BetterGoCodegen.toModelName`, `toVarName`, `formatGoScopes`, `models/model.mustache`, `servers.mustache`.
Test: `GoReservedWordsSpec`, the enum golden and `EdgeCases` rows.

### Multi-consumes content type selector rejects unknown values
Rule: operations with more than one request content type end in `requestContentType ...string`; an unrecognised value returns an error (`ErrInvalidRequest`-style, message "unsupported request content-type '<ct>' for <op>; declared types are [a, b, c]") and no value selects the first declared type.
Why: a typo must not ride the wire as an undeclared Content-Type.
Where: `api/api.mustache`.
Test: `test/api/pet_api_test.mustache` (`uploadPetDocument`, `setPetAvatar`).

### Required-parameter messages
Rule: the sentinel is `missing the required parameter` (lower case, with "the") and the operation layer wraps it with the parameter and `<Class>.<operation>`; an empty required path parameter comes from `requirePathParam` in `value_serializer` and is wrapped the same way (the clause shape `: path parameter '<x>' must not be empty` is the one accepted difference); a nil options struct is the distinct go-only message `options are required when calling <Class>.<op>` (forced, F323).
Why: ST1005 forces the lower case, and the other SDKs cannot receive a null options object.
Where: `base_api.mustache`, `value_serializer.mustache`, `api/api.mustache`.
Test: `test/base_api_test.mustache`, `test/value_serializer_test.mustache`.

### Compression, empty bodies and decompression
Rule: an unsupported or chained `Content-Encoding` raises from the manual decompressor and the caller wraps it in an `ApiError` with the real status; an empty body (`len(raw) == 0`) passes through for any declared encoding; `deflate` is zlib-wrapped (`zlib.NewReader`).
Why: still-encoded bytes must never be returned; a 204 may carry a stale header.
Where: `default_api_client.mustache`.
Test: `test/default_api_client_unit_test.mustache`.

### Redirects, proxy, trust and Location
Rule: the HTTPS to HTTP body-replay guard anchors on the current hop (`via[len(via)-1]`); a `Location` that `req.URL.Parse` rejects is treated as missing by `locationGuardTransport`, so the 3xx is returned with its body; ambient proxy variables are ignored (the transport sets no `Proxy` function unless `TransportOptions` has a proxy, and the comment says so); `caCertPath` replaces the system trust store; the SDK's own `Proxy-Authorization` survives hops.
Why: replaying a TLS-protected body in cleartext or following an unparseable `Location` is wrong; one proxy source is predictable.
Where: `default_api_client.mustache`, `transport_options.mustache`.
Test: `test/default_api_client_test.mustache`, `test/transport_options_test.mustache`.

### ApiResult.headers is a copy
Rule: `NewApiResult` stores `maps.Clone(headers)` (conformant, F325); the field is documented "do not mutate"; read-only is impossible in go.
Why: a caller mutating the result must not alter the transport's map (F325).
Where: `api_result.mustache`.
Test: `test/api_result_test.mustache`.

### Deprecation markers
Rule: a deprecated operation carries a `// Deprecated:` doc paragraph on every public variant (`<Op>` and `<Op>WithHTTPInfo`).
Why: that is the marker `go vet` and `staticcheck` recognise.
Where: `api/api.mustache`.
Test: the golden.

### README floor
Rule: the README "Requirements" version states the `go.mod` `go` directive (1.25); the toolchain is bumped at regeneration.
Why: go enforces its floor as a hard build gate, so an understated README advertises a version the build rejects.
Where: `readme.mustache`, `go_mod.mustache`.
Test: `test/readme_test.mustache`.

### Decisions where go already conforms
Rule: no go-specific rule beyond the shared one for D18 and F268 (BOM stripped in the shared helper), D22 and F300 (`User-Agent` token is the module name), D26 and F324 (no default arguments: an optional body is passed as `nil`, forced), F080 (date-time decode is RFC 3339), F174 (numeric default literals), F206, F217 (uuid), F071 and F072, F090, F091, F094, F295, F020 (the `CACertPath` comment is reworded), F155 and F153, F391 (no `*/` in a `/* */` comment).
Why: confirmed against the ledger's per-language rows as already canonical.
Where: see the named templates in Layout.
Test: the matching generated tests; the ledger rows hold the per-decision lock.

## Known limitations and out of scope

Out of scope in every SDK, and the README says so (S11, closed backlog issues #53, #70, #78, #88, #90): callbacks and webhooks, XML bodies and `xml.nodeType`, Link Objects and mutual TLS. OpenAPI 3.2 and 3.1 are tracked by epics #49 and #96, and loading 3.2 documents is issue #50 (the pinned swagger-parser rejects it).

Open or deferred ledger items that touch go: F021 (`InjectTraceContext(headers)` takes no context, so the registered OpenTelemetry propagator never sees the caller's span; now that operations take `ctx` the hook can take it, deferred); F842 (a multi-consumes operation spends its only variadic slot on `requestContentType ...string`, so a later per-call `...RequestOption` (F827) would be a breaking change; needs an owner decision); F290 (the infallible `AuthHeaders()` returns an empty map on failure, open API-shape question, see Settled); F316 (a go model struct can be built with zero values and without validation, forced); F308 (integer enum members ignore the language casing, parked); F057 (allOf with a discriminator), F215, F054 and F803 are open in the ledger and described under Settled.

Not harmonised on purpose: the multipart file part sends a real filename and a sniffed MIME through the `*os.File` options types (the other nine send the field name and `application/octet-stream`); the OAuth2 token decode is typed, so a numeric `access_token` or `refresh_token` fails the exchange where ten SDKs ignore the bad field (borderline-forced); `ClientAuthMethod` has no `String()` and the OAuth2 `String` forms differ (F285); go model `%v` prints pointer addresses for optional fields (F321, no decision); a oneOf or anyOf wrapper with no match returns `data does not match any oneOf schemas for <X>` while the central helper says `No oneOf/anyOf variant matched the JSON` (F396). `format: number` is `float64` and lossy by design. SOCKS proxies are not supported.

## Build-breaking rules

- Generated files carry the `Code generated ... DO NOT EDIT.` marker. The root AGENTS.md says golangci-lint skips marked files and is not a live gate; `templates/go/golangci.mustache` now sets `linters.exclusions.generated: disable`, so check which is current before relying on either and never claim golangci-lint adds errcheck coverage until `GoLintingSpec` shows it.
- `go vet ./...` and `go tool staticcheck ./...` must pass (`GoLintingSpec`), so an error string must start lower case and a deprecated call must be marked.
- `gofmt -s` and `gofumpt` must produce no diff (`GoFormattingSpec`); the generator formats in the pinned `golang:1.26-alpine` image.
- Comments in templates are `/* */` blocks; a `//` inline comment inside a function is banned by the shared comment rule. A mustache `{{! }}` comment must not contain a literal `{{tag}}`.
- The module must build for `GOOS=wasip1 GOARCH=wasm` (`GoWasmSpec`), so no cgo and no OS-specific dependency.
- Tests run with `-race` and CPU is bounded; golangci-lint and the Go build cache live under `/root/.cache/go`.
- The generator and the golden must be regenerated together; the golden formatters run in Docker and can be flaky (see the root AGENTS.md).

## Decisions taken in batch 6

The audit's own proposal was the default wherever no owner decision existed.

- Header merge (F1044): every layer (transport defaults, configuration defaults, header selector, operation headers, auth headers) is merged by `mergeHeaders` in `default_api_client.mustache`: a key that differs only in case replaces the earlier one, the later layer keeps its spelling, and the keys of one layer are visited in sorted order so the result never depends on map iteration. `removeHeader` and `hasHeader` are case-insensitive, so a caller's `accept-encoding` suppresses the injected one and `Content-Type` is dropped on a bodyless request whatever its spelling. Test: `TestDefaultApiClient_HeaderSpellingsAreMergedCaseInsensitively` and `TestMergeHeaders_...` in `test/default_api_client_unit_test.mustache`.
- Token expiry (F854, F1046, F1045): one margin, applied when the token is stored: `expiry = now + expires_in - min(60 s, expires_in / 2)`, so a 60 s token is reused for 30 s and a 120 s token for 60 s. A grant response without `expires_in` (first fetch, refresh or exchange) leaves no expiry and the token is cached until invalidated; `expires_in <= 0` or unparseable still means stale on arrival. Tests that need a stale token use `"expires_in":0`. Test: `TestOAuth2TokenManager_ShortLivedTokensAreStillReused`, `..._RefreshWithoutExpiresInCachesTheNewToken`.
- OIDC rediscovery (F1052): when the discovery max-age lapses and the refetch fails, the previous delegate keeps serving (stale-if-error) and discovery is retried after 30 s; the first discovery still fails. `max-age=0` still refetches on every call (pinned by `TestOpenIdConnect_DiscoveryIsReusedForTheCacheControlMaxAge`; the floor the audit suggested was not taken). Test: `TestOpenIdConnect_FailedRediscoveryKeepsUsingThePreviousDelegate`.
- Query secrets in errors (F861): `redactURL` replaces the query of a URL with `***` in transport-error messages (`request failed: ...`) and in the redirect refusal messages; the original `*url.Error` stays the cause. Test: `TestDefaultApiClient_NetworkErrorMessageRedactsTheQuery`.
- Trace propagator (F880, race part): `traceContextPropagator` is an `atomic.Pointer`, so `SetTraceContextPropagator` may run while requests are in flight. Per-operation spans and metrics are not added. Test: `TestSetTraceContextPropagator_ConcurrentSetAndInjectIsRaceFree` (the suite runs with `-race`).
- F844 is already conforming: this file exists.

Skipped in batch 6, each needing a public go-only API change that the api-surface check and the other eleven SDKs must agree on first (the allow rules live in `etc/`): F842 (move `requestContentType` into a per-call option), F875 (`models.Date`), F876 (named inline parameter enums), F877 (return slices and maps by value), F878 (typed union constructors and accessors), F879 (per-tag service interfaces), F881 (exported `AllValues`/`IsValid`; `TestModel_InlineEnumValueListIsNotExported` pins the helper unexported today), F874 and F1053 (a context-aware auth method), F1048 (a response size limit option). F873 (single-pass model decode), F882 (operation doc comment layout, which the generated-prose alignment rules in `CLAUDE.md` pin) and F930 (no petstore operation has a nested object form field, so the check cannot be driven through the public API from `test/base_api_test.mustache`) need a larger change than could be verified without the Docker gate.

## Decisions taken in batch 7

The audit's own proposal was the default wherever no owner decision existed.

- Decoded responses (F1165, D92): when the body was decompressed, the returned headers drop `content-encoding` and `content-length`; an empty body or `identity` leaves them. Tests: `assertDecompresses` in `test/default_api_client_test.mustache`, `TestDefaultApiClient_DecodesZlibWrappedDeflateResponse`.
- Proxy URL in errors (F1086, D79): a rejected proxy value is shown with its `user:password@` part replaced by `***@` (`redactProxyUserinfo`, on the raw text because the value may not parse), and a `*url.Error` is unwrapped so the raw URL is not repeated. Test: `TestTransportOptions_ProxyRejectionMessages`.
- Cross-origin redirects (F1093): besides the fixed allowlist and the spec's apiKey header names, every request header that `isSecretHeaderName` treats as a secret (S15: key, token, secret, Authorization, Cookie, Proxy-Authorization) is removed on a cross-origin hop. Test: `TestDefaultApiClient_StripsSecretNamedHeadersAcrossCrossOriginRedirect`.
- Per-call authenticators (F856): `invokeApiForResult` injects the API's own client into a per-call override that implements `auth.HttpAwareAuthenticator`, so a per-call OAuth2 or OIDC authenticator no longer fails with `ErrApiClientNotInjected`. Test: `TestBaseApi_PerCallHttpAwareAuthenticatorIsInjected`.
- Auth requests carry no caller default headers (F860): every request that goes through `authApiClientAdapter` (token endpoint, OIDC discovery) is marked with `authRequestContextKey` and `SendRequestWithOptions` then skips `TransportOptions.DefaultHeaders`; User-Agent, X-Request-ID and the SDK's own headers stay. No exported API changed. Test: `TestDefaultApiClient_AuthRequestsOmitTheCallersDefaultHeaders`.
- File header (F1156): the info description in `api_info.mustache` goes through `unescapeDocComment`. Test: `TestMetadata_FileHeaderCarriesNoBackslashEscapedQuotes`.
- Tests only: F1169 (cookie from auth now asserts the header), F1063 (Set-Cookie is not replayed), F1067 (duplicate key keeps the last value), F1111 (StrictTag), F1144 (hostile Availability members), F1145, F1146, F1114, F1115, F1116, F1124 (slug), F1127 (required header and cookie guards), F1128 (createAccount), F1130 (trackingId), F1163 (offset-less date-time), F1075 (token request headers), F1159 (openid scope), F1151 (caller-owned handles stay open).
- Already conforming in go: F054 (the query, header, path and cookie content arms all use `serialize()`), F833, F854, F1044, F1045, F861, F922, F924, F925, F927, F931, F1142, F1079 (`Build()` rejects zero).
- Skipped, needing shared Java, the `etc/` allow rules or an owner decision: F057, F215 (settled S5), F228, F1062 (several 2xx schemas), F855 (401 replay), F1048 (response size limit option), F1080 (bounded error text), F1055 (injected-name collisions need `computeSignatureArgs`), F859, F867, F1084, F1121 (no operation takes an Accept header parameter, so it is not reachable from a go test).

## Verify

```
etc/ci-local.sh check Go
mvn verify -Dgroups=go
```

Inside the generated module: `make lint`, `make vet`, `make staticcheck`, `make test`, `make docs`.

## Batch 5 (landed)

- F826: `format: binary` request bodies and upload fields are `io.Reader` (`typeMapping` `file`), arrays are `[]io.Reader`; an `*os.File` still satisfies it and keeps its real filename and sniffed MIME in the multipart part. Tests: `test/api/pet_api_test.mustache` (`...AcceptsAnIoReader`), `test/skills_test.mustache`.
- F831: `TooManyRequestsError` (429, a `ClientError`) selected by `FromResponse`; test rows in `test/api_error_test.mustache`.
- F833: see Content parameters above; test `TestEncodeContentCookieValue_UsesTheUnreservedSetOnly` in `test/object_serializer_test.mustache`.
- F839: `CheckRedirect` restores the method and body on 301/302 for every method except POST (net/http demotes all of them); 303 and 307/308 already conform. Test `TestDefaultApiClient_RedirectMethodRewriting`.
- F824: `Close()` cancels the base context, closes idle connections of the owned transport only, and later calls return `ErrClientClosed`; test `TestDefaultApiClient_ClosedClientRejectsRequestsAndLeavesSuppliedDoerOpen`.
- F832: already conforming (the error names `Class.operationId`).
