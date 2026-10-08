# Cross-language parity — audit backlog

Source audits (full detail): round 1 = core infra; round 2 = models/operations/runtime/project. 44 line items, 43 distinct (base64 leniency spans both). Rule: *a divergence is a defect until a language feature forces it.* Known-forced/closed items live in `AGENTS.md` and are not listed here.

Status legend: `[ ]` open · `[~]` in progress · `[x]` done · `[-]` deferred/won't-fix.

## Decisions taken (2026-10-08)
- **weightKg / bare `type: number` → decimal in ALL three** offenders: ruby `Float`→`BigDecimal`, swift `Double`→`Foundation.Decimal`, go `float64`→`encoding/json.Number`. Accepts the public-field-type (breaking) change.
- **Content-type selector → THROW client-side** on an unrecognized request content-type, aligning every language to java/elixir and the generator's stated intent.

---

## TIER 1 — actionable (real wire / behavior / contract) — DOING NOW

- [ ] **T1-1** php OAuth2 authorize-URL emits `+` not `%20` — `http_build_query` default RFC1738. Fix: `PHP_QUERY_RFC3986`. Files: `templates/php/.../oauth2_authorization_code_authenticator.mustache`, `oauth2_implicit_authenticator.mustache`.
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
- [ ] **T3-4** docs: field-level example values omitted (python/go/swift/elixir).
- [ ] **T3-5** docs: elixir model docs thinnest (no field desc / deprecation / externalDocs).
- [ ] **T3-6** rust advertises zstd but not brotli.
- [ ] **T3-7** java/kotlin package name generator-default `openapi-<lang>-client`.
- [ ] **T3-8** swift `Metadata` memberwise init drops `createdAt`.
- [ ] **T3-9** no field-dumping toString/repr on java/csharp/php/node/dart models.
- [ ] **T3-10** misattributed NO_AUTH comment on inherit-global ops (php/rust/elixir).
- [ ] **T3-11** rust emits empty content-type boilerplate on bodyless ops.
- [ ] **T3-12** setPetAvatar selector param name `requestContentType` vs `contentType` (node/php/ruby/elixir).
- [ ] **T3-13** rust `#[default]` enum-variant vs schema field default mismatch (DefaultsModeEnum). *(latent)*
- [ ] **T3-14** go error-body downgrade-guard anchors original-vs-current hop (also dart/swift/elixir). *(round 1, benign)*
- [ ] **T3-15** php populates `data` for non-JSON body without returnType. *(round 1, benign)*
- [ ] **T3-16** swift resets token expiry to never-expires when expires_in omitted. *(round 1)*
- [ ] **T3-17** error-message wording drift (enum-validation; empty-path). *(round 1)*
- [ ] **T3-18** redaction repr field-sets differ (java Basic omits host; go/node ApiKey omit location). *(round 1)*
- [ ] **T3-19** ValueSerializer latent path-array encoding splits (unreachable). *(round 1)*

## Clean layers (0 findings)
Enums; composite/polymorphic/recursive models.
