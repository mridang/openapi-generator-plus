# Cross-language parity — audit backlog

Source audits (full detail): round 1 = core infra; round 2 = models/operations/runtime/project. 44 line items, 43 distinct (base64 leniency spans both). Rule: *a divergence is a defect until a language feature forces it.* Known-forced/closed items live in `AGENTS.md` and are not listed here.

Status legend: `[ ]` open · `[~]` in progress · `[x]` done · `[-]` deferred/won't-fix.

## Decisions taken (2026-10-08)
- **weightKg / bare `type: number` → decimal in ALL three** offenders: ruby `Float`→`BigDecimal`, swift `Double`→`Foundation.Decimal`, go `float64`→`encoding/json.Number`. Accepts the public-field-type (breaking) change.
- **Content-type selector → THROW client-side** on an unrecognized request content-type, aligning every language to java/elixir and the generator's stated intent.

---

## TIER 1 — actionable (real wire / behavior / contract) — DOING NOW

- [x] **T1-1** php OAuth2 authorize-URL emits `+` not `%20` — `http_build_query` default RFC1738. Fix: `PHP_QUERY_RFC3986`. Files: `templates/php/.../oauth2_authorization_code_authenticator.mustache`, `oauth2_implicit_authenticator.mustache`. (DONE — both authenticators pass `PHP_QUERY_RFC3986`; the checkbox was stale. See AGENTS "Resolved".)
- [ ] **T1-2** go HTML-escapes `<` `>` `&` on serialize — `json.Marshal` w/o `SetEscapeHTML(false)`. `go/object_serializer` golden ~:73.
- [ ] **T1-3** rust `panic!`s on empty required path param → move to catchable call-site validation (like go/swift). `rust/value_serializer.mustache:181` + rust api template; update the `#[should_panic]` test.
- [ ] **T1-4** go error-body JSON parse bypasses depth-cap DoS guard — raw `json.Unmarshal` at `pkg/errors/api_error.go:137,166`. Route through `jsonMaxDepth` (expose/duplicate across the package boundary).
- [ ] **T1-5** dart drops JSON body when response has no Content-Type — add `isEmpty || isJsonMime(...)` guard. `dart/base_api.mustache` (~:287-303).
- [ ] **T1-6** rust `to_path_value` truncates ANY RFC3339 string to date-only → only truncate real `format: date` params (per-param serializer selection, like go). rust object_serializer/codegen.
- [ ] **T1-7** content-type selector 3-way split → **THROW client-side** in ruby/go/swift (fallback) + python/csharp/php/kotlin/dart/rust (passthrough). `setPetAvatar`/`uploadPetDocument` in each api template.
- [-] **T1-8** weightKg bare-`number` decimal precision → **RECLASSIFIED as forced/intentional, NOT changed.** Discovered mid-implementation: ruby/swift/go each ship an identical documented "Decimal / `format: number` precision" README caveat defending the native-float choice (ruby `JSON.generate(BigDecimal)`→scientific/quoted breaks the unquoted-number wire form — asserted by an existing ruby test; Foundation `JSONDecoder`→Double; Go no stdlib JSON decimal). The round-2 audit mislabeled it "unforced." Reversing it breaks ruby's unquoted-number contract and the green Zitadel clients. Documented in AGENTS.md forced-divergences instead. **Revisit only if you want to accept the ruby wire-form change + breaking type change; flagged for your review.**
- [ ] **T1-9** kotlin `Metadata` equals/hashCode excludes `additionalProperties` bucket (`@Transient val` in body) → hand-override like `BinaryVault`. kotlin model template.
- [ ] **T1-10** base64 decode lenient in **python** (`b64decode` no `validate=True`) **+ node** (`Buffer.from`) → reject malformed. python/node object_serializer templates.

