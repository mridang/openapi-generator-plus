# Rust SDK generator

`BetterRustCodegen` (generator name `rust-plus`) generates an async Rust crate: one module per API tag, one struct or enum per model, an `ApiClient` trait with a `DefaultApiClient`, authenticators, typed errors, and an in-crate and `tests/` suite. The HTTP stack is `reqwest` 0.12 with `rustls-tls-native-roots` (http2, multipart, charset) on `tokio`; JSON is `serde` and `serde_json`; dates are `chrono`; response bodies are decoded by hand with `flate2`, `brotli` and `zstd`; the crate has an optional `opentelemetry` feature. Generated tests run with `cargo nextest` (JUnit XML through the `ci` profile), with `testcontainers` for the Squid and Chasm fixtures. The floor is edition 2021 with `rust-version = "1.85"` (also `clippy.toml` `msrv`), the Docker toolchain is `rust:1.96`, and formatting is stock `cargo fmt`.

## Read first

- Shared cross-language contract and decisions: `../AGENTS.md` (`src/main/java/io/github/mridang/codegen/generators/AGENTS.md`)
- Repository root: `/AGENTS.md` (workflow, commands, guardrails)
- Codegen class: `BetterRustCodegen.java` (this folder)
- Templates: `src/main/resources/templates/rust/`
- Specs: `src/spec/java/io/github/mridang/codegen/spec/rust/` (`RustSpec`, `RustBuildSpec`, `RustClientSpec`, `RustFormattingSpec`, `RustLintingSpec`, `RustReservedWordsSpec`)
- Golden: `src/spec/resources/generated/rust/`

## Layout

| Path | What lives there | Edit this to change |
|---|---|---|
| `BetterRustCodegen.java` | type mappings, naming, default literals, recursive-property boxing, scheme-authenticator rendering, options structs, `mod.rs` emission, formatter image | a type mapping, an identifier rule, a default-value literal, the file list |
| `templates/rust/errors/*.mustache` | `ApiError` and the status-specific errors, `ConfigurationError`, `OAuth2*Error`, `SerializationError`, `Network*Error` | error classes and their `Display` text |
| `templates/rust/auth/` and `auth/oauth/` | `Authenticator` trait, API key, basic, bearer, per-scheme wrappers, OAuth2 token manager and flows, OpenID Connect | authentication |
| `default_api_client.mustache`, `transport_options.mustache` | reqwest client build, redirects, timeout budget, proxy, TLS, decompression, close | transport options and their validation |
| `base_api.mustache`, `api/api.mustache`, `api/options.mustache` | request assembly, response decoding, per-operation methods and option structs | an operation signature or request/response handling |
| `object_serializer.mustache`, `value_serializer.mustache`, `form_url_encode`, `models/` | JSON and parameter encoding, serde adapters, models, base64 | models and wire encoding |
| `configuration.mustache`, `client.mustache`, `servers.mustache`, `server_configuration.mustache` | `Configuration` and builder, `Client` facade, server tables | client construction |
| `readme.mustache`, `skills.mustache`, `cargo_toml.mustache`, `makefile.mustache`, `clippy_toml`, `deny_toml`, `nextest_toml`, `rustfmt_toml` | README, SKILLS.md, manifest and lint config (the one home for lint exclusions) | README sections, dependencies, lints |
| `templates/rust/test/` and `test/api/` | generated tests (integration under `tests/`; unit tests are also in-crate `#[cfg(test)]` in the serializers) | generated tests |

## Idioms (deliberate differences from the other eleven)

| Topic | What rust does | Why |
|---|---|---|
| Errors | no exceptions: operations return `Result<_, Box<dyn Error>>`; caller mistakes are `ConfigurationError` (a typed `Err`), the not-yet-exchanged code is `OAuth2AuthorizationCodeError::CodeNotExchanged`; the parent error is reached by a composition accessor | no exceptions and no implementation inheritance |
| Auth header accessor | `auth_headers()` is infallible, `try_auth_headers()` is the `Result` variant, `as_http_aware_mut` is the downcast hook, `NO_AUTH` is pointer identity | `async fn` in a trait, no RTTI on trait objects; see D12 |
| Overloads and defaults | `*_with_content_type` request-content-type variants, `get_access_token_with_refresh_url`, `with_client_auth_method`, `send_request_with_options` | no default arguments or overloads |
| Unions | oneOf is an enum with payloads; anyOf is a struct of typed `Option` slots; no `actual_instance` wrapper | sum types are the only tagged union |
| Default config | `Default` trait on `Configuration` and builders | Rust `Default` idiom |
| Ownership | `ApiResult::into_data(self)` moves the payload out | by-value counterpart exists only with ownership |
| Support types | newtypes `JsonValue`, `DateTime`, `Base64Serde`, `ProtoDuration` | orphan rule and serde: no serde_json type leaks into a public signature |
| Model names | `Result`, `Option`, `Vec`, `Box`, `Some`, `None`, `Ok`, `Err` get the `Model` prefix (D45) | `use super::*` in `models/*.rs` would shadow the prelude |
| Recursive models | recursive properties become `Box<T>` (`indirectsRecursiveProperties`) | infinite-size struct (E0072) |
| Identifiers | real keywords become `r#name`; `crate`, `self`, `super`, `Self` keep a leading underscore | raw identifiers are rejected for those four |
| Numbers | `format: number` is `f64`, documented lossy (D83); integer enum members are `Number<n>` (`NumberMinus1`) | native binary float |
| Cancellation | no SDK API: drop the future; `timeout` bounds a call | native async cancellation; see S7 below |
| Unique sets | `HashSet`, written sorted by a serde adapter (D69) | hash order is random |
| Optional description | `ServerConfiguration`/`ServerVariable` description is an empty `String` when absent | empty-string sentinel shared with go, swift and dart; metadata only, never on the wire |

## Settled decisions (do not re-open in review)

Each cross-language decision is stated once in the shared file; here only how rust realises it. Where the Rule line says "intended" the rule is the canonical behaviour for rust and the templates were not confirmed to implement it when this file was written; check the template before relying on it. Decisions on which rust needs no rust-specific rule are listed at the end of this section.

### Strict containers (S1, 2026-11-16)
Rule (intended): a container-of-primitives response (`HashMap<String, i32>`, `Vec<Vec<i32>>`) is checked element by element like a single primitive, so a fractional, quoted or out-of-range leaf is a `SerializationError`.
Why: the container path bypassed the primitive check in seven languages; all twelve now behave alike.
Where: `object_serializer.mustache`, `base_api.mustache`.
Test: the `ObjectSerializer` tests in `test/object_serializer_test.mustache` and the `getInventory`/`getMatrix` rows in the pet/store API tests.

### Content parameters use the SDK serializer (S2, D50, D80)
Rule: a `content: application/json` parameter in query, header, path or cookie is encoded by `object_serializer::serialize(..)?`, mapped to the operation's boxed `SerializationError`, in both the required and the optional arm.
Why: `serde_json::to_string(..).unwrap_or_default()` swallowed failures and produced bytes different from the other eleven SDKs.
Where: `api/api.mustache`, `object_serializer.mustache`.
Test: `searchStore.q` rows in `test/api/store_api_test.mustache`.

### Schema constraints are not enforced (S3, D55)
Rule: the client does not enforce `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems`, `maxItems`; rust has no native enforcement, so the server owns validation, and the README section says so.
Why: only python gets it natively and cheaply through pydantic; hand-written enforcement would differ per language.
Where: `readme.mustache` (the "Schema constraints" section holds the constraint notes).
Test: none: asserting absence is not worth a test; the README text is the contract.

### allOf with a discriminator (S4)
Rule: the base is a discriminated enum with one variant per mapped subtype (the `oneOf` path, fed by `makeClassicBaseUnion` in `BetterRustCodegen`); its hand-written `Deserialize` dispatches on the discriminator, decodes into the mapped subtype, and rejects a missing or unknown value by naming it; each subtype struct keeps all fields (no serde `flatten`) and its constructor sets the discriminator to the mapping value, so it serializes that value. A model reused in two unions is never re-parented.
Why: structs cannot subclass; flattening plus dispatch keeps the wire identical. The same enum serves oneOf with a discriminator and classic allOf; `BetterRustCodegen.removesDiscriminatorPropertyFromChildren` (false) and `setsDiscriminatorDefaultOnChildren` carry the discriminator rules.
Where: `models/model.mustache`, `BetterRustCodegen.java`.
Test: `test/composed_schema_test.mustache` (oneOf cases and `test_bhv_unions_301` to `304`, `test_bhv_serializationobject_301`); fixture `Vehicle` and `Shape` in the petstore spec.

