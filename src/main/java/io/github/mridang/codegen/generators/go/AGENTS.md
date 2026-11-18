# Go SDK generator

`BetterGoCodegen` (generator name `go-plus`) generates a Go module: one file per API tag, one struct per model, a generic `Set[T]`, an `ApiClient` interface with a `DefaultApiClient`, authenticators, sentinel and typed errors, and a `_test.go` suite. The HTTP stack is the standard library `net/http`; the SDK adds `brotli` and `klauspost/compress` for response decoding and `google/uuid`, and the transport accepts a caller-supplied `HttpDoer` or `http.RoundTripper`. JSON is `encoding/json` with HTML escaping off. Generated tests run with `go test` through `gotestsum` (JUnit XML, `-race`), with `testcontainers-go` for the Squid and Chasm fixtures. The floor is `go 1.25` in `go.mod` (the README states it), the Docker toolchain is `golang:1.26-alpine`, formatting is `gofmt -s` plus `gofumpt`, and `go vet` and `staticcheck` gate the code. The module also builds for `GOOS=wasip1 GOARCH=wasm` (`GoWasmSpec`).

## Read first

- Shared cross-language contract and decisions: `../AGENTS.md` (`src/main/java/io/github/mridang/codegen/generators/AGENTS.md`)
- Repository root: `/AGENTS.md` (workflow, commands, guardrails)
- Codegen class: `BetterGoCodegen.java` (this folder)
- Templates: `src/main/resources/templates/go/`
- Specs: `src/spec/java/io/github/mridang/codegen/spec/go/` (`GoSpec`, `GoBuildSpec`, `GoClientSpec`, `GoFormattingSpec`, `GoLintingSpec`, `GoReservedWordsSpec`, `GoWasmSpec`)
- Golden: `src/spec/resources/generated/go/`

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
| `call_hook.mustache`, `otelhook.mustache` | the call hook types (`pkg/call_hook.go`) and the OpenTelemetry adapter (`pkg/otelhook`); `base_api.mustache` calls the hook, `configuration.mustache` and `client.mustache` carry it | hook and telemetry behaviour |
| `readme.mustache`, `skills.mustache`, `go_mod.mustache`, `makefile.mustache`, `golangci.mustache`, `dev_dependencies.mustache` | README, SKILLS.md, module manifest, tool block and lint config | README sections, dependencies, lint |
| `templates/go/test/` and `test/api/` | generated `_test.go` files | generated tests |

## Idioms (deliberate differences from the other eleven)

| Topic | What go does | Why |
|---|---|---|
| Errors | caller mistakes are exported sentinels matched with `errors.Is` (`ErrMissingRequiredParameter`, `ErrInvalidPathParameter`, `ErrInvalidProxyURL`, `ErrInvalidTimeout`, `ErrInvalidCACertificate`, `ErrTransportConflict`, `ErrInvalidServerVariable`, `ErrClientClosed`, ...); SDK errors implement `Error`, `Unwrap` and `As` | no exceptions; the error protocol is the unwrap chain |
| Typed errors | one struct per declared error schema in `pkg/errors` (`<Model>Error`) embedding the status struct it subclasses; `Unwrap` returns that embedded struct so `errors.As` matches at every level; `Body()` returns the decoded model; operations pass a `map[string]errorFactory` to `FromResponseWith`; `IsRetryable()` and `RequestID()` are on `ApiError` | no generics on the base error and no covariant returns; embedding with `Unwrap` is the Go form of subclassing |
| Messages | error strings start lower case (`missing the required parameter`, `too many redirects`) | staticcheck ST1005 forbids a capitalised error string |
| Cancellation | `ctx context.Context` is the first parameter of every operation (S7) | Go convention, the one deliberate shape difference |
| Builders | package-level `NewConfigurationBuilder()` and `NewTransportOptionsBuilder()`, setters named `val` | no static methods |
| Overloads and defaults | `*WithHTTPInfo` result variants, `GetAccessTokenWithRefresh`, `WithClientAuthMethod`, a trailing variadic `RequestOption` (`WithTimeout`, `WithHeaders`, `WithMaxRetries`, `WithRequestContentType`) on every operation | no default arguments or overloads |
| Optional parameters | an `opts.<Op>Options` struct per operation; required file or reader fields are pointers or interfaces checked for nil at call time | struct fields cannot be required |
| Inheritance | `BaseAuthenticator` is an embedded empty struct; no implementation inheritance for models | embedding, not subclassing |
| Unions | oneOf is a struct holding an `any` value with `MarshalJSON` and `UnmarshalJSON` that tries each variant in declaration order or dispatches on the discriminator | no sum types |
| Support types | generic `Set[T]`, `DateTime`, `Duration` (ISO-8601 and protobuf forms), `iso8601` helpers, a `TraceContextPropagatorFunc` hook | stdlib gaps; no optional import to detect OpenTelemetry |
| Zero values | a model struct can be built without its required properties; decode enforces them | the zero-value struct is forced |
| Immutability | `ApiResult.headers` is a `maps.Clone` and documented "do not mutate" | Go has no immutable map |
| Numbers | `format: number` is `float64`, documented lossy | no stdlib decimal |
| Optional description | `ServerConfiguration`/`ServerVariable` description is an empty string when absent | empty-string sentinel shared with rust, swift and dart; metadata only |
| Naming | initialisms in upper case (`URL`, `ID`, `HTTP`) | Effective Go |

## Settled decisions (do not re-open in review)

Each cross-language decision is stated once in the shared file; here only how go realises it. Where the Rule line says "intended" the rule is the canonical behaviour for go and the templates were not confirmed to implement it when this file was written; check the template before relying on it. Decisions on which go needs no go-specific rule are listed at the end of this section.

### Strict containers (S1, 2026-11-16)
Rule (intended): a container-of-primitives response (`map[string]int32`, `[][]int32`) is checked element by element like a single primitive, so a fractional, quoted or out-of-range leaf is a `*SerializationError`. A single primitive already behaves this way (recorded as conformant in the ledger).
Why: the container path bypassed the primitive check in seven languages; all twelve now behave alike.
Where: `object_serializer.mustache`, `base_api.mustache`.
Test: `test/object_serializer_test.mustache` and the `getInventory`/`getMatrix` rows in `test/api/store_api_test.mustache`.

### Content parameters use the SDK serializer (S2, D50, D80)
Rule: a `content: application/json` parameter is encoded by the package `serialize()` (an `Encoder` with HTML escaping off) and its `*SerializationError` is returned from the operation before any request. The query, options and required arms are landed; header and path arms are intended. A `content:` cookie is serialised by `serialize()` and then percent-encoded by `encodeContentCookieValue`, keeping only A-Z a-z 0-9 - . _ ~ literal (D52), and still passes `isValidCookieValue`.
Why: `json.Marshal` escaped `<`, `>`, `&` and its error was discarded, so the bytes differed from the other eleven.
Where: `api/api.mustache`, `object_serializer.mustache`.
Test: `searchStore.q` rows in `test/api/store_api_test.mustache`.

### Schema constraints are not enforced (S3, D55)
Rule: the client does not enforce `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems`, `maxItems`; go has no native enforcement, so the server owns validation. The README must carry a "Schema constraints" section (intended; confirm the heading in `readme.mustache`).
Why: only python gets it natively and cheaply through pydantic; hand-written enforcement would differ per language.
Where: `readme.mustache`.
Test: none: asserting absence is not worth a test; the README text is the contract (`test/readme_test.mustache` pins its sections).