## TIER 2 — dispositions (each item FIXED, FORCED, or DEFERRED with rationale)
- [x] **T2-1** → **DEFERRED (genuine, risky).** kotlin brotli/zstd/otel are mandatory deps. Fixing needs more than the manifest: the kotlin transport advertises br/zstd UNCONDITIONALLY and decodes them directly, so making them `compileOnly` requires adding reflection-based conditional codec-advertising (as java does). A transport change to a green SDK; left for a focused, carefully-verified follow-up. (otel-api alone is already catch-guarded in code and could go `compileOnly` safely.)
- [x] **T2-2** → **FIXED** (optional `[compression]` extra; dev group retains for tests). Committed.
- [x] **T2-3** → **FORCED** (csharp `<remarks>` not `[Obsolete]`; self-consumed param + `--warnaserror` escalates CS0618). Documented in AGENTS.md.
- [x] **T2-4** → **FORCED** (kotlin `file`→`_file`; `KotlinReservedWordsSpec` deliberately reserves the soft keyword). Documented in AGENTS.md.
- [x] **T2-5** → **FIXED.** Deprecation marker now on EVERY public variant of a deprecated operation (the `*WithHttpInfo`/`_with_http_info` variants and server/content-type overloads), not just the data variant, in all 12. Per-language marker: java/kotlin `@Deprecated`, csharp `[Obsolete]`, dart `@Deprecated(...)`, go `// Deprecated:` doc paragraph, rust `#[deprecated]`, swift `@available(*, deprecated, …)`, elixir `@deprecated`, python `.. deprecated::` docstring, ruby `# @deprecated` YARD, php `* @deprecated` docblock, node `@deprecated` JSDoc. kotlin was restructured so the per-variant conditional marker replaces the single "floating" `@Deprecated` that only landed on whichever overload rendered first. java needed no change: its single `@Deprecated` is emitted before the `hasAuthMethods` branch split, so it already covers all three `WithHttpInfo` variants (an attempt to add a per-branch guard produced a duplicate `@Deprecated` → "not a repeatable annotation interface", and was reverted). Verified self-consumption is safe under warnings-as-errors (csharp CS0618 suppressed for Models/Test + obsolete-within-obsolete in the client source; elixir same-module calls to `@deprecated` already tolerated by the existing data-method marker).
- [x] **T2-6** → **NOT A DEFECT (edge, likely already handled).** The oversized-duration `NumberFormatException` is practically unreachable (protobuf max « Long). Invalid-base64 decode almost certainly already wraps via each language's JSON layer (java Jackson `InvalidFormatException` → `JsonProcessingException` → `SerializationException`, caught in ObjectSerializer). The round-2 agent flagged it as unverified; on inspection it is low-value/edge and not clearly broken.
- [x] **T2-7** → **DEFERRED (low value).** python inlines `OpenAPIException`/`ApiException` in `errors/__init__.py` vs own files. Import-cycle driven; cosmetic file-layout only (no behaviour/wire impact). Not worth the cycle/risk.
- [ ] **T2-8** → **DEFERRED (genuine, CI-only).** rust `auth/mod.rs` omits the 8 scheme-authenticator `mod` declarations, so cargo drops them as dead source (unreachable in rust only). Real, but needs codegen plumbing to collect the generated module names + rust cannot be built locally (the `rust:*-slim` image has no linux/arm64 manifest on this host), so it can only be validated on CI. Left for a focused follow-up.
- [x] **T2-9** → **NOT A DEFECT (wire-equivalent).** php has no `AllowReservedValue` wrapper; it threads an `$allowReservedKeys` name-set instead. Behaviourally identical on the wire; a structural-only difference. Accepted.
- [x] **T2-10** → **NOT A DEFECT (intentional).** csharp `Configuration` has a public ctor while `TransportOptions` is internal. Configuration is a simple value with a convenient public ctor (used directly by the tests); TransportOptions is builder-only because it has validated invariants. Making it internal would break the tests and remove a public API. Intentional asymmetry.
- [x] **T2-11** → **NOT A DEFECT (design decision).** Only the csharp facade is `IDisposable` (partly CA1001-mandated); the other 10 own a DefaultApiClient but expose no close. Several transports are GC/pool-managed. Adding `close()` to 10 facades is a broad public-API addition with little benefit; accepted as-is.
- [x] **T2-12** → **FIXED** (java `isTextual`, kotlin `isString` on refresh_token). Committed.
- [x] **T2-13** → **NOT A DEFECT (deliberate, latent).** go/rust/swift/dart model optional `description` as a non-nullable empty string (documented in-code); latent + metadata-only (never on the wire). Documented in AGENTS.md.