### Required and nullable (S5, D66)
Rule: a required nullable property must be present on decode (absent is an error), an explicit `null` is kept, and encode always writes the key (`null` when unset). A required non-nullable field hard-fails on `null`.
Why: the canonical behaviour is shared by all twelve; the `required_nullable` deserializer distinguishes absent from `null`.
Where: `models/model.mustache` (`requiredNullablePlain`), `object_serializer.mustache` (`required_nullable`).
Test: `test_required_nullable_null_is_kept`, `test_required_nullable_missing_key_rejected` and `test_required_nullable_writes_null_key` in `object_serializer.mustache`; `test/composed_schema_test.mustache` nullable `label` case.

### uniqueItems order unspecified (S6, D69)
Rule: sets are sent in the language's natural order; for rust the D69 rule applies and scalar sets are written sorted (numbers numerically, strings by code point, booleans false first) through `object_serializer::sorted_set::serialize` (and `::option`). Byte-array items are excluded. Do not add sorting elsewhere.
Why: a `HashSet` iterates in random order, so unsorted bytes would differ per run.
Where: `models/model.mustache`, `object_serializer.mustache`.
Test: `sorted_set` tests in `object_serializer.mustache`; the model golden.

### Cancellation is dropping the future (S7)
Rule: rust adds no cancellation parameter and no client signal: a caller cancels by dropping the future or wrapping it in `tokio::time::timeout`/`select!`, and `TransportOptions.timeout` bounds a call. Cancellation is never reported as `NetworkTimeoutError`.
Why: this is the native mechanism, and a wrapper handle would be less natural than the one every async Rust caller already uses. The README section "Cancellation" says so.
Where: `readme.mustache`, `default_api_client.mustache`.
Test: `test_cancelled_code_exchange_keeps_the_delegate` in `test/openid_connect_authenticator_test.mustache`.

### Parameter order (S8, owner confirmation pending)
Rule (intended): required parameters, then server, then the optional-parameters struct; rust has no per-call cancellation handle. Adding security never moves an argument.
Why: callers must not break when a spec gains security on an operation.
Where: `api/api.mustache`, `api/options.mustache`.
Test: server-override rows in `test/api/pet_api_test.mustache`.

### Injected transport (S9)
Rule: the client accepts a `reqwest::Client` (`Client::with_http_client`, `DefaultApiClient::with_client`), keeps the SDK defaults, never closes a caller-supplied client, and fails fast with `ConfigurationError::InvalidArgument` on a conflicting proxy, CA or `verify_ssl(false)`. Landed. Open: a supplied client that follows redirects itself bypasses the SDK redirect guard (F1047; reqwest offers no way to read the policy back, so the README tells callers to use `Policy::none()`).
Why: OpenAI, Stainless, Speakeasy, Fern, Azure and Stripe SDKs all accept the standard client. F405 is open.
Where: `default_api_client.mustache`, `transport_options.mustache`, `client.mustache`.
Test: none yet.
Landed with F824: the facade has `close()` and `is_closed()` (delegating to `ApiClient::close`/`is_closed`); a closed client fails requests with `ConfigurationError::InvalidState("ApiClient is closed")` and a supplied `reqwest::Client` is never shut down (`test/client_test.mustache`).
Also landed: `TooManyRequestsError` (429) wired like `ConflictError`; redirect method rewriting follows RFC 9110 15.4 (`redirect_next_method`); content cookies are `urlencoding::encode`d JSON, already the D52 unreserved set.

### Dependencies (S10)
Rule: a small pure crate or a version bump is acceptable when it standardises behaviour; never a crate that needs a native binary. `brotli`, `flate2`, `zstd` (static), `encoding_rs`, `urlencoding`, `regex`, `base64`, `sha2` (PKCE S256) are accepted; reqwest uses `rustls-tls-native-roots`, not OpenSSL.
Why: a native-binary dependency makes the client harder to install and test.
Where: `cargo_toml.mustache`, `deny_toml.mustache`.
Test: `cargo deny check` (`make audit`), `RustBuildSpec`.

### Scope order, UUID case, ASCII blank keys, error cause (S14, D04, D19, D67)
Rule: OAuth scopes keep the spec's declared order (`formatRustScopes`, `scope=pets%3Aread+pets%3Awrite`); `Uuid` writes lowercase hyphenated; an API key or bearer token is blank only when empty or ASCII whitespace (space, `\t`, `\n`, `\r`, `\f`, `\v`) using `trim_matches` with that set, not `trim()`, and a leading `Bearer ` is stripped first (intended); every status-specific error accepts an optional cause through `with_source` (the `ApiError` constructor takes only status, message, headers and body, and the cause is attached afterwards).
Why: `str::trim` strips Unicode whitespace the other eleven keep.
Where: `BetterRustCodegen.formatRustScopes`, `auth/bearer_authenticator.mustache`, `auth/api_key_authenticator.mustache`, `errors/api_error.mustache`.
Test: `test/oauth2_client_credentials_authenticator_test.mustache`, `test/bearer_authenticator_test.mustache`, `test/api_error_test.mustache`.

### Secrets are redacted in string forms (S15)
Rule: the `Debug` forms of `Configuration` and `TransportOptions` mask the proxy password and any default header named `Authorization`, `Proxy-Authorization`, `Cookie`, or containing key, token or secret, as `***` (`redact_headers`).
Why: a logged client must not leak credentials.
Where: `configuration.mustache`, `transport_options.mustache`.
Test: `test/configuration_test.mustache`, `test/transport_options_test.mustache`.

### Timeouts, redirects, validation timing (S16, D76, D77, D79, D72, D73)
Rule: one end-to-end timeout budget (a `std::time::Instant` deadline; `remaining_budget` per hop, a zero remainder is `NetworkTimeoutError`); a caller-supplied stream or file handle is never closed; proxy URL validation happens in `build()`, not in the builder setter; `caCertPath` is validated even when `verify_ssl` is false and pins the trust store. The budget is landed. Landed (batch 6): `timeout` must be a positive number of milliseconds or `None`; zero and negative values fail `build()` with `ConfigurationError::InvalidTimeout`, displayed as `Invalid timeout: must be a positive number of milliseconds (got N)` (D77). Landed (batch 7): proxy and CA errors store the full canonical message and display it verbatim (D79): `Invalid proxy URL: <url>`, `Invalid proxy URL (must use http or https scheme): <url>`, `Invalid proxy URL (missing host): <url>` with the userinfo shown as `***@`, and `failed to read|parse CA certificate from "<path>": <cause or no PEM blocks found>`.
Why: the budget used to apply per hop; redirects could multiply it.
Where: `default_api_client.mustache`, `transport_options.mustache`, `errors/configuration_error.mustache`.
Test: `test/default_api_client_test.mustache`, `test/default_api_client_unit_test.mustache`, `test/transport_options_test.mustache`.

### OIDC rediscovery keeps the session (D01)
Rule: `OAuth2TokenManager` owns tokens and the exchanged flag (an `AtomicBool` with `mark_exchanged`/`is_exchanged`); the auth-code authenticator holds an `Arc<OAuth2TokenManager>` and gains `pub(crate) with_token_manager`; on rediscovery the OIDC authenticator builds the replacement around the previous manager, and discovery is serialised (single-flight).
Why: replacing the delegate reset the session, so every call after the discovery max-age failed with "Must call exchange_code()". 11 of 12 SDKs had the bug; elixir was already correct.
Where: `auth/oauth/openid_connect_authenticator.mustache`, `oauth2_auth_code_authenticator.mustache`, `oauth2_token_manager.mustache`.
Test: `test/openid_connect_authenticator_test.mustache`.

### Refresh grant carries client authentication (D02)
Rule: `get_access_token_with_refresh_url(token_url, Option refresh_url, params, headers)` posts the refresh to `refresh_url` with `client_id`/`client_secret` from the params and the same extra headers; `get_access_token_with_headers` delegates to it.
Why: RFC 6749 sec 6 and 3.2.1; rust has no default arguments, hence the separate method.
Where: `auth/oauth/oauth2_token_manager.mustache`, `oauth2_password_authenticator.mustache`, `oauth2_auth_code_authenticator.mustache`.
Test: `test_refresh_grant_goes_to_the_refresh_url` in `test/oauth2_auth_code_authenticator_test.mustache`.

