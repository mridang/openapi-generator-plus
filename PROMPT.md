# SDK Harmonisation Review — Reusable Prompt

This is the standing spec for the recurring **cross-SDK parity audit**. The
project emits 12 SDKs (`csharp dart elixir go java kotlin node php python ruby
rust swift`) from Mustache templates under `src/main/resources/templates/<lang>/`.
The goal is **12 perfectly harmonised SDKs** — identical behaviour, identical
public surface, identical docs, identical test layout, and identical file
structure, modulo each language's idioms. Perfect harmonisation is the bar: if
two SDKs would look different to someone reading them side by side — in code,
in docs, or in tests — that is a defect to be raised.

Run this audit by fanning out **one agent per dimension** (see table below).
Each agent reads the analogous file across all 12 SDKs and diffs *behaviour*,
not cosmetics. Synthesise the agents' findings into one ranked report.

**High-yield hint (where the real bugs hide).** Empirically the caller-visible
correctness/security defects concentrate in **HTTP transport & request lifecycle
(dim 1)** and **serde — model serde (dim 2) + value serde (dim 3)**. On deeper
rounds, spend the most agents there: go past the happy path into edge inputs —
malformed/truncated bodies, unusual server responses, reserved/empty/null param
values, oneOf/anyOf/discriminator corners, charset/encoding, redirects, retries.
The structural dimensions (docs, tests, config) yield mostly low-severity parity
nits; transport + serde is where wire-format and data-loss bugs live.

---

## Structural invariants (always-on — every round checks these)

These hold across all 12 SDKs regardless of which dimension surfaces them. A
violation is a finding: **DIVERGENCE** if some SDKs comply and others don't;
**UNIFORM-GAP** if all 12 miss it.

