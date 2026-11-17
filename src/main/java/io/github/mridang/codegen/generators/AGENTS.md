# Shared SDK generator rules (all twelve languages)

This repository generates one SDK in twelve languages (java, kotlin, csharp, php, python, ruby, node, dart, go, rust, swift, elixir) from the same petstore-derived spec. This file is the canonical home of every cross-language rule: the error contract, the wire contract, the test layout, the fixture rules, the parity workflow, the session decisions S1-S16, the decisions D01-D94 of the former PARITY_DECISIONS.md, the forced divergences and a cross-language idiom matrix. Each rule is written once here; a language's own AGENTS.md says only how that language realises it. A difference between two languages is a defect until a named language feature forces it. Decision ids: `S<n>` session decisions (2026-11-16, owner approved), `D<nn>` PARITY_DECISIONS.md, `FD-<n>` forced divergence, `RF-<n>` resolved cross-language fix, `KT-<n>` known-thin gate, `TL-<n>` closed audit tail, `G<n>` client guardrail, `AS-D<nn>` API_SURFACE.md canonical decision (a different numbering from D<nn>).

## Read first

- [Repository root AGENTS.md](../../../../../../../../AGENTS.md): working rules, commands, Docker, the client SDKs.
- The template and generated-prose rules (Mustache-only file generation, no spec-sourced vendor extensions, character-level alignment of the twelve SDKs) are in the root AGENTS.md, section "Template and generated-prose rules".
- Shared code in this folder: [AbstractBetterCodegen.java](AbstractBetterCodegen.java), [DockerFormatter.java](DockerFormatter.java), [NamingConvention.java](NamingConvention.java), [BarrelFileEmitter.java](BarrelFileEmitter.java), [WithTypeSignatureSupport.java](WithTypeSignatureSupport.java).
- Per-language canonical files: `<lang>/AGENTS.md` in this folder (the template folders `../../../../../../resources/templates/<lang>/AGENTS.md` only point back to them).
- Specs: `src/spec/java/io/github/mridang/codegen/spec/<lang>/`; goldens: `src/spec/resources/generated/<lang>/`; the fixture spec `src/spec/resources/specs/petstore/openapi.yaml`.
- Tooling and ledgers: `etc/ci-local.sh`, `etc/api-surface.py`, `etc/api-surface-allow.json`, `etc/parity-findings.py`, `PARITY_FINDINGS.json`, `API_SURFACE.md` (generated; owns the per-element surface table and `AS-D` decisions).

## Gate lessons: working with the generators (read before editing a template)

Ordered by how much they cost in past sessions. Each rule: what to do, an example, the cheapest command that catches it.

### Before you push
- Never push before the full gate passes for all 12 languages and the quick gate (`etc/ci-local.sh check --quick`). CI takes about 25 minutes and a red push costs a round; push in large batches, never fix-by-fix, but commit locally often. Check the CI per job (`gh run view <id> --json jobs`), not the run-level colour, and never claim "green" for a language you could not run locally (rust and kotlin are CI-first).
- Every agent runs its own language's gate in its own worktree: `etc/ci-local.sh check <Lang>` (about 2 minutes). It is parallel-safe (own output folder per run, sweeps containers only when alone). Cap about 4 at once on this 10 CPU / 16 GB machine; 8 at once triggers the Testcontainers flake and Docker address-pool exhaustion. Never run two gates in one worktree: the goldens race.
- Run the language's full spec set (Client, Build, Linting, Formatting, StaticAnalysis, TypeCheck), not only `<Lang>ClientSpec`: Swatch import bugs and `//` comments hid behind a green ClientSpec. Example: Dart and Ruby lint failed on CI because the quick check skipped lint.
- The `regenerate` step of the gate fails whenever goldens changed and are uncommitted. That is expected: commit the regenerated goldens, all twelve current, in the same commit as the template change (a stale but self-consistent golden passes locally and fails CI). Check: `git status --short` after the gate.
- Read the run's own log, never a stale copy: output folders are per run, so open the newest one and check its mtime and the `BUILD SUCCESS|FAILURE` line, not the wrapper's exit code. After a targeted run, grep the log for the intended class: `grep "Tests run:" log | grep <Spec>` (a wrong class name passes vacuously).
- Classify a failure as code or infrastructure before editing: `ContainerLaunchException`, `404 exec`, exit 137, registry 502, pip/apk/SPM download errors are infra; re-run that language once. A compile error hides later findings, so re-run the whole spec after fixing it.

