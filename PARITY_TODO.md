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

## TIER 2 — medium / unforced (next)
- [ ] **T2-1** kotlin manifest forces brotli+zstd+otel mandatory (others optional). `templates/kotlin/build.gradle.kts`.
- [ ] **T2-2** python manifest forces brotli+zstandard mandatory though code treats optional → move to extras. `pyproject.toml` template.
- [ ] **T2-3** csharp deprecated param uses `<remarks>` not `[Obsolete]`. Options template.
- [ ] **T2-4** kotlin renames required multipart param `file`→`_file` (unforced). kotlin options template.
- [ ] **T2-5** deprecation marker missing on `*WithHttpInfo` operation variant in 11/12 (java marks all). api templates.
- [ ] **T2-6** java leaks raw `NumberFormatException` (oversized duration) + java/kotlin/dart leak raw native exception on invalid base64 → wrap in SerializationError. object_serializer templates.
- [ ] **T2-7** python inlines the two root error types (others split). python errors template. *(round 1 structural)*
- [ ] **T2-8** rust excludes 8 scheme-authenticator files from the crate (`auth/mod.rs` missing mods). rust auth mod template. *(round 1)*
- [ ] **T2-9** php has no `AllowReservedValue` wrapper (threads a name-set instead). *(round 1)*
- [ ] **T2-10** csharp `Configuration` ctor public while `TransportOptions` ctor internal. *(round 1)*
- [ ] **T2-11** facade lifecycle gap — only csharp facade disposable; 10 own a DefaultApiClient with no close. *(round 1)*
- [ ] **T2-12** java & kotlin skip is-string check on `refresh_token` capture. *(round 1)*
- [ ] **T2-13** description null-vs-`""` in dart/go/rust/swift (ServerConfiguration/ServerVariable only). *(round 1)*

## TIER 3 — low / cosmetic / latent / docs
- [ ] **T3-1** docs: README H1 uses package name not spec title (go/rust/swift).
- [ ] **T3-2** docs: externalDocs links dropped (dart all 3, elixir all 3, rust model).
- [ ] **T3-3** docs: java/kotlin emit wrong operation externalDocs description text.
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
