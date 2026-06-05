# Agent Instructions

## Running Tests

All commands must be prefixed with `devbox run --`.

### Regenerate all clients from templates

```bash
devbox run -- mvn test -Dtest="io.github.mridang.codegen.generators.GenerateClientsTest"
```

This regenerates all 12 language SDKs from Mustache templates into `src/spec/resources/generated/{lang}/`.

### Run full verification (compile + unit tests + integration tests)

```bash
devbox run -- mvn verify 2>&1 | tee /private/tmp/claude-501/mvn-verify-output.txt
```

Always save output to a temp file for inspection. Docker must be running for integration tests.

### Run a single language's integration tests

```bash
devbox run -- mvn test -Dtest="io.github.mridang.codegen.spec.rust.RustClientSpec"
devbox run -- mvn test -Dtest="io.github.mridang.codegen.spec.ruby.RubyClientSpec"
devbox run -- mvn test -Dtest="io.github.mridang.codegen.spec.java.JavaClientSpec"
devbox run -- mvn test -Dtest="io.github.mridang.codegen.spec.node.NodeClientSpec"
devbox run -- mvn test -Dtest="io.github.mridang.codegen.spec.python.PythonClientSpec"
devbox run -- mvn test -Dtest="io.github.mridang.codegen.spec.php.PhpClientSpec"
devbox run -- mvn test -Dtest="io.github.mridang.codegen.spec.csharp.CSharpClientSpec"
devbox run -- mvn test -Dtest="io.github.mridang.codegen.spec.go.GoClientSpec"
devbox run -- mvn test -Dtest="io.github.mridang.codegen.spec.kotlin.KotlinClientSpec"
devbox run -- mvn test -Dtest="io.github.mridang.codegen.spec.swift.SwiftClientSpec"
devbox run -- mvn test -Dtest="io.github.mridang.codegen.spec.dart.DartClientSpec"
devbox run -- mvn test -Dtest="io.github.mridang.codegen.spec.elixir.ElixirClientSpec"
```

### Workflow for template changes

1. Edit templates under `src/main/resources/templates/{lang}/`
2. Regenerate: `devbox run -- mvn test -Dtest="io.github.mridang.codegen.generators.GenerateClientsTest"`
3. Run tests for affected language: `devbox run -- mvn test -Dtest="io.github.mridang.codegen.spec.{lang}.{Lang}ClientSpec"`
4. Run full verify before committing: `devbox run -- mvn verify`

### Checking test failures

After a failed run, inspect the surefire reports:

```bash
grep -E "(FAILED|panicked|error)" target/surefire-reports/io.github.mridang.codegen.spec.{lang}.{Lang}ClientSpec.txt
```

### Notes

- Docker must be running for integration tests (they use testcontainers)
- Ruby tests may show a flaky "proxy-test-network already exists" Docker error — re-run
- Swift codegen runs `git init` inside Docker for swift-format; the `.git` is removed after formatting
- Rust OAuth2 tests require `#[tokio::test(flavor = "multi_thread")]` because the token manager uses `block_in_place`
- **Container reap filter** — testcontainers-java labels its containers
  with `org.testcontainers=true` and `org.testcontainers.managed-by=testcontainers`,
  NOT a project-specific label. To reap leaked fixtures after a test run:

  ```bash
  docker ps -a --filter label=org.testcontainers=true -q          | xargs -r docker rm -f
  docker ps -a --filter label=org.testcontainers.managed-by=testcontainers -q | xargs -r docker rm -f
  ```

  Earlier filters using `com.mridang.openapi.testcontainer=true`
  matched nothing because testcontainers ignores custom labels by default.
  `mvn integration-test` does **not** trigger the `post-integration-test`
  cleanup exec — only `mvn verify` does. Run the reap commands manually
  after each per-language spec invocation, or use `mvn verify` when
  doing a full sweep.

## What counts as a real cross-language gap (audit criterion)

A finding is worth raising as a gap **only if all three hold**:

1. **Divergence** — some of the 12 SDKs do A, others do B. "Everyone
   does X" or "no one does X" is consistent behaviour, not a parity
   gap. A consistent feature-gap (e.g. no SDK auto-retries on 429) is
   a feature request, not a bug.
2. **Caller-visible** — the divergence shows up in wire format, data
   shape, error type, or security boundary the caller can observe.
   Differences that the underlying HTTP library hides from generated
   code (HTTP/2 vs HTTP/1.1, chunked-encoding reassembly, connection-
   pool size defaults) don't count.
3. **Correctness or security impact** — silent data loss, wrong wire
   format, injection vector, type-confusion. Cosmetic differences
   (header capitalisation, log message wording, internal field naming)
   don't count.

Past gaps that passed the criterion and got fixed:
- Gap L: 5 throw / 7 silent on oneOf no-match (data corruption)
- Gap N: 12 forward CRLF in API-key header (security, all-same)
- Gap S: 6 strict / 6 lenient on type mismatch (silent data)
- Gap V: 2 accept NaN / 10 reject (wire-format spec violation)
- Gap W: 11 produce `/pet//details` / 1 catches (URL malformation)
- Gap Y: 8 emit equals / 4 don't (silent set/map misbehaviour)
- additionalProperties: 8 round-trip / 4 drop (data loss)

Past dimensions that failed the criterion and were dropped:
- HTTP/2 negotiation (divergent but caller-invisible)
- Chunked transfer encoding (consistent — every lib does it)
- Rate-limit auto-retry (consistent — no lib does it)

When in doubt, ask: "if this difference flipped on one SDK
overnight, would a caller of that SDK notice?" If no, skip it.

## Known unaddressed issues (do not attempt to fix)

