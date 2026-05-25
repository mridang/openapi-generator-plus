# Round-4 follow-up — deferred work

This document tracks items from the Round-4 backlog that were triaged but
not landed in this branch. Each entry has a one-line "deferred because X"
note. Re-open when an owner has bandwidth or a real consumer hits the gap.

## Group 1 — template/serialization

### F4 format:byte type-surface in PHP/Ruby/Node/Elixir
**Status:** deferred — cross-language wire-format decision needed.
PHP/Ruby/Node/Elixir expose `format: byte` fields as plain string with no
serializer-side base64 round-trip enforcement. Java/Kotlin/Python/Go/Rust
expose a real Buffer/bytes type. Picking one direction (string + doc, or
Buffer everywhere) breaks the public type-surface in either 4 langs or 7
langs respectively. Touches every generated model that has a byte field
plus the per-language ObjectSerializer. Petstore fixture has no
exercised `format: byte` path so the gap is dormant in CI.

### F5 JSON nesting depth limit in Node/Dart/Elixir/Swift
**Status:** deferred — needs per-language pre-flight scanner + tests.
Java/Kotlin/Python/Go/Rust already cap recursion (~1000 / Go's
`MaxJSONDepth=1000`, Python `json` stdlib default, Jackson default,
serde_json default 128). Node `JSON.parse`, Dart `jsonDecode`, Elixir
`Jason.decode`, and Swift `JSONDecoder` have no depth cap by default —
a 100k-deep `{"a":{"a":...}}` payload OOMs or stack-overflows the
runtime. Fix is a copy of the Go `jsonMaxDepth` pre-flight scanner
(state machine over UTF-8 bytes counting `{`/`[` minus `}`/`]` outside
string literals) before handing off to the native decoder, plus one
unit test per language asserting a synthetic too-deep payload returns
a typed `SerializationError` rather than crashing. Not landed because
it's net-new code in 4 lang ObjectSerializer templates (~50 lines each)
plus matching unit tests; needs a focused PR per lang.

### F6 vendorExtensions cleanup in Go/Python/Elixir/Dart
**Status:** partially deferred — see scope notes.
Audit of `vendorExtensions.x-*` in templates:
- `vendorExtensions.x-is-empty-body` in `python/api.mustache` (set by
  `CleanEmptyRequestBodiesRule`). Native equivalent would be checking
  `bodyParam.dataType` being null/object-typed; needs a normalisation
  pass to convert the extension into a structural condition.
- `vendorExtensions.hasRequiredOptions` in `dart/api/api.mustache` (set
  in `AbstractBetterCodegen.generateOptionsFilesForOps`). This is not
  `x-`-prefixed so not strictly an OAS extension, but does violate the
  CLAUDE.md spirit. Replacing it means adding a new CodegenOperation
  subclass field or routing through `allParams`/`optionsParams`
  derivation in the template — substantial template surgery.
- `vendorExtensions.examples` in `go/api/api.mustache`,
  `python/models/model.mustache`, `python/api/api.mustache`,
  `elixir/models/model.mustache`, `elixir/api/api.mustache`,
  `dart/api/api.mustache`, `dart/models/model.mustache`. This reads
  upstream-set examples; not project-set extensions. Acceptable.

The first two are the legitimate fixes. Both involve restructuring how
the per-language Java code communicates with the template, with risk
of regressing other generators that share `AbstractBetterCodegen`.
Deferred to a dedicated PR per concern.

### F8 StringBuilder template assembly in Ruby+Rust
**Status:** NOT A VIOLATION — confirmed during audit.
- `BetterRubyCodegen.qualifyModelTypes` (line 685): walks a type-string
  character-by-character to qualify type names with `Models::` prefix.
  This is type-string manipulation, not file content. Rendered into a
  `.mustache` template via `context.put("initializeSignature", sig)`.
- `BetterRubyCodegen.generateOptionsFileContent` (line 779) and
  `generateOptionsRbsFile` (line 842): assemble Ruby method signature
  parameter lists. These are inserted as `initializeSignature` string
  placeholders into `api/options.mustache` and `api/options_rbs.mustache`.
- `BetterRustCodegen.processOpts` (line 266): builds an identifier name
  via a Mustache lambda that PascalCases tokens. Inline identifier
  munging, not file content.

