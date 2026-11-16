# Rust SDK generator

`BetterRustCodegen` (generator name `rust-plus`) generates an async Rust crate: one module per API tag, one struct or enum per model, an `ApiClient` trait with a `DefaultApiClient`, authenticators, typed errors, and an in-crate and `tests/` suite. The HTTP stack is `reqwest` 0.12 with `rustls-tls-native-roots` (http2, multipart, charset) on `tokio`; JSON is `serde` and `serde_json`; dates are `chrono`; response bodies are decoded by hand with `flate2`, `brotli` and `zstd`; the crate has an optional `opentelemetry` feature. Generated tests run with `cargo nextest` (JUnit XML through the `ci` profile), with `testcontainers` for the Squid and Chasm fixtures. The floor is edition 2021 with `rust-version = "1.85"` (also `clippy.toml` `msrv`), the Docker toolchain is `rust:1.96`, and formatting is stock `cargo fmt`.

## Read first

- Shared cross-language contract and decisions: `../AGENTS.md` (`src/main/java/io/github/mridang/codegen/generators/AGENTS.md`)
- Repository root: `/AGENTS.md` (workflow, commands, guardrails)
- Codegen class: `BetterRustCodegen.java` (this folder)
- Templates: `src/main/resources/templates/rust/`
- Specs: `src/spec/java/io/github/mridang/codegen/spec/rust/` (`RustSpec`, `RustBuildSpec`, `RustClientSpec`, `RustFormattingSpec`, `RustLintingSpec`, `RustReservedWordsSpec`)
- Golden: `src/spec/resources/generated/rust/`
- Parity ledger: `PARITY_DECISIONS.md` / `.json` (the D ids below), `PARITY_FINDINGS.json` (the F ids), `etc/api-surface-allow.json` (the rust API-surface allowances)

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
| Numbers | `format: number` is `f64`, documented lossy (D83); integer enums keep `NUMBER_<n>` (see Known limitations) | native binary float |
| Cancellation | no SDK API: drop the future; `timeout` bounds a call | native async cancellation; see S7 below |
| Unique sets | `HashSet`, written sorted by a serde adapter (D69) | hash order is random |
| Optional description | `ServerConfiguration`/`ServerVariable` description is an empty `String` when absent | empty-string sentinel shared with go, swift and dart; metadata only, never on the wire |

## Settled decisions (do not re-open in review)

Each cross-language decision is stated once in the shared file; here only how rust realises it. Where the Rule line says "intended" the rule is the ledger's canonical behaviour for rust and the templates were not confirmed to implement it when this file was written; check the template before relying on it. Decisions on which rust needs no rust-specific rule are listed at the end of this section.

### Strict containers (S1, F006, 2026-11-16)
Rule (intended): a container-of-primitives response (`HashMap<String, i32>`, `Vec<Vec<i32>>`) is checked element by element like a single primitive, so a fractional, quoted or out-of-range leaf is a `SerializationError`.
Why: the container path bypassed the primitive check in seven languages; all twelve now behave alike.
Where: `object_serializer.mustache`, `base_api.mustache`.
Test: the `ObjectSerializer` tests in `test/object_serializer_test.mustache` and the `getInventory`/`getMatrix` rows in the pet/store API tests.

### Content parameters use the SDK serializer (S2, D50, D80, F054, F068)
Rule: a `content: application/json` parameter in query, header, path or cookie is encoded by `object_serializer::serialize(..)?`, mapped to the operation's boxed `SerializationError`, in both the required and the optional arm.
Why: `serde_json::to_string(..).unwrap_or_default()` swallowed failures and produced bytes different from the other eleven SDKs.
Where: `api/api.mustache`, `object_serializer.mustache`.
Test: `searchStore.q` rows in `test/api/store_api_test.mustache`.

### Schema constraints are not enforced (S3, D55, F056)
Rule: the client does not enforce `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems`, `maxItems`; rust has no native enforcement, so the server owns validation, and the README section says so.
Why: only python gets it natively and cheaply through pydantic; hand-written enforcement would differ per language.
Where: `readme.mustache` (the constraint notes; the section heading required by S3 is still named "Numeric / string constraint validation" and should be "Schema constraints", intended).
Test: none: asserting absence is not worth a test; the README text is the contract.

