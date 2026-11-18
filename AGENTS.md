# Working in this repository

This repo generates **one SDK in twelve languages**. Java, Kotlin, C#, PHP, Python,
Ruby, Node, Dart, Go, Rust, Swift and Elixir. Everything here follows from that: a
change to one generator is a change to twelve, and a difference between two languages
is a defect until you can name the language feature that forces it.

The alignment rules for generated prose and examples are in the section "Template and generated-prose rules" below. This repository keeps agent instructions in `AGENTS.md` files only; there is no `CLAUDE.md`.

## Goal and how this documentation is organised

The goal is best-in-class developer experience, harmonised across all twelve SDKs: the same capabilities, the same behaviour on the wire and the same shape of the API (parameter order, how a client is created and configured, how options, errors, retries, timeouts, cancellation, transports and authentication are used), expressed the way each language's developers expect. Some languages use builders and some do not; that is idiom, but the order of concepts and the capabilities stay the same. When a language cannot do something natively, a small pure library or a version bump is acceptable if it makes the SDK cleaner. The public API shape is fixed now so that later features (OpenAPI 3.1 and 3.2 support, epics #96 and #49) are additive and never breaking. The reference points are the major generated SDKs (Stainless-generated OpenAI, Anthropic and Cloudflare, Speakeasy, Fern, Stripe, AWS and Azure).

Agent documentation lives in scoped `AGENTS.md` files, in one standard format (the human documentation is the README tree described in the next section):

| File | What it holds |
|---|---|
| this file | The goal, the layout, the commands and the workflow. Start here. |
| `src/main/java/.../generators/AGENTS.md` | The cross-language contract: the error and wire contracts, the settled cross-language decisions, the idiom matrix and the test layout. Each decision is written once, here. |
| `src/main/java/.../generators/<lang>/AGENTS.md` | One per language: how that language realises the shared contract, its idioms, its settled decisions (with reasons, so reviews do not reopen them), its limitations, its build-breaking rules and its verify commands. `src/main/resources/templates/<lang>/AGENTS.md` points to it. |

Read the root file, the shared file and the file of every language you touch before changing code. Settled decisions are not discussed again in review: if you disagree, open an issue with new evidence instead of changing the code. Open work is a GitHub issue in the milestone "Rebuild: harmonised SDKs"; a decision is written into the right `AGENTS.md` and never into an issue alone.


## README and docs are part of every change

Every change that touches behaviour, a type mapping, a supported version, an option or a generated file updates the human documentation in the same pull request. A change with no documentation update is not finished, exactly like a fix with no test. Check it before you commit, and name the pages you updated in the commit or the report.

The documentation tree is fixed. Do not put language-specific tables in the root `README.md`; link to them.

| Page | What it holds |
|---|---|
| `README.md` | What the generator is, how it works in a few paragraphs, the install and usage commands, and links to every page below. Short. |
| `docs/how-it-works.md` | The pipeline: spec normalisation rules, the shared generator, the templates, the goldens, the gates, and the standard format every generated SDK follows. |
| `docs/types.md` | The standard type table: every OpenAPI type and format, and the type each language uses for it, with the language version that type needs. |
| `docs/languages/<lang>.md` | One page per language (twelve): supported language versions and the support policy, the type mapping for that language, dependencies, the options it accepts, idiom notes, known limits. |
| `docs/decisions.md` | The settled cross-language decisions in prose for users, linking to the issues for deferred work. |
| `docs/faq.md` | Questions users and agents ask, each answered with the decision behind it, and the questions still under review. |
| `docs/comparisons/<tool>.md` | One page per competing generator (ogen, Orval, and later progenitor and swift-openapi-generator): what it does, what we do differently and why, and a verdict per feature. |

Rules for the pages: keep tables in the language page and the types page only, one source of truth each (a version floor is written once and the other pages link to it); regenerate or edit the language page whenever its manifest floor, type mapping or option list changes; state the floor from the build manifest, not from memory; never describe a feature the generated SDK does not have.
## Template and generated-prose rules

**Structure and layout of generated files belong in Mustache templates.** When the generator emits a file (per-operation Options classes, authenticators and so on), render a `.mustache` file under `src/main/resources/templates/<language>/` with a context map. Never build file content with `StringBuilder` or string concatenation in Java.

**Never depend on a spec-sourced vendor extension in a template.** An `x-*` key that came from the input document is fragile: it works only if every spec sets it. Use the native `CodegenOperation`, `CodegenParameter` or `CodegenProperty` field where one carries what you need (`{{#content}}`, `{{#servers}}`, `{{#cookieParams}}`, `{{style}}`, `{{isExplode}}`). Mustache is logic-less, so a value the generator has to compute (a decorator object, a format flag such as `isDuration`) is computed in Java and handed to the template through `vendorExtensions` under a name without an `x-` prefix, so that it reads as generator-owned. That is the sanctioned mechanism where no native field exists.