### allOf with a discriminator (S4)
Rule (revised in #104): a classic allOf base is realised as a union of its subtypes: `BetterGoCodegen.postProcessAllModels` turns the marked base (`x-allof-base`) into a `oneOf` of its mapped subtypes, so `Vehicle` is a wrapper with `ActualInstance`, `AsCar`, `IsCar` and a discriminator-dispatching `UnmarshalJSON`, and each subtype stays a flat struct with its discriminator defaulted by `NewCar`. The common fields are not exposed on the wrapper (a flat base struct could not hold the subtype, and embedding would promote the embedded `UnmarshalJSON`). A model reused in two unions is never re-parented. The oneOf path: `models/model.mustache` generates an `UnmarshalJSON` that reads the discriminator, dispatches on `mappedModels`, and returns `unknown discriminator value %q for <Class>` for an unmapped value (the other eleven also reject it).
Why: structs cannot subclass; embedding keeps the shared fields once and the interface gives callers the common surface. Classic allOf with a discriminator is #104.
Where: `models/model.mustache`, `BetterGoCodegen.java`.
Test: `test/composed_schema_test.mustache` (oneOf cases only).

### Required and nullable (S5, D66)
Rule (intended): a required nullable property is a pointer with a JSON tag without `omitempty`; decode requires the key (absent is an error), an explicit `null` is kept as `nil`, and encode always writes the key (`null` when `nil`). A required non-nullable property already fails on a missing key or `null` in the generated `UnmarshalJSON`, and a required property with a default (a synthetic discriminator default) is filled when absent (intended).
Why: the canonical behaviour is shared by all twelve.
Where: `models/model.mustache`, `models/json_marshal.mustache`.
Test: `test/model_test.mustache`, `test/composed_schema_test.mustache` nullable `label` case.

Query side (D98): a required nullable query parameter is a pointer field on the Options struct (`*T`); the api template tests `options.X == nil` before calling `serializeStyled` (a typed nil pointer in an `any` is a non-nil interface) and writes `""` for nil.

### uniqueItems order (S6, D69)
Rule: sets are sent in the language's natural order; for go `Set[T].MarshalJSON` sorts a copy of scalar members (string, int, uint, float, bool kinds, enums included) and leaves other members in insertion order. Do not sort elsewhere.
Why: a `map`-backed set iterates in random order, so unsorted bytes would differ per run. `T` stays `comparable`, so there is no public type change.
Where: `models/set.mustache`.
Test: the `Set` tests in `test/model_test.mustache`.

### Cancellation (S7)
Rule: every operation takes `ctx context.Context` as its FIRST parameter, before the path parameters, body, options and server. Cancelling that context cancels the call and returns `request cancelled: context.Canceled`, never `*NetworkTimeoutError`. The client owns no base context any more: `Close()` is graceful and cancels nothing. Landed in batch 4.
Why: `ctx` first is the Go convention and what every go caller already passes; a wrapper handle would be less natural. F228 was open for go before batch 4.
Where: `api/api.mustache`, `default_api_client.mustache`, `client.mustache`.
Test: `test/default_api_client_test.mustache`, `test/default_api_client_unit_test.mustache`, `test/api/pet_api_test.mustache`.

### Parameter order (S8, owner confirmation pending)
Rule (intended): required parameters, then `server`, then the options struct, then the trailing `requestContentType ...string` on multi-consumes operations; `ctx` stays first. Adding security never moves an argument.
Why: callers must not break when a spec gains security on an operation.
Where: `api/api.mustache` (the `signatureArgs` loop), `api/options.mustache`.
Test: server-override rows in `test/api/pet_api_test.mustache`.

### Injected transport (S9)
Rule: `TransportOptionsBuilder.HttpClient(HttpDoer)` accepts any value with `Do(*http.Request) (*http.Response, error)` (an `*http.Client` is wrapped), and `TransportOptionsBuilder.RoundTripper(http.RoundTripper)` wraps the caller's round tripper with the SDK defaults. Supplying both, or combining one with the SDK's own proxy, TLS or pool settings, fails `Build()` with `ErrTransportConflict`; the SDK never closes a caller-supplied client. Landed.
Why: OpenAI, Stainless, Speakeasy, Fern, Azure and Stripe SDKs all accept the standard client. Go is done.
Where: `transport_options.mustache`, `default_api_client.mustache` (`newPolicyClient`, `locationGuardTransport`), `client.mustache`, `readme.mustache`.
Test: `test/transport_options_test.mustache`, `test/default_api_client_test.mustache`.

### The call hook and the OpenTelemetry adapter (#158, #108)
Rule: `Start` returns `(CallObserver, error)` because go returns errors instead of throwing them; `baseApi.invokeApiForResult` calls it right after `InjectTraceContext` on the first attempt only and keeps the hook's edits as a delta (`callState.begin`) that is put on the rebuilt headers of every later attempt, except the names the fresh authentication of that attempt set (the replay after a 401 keeps its new token). The generated `<Op>WithHTTPInfo` method holds the `callState`, names its error result and defers `callState.end`, so `Finish` runs exactly once on every exit after `Start`, the decoding included; a panic in `Finish` is recovered. The hook lives on `Configuration` (`CallHook()`, builder `CallHook`) and clients take the trailing `WithCallHook` `ClientOption`. The adapter is the package `pkg/otelhook` of the same module (no nested module): the core package imports none of OpenTelemetry for it; `hasOpenTelemetryAdapter()` is true.
Why: one seam for editing, observing and tracing; a nested module would need a version tag and a `replace` for the golden.
Where: `base_api.mustache`, `api/api.mustache`, `call_hook.mustache`, `configuration.mustache`, `client.mustache`, `otelhook.mustache`, `go_mod.mustache`.
Test: `test/base_api_test.mustache` (hooks), `test/client_test.mustache`, `test/configuration_test.mustache`, `test/call_hook_test.mustache`, `test/otelhook_test.mustache`.

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

### Timeouts, redirects, validation timing (S16, D76, D77, D79, D72, D73)
Rule: one end-to-end timeout budget; a caller-supplied stream or file handle is never closed; proxy URL validation happens in `Build()`, not in the builder setter (the setter stores `proxyErr`); `caCertPath` is validated even when `verifySsl` is false. `Build()` rejects a zero or negative timeout with `ErrInvalidTimeout` and the canonical text `invalid timeout: must be a positive number of milliseconds (got N)` (D77, batch 6); `nil` is the only way to ask for no timeout.
Why: the budget used to apply per hop; redirects could multiply it.
Where: `default_api_client.mustache`, `transport_options.mustache`.
Test: `test/transport_options_test.mustache`, `test/default_api_client_test.mustache`.

### OIDC rediscovery keeps the session (D01)
Rule (intended): the token manager owns the exchanged flag (an `atomic.Bool`), and `resolveDelegate` builds the replacement authenticator around the previous delegate's `*OAuth2TokenManager`; the mutex is already held across the discovery fetch.
Why: replacing the delegate reset the session, so every call after the discovery max-age failed with "Must call ExchangeCode()".
Where: `auth/oauth/openid_connect_authenticator.mustache`, `oauth2_auth_code_authenticator.mustache`, `oauth2_token_manager.mustache`.
Test: `test/openid_connect_authenticator_test.mustache`.

### Refresh grant and exchange (D02, D03)
Rule: `GetAccessTokenWithRefresh(tokenURL, refreshURL, params, headers)` posts a held refresh token to `refreshURL` with `client_id`/`client_secret` copied from the params and the same extra headers, and falls back to the original grant at `tokenURL`; `Exchange(tokenURL, params)` runs under `m.mu`, saves and clears the session, posts verbatim, and restores the old state on failure; `ExchangeCode` calls only it. The refresh method is landed (batch 6); the exchange is landed with the flag still on the authenticator.
Why: RFC 6749 sec 6 and 3.2.1; go has no default arguments, hence the separate method.
Where: `auth/oauth/oauth2_token_manager.mustache`, `oauth2_password_authenticator.mustache`, `oauth2_auth_code_authenticator.mustache`.
Test: `test/oauth2_token_manager_test.mustache`, `test/oauth2_auth_code_authenticator_test.mustache`.

### OAuth wording, grammar and request order (D05, D07, D08, D09, D10)
Rule: the token body and the authorize URL are emitted in the fixed order grant_type, code, refresh_token, username, password, client_id, client_secret, redirect_uri, scope, then the rest sorted (`encodeTokenForm`, landed in batch 6), escaped with `url.QueryEscape` and never `url.Values.Encode`. Intended: OIDC always requests `openid`; token and discovery errors use the shared wording ("token response is not valid JSON", "is not a JSON object", "missing or empty access_token field") with no parser detail; `expires_in` and `max-age` use one digits-plus-fraction grammar with a one-year ceiling applied before the `int64` conversion; precondition texts are "authorization code must not be empty" and "must call ExchangeCode() before making API requests" (today the not-exchanged sentinel carries an `oauth2 authorization code:` prefix).
Why: map order and per-language wording made bytes and messages differ.
Where: `auth/oauth/*.mustache`, `errors/oauth2_token_error.mustache`.
Test: the `test/oauth2_*_test.mustache` and `test/openid_connect_authenticator_test.mustache` files.

### Implicit flow, empty state, sync auth accessor (D06, D11, D12)
Rule: an empty implicit access token counts as unset (already conformant) and an empty `state` is absent in authorize URLs (already conformant). The infallible `AuthHeaders()` stays on the `Authenticator` interface and returns an empty map when the token cannot be obtained; `AuthHeadersOrError()` is the loud path and is the one the transport calls. Removing the `log.Printf` from the password and auth-code variants so all four are silent is intended.
Why: the interface method cannot return an error. F290 is an open API-shape question, not a settled rule: failing loudly would change the interface signature in every authenticator and in every caller of the interface, and no decision has been taken.
Where: `authenticator.mustache`, `auth/oauth/oauth2_implicit_authenticator.mustache`, `base_api.mustache`.
Test: `test/oauth2_implicit_authenticator_test.mustache`.

### Query, form and multipart order
Rule: query parameters, `application/x-www-form-urlencoded` pairs and multipart parts are emitted in the spec's declaration order, never in map iteration order: `orderedKeys(m, order)` in `base_api.mustache` sorts the keys by the order the operation passes, array members expand in place, and the form body is joined in order with the same `QueryEscape` rules. Landed.
Why: Go maps iterate in random order, so the wire bytes differed per run. Go is done.
Where: `base_api.mustache`, `api/api.mustache`.
Test: `test/base_api_test.mustache`, `searchStore` and `setPetPreferences` rows in the pet and store API tests.

### Unusable responses carry the real headers (D13)
Rule: a response that arrived and could not be used (decompress failure, redirect refusals) is an `ApiError` carrying the real status and the real lower-cased headers; go already passes the response headers on both paths.
Why: a response that arrived is not a "no response" error.
Where: `default_api_client.mustache`.
Test: `test/default_api_client_test.mustache`.

### Error text matrix (D14, D15, D16, D17, D20, D28, D29)
Rule: serializer messages are "failed to serialize object to JSON: ..." and "failed to deserialize JSON: ..." (lower case forced); redirect refusals read "refusing to follow redirect to non-HTTP(S) URL: <next>", "refusing to replay request body across HTTPS->HTTP redirect: <cur> -> <next>" and "too many redirects (exceeded N)" with the `*url.Error` unwrapped by `redirectRefusalMessage` and no `redirect refused:` prefix (landed, batch 6); the "Response headers:" line is compact JSON with sorted keys written by a `json.Encoder` with `SetEscapeHTML(false)` (`compactHeadersJSON`, landed, batch 6); list values render `[a, b, c]` through `strings.Join` (intended); the missing-parameter message names the caller-facing identifier; use-after-close wraps `ErrClientClosed`, whose text is "API client has been closed and can no longer send requests" (the forced "API client" wording).
Why: one message grammar across all twelve; the lower-case first letter is the only forced difference (ST1005).
Where: `api_error.mustache`, `errors/*.mustache`, `object_serializer.mustache`, `default_api_client.mustache`, `server_configuration.mustache`, `api/api.mustache`.
Test: `test/api_error_test.mustache`, `test/default_api_client_test.mustache`, `test/server_configuration_test.mustache`.

### Error bodies are depth-capped and HTML-unescaped
Rule: `pkg/errors` carries its own `errorBodyJSONDepth` and `unmarshalErrorBody`, duplicated because the errors package cannot import the root package; `FromResponse` and `GetTypedErrorBody` never call a raw `json.Unmarshal`. The serializer uses `json.Encoder` with `SetEscapeHTML(false)`, so `<`, `>` and `&` go on the wire literally. A leading BOM is stripped in both the error path and the success path (conformant).
Why: a non-2xx body must not be a stack-overflow vector, and the bytes must match the other eleven.
Where: `errors/*.mustache`, `object_serializer.mustache`.
Test: `test/api_error_test.mustache`, `test/object_serializer_test.mustache`.

### Header injection checks are case-insensitive (D21)
Rule: `hasHeader` compares names with `strings.EqualFold`; if the caller supplied any case variant of `User-Agent` or `X-Request-ID`, even empty, nothing is injected and the caller's key and value stay as written. Landed (batch 5).
Why: RFC 9110 sec 5.1.
Where: `default_api_client.mustache`.
Test: `test/default_api_client_test.mustache`.

### Response body decoding (D30, D31, D32, D33)
Rule (intended, partly): a binary return is raw bytes and never JSON-parsed (an `isBinary` branch before the JSON test in `api/api.mustache`); a `format: byte` return is JSON by default with one strict base64 decoder and every failure a `*SerializationError` (already conformant); a text-class content type on a `*string` return gives the text (branch exists); any other non-JSON content type on a non-binary return is a `*SerializationError` naming the content type (intended;  is open); a binary request body is sent unchanged under any selected content type through `invokeApiParams.rawBody` (landed, batch 6).
Why: five different outcomes across the twelve SDKs.
Where: `base_api.mustache`, `api/api.mustache`.
Test: `test/base_api_test.mustache`.

### Form values and multipart scalars (D39, D53)
Rule: a map, struct or array of composites in a form-urlencoded body is a `*SerializationError` ("form-urlencoded field <name> must be a scalar or array of scalars"), values with a `String` method (time, `DateTime`, uuid) count as scalars; in multipart a scalar (including date-time through `isTemporalScalar`) is a plain text part written with `stringify(v)` and only maps and model structs become `application/json` parts. Landed (batches 5 and 6).
Why: `%v` emitted go-specific repr text.
Where: `base_api.mustache`.
Test: `test/base_api_test.mustache`, pet API tests for `setPetPreferences` and `uploadPetDocument`.

### Parameter encoding (D49, D50)
Rule: `format: byte` parameters (and each element of an array of them) are standard base64 with padding (`stringify` and `toStringSlice` treat `[]byte` as a scalar); `allowReserved` keeps only the RFC 3986 query-legal reserved set literal and always encodes `#`, `[` and `]` (`encodeQueryAllowingReserved`); query and form encoding keep exactly `A-Za-z0-9-._~` literal (already conformant, lock test intended).
Why: the same bytes in all twelve.
Where: `value_serializer.mustache`, `base_api.mustache`.
Test: `test/value_serializer_test.mustache`, `test/base_api_test.mustache`.

### Path parameter dot segments (D48)
Rule: a path parameter of exactly `.` or `..` returns `ErrInvalidPathParameter` ("path parameter '<name>' must not be '.' or '..'") from `requirePathParam` before any request; `a..b` and `.hidden` are accepted. Landed (batch 5).
Why: a dot segment would be normalised away from the intended resource.
Where: `value_serializer.mustache`, `path_utils.mustache`.
Test: `test/value_serializer_test.mustache`.

### Models: field remarks, additionalProperties, uri, byte (D56, D58)
Rule: `readOnly` and `writeOnly` properties carry only a doc remark and have no wire effect; `MarshalJSON` passes `AdditionalProperties` through `withoutDeclaredKeys` so a declared property wins and the key is written once; each `isUri` field is checked by `checkAbsoluteURIField` and stays a `string`; `[]byte` fields (`hasByteFields`) are checked by `checkBase64Fields` against a strict RFC 4648 pattern before decoding. Landed (batches 5 and 6).
Why: one wire rule per feature in all twelve.
Where: `models/model.mustache`, `models/json_marshal.mustache`, `BetterGoCodegen.java`.
Test: `test/model_test.mustache`.

### Per-operation server types (D42)
Rule: a server variable gets one type per operation; a name occurring in two or more variants of the operation becomes `<VariantName><Var>`, a variable used once keeps its plain name (`enrichOperationServers`, computed once), so petstore gets `RegionalRegion`, `MirrorRegion` and `Channel`.
Why: duplicate type names failed to compile.
Where: `BetterGoCodegen.java`, `api/api.mustache`.
Test: `getMultiServerPetInfo` rows in `test/api/pet_api_test.mustache`.

### Identifiers, enums and literals (D40, D43, D44, D45, D46)
Rule: a digit-leading or empty sanitised name gets a deterministic letter `n` before casing and never goes through the reserved-word escape; go escapes nothing for variables because PascalCase fields never collide with keywords (conformant); a model named `Error` becomes `ModelError`; enum string values are escaped in order (backslash, CR/LF/TAB, then the enum escape table) and the quote is escaped, never stripped, and the value sits inside a `/* */` comment that must stay comment-safe; every spec string written into a code literal goes through one value-preserving escaper (`formatGoScopes`, `toDefaultValue`; routing control characters through the shared escaper is intended).
Why: stripping or double-escaping changed values; the escape prefix made digit names illegal.
Where: `BetterGoCodegen.toModelName`, `toVarName`, `formatGoScopes`, `models/model.mustache`, `servers.mustache`.
Test: `GoReservedWordsSpec`, the enum golden and `EdgeCases` rows.

### Multi-consumes content type selector rejects unknown values
Rule: operations with more than one request content type end in `requestContentType ...string`; an unrecognised value returns an error (`ErrInvalidRequest`-style, message "unsupported request content-type '<ct>' for <op>; declared types are [a, b, c]") and no value selects the first declared type.
Why: a typo must not ride the wire as an undeclared Content-Type.
Where: `api/api.mustache`.
Test: `test/api/pet_api_test.mustache` (`uploadPetDocument`, `setPetAvatar`).

### Required-parameter messages
Rule: the sentinel is `missing the required parameter` (lower case, with "the") and the operation layer wraps it with the parameter and `<Class>.<operation>`; an empty required path parameter comes from `requirePathParam` in `value_serializer` and is wrapped the same way (the clause shape `: path parameter '<x>' must not be empty` is the one accepted difference); a nil options struct is the distinct go-only message `options are required when calling <Class>.<op>` (forced).
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
Rule: `NewApiResult` stores `maps.Clone(headers)` (conformant); the field is documented "do not mutate"; read-only is impossible in go.
Why: a caller mutating the result must not alter the transport's map.
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
Why: confirmed already canonical.
Where: see the named templates in Layout.

## Known limitations and out of scope

Out of scope in every SDK, and the README says so (S11, closed backlog issues #53, #70, #78, #88, #90): callbacks and webhooks, XML bodies and `xml.nodeType`, Link Objects and mutual TLS. OpenAPI 3.2 and 3.1 are tracked by epics #49 and #96, and loading 3.2 documents is issue #50 (the pinned swagger-parser rejects it).

Open or deferred items that touch go: F290 (the infallible `AuthHeaders()` returns an empty map on failure, forced by the interface, see Settled); F316 (a go model struct can be built with zero values and without validation, forced); classic allOf with a discriminator is #104.

Not harmonised on purpose: the multipart file part sends a real filename and a sniffed MIME through the `*os.File` options types (the other nine send the field name and `application/octet-stream`); the OAuth2 token decode is typed, so a numeric `access_token` or `refresh_token` fails the exchange where ten SDKs ignore the bad field (borderline-forced); `ClientAuthMethod` has no `String()` and the OAuth2 `String` forms differ; go model `%v` prints pointer addresses for optional fields (no decision); a oneOf or anyOf wrapper with no match returns `data does not match any oneOf schemas for <X>` while the central helper says `No oneOf/anyOf variant matched the JSON`. `format: number` is `float64` and lossy by design. SOCKS proxies are not supported.

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

- Header merge: every layer (transport defaults, configuration defaults, header selector, operation headers, auth headers) is merged by `mergeHeaders` in `default_api_client.mustache`: a key that differs only in case replaces the earlier one, the later layer keeps its spelling, and the keys of one layer are visited in sorted order so the result never depends on map iteration. `removeHeader` and `hasHeader` are case-insensitive, so a caller's `accept-encoding` suppresses the injected one and `Content-Type` is dropped on a bodyless request whatever its spelling. Test: `TestDefaultApiClient_HeaderSpellingsAreMergedCaseInsensitively` and `TestMergeHeaders_...` in `test/default_api_client_unit_test.mustache`.
- Token expiry: one margin, applied when the token is stored: `expiry = now + expires_in - min(60 s, expires_in / 2)`, so a 60 s token is reused for 30 s and a 120 s token for 60 s. A grant response without `expires_in` (first fetch, refresh or exchange) leaves no expiry and the token is cached until invalidated; `expires_in <= 0` or unparseable still means stale on arrival. Tests that need a stale token use `"expires_in":0`. Test: `TestOAuth2TokenManager_ShortLivedTokensAreStillReused`, `..._RefreshWithoutExpiresInCachesTheNewToken`.
- OIDC rediscovery: when the discovery max-age lapses and the refetch fails, the previous delegate keeps serving (stale-if-error) and discovery is retried after 30 s; the first discovery still fails. `max-age=0` still refetches on every call (pinned by `TestOpenIdConnect_DiscoveryIsReusedForTheCacheControlMaxAge`; the floor the audit suggested was not taken). Test: `TestOpenIdConnect_FailedRediscoveryKeepsUsingThePreviousDelegate`.
- Query secrets in errors: `redactURL` replaces the query of a URL with `***` in transport-error messages (`request failed: ...`) and in the redirect refusal messages; the original `*url.Error` stays the cause. Test: `TestDefaultApiClient_NetworkErrorMessageRedactsTheQuery`.
- Trace propagator (race part): `traceContextPropagator` is an `atomic.Pointer`, so `SetTraceContextPropagator` may run while requests are in flight. Per-operation spans and metrics are not added. `InjectTraceContext(ctx, headers)` takes the call context and hands it to the propagator, so a span carried by the operation's `ctx` reaches `traceparent` and `tracestate` (F06, #131); the other eleven read the ambient context (D101). Tests: the span cases of `trace_context_util_test` pass the span through the call context, and `TestBhvTracing013`, `015` and `016` in `base_api_test` drive an operation under a real span. Test: `TestSetTraceContextPropagator_ConcurrentSetAndInjectIsRaceFree` (the suite runs with `-race`).
- F844 is already conforming: this file exists.

Deferred from batch 6 and not landed since: the `models.Date` type for `format: date` (#126), single-pass model decode (#107) and a response size limit option (#112). The rest of that list, a per-call content type, the context-aware auth method, named inline parameter enums, values instead of pointers, typed union constructors and accessors, the per-group interface, exported enum value lists and the operation doc comment layout, landed in the later batches described below.

## Decisions taken in batch 7

The audit's own proposal was the default wherever no owner decision existed.

- Decoded responses (D92): when the body was decompressed, the returned headers drop `content-encoding` and `content-length`; an empty body or `identity` leaves them. Tests: `assertDecompresses` in `test/default_api_client_test.mustache`, `TestDefaultApiClient_DecodesZlibWrappedDeflateResponse`.
- Proxy URL in errors (D79): a rejected proxy value is shown with its `user:password@` part replaced by `***@` (`redactProxyUserinfo`, on the raw text because the value may not parse), and a `*url.Error` is unwrapped so the raw URL is not repeated. Test: `TestTransportOptions_ProxyRejectionMessages`.
- Cross-origin redirects: besides the fixed allowlist and the spec's apiKey header names, every request header that `isSecretHeaderName` treats as a secret (S15: key, token, secret, Authorization, Cookie, Proxy-Authorization) is removed on a cross-origin hop. Test: `TestDefaultApiClient_StripsSecretNamedHeadersAcrossCrossOriginRedirect`.
- Per-call authenticators: `invokeApiForResult` injects the API's own client into a per-call override that implements `auth.HttpAwareAuthenticator`, so a per-call OAuth2 or OIDC authenticator no longer fails with `ErrApiClientNotInjected`. Test: `TestBaseApi_PerCallHttpAwareAuthenticatorIsInjected`.
- Auth requests carry no caller default headers: every request that goes through `authApiClientAdapter` (token endpoint, OIDC discovery) is marked with `authRequestContextKey` and `SendRequestWithOptions` then skips `TransportOptions.DefaultHeaders`; User-Agent, X-Request-ID and the SDK's own headers stay. No exported API changed. Test: `TestDefaultApiClient_AuthRequestsOmitTheCallersDefaultHeaders`.
- File header: the info description in `api_info.mustache` goes through `unescapeDocComment`. Test: `TestMetadata_FileHeaderCarriesNoBackslashEscapedQuotes`.
- Tests only: F1169 (cookie from auth now asserts the header), F1063 (Set-Cookie is not replayed), F1067 (duplicate key keeps the last value), F1111 (StrictTag), F1144 (hostile Availability members), F1145, F1146, F1114, F1115, F1116, F1124 (slug), F1127 (required header and cookie guards), F1128 (createAccount), F1130 (trackingId), F1163 (offset-less date-time), F1075 (token request headers), F1159 (openid scope), F1151 (caller-owned handles stay open).
- Already conforming in go: F054 (the query, header, path and cookie content arms all use `serialize()`), F833, F854, F1044, F1045, F861, F922, F924, F925, F927, F931, F1142, F1079 (`Build()` rejects zero).
- Object-valued parameters (D47): `serializeStyled` and `serializeDeepObject` encode a model or string-keyed map property by property (`objectEntries`): exploded form query is `queryPairs` (one `name=value` per property in declaration order), comma form, simple header, label, matrix and the delimited styles are flat strings, deepObject is `name[prop]`. A null property is left out and a nested object or array keeps its JSON text (the D47 `SerializationError` for a nested leaf is not raised: these helpers have no error channel). Tests: `TestSerializeStyled_ObjectValuedParameterFollowsItsStyle`, `TestPetApi_GetPetTagSendsObjectValuedAndStyledParameters`.
- apiKey names: the redirect allowlist and its tests render a spec apiKey header name through `goStringLiteral`, so a quote or backslash cannot break the file. Test: `BetterGoCodegenTest.escapesQuoteBackslashAndControlCharactersInAnApiKeyName`.
- More tests only: F1133 (redirect chain budget, chasm), F1161 (multi-value response headers and the typed status routes), F1069 (lowercase uuid path), F1158 (deepObject filter and matrix/label path), F1175 (prefixItems).
- Already conforming or settled in go: F824 (`Client.Close`, batch 5), F826 (`io.Reader`, batch 5), F229 (done), F803 (S8), F056 (S3), F090 (ambient proxy ignored by design), F149 (`TestBinaryResponse_TextPlainBodyForModelReturnIsASerializationError`), F1150 (go sets no `Proxy-Authorization` header itself; the transport proxy authenticates every hop).
- Not done in go: 401 replay (F855, since landed for the OAuth authenticators in batch 9), the response size limit option (#112), a `String`/`GoString` on every model with a password field (F867: it needs a template flag for the field type, pointer or not, that cannot be verified without the gate), and more charsets (they need `golang.org/x/text`).

## Decisions taken in batch 9

- A 401 replays once. `auth.AccessTokenInvalidator` (public interface in `http_aware_authenticator.mustache`) has `InvalidateAccessToken()`, implemented by the OAuth2 client credentials, password, authorization code and OpenID Connect authenticators (the token manager already had it; it drops the access token and keeps the refresh token, so the next token call is a refresh grant first). `invokeApiForResult` calls it on a 401 and replays the request once with headers built again; a second 401 is the usual `*UnauthorizedError`, and a failed re-authentication leaves the original 401. API key, basic and bearer authenticators do not implement the interface and never replay. Go buffers every body (multipart and `io.Reader` bodies are read into a `[]byte` before the first send), so, like C#, no body is non-replayable and a multipart request is replayed too. Test: `TestBaseApi_A401OnOAuthRequestRenewsTheTokenAndReplaysOnce`, `TestBaseApi_ASecond401IsReportedAfterOneReplay`, `TestBaseApi_AFailedReauthenticationLeavesTheOriginal401`, `TestBaseApi_ABearerRequestIsNeverReplayedOnA401` and one `InvalidateAccessToken` test per authenticator.
- PKCE S256. `BuildAuthorizationURL` makes a 43-character verifier (32 `crypto/rand` bytes, base64url), keeps it in the authenticator and appends `code_challenge` (SHA-256, base64url) and `code_challenge_method=S256` after `state`; `ExchangeCode` sends `code_verifier` (after `redirect_uri` in the form order) once and forgets it, success or not. The OIDC delegate rebuilt by rediscovery inherits the verifier; there is no nonce. Standard library only. Test: `TestOAuth2AuthCode_AuthorizeUrlCarriesAPkceChallengeThatTheExchangeProves`, `TestOAuth2AuthCode_EachAuthorizeUrlStartsANewChallengeAndTheVerifierIsSingleUse`, `TestOpenIdConnect_PkceVerifierSurvivesRediscovery`.
- Retries. `TransportOptionsBuilder.MaxRetries(n)` (default `DefaultMaxRetries` = 2, `0` disables, a negative value is `ErrInvalidMaxRetries` from `Build()`) and `TransportOptions.MaxRetries()`. The loop is in `baseApi.invokeApiForResult`, not in the transport, because every attempt must rebuild the headers and ask the authenticator again; `DefaultApiClient.retryPolicy()` hands it the limit and the timeout, so a custom `ApiClient` is never retried (its transport is the caller's). It retries a `*NetworkError` or `*NetworkTimeoutError` (not a cancellation or `ErrClientClosed`) and the statuses 408, 409, 429 and 500-599 except 501. The wait is 0.5 s doubling to 8 s minus up to 25% jitter, or the `Retry-After` header (seconds or HTTP date) capped at 60 s. With a timeout the whole loop runs under one `context.WithTimeout`, and a wait that would outlast the deadline ends the retries with the last failure; cancelling the context during the wait returns the cancellation error. A replayed 401 does not count as a retry. The README and SKILLS describe it. The 60 s cap of `Retry-After` is tested on `retryAfterDelay` in `default_api_client_unit_test.mustache` (the internal-package test file). Test: `TestBaseApi_RetriesTwiceByDefaultWithBackoff`, `TestBaseApi_StopsAfterMaxRetriesAndReturnsTheLastFailure`, `TestBaseApi_MaxRetriesZeroSendsOneRequest`, `TestBaseApi_RetriesOnlyTheTransientStatuses`, `TestBaseApi_RetryAfterSecondsReplacesTheBackoff`, `TestBaseApi_RetryAfterHttpDateReplacesTheBackoff`, `TestBaseApi_RetriesNeverExtendTheEndToEndTimeout`, `TestBaseApi_ARetryAfterBeyondTheTimeoutEndsTheRetries`, `TestBaseApi_CancellingDuringTheBackoffStopsTheRetries`, `TestBaseApi_ARetryAppliesTheAuthenticationHeadersAgain`, `TestBaseApi_RetriesANetworkErrorThatHappenedBeforeAResponse`, `TestTransportOptions_MaxRetriesDefaultsToTwoAndZeroDisablesThem`. The mock-server tests that expect one failing request build their client with `MaxRetries(0)`.
- Error text cap. `ApiError.Error()` shows at most 1024 body bytes (cut on a character boundary) then `... (N more bytes)`, every control character (below 0x20 and 0x7f) as `\xNN`, and `***` for the value of authorization, proxy-authorization, cookie, set-cookie, www-authenticate, proxy-authenticate and any header name containing key, token or secret; `ResponseBody()` and `ResponseHeaders()` keep the real values. Test: `TestApiError_ErrorCapsTheBodyEscapesControlsAndMasksSecretHeaders`.
- F825 is rust only and skipped in go, where a caller already matches SDK errors with `errors.As` against the exported types.

## Decisions taken in batch 10

- Token response errors: the three 2xx token failures are the fixed texts `Token response is not valid JSON` (malformed body, no parser detail), `Token response is not a JSON object` and `Token response missing or empty access_token field`. Test: `TestOAuth2TokenManager_UnusableTokenResponsesUseTheFixedMessages`.
- `token_type`: a missing, null or case-insensitive `bearer` type is accepted; anything else is an `*OAuth2TokenError` `Unsupported token_type in token response` without the value. Test: `TestOAuth2TokenManager_ATokenTypeThatIsNotBearerIsRefusedWithoutEchoingIt`, `..._ABearerOrMissingTokenTypeIsAccepted`.
- OIDC discovery: an `issuer` that differs from the discovery URL minus `/.well-known/openid-configuration` (trailing slash ignored; no issuer is accepted) and an authorization or token endpoint that is not https (http on `localhost`, `*.localhost`, `127.0.0.1`, `::1` is allowed) are a `*SerializationError`. Test: `TestOpenIdConnect_DiscoveryIssuerMustMatchTheDiscoveryUrl`, `TestOpenIdConnect_DiscoveryRefusesClearTextEndpointsExceptLoopback`.
- CA messages: `failed to read CA certificate from "<path>": <cause>` and `failed to parse CA certificate from "<path>": no PEM blocks found`, carried by an unexported `caCertificateError` that still matches `ErrInvalidCACertificate` (and the cause) through `errors.Is`. Test: `TestTransportOptions_GarbagePemCaCertFileIsRejected`, `..._EmptyCaCertFileAndDirectoryGetTheirOwnMessages`.
- List rendering: the server-variable and content-type messages render lists as `[a, b, c]` through `bracketedList`. Tests: `TestServerConfiguration_RejectionMessageNamesValueVariableAndAllowedValues` and the two content-type rows in `test/api/pet_api_test.mustache`.
- Server URL substitution: all placeholders are replaced in one pass (`strings.NewReplacer`), so a value that contains `{other}` is never expanded again and the result no longer depends on map iteration order. Test: `TestServerConfiguration_AValueIsNeverExpandedAgain`.
- Cookies: the `Cookie` header from an authenticator is written sorted by cookie name. Test: `TestBaseApi_SeveralAuthCookiesAreWrittenSortedByName`.
- `make lint` runs `format-check` (`gofmt -s -l` and `gofumpt -l`, non-zero on any file listed), `vet` and `staticcheck` before golangci-lint.
- SKILLS and README: the error section names the `{{errorPrefix}}Error` root and its four direct kinds, with a catch of the root after the `ApiError` arms; the README follows the java layout (Installation, Usage, Configuration with Raw responses, Timeouts, Retries, Expired tokens, Request id, Cancellation, Custom HTTP client; Caveats; Not supported with XML, Link Objects and mutual TLS); SKILLS Configuration has Custom Transport Options, Retries, Custom HTTP client, Cancellation and Raw responses. Tests: `TestReadme_HasTheSharedSectionsInTheSharedOrder`, `TestSkills_ConfigurationHasTheSharedSubsectionsInTheSharedOrder`, `TestSkills_ErrorHandlingNamesTheRootAndTheSiblingsOfApiError`.
- Tests only: F927 (the downgrade message is pinned exactly), F1065 (IPv6 proxy), F1073 and F1074 (bearer), F1112 (`BinaryVault.Equal`), F1129 (`TreatmentRecord`), F1124 (third server, `nickname`), F1135 (identity), F1136 (non-BMP filename), F1141 (`GetDefaults`), F1107 (snake_case and `...RedactsSecret` names renamed to `Test<Subject>_<Camel>`).
- Already conforming: F215 (`RequiredNullable` model and `TestDeserialize_RequiredNullableKeyMustBePresentAndNullIsKept`), F831 (`TooManyRequestsError`), F855 and F858 PKCE (batch 9), F1080 (batch 9), F1146 (`TestEdgeCases_InlinePhaseEnumRoundTripsItsWireValues`), F1180 (`auth.AccessTokenInvalidator`, batch 9), F290 (`AuthHeaders()` is infallible by the interface and `AuthHeadersOrError` is the one `base_api` calls; a panic would break the interface).
- Not done in go: a `String`/`GoString` on every model with a password field (F867: it needs a template flag for the field type, pointer or not, that cannot be verified without the gate), more charsets (they need `golang.org/x/text`), a released redirect body (net/http's client closes redirect bodies itself, and a closing double cannot be verified here), the response size limit option (#112) and the `models.Date` type (#126).

## Decisions taken in wave 2

Owner decisions of 2026-11-17 realised in go, with the triage of every item on the wave-2 work list.

The "Skipped" lists of batches 6, 7, 9 and 10 above are history: every id implemented below supersedes its entry there, and `TestModel_InlineEnumValueListIsNotExported` no longer exists (the list is exported, `TestModel_EnumsExposeTheirAllowedValues`).

### Implemented
- **F830 (request context on errors).** `ApiError` (so every subclass) has `RequestMethod()` and `RequestPath()` (the path without its query string or fragment), set by `AttachRequest(method, path)`, which keeps a value that is already set. `baseApi` calls it where it builds the error: a status error (`throwAPIError`), a refused redirect and a `*NetworkError` or `*NetworkTimeoutError` from the transport; the path is `params.path` after path substitution, or the URL path when a per-operation server made it absolute (`requestPathOf`). `Error()` gets a `Request: METHOD /path` line between the message and the status line, omitted when the request is unknown; the message text itself is unchanged (`API returned status code N`). The wording is the one python chose; csharp (`... for GET /path`) and dart (`GET /path returned status code N`) chose another, so the twelve still need one wording (shared decision). Test: `test/api_error_test.mustache`, `test/base_api_test.mustache` (`StatusErrorCarriesTheRequestMethodAndPath...`), `test/base_api_unit_test.mustache`.
- **F827 and F842 (per-call options).** Every operation, convenience and `WithHTTPInfo`, ends in `callOptions ...RequestOption` (`request_option.mustache`): `WithTimeout(time.Duration)`, `WithHeaders(map)`, `WithMaxRetries(int)` and `WithRequestContentType(string)`. This frees the variadic slot `requestContentType ...string` held and gives an operation with no options struct a slot too. An invalid option (a timeout under one millisecond, a negative limit) returns `ErrInvalidTimeout` or `ErrInvalidMaxRetries` before any request is built. The timeout bounds the whole call, retries included, as a context deadline, and `RequestOptions.Timeout` hands it to `DefaultApiClient`, which gives that request a copy of its policy client with the timeout (so it can lengthen the client's too). The headers replace a header of the same name from any layer, after the authentication headers. A custom `ApiClient` is never retried, so `WithMaxRetries` does nothing for it. Test: `test/request_option_test.mustache`, `test/base_api_test.mustache` (`PerCall...`).
- **F868 (immutable models with a copy).** Every model property has `With<Property>(value)` with a value receiver returning the changed copy; an optional or nullable property also has `Without<Property>()`, and a model with additional properties has `WithAdditionalProperties(map)` (copies the map). The copy is shallow (the README says so). Test: `test/composed_schema_test.mustache` (`TestModel_With...`).
- **F1062 (several 2xx responses).** For an operation with `vendorExtensions.op.hasMultiple2xx`, `api.mustache` emits `decodeSuccess(status, body, &data)`: it decodes the body as the schema of that status (a `2XX` range last) and wraps it with the typed constructor; an undeclared 2xx falls back to the union's own decode. Test: `test/api/store_api_test.mustache` (`ReserveStockDecodesTheBodyByItsStatus`, a body that fits both schemas).
- **F878 (typed unions).** A oneOf or anyOf wrapper has `New<Variant><Union>(v)`, `As<Variant>() (v, ok)` and `Is<Variant>()` per variant (`goVariantName` lambda, `variantName` in `BetterGoCodegen`: `[]byte` is `ByteSlice`, `map[string]int32` is `Int32Map`); `MarshalJSON` rejects a value that is not a declared variant, the zero union included. The untyped `New<Union>(any)` stays. Test: `test/composed_schema_test.mustache`, `BetterGoCodegenTest`.
- **F879 (interface per API group).** `<Group>ApiInterface` lists every operation and its `WithHTTPInfo` variant, with `var _ <Group>ApiInterface = (*<Group>Api)(nil)`. The assertion is the test: it fails to compile when the two drift.
- **F879 widened (mockable client, mocking001 to 009).** The `Client` group fields are typed as `<Group>ApiInterface`, not `*<Group>Api`, and `Client.Close()` returns nil when the client holds no transport, so `&pkg.Client{Pet: fake}` is a usable client of fakes. `newClient` builds each group, sets its scheme credentials on the concrete pointer, and only then stores it in the interface field. There is no client interface (Go interfaces carry no fields), so mocking007 is an exception and mocking008 is the Go test. Tests: `test/client_test.mustache` (mocking001, 002, 008), `test/api/pet_api_test.mustache` (003 to 006) and `test/readme_test.mustache` (009).
- **F877 (by value).** An operation returning a slice (byte slices too), a map or a string returns it by value (`vendorExtensions.returnByValue`, `returnsByValue`); an empty body is still the typed empty-body `ApiError`. `ApiResult[T].Data` stays `*T`: nil means no body, and the generic type has no by-value form. Test: `BetterGoCodegenTest`, and every generated test that reads such a result.
- **F894 (package metadata).** `repositoryUrl`, `homepageUrl`, `authors`, `documentationUrl` and `issueTrackerUrl` are registered in `BetterGoCodegen` and written to the README Module list when given, next to the version and `licenseId` (SPDX). `go.mod` has no metadata fields, so the manifest carries only the SPDX line it already had. The shared option plumbing is not changed (see the shared needs below).
- **F862 (credentials).** Every per-scheme authenticator constructor (`auth/scheme_authenticator.mustache`, rendered by `renderSchemeAuthenticator`) resolves each credential through `auth.ResolveCredential(passed, envName, what)`: a non-empty argument wins, then the variable the shared generator named (`envVars` in the scheme context, `{PREFIX}_{FIELD}`, qualified by scheme id when two schemes share a field), read once at construction; an empty variable is unset. A credential found in neither place returns an error wrapping `auth.ErrMissingCredential` (the root `ErrMissingCredential` is the same value) and naming the variable, so every scheme constructor, the OAuth ones included, returns `(T, error)`; a Bearer scheme's error also matches `auth.ErrInvalidBearerToken`, as the old empty-token check did. A Bearer scheme also has `New<Scheme>AuthenticatorWithProvider(host, provider)` (the provider is asked for every request and no variable is read). `New<Client>WithToken(host, accessToken, ...)` reads `{PREFIX}_BASE_URL` and `{PREFIX}_TOKEN` with `envVarPrefix` (the API title, shared by all languages; the old go-only module-leaf `envPrefix` is gone), and `New<Client>WithTokenProvider(host, provider, ...)` and `auth.NewBearerAuthenticatorWithProvider` take a `func(ctx) (string, error)` run for every request. Test: `test/scheme_authenticator_test.mustache` (one set of tests per scheme from `schemeAuthTests`: precedence, empty and unset variable, provider) and `test/client_test.mustache`.
- **F827 (every operation takes per-call options).** Satisfied without an Options struct: the `...RequestOption` tail is on every operation, so `mintsOptionsForEveryOperation()` stays `false` (an empty Options struct in front of it would add a parameter and no capability). Test: `store_api_test.mustache` (`AnOperationWithoutAnOptionsStructTakesPerCallHeaders`), `BetterGoCodegenTest`.
- **F857 (one credential per scheme).** `credentials.mustache` generates `Credentials` (one `Authenticator` field per scheme, named after it, from `credentialSchemes`) and `New<Client>WithCredentials(host, credentials, transport)`. Each operation passes its accepted scheme ids (`authMethods`, spec order) as `schemes` to `invokeApi`, and `baseApi` applies the first one the client has a credential for, else the single authenticator of `New<Client>`; `security: []` still passes `noAuth`. The per-call `Auth` field and the `pkg/options/authenticator.go` mirror interface are gone. Shared need: `computeSignatureArgs` still adds an `options` argument to an operation whose only reason was security, so such an operation takes an empty Options struct (pass nil). Test: `pet_api_test.mustache` (`AddPetUsesOnlyTheCredentialOfTheSchemeItRequires` and neighbours).
- **F840 (cookies).** Already as decided: a legal value is sent raw, an illegal one returns `ErrInvalidCookie` before sending, a percent-encoded value stays as the caller wrote it. Test: `pet_api_test.mustache` (`CookieParamIsSentRawOrRefusedBeforeSending`). An explicit `style: form` is not told apart from the default by the shared parameter model, so nothing encodes on the caller's behalf.
- **F1078 (graceful Close).** `DefaultApiClient.Close` sets the closed flag and releases only the transport the SDK created; it no longer cancels a base context, so a request in flight finishes and only its own context cancels it. Test: `default_api_client_test.mustache` (`CloseLetsTheRequestsInFlightFinish`, `CloseNeverClosesACallerSuppliedClient`).
- **F316 (no validation at construction).** `New<Model>` and the `With` methods only assign. Test: `model_test.mustache` (`ConstructionNeverValidatesAnEnumValue`, `DecodingAnUnknownEnumValueFailsLoudly`).
- **F1085 (test TLS keys).** Done as a shared change: the run generates the test CA and server certificate and mounts them at `testdata/certs`; none is committed.
- **F866 (sibling default on a `$ref`).** `fixEnumDefaultValue` turns `siblingDefaultEnumType` plus `siblingDefaultEnumVar` into the property's `defaultValue` (`AvailabilityOnHold`), so the existing default paths of `model.mustache` (the `New` function and the decoder) apply it with no template change. Only a referenced string enum; the schema's own default wins. Test: `test/model_test.mustache` (`ASiblingDefault...`), `BetterGoCodegenTest`.
- **F874 and F1053 (the call context reaches the token request).** `auth.ContextAuthenticator` (`AuthHeadersOrErrorContext(ctx)`) is implemented by the client credentials, password, authorization code, OpenID Connect and bearer authenticators; `baseApi` prefers it, `auth.RequestOptions.Context` carries the context to the transport through `authApiClientAdapter`, and the token manager and OIDC discovery send their requests with it. Cancelling the call aborts the token request and releases the manager's lock. A caller blocked on that lock behind another call's token request still waits for it. Test: `test/base_api_test.mustache` (`CancellingTheCallAbortsTheTokenRequest`, `AnAuthenticatorThatNeedsRequestsReceivesTheCallContext`).
- **F882 (operation doc comments).** The comment lists only the positional parameters (`ctx`, path, body), `options` and `callOptions`, leaves the query, header, form and cookie parameters to the Options struct, and ends with a `//` paragraph and `METHOD /path`.
- **F876 (inline parameter enums).** An inline enum parameter is a named type in the options package (`FindPetsByStatusStatus`, constants `...Available`, a `String` method), built in `generateOptionsFileContent`. Only a scalar string or integer enum; an array of enum values stays the element type.
- **F881 (enum values).** `All<Enum>Values()` is exported for inline enums too and every enum has `IsValid()`.
- **F308 (integer enum casing).** `NUMBER_1` becomes `Number1` (`PriorityNumber1`, `...NumberMinus1`).
- **F1055 (injected names).** A path or body parameter named like an argument or a local of the operation body (`ctx`, `options`, `server`, `callOptions`, `path`, `result`, the imported package names and the others in `getInjectedArgumentNames`) gets the suffix `Param`. The petstore fixture row stays open because it needs the shared spec.
- **F229 (connection reuse).** The owned transport keeps 10 idle connections per host (`MaxIdleConnsPerHost`), where the default of 2 applies otherwise.
- **F889, F834, F835, F836, F837, F828 (README and SKILLS notes).** README sections Credentials, Per-call options, Errors, Operations with several success responses, Models are immutable, Logging and tracing, Response compression, Versioning and supported Go, Behaviours to know; SKILLS Credentials, Per-call options, Response compression, logging and versioning.
- **F930, F1121, F1140 (tests).** `test/base_api_unit_test.mustache` refuses a nested object and an array of objects in a form-urlencoded body (internal, since no operation has such a field) and pins that an explicit Accept header parameter beats the client default and the negotiated one; `test/default_api_client_unit_test.mustache` pins that a followed, a refused and an exhausted redirect response is closed.

### Already satisfied or forced
- **F1087.** go keeps its floor: `go.mod` says `go 1.25`, which is not newer than the oldest non-end-of-life Go.
- **F1178, F1082.** java and node only.
- **F875.** Not done: `format: date` stays a validated plain string. A `models.Date` type changes the type of every date parameter, option and model field and is not among the owner decisions; it needs one (#126).

### Shared, not go
- **Shared, not go.** The petstore spec names a security scheme it never declares (#121), and the wording of the request-context message differs in python, csharp and dart. The go public API added above (With/Without copies, typed union accessors, `All<Enum>Values`, `RequestOption`, `<Group>ApiInterface`, the request context on `ApiError`, the context-aware authenticator, the `callOptions` parameter) is the shape the other languages are compared against.

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

## Gate lessons (what broke the gate and how to avoid it)

- Generated code is verified only by the Docker specs, so a template edit is unproven until `etc/ci-local.sh check Go` is green (GoFormattingSpec, GoLintingSpec, GoClientSpec). Run it before every push; the quick mode skips the integration specs and lets lint failures through.
- Never write `{{` in a Go composite literal or a Mustache comment: `map[string]struct{{}}` and `{{! ... {{#x}} ... }}` close at the first `}}` and leak the tail. Add a space (`struct{ }`) or use `{{{ }}}`. Catch: `grep -rn '{{!.*{{' src/main/resources/templates/go`, then regenerate Go.
- Generated Go uses `/* */` comments, never `//` (GoFormattingSpec). Catch: `grep -rn '^\s*//' src/main/resources/templates/go`.
- staticcheck QF1002 wants a tagged switch (`switch x { case ... }`) instead of `switch { case x == ... }` on one variable; ST1005 wants lowercase error strings with no capital or trailing period, so do not share a capitalized message with other languages. Catch: GoLintingSpec (golangci-lint with staticcheck). Do not enable `F050` (generated files) without a baseline.
- Unit and integration test templates share one package: never reuse a `TestX` name across them (`func TestX redeclared`). Catch: `grep -rhn '^func Test' src/main/resources/templates/go/test | sort -k2 | uniq -d -f1`, or `go vet ./...` on the golden.
- A `[16]byte` (`uuid.UUID`) is a scalar, not a list: check it before the array/slice branch in `value_serializer.mustache` or it serializes as 16 numbers. Catch: a uuid parameter in GoClientSpec.
- Stub clients and canned responses in test templates must return a body the generated decoder accepts (a valid `Pet` object, not `""` or `{}` once required fields are enforced), or the test fails in `UnmarshalJSON`, not in the code under test. Catch: `go test ./...` in GoClientSpec.
- Encode once, at the URL builder: never strip or add percent-encoding in `SerializeValue`/`SerializeStyled` (double encoding). Catch: the path-encoding tests in GoClientSpec.
- Do not rename the response DTO to `ApiResponse` (it collides with the spec model through the dot-import). Binary and multipart types are `*os.File`, never `[]byte`. Catch: `go build`.
- Every `{{#is...}}` branch of a hand-rolled `UnmarshalJSON` needs the same required-field check (the `isUnevaluatedPropertiesFalse` branch once skipped it); enum defaults map the var name to the wire value through `enumVars`, never lowercase it. Catch: a fixture with an additionalProperties-only model and a non-lowercase enum value.
- Spec-enforced comment text: grep `src/spec` for `contains("` before rewording or deleting a "cruft" comment (the `preSeed` wording is asserted case-sensitively). gofmt rejects a raw U+FEFF, so write the escape in the test template.
- `devbox run` hijacks `go` and golangci-lint is not installed locally, so Go is checked in Docker through the specs. Redirect guards compare against the previous hop, not `via[0]`.

- **F15 (typed `additionalProperties`, #103).** `AdditionalProperties` is `map[string]V` (`*V` when nullable); `UnmarshalJSON` decodes each extra into `V`, and rejects a raw `null` unless nullable.