### allOf with a discriminator (S4, F057, F802)
Rule (intended, not implemented): rust uses an internally tagged enum for the parent, subtype structs list the common fields and omit the discriminator field, and serde `flatten` is avoided; a model reused in two unions is never re-parented.
Why: structs cannot subclass; flattening plus dispatch keeps the wire identical. Today only oneOf with a discriminator is realised (an enum with hand-written `Deserialize` that dispatches on the discriminator and rejects unknown values); `BetterRustCodegen.removesDiscriminatorPropertyFromChildren` and `setsDiscriminatorDefaultOnChildren` carry the discriminator rules.
Where: `models/model.mustache`, `BetterRustCodegen.java`.
Test: `test/composed_schema_test.mustache` (oneOf cases only); F057 is still open in `PARITY_FINDINGS.json`.

### Required and nullable (S5, D66, F215)
Rule (intended): a required nullable property must be present on decode (absent is an error), an explicit `null` is kept, and encode always writes the key (`null` when unset). A required non-nullable field hard-fails on `null` (landed, tested).
Why: the canonical behaviour is shared by all twelve; rust needs a presence-distinguishing deserializer (`Option<Option<T>>` helper) that `model.mustache` does not yet emit (`Option<T>` with `skip_serializing_if` is still used for optional fields).
Where: `models/model.mustache`, `object_serializer.mustache`.
Test: `object_serializer.mustache` in-crate hard-fail tests; `test/composed_schema_test.mustache` nullable `label` case.

### uniqueItems order unspecified (S6, F319, D69)
Rule: sets are sent in the language's natural order; for rust the D69 rule applies and scalar sets are written sorted (numbers numerically, strings by code point, booleans false first) through `object_serializer::sorted_set::serialize` (and `::option`). Byte-array items are excluded. Do not add sorting elsewhere.
Why: a `HashSet` iterates in random order, so unsorted bytes would differ per run.
Where: `models/model.mustache`, `object_serializer.mustache`.
Test: `sorted_set` tests in `object_serializer.mustache`; the model golden.

### Cancellation is dropping the future (S7, F228)
Rule: rust adds no cancellation parameter and no client signal: a caller cancels by dropping the future or wrapping it in `tokio::time::timeout`/`select!`, and `TransportOptions.timeout` bounds a call. Cancellation is never reported as `NetworkTimeoutError`.
Why: this is the native mechanism, and a wrapper handle would be less natural than the one every async Rust caller already uses. The README section "Per-call cancellation" says so.
Where: `readme.mustache`, `default_api_client.mustache`.
Test: `test_cancelled_code_exchange_keeps_the_delegate` in `test/openid_connect_authenticator_test.mustache`.

### Parameter order (S8, F803, owner confirmation pending)
Rule (intended): required parameters, then server, then the optional-parameters struct; rust has no per-call cancellation handle. Adding security never moves an argument.
Why: callers must not break when a spec gains security on an operation. F803 is still open in the ledger.
Where: `api/api.mustache`, `api/options.mustache`.
Test: server-override rows in `test/api/pet_api_test.mustache`.

### Injected transport (S9, F405)
Rule (intended, not yet implemented): the client builder will accept a `reqwest::Client`, keep the SDK defaults, never close a caller-supplied client, and fail fast on conflicts with the SDK's own proxy, TLS or pool settings. Today `DefaultApiClient` builds its own `reqwest::Client` from `TransportOptions` in `build_http_client`.
Why: OpenAI, Stainless, Speakeasy, Fern, Azure and Stripe SDKs all accept the standard client. F405 is open.
Where: `default_api_client.mustache`, `transport_options.mustache`, `client.mustache`.
Test: none yet.

### Dependencies (S10)
Rule: a small pure crate or a version bump is acceptable when it standardises behaviour; never a crate that needs a native binary. `brotli`, `flate2`, `zstd` (static), `encoding_rs`, `urlencoding`, `regex`, `base64` are accepted; reqwest uses `rustls-tls-native-roots`, not OpenSSL.
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
Rule: one end-to-end timeout budget (a `std::time::Instant` deadline; `remaining_budget` per hop, a zero remainder is `NetworkTimeoutError`); a caller-supplied stream or file handle is never closed; proxy URL validation happens in `build()`, not in the builder setter; `caCertPath` is validated even when `verify_ssl` is false and pins the trust store. The budget is landed. Intended: `timeout` must be a positive number of milliseconds or `None` (message "must be positive"), and proxy/CA errors store the full canonical message and display it verbatim with credentials redacted.
Why: the budget used to apply per hop; redirects could multiply it.
Where: `default_api_client.mustache`, `transport_options.mustache`, `errors/configuration_error.mustache`.
Test: `test/default_api_client_test.mustache`, `test/default_api_client_unit_test.mustache`, `test/transport_options_test.mustache`.