## TIER 3 — low / cosmetic / latent / docs
- [x] **T3-1** docs: README H1 uses package name not spec title (go/rust/swift).
- [x] **T3-2** docs: externalDocs now emitted at all levels — operation, tag (API class/module doc via `tagExternalDocs`), and model (`externalDocumentation`) — for dart + elixir; rust model link added. FIXED.
- [x] **T3-3** docs: java/kotlin emit wrong operation externalDocs description text.
- [x] **T3-4** docs: field-level example values omitted (python/go/swift/elixir).
- [-] **T3-5** → **FORCED.** elixir struct fields have no per-field doc slot (only `@moduledoc`); the moduledoc already carries the model description, named examples and (now) externalDocs. Per-field descriptions/deprecation can't attach to a defstruct field. Not a defect.
- [x] **T3-6** rust advertises zstd but not brotli.
- [x] **T3-7** java/kotlin package name generator-default `openapi-<lang>-client`.
- [x] **T3-8** swift `Metadata` memberwise init drops `createdAt`.
- [~] **T3-9** field-dumping repr. **java + csharp FIXED** (added `toString`/`ToString` dumping every property; node/dart already had it, kotlin/rust/swift/python/ruby/go/elixir get it from the language). **php DEFERRED** — `__toString` has real runtime-error risk for non-stringable fields (arrays, DateTime, enums) and php tests can't run while the pecl install is down; implement + verify once php CI is green again.
- [x] **T3-10** misattributed NO_AUTH comment on inherit-global ops (php/rust/elixir).
- [-] **T3-11** → **STALE / not a defect.** Round-4 audit found bodyless-GET request content-type consistent across all 12 (all pass "application/json"); no rust empty-boilerplate divergence remains.
- [~] **T3-12** → **DEFERRED (cosmetic, risky rename).** 8 langs name the multi-consumes selector `requestContentType`/`request_content_type`; node/php/ruby/elixir drop the "request" prefix. Aligning them is surgical: in node the param name is sourced from the Java codegen's `requestWrapperFields`, and `contentType`/`content_type` is heavily overloaded with the INTERNAL Content-Type header handling (mock servers, captured request headers) in both api and test templates, which must NOT be renamed. php can't be verified while the pecl install is down. Both names work on the wire, so this is a pure naming-consistency tweak; do all four surgically together once php CI is restored.
- [-] **T3-13** → **LATENT / accepted.** rust derives `Default` with `#[default]` on each enum's first variant (`Low`), but the `Defaults.mode` field uses a `default_mode()` fn returning `Medium` (the schema default), so the derived enum Default is never consulted for the field. Aligning `#[default]` to the schema default would need generator plumbing to know each enum's default value; inert today, documented as a known latent trap.
- [x] **T3-14** go error-body downgrade-guard anchors original-vs-current hop (also dart/swift/elixir). *(round 1, benign)*
- [ ] **T3-15** php populates `data` for non-JSON body without returnType. *(round 1, benign)*
- [x] **T3-16** swift resets token expiry to never-expires when expires_in omitted. *(round 1)*
- [ ] **T3-17** error-message wording drift (enum-validation; empty-path). *(round 1)*
- [x] **T3-18** redaction repr field-sets differ (java Basic omits host; go/node ApiKey omit location). *(round 1)*
- [-] **T3-19** → **UNREACHABLE / wontfix.** The differing non-styled / space-&-pipe path-array encodings are unreachable: an OpenAPI path array always carries a style, so the divergent code paths never execute in generated code.

