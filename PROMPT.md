# SDK Harmonisation Review — Reusable Prompt

This is the standing spec for the recurring **cross-SDK parity audit**. The
project emits 12 SDKs (`csharp dart elixir go java kotlin node php python ruby
rust swift`) from Mustache templates under `src/main/resources/templates/<lang>/`.
The goal is **12 perfectly harmonised SDKs** — identical behaviour, identical
public surface, identical docs and structure, modulo each language's idioms.

Run this audit by fanning out **one agent per dimension** (see table below).
Each agent reads the analogous file across all 12 SDKs and diffs *behaviour*,
not cosmetics. Synthesise the agents' findings into one ranked report.

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
   naming parity, README section parity, deprecation/limitation docs. Flag
   structural/doc divergence that a consumer reading two SDKs side by side
   would notice.

## How to run (audit pass)

1. Read `AGENT.md` (audit criterion + WONTFIX deny-list) end to end.
2. Launch the 9 dimension agents in parallel (one message, multiple tool uses).
3. Each agent reads its file across all 12 SDKs, diffs behaviour, returns
   findings per the output contract.
4. Synthesise: dedupe, drop WONTFIX, split DIVERGENCE vs UNIFORM-GAP, rank by
   severity. Produce a single report + a proposed fix order.
5. Nothing is fixed in the audit pass — it produces the triage list only
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