### exchange_code replaces the session (D03)
Rule: `OAuth2TokenManager::exchange` locks, saves and clears the session, posts the params verbatim, keeps only what the response carried, marks the session exchanged, and restores the old session on failure; `exchange_code` calls only it.
Why: the exchange used to go through the token cache and refresh path.
Where: `oauth2_token_manager.mustache`, `oauth2_auth_code_authenticator.mustache`.
Test: `test/oauth2_token_manager_test.mustache`.

### OAuth wording, grammar and request order (D05, D07, D08, D09, D10)
Rule: OIDC always requests `openid`; token and discovery errors use the shared wording ("Token response is not valid JSON", "... is not a JSON object", "Token response missing or empty access_token field", no parser detail); `expires_in` and `max-age` use one digits-plus-fraction grammar with a one-year ceiling and a saturating `max-age` parse; precondition texts are "Authorization code must not be empty" and "Must call exchange_code() before making API requests" (both landed in batch 6, pinned in `test/oauth2_auth_code_authenticator_test.mustache`). The token body and authorize URL are emitted in the fixed order grant_type, code, refresh_token, username, password, client_id, client_secret, redirect_uri, scope, then the rest sorted (`ordered_params`), which is landed; the remaining wording and grammar items are intended.
Why: `HashMap` order and per-language wording made bytes and messages differ.
Where: `auth/oauth/*.mustache`, `errors/oauth2_token_error.mustache`, `errors/oauth2_authorization_code_error.mustache`.
Test: the `test/oauth2_*_test.mustache` and `test/openid_connect_authenticator_test.mustache` files.

### Implicit flow, empty state, sync auth accessor (D06, D11, D12)
Rule: an empty implicit access token counts as unset; an empty `state` is absent in authorize URLs; the infallible `auth_headers()` returns an empty map on failure and is documented as forced, while `try_auth_headers()` is the loud path and is the one the transport calls.
Why: trait objects with an infallible accessor cannot throw; F290 stays open for the other SDKs.
Where: `auth/oauth/oauth2_implicit_authenticator.mustache`, `base_api.mustache`, `authenticator.mustache`.
Test: `test/oauth2_implicit_authenticator_test.mustache`.

### Unusable responses carry the real headers (D13)
Rule: the three redirect-refusal `ApiError::new` calls pass `Some(collect_headers(response.headers()))` and the decompress failure keeps `Some(resp_headers)` (landed in batch 7). Test: `test_collect_headers_lowercases_names_and_joins_repeated_lines` and `test_redirect_refusal_messages_are_canonical_and_credential_free` (in-crate, `default_api_client.mustache`).
Why: a response that arrived is not a "no response" error.
Where: `default_api_client.mustache`.
Test: `test/default_api_client_test.mustache`.

### Error text matrix (D14, D15, D16, D17, D20, D28, D29, D79)
Rule: landed in batch 6: the redirect refusals read "Refusing to follow redirect to non-HTTP(S) URL: <next>", "Refusing to replay request body across HTTPS->HTTP redirect: <cur> -> <next>" and "Too many redirects (exceeded {})" (D16; the URLs are printed without query or fragment); the "Response headers:" line is compact JSON with sorted keys (D17, a `BTreeMap` in `render_error`); use-after-close is `ConfigurationError::InvalidState("ApiClient has been closed and can no longer send requests")` (D29). Still intended: exact-string tests for `OAuth2ServerError`; serializer messages start with "Failed to ..."; list values render `[a, b, c]` not `{:?}`; `ConfigurationError` `Display` prints the reason verbatim (`InvalidTimeout`, `InvalidProxy` and `InvalidCaCertificate` do since batch 7; the rest keep their prefix); the missing-parameter message names the caller-facing `{{paramName}}` identifier.
Why: one message grammar across all twelve; `HashMap` order is random.
Where: `errors/*.mustache`, `object_serializer.mustache`, `default_api_client.mustache`, `server_configuration.mustache`, `api/api.mustache`.
Test: `test/api_error_test.mustache` (the headers rows are exact; replace the remaining `is_empty` assertion with exact-string rows), `test/default_api_client_unit_test.mustache` (redirect refusals, use-after-close), `test/server_configuration_test.mustache`.

### Header injection checks are case-insensitive (D21)
Rule: `has_header()` compares names with `eq_ignore_ascii_case`; if the caller supplied any case variant of `User-Agent` or `X-Request-ID`, even empty, nothing is injected and the caller's key and value stay as written.
Why: RFC 9110 sec 5.1.
Where: `default_api_client.mustache`.
Test: `test/default_api_client_test.mustache` (echo route).

### Response body decoding (D30, D31, D32, D33)
Rule (landed in batch 7 for the non-JSON branch): a binary return is raw bytes and never JSON-parsed; a `format: byte` return is JSON by default with one strict base64 decoder and every failure a `SerializationError`; a non-JSON success type on a non-binary return gives a `String` its text and anything else a `SerializationError` naming the content type; `.unwrap_or_default()` and `.ok()` are replaced by mapped errors; a `String` return never base64-decodes. `base_api.mustache` still derives binary from `params.return_type == "Vec<u8>"` (`is_binary_return`); the decision asks for an explicit `binary_return` flag.
Why: five different outcomes across the twelve SDKs.
Where: `base_api.mustache`, `api/api.mustache`.
Test: `test/base_api_test.mustache`.

### Form values and multipart order (D39, D53)
Rule: `form_scalar_to_string` returns a `Result` and rejects an object or nested array with `SerializationError` "form-urlencoded field <name> must be a scalar or array of scalars"; null array elements are skipped. Multipart parts are an ordered `Vec<(String, MultipartValue)>`, not a `HashMap`.
Why: `other.to_string()` emitted rust-specific repr; `HashMap` made part order nondeterministic.
Where: `base_api.mustache`, `api_client.mustache`, `default_api_client.mustache`, `api/api.mustache`.
Test: `test/base_api_test.mustache`, pet API tests for `setPetPreferences`.

### String literals and doc prose (D40, D41)
Rule: every spec string written into a code literal (defaults, server URLs and variables, OAuth URLs and scopes) goes through one value-preserving escaper (`toRustStringLiteral`; control characters as `\u{..}`; the API key name and the OAuth and OpenID Connect URLs of the scheme wrappers use it since batch 6), and descriptions use triple braces in `api.mustache` and `servers.mustache`; the manifest description goes through `lambda.jsonString`.
Why: stripping or double-escaping changed values.
Where: `BetterRustCodegen.toDefaultValue`, `formatRustScopes`, `servers.mustache`, `api/api.mustache`, `cargo_toml.mustache`.
Test: the golden, the escape rows in the shared specs and `BetterRustCodegenTest`.

### Per-operation server types (D42)
Rule: a server variable gets one type per operation; a name occurring in two or more variants of the operation becomes `<VariantName><Var>`, a variable used once keeps its plain name (`enrichOperationServers`, computed once).
Why: duplicate enum names failed to compile.
Where: `BetterRustCodegen.java`, `api/api.mustache`.
Test: `getMultiServerPetInfo` rows in `test/api/pet_api_test.mustache`.

### Identifier fallbacks and reserved words (D43, D44, D45)
Rule: a digit-leading or empty sanitised name gets a deterministic letter `n` before casing and never goes through `escapeReservedWord`; `r#` is used only for real keywords, and `crate`, `self`, `super`, `Self` use an underscore; model names colliding with the prelude set get the `Model` prefix (landed for `Result` as `ModelResult`).
Why: the escape prefix made digit names illegal.
Where: `BetterRustCodegen.escapeReservedWord`, `toModelName`, `RUST_PRELUDE_TYPES`.
Test: `RustReservedWordsSpec`.

### String enum literals (D46)
Rule: enum string values are escaped in order: backslash doubling; CR, LF, TAB; then the language's enum escape table; the quote character is escaped, never stripped. The value sits inside `#[serde(rename = ...)]`, so the same escapes apply.
Why: dropping a quote changed the wire value.
Where: `AbstractBetterCodegen.escapeEnumStringValue`, `BetterRustCodegen.formatEnumStringLiteral`.
Test: the enum golden and `EdgeCases` rows.

### Object-valued query and header parameters (D47)
Rule (intended): a model or free-form map parameter becomes a wire-keyed map through `serde_json::to_value` (with `preserve_order` or a `Vec<(String,String)>`), then expands per the style/explode row in declaration order, with nested values a `SerializationError`.
Why: the same expansion in all twelve; object-valued parameters are #109.
Where: `value_serializer.mustache`, `object_serializer.mustache`.
Test: `test/value_serializer_test.mustache`.