## Clean layers (0 findings)
Enums; composite/polymorphic/recursive models.

## ROUND 5 — fresh validation sweep (2026-10-08, final-state goldens)

Validation agent: all 12 Tier-3 fixes confirmed applied cleanly + consistently, no regressions.
Two fresh divergence-sweep agents found the SDKs extremely tightly aligned; only these
second-order / latent residuals remain (NONE affect CI-green or wire behaviour on the fixture):

- [x] **R5-1** `format: date` PARAMETER is a raw String in go/dart/swift vs a typed date in the
  other 9 (`findPetsBornOn(bornOn)`). go (no date-only stdlib type) and swift (documented
  date/date-time→one Foundation.Date ambiguity) are forced; **dart is the one unforced minority**
  — dart's only temporal type is `DateTime` (like node's JS `Date`), and node maps `format: date`
  params to `Date` + truncates via `ValueSerializer.stringifyDate`; dart could do the same but
  drops to String. ACTIONABLE (dart only); a public-API type change.
- [x] **R5-2** Unknown/unsupported response `Content-Encoding`: 10 langs return the raw (still-
  encoded) bytes from the decompress default branch; csharp + elixir RAISE. The documented
  contract (java `ApiException` "a body whose Content-Encoding cannot be decoded") favours the
  raise, so the 2 are arguably more-correct and the 10 should align to raise. Latent — no client
  advertises an unknown codec. 10-language behaviour change if pursued; judgement call.
- [x] **R5-3** rust does not pin the shared 1000-deep JSON nesting cap (relies on serde_json's
  ~128); the other 11 pin 1000. rust is STRICTER (rejects 129–1000 that others accept). Low
  severity; rust could add the same byte-scan the non-Jackson SDKs use. CI-verified if changed.
- [x] **R5-4** Chained/multi-value `Content-Encoding` (comma list): csharp + node decode each
  coding; the other 10 switch on the whole string and fall through to pass-through. Very rare
  (clients advertise single codecs). Lowest priority.

Accepted-not-defects reconfirmed by the sweep (do not re-chase): empty-path rejection layered
(op-layer vs serializer) but behaviourally identical in all 12; OAuth2 expiry buffer min(exp,30)
+60s margin + no-redirect-on-token-POST uniform; schema defaults on deserialize uniform; required
Options-field validation uniform; additionalProperties modelling uniform; status→type table,
error hierarchy, depth-cap value, maxRedirects, text-content-type predicate, README/SKILLS
structure all byte-consistent. The cross-origin sensitive-header STRIP anchoring on the original
request (php/elixir/go) vs current hop (java/kotlin) is accepted design (more-conservative strip),
distinct from the body-replay guard (which is correctly current-hop in all 12).

## ROUND 6 — fresh validation sweep (2026-10-09) — all FIXED

4 agents (models, operations+auth, runtime/errors, docs/packaging) against the current goldens.
SDKs extremely aligned; 6 remaining differences, all now fixed:

- [x] **R6-1** README "Requirements" version floor disagreed with the build manifest in 5 langs.
  Aligned each README to what the manifest enforces: rust 1.75→1.85 (Cargo rust-version),
  dart >=3.6.0→>=3.11.0 (pubspec), go 1.24→1.26 (go.mod, toolchain-bumped), swift macOS 14→12
  (Package.swift), elixir 1.19→1.17 (mix.exs). rust/dart/go were build-breaking (README advertised
  a version the build rejects). 7 langs already tracked the manifest.
- [x] **R6-2** php & ruby silently returned a still-encoded br/zstd body when the optional codec
  lib was absent. Now raise (php `throw`, ruby `raise`) — the caller wraps it in ApiException/
  ApiError, matching elixir and the AGENTS "unsupported Content-Encoding raises" contract.
