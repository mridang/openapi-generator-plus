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
