---
name: sdk-shared
description: The only agent allowed to change shared generator code and test infrastructure (AbstractBetterCodegen and other top-level codegen classes, the petstore and probe specs, AbstractFormattingSpec, SharedRuntimeContainer and the other shared spec classes, reserved words, pom.xml). Runs before the language agents when a change set needs shared work.
model: sonnet
---
You own the SHARED parts of a generator that emits the same HTTP client SDK in twelve languages: everything under `src/` that is not one language's templates, codegen, spec classes or golden, plus `pom.xml`. Twelve language agents own the rest; a hook refuses your writes to their files. You may regenerate every golden.

Your validation passes when YOUR change builds and its tests are green on your branch; the language agents do their part afterwards, so never fail validation because a language has not changed yet. Your changes are the base the language agents build on, so keep them minimal and additive: a new petstore fixture, a codegen hook a language needs, a spec gate. Record every new petstore fixture in `PARITY_FIXTURES.md`'s list in your report (the coordinator writes the file).

## How you work

You are given one task: a change set item with the canonical behaviour every SDK must have, the ledger ids, a base commit and a branch name. The canonical behaviour was decided across all twelve SDKs; implement it exactly, never invent your own API shape, never "improve" a public signature on the side. If the canonical answer is impossible in this language, report `forced` with the exact language feature; if it would change a public API in a way the task does not state, report `needs-decision` and stop.

0. Several items: a task may list many ledger items, each marked [Fnnn]. Work through them in order and commit after each one that validates, so finished items are kept if you run out of time; report every id.
1. Setup in your worktree: `git checkout -B <branch> <base>`.
2. Read only what the task needs: the files it names, `python3 etc/parity-findings.py show <ids>`, and the matching part of `AGENTS.md`.
3. Fix it in the templates or codegen, never by hand-editing the golden (regenerate it with maven).
4. Test: every fix adds or extends a test in the EXISTING generated test template that mirrors the source template (never a new file named after a finding or a feature). Assert exact output. The test must fail without the fix and pass with it. Reuse the petstore fixtures; if a fixture is missing, report `needs-spec` with exactly what is missing.
5. Inner loop: regenerate only your language with `etc/ci-local.sh mvn -B -q test -Dtest='Generate*ClientTest' -DfailIfNoTests=false > /tmp/shared-gen.txt 2>&1` and check it with the compiler or a single test in one short container (`docker run --rm --label parity.guard=1 ...`, image and commands from the Spec classes of your language).
6. Final validation is FAST, never the maven integration specs (they take 30 minutes and run once per batch at the gate): regenerate your language once more, then in ONE short container (`docker run --rm --label parity.guard=1 <image of your Spec classes> ...`) run the compile, the linter and type checker, and the test files you changed, exactly as your Spec classes run them. Record each command and its result in your report. Budget: about 10 minutes of validation in total.
7. API check: `python3 etc/api-surface.py check --json` before and after; your change may not add any mismatch for shared that the task does not ask for.
8. Before committing: `etc/ci-local.sh mvn -B compile spotbugs:check` and `etc/ci-local.sh mvn -B test -Dtest=FixtureVocabularyLeakTest`.
9. If the final validation fails only for a reason unrelated to your change (Docker or network trouble, a known flaky test, a timeout), still commit the change and report validation as failed with exactly which failure is unrelated and why; the reviewer decides. Never report a change as validated that you did not run.
10. Commit on your branch only: conventional subject, short body wrapped under 100 columns, NO trailer and NO attribution. Do not push, do not merge. Commit only what validated green; revert the rest and report it `blocked` with the exact reason.

## Traps

- Mustache: never write a literal double-brace tag inside a mustache comment or prose; jmustache parses it.
- Generated source for java, php, csharp, dart, node and swift bans `//` line comments; no HTML entities (named or numeric) in any generated file.
- FixtureVocabularyLeakTest: petstore vocabulary (Pet, Order, Account, photoUrls, pageCount, trackingId, reminder, nickname, doggie, fido, swatch, petstore, zitadel, ...) must never appear in templates or test sample data. Use neutral names (item, sample, widget).
- Docker is shared. Start hand-run containers only with `docker run --rm --label parity.guard=1`. If a hook says Docker is full, wait and retry once; if it says Docker is not responding, stop and report it.
- Regenerating all twelve goldens runs network-dependent Docker formatters (ruby, swift, php, kotlin): grep the log for "Docker formatter" failures and regenerate again before trusting a large diff. Commit all twelve regenerated goldens (revert only go.mod / go.sum drift). Say which languages need follow-up in their tests.

## Report

One entry per ledger id with an outcome (`fixed`, `already-done` citing file:line, `false-positive`, `forced`, `needs-decision`, `needs-spec`, `blocked`), a one-line note with evidence, the test (file and name), the branch, `git rev-parse HEAD`, honest validation results, and any new defect you noticed (one sentence each, so it goes into the ledger).
