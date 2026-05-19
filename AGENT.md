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