These are real cross-language gaps that have been triaged and explicitly
deferred. Don't reopen them without an owner sign-off — the cost of fixing
exceeds the value of the fix for this project's use case.

### Numeric precision (`format: int64` > 2^53, BigDecimal, `format: decimal`)

Six SDKs silently lose precision on JSON numbers that exceed their native
integer/float range:

- **Node TS** — `JSON.parse` returns IEEE-754 `number`; loses bits above 2^53
- **PHP** — `json_decode` returns float for ints > PHP_INT_MAX (the
  `ObjectSerializer::deserialize` overflow guard for the `int` *type* lands
  in commit `7963a5cc`, but the wider wire-format precision question is
  unsolved)
- **Dart** — `jsonDecode` returns `num`; same IEEE-754 limit
- **Go / Ruby / Swift** — `format: decimal` deserialised as `float64` /
  `Float` / `Double`; `"0.1"` no longer round-trips exactly

Fixing this properly needs all of:
1. A wire-format decision (number vs string vs `oneOf`) — last attempt
   mapping PHP `int64 → string` (commit `759966ca`) was reverted because
   Prism rejected `{"id":"1"}` against `format: int64` schema validation.
2. A public-type-surface decision in each of the 6 SDKs (`bigint` /
   `BigInt` / `*big.Int` / `BigDecimal` / `Decimal`). That's a breaking
   change for existing consumers; needs a migration story.
3. A custom JSON parser per lang that doesn't pre-coerce.

We're not going to do this. The petstore fixture happens not to exercise
the overflow boundary, so the gap is dormant in CI but real in production.
If you find a related symptom, link back to this section instead of trying
to fix it incrementally — partial fixes (e.g. one lang) create wire-format
divergence that's worse than the silent precision loss.

### 307/308 multipart body replay (Kotlin/C#/PHP)

The `MultipartBodyReplayedOn307Redirect` regression test (added in Phase 4
T-new-3) is skipped in Kotlin, C#, and PHP because their underlying HTTP
libraries (Ktor, .NET `HttpClient`, Symfony `HttpClient`) drop or rewrite
the request body when transparently following a 307/308 redirect — RFC 7231
§6.4.7 / RFC 7538 require the body to be replayed verbatim, but the libs
optimise for the more common GET case. Working around this means taking
over redirect handling manually: pre-serialise the multipart body to bytes
(boundary + parts + trailer) *before* the first request, then re-POST the
same byte buffer to the redirect target. Rust has the canonical
implementation — see `client/src/lib.mustache`'s manual redirect loop with
`Body::Bytes` carried across hops. The other 9 SDKs either follow
redirects through a layer that already preserves the body, or use the
Rust-style pattern. The skipped tests document the gap inline (with
`@Disabled` / `Skip = "..."` / `markTestSkipped(...)`); reintroduce them
once the lang-specific manual redirect loop lands.

### Decompression-bomb cap (all 12 SDKs)

Every transport decompresses gzip/deflate/brotli/zstd response bodies to
EOF with no `max_decompressed_response_bytes` cap. A 1 KB compressed
payload expanding to 1 GB OOMs every SDK uniformly. Fix is possible
(add a TransportOptions flag + per-decompressor guard) but the bound has
to be plumbed into each language's underlying stream reader, and the
project's threat model assumes a trusted server. Don't fix.

### Accept-Encoding header value divergence (W7)

