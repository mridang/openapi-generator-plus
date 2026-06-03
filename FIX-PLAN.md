# Fix Plan — Tier 1 + Tier 2 (+ triaged Tier 3 FIX) by language

Canonical target + evidence for each id is in `AUDIT.md`. Editing is per-language
(disjoint `templates/<lang>/`); verification is sequential per language. **Test
first, then code** (see `PROMPT.md` → Fix loop). 🟡 = needs a shared petstore-spec
fixture before its test can be written — flagged for central handling.

## WONTFIX (documented — do not raise again)
- `readme-heading-and-notes` (Package/Crate/Gem/Module headings — idiomatic)
- `error-hierarchy-exception-vs-error-suffix` (*Exception vs *Error — idiomatic, huge churn)
- Ruby/Elixir half of `deprecation-marker-doc-only` (no per-field language mechanism)

## Cross-cutting (same canonical in every listed SDK — read AUDIT.md verbatim)
- `redirect-exhaustion-silent` → throw "too many redirects": java,kotlin,csharp,node,dart,php,python,rust,swift,ruby
- `redirect-scheme-refusal-silent` → throw on non-http(s) Location: csharp,rust,node,dart,ruby,php,elixir,swift
- `downgrade-body-replay-silent` → raise instead of silent break: csharp,dart,php,swift
- `close-lifecycle-three-way` → closed-flag + SDK error on use-after-close: all except java
- `convenience-empty-body-handling` → throw typed ApiError on empty body for body-returning op: all
- `response-body-read-error-not-wrapped` → wrap body-read in transport try/catch: csharp,node,dart,python,kotlin,rust,go
- `oneof-nondiscriminator-no-match-silent` → validate-each-variant + throw: java,csharp,kotlin,node,dart
- `path-double-encoding` → remove outer encode (encode once in serializer): csharp,dart,node,python,ruby,elixir
- `oauth-oidc-missing-endpoint-guard` → validate non-empty endpoints + throw: go,dart,java,python,ruby,elixir
- `oauth-oidc-discovery-no-status-check` → add <200||>=300 guard: java,node,python,go,ruby,rust,swift,elixir
- `bearer-no-empty-token-guard` → reject empty/whitespace token: all 12
- `oauth-exchangecode-no-empty-code-guard` → reject empty code: all 12
- `form-null-field-null-vs-empty` → omit field when null: java,kotlin,node,dart,swift,go 🟡
- `form-array-field-wire-divergence` → repeated-keys via shared serializer; stop Elixir crash: all 🟡 (+shared codegen)
- `authenticator-secret-in-default-string-repr` → redact secret in default repr: python,ruby,elixir,node,go
- `async-auth-skills-section` → add the SKILLS sub-section: elixir,go,java,php,python,ruby
- `manifest-description-missing` → wire {{appDescription}}: java,csharp,node,php,rust,elixir,kotlin
- nullable tidy (`apiresult-rawbody-nullability`, `apiresponse-body-nullable-elixir`, `apierror-responsebody-headers-nullable-split`) → make non-null: the nullable SDKs

## Per language (language-local findings)
- **go**: go-path-slash-not-encoded, go-swift-path-styles-ignored, go-nullable-optional-emits-null, multipart-object-part-contenttype-go, model-equality-swift-go, oauth-cc-authheaders-error-swallow, response-wrapper-type-name-split
- **rust**: basic-auth-rust-silent-drop, rust-styled-separator-collision, model-eq-hash-rust, apierror-drops-transport-cause, oauth-cc-authheaders-error-swallow, readme-precision-caveat-missing
- **swift**: model-equality-swift-go, go-swift-path-styles-ignored, oauth-cc-authheaders-error-swallow, response-wrapper-type-name-split, form-urlencoded-space-plus-vs-pct20
- **dart**: dart-charset-utf16-mojibake, multipart-rfc5987-filename-dart-live-path 🟡, oneof-nondiscriminator-no-match-silent
- **node**: node-hardcoded-redirect-strip-list, enum-unknown-value-silent-vs-throw
- **elixir**: elixir-success-deserialize-swallow, enum-unknown-value-silent-vs-throw, readme-precision-caveat-missing
- **php**: php-rawurlencode-overencodes, php-required-field-no-guard, php-composer-missing-name, deprecation-marker-doc-only (PHP `#[\Deprecated]`), form-urlencoded-space-plus-vs-pct20, multipart-rawbytes-part-contenttype 🟡
- **python**: python-percall-base-url, multipart-field-name-ascii-fold-python 🟡, multipart-rawbytes-part-contenttype 🟡
- **java**: apierror-drops-transport-cause, auth-validation-timing, multipart-rawbytes-part-contenttype 🟡
- **csharp**: multipart-text-part-contenttype-csharp, auth-validation-timing, form-urlencoded-space-plus-vs-pct20
- **kotlin**: (cross-cutting only)

## Shared prerequisites (central, before parallel wave verification)
- petstore-spec fixtures for 🟡 findings: an operation with an **array form field**,
  a path param value carrying `/`+space+sub-delim, a **non-ASCII multipart field name**,
  a **raw-bytes file part**.
- `form-array-field-wire-divergence` likely needs a shared form-serialization helper
  (AbstractBetterCodegen) so all 12 route form fields through the styled/collectionFormat path.