### OIDC rediscovery keeps the session (D01, F016, F230)
Rule: `OAuth2TokenManager` owns tokens and the exchanged flag (an `AtomicBool` with `mark_exchanged`/`is_exchanged`); the auth-code authenticator holds an `Arc<OAuth2TokenManager>` and gains `pub(crate) with_token_manager`; on rediscovery the OIDC authenticator builds the replacement around the previous manager, and discovery is serialised (single-flight).
Why: replacing the delegate reset the session, so every call after the discovery max-age failed with "Must call exchange_code()". 11 of 12 SDKs had the bug; elixir was already correct.
Where: `auth/oauth/openid_connect_authenticator.mustache`, `oauth2_auth_code_authenticator.mustache`, `oauth2_token_manager.mustache`.
Test: `test/openid_connect_authenticator_test.mustache`.

### Refresh grant carries client authentication (D02, F017)
Rule: `get_access_token_with_refresh_url(token_url, Option refresh_url, params, headers)` posts the refresh to `refresh_url` with `client_id`/`client_secret` from the params and the same extra headers; `get_access_token_with_headers` delegates to it.
Why: RFC 6749 sec 6 and 3.2.1; rust has no default arguments, hence the separate method (an API-surface allowance).
Where: `auth/oauth/oauth2_token_manager.mustache`, `oauth2_password_authenticator.mustache`, `oauth2_auth_code_authenticator.mustache`.
Test: `test_refresh_grant_goes_to_the_refresh_url` in `test/oauth2_auth_code_authenticator_test.mustache`.

### exchange_code replaces the session (D03, F119)
Rule: `OAuth2TokenManager::exchange` locks, saves and clears the session, posts the params verbatim, keeps only what the response carried, marks the session exchanged, and restores the old session on failure; `exchange_code` calls only it.
Why: the exchange used to go through the token cache and refresh path.
Where: `oauth2_token_manager.mustache`, `oauth2_auth_code_authenticator.mustache`.
Test: `test/oauth2_token_manager_test.mustache`.

### OAuth wording, grammar and request order (D05, D07, D08, D09, D10)
Rule: OIDC always requests `openid`; token and discovery errors use the shared wording ("Token response is not valid JSON", "... is not a JSON object", "Token response missing or empty access_token field", no parser detail); `expires_in` and `max-age` use one digits-plus-fraction grammar with a one-year ceiling and a saturating `max-age` parse; precondition texts are "Authorization code must not be empty" and "Must call exchange_code() before making API requests". The token body and authorize URL are emitted in the fixed order grant_type, code, refresh_token, username, password, client_id, client_secret, redirect_uri, scope, then the rest sorted (`ordered_params`), which is landed; the wording and grammar items are intended.
Why: `HashMap` order and per-language wording made bytes and messages differ.
Where: `auth/oauth/*.mustache`, `errors/oauth2_token_error.mustache`, `errors/oauth2_authorization_code_error.mustache`.
Test: the `test/oauth2_*_test.mustache` and `test/openid_connect_authenticator_test.mustache` files.

### Implicit flow, empty state, sync auth accessor (D06, D11, D12, F290)
Rule: an empty implicit access token counts as unset; an empty `state` is absent in authorize URLs; the infallible `auth_headers()` returns an empty map on failure and is documented as forced, while `try_auth_headers()` is the loud path and is the one the transport calls.
Why: trait objects with an infallible accessor cannot throw; F290 stays open for the other SDKs.
Where: `auth/oauth/oauth2_implicit_authenticator.mustache`, `base_api.mustache`, `authenticator.mustache`.
Test: `test/oauth2_implicit_authenticator_test.mustache`.

### Unusable responses carry the real headers (D13, F099)
Rule (intended): the three redirect-refusal `ApiError::new` calls pass `Some(headers)`, and the decompress failure keeps `Some(resp_headers)`.
Why: a response that arrived is not a "no response" error.
Where: `default_api_client.mustache`.
Test: `test/default_api_client_test.mustache`.

