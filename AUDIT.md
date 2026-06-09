# SDK Harmonisation Audit — Fresh Round (current templates)

Produced by the `PROMPT.md` 9-dimension review against the LIVE templates (the
prior AUDIT.md was stale: Phases 1–4 + Rounds 3–5 + the form/multipart/enum
convergence batch all landed since). Every finding below was verified against
the current `src/main/resources/templates/<lang>/`. WONTFIX items from `AGENT.md`
(suffix split, numeric precision, PKCE/OIDC-sig, additionalProperties data-loss,
date/datetime wire, etc.) excluded. UNIFORM-GAPs (const-not-validated,
deepobject-key-encoding, license-file-not-emitted, apikey-query/cookie-encoding)
are not parity defects — not fixed here.

## Central prerequisite (DONE)
- Added fixture op `getPetByName` (GET `/pet/byName/{name}`): a **simple-style
  string path param** `name` + a **required query param** `category`. Makes the
  dormant path-encoding (#5/#6/#7) and required-param-validation (#17) findings
  CI-catchable. Per-lang agents test via their local-capturing-server / fake
  ApiClient pattern.

## Canonical: every wire-format test captures the actual request bytes (the
established pattern from the convergence batch — local capturing server or fake
ApiClient), asserts the corrected behaviour, must be red before the fix.

---

## FIX LIST (by language)

### java
- **utf8-bom-strip** (HIGH): Jackson does not strip a leading UTF-8 BOM → first-token parse error. Strip the 3-byte BOM before parse (10 SDKs already strip). Remove the "Not supported: UTF-8 BOM" caveat from `readme`. Canonical = strip.
- **primitive-type-coercion-lenient** (HIGH): default mapper leaves `ALLOW_COERCION_OF_SCALARS` on → `{"id":"42"}`/`{"active":1}` silently coerced. Disable scalar coercion so wrong-typed primitives throw (10 SDKs throw). `java/object_serializer.mustache`.
- **java-kotlin-tilde-overencoded** (MED): `encodePathSegment` (URLEncoder) emits `~`→`%7E`; add `.replace("%7E","~")` to the restore chain (10 SDKs preserve `~`). `java/value_serializer.mustache`.
- **required-nested-param-validation** (MED): only path/body required params validated; add a client-side guard for required query/header/form/cookie params (node/python already do). `java/api/api.mustache`. Exercise via `getPetByName.category`.

### kotlin
- **primitive-type-coercion-lenient** (HIGH): ✅ DONE. `isLenient=false` hardened bare literals, but kotlinx still accepts a *quoted* scalar into a numeric field regardless of `isLenient`. Closed fully with strict per-field `KSerializer`s (StrictInt/Long/Double/Float/Short/Byte/Boolean) applied via `@Serializable(with=...)` in `models/model.mustache`, rejecting quoted-scalar→numeric and string/number→boolean to match Java's `ALLOW_COERCION_OF_SCALARS=false`. Committed `d153f41d`.
- **java-kotlin-tilde-overencoded** (MED): same `~`→`%7E` fix in `kotlin/value_serializer.mustache`.
- **required-nested-param-validation** (MED): `kotlin/api/api.mustache`.

### csharp
- **utf8-bom-strip** (HIGH): System.Text.Json does not strip BOM. Strip before parse; remove readme caveat.
- **oidc-discovery-no-status-check** (MED): `GetDelegateAsync` parses the discovery body with no HTTP-status guard (11 SDKs guard `<200||>=300`). Add the status guard before `JsonDocument.Parse`; switch endpoint reads to `TryGetProperty` for the clean "missing endpoint" message. `csharp/auth/oauth/openid_connect_authenticator.mustache`.
- **apiresult-rawbody-nullability** (LOW): `RawBody` is `string?` though always populated → make non-null `string`. `csharp/api_result.mustache`.
- **editorconfig-universal-baseline** (MED): no `[*]` block (charset/eol/final-newline). Add it above the `[*.cs]` analyzer rules. `csharp/editorconfig.mustache`.
- **required-nested-param-validation** (MED): C# null-checks the whole options obj but not the specific required nested param. `csharp/api/api.mustache`.

### go
- **response-type-name** (MED): ✅ DONE — the transport wrapper is renamed to `ApiHttpResponse` in all 12 SDKs (was `ApiResponse` in 9, `HttpResponse` in go/swift, `HttpApiResponse` in dart). `ApiHttpResponse` is collision-free with the spec's `ApiResponse` *model*. Filename-sensitive langs (java/kotlin/php/csharp/swift) also got the matching `ApiHttpResponse.<ext>` supporting-file name via shared codegen; module-named langs kept their file. The `ApiResponse` model is untouched. Committed `d153f41d`, CI green.
- **skills-oauth-async-ordering** (LOW): reorder Go's OAuth2-lifecycle `####` subsections to Async-first (matches 11). `go/skills.mustache`.
- **user-agent-caveat** (LOW): drop the inaccurate "User-Agent not normalised" readme caveat (UA is in fact uniform). `go/readme.mustache`.
- **required-nested-param-validation** (MED): `go/api/api.mustache`.

