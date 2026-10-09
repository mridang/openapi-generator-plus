# Working in this repository

This repo generates **one SDK in twelve languages**. Java, Kotlin, C#, PHP, Python,
Ruby, Node, Dart, Go, Rust, Swift and Elixir. Everything here follows from that: a
change to one generator is a change to twelve, and a difference between two languages
is a defect until you can name the language feature that forces it.

Read `CLAUDE.md` too: it holds the alignment rules for generated prose and examples.

## Layout

| Path | What it is |
|---|---|
| `src/main/resources/templates/<lang>/` | The mustache templates. This is where SDK code is written. |
| `src/main/java/.../generators/<lang>/Better<Lang>Codegen.java` | Per-language generator wiring. |
| `src/main/java/.../generators/AbstractBetterCodegen.java` | Shared across all twelve. Changing it changes every SDK. |
| `src/spec/resources/generated/<lang>/` | The goldens: a full SDK generated from the petstore spec. **Never hand-edit.** Regenerate. |
| `src/spec/java/.../spec/<lang>/` | The specs that build, lint and test each golden in a container. |
| `src/main/resources/fixtures/` | Shared test fixtures (TLS certs, the squid proxy config) copied into every SDK. |

## Commands

The toolchain is managed by devbox, so commands run through it:

```bash
devbox run -- mvn verify
```

If `devbox run` fails in your environment, set `JAVA_HOME` to the devbox JDK and put
`.devbox/nix/profile/default/bin` on `PATH` instead.

- Regenerate the goldens: `mvn test -Dtest='Generate*ClientTest,FixtureVocabularyLeakTest' -DforkCount=0 -Djacoco.skip=true -DfailIfNoTests=false`
- One language end to end: `mvn verify -Dgroups=<lang>` (`java`, `kotlin`, `csharp`, `php`, `python`, `ruby`, `node`, `dart`, `go`, `rust`, `swift`, `elixir`)
- Everything: `mvn verify`

**Never pipe a build into `tail`.** Save it to a file and grep the file — a failure
scrolls away otherwise and you have to run it again.

The specs run in Docker. Before a run, stop leaked fixtures:

```bash
docker ps -q --filter ancestor=mridang/chasm:1.3.0 --filter ancestor=ubuntu/squid:6.10-24.10_beta | xargs -r docker stop
```

A `ContainerLaunchException`, a `404 exec`, or a container killed with exit 137 is
infrastructure, not your change: re-run that language once before concluding anything.

## The error contract

Every SDK raises the same error for the same failure. The suffix follows the language —
`Exception` in java, kotlin, csharp, php, python, dart; `Error` in go, node, ruby, rust,
swift, elixir — and nothing else differs.

```
<errorPrefix>Error/Exception          root; the prefix is an option, default OpenAPI
├── ApiError                          an HTTP response came back
│   ├── ClientError  → BadRequest(400) Unauthorized(401) Forbidden(403)
│   │                  NotFound(404) Conflict(409) UnprocessableEntity(422)
│   ├── ServerError  → InternalServerError(500)
│   └── NetworkError                  no response: refused, DNS, TLS, reset. status 0
│       └── NetworkTimeoutError       the request timed out. status 0
├── SerializationError                every encode/decode failure, both directions
├── OAuth2ServerError                 token endpoint answered non-2xx (3xx included)
└── OAuth2TokenError                  token endpoint answered 2xx but unusably
```

- Every error type lives in the SDK's errors namespace. Nothing nested inside a
  serializer, a token manager or an api file.
- `OAuth2ServerError` and `OAuth2TokenError` are generated for **every** spec, whatever
  its security schemes.
- One public factory maps a status to a type (`fromResponse`/`from_response`/
  `FromResponse`). `BaseApi` and the OIDC discovery code both call it; there is no
  second copy of the table.
- A caller mistake is **not** an SDK error. Bad proxy URL, missing CA file, a server
  variable outside its enum, empty credentials, a token requested before the auth code
  was exchanged, use after close: raise the language's own built-in
  (`IllegalArgumentException`/`ValueError`/`ArgumentError`/`TypeError`…, and the
  matching state error). Go returns a sentinel, Rust a typed `Err`, Swift a
  `ConfigurationError`. Never `ApiError(0)`, never a panic, never a library exception.
- Never rewrap the platform's cancellation. A Java interrupt surfaces as
  `CancellationException`, a cancelled C# request as `OperationCanceledException`,
  Kotlin/Swift/Rust use their own.
- Every deadline an SDK arms — connect, read, total, pool — classifies as
  `NetworkTimeout…`. This has been wrong three times: check each path has a test.
- The SDK has no cancellation API of its own.

## Tests

- **Do not add new test files.** Extend the test templates that already exist. A new
  file drifts from the other eleven languages immediately.
- A fix must make the petstore tests catch that bug class, in all twelve. If a bug only
  appears under a real client's configuration, change the petstore configuration so it
  reproduces — that is how the Ruby constant-resolution bug was caught.
- Assert the exact type, fully qualified where a library type shares its simple name. A
  test expecting a broad `Exception` is not a contract test: one such test asserted the
  leak it was supposed to prevent for months.