Per the F8 scope ("If it's only assembling type strings ... skip — it's
not a violation"), no action taken.

## Group 2 — D3 trailing-optional auth in remaining langs

**Status:** deferred — high-risk template surgery in 6 langs.
Python and Ruby done in prior cycle (commits `a6f85b34` and `e0acd468`).
Remaining: Node, Kotlin, Java, C#, Go, Rust.

The Node/Kotlin/Java/C#/Go/Rust API templates have deeply nested
single-line Mustache expressions that interleave `{{#hasAuthMethods}}`
with `{{#pathParams}}` / `{{#bodyParam}}` / `{{#queryParams}}` etc. to
build the operation signature. Moving `auth` from leading-required to
trailing-optional means rewriting these single-line expressions in both
the operation method AND its internal forward to `*_with_http_info` /
`*WithHttpInfo`, plus updating every per-lang test/api/*.test
template to pass `auth` as a trailing/named arg.

For Java / C# / Go (which lack kwargs), the task spec calls for an
arity-(N-1) overload without `auth`, requiring the API template to emit
two method definitions per operation. This is substantial template
duplication and re-test.

Deferred because (a) the per-line Mustache surgery is genuinely error-
prone, (b) each lang needs a regen + full integration spec re-run to
catch the inevitable forward-call mismatch, (c) the consequence of
getting it wrong is broken-on-arrival client code in production.

## Group 3 — Round-4 LOW

### #5 C# OAuth2 revocation endpoint (RFC 7009)
**Status:** deferred — feature work, not bug.
If OIDC discovery exposes `revocation_endpoint`, the Java/Python OAuth2
manager exposes a `RevokeAccessTokenAsync`. C# does not. Adding it is
net-new method in `OAuth2TokenManager.cs` template plus a
`tokenRevocationUrl` field in the discovery struct + a unit test.
Not blocking CI; not exercised by the petstore fixture; defer until a
caller asks.

### #6 C# is/as modernization
**Status:** mostly done already.
Audit found most C# `is`/`as` usages already use modern pattern
matching (`is not null`, `is var x &&`, etc.). Remaining `Equals(obj as
Foo)` in `models/model.mustache` is idiomatic for `Equals` overrides.
No actionable items.

### #8 typeMapping comments in Better*Codegen.java
**Status:** deferred — cosmetic, large diff.
12 generators × ~25 type mappings each = 300+ comment additions, all
purely cosmetic. Most mappings are obvious (e.g.
`typeMapping.put("integer", "int")` for Python). The non-obvious ones
(e.g. Ruby's `Boolean` → `Boolean`, several langs mapping `decimal`
to `float`) would benefit from comments but the diff signal is diluted
by the obvious ones. Defer to a dedicated cosmetic-only PR.

### #9 README header partial
**Status:** deferred — survey only.
Each language's `readme.mustache` has a hand-written header block
("# {{appName}}\n\n{{appDescription}}\n...") that could be a shared
`{{> common/header}}` partial. Inspection of the 12 README templates
shows each emits a slightly different ordering (some lead with
installation, some with badges, some with quick-start) — extracting
a common partial requires deciding the canonical ordering and the
trim/whitespace handling for the partial boundary. Not blocking;
defer.

## Group 4 — Lint config harden

Surveyed all 12 lang lint configs. None contain a global rule disable
that hides a real bug — the disables in `kotlin/detekt_yml.mustache`,
`ruby/rubocop.mustache`, `python/pyproject_toml.mustache`,
`node/eslint_config.mustache`, `dart/analysis_options.mustache`,
`swift/swiftlint_yml.mustache` are all style-only or correctness-rules-
that-don't-fit-generated-code (e.g. detekt's `LibraryEntitiesShouldNot
BePublic` for an intentionally-public SDK surface, rubocop's
`Lint/RedundantRequireStatement` for defensive `require` patterns).

The Go (`golangci.mustache`), Rust (`clippy_toml.mustache`), Elixir
(`credo_exs.mustache`), and PHP (`phpstan_neon.mustache`) configs do
not disable any rules globally — all real-bug checks are active.

C# has no project-wide lint config in the template tree (relies on
.NET SDK + xunit defaults); the `.csproj` does not set
`WarningsNotAsErrors` blanket-disables.

Round-4 G4 review confirmed: no rule removals warranted. The detekt
adjustments landed in this branch (NoWildcardImports, NoUnusedImports,
Indentation, UseCheckOrError, UnusedParameter, UnusedPrivateMember,
VariableNaming, ConstructorParameterNaming) are all style-only or
schema-name-preserving rules — no real-bug suppressions.