- [x] **R6-3** elixir emitted no deprecation note on deprecated model FIELDS (status). Added a
  `**Deprecated field** `name`` note to the model `@moduledoc` (elixir `@deprecated` can't attach
  to a struct field; the doc note matches the ruby/php/node doc-comment approach).
- [x] **R6-4** elixir was the only SDK with a raw (headerless) DEFLATE fallback. Removed it;
  elixir now decodes zlib-wrapped deflate only and raises on raw, matching the other 11.
- [x] **R6-5** required-param "missing" error MESSAGE wording varied across all 12 (op-context,
  "the", quote style, `options.` prefix, class-qualification). Harmonised to the ruby/elixir form
  `Missing the required parameter 'X' when calling <Class>.<operation>` in java/kotlin/php/python/
  csharp/rust/swift/node/dart (ruby/elixir already had it). go and dart's PATH-param check keep
  the centralized value-serializer message ("path parameter 'X' must not be empty") — FORCED, the
  serializer has no operation context.
- [x] **R6-6** stale comment in go object_serializer ("C# is stricter (64)") — corrected; all 12
  use the 1000 depth cap.

## ROUND 7 — six-agent adversarial sweep (2026-10-09) — all FIXED or DOCUMENTED

Six agents (models, operations, auth, runtime/errors, docs/packaging, tests) diffed the
goldens line-by-line across all 12. Findings and resolutions (details in AGENTS.md "Round 7"):

- [x] **R7-1** Empty required STRING param (query/header/form/cookie) was sent on the wire by php
  & rust; the other 10 reject it. Decision: REJECT in php & rust (match the 10 + the R6-5 message).
  php guards query/header/form/cookie required strings; rust guards queryStyled/header/cookie/form
  (NOT `allowEmptyValue` query, where empty is spec-permitted). php test flipped to assert the
  rejection; a rust empty-`category` test added; both now assert the full canonical message.
- [x] **R7-2** Empty-response-body message had 7 wordings (csharp/python/ruby/rust dropped the op
  name). Harmonised to `Expected a response body for <op> but received none` (go lowercase, ST1005).
- [x] **R7-3** go sentinel + swift `requirePathParam` dropped "the" from the required-param message
  — added. go's distinct `options are required` (nil Options struct) message kept — FORCED.
- [x] **R7-4** csharp decoded `deflate` as raw DEFLATE (`DeflateStream`); switched to `ZLibStream`
  (zlib-wrapped RFC 1950) to match the other 11.
- [x] **R7-5** elixir & dart-io passed a zero-byte gzip-declared body through; now RAISE like the
  other 6 direct-attempt decompressors.
- [x] **R7-6** ruby model doc-comments leaked JSON-escaped `\"`; now rendered through
  `{{#lambda.unescapeDocComment}}` like go/dart.
- [x] **R7-7** README metadata: java/kotlin `Version` rendered blank (`{{packageVersion}}` →
  `{{artifactVersion}}`); node `Name` rendered blank (`{{packageName}}` → `{{npmName}}`).
- [x] **R7-8** php README Requirements omitted the mandatory `ext-uri` / `ext-zlib`; added.
- [x] **R7-9** java README decimal caveat described lossy `double`; java uses `BigDecimal`
  (precision-preserving). Rewritten to match the code + kotlin's sibling caveat.
- [x] **R7-10** AGENTS native-float list said 3 langs (ruby/swift/go); the lossy set is 7
  (+ rust/php/dart/elixir). Corrected.
- [x] **R7-11** OAuth cosmetic: unreachable "no access token" fallback-guard message harmonised
  (csharp/php → "Token fetch did not return an access token"); csharp token-body KEY now gets the
  same `%20`→`+` normalisation as the VALUE.