- No skipped tests except a genuine platform limit (a codec the runtime lacks), and the
  reason goes in the skip message.
- A language's report must count every test its runner ran.

## Linting

Each language's spec runs the linters and type checkers a real client of that language
runs, over sources **and** tests, and the golden must pass them.

**No generated file may disable a whole tool or rule set.** No `# rubocop:disable all`,
no `@file:Suppress("detekt:all")`, no blanket `# ruff: noqa`, no `phpcs:ignoreFile`. If
generated code genuinely cannot satisfy a rule, name that rule in the shipped config
with a one-line reason. Suppressions scattered through sources hide real defects: both
Python and Ruby passed their linters vacuously until this was fixed.

## The client SDKs

Six real SDKs are generated from this repo (Zitadel: java, dotnet, php, python, ruby,
node). In each, `.openapi-generator-ignore` lists the hand-written files the generator
must not overwrite; everything else is generator-owned and will be replaced.

- A fix for generated code belongs **here**, never in the client.
- Adding a dependency to a generated test means every client's keep-listed manifest
  needs it too, or its suite dies at collection.
- After a change lands: rebuild the image, regenerate all six, run their suites.

## Guardrails — regressions that have bitten the client SDKs

A change here passes `mvn verify` on the petstore golden but can still break a real
client, because the clients' CI runs gates the golden does not (dependency hygiene,
containerised analyzers, type checkers). After changing anything that alters generated
code — a file header, an import block, a suppression, a dependency list — regenerate a
real client and run THAT client's own CI gates, not just the golden's tests. Known traps:

1. **`.openapi-generator/DEV-DEPENDENCIES` must list only the deps of tests actually
   emitted to a client** (the `emitUnitTests()` set). It currently also lists deps used
   only by golden-only `generateTests` tests — `assertj-core` (MetadataTest,
   ComposedSchemaTest, per-tag API tests), `junit-jupiter-params` (BaseApiTest,
   OAuth2TokenManagerTest), `testcontainers` (ChasmContainer, SquidContainer). A client
   sets `generateUnitTests` but not `generateTests`, so it never gets those tests; if it
   declares those deps (to "match" this file) they are unused and `mvn dependency:analyze`
   fails. Emit two lists, or filter by what the target generation actually emits.

2. **python: keep `typing_extensions` and its declaration coupled.**
   `models/model.mustache` imports `from typing_extensions import Self` while
   `pyproject_toml.mustache` declares `typing-extensions>=4.7.1`. On Python 3.13 the
   format step (`ruff check --fix`) strips the `Self` import as unused, leaving the
   declared dependency unused → FawltyDeps fails in the client. Either import `Self` from
   stdlib `typing` on 3.13+ and drop the pyproject dependency, or keep both — never one
   without the other.

3. **Removing a `// <auto-generated/>`-style marker turns analyzers ON for generated
   code.** That is usually right, but the generated code then depends on the analyzer
   suppression config (e.g. csharp `.editorconfig`). Make sure every build path that
   compiles the code can see that config — a container build whose `.dockerignore`
   excludes `.editorconfig` will fail even though the local build passes.

4. **A container port the image does not `EXPOSE` is only published on a lenient daemon.**
   The squid proxy fixture publishes 3128 and 3129, and the chasm fixture 4010 and 8443,
   but the images only `EXPOSE` 3128 and 4010. Docker publishes a host binding for a
   `PortBindings` entry only when the port is also in the container's `Config.ExposedPorts`;
   testcontainers-php sets `PortBindings` but never `ExposedPorts`, so on a strict daemon
   (CI) the auth and TLS ports get no binding and `getMappedPort` returns empty, while a
   lenient daemon (local Docker Desktop) publishes them anyway and hides it. The php
   bootstrap builds both fixtures through a container that overrides `createContainerConfig`
   to set `ExposedPorts` for every requested port (each value an empty object). If you add
   a published port to any fixture, expose it there too, and verify on CI, not just locally.

## Forced divergences — do not "align" these away

These twelve-language differences are **required by a named language feature**. They read
as inconsistencies, but collapsing them re-introduces a real bug. Each entry names the
feature so you can tell a forced divergence from a defect.

**php types a defaulted, schema-non-nullable field as non-nullable** (`Defaults::$retries`
`int = 3`, `$mode DefaultsModeEnum = MEDIUM`, `Order::$status OrderStatusEnum = PLACED`),
rejecting `null`, while the other 11 collapse "optional" into nullable and accept `null`.
php's typed-property rule (non-nullable iff default AND schema-non-nullable) is spec-faithful
and arguably *more* correct — it honours the OpenAPI nullable-vs-optional distinction the
others discard. Do not "align" php to the looser majority; that would make it accept a
`null` the schema forbids. (A property with no default stays `?type = null` in php too.)

**swift types `retryAfter` as `TimeInterval` (a `Double` of seconds)**, not a structured
duration, because Foundation has no protobuf-duration / ISO-8601-duration type; the other
ten non-elixir SDKs use their stdlib duration (`Duration`/`TimeSpan`/`ProtobufDuration`/…).
The wire form (`"3600s"`) round-trips through swift's serializer regardless.