### rust
- **rust-path-param-double-encoded** (HIGH): `api/api.mustache` re-encodes the already-encoded output of `serialize_styled(...,"path",...)` → `%2520`/`%252F`. Drop the post-serialize char re-encoder; substitute `v` directly. `rust/api/api.mustache`.
- **rust-no-empty-path-guard** (MED): `serialize_styled` lacks the empty-string path guard the other 11 raise; add it (and un-ignore the parity test). `rust/value_serializer.mustache`.
- **optional-enum-default-omitted** (MED): optional enum field with schema `default` inits to `None` → omitted on wire; map the raw default to the enum variant (9 SDKs apply it). `rust/models/model.mustache`.
- **crate-publish-empty-description** (HIGH): `cargo_toml` emits empty `description` when `info.description` absent → `cargo publish` fails. Add `{{^appDescription}}` fallback. `rust/cargo_toml.mustache`.
- **user-agent-caveat** (LOW): drop caveat. `rust/readme.mustache`.
- **required-nested-param-validation** (MED): `rust/api/api.mustache`.

### swift
- **response-type-name** (MED): ✅ DONE (see go) — wrapper renamed to `ApiHttpResponse` across all 12.
- **optional-enum-default-omitted** (MED): init optional enum field to the default variant (currently `= nil`). `swift/models/model.mustache`.
- **required-nested-param-validation** (MED): `swift/api/api.mustache`.
- (swift-linux-proxy-refusal: genuine swift-corelibs-foundation platform limit, already fail-fast — NOT fixed.)

### dart
- **response-type-name** (MED): ✅ DONE (see go) — wrapper renamed to `ApiHttpResponse` across all 12.
- **optional-enum-default-omitted** (MED): init optional enum field to the default variant (currently no default in ctor). `dart/models/model.mustache`.
- **required-nested-param-validation** (MED): `dart/api/api.mustache`.

### node
- **client-secret-leak-in-default-repr** (MED): OAuth2 authenticators expose `clientSecret`/`password` via `util.inspect`/`console.log`. Add `[util.inspect.custom]`/`toJSON` masking (or `#private` field). `node/auth/oauth/*`.
- **node-model-equality** (LOW): generated models have no value-equality; add an `equals(other)` method (11 SDKs expose value-eq). `node/models/model.mustache`.
- **node-transport-failure-headers-empty-not-null** (LOW): 4 transport throw sites pass `{}` for `responseHeaders`; pass `null` to match the documented contract + 11 SDKs. `node/default_api_client.mustache`, `node/api_error.mustache`.
- **user-agent-caveat** (LOW): drop caveat. `node/readme.mustache`.
- (node already validates required nested params + on side A for decompression — no #17/#1 fix.)

### python
- **decompression-error-not-wrapped** (MED): `_decompress_body` runs after the `except urllib3.HTTPError` block → raw `gzip.BadGzipFile` leaks. Move it inside the transport try/except so a decode failure surfaces as `ApiException`. `python/default_api_client.mustache`.
- **oneof-multiple-match** (MED): Python raises on a payload matching >1 oneOf variant; the other 11 first-match. Align Python to first-match for parity. `python/models/model.mustache`.
- **python-error-throw-headers-coerced-to-none** (LOW): `dict(response.headers) if response.headers else None` on the error path conflates empty-headers with no-response; pass `dict(response.headers)` unconditionally. `python/base_api.mustache`.
- **crate-publish-empty-description** (HIGH-class): `pyproject_toml` emits empty `description` with no fallback; add `{{^appDescription}}` fallback. `python/pyproject_toml.mustache`.
- (python on side A for required-param validation + secret-redaction — no #17/#11 fix.)

### php
- **decompression-error-not-wrapped** (MED): only `catch (TransportExceptionInterface)` wraps; `gzdecode` failure throws `\RuntimeException` uncaught. Broaden the catch around decompress. `php/default_api_client.mustache`.
- **php-authenticator-secret-leaks** (MED): Basic/Bearer/ApiKey leak via `var_dump`/`print_r` (no `__debugInfo`). Add `__debugInfo(): array` returning `'***'`. `php/auth/{basic,bearer,api_key}_authenticator.mustache`.
- **client-secret-leak-in-default-repr** (MED): same for OAuth2 authenticators (`clientSecret`/`password`). Add `__debugInfo`. `php/auth/oauth/*`.
- **apiresult-rawbody-nullability** (LOW): `?string $rawBody`→`string`. `php/api_result.mustache`.
- **crate-publish-empty-description** (HIGH-class): `composer` description fallback. `php/composer.mustache`.
- **required-nested-param-validation** (MED): `php/api/api.mustache`.

### ruby
- **decompression-error-not-wrapped** (MED): `decompress_body` after the `rescue` block → raw `Zlib::GzipFile::Error` leaks. Move inside the rescue. `ruby/default_api_client.mustache`.
- **client-secret-leak-in-default-repr** (MED): OAuth2 authenticators leak `@client_secret`/`@password` via default `inspect`/`p`. Override `inspect` to mask. `ruby/auth/oauth/*`.
- **required-nested-param-validation** (MED): `ruby/api/api.mustache`.

### elixir
- **apiresult-rawbody-nullability** (LOW): `raw_body: String.t() | nil`→`String.t()`. `elixir/api_result.mustache`.
- **elixir-per-op-auth-precedence** (LOW): drop/reorder the extra `Keyword.get(opts,:auth)` channel so `options.auth` is not silently overridden (11 source per-op auth only from `options.auth`). `elixir/api/api.mustache`.
- **required-nested-param-validation** (MED): `elixir/api/api.mustache`.

---

## NOT FIXED (with reason)
- `swift-linux-proxy-refusal` — swift-corelibs-foundation has no `connectionProxyDictionary`; already throws a clear SDK error. Now recorded as WONTFIX in `AGENT.md` (platform limit, no in-library fix).
- UNIFORM-GAPs (const-not-validated, deepobject-key-encoding, license-file, apikey-query/cookie-encoding) — all 12 identical; feature requests, not parity defects.
- `*Exception` vs `*Error` suffix — WONTFIX (idiomatic, documented).
