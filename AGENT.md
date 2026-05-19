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