**dart advertises only `gzip, deflate` (no `br`/`zstd`, no probe)** because dart has no
stdlib brotli/zstd and, on web, the fetch layer owns Content-Encoding; it only advertises
what `dart:io` can decode. (swift advertises nothing — URLSession auto-negotiates; csharp
advertises `br` but not `zstd` — no .NET stdlib zstd. All codec-set differences trace to
stdlib availability. rust was the one real gap — see T3-6 — because a brotli crate exists.)

**php's composer package name carries a vendor segment (`openapi/…`)** because composer
*requires* `vendor/package`; the other SDKs' ecosystems have no vendor concept, so there is
no majority vendor to match. The package segment is spec-derived; the vendor is structural.

**`responseHeaders` null vs empty is a signal, not sloppiness.** `null`/`nil`/`None`
response headers mean *no HTTP response arrived* — a transport failure (refused, DNS,
TLS, reset, timeout). An **empty map** means a response arrived carrying no headers. The
distinction is documented in the python and swift error sources and asserted by python's
`test_none_headers_and_body_mark_transport_no_response`. Do **not** default the field to
an empty map "for consistency": it erases the transport-failure signal. The HTTP-response
path never leaks a null because `ApiHttpResponse.headers` is a non-nullable map in every
language (go/elixir build it with `make(map…)`/`%{}`), and `fromResponse` passes that real
map straight through; java/csharp's `null ? null : copy(...)` only preserves an incoming
null, never converts empty→null. Null is reachable only via the transport-failure
constructor, which is exactly the point.

**php names the error-body code `errorCode`, not `code`.** PHP's `\Exception` base owns
`$code`/`getCode()`, and the base constructor is `(message, code, previous)` — the SDK
passes `statusCode` as that inherited `$code`. A second property called `code` would
shadow it, so the error-body field is `errorCode`. Proven by phpstan.

**csharp uses `IDisposable`/`Dispose()` where others use `close()`.** Deterministic
cleanup in .NET is the `IDisposable` contract that `using` requires. Java uses
`AutoCloseable`/`close()`, python a context manager, etc. Same lifecycle, language-forced
spelling.