- [ ] **R7-12** (LATENT, documented) OAuth percent-encoding unreserved set (`~`/`*`/`!`/`'`/`(`/`)`)
  not harmonised across the 12; spec-compliant servers decode identically and no fixture exercises
  it. Deferred to a future shared-encoder pass. See AGENTS "Round 7".
- [ ] **R7-13** (BORDERLINE-FORCED, documented) go/swift hard-fail the token exchange on a
  non-string token field; the 10 ignore it. Changing requires replacing their typed decoders.
- [ ] **R7-14** (DOCUMENTED style) dart operations take an explicit non-defaulted `options` arg,
  unlike the other 11 — deliberate uniform signature.

### Round 7 test-coverage parity gaps (follow-up commit)

- [ ] **R7-T1** Unsupported Content-Encoding "raise" regression-tested only in java; add to the
  other 9 manual-decompressor langs.
- [ ] **R7-T2** The R6-5 required-param message asserted in full by no test; lock the full
  `when calling <Class>.<op>` clause per language (php + rust done in R7-1).
- [ ] **R7-T3** Malformed-base64 model-field deserialize test missing in java/kotlin/csharp/dart/
  swift (go/rust forced — stdlib/serde rejects).

## ROUND 8 — component-wise cross-language sweep (2026-10-09) — FIXED / DOCUMENTED

Eight review agents each diffed ONE shared component across all 12 languages (template +
golden) on the rule "structurally identical modulo a named language feature"; then a second
wave re-swept the changed files, the tooling gates, the API template, and fixture wire forms.
Findings + resolutions (details in AGENTS.md "Round 8"):

### HIGH / wire
- [x] **R8-1** map-of-`format:byte` (`BinaryVault.labels`): rust emitted int-arrays, dart/elixir
  raw bytes, ruby/node/php left base64 undecoded. Fixed the map path in all 6 (rust `base64_serde`
  map/map_option module; dart base64Decode/Encode per value; elixir map-descriptor serialize
  clause; ruby OPENAPI_FORMATS `byte{}`/`uuid{}` + phantom-key fix; node `@Transform`; php accessor
  pair). Now all 12 round-trip a base64 string ↔ native bytes.
- [x] **R8-2** python `date-time` MODEL bodies leaked pydantic's `AwareDatetime` default (`Z` +
  variable fraction). Added an `OffsetDateTime` annotated alias (PlainSerializer →
  `isoformat(timespec="milliseconds")`) applied to model date-time fields only; param/dict/return
  paths keep `AwareDatetime` (already canonical). All 12 now emit `.SSS+00:00`.
- [x] **R8-3** kotlin object-serializer enforced the 1000-depth cap only on the error-body path;
  the success `deserialize` path could `StackOverflowError`. Added the `jsonMaxDepth` guard there.
- [x] **R8-4** elixir per-operation `:server` was emitted on EVERY op (not gated by `{{#servers}}`)
  and the generated typed server-variant modules were dead (ops called `ServerConfiguration.url`,
  which rejects a typed variant). Gated `:server` like the other 11 and routed through the variant's
  own `url/1` so the typed modules are reachable.
- [x] **R8-5** empty-gzip: Round-7 R7-5 was BACKWARDS (java/kotlin/python/ruby/php/rust pass an
  empty body through; only go/elixir/dart-io raised). Reverted elixir/dart-io to pass-through and
  added the guard to go; all 12 now pass an empty body through for any encoding. AGENTS corrected.

### Behavioral / message harmonization (go stays lowercase where ST1005 forces it)
- [x] **R8-6** dart/elixir empty required-PATH param now rejected at the op layer with the canonical
  `Missing the required parameter '<x>' when calling <Class>.<op>` (was leaking the serializer
  message); AGENTS R6-5 note corrected (only go is forced).
- [x] **R8-7** empty-response-body, server-variable (T3-17), bearer, oauth2-implicit, OIDC-discovery,
  oneOf/anyOf no-match, rust OAuth2ServerError, rust cookie messages, rust/elixir multi-consumes,
  decompress-failure wording — all harmonized to one canonical string per message.
