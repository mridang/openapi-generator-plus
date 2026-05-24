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
