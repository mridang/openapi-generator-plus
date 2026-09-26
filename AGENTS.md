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
docker ps -q --filter ancestor=mridang/chasm:1.3.0 --filter ancestor=ubuntu/squid:5.2-22.04_beta | xargs -r docker stop
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

4. **A test fixture exposing more than one container port is CI-fragile.** The squid
   proxy fixture exposes 3128 and 3129; `getMappedPort` for the second port can come back
   empty on a CI runner (it does in php). If you add a port, wait for it explicitly and
   confirm it maps in CI, not just locally.