### Error text matrix (D14, D15, D16, D17, D20, D28, D29, D79)
Rule (intended): exact-string tests for `OAuth2ServerError`; serializer messages start with "Failed to ..."; the redirect refusals read "non-HTTP(S)", "<cur> -> <next>" and "Too many redirects (exceeded {})"; the "Response headers:" line is compact JSON with sorted keys (a `BTreeMap`); list values render `[a, b, c]` not `{:?}`; `ConfigurationError` `Display` prints the reason verbatim; the missing-parameter message names the caller-facing `{{paramName}}` identifier; use-after-close is `ConfigurationError::InvalidState` with the canonical text and an accurate struct doc.
Why: one message grammar across all twelve; `HashMap` order is random.
Where: `errors/*.mustache`, `object_serializer.mustache`, `default_api_client.mustache`, `server_configuration.mustache`, `api/api.mustache`.
Test: `test/api_error_test.mustache` (replace the `is_empty` assertion with exact-string rows), `test/default_api_client_test.mustache`, `test/server_configuration_test.mustache`.

### Header injection checks are case-insensitive (D21, F299)
Rule: `has_header()` compares names with `eq_ignore_ascii_case`; if the caller supplied any case variant of `User-Agent` or `X-Request-ID`, even empty, nothing is injected and the caller's key and value stay as written.
Why: RFC 9110 sec 5.1.
Where: `default_api_client.mustache`.
Test: `test/default_api_client_test.mustache` (echo route).

### Response body decoding (D30, D31, D32, D33, F033, F085, F149, F150, F151)
Rule (intended, partly): a binary return is raw bytes and never JSON-parsed; a `format: byte` return is JSON by default with one strict base64 decoder and every failure a `SerializationError`; a non-JSON success type on a non-binary return gives a `String` its text and anything else a `SerializationError` naming the content type; `.unwrap_or_default()` and `.ok()` are replaced by mapped errors; a `String` return never base64-decodes. `base_api.mustache` still derives binary from `params.return_type == "Vec<u8>"` (`is_binary_return`); the decision asks for an explicit `binary_return` flag.
Why: five different outcomes across the twelve SDKs. F149 is open in the ledger.
Where: `base_api.mustache`, `api/api.mustache`.
Test: `test/base_api_test.mustache`.

### Form values and multipart order (D39, D53, F216, F087)
Rule: `form_scalar_to_string` returns a `Result` and rejects an object or nested array with `SerializationError` "form-urlencoded field <name> must be a scalar or array of scalars"; null array elements are skipped. Multipart parts are an ordered `Vec<(String, MultipartValue)>`, not a `HashMap`.
Why: `other.to_string()` emitted rust-specific repr; `HashMap` made part order nondeterministic.
Where: `base_api.mustache`, `api_client.mustache`, `default_api_client.mustache`, `api/api.mustache`.
Test: `test/base_api_test.mustache`, pet API tests for `setPetPreferences`.

### String literals and doc prose (D40, D41)
Rule (intended): every spec string written into a code literal (defaults, server URLs and variables, OAuth URLs and scopes) goes through one value-preserving escaper (`toRustStringLiteral`; control characters as `\u{..}`), and descriptions use triple braces in `api.mustache` and `servers.mustache`; the manifest description goes through `lambda.jsonString`.
Why: stripping or double-escaping changed values.
Where: `BetterRustCodegen.toDefaultValue`, `formatRustScopes`, `servers.mustache`, `api/api.mustache`, `cargo_toml.mustache`.
Test: the golden and the escape rows in the shared specs.

### Per-operation server types (D42, F122, F145)
Rule: a server variable gets one type per operation; a name occurring in two or more variants of the operation becomes `<VariantName><Var>`, a variable used once keeps its plain name (`enrichOperationServers`, computed once).
Why: duplicate enum names failed to compile.
Where: `BetterRustCodegen.java`, `api/api.mustache`.
Test: `getMultiServerPetInfo` rows in `test/api/pet_api_test.mustache`.