1. **Test co-location — one source file, one test file.** Every non-trivial
   source file has a corresponding test file at the analogous path, named by the
   language's idiom:
   `a/b/c/foo.ts` → `a/b/c/foo.test.ts`; `Foo.java` → `FooTest.java`;
   `Foo.kt` → `FooTest.kt`; `foo.py` → `test_foo.py`;
   `foo.rb` → `foo_test.rb`/`spec/foo_spec.rb`; `foo.go` → `foo_test.go`;
   `Foo.cs` → `FooTest.cs`; `foo.dart` → `foo_test.dart`;
   `foo.ex` → `foo_test.exs`; `Foo.swift` → `FooTests.swift`;
   `foo.rs` → an inline `#[cfg(test)] mod tests` or a sibling under `tests/`.
   **One unit, one test file** — never a single aggregate/grab-bag file standing
   in for several units, and never a unit left with no test at all. If SDK X
   tests behaviour B inside `foo`'s own test file but SDK Y tests the same
   behaviour from an unrelated/aggregate file (or not at all), that is a
   DIVERGENCE. (Matches the maintainer's rule: *"one file, one unit test — `foo`
   has `foo.test`, no random files."*)

2. **Documentation parity — same docs, same shape, everywhere.** Every SDK ships
   the same documentation set with the same structure, modulo language idiom:
   the README has the same sections in the same order; `SKILLS.md` has the same
   subsections in the same order; every public type, method, parameter, and
   error carries an idiomatic doc comment (Javadoc / KDoc / XML-doc / docstring /
   YARD / rustdoc / `///` / `@doc` / TSDoc) that says the same thing; the same
   limitations/caveats are documented in the same place. A section, caveat, or
   doc-comment present in one SDK's docs but absent — or differently ordered — in
   another is a DIVERGENCE; a doc gap shared by all 12 is a UNIFORM-GAP.

3. **Idempotency.** Regenerating produces an empty `git status`. Any churn on a
   no-op regen is a finding.

---

## The one question every finding must answer

> If this difference flipped on a single SDK overnight, would a **caller** of
> that SDK notice — in wire format, data shape, error type, security boundary,
> or public API?

If no → it is not a finding. Drop it.

## Finding classification (tag every finding with exactly one)

| Tag | Meaning | Action |
|-----|---------|--------|
| **DIVERGENCE** | Some SDKs do A, others do B. This is the target. | Raise. Name which SDKs are on each side. |
| **UNIFORM-GAP** | All 12 behave the same, but all 12 are missing a feature / share a latent bug. | Raise **separately** — tag as `UNIFORM-GAP`. Not a harmonisation defect; a feature request. Do **not** mix into the DIVERGENCE list. |
| **WONTFIX** | Already triaged + deferred in `AGENT.md` → "Known unaddressed issues". | **Exclude entirely.** Do not re-report. |

A finding qualifies as **DIVERGENCE** only if **all three** hold (per
`AGENT.md` audit criterion):

1. **Divergence** — some of the 12 do A, others do B. "Everyone does X" and
   "no one does X" are consistency, not divergence (→ `UNIFORM-GAP`).
2. **Caller-visible** — shows up in wire format, data shape, error type, or
   security boundary the caller can observe. Differences the underlying HTTP
   library hides (HTTP/2 vs 1.1, chunked reassembly, pool sizing) do **not**
   count.
3. **Correctness or security impact** — silent data loss, wrong wire format,
   injection vector, type confusion. Cosmetic differences (header
   capitalisation, log wording, internal field names) do **not** count.

## Hard exclusions (do not report)

- Anything in `AGENT.md` → **"Known unaddressed issues (do not attempt to fix)"**
  and every line tagged `WONTFIX` there. Read that section first and treat it
  as the deny-list. Current WONTFIX themes include: numeric precision
  (int64 > 2^53, decimal), 307/308 multipart replay (Kotlin/C#/PHP),
  decompression-bomb cap, Accept-Encoding value divergence (W7), OAuth2 PKCE,
  OIDC signature validation, pagination iterator, logger/interceptor hooks,
  date/datetime wire normalisation (W3/W4/W5), additionalProperties data-loss,
  SOCKS proxies, HTTP/2, chunked encoding, 429 auto-retry, LICENSE/CHANGELOG
  emission, CI/hooks/devcontainer shipping, User-Agent default divergence,
  per-call cancellation, OAS 3.1 gaps (dependentRequired, webhooks/callbacks,
  numeric/string constraint validation), runtime version baselines.
- Pure idiom divergence that is caller-visible but *correct per language*
  (Elixir bang-vs-tuple, Python async-only, Go `(*T, error)`, Dart oneOf
  primitive filtering). If unsure whether something is "idiom" vs "defect",
  raise it as DIVERGENCE with a note and let triage decide.

## Output contract (every agent returns this)

For each finding:

- **id** — short slug (e.g. `oauth-refresh-empty-guard`)
- **tag** — `DIVERGENCE` | `UNIFORM-GAP`
- **dimension** — which area
- **title** — one line
- **sdks_a** / **behaviour_a** — the SDKs on side A and what they do
- **sdks_b** / **behaviour_b** — the SDKs on side B (omit for UNIFORM-GAP)
- **caller_impact** — what a caller observes (wire/shape/error/security)
- **severity** — `high` (data loss / security / wrong wire) | `medium`
  (behavioural surprise) | `low` (DX / docs)
- **evidence** — file path + line/snippet per side, per SDK
- **proposed_canonical** — which side should win and why (the harmonised target)
- **why_not_surfaced** — *root cause:* why this divergence/bug never bit in
  practice until now. (No exercising call path? Dormant feature with no fixture?
  Only triggers on an unusual server response or param shape? Masked by a
  downstream HTTP library? Cosmetic until a specific input hit it?) **Every
  finding must answer this** — a finding with no plausible reason it stayed
  hidden is probably mis-analysed.
- **why_not_caught** — *test-gap root cause:* why the existing tests did not
  catch it, and the exact test that would have. Name the missing assertion, the
  missing fixture, or the missing/aggregate test file (tie this to the
  test-co-location invariant — a missing `foo.test` is itself the gap). A finding
  whose fix lands without closing this gap **will regress**, so the fix is not
  "done" until a test that *would have caught it* exists and is red before the
  fix.

Sort findings: DIVERGENCE before UNIFORM-GAP; within each, severity desc.

## Dimensions & file scope (one agent each)

Java is the canonical reference layout; find the analogous file in each lang
(names differ by idiom: `.rs`, `.swift`, `.ex`, `lib/...`, `src/...`).

1. **HTTP transport & request lifecycle** — `default_api_client`, `base_api`,
   `client`, `transport_options`, `header_selector`. Request construction
   (method/URL/query/header assembly), redirect handling (3xx, 303→GET,
   cross-origin header stripping), timeouts, proxy (creds injection), TLS /
   caCert handling, compression/decompression, connection close/dispose.
2. **Model serde (object serializer)** — `object_serializer` + model deser
   dispatch in `models/model`. Discriminator routing, oneOf/anyOf match +
   no-match behaviour (throw vs silent), additionalProperties round-trip,
   null-on-required, unknown-property handling, strict vs lenient type
   coercion, const/enum validation.
3. **Value serde (param/primitive serializer)** — `value_serializer`. Query/
   path/header param styles (form/simple/spaceDelimited/pipeDelimited/deepObject),
   array + object param encoding, explode, date/date-time/byte/binary/decimal
   wire formatting, null/empty-value handling, reserved-char percent-encoding.
4. **Base model layer** — `models/model`. Struct/class shape, required vs
   optional, nullability, default values, enum representation, equality/hashing,
   immutability, deprecation annotation emission, field naming, prefixItems/
   tuple mapping, type:[T,null].
5. **OAuth2 & token manager** — `auth/oauth/*`. Token manager caching/expiry/
   refresh, all grant types (auth code, client credentials, implicit, password),
   OIDC, refresh-token empty guard, exchangeCode precondition guard, basic-auth
   vs body client-auth method, expires_in type tolerance + negative guard,
   invalidateAccessToken parity.
6. **Non-OAuth authenticators** — `auth/{basic,bearer,api_key,base,http_aware,
   scheme}_authenticator`, `api_key_location`. CRLF/header-injection guards,
   value encoding, api-key location (header/query/cookie), auth combination/
   precedence, non-ASCII header handling.
7. **Error model & exception hierarchy** — `errors/*`, `api_error`. The 8
   status subclasses + Client/Server split, status→exception mapping,
   error-body parsing, typed error body access, hierarchy shape.
8. **Configuration, servers & API operations** — `configuration`, `servers`,
   `server_configuration`, `server_variable`, `api/api`, `api/options`,
   `api_response`, `api_result`. Server/base-URL selection, server variables,
   default headers, operation method signatures, per-op Options objects,
   multipart/form handling, content-type negotiation, response wrapping, auth
   param threading.
9. **Docs, packaging & structural parity** — `readme`, `skills`, packaging
   (`pom`/`makefile`/`gitignore`/`editorconfig`/lint configs), file layout +
   naming parity, README section parity, deprecation/limitation docs. Enforce
   **Documentation parity** (structural invariant #2): same README sections in
   the same order, same `SKILLS.md` subsections, and an idiomatic doc comment on
   every public type/method/param/error saying the same thing across all 12.
   Flag any structural/doc divergence a consumer reading two SDKs side by side
   would notice.
10. **Test parity & co-location** — the `test/` tree of every SDK. Enforce
    **Test co-location** (structural invariant #1): every source file has its
    own idiomatically-named test file, one unit per test file, no aggregate
    grab-bags, no untested units. Diff *what is asserted*: if SDK X has a test
    for behaviour B (BOM strip, secret redaction, required-param rejection, …)
    and SDK Y has no equivalent assertion — or buries it in an unrelated file —
    that is a DIVERGENCE. A behaviour all 12 leave untested is a UNIFORM-GAP.
    This dimension is also how `why_not_caught` gets answered for every other
    dimension's findings.

## How to run (audit pass)

1. Read `AGENT.md` (audit criterion + WONTFIX deny-list) end to end. The WONTFIX
   list is a hard deny-list: anything on it is **excluded entirely** — never
   re-reported, not even as "still present".
2. Launch the dimension agents in parallel (one message, multiple tool uses) —
   all 10 dimensions above, including the structural invariants (test
   co-location, documentation parity, idempotency).
3. Each agent reads its file across all 12 SDKs, diffs behaviour, returns
   findings per the output contract — **including `why_not_surfaced` and
   `why_not_caught` for every finding** (a finding without both is incomplete).
4. Synthesise: dedupe, **drop every WONTFIX**, split DIVERGENCE vs UNIFORM-GAP,
   rank by severity. Produce a single report + a proposed fix order.
5. Run **multiple rounds** until a round surfaces nothing new (loop-until-dry):
   a fresh fan-out can find what the previous pass's framing missed. Collate all
   rounds' findings into one deduped list.
6. Nothing is fixed in the audit pass — it produces the triage list only
   (written to `AUDIT.md`).

## Fix loop (after triage — automatable)

Once findings are triaged FIX/WONTFIX, fix them with this loop. It is organised
**by language**, not by finding, and is parallel-safe because each language's
templates live in a disjoint `templates/<lang>/` dir.

**Per fix, the order is non-negotiable: TEST FIRST, then code.**

1. **Add the test first.** Extend that language's generated test template
   (`templates/<lang>/test/...`) with an assertion that encodes the *corrected*
   behaviour from the finding's `proposed_canonical`. The test must fail against
   the current template (red) and pass after the fix (green).
2. **Then make the change** in the language's `.mustache` template (or, if the
   finding's canonical lives in shared codegen, in `Better<Lang>Codegen.java` /
   `AbstractBetterCodegen.java` — but a parallel per-language agent must NOT edit
   shared files; it reports them as a `shared_dep` for central handling).
3. **Regenerate only that one language**, never the whole suite.
4. **Run only that language's spec** — never `mvn verify` (it runs all 12 and
   takes ~20 min). Per-language is the unit of verification:
   `devbox run -- mvn test -Dtest=Generate<Lang>ClientTest` to regen, then the
   language's integration spec. Red → fix → rerun that one language only.
5. **Green → commit** (one commit per language or per coherent finding group),
   then move to the next language.

**Cross-cutting findings** (same canonical behaviour across many SDKs — e.g.
redirect-refusal-throws, empty-body-throws, form-field routing): each language
implements the *same* agreed `proposed_canonical` from `AUDIT.md`, so per-language
agents stay coherent without coordinating, as long as they read the canonical
target verbatim.

**MANDATORY — the identical test goes in ALL 12, not just the divergent ones.**
A finding usually names a subset of SDKs that have the bug, but the regression-
guard test must be added to **every** language's suite — the same scenario, the
same assertion, the same input/expected pair, only translated to each language's
idiom. Rationale: if only the currently-broken SDKs get the test, the *other*
SDKs can silently regress into the same bug on a future change and nothing will
catch it. Lock the behaviour across the whole fleet so the bug **cannot resurface
in any language**. Concretely, for each fixed finding:
1. Write the canonical test once (scenario + exact input + exact expected output)
   in `AUDIT.md` next to the finding.
2. Add that same test to all 12 `templates/<lang>/test/...` suites, in the
   co-located test file for the unit under test (per Structural Invariant #1).
3. It must be **red before** the fix in the SDKs that have the bug, and **green**
   (already-correct) in the SDKs that don't — both states prove the assertion is
   real, not vacuous.
4. A finding is not "done" until all 12 carry the test and all 12 are green.
This is the fleet-wide expression of `AGENT.md`'s "TEST-COUNT PARITY — every SDK
has every test": parity is enforced at fix time, every time, no exceptions.

**Shared prerequisites handled centrally before/after the parallel wave:**
- **Fixtures.** Dormant findings (`🟡 needs fixture`) require a petstore-spec
  addition (an operation/param/field that exercises the path) so the test can
  catch the regression. Add these to the shared spec centrally first, then the
  per-language tests can assert against them.
- **Shared codegen** (`AbstractBetterCodegen.java`) and **`AGENT.md` WONTFIX
  documentation** are edited centrally, never by a parallel per-language agent.

**Parallelism rule:** editing fans out (one agent per language, disjoint dirs);
**verification is sequential per language** (docker + maven contention makes
parallel spec runs unreliable). Never run the full verify.