### Cheap checks (seconds, run before the heavy gate)
- The generator Java sources compile at `-source 11`: no `instanceof` pattern matching, records, switch expressions or text blocks. Example: `if (o instanceof String s)` fails the build. Check: `etc/ci-local.sh mvn -o -q compile test-compile` (works offline in seconds). devbox is broken on this machine: do not use it, use `etc/ci-local.sh`.
- Fixture vocabulary: no petstore words in shipped templates or generated-test sample data (`pet`, `doggie`, `photoUrls`, `/v1/pets/`, `shipDate`, `fido`, `swatch`, and client names such as `zitadel`); use neutral names such as `/items`. The only exemptions are the HTTP and OAuth words in `SHARED_WITH_PROTOCOL_VOCABULARY` in that test (`mimeType`, `expiresAt`, `retryAfter`); never add a fixture-invented name to it. Check: `etc/ci-local.sh mvn -o -q test -Dtest=FixtureVocabularyLeakTest` (about 8 seconds); put it in every agent brief and run it after each merge of agent branches.
- Mustache: never write `{{` in a test template's prose (it is parsed as a tag); never put `}}` or a `{{tag}}` inside a `{{! }}` comment (jmustache ends the comment at the first `}}` and leaks the rest); avoid tag-like text in doc comments (`{{#apiKeyHeaderNames}}` in a comment became a real section, and two section tags on one line break standalone-line stripping). Use `{{{x}}}` where HTML escaping would corrupt `"` or a backtick. Check: regenerate one language right after editing; a pure refactor must give a zero golden diff.
- Banned in generated code: `//` comments in php and csharp (and the other `//`-banned languages; use block or `###` comments), any HTML entity (`&quot;`, `&#x60;`; use `&#38;`), inline lint suppressions and whole-tool disables. Check: `grep -rnE '^\s*//|&quot;|&#x60;|&lt;' src/main/resources/templates/<lang>` and `grep -rE 'disable all|detekt:all|noqa|nolint|@SuppressWarnings|rubocop:disable' src/spec/resources/generated`.
- Never weaken or globally relax a linter, type or format rule to get green: fix the code or template. Example: a blanket `detekt:all` hid 76 files of real findings. A rule needs a named single suppression with a reason in the shipped config.
- API surface: new public API needs an `etc/api-surface-allow.json` rule with a `reason` naming the forcing language feature; an allow rule is only for genuine idiom. The extractor now reads wrapped class headers (a header split across lines is no longer missed). Report checker changes separately from template changes: loosening the checker once made a surface count look better by about 100. Check: `python3 etc/api-surface.py check`.
- Secrets scanners: never assign a literal to a `password`-like name in a test (GitGuardian Generic Password fires on `s3cret-value`); build such values at runtime (concatenate or generate). The RFC 7636 appendix B PKCE vector is a known false positive for Generic High Entropy Secret; avoid the literal. Hits stay in history until a human dismisses them. Check: the GitGuardian row of `gh pr checks` after every push.
- Tests with tokens and OAuth: to get a stale token use `expires_in` 0, not a small margin (time margins failed in C#, Ruby, Node). Exact authorize-URL assertions must ignore the random PKCE `code_challenge`. When a new test fails, check the test first (fake headers, expected format) before blaming the code.
- A test must fail without its fix: revert the fix once to prove it. Every behaviour fix needs a test in all twelve test templates; assert the exact canonical string, not `.contains(".123")`.

### Editing templates
- Templates are the only durable source: edit the Mustache template, regenerate, never hand-edit a golden. A stale `target/classes` template gives a false "fix not applied": run `process-resources` first.
- A bug in one language usually exists in all twelve: grep the other eleven templates for the same pattern (Options imports, escapes, NullAway, `//` comments) and fix every site. Language-forced differences are not defects; classify before fixing.
- Do not change shared behaviour in `AbstractBetterCodegen.java` for all languages without an opt-in flag (discriminator-parent broke Python: `setsDiscriminatorParent()`); language agents never edit shared files (`AbstractBetterCodegen.java`, `etc/api-surface.py`, the allow list); the integrator does.
- Void responses are never deserialized; base-URL joins trim a trailing slash; inherited-global security emits `null`, not `NO_AUTH`. Each is one test per SDK.
- Test every template shape against a fixture that triggers it: add the shape to `src/spec/resources/specs/probe/openapi.yaml` and regenerate the twelve goldens. YAML `off`/`on`/`yes`/`no` are booleans; use `type: [string, "null"]` for nullable in OAS 3.1.
- Wording and rule-bearing comments are spec-enforced: before deleting "cruft" comments, grep the specs (FormattingSpec).

### Shared helper containers (Chasm and Squid)
- `SharedHelperContainers` (src/spec/java) starts one Chasm and one Squid per Maven run on the shared runtime network and tears them down in the same shutdown hook that closes it. `SharedRuntimeContainer` passes their addresses to every runtime container as environment variables: `CHASM_HTTP_URL`, `CHASM_HTTPS_URL` (host-mapped through `host.docker.internal`), `CHASM_INTERNAL_HTTP_URL`, `CHASM_INTERNAL_HTTPS_URL` (alias `chasm`, reachable from Squid and from the runtime container), `CHASM_INTERNAL_HTTPS_ADDRESS_URL` (by IP, which the certificate does not list), `PROXY_URL`, `PROXY_AUTH_HOST_PORT`, `PROXY_AUTH_URL` and `API_BASE_URL`.
- Every language's Chasm/Squid fixture checks `CHASM_HTTP_URL` and `PROXY_URL`: when both are set it uses those instances and starts nothing; otherwise it starts its own containers exactly as before, so a generated SDK still runs standalone. A new fixture must keep both paths, and a test must not assume exclusive access (both servers are stateless; no test reads the proxy access log or resets either).
- F1085, no committed private key: `TestCertificates` (src/spec/java) generates the test CA and server certificate with `keytool` once per Maven run into a temp directory; the shared Chasm gets `server.pem` and `server-key.pem` from it, and `SharedRuntimeContainer.certsDirectory` mounts the directory read-only at each language's certs path inside `/app` (so `/work` has it after the copy). The generator no longer emits `ca.pem`, `ca-key.pem`, `server.pem` or `server-key.pem`; a new language must add its certs path to `certsDirectory`. `NoCommittedPrivateKeysTest` guards `src/main` and `src/spec/resources`.

### Process
- Verify every audit or agent finding against the current code on the working branch before editing: 7 of 18 findings were false positives, "already correct" and "done" reports were wrong repeatedly. Put `git rev-parse --abbrev-ref HEAD` and a one-line repro in each brief, and run the gate yourself after an agent reports done.
- Agents must triage EVERY finding in their list and report a verdict for each; do not stop early or report only the easy ones, and ask for per-unit coverage in the report.
- Kill-and-restart hazards: kill the surefire/failsafe JVMs before pruning containers; a killed regen leaves goldens half-formatted (`git checkout -- <goldens>`, then a clean regen; 100+ differing files means a missing Docker formatter, not your change); stop stale watchers with `TaskStop`; do not start a second verify while one runs. If `docker ps` hangs the Docker VMM is wedged: stop and report instead of looping.
- Commit before a context cut: uncommitted work at a boundary once left 179 dirty files. Commit per logical unit and check `git status` and `git branch --show-current` at the start of every turn. Never write rules only in chat: record them in the right `AGENTS.md` at once.
- No stray files: `git add` explicit paths, never `git add -A` at the repo root (wheels were committed once); check `git status --porcelain | grep '^??'` before a push, since untracked new templates with their goldens must be committed together. Keep logs and scripts under private names, not shared ones like `regen.sh`.
- Update the ledger (`PARITY_FINDINGS.json`) and the relevant `AGENTS.md` with each batch; a finding that is not written to a file with an id is lost at the next context cut.
- Do not say "starting" or "running" without a live process: confirm with `ps` and log growth, and never end a turn on intent.

## Process lessons (read before running agents or pushing)

These came out of the long parity session and each one cost at least a CI round. They add to the [Gate lessons](#gate-lessons-working-with-the-generators-read-before-editing-a-template) above and do not repeat them.

### Agents and worktrees
- Never resume or message a running agent to add scope. A message to a still-running agent starts a second instance on the same worktree, and the two race each other's gates and edits. Give new scope to a fresh agent with its own worktree and branch, or wait for the first to finish. Example: a "please also fix X" sent mid-run produced two gates on one tree and a golden that matched neither.
- One agent per worktree and one gate per worktree. Never kill processes you did not start; a gate that looks stuck is usually a Docker or container wait, so read its log first. Example: killing a surefire JVM left half-formatted goldens (`git checkout -- <goldens>` fixed it).
- Gate slots: the machine has 16 GB, so run at most four gates at once, taking a slot through a small local helper (a lock directory per slot, released when the gate exits; keep it out of the repository). Exit 137 from a container or a JVM means out of memory: lower the number of concurrent gates, do not retry blindly.

### Gate and push
- A gate result counts only when all four steps print `ok` on a clean committed tree. The PHP gate once passed on uncommitted edits from another instance and CI then failed. Run `git status --short` before and after the gate, and quote the sha it ran on.
- Never push when `python3 etc/api-surface.py check` or the quick gate fails, and never push a failure without saying so. Run `etc/ci-local.sh check --quick` on the merged head before every push, because a clean branch and a clean integration branch can still fail together.
- A full gate takes about 5 minutes now that Chasm and Squid are shared; plan batches around that and not around the old 2 minutes per language.
- A timing test waits for an observable condition (the server's hit count, a callback), never a fixed sleep. Example: a retry test that slept 200 ms flaked on a loaded runner; waiting for the second request did not.
- A test that pins a default must not depend on which language class is used once every language overrides the hook. Example: the default options hook is pinned with a subclass, not with a particular language's generator.

### Merging language branches into the integration branch
- `etc/api-surface-allow.json` conflicts on every merge. Resolve it by merging the rule lists, never by taking one side: union the rules added since the merge base and subtract the rules removed. A short script that loads the three versions (base, ours, theirs) and writes the sorted result is the safe way; check the result with `python3 etc/api-surface.py check`.
- A transitional allow rule must say "delete when X" in its reason, and is deleted in the change that lands X. Example: a rule that exists only until a language adopts the shared hook.

### Secrets and fixtures
- GitGuardian: never write a base64 Basic-auth literal or a password literal in a test template. Build the value at runtime (concatenate or generate it). A finding on a history commit stays until a human ignores it in GitGuardian, so say so in the PR.
- No petstore operation names in shipped READMEs or templates. Example: `FixtureVocabularyLeakTest` caught `FindPetsByStatusOptions` in a README.
- The shared helper containers start once per Maven run (`SharedHelperContainers`), and the generated tests use `CHASM_HTTP_URL` and `PROXY_URL` when they are set. A TLS host-name test must match the stable part of the message, because the shared container name replaces the IP in it.
- Kotlin: the ktlint download races when two processes share one cache path. Download to a per-process file and move it into place.

### Docker and reading logs
- If `docker ps` hangs, the Docker VMM has a wedged vCPU: container creation stalls and a restart of the daemon does not clear it. Stop, say so, and let the owner restart Docker; do not queue more runs behind it.
- Read the newest log of the current run and check its mtime. A stale log from an earlier run once showed a green result for a gate that had not run.

## Layout

| Path | What lives there | Edit it to change |
|---|---|---|
| `AbstractBetterCodegen.java` (about 6000 lines) | Everything shared by the twelve: default/enum literal escaping (`escapeStringLiteral`, `escapeEnumStringValue`), doc-comment normalisation and the `unescapeDocComment` lambda, reserved-word and model-name collision logic (`getReservedModelNames`, `getModelNameCollisionPrefix` = `Model`), per-operation server enrichment (`enrichOperationServers`), Options-class rendering via Mustache, `sanitizeByteArrayExample`, `preprocessOpenAPI` | Anything that must change in all twelve at once. Changing it changes every SDK: regenerate all goldens. |
| `DockerFormatter.java` | Runs a language's formatter in a container, a few concurrent containers per JVM, fails loudly on any error (a swallowed failure once left a language unformatted non-deterministically) | Formatter container image or concurrency. Formatters install tooling from the network, so local regeneration can show spurious drift: commit all twelve goldens current, never a subset. |
| `NamingConvention.java` | Enum of casing strategies (`CAMEL_CASE`, ...) the codegens declare as static fields instead of scattering `StringUtils` calls | A language's identifier casing. |
| `BarrelFileEmitter.java` | Opt-in interface; `writeOptionsBarrelFiles` calls `emitBarrelFiles`. Implemented by python, node, dart, rust only | The index/mod/barrel file that re-exports the per-operation Options classes. |
| `WithTypeSignatureSupport.java` | Opt-in interface for companion type files; ruby is the only implementor (`.rbs` for Steep) | Companion signature files. |
| `<lang>/Better<Lang>Codegen.java` | Per-language wiring on top of the abstract class | Type mappings, reserved words, file layout of one language. |
| `src/main/resources/templates/<lang>/` | The Mustache templates: SDK code is written here, never built with `StringBuilder` | Generated code and generated tests. Never use `vendorExtensions.x-*` in a template. |
| `src/main/resources/fixtures/` | The squid proxy config copied into every SDK (TLS material is generated per run, see above) | Shared test fixtures. |
| `src/spec/resources/generated/<lang>/` | Goldens: a full SDK generated from petstore. Never hand-edit; regenerate | Nothing by hand. |

A mustache `{{! comment }}` must never contain a literal `{{tag}}` (jmustache ends the comment at the first `}}` and leaks the rest). A golden that changes shape must be regenerated with `mvn test -Dtest='Generate*ClientTest,FixtureVocabularyLeakTest' -DforkCount=0 -Djacoco.skip=true -DfailIfNoTests=false`.

## Idioms (deliberate differences from the other eleven)

The cross-language matrix. Every cell is a genuine idiom or a forced difference; anything else is a defect. `etc/api-surface-allow.json` holds the machine-readable form (129 rules, each with a reason); a rule is added only for genuine idiom and states the forcing feature in its `reason`. Rows with an id are decisions below.

| Topic | java | kotlin | csharp | php | python | ruby | node | dart | go | rust | swift | elixir |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Error suffix | Exception | Exception | Exception | Exception | Exception | Error | Error | Exception | Error | Error | Error | Error |
| Injected transport (S9) | java.net.http.HttpClient | Ktor HttpClient | HttpClient | PSR-18 client | httpx | Faraday | fetch + fetchOptions | http.Client | interface with Do, RoundTripper wrapping | reqwest::Client | URLSession | Req options |
| Cancellation (S7) | interrupt becomes CancellationException | coroutine cancellation | trailing `CancellationToken cancellationToken = default` + builder token | native | asyncio task cancel, `close()` cancels in flight | native | trailing options `{ signal?: AbortSignal }` + client signal | native | `ctx context.Context` FIRST parameter + client base context cancelled by `Close()` | native (drop the future) | native Task cancellation | native |
| Configuration build | builder | builder | builder | `ConfigurationBuilder.php` (PSR-4: one class per file) | frozen dataclass plus public `__init__`, builder | builder | builder + one options object | builder | package-level `NewConfigurationBuilder()` | builder, `Default` | builder, `default()` | `new/1` keyword list, no builder (immutable data) |
| Auth headers shape | sync `getAuthHeaders` | `suspend` (sync and async folded) | sync plus `...Async` (TAP) | sync | sync | sync | sync plus `...Async` | sync plus `Future` variant | sync `AuthHeaders` plus `AuthHeadersOrError` (no exceptions) | `async fn` plus `try_auth_headers`, `as_http_aware_mut` (no RTTI) | `async throws` | sync |
| `NO_AUTH` sentinel | constant | constant | constant | own file (PSR-4) | constant | frozen object | constant | concrete const class | constant | pointer identity | constant | unique atom |
| Sync accessor on token flows (D12) | raises | raises | raises | raises | raises | raises | raises | raises | returns empty map (forced, typed variant exists) | returns empty map (forced) | async only | raises |
| Caller-mistake error | IllegalArgumentException | IllegalArgumentException | ArgumentException | InvalidArgumentException | ValueError/TypeError | ArgumentError | TypeError/Error | ArgumentError | exported sentinel (`errors.Is`) | typed `Err(ConfigurationError)` | `ConfigurationError` | ArgumentError |
| Error cause (S14) | optional cause (getCause) | optional cause | `innerException` (CA1032) | `$previous` | `raise ... from` | `Exception#cause` | `{ cause }` ES2022 | `cause` field (no chaining) | `Unwrap()` | `with_source` | `underlyingError` | optional cause |
| Composed oneOf/anyOf (AS-D41) | polymorphic base class | wrapper with `actualInstance` | polymorphic base class | symfony normalizer + wrapper | pydantic RootModel wrapper | `build` returns the variant, `*Composite` struct for anyOf | TS union alias (primitives: `Array<Buffer> \| Buffer`) | wrapper, `value`/`values` renamed `actualInstance`/`matchedInstances` | wrapper, same renames | sum type; anyOf is a struct of typed `Option` slots | wrapper, `actualInstance(as:)` overload | module functions on the built value, `AnyOfComposite` struct |
| allOf + discriminator (S4) | inheritance | sealed interface + flattened data classes | inheritance | inheritance | inheritance | inheritance | inheritance | inheritance | embedded base struct, decode via interface + discriminator function | internally tagged enum, no serde flatten | protocol + enum with hand-written decoder | flattened structs, dispatch function on the base module, behaviour |
| Bare `type: number` (D83, FD-11) | `BigDecimal` exact | `BigDecimal` exact | `decimal` exact | `float` | `Decimal`, lossy encode | `Float` | branded `Decimal` | `double` | `float64` | `f64` | `Double` | `float()` |
| Deprecation marker | `@Deprecated` (one, before the auth split) | `@Deprecated` on each of four variants | `[Obsolete]`; deprecated parameter uses `<remarks>` (CS0618 + `--warnaserror`) | doc `@deprecated` | `.. deprecated::` docstring | doc `@deprecated` | doc `@deprecated` | `@Deprecated(...)` | `// Deprecated:` | `#[deprecated]` | `@available(*, deprecated, ...)` | `@deprecated`; deprecated field via `@moduledoc` note |
| Content-Encoding | gzip/deflate, br/zstd via optional libs, raises if lib absent | same | `br` yes, no `zstd`, `ZLibStream` for deflate | br/zstd optional ext, raises if absent | br/zstd optional `[compression]` extra | raises if lib absent | fetch owns it: passes through (forced) | gzip, deflate only; web passes through (forced) | manual, zlib-wrapped deflate | gzip, deflate, br, zstd | URLSession negotiates, passes through (forced) | manual, zlib-wrapped only, no raw deflate |
| Operation `options` argument | `@Nullable`, overloads drop trailing args | default null | default | default | default | default | default | explicit, no default | struct values/pointers (fields cannot be required) | struct | default | keyword `opts` list |
| Missing-parameter text | canonical | canonical | canonical | canonical | canonical | canonical | `TypeError`, double-quoted literal | canonical | lowercase start, empty path clause shape differs, `options are required` for a nil struct | canonical | canonical | canonical |
| Multipart file part (TL-3) | field name + `application/octet-stream` | same | same | real filename, sniffed MIME (file-handle Options type) | same | real filename, sniffed MIME | same | same | real filename, sniffed MIME | same | same | same |
| `ServerVariable` file | own file (one public class per file) | with server config | with server config | own file (PSR-4) | with server config | own file (Zeitwerk path-constant test) | with server config | with server config | with server config | with server config | with server config | with server config |
| `retryAfter` (FD-2) | structured duration | structured | `TimeSpan` | structured | structured | structured | structured | structured | `time.Duration` (about 292 years, D88) | structured | `TimeInterval` Double (forced) | `Duration.t()` |
| Optional server `description` (FD-14) | nullable | nullable | nullable | nullable | nullable | nullable | nullable | empty string | empty string | empty string | empty string | nullable |
| Defaulted non-nullable field (FD-1) | accepts null | accepts null | accepts null | rejects null (typed property) | accepts null | accepts null | accepts null | accepts null | accepts null | accepts null | accepts null | accepts null |
| Reserved-word quirks | literals `true false null _` escaped (D44) | soft keyword `file` becomes `_file` (FD-13) | n/a | variables not escaped except `this` (D44) | n/a | narrowed variable set (D44) | n/a | n/a | n/a | n/a | `Error`/`Result` models prefixed (D45) | n/a |
| Platform limit with a documented refusal | none | none | no zstd | none | none | none | no Content-Encoding raise | no br/zstd | go decode of 292y+ durations; U+2028/9 always escaped (D84) | `'` re-encoded by URL crate | caCertPath and ambient proxy on Linux refused/ignored (D72, D74); 512-level cap, first-wins duplicates (D87); sorted keys (D85); lenient `yyyy-MM-dd` date-time (TL-6) | none |
| Lint/type gate on the golden | named in the java file | `-Xwarning-level` flags; `allWarningsAsErrors` deferred (KT-6) | analyzers + `--warnaserror` | phpstan 9, php-cs-fixer, rector dry run | ruff default rules + mypy non-strict (KT-2) | RuboCop, Steep `:lib` full strength (KT-3) | tsconfig strict | analyzer strict-casts/inference/raw-types | `go vet`, staticcheck; golangci-lint is a no-op on generated code (KT-1) | `-D warnings`, `cargo doc -D warnings` (CI only, amd64) | `swift-format lint --strict`, `-warnings-as-errors`; SwiftLint not run (KT-4) | `mix compile --warnings-as-errors`, dialyzer |

Further idioms that hold in all twelve and need no row: the entry class is `Client`; `ApiError`'s parent is reached by inheritance except rust (composition accessor) and elixir (`api_error?/1`); `ApiResult.headers` is a read-only copy wherever the language can express it (D27; go documents "do not mutate").

## Settled decisions (do not re-open in review)

Defaults for every row below unless a row says otherwise. Where: `AbstractBetterCodegen.java` plus the twelve `templates/<lang>/` trees (the owning template is named in the language file). Test: the per-language test template named for the area (ObjectSerializer, ValueSerializer, BaseApi, ApiError, OAuth2TokenManager, authenticator tests) and the petstore golden that compiles and lints it; a fix lands with a table row there, never a new test file. Where a decision names a fixture operation, that operation is in PARITY_FIXTURES (below).

### The error contract

Rule: every SDK raises the same error for the same failure; only the suffix follows the language.

```
<prefix>Error/Exception           root; the prefix is an option, default OpenAPI
├── ApiError                      an HTTP response came back
│   ├── ClientError  → BadRequest(400) Unauthorized(401) Forbidden(403) NotFound(404) Conflict(409) UnprocessableEntity(422)
│   ├── ServerError  → InternalServerError(500)
│   └── NetworkError              no response: refused, DNS, TLS, reset. status 0
│       └── NetworkTimeoutError   the request timed out. status 0
├── SerializationError            every encode/decode failure, both directions
├── OAuth2ServerError             token endpoint answered non-2xx (3xx included)
└── OAuth2TokenError              token endpoint answered 2xx but unusably
```

Every error type lives in the SDK's errors namespace, never nested in a serializer, token manager or api file. `OAuth2ServerError` and `OAuth2TokenError` are generated for every spec. One public factory (`fromResponse`/`from_response`/`FromResponse`) maps a status to a type; `BaseApi` and OIDC discovery both call it. A caller mistake (bad proxy URL, missing CA file, server variable outside its enum, empty credentials, token requested before the code was exchanged, use after close, bad timeout) is never an SDK error: raise the language's built-in argument/state error; go returns a sentinel, rust a typed `Err`, swift a `ConfigurationError`; the one dedicated type is the not-yet-exchanged code (swift `OAuth2AuthorizationCodeError.codeNotExchanged`, rust `OAuth2AuthorizationCodeError::CodeNotExchanged`). Never `ApiError(0)`, a panic or a library exception. Cancellation is never rewrapped and never raised as the timeout error (S7). Every deadline the SDK arms (connect, read, total, pool) classifies as `NetworkTimeout`: this was wrong three times, so each path needs a test. Why: callers catch one hierarchy; leaks of library exceptions were the recurring bug. Test: each language's ApiError test table (exact type, fully qualified where a library type shares the simple name) plus D14 rows. The earlier sentence "the SDK has no cancellation API of its own" is superseded by S7.

### The wire contract (summary)

The twelve SDKs produce identical bytes for identical calls and accept and reject identical inputs. The detail lives in D36-D92 and S1-S6 below; the invariants are: date-time is written `YYYY-MM-DDTHH:MM:SS.SSS+00:00` (fixed three digits, numeric offset, never `Z`); the query and form body keep exactly `A-Za-z0-9-._~` literal (D52); space is `%20` in queries and authorize URLs and `+` in form bodies; order is declaration order (D53, D85); strings are written literally (D84); binary bodies are never re-encoded (D35); caller headers always win (D21). Where a language cannot comply the exception is an `FD-` or a `FORCED` row below, with the forcing feature named.

### Test layout (repository rule; wins over the global default)

Rule: do not add new test files; extend the test templates that exist (a new file drifts from the other eleven immediately). A fix must make the petstore tests catch that bug class in all twelve; if a bug only appears under a real client's configuration, change the petstore configuration so it reproduces (that is how the Ruby constant-resolution bug was caught). Assert the exact type; a broad `Exception` expectation is not a contract test. No skipped tests except a genuine platform limit, with the reason in the skip message. A language's report must count every test its runner ran. Spec gates assert the runner's exit code, not a count above zero (KT-5). Do not let any layer of the twelve differ in test names or structure beyond idiom; `.claude/agents/test-parity-auditor.md` audits that. Unit/spec split for the generator itself (`src/test/java`, the `Generate*ClientTest` regenerators, `FixtureVocabularyLeakTest`, `*ReservedWordsSpec`, `FormattingSpec`) follows the mirror-the-source-file rule.

### Fixture rules (PARITY_FIXTURES, folded)

Rule: every parity fixture lives only in `src/spec/resources/specs/petstore/openapi.yaml` (also served by chasm to every SDK's generated tests); nothing is removed or renamed; every new operation answers from the spec alone (example or schema). `x-internal` operations (the `/test/redirect/*` mocks) are dropped from SDK generation by `DropInternalOperationsRule`. `FixtureVocabularyLeakTest` fails on petstore vocabulary in any generated SDK (names that spell "pet", multi-word camelCase properties such as `weightKg`, `petstore`, `swatch`, `fido`, `doggie`, `zitadel`, and the fixture names `pageCount`, `lastError`, `trackingId`, `asOf`): templates and generated-test sample data use neutral names, the fixture names only in petstore-derived goldens. `remindAt` is not a fixture (ruby template already contains it); the D39 form field is `reminder`. Fixture map: `getPetSummary` (D32, F070, F218, F219: required cookie `session`, matrix path `asOf`, `days` dates), `getOrderByTrackingId` (D67), `createAccount`/`Account` (D56, F046), `searchStore` (D80, D50, D81, D82), `testRedirectNoLocation`/`MalformedLocation` (D71), `testRedirectChain1-3` with 500 ms delays (D76: `timeout(1200)` fails, `timeout(5000)` passes), `PetCriteria` on `getPetTag` (D47) plus `fingerprint`/`X-Fingerprint` byte params (D51), `TreatmentRecord` (D64), `RequiredNullable` (D66), `Separator` and `EdgeCases.'2fa'` (D43), `Error`/`Result` (D45), `reserveStock`/`Reservation`/`ReservationReceipt` (F1062: 200 and 201 carry different schemas, so the operation returns the synthesised `ReserveStockResponse` union and decodes by the actual status; `MultipleSuccessResponsesRule` + `vendorExtensions.op.successResponses`), required header `X-Correlation-Id` on `getPetByName` (D28), `Mirror` server variant and the hostile global server (D40, D42), `Defaults.*` and `Availability` values (D40, D46, D60, D61), `Order.quantity` bounds and `Pet.nickname` pattern (D55), `EdgeCases.phase`/`slug`/`cutoff` (F024, F027, D81). Hostile text in `info.description` and the pet tag (D41) is the point: metadata tests assert the manifest description equals `info.description` verbatim. Known gaps: no path or form `format: byte` parameter (D51 title promises them), no `multipleOf`/`minItems`/`maxItems` fixture (D55), `getBySwatch` may now pick up the component default `green`.

### Parity workflow (pointers)

Rule: default is fixing in direct batches of 15-20 ledger items grouped by bug class: edit templates and codegen, regenerate with `etc/ci-local.sh mvn -B test`, run `python3 etc/api-surface.py check` (must not get worse) and `etc/ci-local.sh check --quick` on a clean tree, one commit per batch, push once per round of 30-50 edits, let CI (about 30 minutes) decide. Every fix needs a test or golden that fails without it. The agent workflow (`/parity-fix`, `.claude/workflows/parity-fix.js`, owner agents `.claude/agents/sdk-<lang>.md`, `parity-guard`, `idiom-<lang>`) is for a hard item spanning all twelve and costs about 650k tokens per landed item; the standing read-only auditors are `oas-conformance-auditor` and `test-parity-auditor`. One ledger: `PARITY_FINDINGS.json` through `etc/parity-findings.py` (`count`, `set`, `show`, `render`, `tokens`); never edit the rendered `.md`. Hooks (`commit-guard.sh`, `docker-guard.sh`, `ownership-guard.sh`) hold agents to the rules; `parity-halt` in the git common dir blocks commits and pushes after a CI failure that a local pass missed. Do not touch `.github` workflows from this repo (S13). Review the cumulative diff and every added allow rule before a PR leaves draft; harmonised means the same behaviour in each language's own idiom, never a pattern bolted on from another language.

### Session decisions of 2026-11-16 (S1-S16, owner approved)

### S1 Strict containers (F006)
Rule: a response that is a container of primitives is checked element by element like a single primitive, in all twelve. Why: D54 covered model fields and top-level values; containers leaked coercion. Test: ObjectSerializer table rows with a fractional, quoted and out-of-range element.

### S2 Content parameters (F054)
Rule: `content:` parameters are encoded through the SDK's own object serializer in every location (query, header, path, cookie), identical bytes in all twelve. Why: extends D50/D80; they are the same rule. Test: `searchStore.q` and the content header.

### S3 Schema constraints (F056)
Rule: the client does not promise to enforce pattern, minLength, maxLength, minimum, maximum, multipleOf, minItems or maxItems; the server owns validation. Enforce only where the language's model layer does it natively and cheaply (python via pydantic). Every README documents it in the section "Schema constraints". Why: supersedes D55's python clause (python no longer strips the Field constraints).

### S4 allOf with a discriminator (F057, F802)
Rule: inheritance in java, csharp, node, python, ruby, php, dart; kotlin a sealed interface plus flattened data classes (a data class cannot be open); go an embedded shared base struct and decode through an interface plus a function that reads the discriminator; rust an internally tagged enum whose subtype structs list the common fields, omit the discriminator field and avoid serde flatten; swift a protocol for the common surface plus an enum with associated values and a hand-written decoder reading the discriminator; elixir flattens into each struct with a dispatch function on the base module and a behaviour for shared accessors. A class has one parent, so a model reused in two unions is never re-parented: inheritance only for the allOf+discriminator parent; unions use interfaces or sealed types.

### S5 Required + nullable (F215, D66)
Rule: the key must be present on decode (missing is an error), an explicit null is a legal value and is kept, and encode always writes the key (null when unset). Fixture: `RequiredNullable.note`.

### S6 uniqueItems order (F319)
Rule: left unspecified; sets go out in the language's natural collection order, documented in the READMEs. Do not sort. Why: supersedes D69 (sorted scalars), which is not implemented.

### S7 Cancellation (F228)
Rule: go takes `ctx context.Context` as the first parameter of every operation (the one deliberate shape difference) plus a client base context cancelled by `Close()`, merged with the call context; csharp takes a trailing `CancellationToken cancellationToken = default` on every operation plus a builder token linked with it (Azure SDK guideline); node takes a trailing per-call options object `{ signal?: AbortSignal }` (like OpenAI and Stripe) plus an optional client signal; python adds no parameter (asyncio cancellation aborts the request, `close()` cancels in-flight requests); the others use their native mechanism. Cancellation raises the language's cancellation error, never the timeout error. Why not a shared `RequestOptions` everywhere: the native handle is what each ecosystem's callers already pass, and a wrapper is less natural in all of them.

### S8 Parameter order (F803)
Rule: required parameters, then server, then the optional-parameters object, then the per-call cancellation handle where the language has one; always, with or without security. Adding security never moves an argument. Owner confirmation pending (see Known limitations).

### S9 Injected transport (F405)
Rule: every client builder accepts the language's standard HTTP client object (see matrix), keeps the SDK defaults applied, never closes a caller-supplied client, and fails fast on conflicts with the SDK's own proxy/TLS/pool settings. Why: OpenAI, Stainless, Speakeasy, Fern, Azure and Stripe SDKs all accept the standard client.

### S10 Dependencies
Rule: a small pure library or a version bump is acceptable when it standardises behaviour cleanly; never a dependency that needs a native binary.

### S11 Out of scope
Rule: callbacks and webhooks, XML bodies and XML nodeType, Link Objects, mutual TLS (closed issues #53, #70, #78, #88, #90). The README says so.

### S12 OpenAPI 3.2 and 3.1
Rule: tracked as epics #49 (3.2) and #96 (3.1) with sub-issues; loading 3.2 documents is #50 (the pinned swagger-parser rejects 3.2).

### S13 Test runner speed
Rule: the CI test job is CPU-bound; spec parallelism is 3 (`junit-platform.properties`); linter and compiler caches live under `/root/.cache` per language (mypy, ruff, golangci-lint, RuboCop, dialyzer PLT files only); `.github` workflows are not touched from this repo (workflow issues are filed in mridang/action-runner-common #47-#50).

### S14 Scope order, UUID case, blank keys, error cause
Rule: OAuth scopes keep the spec's declared order (D04); uuid is written lowercase and validated (D67); an api key or bearer token is blank only when empty or ASCII whitespace (space, `\t \n \r \f \v`, D19); every status-specific error accepts an optional cause.

### S15 Secrets
Rule: the string forms of `Configuration` and `TransportOptions` redact the proxy password and any default header named Authorization, Proxy-Authorization, Cookie or containing key, token or secret, as `***`.

### S16 Timeouts and redirects
Rule: one end-to-end timeout budget across redirect hops (D76); a caller-supplied stream or file handle is never closed by the SDK; proxy URL validation happens in `build()`, not in the builder setter; `caCertPath` is validated even when `verifySsl` is false (D73).

### OAuth and OIDC (D01-D12)
Where/Test default applies; owners are `auth/oauth/*` templates. Rows are the rule; Why is the RFC or the bug it closes.

| Id | Rule | Why / forced exceptions |
|---|---|---|
| D01 | OIDC rediscovery refreshes the discovery document only. The session (tokens and the "exchanged" flag) lives in `OAuth2TokenManager`, which outlives discovery; the replacement delegate is built around the previous delegate's manager; discovery is single-flight (lock, Mutex, in-flight future/Task). | Rediscovery used to drop the session. Rejected: mutate endpoints in place, serve a stale delegate on failure, ignore max-age. php has no lock (single-threaded, forced). |
| D02 | The refresh grant carries client authentication (RFC 6749 s6, 3.2.1) and the fallback re-runs the real grant; token managers take an optional `refreshUrl`. | Refresh was unauthenticated. |
| D03 | `exchangeCode` always POSTs the authorization_code grant and replaces the session; it never goes through the token cache or refresh. A response without refresh_token leaves none. | A cache hit returned a stale session. |
| D04 | Per-scheme wrappers pass the spec's declared scopes in declaration order (`LinkedHashMap` in `SchemeAuthSpec`). | One generator fix; per-operation security scopes stay unused. |
| D05 | OIDC authenticators always request `openid` (prepended, case-sensitive de-dup), at runtime in all twelve. | OIDC Core s3.1.2.1. |
| D06 | The implicit flow treats an empty access token as unset (`Must set the access token before making API requests`); `setAccessToken("")` stays legal. | Clearing the token must be possible. |
| D07 | Token errors are byte-identical in all twelve with no cause or parser detail: `Token response is not valid JSON`, `Token response is not a JSON object`, `Token response missing or empty access_token field`. | Secrets must not leak through parser detail. |
| D08 | String `expires_in` is `^[0-9]+(\.[0-9]+)?$` after trimming ASCII whitespace, floored, anything else means stale; a one-year ceiling (31 536 000 s) applies to `expires_in` and discovery max-age before arithmetic. | Overflow and grammar drift. |
| D09 | Two messages everywhere: `Authorization code must not be empty` and `Must call <exchangeCode-in-language>() before making API requests`; go lowercases (ST1005). | One sentence per condition. |
| D10 | Token body order: grant_type, code, refresh_token, username, password, client_id, client_secret, redirect_uri, scope, then others sorted; the authorize URL has one fixed order too. | Wire bytes are part of the contract. |
| D11 | An empty `state` is absent in authorize URLs. | Consistency across builders. |
| D12 | The sync accessor on token flows raises `OAuth2 authenticators require async token exchange; use <asyncMethod>() instead.` | go `AuthHeaders()` and rust `auth_headers()` are infallible by interface (forced) and return an empty map; their typed variants are what the SDK uses. |

### Error messages and API shape (D13-D29)

| Id | Rule | Why / forced exceptions |
|---|---|---|
| D13 | An `ApiError` for a response that arrived but was unusable (decompress failure, the three redirect refusals) carries the real status, the real lower-cased response headers, null body, and the cause. | `responseHeaders` null/nil/None means "no HTTP response arrived" (FD-5); do not default it to an empty map. |
| D14 | Error-contract test matrix: exact `OAuth2ServerError` message (three rows), deep-body cap, exact rendering, BOM and scalar bodies are table rows in each language's ApiError test. | Locks D13-D18. |
| D15 | `Failed to serialize object to JSON: <detail>`, `Failed to deserialize JSON: <detail>`, `JSON nesting depth <N> exceeds limit 1000` from the SDK's own pre-scan. | One message per failure. |
| D16 | `Refusing to follow redirect to non-HTTP(S) URL: <next-url>`, `Refusing to replay request body across HTTPS->HTTP redirect: <current-url> -> <next-url>`, `Too many redirects (exceeded <N>)`. go lowercases and drops its wrapper. | ST1005. |
| D17 | `Response headers: {"a":"b"}` compact JSON, keys sorted, `/` unescaped, omitted when empty. | java's per-run order disappears. |
| D18 | Every JSON parse entry point strips one leading U+FEFF in the shared parse helper that both the success path and `fromResponse` call. | The two paths drifted. |
| D19 | Bearer: strip a `bearer ` prefix (case-insensitive) once, then reject empty or ASCII-whitespace-only (`Bearer token must not be empty`), then the printable-ASCII check; api keys likewise. | Explicit whitespace set. |
| D20 | Lists in messages render `[a, b, c]`; rejected values are single-quoted: `Invalid value '<v>' for server variable '<name>'. Allowed values: [a, b, c]`, `Unsupported request content-type '<ct>' for <op>; declared types are [a, b, c]`. | go `%s`, lowercase first letter. |
| D21 | `User-Agent` and `X-Request-ID` injection checks are case-insensitive; if any variant is present (even empty) nothing is injected and the caller's key is untouched. | RFC 9110 s5.1. |
| D22 | Default User-Agent token is the SDK's published package name in its registry (`<name>/<version> (<lang>)`; php without the `openapi/` vendor). | Identifies the real package. |
| D23 | Every model and union has a field-dumping string form that renders containers element-wise, includes `additionalProperties`, bytes by content. | dart gained it; php `__toString` deliberately not added (non-stringable fields; open). |
| D24 | oneOf/anyOf wrappers get the same value equality and hash as plain models (anyOf compares the retained list in order). | Wrappers were identity-compared. |
| D25 | Already satisfied: a null required Options object reads as its first missing field with the canonical message. | go `options are required` is forced. |
| D26 | Where overloads or defaults exist an optional body and trailing optional arguments can be omitted (java gained the overloads). | dart, go, rust pass the nullable explicitly (forced). |
| D27 | `ApiResult.headers` is a defensive read-only copy (kotlin `toMap`, python `MappingProxyType`, dart `Map.unmodifiable`, node `Object.freeze`, ruby `dup.freeze`, go `maps.Clone`). | go cannot express read-only: documented. |
| D28 | The name in the missing-parameter message is the caller-facing identifier (`xCorrelationId`, `x_correlation_id`), never the wire name; kotlin's `_file` is therefore right. | Matches path params. |
| D29 | Use after close: `ApiClient has been closed and can no longer send requests` in all twelve (go: `API client has been closed ...`) with the language's built-in state error. | One wording. |

### Content negotiation and bodies (D30-D39)

| Id | Rule | Why / forced exceptions |
|---|---|---|
| D30 | A `format: binary` response is never JSON-parsed, whatever the Content-Type: absent/text/JSON/XML/JS yield the body text's UTF-8 bytes, everything else base64-decoded transport bytes. | Binary corrupted by the JSON path. |
| D31 | A top-level `format: byte` return follows the model path: JSON string decoded with the strict base64 decoder; every failure is a `SerializationError`. | One decoder. |
| D32 | Non-JSON 2xx Content-Type on a non-binary return: String return and text-class type give the text verbatim, anything else is `SerializationError` naming `Unexpected response Content-Type '<media-type>'`; absent means JSON. | dart, swift, node, go keep absent-is-JSON. |
| D33, D34 | Covered by D32 (String plus text-class returns the body); the java mirror's empty-Content-Type base64 bug is fixed. | No public operation returns a String yet, so D33 is not locked. |
| D35 | A binary request body is sent verbatim under whatever Content-Type the caller selected (even `application/json`); the selector only changes the header. | Never route through the JSON serializer. |
| D36 | Multipart primitives (string, number, boolean, date, date-time, duration, uuid, enum) are text parts with no part Content-Type and the canonical stringify form; only objects and models are `application/json` parts. | Fixtures `uploadPetDocument` isPrimary/pageCount/takenAt/swatch. |
| D37 | Fixed: python, php and swift emit the canonical text part (remaining scalar drift is D36). | |
| D38 | A JSON part carries exactly Content-Disposition then Content-Type `application/json` (no charset, no Content-Length). | Forced: kotlin Ktor adds Content-Length to binary parts; dart must add a repeated text part as a `MultipartFile`. |
| D39 | Form-urlencoded scalars use the canonical stringify; arrays repeat the key; nulls omitted; a nested object, map or array of objects is rejected with a `SerializationError` instead of each language's repr. | Fixture `reminder`. |

### Codegen escaping and names (D40-D46)

| Id | Rule | Why / forced exceptions |
|---|---|---|
| D40 | A string literal in generated source evaluates to exactly the spec's bytes: one `escapeStringLiteral` in `AbstractBetterCodegen`, exposed as `{{#lambda.stringLiteral}}`, used by every default, server and OAuth literal. | Defaults and server text with quotes, `$`, `#{}`, `*/` broke builds. |
| D41 | Doc prose is html-unescaped once in `preprocessOpenAPI` (extended to servers and externalDocs) then normalised and rendered with `unescapeDocComment`; manifest descriptions use one escaper per manifest format; no HTML entity (named or numeric) in any golden. | `*/` and `#{x}` must be neutralised. |
| D42 | Per-operation server codegen: one type per variable per operation, unique variant names (`Server<index>` fallback). | Dead and colliding variant types. |
| D43 | A digit-leading identifier gets the letter `n` before casing (`2fa` becomes `n2fa`/`N2fa`); an enum constant that cleans to empty becomes `SPACE_<n>` or `VALUE_<hex codepoints>`; never an escape prefix. | Wire name stays via alias. |
| D44 | Escape a reserved word only when the language cannot use it as that kind of name: java gains `true false null _`; php stops escaping variable names except `this`; ruby narrows its variable set. | Over-escaping changed public names. |
| D45 | Names that collide with language or SDK types get the `Model` prefix in all twelve from `getReservedModelNames()`. | `ModelError`/`ModelResult` survive as the surface name where forced (AS-D01..D04). |
| D46 | String enum literals escape the quote char (never strip), backslash and interpolation sigils; CR/LF/TAB become escapes except ruby and php single-quoted literals. | Stripped quotes changed wire values. |

### Query, form, path and parameters (D47-D53, D78-D82)

| Id | Rule | Why / forced exceptions |
|---|---|---|
| D47 | Object-valued query/header parameters go through `ObjectSerializer.toWireMap` (ordered, wire-keyed, nulls dropped) then expand per the OAS style/explode rows; nested values are rejected. | Fixture `getPetTag` criteria. |
| D48 | A path value of exactly `.` or `..` is rejected before dispatch: `Path parameter '<name>' must not be '.' or '..'` (go lowercases). | Path traversal. |
| D49 | allowReserved keeps literal exactly the RFC 3986 query-legal set and encodes `#`, `[`, `]`. | Forced: node, rust, ruby re-encode `'` as `%27` (URL class). |
| D50, D80 | `content: application/json` parameters in every location go through `ObjectSerializer.serialize` (wire names, canonical date-times, nulls omitted, NaN/Infinity rejected, `< > &` literal); failures propagate as `SerializationError`; then the location's normal encoding. | Same as S2. |
| D51 | `format: byte` parameters (query, header, path, form, each array/map element) are standard-alphabet padded base64. | Fixture `fingerprint`. |
| D52 | Query and form encoding keeps exactly `A-Za-z0-9-._~`; `*!'()` are `%2A %21 %27 %28 %29`. | Same set as OAuth (RF-24). dart and some locks still open (see limitations). |
| D53 | Query params, form pairs and multipart parts follow declaration order, in an insertion-ordered container; authenticator query params are appended after. | Hash order differed per run. |
| D78 | allowReserved encoder output is the contract. | Forced: `[` `]` re-encoded in python, swift, dart; `'` in node, rust. |
| D81 | `format: time` is `HH:mm:ss`, plus `.SSS` only when milliseconds are non-zero, truncated not rounded; go, dart, swift pass the String through. | Fixture `cutoff`, `at`. |
| D82 | A `uniqueItems` array parameter expands exactly like an array parameter in the Set's iteration order. | S6. |

### Models, validation and defaults (D54-D69, D83)

| Id | Rule | Why / forced exceptions |
|---|---|---|
| D54 | Integer decode (field, element, top level) rejects fractional, quoted, boolean and out-of-range values for int32 (also format-less) and int64; nothing truncated. | Extended to containers by S1. |
| D55 | No SDK enforces pattern, length, range, multipleOf or item-count keywords; the server is the authority. | S3 allows native enforcement (python). |
| D56 | `readOnly`/`writeOnly` have no wire effect in any SDK (documentation only); java's inverted Jackson access modes are removed. | go, rust, kotlin, elixir cannot omit per property without custom serializers. |
| D57 | date-time decode accepts only RFC 3339 with offset; never consults host clock or zone; encode is fixed `.SSS+00:00`. | Forced: swift also accepts a bare `yyyy-MM-dd` (TL-6). |
| D58 | `format: byte` decode is strict RFC 4648 standard alphabet, padded, no whitespace; trailing pad bits are not pinned. | Tests must not assert on pad bits. |
| D59 | A required property with a default (including the synthetic discriminator default) is filled when absent; explicit null is still rejected. | go, dart, swift, rust changed. |
| D60 | A default on a `$ref`d enum (sibling or component) becomes the typed enum member; the property default wins over the component's. | Fixtures `Defaults.swatch`, `StockItem.availability`. |
| D61 | Numeric defaults render as exact compiling literals of the target type (decimal exact text, `f` suffix, `.0`, `L`). | Partly implemented. |
| D62 | `format: uri` keeps each language's type surface; wire rule is an absolute RFC 3986 URI, any scheme, byte-verbatim. | Forced: kotlin `io.ktor.http.Url` normalises some inputs. |
| D63 | An `additionalProperties` key colliding with a declared property: the declared value wins, the key is written once. | No duplicate JSON keys. |
| D64 | Non-discriminated oneOf/anyOf variants are tried in declaration order; anyOf merges with the later declared variant winning. | Templates iterated an alphabetical TreeSet. |
| D65 | A null element in an array or map of models is rejected unless the element schema is nullable. | Dynamic languages cannot see nullability and always reject. |
| D66 | Required + nullable: see S5. | go: required+nullable becomes a pointer without omitempty. |
| D67 | `format: uuid` is written lowercase hyphenated, validated both directions; string-typed languages reject malformed values before dispatch. | RFC 9562. |
| D68 | A model missing required properties cannot be built or sent (java no public no-arg constructor, node validate step, rust builder). | go zero values are forced. Needs-user flag set in the ledger; see limitations. |
| D69 | Superseded by S6: scalar `uniqueItems` arrays are not sorted. | |
| D83 | Bare `type: number` is exact in java, kotlin, csharp and node (plain notation, no exponent); python decodes exact and widens to float on encode (forced); the seven native-float languages are documented lossy, each README carrying a "Decimal / `format: number` precision" caveat. | FD-8. |

### Redirects, proxy, TLS, timeouts (D70-D79)

| Id | Rule | Why / forced exceptions |
|---|---|---|
| D70 | A 3xx without a usable Location is returned unchanged; "too many redirects" only when the budget is exhausted with a Location present; the https-to-http body-replay refusal fires only for 307/308 with a body. | Already uniform. |
| D71 | An unparseable Location behaves like a missing one (resolve before draining the body); a resolvable forbidden scheme stays a typed `ApiError`. | Fixtures `testRedirectNoLocation`, `MalformedLocation`. |
| D72 | `caCertPath` is a pin: it replaces the system store, hostname checks still apply. | swift on Linux refuses (corelibs has no trust challenge, forced). |
| D73 | `caCertPath` is validated whenever set, even with `verifySsl=false`. | S16. |
| D74 | Ambient proxy settings (env, JVM, OS) are ignored; `TransportOptions.proxy` is the only source. | swift on Linux cannot disable libcurl's env proxy (forced, next to `proxyUnsupported`). |
| D75 | The SDK's own `Proxy-Authorization` survives redirect hops (re-applied after the cross-origin strip, not on a cross-origin https hop). | java, kotlin, swift, dart inject it as a header. |
| D76 | `timeout` is one end-to-end budget (connect, TLS, hops, body); an exhausted budget raises `NetworkTimeout` without sending. | Fixtures `testRedirectChain1-3`. |
| D77 | `timeout` is null or a positive integer of milliseconds: `Invalid timeout: must be a positive number of milliseconds (got N)` at configuration. | Same error family as a bad proxy. |
| D79 | Proxy: `Invalid proxy URL: <url>`, `Invalid proxy URL (must use http or https scheme): <url>`, `Invalid proxy URL (missing host): <url>`; CA: `failed to read CA certificate from "<path>": <cause>`, `failed to parse CA certificate from "<path>": <cause or no PEM blocks found>`; userinfo is shown as `***@`. | go: lowercase, sentinel wrapped. |

### Serialization and wire edge cases (D84-D94)

| Id | Rule | Why / forced exceptions |
|---|---|---|
| D84 | Request JSON writes `/ < > &`, all non-ASCII, U+2028/2029 and DEL literally; only `"`, `\` and controls below U+0020 are escaped. | Forced: go always escapes U+2028/9 (RF-2 turned HTML escaping off). |
| D85 | Object keys follow declaration order, additionalProperties after. | Forced: swift writes sorted keys (`JSONEncoder`). |
| D86 | `format: double` body text is not canonicalised: equal IEEE-754 value, not equal bytes; non-finite rejected; node `-0.0` sign loss documented. | Parameters keep the canonical `.0` form. |
| D87 | Depth cap 1000 and last-duplicate-wins are the contract. | Forced: swift enforces about 512 levels and first-wins duplicates (php and rust fixed). |
| D88 | Protobuf durations accept +-315 576 000 000 s. | Forced: go `time.Duration` (about 292 years); the error stays `SerializationError`. |
| D89 | Multipart parts carry only Content-Disposition (and Content-Type for file and JSON parts); no Content-Transfer-Encoding, Content-Length or charset on text; names backslash-escaped. | kotlin Ktor exception (D38). |
| D90 | Multipart filename: per-code-point `_` fallback, `filename*` keeps only `A-Za-z0-9-._~` (the go/php/rust form). | One rule. |
| D91 | Declared-charset minimum set everywhere: utf-8, us-ascii, iso-8859-1, windows-1252, utf-16 (BOM-aware), utf-16le/be; registry languages decode more; go, dart, elixir, swift fall back to lenient UTF-8 with U+FFFD. | Forced: no charset registry. |
| D92 | Returned headers drop Content-Encoding and Content-Length whenever the body was decoded. | Headers describe the body the caller receives. |
| D93 | dart `findPetsBornOn` doc comment says the date is a `DateTime` passed through `stringifyDate`. | Doc truth. |
| D94 | Verified fixed, locks exist: non-string date-time rejected, bare null body is absent, non-object where a model is expected is `SerializationError`, exact wire names only, NaN/Infinity rejected, numeric-only additionalProperties encodes an object, swift truncates sub-millisecond, empty `User-Agent` sent as given, ruby form bodies use `stringify`, an auth query param replaces a same-named operation param. | No action. |

### Forced divergences (FD): do not align these away

Each is required by a named language feature; collapsing it reintroduces a bug. Where: the language template and the allow rule named in `etc/api-surface-allow.json`.

- **FD-1 php defaulted non-nullable fields (typed-property rule).** php types a defaulted, schema-non-nullable field (`Defaults::$retries`, `Order::$status`) as non-nullable and rejects null; the other eleven accept null. It is spec-faithful (nullable versus optional). A property without a default stays `?type = null`. Allow rule: `defaults.*`, `order.status`, `edgecases.freeformany` prop-optional.
- **FD-2 swift `retryAfter` is a `TimeInterval`.** Foundation has no protobuf or ISO-8601 duration; the wire `"3600s"` still round-trips.
- **FD-3 codec sets track stdlib availability.** dart advertises only gzip and deflate (no stdlib br/zstd; on web fetch owns it); swift advertises nothing (URLSession negotiates); csharp advertises `br` but not `zstd`.
- **FD-4 php composer package name carries a vendor segment** (`openapi/...`), because composer requires `vendor/package`; the segment after it is spec-derived.
- **FD-5 `responseHeaders` null versus empty is a signal.** null/nil/None means no HTTP response arrived (transport failure); an empty map means a response with no headers. Never default to an empty map. `ApiHttpResponse.headers` is non-null in every language, so null is reachable only via the transport-failure constructor.
- **FD-6 php names the OAuth2 token-error code `errorCode`** because `\Exception::$code` is a typed `int`; `OAuth2ServerException::getCode()` is 0 while `ApiException` passes `statusCode` as the inherited code.
- **FD-7 csharp cleanup is `IDisposable.Dispose()`**; java `AutoCloseable`, python a context manager.
- **FD-8 configuration construction.** Builder in eleven; elixir `new/1` takes a keyword list (immutable data, no chaining); php `ConfigurationBuilder.php` (PSR-4); python frozen dataclass also exposes `__init__`; go `NewConfigurationBuilder()` (no static methods); rust/swift expose `Default`/`default()`.
- **FD-9 `ServerVariable` in its own file in java, php, ruby** (one public class per file; PSR-4; Zeitwerk path-constant test `declares exactly the constant each lib file path names`). Do not fold ruby's in.
- **FD-10 auth-header method shape tracks the concurrency model** (matrix row); the `NO_AUTH` sentinel shape is forced per language.
- **FD-11 bare `type: number` is native float in seven SDKs** (ruby `JSON.generate(BigDecimal)` breaks the unquoted number, Foundation decodes to `Double`, go has no stdlib decimal, php/rust/dart/elixir decode through their JSON number type). Making python and node lossless, and rust/dart/elixir adopting a decimal package, is open and a trade-off, not an oversight (see limitations).
- **FD-12 csharp marks a deprecated Options parameter with `<remarks>`, not `[Obsolete]`**, because the generated `PetApi` reads it and `--warnaserror` makes CS0618 an error that `.editorconfig` cannot lower. Deprecated models and operations use `[Obsolete]`.
- **FD-13 kotlin escapes the soft keyword `file` to `_file`**; `KotlinReservedWordsSpec` requires it. Keep `file` in `reserved-words/kotlin.txt`.
- **FD-14 go, rust, swift, dart model an absent optional `description`** (ServerConfiguration/ServerVariable) as an empty string; metadata only, no wire impact.
- **FD-15 required-parameter message.** Uniform `Missing the required parameter '<param>' when calling <Class>.<operation>`; go lowercases and its empty-path clause comes from `requirePathParam` (`missing the required parameter: path parameter '<x>' must not be empty`), and go keeps `options are required when calling <Class>.<op>` for a nil struct; node's `TypeError` is double-quoted because the message embeds single quotes. go and swift include "the".
- **FD-16 unsupported Content-Encoding raises** in every manual decompressor (never returns still-encoded bytes); node, swift and dart-web pass through (the runtime owns decoding and the header may survive). A chained coding is rejected uniformly. An empty body passes through for any coding in all twelve. csharp decodes deflate as zlib via `ZLibStream`; elixir does not fall back to raw deflate; php/ruby/java/kotlin raise when the optional br/zstd library is absent.
- **FD-17 go and swift hard-fail a non-string `access_token`/`refresh_token`** (typed decoders); the other ten ignore the bad field. Borderline-forced.
- **FD-18 dart operations take an explicit `options` argument without a default**; do not special-case some operations.
- **FD-19 csharp uses the BCL `DistributedContextPropagator`** for trace context (W3C) and types OIDC endpoints as `System.Uri` (rejects non-absolute); dart, swift and go register the propagator through a hook because no optional dependency can be detected at run time.
- **FD-20 swift's authorize URL** keeps `URLComponents` encoding (`*!'()` literal, literal `+`); the other eleven use `-._~` literal.
- **FD-21 python maps `date-time` model fields to `OffsetDateTime`** (aware datetime serialised `.SSS+00:00`), not bare `AwareDatetime`; `typeMapping` stays `AwareDatetime`.
- **FD-22 elixir** `:server` override is emitted only on operations that declare `servers:` and resolves through the typed variant module's `url/1`; elixir flags deprecated fields in `@moduledoc` and omits per-field docs.
- **FD-23 rust** `to_path_value` never date-truncates; `serialize_styled` never panics; per-scheme api-key wrappers import `ApiKeyLocation` only for the api-key base; rust advertises and decodes br and zstd.

### Resolved cross-language fixes (RF): now consistent, do not re-flag

- **RF-1** Space in query/authorize URL is `%20` everywhere (php `PHP_QUERY_RFC3986`, ruby custom params encoder); the form body keeps `+`.
- **RF-2** go serializes with HTML escaping off; its error-body JSON is depth-capped (`unmarshalErrorBody`); do not revert `FromResponse` to raw `json.Unmarshal`.
- **RF-3** dart defaults an absent response Content-Type to JSON (like swift and node).
- **RF-4** The multi-consumes content-type selector rejects an unrecognised value in all twelve; null defaults to the first declared type.
- **RF-5** kotlin `equals`/`hashCode` include the `additionalProperties` bucket.
- **RF-6** python and node reject malformed base64; python brotli/zstandard are an optional `[compression]` extra kept in the dev group.
- **RF-7** java and kotlin require a JSON string `refresh_token`.
- **RF-8** The README "Requirements" floor tracks the build manifest (rust, dart, go, swift, elixir; php lists `ext-uri` and `ext-zlib`).
- **RF-9** java/kotlin `artifactId` defaults to the invoker package's last segment plus `-client`.
- **RF-10** Model field docs carry the scalar example in eleven SDKs (not elixir); `sanitizeByteArrayExample` nulls the `"null"` placeholder for all.
- **RF-11** The `security: []` comment is emitted only on genuinely unauthenticated operations in php, rust, elixir.
- **RF-12** The https-to-http replay guard anchors on the current hop in all twelve; the cross-origin header strip still uses the original URL (do not conflate).
- **RF-13** swift's additionalProperties memberwise init includes the declared optional fields; swift's refresh without `expires_in` keeps the prior expiry.
- **RF-14** Basic and ApiKey authenticator reprs are aligned (host, location); the OAuth2 reprs are not harmonised (F285).
- **RF-15** A deprecated operation carries its marker on every public variant (java's single marker before the auth split is correct; adding a second is a compile error).
- **RF-16** A required string parameter must not be empty in any location (`allowEmptyValue: true` on a query param is the one exception); the empty-response-body message is `Expected a response body for <op> but received none` (go lowercase).
- **RF-17** ruby model doc comments use `unescapeDocComment`; the lambda is applied in model templates only, so whether operation docs leak `\"` is open (F370).
- **RF-18** README `Version`/`Name` use the populated variables (`artifactVersion`, `npmName`); java's README decimal caveat is scoped to float and double.
- **RF-19** OAuth2 percent-encoding keeps `-._~` literal and encodes `*!'()` in token body, Basic header and authorize URL (swift excepted, FD-20).
- **RF-20** elixir decodes only zlib-wrapped deflate; elixir flags deprecated fields in `@moduledoc`.
- **RF-21** Per-model oneOf/anyOf no-match strings differ per language (six wordings plus the central `No oneOf/anyOf variant matched the JSON`); not harmonised (F396).
- **RF-22** `FixtureVocabularyLeakTest` guards vocabulary; `TESTCONTAINERS_RYUK_DISABLED=true` is set on the shared runtime container; the python test image is pinned `python:3.14-alpine` (the floating tag resolved to 3.15 without a `zstandard` wheel).
- **RF-23** go's golangci-lint is not a live gate (KT-1); kotlin `allWarningsAsErrors` stays deferred, dart's strict analyzer is armed.
- **RF-24** Authorize-URL and token-body encoders share the D52 set; swift excepted.

### Known-thin gates (KT, WONTFIX: do not over-strengthen)

- **KT-1** go `.golangci.yml` is bare `version: "2"`; generated files carry `Code generated ... DO NOT EDIT.` and are skipped. `go vet` and `staticcheck` are the live gates. Including generated files reports about 42 issues (33 errcheck, 9 govet) and is deferred. Never claim golangci-lint is non-vacuous for go.
- **KT-2** python ruff runs default rules (E4/E7/E9, F); mypy is non-strict.
- **KT-3** ruby Steep `:lib` is full strength; `:test` disables core diagnostics (no minitest sigs).
- **KT-4** swift `.swiftlint.yml` is committed but not run in CI (no Linux binary); `swift-format lint --strict` and `-warnings-as-errors` gate.
- **KT-5** Spec gates are not byte-identical to client Makefile targets (elixir plain `mix compile`, php `phpcbf || true`, swift `make analyse` run by no spec); test-run specs assert exit code, not count (F382).
- **KT-6** kotlin `allWarningsAsErrors` is deferred (R8-F4: a required-param null-check and a Kotlin 2.2 `equals()` warning need cleanup first); `-Xwarning-level=...:disabled` flags replaced the deprecated `-Xsuppress-warning`.

### Closed audit tail (TL): do not re-open without new evidence

- **TL-1** kotlin strict-key (`unevaluatedProperties: false`) on the production path: fixture-only, needs reflection routing.
- **TL-2** node drops null entries of a free-form map while java and kotlin keep them: fixture edge.
- **TL-3** multipart filename: go, ruby, php send real filenames via file-handle Options types; aligning the other nine to field name breaks the published ruby and php Options APIs.
- **TL-4** kotlin multipart test depth is structural (Ktor body is opaque).
- **TL-5** swift's duplicate multipart builder in `BaseApi` and `DefaultApiClient` stays (visibility and test-repoint tangle, no wire impact).
- **TL-6** date-time strictness: python is strict; swift is lenient through the `yyyy-MM-dd` fallback at `ObjectSerializer.swift:81` (swift maps `format: date` to String); the fallback is not forced and could go (F307).

### Client guardrails (G): regressions that bit real clients

A change passes `mvn verify` on the golden but can break a real client whose CI runs gates the golden does not. After changing a header, an import block, a suppression or a dependency list, regenerate a real client and run that client's own CI.

- **G1** `.openapi-generator/DEV-DEPENDENCIES` must list only dependencies of tests actually emitted to a client (`emitUnitTests()` set); `assertj-core`, `junit-jupiter-params` and `testcontainers` belong to golden-only `generateTests` tests and make `mvn dependency:analyze` fail in a client. Emit two lists or filter.
- **G2** python: keep `typing_extensions` and its pyproject declaration coupled; on 3.13 `ruff check --fix` strips an unused `Self` import and FawltDeps then fails.
- **G3** Removing a `// <auto-generated/>`-style marker turns analyzers on for generated code, which then depends on the suppression config (csharp `.editorconfig`); make sure every build path (a container whose `.dockerignore` hides it) sees that config.
- **G4** A container port the image does not `EXPOSE` is published only on a lenient daemon. The squid fixture publishes 3128 and 3129, chasm 4010 and 8443, but only 3128 and 4010 are exposed; the php bootstrap sets `ExposedPorts` for every requested port. Any new fixture port must be exposed there and verified on CI.
- **G5** Generated files may not disable a whole tool or rule set (`rubocop:disable all`, `@file:Suppress("detekt:all")`, blanket `ruff: noqa`, `phpcs:ignoreFile`); name the single rule in the shipped config with a one-line reason.
- A fix for generated code belongs here, never in a client; a dependency added to a generated test must be in every client's keep-listed manifest.

### API surface decisions (AS-D01..AS-D82)

`API_SURFACE.md` is generated by `etc/api-surface.py` and owns its 82 canonical decisions; do not copy them. The families a reviewer meets: internal helpers (`ValueSerializer`, `HeaderSelector`, parts of `ObjectSerializer`) are hidden or marked internal; `ApiError(statusCode, message, responseHeaders, responseBody, errorBody, cause)` and `fromResponse(statusCode, responseHeaders, responseBody)`; authenticator constructors end with optional `refreshUrl`/`clientAuthMethod`; builder setters take the property name, not `val`; composed schemas expose `actualInstance` and (anyOf) `matchedInstances`; `Servers.ALL`; per-operation server variants expose public variables; `additionalProperties` is a non-null map; discriminators are required; named inline enums `<Model><Prop>Enum`; initialisms are word-cased in camel/Pascal SDKs (`WithHttpInfo`, `clientId`), go and swift converging on word-casing; swift `ConfigurationError` cases match rust's. A mismatch is a defect unless an allow rule names the forcing language feature.

## Known limitations and out of scope

- Out of scope (S11): callbacks and webhooks (#53), XML bodies and `nodeType` (#70), Link Objects (#78), mutual TLS (#90), and the item closed as #88; the READMEs say so. OpenAPI 3.2 and 3.1 are epics #49 and #96; loading 3.2 documents is #50.
- S8 (parameter order) awaits owner confirmation.
- D68 (a model missing required properties cannot be built) carries a "needs user" flag in the ledger; java, node and rust have not been confirmed to reject before dispatch.
- D61 (numeric default literals) and D83 python/node precision are partly implemented; making python (`parse_float=Decimal`) and node (reviver using `context.source`) lossless is open (F075, F133, F202, F221, F224), as is rust, dart and elixir adopting a decimal package.
- D52: dart still diverges on the percent-encoding set and nine languages lack a lock test. D23: php `__toString` is not added; kotlin omits the transient bucket, go prints pointers for optional fields. D12 and D26 have the go, rust and dart exceptions above.
- Partly implemented ledger-tracked: D01/D12 (go and rust sync accessor), D04, D08, D38, D44, D49/D78, D72, D84-D91 residuals.
- Kotlin `allWarningsAsErrors` and go golangci-lint on generated code are deferred (KT-1, KT-6). rust compiles only on CI (amd64), so rust changes are CI-validated.
- Not harmonised: OAuth2 authenticator reprs (F285), per-model no-match strings (F396), operation-doc `\"` leakage (F370), oneOf equality in some languages (D24 rollout), the unused `getBySwatch` default question.
- Local golden regeneration is flaky (network-dependent Docker formatters; spurious php and swift drift): commit all twelve goldens current, never a subset.

## Build-breaking rules

- No `//` comments in generated php or csharp templates, and no HTML entity (named or numeric) in any golden; use block or `###` comments and `&#38;`/target-typed `new()` where a generator rule needs them (`FormattingSpec`).
- No generated file may disable a whole tool or rule set (G5); a rule needs a named single suppression with a reason in the shipped config.
- Every language's lint and type check runs in check mode over sources and tests, with no `|| true`; a golden that fails it fails the build.
- Structure and layout of generated files belong in Mustache templates; no `StringBuilder` file content, no `vendorExtensions.x-*` in templates; a mustache comment never contains a `{{tag}}`.
- `FixtureVocabularyLeakTest` fails on petstore vocabulary in templates or generated-test sample data; `*ReservedWordsSpec` enforces the reserved-word lists (java literals, kotlin `file`).
- `etc/api-surface.py check` must not get worse; an allow rule is added only for genuine idiom, with a `reason`.
- `.github` workflows are not edited from this repo; never pipe a maven build into `tail` (save to a file and grep it); regenerate goldens, never hand-edit.
- Goldens are committed all twelve current; a `ContainerLaunchException`, `404 exec` or exit 137 is infrastructure, re-run that language once before concluding.

## Verify

```bash
devbox run -- mvn test -Dtest='Generate*ClientTest,FixtureVocabularyLeakTest' -DforkCount=0 -Djacoco.skip=true -DfailIfNoTests=false   # regenerate all goldens
etc/ci-local.sh mvn -B test                    # regenerate plus unit tests, the one definition of green
python3 etc/api-surface.py check              # surface parity; must not get worse
etc/ci-local.sh check --quick                 # clean-tree gate before a push
etc/ci-local.sh check <Lang...>               # one or more languages end to end (about 2 minutes each)
devbox run -- mvn verify -Dgroups=<lang>      # java kotlin csharp php python ruby node dart go rust swift elixir
python3 etc/parity-findings.py count          # the single progress number
```

Before a Docker run stop leaked fixtures: `docker ps -q --filter ancestor=mridang/chasm:1.3.0 --filter ancestor=ubuntu/squid:6.10-24.10_beta | xargs -r docker stop`; start hand-run containers only with `docker run --rm --label parity.guard=1`. After a change that alters generated code, regenerate a real client (Zitadel: java, dotnet, php, python, ruby, node) and run its own CI gates.