### Identifier fallbacks and reserved words (D43, D44, D45, F172, F363)
Rule: a digit-leading or empty sanitised name gets a deterministic letter `n` before casing and never goes through `escapeReservedWord`; `r#` is used only for real keywords, and `crate`, `self`, `super`, `Self` use an underscore; model names colliding with the prelude set get the `Model` prefix (landed for `Result` as `ModelResult`).
Why: the escape prefix made digit names illegal.
Where: `BetterRustCodegen.escapeReservedWord`, `toModelName`, `RUST_PRELUDE_TYPES`.
Test: `RustReservedWordsSpec`.

### String enum literals (D46, F044, F047)
Rule: enum string values are escaped in order: backslash doubling; CR, LF, TAB; then the language's enum escape table; the quote character is escaped, never stripped. The value sits inside `#[serde(rename = ...)]`, so the same escapes apply.
Why: dropping a quote changed the wire value.
Where: `AbstractBetterCodegen.escapeEnumStringValue`, `BetterRustCodegen.formatEnumStringLiteral`.
Test: the enum golden and `EdgeCases` rows.

### Object-valued query and header parameters (D47, F063)
Rule (intended): a model or free-form map parameter becomes a wire-keyed map through `serde_json::to_value` (with `preserve_order` or a `Vec<(String,String)>`), then expands per the style/explode row in declaration order, with nested values a `SerializationError`.
Why: the same expansion in all twelve; F063 was parked in the ledger.
Where: `value_serializer.mustache`, `object_serializer.mustache`.
Test: `test/value_serializer_test.mustache`.

### Path parameter dot segments (D48, F064)
Rule: a path parameter of exactly `.` or `..` is rejected before dispatch with `ConfigurationError::InvalidArgument("Path parameter '<name>' must not be '.' or '..'")`; the guard is skipped for uuid parameters (which also removed an `is_empty`-on-`Uuid` compile error). An empty required path parameter is an `Err`, never a panic.
Why: dot segments are normalised away by URL parsing and change the route.
Where: `api/api.mustache`, `value_serializer.mustache`.
Test: `test/api/pet_api_test.mustache`.

### allowReserved (D49, D78, F066, F387)
Rule: only the RFC 3986 section 3.4 query set stays literal; `#`, `[` and `]` are percent-encoded. The `'` to `%27` rewrite by the `url` crate (WHATWG special-query set) is a documented FORCED exception, pinned by a transport test; the encoder output (`encode_query_allowing_reserved`) is the contract.
Why: reqwest/url cannot be told to leave `'` alone.
Where: `value_serializer.mustache`, `default_api_client.mustache`.
Test: the full-string unit row and the `getPetTag` row in `test/value_serializer_test.mustache` and `test/api/pet_api_test.mustache`.

### format: byte parameters (D51, D58, F069)
Rule: `Vec<u8>` parameters are base64 (STANDARD engine) in query, header, path, form and cookie, chosen by type: `stringify`, `to_query_value` and `to_header_value` check for `Vec<u8>` by downcasting through `Any`. A `format: byte` map value is a base64 string through `base64_serde::map` and `map_option`. Decode is strict RFC 4648 with padding.
Why: serde would otherwise emit a number array.
Where: `object_serializer.mustache`, `models/base64_serde.mustache`.
Test: `test/value_serializer_test.mustache`, `test/object_serializer_test.mustache`.

### Required parameters and read-only/write-only (D56, F025, F055)
Rule: `readOnly` and `writeOnly` have no wire effect in rust; `model.mustache` adds a "Read-only" or "Write-only" doc remark only. An empty required string parameter is rejected before dispatch in path, query, header, form and cookie, except `allowEmptyValue: true` query parameters (`queryStyledAllowEmpty`).
Why: rust was already wire-neutral.
Where: `models/model.mustache`, `api/api.mustache`.
Test: `Account.id`/`password` in the golden; `test/api/pet_api_test.mustache`.

### Defaults (D59, D60, D61, F129, F170, F320, F131, F171, F174)
Rule: floating-point defaults always carry a decimal point (`Some(2.0)`, landed); a required property with a default (including the synthetic discriminator) gets `#[serde(default = "default_<field>")]`, and a default on a `$ref` enum becomes the typed member (`Some(Availability::OnHold)`) consistent with the hand-written `Default` (both intended).
Why: `Some(2)` on an `f64` is E0308.
Where: `BetterRustCodegen.toDefaultValue`, `models/model.mustache`.
Test: the default-value assertions in the golden and `test/composed_schema_test.mustache`.