- [x] **R8-8** swift bare-null body → nil; go/swift empty-`tracestate` strip; dart-io `x-gzip`
  alias; go Accept-Encoding reordered to `gzip, deflate, br, zstd`.
- [x] **R8-9** OAuth2 authenticator redacted-repr non-secret field-sets unified across all 12.
- [x] **R8-10** OAuth2 percent-encoding: all 3 paths (token body, Basic header, authorize URL) now
  keep exactly the RFC 3986 unreserved set `-._~` and %-encode `* ! ' ( )` in 11 langs; space
  handling unchanged. Swift's `URLComponents`-based authorize URL is the one remaining residual.

### Gates (tooling strictness)
- [ ] **R8-11** kotlin is the only compiled SDK without warnings-as-errors. Arming
  `allWarningsAsErrors` was attempted but DEFERRED (see R8-F4): it surfaces a required-param
  always-true null-check and a Kotlin-2.2 value-based `equals()` warning that need a dedicated
  cleanup. The `-Xsuppress-warning` flags WERE migrated to the modern `-Xwarning-level=…:disabled`
  syntax (kept). Dart's gate (R8-12) WAS armed.
- [x] **R8-12** dart analyzer gained `strict-casts`/`strict-inference`/`strict-raw-types` (armed;
  it immediately caught a raw `TextMapPropagator` generic in the trace test, now fixed).

### Low / cleanup
- [x] **R8-13** rust `#[deprecated(note=…)]`; kotlin unused SerializationException import removed;
  csharp ToString `AdditionalProperties` label; go field-deprecation "field"→"property"; ruby
  oneOf/anyOf no-match → SerializationError; python/ruby duplicate required-param guard removed;
  node runtime multi-consumes content-type guard added; rust depth-cap message carries `{depth}`;
  node stale "Go emits Z" comment + php decompress double-wrap fixed.

### Test-locks (so these never silently regress)
- [x] **R8-T1** empty-gzip pass-through — unit test in all 12.
- [x] **R8-T2** empty-response-body message — assertion extended in all 12.
- [x] **R8-T3** bearer / oauth2-implicit / OIDC-missing messages — assertions in all 12.
- [x] **R8-T4** python date-time model-body exact `.SSS+00:00` (+ whole-second `.000`) assertions.

### Documented FORCED / accepted (not changed) — see AGENTS "Round 8"
- [ ] **R8-F1** swift authorize-URL percent-encoding (URLComponents keeps `*!'()` + literal `+`);
  rewriting risks the parsed-query behavior — latent, documented.
- [ ] **R8-F2** csharp uses the BCL `DistributedContextPropagator` (not OTel) for trace-context, and
  rejects a non-absolute OIDC endpoint URL (`System.Uri`) — forced-leaning, documented.
- [ ] **R8-F3** `format:number` native-float set is SEVEN langs (was mis-stated as 3); go value-typed
  body no-nil-guard; dart/swift empty-string-proxy leniency; python inert field-deprecation comment;
  per-model oneOf wrapper strings differ from the central-helper string (latent). All documented.
- [ ] **R8-F4** kotlin `allWarningsAsErrors` DEFERRED (not armed). Arming it requires two cleanups
  first: (1) the kotlin query-param builder null-checks a REQUIRED non-null param (`if (options.x
  != null)` → "condition is always true") — split the builder on `{{#required}}` so required params
  emit unconditionally; (2) Kotlin 2.2 emits "identity-sensitive operation on value type" for the
  generated `equals()` comparing `OffsetDateTime` fields on date-time models (Metadata, PetPassport,
  …) — a false positive on correct structural `!=`, needs a `@Suppress` or an equals rework. Arm the
  gate after these land. The modern `-Xwarning-level=UNCHECKED_CAST:disabled`/`DEPRECATION:disabled`
  syntax was adopted (replacing the now-deprecated `-Xsuppress-warning`).