**Configuration construction** is a fluent `Configuration.builder()….build()` in eleven
of the twelve. **elixir** is the sole language with no builder — `new/1` takes a
keyword-options map, because Elixir has immutable data and no mutable object/instance
methods to chain. Within the eleven builders, these variations are forced: **php** keeps
the builder in its own `ConfigurationBuilder.php` (PSR-4 one-class-per-file, no nested
classes); **python**'s frozen `@dataclass` also exposes a public `__init__` (no private
constructors); **go** builds via a package-level `NewConfigurationBuilder()` (no static
methods on types); **rust**/**swift** expose the default config via `Default`/`default()`
(Rust `Default` trait idiom).

**`ServerVariable` lives in its own file in java, php and ruby** — each has a
one-public-constant-per-file autoloading rule: java's compiler (one public top-level class
per `.java`), php's PSR-4, and ruby's Zeitwerk-style path↔constant convention, which the
ruby golden enforces with a test (`declares exactly the constant each lib file path
names`). The other nine co-locate it in `server_configuration`. Do **not** fold the ruby
one in "to match the majority" — the constant-per-file test fails and real autoloading
breaks. (This looks like an alignable split but is not; a read-only audit that never runs
the ruby suite will mislabel it.)

**Auth-header method shape tracks the concurrency model.** Sync-only languages
(java, php, python, ruby, go, elixir — synchronous HTTP clients) expose a single
`getAuthHeaders`. Keyword-colored-async languages fold sync and async into one method
(kotlin `suspend`, swift `async throws`, rust `async fn`). Value-wrapped-async languages
carry a distinct `…Async` alongside the sync one (csharp `Task`, node `Promise`, dart
`Future` — the TAP "Async suffix" convention). Rust additionally has `try_auth_headers`
(a `Result` variant — no exceptions) and `as_http_aware_mut` (an explicit downcast hook —
trait objects have no RTTI). The `NO_AUTH` sentinel exists in all twelve; its shape is
forced (elixir a unique atom, rust pointer-identity, ruby a frozen object, php its own
file per PSR-4).

**`format: number` (bare, no format) maps to the native binary float in SEVEN SDKs, not an arbitrary-precision decimal.** FIVE preserve full textual precision — java/kotlin (`BigDecimal`), csharp (`decimal`), python (`Decimal`), node (branded `Decimal` + `JSON.rawJSON`); the other SEVEN decode into the language's native float — ruby (`Float`), swift (`Double`), go (`float64`), rust (`f64`), php (`float`), dart (`double`), elixir (`float()`). This reads like a defect but is a **deliberate, documented** decision — each of the seven ships an identical "Decimal / `format: number` precision" caveat in its README explaining the forcing feature and the caller opt-in: ruby's `JSON.generate(BigDecimal)` emits scientific/quoted text that breaks the unquoted-number wire form (ruby's `object_serializer` test asserts `weightKg:1.5` unquoted and explicitly warns that Decimal modelling "regressed to emitting a quoted string"); Foundation's `JSONDecoder` decodes JSON numbers into `Double` (opt in with `userInfo[.useDecimal]`); Go's `encoding/json` has no stdlib decimal (use `math/big.Rat`); php/rust/dart/elixir decode through their stdlib / serde JSON-number type. Do **not** silently "align" these to a decimal type — it reverses the documented decision and risks breaking the unquoted-number wire contract, and the seven are consistent with each other. (rust/dart/elixir COULD adopt a decimal crate/package — `rust_decimal`, `package:decimal`, `Decimal` — if strict precision parity is later wanted; that is a deliberate open trade-off, not an oversight. Earlier copies of this note named only ruby/swift/go — the lossy set is seven. A read-only audit that doesn't read the README caveats will mislabel this as an unforced defect.)

**csharp marks a deprecated Options parameter with a doc `<remarks>` note, not `[Obsolete]`.** The generated `PetApi` reads the field internally to build the request, and `CSharpBuildSpec`/`CSharpStaticAnalysisSpec` compile with `dotnet build --warnaserror`, which escalates `CS0618` ("use of obsolete member") to a hard ERROR. `.editorconfig`'s `dotnet_diagnostic.CS0618.severity = none` does NOT override `--warnaserror`, so `[Obsolete]` breaks the build (verified). The `<remarks>` note still surfaces the deprecation in IntelliSense. Deprecated *models/operations* use `[Obsolete]` because nothing self-consumes them; a deprecated *parameter* is self-consumed, so it cannot. (The round-2 audit mislabeled this as fixable.)

**kotlin escapes the soft keyword `file` to `_file`.** `KotlinReservedWordsSpec` deliberately requires `file` in the reserved-word list, so the generator conservatively escapes it even though `file` is only a soft keyword (legal as a plain identifier). This is an intentional repo policy (escape all Kotlin keywords, soft included), enforced by a test; do not remove `file` from `reserved-words/kotlin.txt`. (The round-2 audit mislabeled `_file` as unforced.)

**go/rust/swift/dart model an optional `description` (ServerConfiguration/ServerVariable) as a non-nullable empty string, not a nullable type.** Each says so in-code ("or an empty string if not provided"). The other eight use a nullable type. This is a deliberate empty-string-sentinel representation of an absent optional field; it is **latent** (both fixture servers carry a description) and **metadata-only** (description never goes on the wire), so the two representations are behaviourally identical. Accepted as-is; not worth churning four SDKs' public field type for a cosmetic difference with no wire impact.

## Resolved cross-language fixes (now consistent — do NOT re-flag as defects)

These were real cross-language defects found by the parity audits and FIXED to a
single consistent behaviour. They are listed so a future audit recognises the
current behaviour as intentional and does not "rediscover" the old divergence.

- **Query / authorize-URL space encoding is `%20` everywhere** (RFC 3986), including php's OAuth2 `buildAuthorizationUrl` (`http_build_query(..., PHP_QUERY_RFC3986)`) and ruby's Faraday path (custom `QueryParamsEncoder`). The x-www-form-urlencoded request BODY still uses `+` (correct there). Do not "simplify" any authorize-URL back to a default encoder.
- **go serializes with HTML escaping OFF** (`json.Encoder` + `SetEscapeHTML(false)`), so `<`, `>`, `&` go on the wire literally like every other SDK — not `<`/`>`/`&`.
- **go error-body JSON parsing is depth-capped** (`pkg/errors` has its own `errorBodyJSONDepth`/`unmarshalErrorBody`, duplicated because the errors package cannot import the root package). A non-2xx body cannot be a stack-overflow DoS vector; do not revert `FromResponse`/`GetTypedErrorBody` to a raw `json.Unmarshal`.
- **dart defaults an absent/empty response Content-Type to JSON** (`responseContentType.isEmpty || isJsonMime(...)`), so a JSON body with no Content-Type is deserialized, not dropped — matching swift/node.
- **rust `to_path_value` does NOT date-truncate.** It is a plain stringify; a `format: date` param is a `NaiveDate` (already `YYYY-MM-DD` by its type) and a `format: date-time` param keeps its full RFC 3339 instant. The old string-shape heuristic that trimmed any `...T...` value to date-only is removed (it corrupted date-time path params).
- **rust `value_serializer::serialize_styled` never panics.** An empty required path is rejected at the operation layer (`ConfigurationError::InvalidArgument`), per the error contract (a typed Err, never a panic).
- **The multi-consumes request content-type selector REJECTS an unrecognised value in all 12** (java/elixir already did; go/swift/ruby no longer silently fall back to the first type; python/csharp/php/kotlin/dart/rust no longer pass an arbitrary value onto the wire). Each throws its language's argument/validation error (`IllegalArgumentException`/`ArgumentError`/`ValueError`/`ArgumentException`/`\InvalidArgumentException`/`ConfigurationError::InvalidArgument`/go `error`). null still defaults to the first declared type. A typo never rides the wire as an undeclared Content-Type.
- **kotlin model `equals`/`hashCode` include the `additionalProperties` overflow bucket.** The bucket is a `@Transient` body property the synthesized data-class equality would exclude, so models with additionalProperties get the hand-written override (the generator already did this for `ByteArray` fields). Two instances differing only in extra keys are unequal.
- **python and node reject malformed base64 on decode** (python `b64decode(validate=True)`; node `decodeBase64` validates the alphabet/length before `Buffer.from`). They no longer silently truncate invalid input, matching ruby/php/elixir/go/rust.
- **python's brotli/zstandard are an optional `[compression]` extra**, not mandatory runtime deps (the client imports them behind `try/except ImportError`); they stay in the `dev` dependency-group so the test suite still exercises the br/zstd paths.
- **java and kotlin require a JSON *string* `refresh_token`** in the OAuth2 token manager (java `JsonNode.isTextual()`, kotlin `JsonPrimitive.isString`), ignoring a numeric/boolean value instead of coercing it, matching the other ten SDKs.
- **The README "Requirements" version floor tracks the build manifest.** Each generated README states the minimum language version its own manifest enforces (rust `Cargo.toml rust-version`, dart `pubspec sdk`, go `go.mod go` directive — toolchain-bumped at regen, swift `Package.swift` platforms, elixir `mix.exs elixir`, etc.). Do NOT let the README floor drift below the manifest — rust/dart/go enforce their floor as a hard build gate, so an understated README advertises a version the build rejects.
- **php/ruby raise when a br/zstd response arrives but the optional codec lib is absent** (php `throw`, ruby `raise`), rather than returning the body still-encoded — same as elixir, and required by the "unsupported Content-Encoding raises" contract below. The lib-present branch still decodes.
- **elixir decodes zlib-wrapped `deflate` only** (like the other 11); it does NOT fall back to raw (headerless) DEFLATE. A raw-DEFLATE body raises. (The raw-inflate fallback was removed for parity.)
- **elixir flags deprecated model FIELDS with a `@moduledoc` note** (`**Deprecated field** \`name\``), since elixir's `@deprecated` attribute attaches only to functions, not struct fields — the doc note is the field-level equivalent the other doc-comment SDKs (ruby/php/node) use.
- **The required-parameter "missing" error message is uniform:** `Missing the required parameter '<param>' when calling <Class>.<operation>` in all 12 API layers. The ONE exception is go's and dart's empty-PATH-param check, which lives in the centralized value-serializer (`path parameter '<x>' must not be empty`) and has no operation context available — FORCED, do not try to add `<Class>.<operation>` there. node's `TypeError` string is double-quoted (`"Missing the required parameter '...'"`) because the message embeds the single-quoted `<param>` — a single-quoted literal there is a syntax error; this is a language requirement, not drift from the other SDKs' quote style.
- **An unsupported response `Content-Encoding` raises, it is not passed through, in every MANUAL decompressor.** java/go/rust/python/ruby/kotlin/dart-io/php (and csharp/elixir, which already did) raise from their decompress function on an unrecognised coding; the existing per-language caller wraps it into that SDK's `ApiException`/`ApiError` carrying the response status, per the documented contract — never returns still-encoded bytes. `identity` and an empty/absent Content-Encoding pass through unchanged (the common path, exercised by every client spec). **node, swift and dart-web are FORCED to pass through**: the fetch layer / URLSession / the browser own Content-Encoding there and (on Linux) the header survives after the body is already decoded, so the client cannot tell an unknown-but-already-decoded body from a still-encoded one and must not raise. Do NOT change the default branch of a manual decompressor back to pass-through, and do NOT try to make node/swift/dart-web raise. A chained (multi-coding) Content-Encoding is likewise rejected uniformly: the manual langs' single-token switch treats it as unknown and raises, and csharp's `DecodeContentEncoding` has an explicit multi-coding guard (it formerly decoded each coding). This is unreachable in practice (the clients advertise a single codec).
- **java and csharp models have a field-dumping `toString`/`ToString`.** Both previously lacked one (node/dart already had `inspect`/`toString`; kotlin data-class, rust `Debug`, swift, python repr, ruby, go, elixir get it from the language). Each now dumps `ClassName{prop=value, …}` over all vars (+ the additionalProperties bucket), with byte arrays rendered via `Arrays.toString` / base64 rather than a memory address. csharp uses a fully-qualified `System.Text.StringBuilder` (no `using System.Text` in the model file) and passes the C# analyzers under `--warnaserror`. php's `__toString` is intentionally NOT added yet — it would risk a runtime error on non-stringable fields (arrays, DateTime, enums) and php can't be verified while the pecl toolchain install is failing; add it once php CI is restored.
- **rust advertises and decodes brotli (`br`) as well as zstd.** rust was the only codec-capable SDK advertising `zstd` but not `br`; it now lists `gzip, deflate, br, zstd` in Accept-Encoding and has a `"br"` arm in `decompress_body` (`brotli::Decompressor`, crate `brotli = "7"`, a mandatory dep alongside flate2/zstd). The old "Brotli is intentionally not advertised" comment is removed. A server that ignores Accept-Encoding and sends `Content-Encoding: br` is now decoded instead of silently passed through. rust compiles only on CI (amd64), so this is CI-validated.
- **java/kotlin `artifactId` is spec-derived, not the generator default.** `BetterJavaCodegen`/`BetterKotlinCodegen` default `artifactId` to the invoker package's last segment + `-client` (e.g. `com.example.petstore` → `petstore-client`) instead of `openapi-<lang>-client`, so the Maven/Gradle coordinates reflect the API like the other SDKs' package names. An explicit `artifactId` config still overrides it.
- **Model field docs carry the scalar `example` in all 12.** python/go/swift previously dropped it; they now emit `{{#example}}` in the field doc comment (python `# Example:`, go `// Example:`, swift `/// Example:`). This is safe because `AbstractBetterCodegen.sanitizeByteArrayExample` nulls the synthesised `"null"` placeholder (and `[B@…` byte-array artefacts) for all 12 generators, so the section falls through for exampleless fields — do NOT re-add a "no example / no sanitizer" guard to the swift/go templates (that comment was stale). A mustache `{{! ... }}` comment must never contain a literal `{{tag}}` (e.g. `{{#example}}`): jmustache ends the comment at the first `}}` and leaks the rest into output.
- **The "security: []" no-auth comment is emitted ONLY on genuinely unauthenticated operations in php/rust/elixir.** These three previously stamped the "explicitly unauthenticated — pass the no-auth sentinel" comment on EVERY operation, including ones that inherit the global security requirement (where the code correctly passes null/None/nil, contradicting the comment). The comment is now split on `vendorExtensions.op.securityNone`: real `security: []` ops get the sentinel rationale, inherited-auth ops get an accurate "inherits the global security requirement; pass null" note. Runtime behaviour was already correct and is unchanged; only the misleading comment was gated. The other 9 already did this.
- **The HTTPS→HTTP body-replay downgrade guard anchors on the CURRENT hop, not the original request, in all 12.** go/swift/dart/elixir previously compared the ORIGINAL request's scheme (go `via[0]`, swift `task.originalRequest`, dart the original `uri`, elixir `orig_url` threaded unchanged) against the next hop; an http→https→http redirect chain downgrades on the FINAL hop even though the first request was already http, so the original-anchor missed it and replayed a TLS-protected body in cleartext. Each now anchors on the hop that received the redirect (go `via[len-1]`, swift `task.currentRequest`, dart `currentUri`, elixir `url`). The SEPARATE cross-origin sensitive-header strip still uses the original URL (deliberate, do not conflate). 7 languages already did current-hop.
- **swift's additionalProperties-model memberwise init includes the declared optional fields** (e.g. `Metadata(createdAt:additionalProperties:)`), not just `additionalProperties`. The `{{#isAdditionalPropertiesTrue}}` init previously emitted only requiredVars + additionalProperties, dropping optional schema fields; `Metadata` (the sole declared-field + additionalProperties model) was the only place it surfaced.
- **swift's OAuth2 token manager leaves a known expiry untouched when a refresh response omits `expires_in`** (matching the other 11) rather than overwriting it with the never-expires `nil` sentinel. Resetting to nil on a refresh-without-expires_in would cache a token forever; the other 11 keep the prior expiry so a stale token is refetched.
- **Authenticator debug/repr field-sets are uniform:** java `BasicAuthenticator` includes `host` (its Bearer already did; all 11 others include it); go/node `ApiKeyAuthenticator` repr include `location` (go gained an `ApiKeyLocation.String()` so it prints the name, not the iota int). Secrets stay masked; only the non-secret field-set was aligned.
- **rust per-scheme API-key authenticator wrappers import `ApiKeyLocation`.** The generated `auth/<scheme>_authenticator.rs` wrappers name an `ApiKeyLocation::{Header,Query,Cookie}` variant in their `super(...)` call, so the module must `use super::ApiKeyLocation;`. This is emitted ONLY for the `ApiKeyAuthenticator` base class (`needsApiKeyLocation` in `BetterRustCodegen.renderSchemeAuthenticator`) — bearer/basic/oauth wrappers must NOT import it or `-D warnings` fails on the unused import. This surfaced only once T2-8 declared the previously-dead per-scheme modules (they weren't compiled before), and rust compiles only on CI (amd64), so the import bug was invisible to local verify. Do NOT drop the conditional import.
- **A deprecated operation carries its language's deprecation marker on EVERY public variant**, not just the primary data method: the `*WithHttpInfo`/`_with_http_info` result-returning variant and any server-override / request-content-type overloads all get it. Markers: java/kotlin `@Deprecated`, csharp `[Obsolete]`, dart `@Deprecated(...)`, go `// Deprecated:` doc paragraph, rust `#[deprecated]`, swift `@available(*, deprecated, …)`, elixir `@deprecated`, python `.. deprecated::` docstring, ruby/php/node doc-comment `@deprecated`. kotlin's api template no longer uses a single "floating" `@Deprecated` before the first overload (which left `WithHttpInfo` + base variants bare when server overloads existed) — each of the four method variants now has its own `{{#isDeprecated}}` guard. Do NOT "consolidate" these back to one marker. (java is the opposite case: its single `@Deprecated` sits BEFORE the `{{#hasAuthMethods}}`/`{{^hasAuthMethods}}` split, so it already covers all three `WithHttpInfo` branch variants — adding a second per-branch guard produces a duplicate `@Deprecated` and the compile error "Deprecated is not a repeatable annotation interface". Leave java's shared marker alone.) Self-consumption is safe under warnings-as-errors: csharp CS0618 is suppressed in `Models/`+`Test/` via `.editorconfig` and the client source's only caller of the obsolete `WithHttpInfoAsync` is the obsolete data method (obsolete-within-obsolete rule); elixir same-module calls to an `@deprecated` function do not warn (the pre-existing data-method marker already proved this under `--warnings-as-errors`).

### Round 7 (parity sweep)

- **A required STRING parameter must not be empty in any of the 12 — path, query, header, form or cookie.** An empty string is not a present value for a required parameter, so the client throws the canonical `Missing the required parameter '<name>' when calling <Class>.<operation>` before dispatch rather than putting `?category=` / `category=` on the wire. php and rust previously guarded only the PATH param and emitted an empty required query/form string unencoded; they now guard query/header/form/cookie required strings too (the `{{#isString}}` guard means non-string required params still rely on the language type for presence). The ONE deliberate exception is a `query` param declared `allowEmptyValue: true` (rust `queryStyledAllowEmpty`), where the spec explicitly permits an empty value — do NOT add the empty guard there. php query/header/form use `{{baseName}}` in the message (matching java/kotlin); path uses `{{paramName}}`.
- **The empty-response-body error message is uniform:** `Expected a response body for <op> but received none`, thrown as the SDK's `ApiException`/`ApiError` when a body-returning operation gets an empty/undecodable body. Previously 7 different wordings, with csharp/python/ruby/rust dropping the operation name. go keeps a lowercase `expected …` (ST1005 forbids a capitalized error string) — that lowercase is the only forced divergence. Do NOT re-introduce per-language wording.
- **go's and swift's required-parameter messages now include the word "the".** go's sentinel is `errors.New("missing the required parameter")` (still lowercase for ST1005) and swift's `requirePathParam` helper says `Missing the required parameter '…'` — both were missing "the" and so were stragglers of the R6-5 harmonization. go additionally keeps a distinct `options are required when calling <Class>.<op>` message for the wholly-nil Options struct (a go-only condition — the other SDKs can't receive a null options object or fold it into the first param check); that one is FORCED, leave it.
- **csharp decodes `Content-Encoding: deflate` as zlib-wrapped (RFC 1950) via `ZLibStream`,** not raw DEFLATE (RFC 1951) via `DeflateStream`. HTTP `deflate` is the zlib format, which the other 11 manual decompressors decode (java `InflaterInputStream`, python `zlib.decompress`, go `zlib.NewReader`, rust `ZlibDecoder`, …); `ZLibStream` exists on the `net8.0` target, so the raw choice was never forced. (Latent: csharp's primary path is `AutomaticDecompression`; the manual branch is reached only by a non-auto-decompressing transport.)
- **elixir and dart-io RAISE on a zero-byte body declared `Content-Encoding: gzip`,** matching the 6 direct-attempt decompressors (java/kotlin/python/go/ruby/php) — a zero-byte gzip stream is malformed. elixir's `gunzip_body(<<>>, …)` passthrough clause and dart-io's blanket `if (bytes.isEmpty) return bytes` were removed; `identity` and an empty Content-Encoding still pass an empty body through (that is the common no-content path).
- **ruby model doc-comments render descriptions through `{{#lambda.unescapeDocComment}}`** (like go/dart), so a description containing a quote shows `"` in the `#` comment, not the JSON-escaped `\"`. A `#` comment needs no quote-escaping; only ruby leaked the escapes.
- **README package metadata uses the populated codegen variable:** java/kotlin `Version` is `{{artifactVersion}}` (the pom/gradle value) not the empty `{{packageVersion}}`; node `Name` is `{{npmName}}` (the package.json value) not the empty `{{packageName}}`. The mismatched variables rendered blank `Version`/`Name` fields.
- **php README Requirements lists the mandatory `ext-uri` and `ext-zlib` extensions** that `composer.json` hard-`require`s (same rule as the R6-1 version-floor: the README Requirements must track the manifest). The optional brotli/zstd/ds extensions stay in `suggest` and out of Requirements.
- **java's README decimal caveat matches the generated code.** java types `format: number`/`format: decimal` as `BigDecimal` (precision-preserving, like kotlin/csharp/python/node) — the README now says so and scopes the IEEE-754 loss warning to `format: float`/`format: double`. It previously carried the lossy-`double`-language caveat verbatim, telling users to hand-roll a BigDecimal workaround for a problem java does not have.
- **OAuth2 percent-encoding: the unreserved-character set (`~`, `*`, `!`, `'`, `(`, `)`) is NOT yet harmonized across the 12 (LATENT, documented).** The `+`-vs-`%20` space encoding WAS normalized (see above), but each language still leans on its stdlib encoder's own safe-set in the token-request body, the RFC 6749 §2.3.1 Basic client-auth header, and the authorize URL — so a `client_secret`/`scope`/`state` containing `~`/`*`/`!` is placed on the wire with different raw bytes per language (java/kotlin/php/ruby escape `~`, the other 8 keep it literal; `*` splits the other way). This is latent: a spec-compliant form/query-decoding server decodes them identically, and no petstore fixture uses these characters, so nothing tests it. A future pass could route all three paths through one shared encoder per language with rust's unreserved set (`-_.~`); it is deferred, not forced. csharp's token-body KEY now gets the same `%20`→`+` normalization its VALUE already had (keys are fixed OAuth param names, so this was latent too); swift's authorize URL still emits a literal `+` for a `+` in a value (a `URLComponents` behavior) — same latent class.
- **go and swift HARD-FAIL the OAuth2 token exchange on a non-string `access_token`/`refresh_token`; the other 10 ignore the bad field and continue (BORDERLINE-FORCED, documented).** go decodes the token response into a typed struct (`RefreshToken string`) and swift into a `Decodable`, so a numeric token field makes the whole `json.Unmarshal`/`JSONDecoder` fail; the 10 dynamic-parse SDKs (incl. rust's `serde_json::Value`) skip a non-string value and keep going. Only bites a non-conformant OP that returns a numeric token field. Changing go/swift would mean replacing their typed decoders with dynamic parsing — deferred as borderline-forced; the T2-12 "matching the other ten" note applies to java/kotlin specifically, not go/swift.
- **dart operations take an explicit `options` argument with no default** (`findPetsByStatus(FindPetsByStatusOptions? options)`, called `findPetsByStatus(null)` when empty), unlike the other 11 which default it (`= null`/`= None`/`options?`). This is a deliberate uniform dart signature — every dart operation has the same positional `options` shape regardless of whether any param is required — not an oversight. Zero wire impact. Do NOT special-case some dart operations to optional-positional.

## Known-thin gates (WONTFIX)

Audited: every language's lint/format/type-check runs in **check mode** with honestly
scoped config — no `|| true`, no write-mode-posing-as-check, near-zero inline
suppressions (all named single rules). These few gates are real but deliberately thin;
they are accepted as-is, so don't "discover" them as defects or over-strengthen them into
a cascade of golden fixes:

- **go** `.golangci.yml` is bare `version: "2"` (golangci default linters only). Not
  vacuous: the same spec already runs `go vet` and `staticcheck` separately, so
  golangci adds only errcheck/ineffassign/unused on top.
- **python** ruff runs its default ruleset (E4/E7/E9 + F) with no extra `select`, and
  mypy runs non-strict. Adequate for fully-annotated generated code; it will not flag a
  *missing* annotation.
- **ruby** steep's `:lib` target is full strength (checks lib against the `.rbs` sigs);
  the `:test` target disables the core type diagnostics because the minitest DSL has no
  sigs to check against.
- **swift** `.swiftlint.yml` is committed but **not** run by CI (no Linux SwiftLint
  binary); the gate is still real via `swift-format lint --strict` + `swift build
  -warnings-as-errors`. The client's local `make lint` does run SwiftLint.
- The client test-run specs assert on the runner's **exit code**, not on a
  test-count > 0 — a runner that green-exits on an empty suite would pass unnoticed
  (pytest's `testpaths`/collection-error behaviour covers python, but the guard is not
  general).

## Second parity-audit tail — assessed and closed (do not re-open as defects)

A second read-only cross-language audit surfaced six more "divergences". Each was
investigated directly (not just from the audit's summary, which overstated several) and
found **not** to be a clean fixable defect. They are closed; do not spend another pass
"fixing" them without new evidence.

- **kotlin strict-key (`unevaluatedProperties: false`).** The strict-deserialize path is
  only exercised by the petstore fixture model `StrictTag`; no real client spec needs it.
  Forcing it on kotlin's production deserialize path needs a marker interface + reflection
  routing against kotlinx-serialization — risk with no wire benefit. Fixture-only.
- **free-form null retention.** node's serializer drops `null` entries from a free-form
  map while java/kotlin keep them. Reachable only through a petstore free-form-map fixture
  edge; aligning it needs per-field metadata the schema doesn't carry. Fixture-only.
- **multipart filename.** go/ruby/php send a real filename + sniffed MIME via file-handle
  Options types; the other nine send the field name + `application/octet-stream`.
  "Aligning to the nine" is a codegen type-mapping change that **breaks ruby's and php's
  published client Options APIs** — a real regression, not a cleanup.
- **kotlin multipart test depth.** Ktor's multipart body is a black box to the test, so
  the assertion is structural, not byte-level. Test-coverage nicety, no wire impact.
- **swift dual multipart builder.** `BaseApi` and `DefaultApiClient` both carry
  `buildMultipartBody`/`appendMultipartField`; the helpers are interdependent and the
  tests call `DefaultApiClient`'s directly while `BaseApi`'s are `private`. Deduping is a
  visibility+test-repoint tangle with no wire impact. (Auth-method shape itself is already
  covered under "Forced divergences".)
- **date-time input strictness.** The audit called python "lenient" — it is not; python
  already rejects naive/offset-less datetimes. And **swift cannot be made strict**: it
  maps both `format: date` and `format: date-time` to one `Foundation.Date`, so it needs
  the date-only parse fallback. Clean twelve-language parity here is impossible, and the
  partial "fix" would only regress input handling in a few languages while swift stays
  lenient regardless.