### format: uri and format: time (D62, D81, F132, F071)
Rule: `format: uri` sets `vendorExtensions.isUri` and decodes through `object_serializer::absolute_uri` (any scheme, relative references, whitespace and empty rejected). `format: time` is written by `object_serializer::format_time`: `HH:mm:ss`, plus `.SSS` (truncated) only when milliseconds are non-zero, in params and bodies.
Why: one wire rule in all twelve.
Where: `object_serializer.mustache`, `BetterRustCodegen.java`, `models/model.mustache`.
Test: `test_uri_property_accepts_any_absolute_uri_and_rejects_relative_references` and the `format_time` tests in `object_serializer.mustache`.

### additionalProperties collisions (D63, F138)
Rule: `additional_properties` uses `#[serde(flatten, serialize_with = "serialize_additional_properties")]`, a per-model function that lists the declared wire names and calls `object_serializer::serialize_extras`, which drops colliding keys; the declared value wins.
Why: flatten alone writes the key twice.
Where: `models/model.mustache`, `object_serializer.mustache`.
Test: `Metadata` rows in `test/metadata_test.mustache`.

### Models cannot be sent incomplete (D68, F227)
Rule (intended): no `Default` (derived or hand-written) for a model with required variables lacking defaults; `new(...)` is the constructor.
Why: java, node and rust let callers build a model missing required properties; F227 is deferred in the ledger.
Where: `models/model.mustache` (`hasDefaultedOptionalVars`).
Test: `test/composed_schema_test.mustache`, `test/metadata_test.mustache`.

### No ambient proxy (D74, F090)
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
Why: confirmed against the ledger's per-language rows as already canonical.
Where: see the named templates in Layout.
Test: the matching generated tests; the ledger rows hold the per-decision lock.

## Known limitations and out of scope

Out of scope in every SDK, and the README says so (S11, closed backlog issues #53, #70, #78, #88, #90): callbacks and webhooks, XML bodies and `xml.nodeType`, Link Objects and mutual TLS. OpenAPI 3.2 and 3.1 are tracked by epics #49 and #96, and loading 3.2 documents is issue #50 (the pinned swagger-parser rejects it).

SOCKS proxies are rejected at construction (only `http://` and `https://`); enabling them needs extra reqwest features in all twelve. The SDK ships no `LICENSE` file.

Open or deferred ledger items that touch rust: F346 (no data-returning variant of the request-content-type selector; needs a naming decision because `_with_content_type` returns `ApiResult` and cannot be renamed); F804 (dropping `reqwest::Client` does not close an idle keep-alive socket, so the canonical close text cannot be met until the project chooses between rewording it or `pool_max_idle_per_host(0)` for all twelve); F580 (`test_custom_ca_cert_replaces_the_system_trust_store` silently skips when `example.com:443` is unreachable, so CA coverage depends on the network, unverified); F308 (integer enum members stay `NUMBER_<n>` with `non_camel_case_types = "allow"`, parked); F390 (an overflowing `1e400` is rejected, the other seven accept it as Infinity, parked); F195 (the inline-comment ban is off for rust because `getInlineCommentPattern()` returns null, deferred; doc comments are `///` and block comments are used); F191 (rust is the only SDK whose docs build runs in a spec, deferred for the rest); F057 (allOf with a discriminator, open); F405 and F803 (see Settled); F290 (infallible `auth_headers()` returns an empty map, forced here).

The oneOf and anyOf wrapper no-match strings differ per language (rust: `<X>: anyOf payload matched no member schema`); this is documented and not harmonised (F396). A `format: number` is `f64` and lossy by design. The first-wins duplicate keys and 512-level decoder cap forced exceptions belong to swift, not rust.

## Build-breaking rules

- No `#![allow(...)]` or `#[allow(...)]` in generated source: exclusions go in `[lints]` in `cargo_toml.mustache` with a reason.
- `cargo clippy --all-targets --all-features -- -D warnings` and `RUSTDOCFLAGS="-D warnings" cargo doc --no-deps` must pass (`RustLintingSpec`), so an unused import or a missing doc fails.
- `cargo fmt -- --check` must pass with stock rustfmt; the generator formats with `cargo fmt` in the pinned `rust:1.96` image (`RustFormattingSpec`).
- Generated tests use block comments in templates; the `//` inline-comment check is off for rust (F195) but do not add new inline comments.
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