**The twelve SDKs are character-level aligned.** Generated prose (`README.md`, `SKILLS.md`) is written once and repeated verbatim in every language; only a token that names something the language calls differently may vary (`interface`, `protocol`, `trait`, `behaviour`; `property`, `field`, `attribute`; a type such as `byte[]`, `Vec<u8>`, `Data`). Sections appear in the same order with the same headings in all twelve, and a section is never added to one language alone.

**Every code example in generated prose compiles and runs as written.** Read the generated file it describes and copy the real signature; never write an example from memory of another language. Verify examples by extracting them into a scratch file and building them against the generated package. Defects that shipped before: a `RequestContext` parameter no generator emits, `url()` where the accessor is `getUrl` or `get_url`, a `Servers::SERVER_0` constant where the class declares `server0()`, error-handling examples calling suspending operations without `await`, a Maven coordinate with `LATEST`.

**A change to one generator applies to all twelve.** After fixing one, compare the other eleven for the same fault in a different shape; grepping for the literal you fixed misses a phantom parameter that is a typed argument in one language and an untyped one in another.

**The fixture cannot leak.** The petstore vocabulary reads as correct in that one output and wrong everywhere else. `FixtureVocabularyLeakTest` generates every SDK from a second spec that shares no vocabulary and fails on any fixture identifier. When it reports a word you think is legitimate, prove that it comes from the spec under generation before adding it to the allow-list.

**Integration tests run in Docker.** The specs for generated clients run inside containers; use `etc/ci-local.sh` (see Commands) and save long Maven output to a file before reading it.

## Working method

The twelve SDKs must be perfectly harmonised: the same public API shape, wire bytes, accepted and rejected inputs and errors, differing only by genuine language idiom. These rules hold for every session and every agent, however the work starts:

- **Fix in direct batches.** Fix 15 to 20 items at a time, grouped by bug class: edit the templates and codegen, regenerate with `etc/ci-local.sh mvn -B test`, run `etc/ci-local.sh check --quick` on a clean tree, make one commit per batch, push, and let CI decide. Start with the simple items and leave the cross-language ones (`objectserializer`, `defaultapiclient`, the auth classes) for last. Every fix needs a test or a golden that fails without it. A change found in one language is applied to all twelve; it is never fixed in one alone.
- **Never stop on a promise.** A turn ends on a finished result, a real blocker or a question, never on "starting now": the tool call goes in the same turn. Work in rounds of 30 to 50 edits, then run the touched languages' specs once (`etc/ci-local.sh check <Lang...>`, about 2 minutes each) and push once; CI takes about 30 minutes, so do not wait for it between rounds.
- **Review before the PR leaves draft.** After every few batches, run one independent review of the cumulative diff, with the `idiom-<lang>` agents (`.claude/agents`) for the languages touched. Harmonised means the same capabilities and behaviour, expressed the way that language's developers expect, never a pattern bolted on from another language.
- **Green before it lands.** `etc/ci-local.sh` is the one definition of green (CI runs the same steps). `.claude/hooks/commit-guard.sh` refuses commit messages with a trailer or attribution line and refuses pushes of commits that have not passed a local check (`--quick` is enough for the main session; CI runs the full suites).
- **Docker.** `.claude/hooks/docker-guard.sh` removes leaked test containers and holds new runs while Docker is full. Start hand-run containers only with `docker run --rm --label parity.guard=1`.
- **Nothing outside the repo.** Working state lives in the `AGENTS.md` files and in GitHub issues, never in a scratch directory or a session's memory.
- **No public API guard yet.** The tool that compared the twelve public surfaces was removed; until the Java specs of #122 exist, compare the public members of the twelve by hand before a change that adds, renames or reorders one.

The desktop app reads hooks and agents from the main checkout's `.claude/`, so after pulling changes to them copy `.claude/{settings.json,agents,hooks}` from the PR branch into the main checkout.

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

## Rules that live in the shared file

The error contract, the test layout, the linting rules, the guardrails that bit real clients, the forced divergences, the resolved cross-language fixes, the known-thin gates and the closed audit tail are written once in [the shared generator rules](src/main/java/io/github/mridang/codegen/generators/AGENTS.md). Do not copy them here. The short form:

- Every SDK raises the same error for the same failure; the error tree and its messages are the "error contract" there.
- Do not add test files: extend the test templates that exist, and make a fix visible to the petstore tests of all twelve languages.
- No generated file may disable a whole tool or rule set; a rule that cannot be met is named in the shipped config with a one-line reason.
- A difference between two languages is a defect until a named language feature forces it; the forced ones are listed there so that nobody aligns them away.

## The client SDKs

Six real SDKs are generated from this repo (Zitadel: java, dotnet, php, python, ruby,
node). In each, `.openapi-generator-ignore` lists the hand-written files the generator
must not overwrite; everything else is generator-owned and will be replaced.

- A fix for generated code belongs **here**, never in the client.
- Adding a dependency to a generated test means every client's keep-listed manifest
  needs it too, or its suite dies at collection.
- After a change lands: rebuild the image, regenerate all six, run their suites.