The 12 SDKs send 5 different literal `Accept-Encoding` values today:
`br, gzip, deflate, zstd` (Go/Rust); `gzip, deflate, br` (C#/Node/Elixir);
the same with a Linux-conditional fallback (Swift); `gzip, deflate` +
runtime-conditional `br`/`zstd` (Java/Python/PHP/Ruby); fixed
`gzip, deflate` (Kotlin/Dart). Each lang advertises only what its
underlying HTTP library can decompress, so the divergence is functionally
correct — just cosmetic on the wire. Don't normalise; if a server demands
a specific advertised set, callers can override via
`TransportOptions.defaultHeader("Accept-Encoding", "...")`.

### OAuth2 PKCE — RFC 7636 (L34)

No SDK implements PKCE (`code_verifier` / `code_challenge`) for the
`authorizationCode` grant. PKCE is mandatory for public clients
(mobile/SPA) per current OAuth 2.1 draft, but our consumers are
confidential clients (server-to-server with a `client_secret`) and don't
strictly need it. If a public-client SDK is ever needed, generate a
crypto-random 43–128-char `code_verifier`, hash with SHA-256, base64url
the digest as `code_challenge` with `code_challenge_method=S256`, send
both on `buildAuthorizationUrl`, and send `code_verifier` on
`exchangeCode`. Not implementing.

### OIDC ID-token signature + claim validation (L36)

The OAuth2 token-endpoint response may include an `id_token` JWT alongside
`access_token`. No SDK verifies the JWT signature against the OP's JWKS,
nor validates the standard claims (`iss`, `aud`, `exp`, `nonce`). This is
typically an application-level concern; the access_token is what we use
for API auth. Implementing properly would need a JWT lib per lang (jose,
jjwt, python-jose, etc.) + JWKS cache + algorithm-specific verifiers
(RS256, ES256, EdDSA). Not implementing.

### Pagination iterator (L41)

No SDK ships a helper that auto-iterates `Link: <next-url>; rel="next"`
(RFC 5988) responses or cursor-in-body conventions. OpenAPI doesn't
standardise pagination, so per-API iteration logic lives best at the
application layer. Not implementing.

### Logger / interceptor hooks + sensitive-header masking (L38, L40)

No `TransportOptions` field exposes a request/response interceptor, and
no built-in helper masks sensitive header values (Authorization, Cookie,
X-API-Key) before stringifying for logs. Each language has its own
middleware ecosystem (Java HttpClient interceptors, OkHttp interceptors,
Symfony HttpClient event listeners, etc.) — push the concern there. Not
implementing in the SDK.

### Date / DateTime wire-format normalisation (W3, W4, W5)

Three related dimensions, all deferred because:

- petstore fixture has zero `format: date` or `format: date-time` path
  parameters, so CI cannot exercise any change we make
- per-lang fixes require touching the type-mapping in the Java codegen
  (e.g. mapping OAS `format: date` → `chrono::NaiveDate` in Rust instead
  of `DateTime<Utc>`, or to a date-only wrapper in Dart/Node/Swift)
- the existing behaviour is mostly correct for each lang's native type
  (Python/Java/Kotlin/C#/Ruby/Elixir produce date-only because they map
  `format: date` to a date-only native type; Go/PHP have explicit
  `StringifyDate` / `|date` sigil; Dart/Swift/Node/Rust use a full
  datetime type and serialise as full ISO)

**W3 (date in path)** — when `format: date`, six SDKs emit
`YYYY-MM-DD` and six emit full ISO. Owner decision was "respect the
schema's declared format + add tests" but with no path-param coverage
in petstore this can't be validated. To revisit, add a fixture op with
`schema: { type: string, format: date }` as a path parameter, then fix
Dart/Swift/Node/Rust to emit date-only when the path-param sigil
indicates `|date`.

**W4 (DateTime precision)** — seconds (Java/Kotlin/C#/Python/Dart/Go) /
milliseconds (Node/Swift) / microseconds (Ruby/PHP/Rust) / native
(Elixir). Owner picked "any consistent format, never discuss again"
but the per-lang serialiser refactor touches every model's datetime
field. Defer until a real wire-compat bug surfaces.

**W5 (UTC trailing form)** — `+00:00` (9 langs) vs `Z` (Python, Rust,
Elixir). Cosmetic; both round-trip cleanly through every parser. Defer.

### additionalProperties data-loss in 4 SDKs

When a schema combines fixed properties AND `additionalProperties: true`
(e.g. `Metadata` in petstore), 8 of 12 SDKs round-trip extras correctly
(Java, C#, Node, Swift, Dart, Go, Rust, Python). The remaining 4
silently drop extras on deserialise:

- **Kotlin** — `@kotlinx.serialization.Transient` annotation on the
  `additional_properties` field excludes it from both serialise and
  deserialise. Fix: remove `@Transient` and add a custom KSerializer
  that funnels unknown JsonElements into the map (substantial refactor).

- **PHP** — `additional_properties` is declared as a public array but
  the Symfony denormaliser doesn't populate it from unknown JSON keys.
  Fix: implement `DenormalizerInterface` on the model OR use the
  `ObjectNormalizer::EXTRA_ATTRIBUTES` context option to collect extras
  into a callback.

- **Ruby** — `Dry::Struct` ignores attributes not declared via
  `attribute :foo` and exposes no hook to capture unknown keys. Fix:
  override `Dry::Struct.new` to peel off unknown JSON keys into the
  field before calling super.

- **Elixir** — `defstruct` only declares the fixed fields. The
  generated `additional_properties/0` accessor returns `true` but the
  struct has no field to store extras. Fix: add
  `additional_properties: %{}` to the defstruct + capture unknown keys
  in the generated `build/1` function.

These are real data-loss bugs but each fix is a 50–100-line refactor of
the affected `models/model.mustache` plus possibly the language's
ObjectSerializer. Defer until a real consumer hits the issue, since
petstore CI's Metadata tests currently assert only the fixed-field
round-trip (extras assertions are weak in the affected langs).

### Round-5 audit — scope-boundary decisions (WONTFIX / deferred)

A Round-5 audit produced ~75 findings across wire/auth/codegen dimensions.
After triage (~40% were false positives or unverifiable on inspection), the
genuinely-actionable, commonly-handled fixes were landed: Go basic-auth
fail-fast parity, Swift Accept-Encoding decompression, Elixir total-request
timeout, Java+Kotlin OAuth2 password-grant endpoint routing, Kotlin NUL-byte
source hygiene, plus the Phase 1.5 query-serialization decorator and the
formatter-image / cache reproducibility fixes.

The following remaining findings are **deliberately not fixed** — they are
edge cases that essentially no shipping API-client SDK (Stripe, Twilio,
GitHub, AWS SDKs) nor the mainstream generators (openapi-generator,
swagger-codegen) handle. Documented as decisions, not lingering TODOs:

- **F-A5-06 sensitive-header allowlist for custom API-key on redirect** —
  requires an attacker-controlled cross-origin redirect plus a custom-header
  API key simultaneously. No mainstream SDK strips caller-named headers on
  redirect. WONTFIX.
- **F-A5-07 OAuth2 token-endpoint redirect refusal** — token endpoints do
  not redirect in practice; purely theoretical. WONTFIX.
- **F-A5-05 OIDC discovery hardening (issuer/HTTPS validation)** — certified
  OIDC *libraries* do this; generated API clients do not. Revisit only if
  OIDC becomes a first-class target. Deferred.
- **F-W5-12 https->http redirect body cleartext** — rare downgrade scenario;
  `Authorization` is already stripped (Round-4). Body-over-cleartext on a
  downgrade redirect is a corner no SDK guards. WONTFIX.
- **F-W5-15 Set-Cookie comma-join** — generated SDKs do not expose structured
  cookies; exposing a separate list accessor is a large public-API change for
  a need no consumer has. WONTFIX.
- **F-C5-14 multipart vs JSON content-type tie-break** — only triggers when a
  spec lists multiple request content types for one operation (rare).
  Deferred (cheap if ever needed, via the effectiveConsumes decorator).
- **D3 trailing-optional auth (Node/Kotlin/Java/C#/Go/Rust)** — ergonomics,
  not a bug; blocked on the deferred signatureArgs decorator (Phase 1.7,
  itself deferred for irregular per-variant template structure). WONTFIX.
- **F4 format:byte type surface (PHP/Ruby/Node/Elixir)** — the actual bug
  (PHP byte->int) is already fixed (F-C5-01); the remainder is
  string-vs-Buffer cosmetics. WONTFIX.
- **F-C5-02/03 deprecation-marker effectiveness, F-C5-06 UUID typed wrapper,
  F-C5-07 format:time/duration, F-C5-19 integer-enum typing, F-C5-29
  discriminator-mismatch, plus ~35 Round-5 LOW** (format keywords
  email/ipv4/hostname silently dropped, model-name collision detection,
  Bearer empty-after-prefix-strip, expires_in overflow cap, etc.) — field
  standard is "map to string"; typed wrappers and these micro-validations are
  gold-plating no generated client does. WONTFIX.
- **F-C5-09 readOnly/writeOnly strip, F-C5-10/11/27 map-of-Model deep
  deserialize, F-C5-16 default-on-deserialize** — real correctness gaps, but
  (a) the petstore fixture exercises none of them (no readOnly/writeOnly
  field, no map-of-named-model, no defaulted field), so they are unverifiable
  without first extending the spec, and (b) each is a multi-language
  serializer refactor. Same class and precedent as the "additionalProperties
  data-loss in 4 SDKs" entry above. Deferred until a real consumer hits them
  and a fixture is added.

The high-value, commonly-implemented behaviours (basic-auth validation,
redirect Authorization stripping, OAuth2 lifecycle, multipart field escaping,
timeouts, TLS, response decompression) are all already implemented.

### SOCKS proxies (HTTP/HTTPS only — explicit reject)

All 12 SDKs accept only `http://` and `https://` proxy URLs via
`TransportOptions.proxy()`. Anything else (`socks5://`, `socks4://`,
`socks://`) throws/panics with a clear "must use http or https scheme"
message at construction time. This is enforced uniformly in every
`transport_options.mustache`.

Why not SOCKS:
- Underlying HTTP libraries (Java HttpClient, .NET HttpClient, urllib3,
  reqwest, net/http, Faraday, undici, Symfony HttpClient, Dart http,
  Req+Finch, Ktor, URLSession) require extra dependencies or feature
  flags to speak SOCKS — adding it means per-lang library work in all
  12 SDKs with non-trivial divergence in API surface.
- Corporate proxies overwhelmingly use HTTP CONNECT; SOCKS is rare in
  OpenAPI client deployment.
- The validation is deliberately fail-fast — silently passing a
  socks5 URL through to the lib would either be silently dropped
  (lib parses scheme as http) or fail later with an opaque connect
  error.

If you need SOCKS, configure it at the OS / shell level via standard
proxy env vars (`SOCKS_PROXY`) and a SOCKS-aware wrapper, or pre-route
through a local HTTP-CONNECT bridge. Don't audit this as a gap.

### HTTP/2 negotiation divergence (don't audit)

Some SDK underlying HTTP clients negotiate HTTP/2 by default
(java.net.http.HttpClient, Go w/ TLS, Swift URLSession), others HTTP/1.1
(Node undici, Python urllib3, Rust reqwest unless feature-flagged,
Kotlin Ktor CIO). The divergence is purely on-the-wire protocol version;
servers respond identically to either, callers see the same response
bytes. Not a bug, not a parity gap worth tracking. Don't re-audit.

### Chunked transfer encoding (don't audit)

Servers may send `Transfer-Encoding: chunked` to stream a response in
pieces. Every HTTP library in every one of the 12 langs reassembles the
chunks transparently before handing the body to our SDK code — this is
table-stakes HTTP/1.1 behaviour that's been correct since the libs were
written. Auditing this dimension was never going to find a bug. Don't
re-audit.

### Rate-limit auto-retry on 429 (don't audit)

When a server returns `429 Too Many Requests` with a `Retry-After`
header, no SDK in the 12 implements automatic backoff and retry. This
is a net-new *feature*, not a bug — callers can read the header off the
exception themselves and retry as their app sees fit. Not a parity gap,
not silent data loss, not a security issue. If we wanted it, it'd be a
TransportOptions flag plus per-lang retry logic; we don't. Don't
re-audit.

### Non-ASCII header value handling (FIXED in API-key authenticators)

RESOLVED for ApiKeyAuthenticator in all 12 SDKs (commit eb6a1f56).
The HEADER location now rejects any value outside RFC 7230 §3.2.6's
HTAB + printable-ASCII range, raising an idiomatic error per language
(IllegalArgumentException / ArgumentException / ValueError / panic /
preconditionFailure / etc.). The Gap N CRLF check was extended to
cover the full non-ASCII range in the same loop.

Query and Cookie locations remain permissive — both have downstream
URL-encoding pipelines that handle non-ASCII safely.

Custom `headerParams` passed directly to `invokeApi()` are NOT
validated by this layer — that's a caller-level concern out of scope
for the API-key authenticator. If a user wants strict validation on
arbitrary custom headers, that needs a separate hook in the base
header-merge path. Not done; not auditing.

### Gap-fix cycle 16-17 (parallel agent sweep, 2026-05-19) — landed

- **Gap AA (FIXED)**: Swift no-Content-Type silently returned nil
  instead of JSON-parsing. Other 11 default to JSON when CT missing.
  Now matches. commit `aeab1276`.
- **Gap AC (FIXED)**: Bearer authenticators in all 12 SDKs now reject
  non-ASCII / CR-LF tokens (RFC 7230 §3.2.6). Same lazy printable-ASCII
  check as ApiKey. Tests in 11 langs (Swift skipped — preconditionFailure).
  commits `aeab1276` + `4934f4dc`.
- **Gap AD (FIXED)**: Go's encoding/json had no recursion-depth limit;
  malicious 100k-deep payload crashed via stack overflow. Added
  jsonMaxDepth pre-flight scan with MaxJSONDepth=1000. commit `aeab1276`.
- **Gap AE (FIXED)**: Go's decodeBodyByCharset silently reinterpreted
  UTF-16 bytes as UTF-8. Added utf-16/utf-16le/utf-16be branches with
  BOM detection + surrogate pair handling. commit `aeab1276`.
- **Gap AG (FIXED)**: 10 of 12 SDKs threw on JSON responses with
  UTF-8 BOM prefix (RFC 8259 §8.1 forbids it but Windows producers
  emit it). Java Jackson + C# System.Text.Json strip silently; the
  other 10 (Python, Ruby, Node, Go, Rust, Swift, Dart, PHP, Kotlin,
  Elixir) now do too via single-line check at deserialize entry.
  commit `2bbefb50`.

### Still open from this cycle

- **Gap AF**: Empty response body when return type declared — Node,
  Swift, Rust throw parse errors; the other 9 return null/None/nil
  cleanly. Needs lenient empty-body short-circuit in those 3.
  (Verified during cycle: all three actually short-circuit when body
  is empty/null. False alarm — no fix needed.)
- **Gap AI (FIXED)**: Cookie request header URL-encoded values,
  breaking JWT cookies. All 12 SDKs now validate per RFC 6265 and
  send raw. commit `b649dcb3`.
- **Gap AS (FIXED)**: Swift buildQueryString used `.urlQueryAllowed`
  which doesn't encode `&`, `=`, `+` — query values containing those
  chars would split the URL. Now uses RFC 3986 unreserved-only set.
  commit `f18dd54f`.
- **Gap AT (FIXED)**: Rust OAuth2 build_authorization_url didn't
  URL-encode client_id / redirect_uri / scopes / state. Attacker-
  controlled state could corrupt the URL. Now routed through a
  url_query_encode helper. commit `f18dd54f`.

### Gap-fix cycle 18 — additional pending divergences

- **Gap AU (PARTIALLY FIXED)**: Unknown discriminator value in oneOf
  — Go now returns `fmt.Errorf` instead of silent nil (commit
  `06a58eab`), matching the other 11 SDKs' throw/raise behavior.
  Still open: Python/PHP wrap raw dict in union container on missing
  discriminator field (a separate sub-divergence; less impactful).
- **Gap AV (FIXED)**: `security: []` operation handling — Dart and
  Elixir now skip the default authenticator on explicit no-auth
  operations, matching the other 10 SDKs. commit `a29acc3b`.
- **Gap AJ**: JSON null on required field — Go silently zero-inits
  ({"name": null} → name=""), Python Pydantic accepts, Kotlin
  explicitNulls=false accepts. Other 9 throw. Needs validation pass
  in those 3.
- **Gap AK**: Java's java.net.http.HttpClient silently drops userinfo
  from proxy URL (`http://user:pass@proxy:3128`), so proxy-auth fails
  in Java only. Other 11 either auto-extract via library or use the
  C# manual extraction pattern. Needs Java-only proxy-auth pre-flight.
- **Gap AL**: Content-Encoding header lie (server claims gzip, sends
  plain text). Dart crashes unconditionally; C#/Kotlin/Node/Swift/
  Elixir silently pass corrupted bytes; Java/Python/Ruby/Go/PHP/Rust
  surface a decompression error. Standardise on error.
- **Gap AM (security)**: TLS verifySsl=false has divergent semantics
  — some langs disable both chain + hostname verification, others
  keep hostname check. Document or standardise.
- **Gap AN (NOT A BUG)**: Bearer / Basic / ApiKey validation
  asymmetry — Bearer now validates (Gap AC), ApiKey validates (Gap N),
  but Basic doesn't. Re-examined: the Basic Authorization header is
  the base64 of `username:password`, and base64 output is always pure
  printable ASCII regardless of input bytes. So Basic CANNOT inject
  CR/LF into the header value via input — the asymmetry is correct.
  Closed without action.

### Typed error body access (3-pattern divergence, idiomatic)

Three patterns exist for typed-error-body access (per recent agent
audit):

- **Pre-parse + cast** (Java, Kotlin, C#): `errorBody` is parsed once
  at throw-time into `Object`; `getTypedErrorBody(Class<T>)` does a
  runtime cast. Zero deserialize cost; runtime cast may silently
  return null on mismatch (C#).
- **Lazy deserialize** (PHP, Python, Ruby, Node, Go, Swift, Rust,
  Elixir): `getTypedErrorBody(T)` calls ObjectSerializer.deserialize
  on the raw response body each call.
- **Closure-based** (Dart): `typedErrorBody<T>(T Function(Map) fromJson)`
  requires caller to supply a fromJson factory (matches how Dart
  models work — no reflection). DX divergent from the other 11 but
  consistent with Dart conventions.

This is idiomatic per-language behavior, not a real correctness gap.
Documented; not auditing again.

### Response header case-insensitive lookup (uniform feature gap)

All 12 SDKs preserve transport-case in the response-headers Map exposed
to callers. They all do case-insensitive lookup internally (for
`Content-Type`, etc.) but a caller doing
`response.headers['content-type']` may miss a `Content-Type` value.
This is a uniform behavior (no divergence), and adding a
case-insensitive `getHeader(name)` accessor across 12 SDKs is a
non-trivial API surface change. Documented; not in scope for the
divergence-fix cycles. Don't re-audit.

### OAS 3.1 / JSON Schema 2020-12 feature gaps (uniform — all 12 SDKs)

Audit wave 4 (2026-05-20) confirmed the following OAS 3.1 features are
uniformly unsupported across all 12 SDKs (no divergence — consistent
absence). All require codegen-core upgrades or substantial template
work to address. Documented as known limitations.

- **Gap AW (WONTFIX)** — `dependentRequired` / `dependentSchemas`:
  conditional-required validation. No mainstream client codegen
  implements this (openapi-generator, swagger-codegen, openapi-
  typescript, NSwag, autorest all skip it). Server-side validation
  is authoritative; users wanting client-side checks can plug in a
  JSON Schema validator library. Don't re-audit; documented in all
  12 per-SDK READMEs.
- **Gap AX** — `if` / `then` / `else` schema composition + `unevaluated
  Properties` / `unevaluatedItems`: conditional schemas silently
  dropped. Strict-property enforcement (`unevaluatedProperties:
  false`) ignored — extra fields accepted everywhere. Affects: 12.
- **Gap AY** — `const` keyword: swagger-core has `getConst()` but
  upstream `DefaultCodegen` doesn't translate it (literal warning in
  upstream code: "Maybe it's a const (not yet supported) in openapi
  v3.1 spec."). A `const: "v1"` field generates as a regular settable
  string. Affects: 12.
- **Gap AZ (fixed — normalized cross-lang)** — `prefixItems` (tuple
  arrays). OAS 3.1 / JSON Schema 2020-12 `prefixItems` declares a fixed
  per-index type sequence, e.g.
  `prefixItems: [{type: number}, {type: number}, {type: string}]`.
  No mainstream client codegen has a portable representation for a
  heterogeneous fixed-arity tuple across all 12 target languages, so
  this is handled by `NormalizePrefixItemsRule` (invoked from
  `AbstractBetterCodegen.processOpenAPI`) before the per-language
  pipeline inspects schemas. The rule walks every schema reachable
  from components, parameters, request bodies, and responses and
  rewrites any schema with non-empty `prefixItems` into
  `type: array, items: {}` (the empty object schema, which every
  language template maps to its "any" type). The original per-index
  types are preserved in the schema description as
  `"Tuple of N positional items: [type1, type2, ...]"`. Resulting
  per-lang property types: Java `List<Object>`, Kotlin `List<Any>`,
  C# `List<Object>?`, Go `*[]interface{}`, Rust
  `Option<Vec<serde_json::Value>>`, Swift `[AnyCodable]?`, Dart
  `List<Object>?`, Python `Optional[List[object]]`, Node
  `Array<unknown>`, PHP `?array`, Ruby `Array<Object>`, Elixir
  `[any()]`. Positional type safety is lost — callers must downcast
  per index following the docstring. Idiomatic per-lang tuple emission
  (Python `NamedTuple`, Kotlin `Pair`/data class, Rust tuple struct,
  Swift typed tuple, etc.) is a follow-up. Exercised by `GeoPoint` in
  the petstore spec, referenced as `Pet.location`. Affects: 12 (the
  rule applies uniformly across all SDKs).
- **Gap BA** — `type: ["string", "null"]` 3.1 syntax: relies entirely
  on swagger-parser auto-converting to `nullable: true`. If the
  conversion is broken upstream, all 12 SDKs fail together (nullable
  not emitted on the field). Needs an upstream verification test.
- **Gap BB (partially fixed — rule landed)** — `contentEncoding` /
  `contentMediaType`: 3.1 string-with-embedded-binary annotation was
  silently ignored. Now normalized by `CONTENT_ENCODING` rule
  (`AdvancedOpenAPINormalizer` → `ContentEncodingRule`): `base64` /
  `binary` map onto the existing 3.0 `format: byte` / `format: binary`
  codepath; `base64url` and `base16` set `format: byte` plus the
  `x-is-base64url` / `x-is-base16` extension flags so per-language
  templates can route to URL-safe / hex decoders. `contentMediaType`
  is appended to the schema description as
  `"Content media type: <value>"`. Per-language template work to
  honour `x-is-base64url` / `x-is-base16` flags is still pending (Java
  `Base64.getUrlDecoder`, Python `base64.urlsafe_b64decode`, etc.) —
  the default base64 path is already correct for `base64`. Affects:
  12 (rule applies across all SDKs uniformly; routing TODO).
- **Gap BC (WONTFIX)** — `webhooks` (3.1 top-level) and `callbacks`
  (3.0 per-op): SDK scope is **client → server** only. We do NOT
  generate any code for spec entries that describe **server → client**
  callbacks. Users who need to handle webhooks should write the
  handler themselves and use the SDK only to deserialize the incoming
  payload (and even then via the relevant request schema, not a
  webhook-specific model). Don't re-audit; documented in all 12
  per-SDK READMEs.
- **Gap BD** — `examples` (plural, named with summary/description):
  only the singular `example` propagates into docstrings; the plural
  `examples` object is dropped. Multiple named scenarios in spec
  ("Happy Path", "Error Case") never reach generated code. Affects: 12.
- **Gap BE (WONTFIX)** — Numeric/string constraint validation
  (`minLength`, `maxLength`, `minimum`, `maximum`, `exclusiveMinimum/
  Maximum`, `minItems`, `maxItems`, `uniqueItems`, `minProperties`,
  `maxProperties`, `multipleOf`). Server-side validation is
  authoritative; client-side checks are DX nicety only. Users wanting
  client-side validation can plug in a JSON Schema validator
  library. Don't re-audit; documented in all 12 per-SDK READMEs.
  (Historical note: Python's Pydantic enforces `pattern` only; the
  other 11 enforce nothing. Closed without action.)

### Dart oneOf primitive filtering (idiomatic minor divergence)

`BetterDartCodegen.filtersOneOfAnyOfPrimitives() = true` removes primitive
variants from `oneOf: [Pet, string]` unions, while the other 11 keep
both. Petstore spec doesn't exercise this. Documented as idiomatic for
Dart's type system (mixing class types with primitives in a union is
awkward in Dart). Not auditing again.

### Per-call cancellation (no CancellationToken / AbortSignal / ctx.Context)

No operation method in any of the 12 SDKs accepts a per-call cancellation
handle. Callers cannot abort an in-flight request mid-flight; they have
to wait for the configured request timeout (`TransportOptions.timeout`)
to fire. PHP even ships an unused `CancellationToken` class — it's
referenced nowhere because the operation surface never threaded it in.

Future fix: add an optional `cancel` (or `ctx` / `signal` / `token`)
parameter to every generated operation method, plumb it through the
`invokeApi` / `send_request` boundary, and bind it to the underlying
transport's native cancellation primitive (`java.net.http.HttpRequest`
+ `CompletableFuture.cancel`, .NET `CancellationToken`, Node
`AbortSignal`, Go `context.Context`, Rust `tokio::select!`, Python
`asyncio.CancelledError`, etc.). The surface change is invasive — every
operation signature in every SDK gets one more argument, and the
TransportOptions builder grows a default-cancellation hook — so it's
deferred until a real consumer asks for it. Don't audit as a gap.

### LICENSE file not emitted (all 12 SDKs)

Every SDK declares `license: MIT` in its package manifest
(`pom.xml`, `Cargo.toml`, `package.json`, `pyproject.toml`, `*.gemspec`,
`composer.json`, `pubspec.yaml`, `mix.exs`, `go.mod` via the GitHub repo
metadata, `Package.swift`, `*.nuspec`, Kotlin `build.gradle.kts`), but
no SDK ships an actual `LICENSE` / `LICENSE.md` file alongside the
generated sources. Most public registries (npm, crates.io, PyPI,
Packagist, RubyGems, pub.dev, Hex.pm) will warn on publish; GitHub's
license auto-detect can't find one to display in the repo sidebar.
This is intentionally a **caller responsibility post-generation** —
drop the appropriate `LICENSE` file into the generated tree as part of
the release pipeline. Not auto-emitted because the SDK template doesn't
know which jurisdiction's text the caller wants, and because including
a third-party MIT text file in every generated tree complicates IP
audits for downstream consumers.

### Runtime version baselines are bleeding-edge

Each SDK's manifest pins a modern minimum runtime: Java 25, .NET 10,
Kotlin 2.2 (JVM target 17), Python 3.13, PHP 8.4, Ruby 3.4, Rust 1.85
(edition 2024), Go 1.25, Swift 6 (macOS 14+ minimum deployment), Dart
SDK `>=3.6`, Elixir 1.18, Node `>=22`. This is **intentional** — we
target latest stable and don't backport to LTS / older runtimes.
Consumers stuck on enterprise-LTS toolchains (Java 17/21, Node 18/20,
Python 3.10, .NET 8) will not be able to consume the generated SDKs
verbatim and must fork the codegen, adjust the language-version pin,
and re-run. Not a parity gap, not a regression — a stance. Don't
re-audit.

### Java / C# UTF-8 BOM not stripped at deserialize entry

10 of 12 SDKs explicitly strip a leading UTF-8 BOM (`U+FEFF`) from
JSON response bodies before parsing (Python, Ruby, Node, Go, Rust,
Swift, Dart, PHP, Kotlin, Elixir — see Gap AG, commit `2bbefb50`).
Java and C# do **not** strip it in the SDK layer — they rely on the
parser defaults (Jackson `ObjectMapper`, `System.Text.Json
.JsonSerializer`), which historically tolerate BOM on `InputStream` /
`ReadOnlySpan<byte>` overloads but **not** on `String` input. Our
generated code mostly funnels through `String` overloads. In practice
this is rare — only Windows-emitted JSON (PowerShell `Out-File -Encoding
utf8` pre-PS6, Notepad save-as) typically produces a BOM, and most
HTTP servers strip it before sending. Not fixing because the failure
surface is tiny and the fix needs a per-call defensive trim that
introduces an allocation on every parse. Documented; not auditing.

### `User-Agent` default header diverges across the 12

There is no normalised cross-SDK default user-agent string. Each SDK
emits whatever its language ecosystem considers idiomatic:

- **Node** — `openapi-typescript-client`
- **Go / Rust** — `petstore` (the petstore-fixture package name)
- **Java / C# / Python / Ruby / PHP / Swift / Kotlin / Dart / Elixir** —
  `petstore_client` / `PetstoreClient` (variants on `<package>_client`)

This is idiomatic per ecosystem (Go/Rust packages tend to ship a bare
package name; Java/Python conventionally suffix `_client`) and every
caller can override via
`TransportOptions.defaultHeader("User-Agent", "...")`. The cosmetic
divergence does not affect wire correctness. Don't normalise.

### Elixir bang-vs-tuple, Python async-only, Go `(*T, error)` (idiomatic divergence)

The shape of the per-operation return surface differs across the 12:

- **Elixir** emits both `add_pet!/1` (raises on error) and `add_pet/1`
  (returns `{:ok, term} | {:error, term}`). This is the Elixir
  convention — every public function has a bang and a tuple variant.
- **Python** is async-only — every operation is `async def`. There is
  no sync wrapper. Callers must use `asyncio.run` or an async runtime.
- **Go** returns `(*T, error)` with no panic / exception alternative.

Each shape is idiomatic for its language. Forcing parity (e.g.
adding a sync wrapper to Python, or removing the bang variant from
Elixir) would make the SDK feel un-native in the target ecosystem. Not
a parity divergence to fix.

### CHANGELOG.md not emitted

No SDK ships a `CHANGELOG.md`. Release notes are a **release artifact**,
not a generation artifact — the codegen has no notion of "what changed
between the previous emit and this one" because every run is a clean
overwrite. Callers maintain their own changelog as part of the release
pipeline (e.g. via `release-please`, `conventional-changelog`, `git
log --pretty=...`). Not auto-emitted.

### CI workflows, pre-commit hooks, devcontainer not shipped

The generated tree does not include `.github/workflows/`, `.pre-commit-
config.yaml`, `.devcontainer/`, `.gitlab-ci.yml`, `Jenkinsfile`, or any
other CI / dev-environment scaffolding. This is **out of scope** for an
SDK generator — the SDK is library code, not an application skeleton,
and the caller's CI environment, secret-management, and release
process are completely orthogonal to the generated bytes. The caller
wires CI to fit their org. Don't add.

### PHP `serializeValue` non-styled path-array (works by accident)

PHP's `ObjectSerializer::serializeValue` for the legacy non-styled
path branch does not percent-encode array items before joining them
with `,` — the same gap that was fixed in Dart's `_serializeArray`
(W1). It currently produces correct URLs because the PHP routing
layer (`invokeApi` path-template substitution) always goes through the
styled path (`serializeStyled`) and never invokes the non-styled
branch for array path parameters. The unsafe branch is dead code in
the current generation pipeline. Left as-is — the Dart sibling fix is
cheap and the PHP fix would touch a code path with no observable
behaviour. If a future refactor wires array path params through the
legacy path in PHP, this becomes a real bug; until then it's a latent
hazard documented here.

### Multipart filename defaults to field name (DX divergence)

When sending binary multipart parts (`multipart/form-data` with a file
field), C#, Dart, Node, Java, and Kotlin reuse the **field name** as
the `filename=` attribute on the part's `Content-Disposition` header.
The other 7 (Python, Ruby, Go, Rust, Swift, PHP, Elixir) either
require an explicit filename, derive it from a `File` / `IO` handle
when one is passed, or omit the attribute entirely. Servers that
discriminate by `filename` (e.g. mime sniffing from the extension)
will see different attribute values depending on which SDK the request
came from. This is an idiomatic divergence — every language picked
the default its multipart library makes easiest — rather than a wire-
format bug. Callers who need a specific filename can pass one
explicitly via the per-language file-part API (where exposed). Not
fixing.

## PRIME DIRECTIVE — identical behaviour across all 12 SDKs, no shortcuts

This is the most important rule in this file. It overrides convenience,
speed, and "good enough".

1. **All 12 SDKs must behave, read, and work the SAME way.** The generated
   clients are one product in 12 languages. Same inputs → same wire output,
   same parsing, same errors, same semantics. There is no "this language is
   special".

2. **No shortcuts. No per-SDK exceptions.** Do NOT "document an exception"
   for a language that can't easily match (e.g. a Dart const-default
   limitation). Do NOT change a *test assertion* to paper over divergent
   behaviour. Do NOT mark something WONTFIX/accepted to dodge work. If one
   SDK genuinely cannot match the others, STOP and raise it with the owner
   — do not silently carve it out.

3. **A change is not done until the WHOLE thing is proven green.** For every
   behavioural change you MUST run each affected language's ENTIRE spec
   package locally — not just `{Lang}ClientSpec`, but ALSO `FormattingSpec`,
   `LintingSpec`/`StaticAnalysisSpec`, `TypeCheckSpec`, `BuildSpec`,
   `ReservedWordsSpec` — and see them all green before committing or pushing.
   Run the language's full `spec.{lang}` set (list the spec class FQNs;
   `mvn verify` for a sweep). Running only `ClientSpec` is forbidden — it
   hides formatting/lint/type/static failures.

4. **Every behavioural fix needs the SAME test in all 12 SDK suites**
   (prefer a negative/malformed-input test), asserting the one
   cross-language-agreed behaviour. A fix that lands in fewer than 12 is
   incomplete.

5. **Working with parallel agents / Docker:** you may fix+verify up to two
   DISJOINT languages at once (separate worktrees so mvn/target don't
   collide). Keep a Docker prune loop running and prune leaked
   testcontainers between waves; never restart Docker to recover —
   `docker ps -aq --filter label=org.testcontainers=true --filter status=exited | xargs -r docker rm -f`.

If you cannot honour all of the above for a change, do not make the change.

## TEST-COUNT PARITY — every SDK has every test

Part of the PRIME DIRECTIVE. All 12 SDKs emit JUnit XML, and the per-language
test count MUST be similar across all languages. The clients are one product
in 12 languages, so the test SUITES must mirror each other.

1. **Every behavioral test exists in all 12 SDK suites.** When a test is added
   for one language, the equivalent test (same scenario, same assertion intent)
   must be added to the other 11. A fix or feature is not complete until its
   test is present in all 12.

2. **Counts must stay close.** A language with materially fewer tests than the
   others is a coverage gap to be closed, not accepted. Periodically compare
   the JUnit XML test counts across languages; investigate and backfill any
   language that lags.

3. **No language is exempt.** If a scenario genuinely cannot be expressed in
   one language's test harness, STOP and raise it — do not silently skip it.