### Path parameter dot segments (D48)
Rule: a path parameter of exactly `.` or `..` is rejected before dispatch with `ConfigurationError::InvalidArgument("Path parameter '<name>' must not be '.' or '..'")`; the guard is skipped for uuid parameters (which also removed an `is_empty`-on-`Uuid` compile error). An empty required path parameter is an `Err`, never a panic.
Why: dot segments are normalised away by URL parsing and change the route.
Where: `api/api.mustache`, `value_serializer.mustache`.
Test: `test/api/pet_api_test.mustache`.

### allowReserved (D49, D78)
Rule: only the RFC 3986 section 3.4 query set stays literal; `#`, `[` and `]` are percent-encoded. The `'` to `%27` rewrite by the `url` crate (WHATWG special-query set) is a documented FORCED exception, pinned by a transport test; the encoder output (`encode_query_allowing_reserved`) is the contract.
Why: reqwest/url cannot be told to leave `'` alone.
Where: `value_serializer.mustache`, `default_api_client.mustache`.
Test: the full-string unit row and the `getPetTag` row in `test/value_serializer_test.mustache` and `test/api/pet_api_test.mustache`.

### format: byte parameters (D51, D58)
Rule: `Vec<u8>` parameters are base64 (STANDARD engine) in query, header, path, form and cookie, chosen by type: `stringify`, `to_query_value` and `to_header_value` check for `Vec<u8>` by downcasting through `Any`. A `format: byte` map value is a base64 string through `base64_serde::map` and `map_option`. Decode is strict RFC 4648 with padding.
Why: serde would otherwise emit a number array.
Where: `object_serializer.mustache`, `models/base64_serde.mustache`.
Test: `test/value_serializer_test.mustache`, `test/object_serializer_test.mustache`.

### Required parameters and read-only/write-only (D56)
Rule: `readOnly` and `writeOnly` have no wire effect in rust; `model.mustache` adds a "Read-only" or "Write-only" doc remark only. An empty required string parameter is rejected before dispatch in path, query, header, form and cookie, except `allowEmptyValue: true` query parameters (`queryStyledAllowEmpty`).
Why: rust was already wire-neutral.
Where: `models/model.mustache`, `api/api.mustache`.
Test: `Account.id`/`password` in the golden; `test/api/pet_api_test.mustache`.

### Defaults (D59, D60, D61)
Rule: floating-point defaults always carry a decimal point (`Some(2.0)`, landed); a required property with a default (including the synthetic discriminator) gets `#[serde(default = "default_<field>")]`, and a default on a `$ref` enum becomes the typed member (`Some(Availability::OnHold)`) consistent with the hand-written `Default` (both intended).
Why: `Some(2)` on an `f64` is E0308.
Where: `BetterRustCodegen.toDefaultValue`, `models/model.mustache`.
Test: the default-value assertions in the golden and `test/composed_schema_test.mustache`.

### format: uri and format: time (D62, D81)
Rule: `format: uri` sets `vendorExtensions.isUri` and decodes through `object_serializer::absolute_uri` (any scheme, relative references, whitespace and empty rejected). `format: time` is written by `object_serializer::format_time`: `HH:mm:ss`, plus `.SSS` (truncated) only when milliseconds are non-zero, in params and bodies.
Why: one wire rule in all twelve.
Where: `object_serializer.mustache`, `BetterRustCodegen.java`, `models/model.mustache`.
Test: `test_uri_property_accepts_any_absolute_uri_and_rejects_relative_references` and the `format_time` tests in `object_serializer.mustache`.

### additionalProperties collisions (D63)
Rule: `additional_properties` uses `#[serde(flatten, serialize_with = "serialize_additional_properties")]`, a per-model function that lists the declared wire names and calls `object_serializer::serialize_extras`, which drops colliding keys; the declared value wins.
Why: flatten alone writes the key twice.
Where: `models/model.mustache`, `object_serializer.mustache`.
Test: `Metadata` rows in `test/metadata_test.mustache`.

### Models cannot be sent incomplete (D68)
Rule (intended): no `Default` (derived or hand-written) for a model with required variables lacking defaults; `new(...)` is the constructor.
Why: java, node and rust let callers build a model missing required properties; see the open owner question in the shared file (D68).
Where: `models/model.mustache` (`hasDefaultedOptionalVars`).
Test: `test/composed_schema_test.mustache`, `test/metadata_test.mustache`.

### No ambient proxy (D74)
Rule: `build_http_client` starts from `ClientBuilder::new().no_proxy()`; only `TransportOptions.proxy` adds a proxy, so `HTTP(S)_PROXY` and `NO_PROXY` are ignored.
Why: reqwest reads the environment by default; the other SDKs ignore ambient settings.
Where: `default_api_client.mustache`.
Test: the `NO_PROXY` rows in `test/default_api_client_unit_test.mustache`.

### Brotli is advertised and decoded
Rule: `Accept-Encoding` is `gzip, deflate, br, zstd` and `decompress_body` has a `"br"` arm (`brotli::Decompressor`); an unsupported or chained `Content-Encoding` raises an `ApiError` with the status, and an empty body passes through for any coding. Response headers drop `Content-Encoding` and `Content-Length` when the body was decoded.
Why: rust was the only codec-capable SDK not advertising `br`; passing encoded bytes through is a silent failure.
Where: `default_api_client.mustache`, `cargo_toml.mustache`.
Test: `test/default_api_client_unit_test.mustache`. CI validates this (amd64).

### Request content type selector rejects unknown values
Rule: the `*_with_content_type` variants reject an unrecognised content type with `ConfigurationError::InvalidArgument`; `None` selects the first declared type.
Why: a typo must not ride the wire as an undeclared Content-Type.
Where: `api/api.mustache`.
Test: `test/api/pet_api_test.mustache` (`uploadPetDocument`, `setPetAvatar`).

### Per-scheme API-key wrappers import ApiKeyLocation
Rule: `renderSchemeAuthenticator` emits `use super::ApiKeyLocation;` only when the base is `ApiKeyAuthenticator` (`needsApiKeyLocation`); bearer, basic and OAuth wrappers must not import it.
Why: an unused import fails `-D warnings`; it was invisible locally because rust only compiles on CI.
Where: `BetterRustCodegen.renderSchemeAuthenticator`.
Test: `RustLintingSpec`, `RustBuildSpec`.

### The no-auth comment, deprecation markers, empty body message
Rule: the "explicitly unauthenticated" comment is emitted only when `vendorExtensions.op.securityNone`; every public variant of a deprecated operation carries `#[deprecated]`; an empty body error reads `Expected a response body for <op> but received none`.
Why: the comment contradicted inherited-auth operations; the wording is uniform in all twelve.
Where: `api/api.mustache`, `base_api.mustache`.
Test: the golden; `test/api/pet_api_test.mustache`.

### README floor and lint exclusions
Rule: the README "Requirements" version states `Cargo.toml rust-version` (1.85); lint exclusions live only in `[lints]` of `cargo_toml.mustache`, each with its reason, and no generated file carries `#![allow]` or `#[allow]`.
Why: an understated README advertises a version the build rejects; exclusions in one place are auditable.
Where: `readme.mustache`, `cargo_toml.mustache`, `clippy_toml.mustache`.
Test: `RustLintingSpec`.

### Decisions where rust already conforms
Rule: no rust-specific rule beyond the shared one for D18 (BOM stripped in the shared JSON helper), D22 (`User-Agent` token is the crate name), D23 and D24 (`derive(Debug)`, `PartialEq`), D25, D26 (no default arguments: the optional body is `Option`), D27 (`ApiResult` headers copy), D33 and D34 (text/plain on a `String` return), D35 to D38 and D89 and D90 (multipart), D52, D54, D57, D64, D65, D71, D75, D82, D84 to D86, D88 (`proto_duration` handles over-292-year durations; go is the forced one), D91 (`encoding_rs` full registry), D92, D94.
Why: confirmed already canonical.
Where: see the named templates in Layout.

## Known limitations and out of scope

Out of scope in every SDK, and the README says so (S11, closed backlog issues #53, #70, #78, #88, #90): callbacks and webhooks, XML bodies and `xml.nodeType`, Link Objects and mutual TLS. OpenAPI 3.2 and 3.1 are tracked by epics #49 and #96, and loading 3.2 documents is issue #50 (the pinned swagger-parser rejects it).

SOCKS proxies are rejected at construction (only `http://` and `https://`); enabling them needs extra reqwest features in all twelve. The SDK ships no `LICENSE` file.

Open or deferred items that touch rust: F346 (no data-returning variant of the request-content-type selector; needs a naming decision because `_with_content_type` returns `ApiResult` and cannot be renamed); F390 (an overflowing `1e400` is rejected in rust, go, swift and csharp; the other SDKs now reject it too); F195 (the inline-comment ban is off for rust because `getInlineCommentPattern()` returns null, deferred; doc comments are `///` and block comments are used); the docs build is #120; classic allOf with a discriminator is #104; F290 (infallible `auth_headers()` returns an empty map, forced here).

The oneOf and anyOf wrapper no-match strings differ per language (rust: `<X>: anyOf payload matched no member schema`); this is documented and not harmonised. A `format: number` is `f64` and lossy by design. The first-wins duplicate keys and 512-level decoder cap forced exceptions belong to swift, not rust.

### Decisions taken in batch 6

The audit's own proposal was used as the default where no owner decision existed. None of these changes the public API shape.

- **Header merging is case-insensitive (extends D21).** Every layer (transport defaults, configuration defaults, header selector, operation headers, auth headers, injected headers) writes through `api_client::insert_header`, which replaces an entry whose name matches under ASCII case-folding; the later layer wins and keeps its spelling. `remove_header` is the case-insensitive removal used for `Content-Type`. A caller's `accept-encoding` in any case suppresses the SDK's own. Where: `api_client.mustache`, `base_api.mustache`, `default_api_client.mustache`. Test: in-crate tests of `insert_header`/`remove_header` and `test_request_headers_merge_case_insensitively` in `test/default_api_client_unit_test.mustache`.
- **A 3xx without a usable Location is returned unchanged (D70).** "Too many redirects" is raised only when the budget is spent and the response carries a Location that resolves against the current URL, so `max_redirects(Some(0))` against a Location-less 302 returns the 302. Test: `test_a_3xx_without_location_is_returned_unchanged_when_max_redirects_is_zero` and its with-Location counterpart in `test/default_api_client_unit_test.mustache`.
- **One expiry margin.** The token manager stores `now + expires_in - min(60 s, expires_in / 2)` and compares it as is, so a 60 s token is reused for 30 s instead of never; a non-positive `expires_in` is stale at once. Test: `test_expires_in_within_the_old_stacked_margins_is_still_cached` in `test/oauth2_token_manager_test.mustache`. The stale-token tests now use `expires_in: 0`.
- **A refresh response without `expires_in` clears the expiry.** The token is then cached until a 401 or `invalidate_access_token`, as the first fetch and `exchange` already do; the old expiry is never kept. Test: `test_refresh_without_expires_in_clears_the_stale_expiry`.
- **Credentials stay out of messages.** `transport_error` calls `reqwest::Error::without_url` before it builds the message and keeps the stripped error as the source; the redirect-refusal messages print URLs through `redacted_url` (no query, no fragment). Test: `test_transport_error_does_not_carry_the_request_url` and `test_redacted_url_drops_the_query_and_fragment` (in-crate, `default_api_client.mustache`).
- **String enums implement `Display`.** A string-valued enum (standalone or inline) writes its wire value, so `Color::Green.to_string()` is `green`. Integer enums are unchanged. Where: `models/model.mustache`. Test: `test_string_enum_displays_its_wire_value` in `test/composed_schema_test.mustache`.
- **Scheme literals are escaped (D40).** The API key name, token URL, authorization URL, refresh URL and OpenID Connect URL go through `toRustStringLiteral`, so a name holding a quote or backslash cannot break the generated source. Test: `BetterRustCodegenTest`.
- **F580.** `test_custom_ca_cert_replaces_the_system_trust_store` fails, rather than returns, when `example.com:443` is unreachable: the sole-anchor coverage never disappears silently. The CI runner has network access (it fetches crates).
- **F804 close semantics.** The canonical text is reworded for rust: `close()` makes the client refuse new requests and drops its handle on the pooled `reqwest::Client`; idle keep-alive sockets are released by reqwest's idle timeout, not at the call. No `pool_max_idle_per_host(0)` (it would cost every caller a connection per request).
- **D77, D09, D16, D17, D29** landed as described under their sections above.

### Owner decisions of 2026-11-17, second round (rust)
- **F857 (credentials per scheme).** `Credentials` (`credentials.mustache`, only when the API has a security scheme) has one `with_<scheme>` setter per distinct scheme; `Client::with_credentials(host, credentials, transport)` hands them to every API group, and `BaseApi::authenticator_for` picks the credential of the first scheme the operation lists that is set. The Options struct has no `auth` field: there is no per-call scheme override. Tests: `test/api/pet_api_test.mustache` (`test_pet_api_credentials_*`).
- **F840 (cookie parameters).** Already as decided: a value is sent raw when every character is a cookie-octet, otherwise `ConfigurationError::InvalidArgument` before sending; nothing is percent-encoded. Tests: `pet_api_test.mustache`.
- **F1078 (close).** Already graceful and idempotent (`DefaultApiClient::close`); `test_close_lets_an_in_flight_request_finish` in `default_api_client_unit_test.mustache` pins the in-flight case.
- **F316 (no validation at construction).** Already so: constructors and `Default` never validate; unknown enum strings fail at decode (`composed_schema_test.mustache`).
- **F1085 (test TLS keys).** Not done: the shared writeTestFixtures copies `certs/*-key.pem` and the shared Chasm mounts `server-key.pem`; removing them is a shared change.

## Build-breaking rules

- No `#![allow(...)]` or `#[allow(...)]` in generated source: exclusions go in `[lints]` in `cargo_toml.mustache` with a reason.
- `cargo clippy --all-targets --all-features -- -D warnings` and `RUSTDOCFLAGS="-D warnings" cargo doc --no-deps` must pass (`RustLintingSpec`), so an unused import or a missing doc fails.
- `cargo fmt -- --check` must pass with stock rustfmt; the generator formats with `cargo fmt` in the pinned `rust:1.96` image (`RustFormattingSpec`).
- Generated tests use block comments in templates; the `//` inline-comment check is off for rust but do not add new inline comments.
- `unsafe_code` is forbidden; `Send` and `Sync` must follow from the field types.
- Rust compiles only in the Docker spec (amd64); a template change is not verified until `RustBuildSpec` passes. CPU is bounded with `CARGO_BUILD_JOBS=2`.
- Run `cargo nextest run --all-features` so the `opentelemetry` feature tests are built.
- The unit tests of serializers live in-crate; integration tests under `tests/` share fixture containers through `testcontainers_helper` with `reusable-containers`.
- The generator and the golden must be regenerated together; the golden formatters run in Docker and can be flaky (see the root AGENTS.md).

## Verify

```
etc/ci-local.sh check Rust
mvn verify -Dgroups=rust
```

Inside the generated crate: `make lint`, `make test`, `make docs`, `make audit`.

### Decisions taken in batch 7

The audit's own proposal was used as the default where no owner decision existed. None of these changes the public API shape.

- **A non-JSON 2xx body on a model return is a `SerializationError` (D32).** The non-JSON, non-binary branch of `invoke_api_for_result` gives a `String` return its text and every other return type `SerializationError` "Unexpected response Content-Type '<media-type>' for <Class>.<operation>" (the `operation` field of `InvokeApiParams`, rust naming, so `PetApi.get_pet_by_id`); a body that is not valid base64 is a `SerializationError` instead of an empty string. Test: `test_base_api_rejects_a_text_plain_body_for_a_model_return`, `test_text_plain_body_for_a_string_return_is_the_text`, `test_text_plain_body_for_a_model_return_is_a_serialization_error_naming_the_type` and `test_bhv_errors_054_an_unexpected_content_type_error_names_the_operation` in `test/base_api_test.mustache`. The four permissive base_api tests now assert their result.
- **An enum `$ref` multipart part is a text part (D36).** `api.mustache` writes a `$ref` to a top-level enum (`isEnumRef`) as `MultipartValue::Text(stringify(..))` with no part Content-Type; only models stay `application/json` parts. Test: `test_pet_api_upload_pet_document_scalar_parts_are_plain_text` (`swatch`). The binary-response tests in `test/base_api_test.mustache` use `get_pet_avatar_with_http_info`, because a model return now rejects a non-JSON body (D32).
- **Returned headers describe the decoded body (D92).** `Content-Encoding` and `Content-Length` are removed from the returned headers when `decompress_body` decoded the body, and kept when it passed through. Test: `test_every_advertised_content_encoding_is_decoded` and the gzip and zstd rows in `test/default_api_client_test.mustache`.
- **The cross-origin strip covers every credential-named header.** `is_credential_header` strips `SENSITIVE_HEADER_NAMES` plus any header the redaction rule (`is_sensitive_header`: `Authorization`, `Proxy-Authorization`, `Cookie`, or a name containing `key`, `token` or `secret`) calls a credential. A header with another name set by a custom authenticator (for example `X-Custom-Auth`) still follows the redirect; a per-origin tracking of SDK-attached headers is not done. Test: `test_is_credential_header_covers_the_redaction_rule` and `test_token_named_header_stripped_on_cross_origin_redirect`.
- **Error text is bounded and masked.** `render_error` caps the body at 1024 bytes with a `... (N more bytes)` suffix, escapes control characters (`\n`, `\r`, `\t`, `\u00XX`) and masks the value of `Set-Cookie`, `WWW-Authenticate`, `Proxy-Authenticate` and every header `is_sensitive_header` flags as `***`; the full body and headers stay on the error's accessors. Test: `test_api_error_display_caps_the_body_and_escapes_control_characters` and `test_api_error_display_masks_credential_bearing_response_headers`.
- **Additional properties are written in ascending key order.** `serialize_extras` sorts the extras so the body bytes do not depend on the per-process `HashMap` order. Map-typed properties (`HashMap<String, T>` fields) still iterate in hash order; changing them needs `BTreeMap` in the type mapping (a public API change) or a per-field `serialize_with`. Test: `test_serialize_extras_writes_the_keys_in_ascending_order`.
- **A default beside a `$ref` is the typed member or the literal of its type (D60).** The shared `captureRefSiblingDefault` puts the sibling default on the property (`vendorExtensions.siblingDefault`, `siblingDefaultIsEnum`, `siblingDefaultEnumType`, `siblingDefaultEnumVar`) and `BetterRustCodegen.seedSiblingDefault` (called from the `fromProperty` override, so the `new()` copies carry it) turns it into `defaultValue`: `Availability::OnHold` for a referenced string enum, `String::from("..")`, an integer, a boolean or a number with a decimal point otherwise. `toDefaultValue` still names the member for the component's own default. Test: `BetterRustCodegenTest` (`aSiblingDefaultOn...`, `aComponentDefaultOnAReferencedEnumNamesTheTypedMember`) and `test/composed_schema_test.mustache` (`test_referenced_enum_with_sibling_default_...`, `StockItem.availability`).
- **Per-call authenticators get the transport.** `Authenticator::inject_api_client(&self, client)` (default: nothing) is called by `BaseApi` before it asks the effective authenticator for headers; the OAuth2 and OpenID Connect authenticators store the client only when they have none (`OAuth2TokenManager::ensure_api_client`). Public API addition. Test: `test_a_per_call_authenticator_is_given_the_transport`.
- **A 401 renews the token and replays once.** `Authenticator::invalidate_access_token(&self)` (default: `false`, so API-key, basic and bearer authenticators never replay) drops the cached access token and keeps the refresh token, so the next token call is a refresh grant first; the OAuth2 and OIDC authenticators return `true`. `BaseApi` replays the request once with the new headers when the first answer is 401; a multipart body is not replayed and a failed re-authentication leaves the original 401. Public API addition. Test: `test_a_401_renews_the_token_and_replays_the_request_once`, `test_a_second_401_is_reported_after_one_replay`, `test_invalidate_then_get_uses_the_refresh_grant_first`.
- **PKCE S256 on the authorization-code flow.** `build_authorization_url` adds `code_challenge` and `code_challenge_method=S256`; the verifier (43 characters from 32 random bytes) lives in the token manager, so OIDC rediscovery keeps it, and `exchange_code` sends it as `code_verifier` and clears it on success. New dependency: `sha2`. No OIDC nonce is sent because the SDK never validates an ID token. Test: `test_authorization_code_flow_uses_pkce_s256`.
- **OIDC discovery checks.** The discovery `issuer`, when present, must equal the discovery URL minus `/.well-known/openid-configuration` (a trailing slash is ignored; a document with no issuer is accepted); the authorization and token endpoints must be https, or http on a loopback host (`localhost`, `*.localhost`, `127.0.0.1`, `[::1]`). Both failures are a `SerializationError`. Test: `test_discovery_issuer_must_match_the_discovery_url`, `test_discovery_refuses_clear_text_endpoints_except_loopback`.
- **Token and discovery requests skip default headers.** `RequestOptions::skip_default_headers` (new public field and builder) makes `DefaultApiClient` leave `TransportOptions::default_headers` out; the token POST and the OIDC discovery GET set it. Test: `test_token_request_headers_and_default_header_opt_out` (also pins the token request's `Content-Type` and `Accept`).
- **Object-valued parameters are expanded (D47).** A model or map parameter in form style with explode is one `name=value` pair per property (`object_serializer::object_query_pairs`); other styles join `k,v,k,v` (`k=v,k=v` for `simple` with explode, a space or `|` for the delimited styles) through `stringify_param`; a nested value is a `SerializationError`. Models keep declaration order because `serde_json` now has `preserve_order`; a map is sorted by key. Matrix and label styles with explode are not expanded per property. Test: `test_object_valued_parameters_expand_per_style` (in-crate) and `test_pet_api_get_pet_tag_object_valued_params_expand_per_style`.
- **Map properties are written in key order.** Optional, required and nullable map properties use `object_serializer::sorted_map`.
- **SKILLS.md documents the whole error tree.** Test: `test_skills_error_section_documents_the_full_error_tree`.
- **Still skipped in batch 7:** per-scheme credentials (an owner decision on the Client shape), a response size limit, the caller-supplied `reqwest::Client` redirect guard (a client's redirect policy cannot be read back), cancellation, and the shared-Java findings.

### Decisions taken in batch 9

- **F855, F858, F1080 already conform.** The 401 renew-and-replay-once (`invalidate_access_token`), PKCE S256 on the authorization-code flow and the capped, masked error text landed in batch 7 (see above) and are unchanged.
- **Automatic retries.** `TransportOptionsBuilder::max_retries(u32)` (default 2, `0` disables; getter `TransportOptions::max_retries`). `BaseApi::invoke_api` owns the loop, because a retry must ask the authenticator for fresh headers: it retries a `NetworkError` that is not a body-read failure (`reqwest::Error::is_body` or `is_decode` on the source chain) and the statuses 408, 409, 429 and 500-599 except 501; a `NetworkTimeoutError` is never retried. The wait is 0.5 s doubling to 8 s with up to 25% jitter, or `Retry-After` (seconds or an HTTP date, capped at 60 s). All attempts share one end-to-end deadline (`RequestOptions::deadline`, new public field and builder, which `DefaultApiClient` uses instead of starting its own clock), and a retry whose wait does not fit in what is left is not made, so the last failure is returned. A multipart body is never retried, and the 401 replay does not use up a retry. `ApiClient` gains `max_retries()` and `request_timeout()` with defaults `0` and `None`, so a custom client never retries. A request sent straight through `DefaultApiClient` is not retried (the loop is in `BaseApi`). tokio gets the `time` feature for the sleep; dropping the future cancels the wait. Where: `base_api.mustache`, `api_client.mustache`, `transport_options.mustache`, `default_api_client.mustache`, `cargo_toml.mustache`, `readme.mustache`, `skills.mustache`. Test: the `test_a_retryable_status_is_retried_*` family in `test/base_api_test.mustache` (count, `Retry-After`, no retry on 400/404/422/501, budget, `max_retries(0)`, fresh auth headers, multipart, network vs timeout), the policy unit tests in `base_api.mustache`, `test_pet_api_retries_a_503_with_the_default_transport`, and the option tests in `test/transport_options_test.mustache`. Tests that expect one failing request through the default transport use `max_retries(0)`.
- **Boxed error helpers.** Operations keep returning `Box<dyn std::error::Error + Send + Sync>`. The new extension trait `<Prefix>ErrorExt`, implemented for that trait object, gives `as_api_error()`, `is_api_error()` and `api_error_kind()`, resolving the root `ApiError`, `ClientError`, `ServerError`, `NetworkError`, `NetworkTimeoutError` and every status wrapper to their `ApiError`. Additive: no signature changes. Where: `errors/root_error.mustache`, `errors/mod.mustache`. Test: `test_boxed_error_helpers_*` and `test_boxed_error_kind_can_be_matched` in `test/api_error_test.mustache`.

### Decisions taken in batch 10

The audit's own proposal was used as the default where no owner decision existed.

- **Already conforming.** F215 (the `required_nullable` deserializer and its in-crate tests, `object_serializer.mustache`), F825 (the boxed-error helpers of batch 9; the operation signatures stay `Box<dyn Error>`), F831 (`TooManyRequestsError` and `ApiErrorKind::TooManyRequests`; no `retry_after` accessor, java has none), F1121 (`base_api.mustache` in-crate test, an explicit `Accept` parameter beats the configured and negotiated one), F1148 (`test_request_json_writes_line_separators_and_del_literally`, `object_serializer.mustache`), F173 (`RUST_PRELUDE_TYPES` in `BetterRustCodegen`, now with `BetterRustCodegenTest.aSchemaNamedLikeAPreludeItemGetsTheModelPrefix`; `String` and `Default` are not in the set, because the `dataType` rewrite of a prelude name would also rewrite the key type of a `HashMap<String, _>`).
- **Token response.** A 2xx token body that is not JSON is `OAuth2TokenError` "Token response is not valid JSON" (no parser detail), one that is not an object is "Token response is not a JSON object", and a missing or empty `access_token` is "Token response missing or empty access_token field". A `token_type` that is not `bearer` in any case (absent and `null` are fine) is "Unsupported token_type in token response", as in java. Test: `test_unusable_token_response_bodies_use_the_canonical_messages` and `test_a_token_type_other_than_bearer_is_refused` in `test/oauth2_token_manager_test.mustache`.
- **Default headers.** `RequestOptions::skip_default_headers` (field and builder) is removed again: a `no_redirect` request, which is the token POST, leaves `TransportOptions::default_headers` out, as in kotlin, php and swift. OIDC discovery now carries the default headers, because it follows redirects (this supersedes the  bullet of batch 7 for discovery). Test: `test_no_redirect_request_leaves_the_transport_default_headers_out` in `test/default_api_client_unit_test.mustache`.
- **Server URLs expand in one pass.** `ServerConfiguration::url` and the per-operation server structs call `server_configuration::expand_placeholders`, so a value that carries `{name}` is never expanded again; the old sequential `replace` made the result depend on `HashMap` order. Test: `test_url_never_re_expands_a_placeholder_a_value_introduces` and the in-crate tests of `server_configuration.mustache`.
- **Lists in messages.** The allowed server-variable values and the declared request content types print as `[a, b, c]`, not as the `{:?}` of a `Vec`. Test: `test_url_rejection_message_names_the_value_the_variable_and_the_allowed_values`, `test_pet_api_set_pet_avatar_rejects_unknown_content_type`.
- **Deterministic bytes.** Authenticator cookies are written sorted by name (`base_api.mustache`); a map of byte arrays is written in key order (`base64_serde::map`); a deepObject parameter lists a model's properties in declaration order and a map's keys sorted (`object_serializer::deep_object_pairs`, which `api.mustache` calls; `to_deep_object_map` and `serialize_deep_object` stay as they were but are no longer used by generated code). Test: `test_base_api_writes_auth_cookies_sorted_by_name`, the `base64_serde` in-crate tests, `test_typed_map_properties_are_written_in_ascending_key_order` and `test_deep_object_pairs_keep_model_declaration_order_and_sort_map_keys`.
- **format: password in Debug.** A model with a `format: password` property drops the `Debug` derive and gets a hand-written `Debug` that prints the property as `***` (`hasPasswordVars`, set in `postProcessModels`). The wire form is unchanged. Test: `test_password_property_is_masked_in_debug_output` in `test/object_serializer_test.mustache`.
- **Injected names.** A parameter named like an injected argument or a local of the operation body (`options`, `server`, `request_content_type`, `path`, `headers`, `params`, `result`, `items` and the other locals of `api.mustache`) is renamed with the suffix `_param` through `getInjectedArgumentNames`. Test: `BetterRustCodegenTest.renamesAParameterNamedLikeAnInjectedArgumentOrALocal`.
- **make lint.** `make lint` runs `cargo fmt -- --check` before clippy.
- **Docs layout.** The README opens with Requirements (tooling table), Installation and Usage, then Raw responses, Timeouts, Automatic retries, Expired tokens, Request id, Cancellation and Custom HTTP client; Custom HTTP client and Cancellation are no longer under "Not supported". SKILLS gains Expired tokens, Custom HTTP client and "Raw responses, timeouts and the request id" beside Retries and Cancellation.
- **Tests added for behaviour that already held.** Unicode-space-only and look-alike bearer tokens, `Content-Encoding: identity` passthrough, an unparseable `Location` is returned as the response, dropping a call's future cancels it, the default no-op `close`, an IPv6 proxy host, a non-BMP multipart filename, byte-array equality, `TreatmentRecord` declaration order, the third global server, unenforced `slug`, `nickname` and `quantity` constraints, the Mirror operation server, a successful summary call, the mock's `/test/response-headers` and typed status routes, the malformed-body parse message.

## Decisions taken in wave 2

Owner decisions of 2026-11-17 realised in rust, with the triage of every item on the wave-2 work list.

### Implemented
- **F830 (request context on errors).** `ApiError` carries `request_method()` and `request_path()` (also `ApiErrorFields`; the status wrappers reach them through `api_error()`, `NetworkError` and `NetworkTimeoutError` get them through `attach_request`). The path is the operation path without scheme, host, query or fragment (`request_path_of`, cut at the first `?` or `#`). `ApiError::from_response(status, headers, body, request_method, request_path)` renders `API returned status code 404 for GET /items/7` when both are given, and the old text otherwise; `with_request` records them on an error built elsewhere. `BaseApi::invoke_api` wraps the real work and attaches the request to every error that has none. Test: `test_from_response_carries_the_request_method_and_path_without_the_query` and two more in `test/api_error_test.mustache`; `test_an_error_response_names_the_request_without_its_query_string` and `test_a_network_failure_carries_the_method_and_path_of_the_request` in `base_api.mustache`.
- **F827 (per-call options).** Every `...Options` struct gains `timeout: Option<Duration>`, `max_retries: Option<u32>` and `headers: HashMap<String, String>` with the chained `timeout`, `max_retries` and `header` setters, and `call_options()` hands them to `InvokeApiParams::call` (`CallOptions`). The timeout replaces the client's end-to-end budget (zero is `ConfigurationError::InvalidTimeout(0)`, nothing is sent), `Some(0)` retries turns retries off and a larger budget works on a client that never retries, and the headers are merged after the default headers and the operation's header parameters and before authentication. `BetterRustCodegen.mintsOptionsForEveryOperation()` returns `true`, so the shared pass mints an Options struct, the `options` signature entry and the `api/options/mod.rs` export for an operation with no query, header, form or cookie parameter and no security too (`vendorExtensions.op.optionsMinted`); that struct has no `auth` field. Every operation therefore ends with `options: Option<&...Options>` (before `request_content_type`). `RequestOptions` (the transport-level struct) is unchanged: the deadline it already carries is how the per-call timeout reaches the transport. `timeout`, `max_retries`, `header` and `call_options` are injected names (`getInjectedArgumentNames`). Test: the `base_api.mustache` in-crate tests, `BetterRustCodegenTest.everyOperationTakesAnOptionsArgument`.
- **F868 (immutable models with a copy).** Fields stay `pub` (struct-update syntax is how every test and caller builds a model, and a value bound without `mut` cannot change); every property gets a consuming `with_<property>(value)` (an optional property takes the inner type and stores `Some`; `r#type` gives `with_type`), and `with_additional_properties`. A copy is `model.clone().with_x(v)`. Test: the `with_` methods are exercised by `test/composed_schema_test.mustache` through the models.
- **F1062 (several 2xx responses).** The operation calls `BaseApi::invoke_api_for_status_union` with a closure that matches the actual status (`200`, `201`, a `2XX` range as `200..=299`) and decodes the body as that variant; an undeclared 2xx falls back to the union's own untagged decoding. Test: `test_store_api_reserve_stock_decodes_the_variant_of_the_actual_status` (a body that satisfies both schemas, so only the status decides).
- **F862 (credentials).** `with_token(host: Option<&str>, access_token: Option<&str>, transport)`: the argument wins, then `{PREFIX}_BASE_URL` and `{PREFIX}_ACCESS_TOKEN` read once at construction (empty means unset; the prefix is the upper-cased snake-case package name, `envPrefix`), and a missing value is `ConfigurationError::InvalidArgument` naming the variable. `with_token_provider(host, provider, transport)` takes a `TokenProvider` (`Arc<dyn Fn() -> String + Send + Sync>`) that `BearerAuthenticator` calls for every request; a token it returns that is empty or not header-safe fails that request through `try_auth_headers`. Only the Bearer path reads the environment; the other authenticators take their credentials as constructor arguments, so a missing one is already a compile error or an `InvalidArgument` at construction. Test: `test/client_test.mustache` (the `with_environment` tests) and `test/bearer_authenticator_test.mustache`.
- **F894 (package metadata).** Options `repositoryUrl`, `homepageUrl`, `authors` (comma-separated), `documentationUrl` and `issueTrackerUrl` are registered in `BetterRustCodegen` and written to `Cargo.toml` (`authors`, `repository`, `homepage`, `documentation`; cargo has no issue-tracker key) and the README Crate list when given; the licence is the existing `licenseId` (SPDX). The shared option plumbing is not touched. Test: `BetterRustCodegenTest.packageMetadataOptionsAndTheEnvironmentPrefixAreExposedToTheTemplates`.
- **F1087 (runtime floor).** Rust keeps `rust-version = "1.85"` (the toolchain is 1.96; 1.85 is not newer than the oldest non-EOL release, and rust has no EOL policy of its own).
- **F860 (OIDC discovery and default headers).** The discovery GET is sent with `RequestOptions::new().no_redirect(true)`, which also leaves the transport's default headers out, so an API key or `Authorization` default header never reaches the identity provider; a 307 or 308 from the discovery URL is an error. This supersedes the F1181 note that discovery carries the default headers. Test: `test_discovery_is_sent_without_following_redirects` in `test/openid_connect_authenticator_test.mustache`.
- **F129 (absent required discriminator).** A subtype's discriminator field gets `#[serde(default = "default_<field>")]` and a function returning the mapping value, so `{"weightKg":2.5}` decodes as a `DryFood` with `food_type == "dry"`. Test: `test_pet_food_subtype_without_its_discriminator_takes_the_mapping_value`.
- **F308 (integer enum casing).** Integer members are `Number1`, `NumberMinus1`, `Number1Dot5` (`toEnumVarName`), and the `non_camel_case_types = "allow"` lint exception is removed from `Cargo.toml`. Test: `BetterRustCodegenTest.anIntegerEnumMemberIsCamelCased` and the `Priority::Number<n>` rows.
- **F933 (serializer messages).** `test_serialize_failure_messages_start_with_the_canonical_prefix` and `test_deserialize_failure_messages_start_with_the_canonical_prefix` in `object_serializer.mustache` pin both prefixes.
- **F1154 (S8 test).** `BetterRustCodegenTest.theServerComesBeforeTheOptionsInEverySignature` reads the golden.
- **F889, F828, F834, F835, F836, F837 (README notes).** The README sections "Credentials", "Per-call options", "Errors", "Operations with several success responses", "Models", "Behaviours to know" (optional versus nullable, unknown enum and discriminator values fail loudly, joined repeated headers, no streaming, `Configuration` versus `TransportOptions`), "Compression", "Logging and tracing" and "Versioning and supported Rust", with short matching entries in SKILLS.

### Already satisfied
- F1121 (`test_header_param_accept_wins_over_the_default_and_the_negotiated_accept`), F1136 (`test_build_filename_directive_non_bmp_character_is_one_underscore_in_the_fallback`, `default_api_client.mustache`), F1148 (`test_request_json_writes_line_separators_and_del_literally`), F1124 (the `slug` and `nickname` rows in `test/composed_schema_test.mustache`) and F1147 (the Mirror rows in `test/api/pet_api_test.mustache`) were already in the templates; the triage evidence looked in the wrong files.
- F878 and F879 (go) do not apply; rust's oneOf is an enum, so each variant constructor (`Union::Variant(v)`) is typed and `match` is the accessor.

### Forced or not done in rust
- **F870.** `Eq` and `Hash` need a per-model check of every field type (`f64`, `HashMap`, `JsonValue` are neither) and add trait bounds to the public models; the models are `Clone` and `PartialEq`, and `with_` covers the copy half of F868.
- **F1140.** reqwest owns the intermediate response of a followed redirect and a request body is a `Vec<u8>`, so there is nothing to observe closing.
- **F1087 (PHP, Python, Ruby floors)** and **F877 (go)** do not apply to rust.


## Gate lessons (what broke the gate and how to avoid it)

- Generated code is verified only by the Docker specs, so a template edit is unproven until `etc/ci-local.sh check Rust` is green (RustFormattingSpec, RustLintingSpec, RustBuildSpec, RustClientSpec). `RustClientSpec` runs locally; run it, alone, before every push.
- No `//` line comments in generated code (RustFormattingSpec bans them; `///` doc comments are fine): use `/* */` for implementation comments. Catch before the first gate: `grep -rn '^\s*//[^/]' src/main/resources/templates/rust`. A Mustache `{{! }}` comment never contains `{{...}}` text.
- `cargo clippy -- -D warnings` is the gate (RustLintingSpec, ~110 s, toolchain pinned to rust:1.96): `redundant_field_names`, `derivable_impls`, `needless_lifetimes`, unused imports and dead code in tests, glob imports. Fix the template; add an `allow` only for lints that are structural (`upper_case_acronyms`). Import conditionally, because an unused import also fails.
- serde_json is built with `preserve_order` and `unbounded_depth` in `cargo_toml.mustache`; keep both, and keep serde_json out of public model signatures (wrap it in `JsonValue`). Optional `#[serde(with = ...)]` fields need `#[serde(default)]`.
- `sha2` (pure Rust) is the only hash dependency (PKCE in `oauth2_token_manager.mustache`); avoid crates that need a native binary (`ring`, `zstd-sys` link failures on macOS). tokio features stay `sync,macros,io-util,rt,time` so the crate builds for wasm.
- Recursive models must be boxed (`Option<Box<TreeNode>>`, E0072) and enum defaults emit `EnumName::Variant`, not `String::from(...)` (E0308). Catch: RustBuildSpec with the TreeNode and Employee/Department fixtures.
- When a constructor gains arguments or is renamed, update `Self::new(...)` inside the type, multi-line calls and struct-update idioms in every test template; search the bare name. Catch: `cargo test --no-run` in the golden.
- Test servers answering one request per connection send `Connection: close`; tests never reach external hosts (grep `https\?://` for non-local names); pin squid and chasm images by tag in every `GenericImage::new` (`grep -rn 'ubuntu/squid' src/main/resources/templates/rust`). Never `#[ignore]` a flaky test: run it 20 times.
- Builder features do nothing alone: call `.gzip(true)`, `.brotli(true)` on the `ClientBuilder`. Assert error text with `contains` (`Display` adds a prefix). Build file content from a Mustache template, never a Java StringBuilder.
- Environment noise is not a regression: `signal: 9` (OOM, run one spec alone, raise Docker memory), `rustc -vV` SIGSEGV, `liconv` or `ring` link errors on macOS, `can't find crate for petstore` after a killed run, a corrupted `CARGO_TARGET_DIR`. Compare against a HEAD baseline, then confirm on CI. A hand-patched copy of the golden under `src/spec/resources/generated/rust` can be built locally with `cargo build` or `cargo clippy -- -D warnings` to test a template idea before the 5-minute Docker loop; never commit that copy.

### Typed errors per declared schema (issue 123)
Rule: one struct per distinct declared error schema, `<Model>Error` in `src/errors/<snake>.rs` (`errors/schema_error.mustache`), wrapping the status error of its parent class (`ApiError`, `ClientError`, `ServerError` or a named status wrapper) and holding the decoded model (`status_error()`, `error_body()`). Each operation passes its own `("404" | "4XX" | "default", Type::from_error_response)` table in `InvokeApiParams::error_schemas`; `ApiError::from_response_with` picks exact status, then range, then default. A body that does not decode, or is empty, gives the plain status error (the decoding failure is its `source()`). `ApiErrorInfo` (status, headers, body, request, `is_retryable`, `request_id`) is implemented by every HTTP error type. Operations keep returning `Box<dyn Error + Send + Sync>`: a per-operation error enum would change the return type of every operation (a breaking change), so it is not done; callers downcast, or use `as_api_error()` / `api_error_kind()`. A decode failure of a response is a `SerializationError` that carries `status_code()`, `response_headers()`, `response_body()`, `request_method()` and `request_path()` (no query). A schema named like a prelude type keeps the `Model` prefix in return types and error bodies (`renamePreludeModels`).
Where: `errors/api_error.mustache`, `errors/schema_error.mustache`, `errors/root_error.mustache`, `errors/serialization_error.mustache`, `base_api.mustache`, `api/api.mustache`. Test: `test_bhv_errors_201` to `204` in `test/api_error_test.mustache`, `test_bhv_errors_205` in `base_api.mustache`.
