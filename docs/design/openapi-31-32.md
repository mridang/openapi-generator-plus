# OpenAPI 3.1 and 3.2 design (issues #96, #49 and #50)

Status: proposal for owner review. Nothing here is built. Written against the worktree `feat/ogen-designs` with OpenAPI Generator 7.22.0 and swagger-parser 2.1.38 (the versions in `pom.xml`; their sources jars were read). "Verified in <file>" means the file was read for this document; "run" means I generated SDKs from a small inline 3.1 spec (and a 3.2 spec) with the shipped generators and read the output (scratch files only, nothing in the repository was changed); "not verified" means neither. The root has no `CLAUDE.md`; the rules are in `AGENTS.md` and `src/main/java/io/github/mridang/codegen/generators/AGENTS.md`, both read.

## 1. Goal

Every OpenAPI 3.1 construct that matters to a client SDK is either generated correctly or reported with a warning, in all twelve SDKs (java, kotlin, csharp, php, python, ruby, node, dart, go, rust, swift, elixir), with no construct silently producing code that does not compile or silently dropping an operation. A document that declares `openapi: 3.2.0` loads and generates the same way, so that every 3.2 feature is additive on top of a public API shape that does not change. Constructs that the owner has ruled out (webhooks, callbacks, links, XML, mutual TLS, client-side validation) stay out, and the generator says so instead of producing a wrong SDK.

## 2. Owner decisions that bind this work

- Client SDKs only, twelve languages, identical capabilities and the same API shape; a difference between two languages is a defect until a named language feature forces it (root `AGENTS.md`). Public API shape is fixed so that 3.1 and 3.2 work is additive and never breaking (root `AGENTS.md`, "Goal").
- No client-side validation (S3, `generators/AGENTS.md`: the client does not promise to enforce pattern, length, range or item-count keywords; the server owns validation). Consequence here: `not`, `contains`, `minContains`, `unevaluatedItems`, `dependentRequired`, `dependentSchemas`, `if`/`then`/`else`, `propertyNames`, `contentSchema` and #83 (opt-in validation) are declined; the generator warns that it ignores them. Opt-in feature flags are declined as well (#131, quoted in `docs/design/streaming.md` section 2), which also declines #93 (opt-in exposure of `x-` extensions) as written.
- Typed errors are thrown, never returned as values; the error tree of `generators/AGENTS.md` is the only one. Every new failure path in this plan uses an existing error type of the language.
- Native types where the language has one (`docs/types.md`). A union is the language's existing union shape (F878 factories and accessors), an enum is the language's existing enum shape; this plan adds no wrapper type.
- Templates never read a spec-sourced `x-*` extension and are Mustache files; Java computes values and hands them over under a name without `x-` (`vendorExtensions.op.*`, `AGENTS.md`, "Template and generated-prose rules"). The loader in section 5 writes carrier keys `x-oas32-*` on the parsed model; the shared generator consumes and removes them in `processOpenAPI`, so no template ever sees one.
- Out of scope (S11, closed #53, #70, #78, #88, #90): callbacks, webhooks, XML bodies and `nodeType`, Link Objects, mutual TLS. The READMEs say so.
- Never edit `.github/workflows` (S13). The shared catalogue `src/spec/resources/test-catalogue.txt` names every behaviour once and each language needs a test carrying the id or an entry in `test-catalogue-exceptions.txt` with the forcing reason. Do not add test files except the mirror file of a new source file; extend the test templates that exist (`generators/AGENTS.md`, "Test layout").
- Every fixture lives in `src/spec/resources/specs/petstore/openapi.yaml` and is served by chasm (`generators/AGENTS.md`, "Fixture rules"); `FixtureVocabularyLeakTest` fails on petstore vocabulary in any generated SDK. The petstore document is already `openapi: 3.1.0` (verified in `specs/petstore/openapi.yaml` line 1).
- All twelve goldens are committed current in one commit, never a subset (`docs/how-it-works.md`, "Formatting").
- Neighbouring designs this one must not contradict: `docs/design/streaming.md` (sequential media types, `text/event-stream`, itemSchema signal), `docs/design/nullable-query.md` (relies on `NormalizeOas31NullableTypeRule`, see risk R7), `docs/design/enum-names.md` and `docs/design/native-types.md` (enum and type shapes).

## 3. The shared behaviour

### 3.1 What the pipeline does today with a 3.1 document (run)

I generated all twelve SDKs from an inline 3.1.0 spec of about 60 constructs and a second set of small probes. All generations exit 0, which hides the problems below: every defect is a silent wrong output, none is a failure. Evidence is from the java output unless another language is named; the model is shared, so the cause is shared.

| Finding | Evidence (run) | Languages |
|---|---|---|
| `type: [string, "null"]` becomes a nullable string | `nick`, `nullableReq` generate `@Nullable String` / `Option<String>` / `Optional[StrictStr]`; the required one keeps its key (S5) | all (read: java, go, rust, python, node) |
| `type: [string, integer]` becomes an anyOf wrapper, but the property type is the raw inline name | property type `Holder_unionStrInt`, class `HolderUnionStrInt` (java, python, rust, node verified in the generated files; kotlin, csharp, ruby, dart, swift, elixir contain the raw name in a type position, not compiled) | broken in 10 of 12; go and php use the cased name |
| The same name defect exists without 3.1 | a plain 3.0.3 spec with a component `my_widget` generates `public final my_widget w;` next to class `MyWidget`; an inline object property `loc` gives `Holder_loc` beside `HolderLoc` | java reproduced; the petstore fixture has no such name (its inline-looking schemas are named components such as `PhotoMetadataLocation`), so no test catches it |
| `type: [array, object]` | becomes `List<Object>`; an object member cannot decode | java |
| `type: "null"` alone | `public final null onlyNull;`, go `*null`, rust `Option<null>`, python `Optional[null]` do not compile | java, go, rust, python |
| `const` with a type | single-value enum (`ConstStrEnum`); works | java, go, rust, python, node |
| `const` without a type | an empty-named enum (`public enum  {` in java) or an enum named like the model (go `Kind *Widget`, rust `Option<Widget>`, python two classes named `Widget`) | java, go, rust, python |
| `oneOf` of `const` values (the JSON Schema enum idiom) | the model is generated with no variants and no values (`Colour` is an empty class); the values are lost without a warning | java |
| `webhooks` | generated as ordinary operations and written into `DefaultApi`, overwriting the file: with a webhook present `DefaultApi` held only `newWidgetPost` and the untagged operations of the document (`createWidget`, `getTuple`, `upload`, `deleteWithBody`) were gone; without `webhooks` they are present. Cause: `DefaultGenerator.processWebhooks` at `DefaultGenerator.java:850` in 7.22.0 | java |
| Path item `$ref` into `#/components/pathItems/...` | the path is dropped silently; `getShared` appears nowhere in the output | java |
| `HEAD`, `OPTIONS`, `TRACE`, `PATCH` operations and path-level `servers` and `parameters` | generated, each with its own Options and Server types; the path-level server URL is baked into each operation | java |
| Path item `summary` and `description` | ignored (no reference in `src/main/java`; issue #58) | all |
| `DELETE` with a request body, a media type object with no schema | generated (`deleteWithBody(@Nullable DeleteWithBodyRequest)`, `getD()` returning void) | java |
| Document without `paths` | generates a client with no operations; exit 0 | java |
| `contentEncoding` / `contentMediaType` | translated by `ContentEncodingRule`, applied to every document (`AbstractBetterCodegen.java:677`); the petstore fixture exercises it (line 2426) | all |
| `prefixItems` | degraded to an array of any value by `NormalizePrefixItemsRule`, applied to every document (`AbstractBetterCodegen.java:671`); the fixture `GeoPoint` produces no model (alias to array) | all |
| `$defs`, `$dynamicRef`, `not`, boolean schemas | a `$ref` into a `$defs` pointer resolves and generates the model; `$dynamicRef`, `not`, `true` and `false` become the any type | java, go, rust, python |
| `if`/`then`/`else`, `dependentRequired`, `dependentSchemas`, `contains`, `unevaluatedItems`, `patternProperties`, `propertyNames` | ignored with no warning; `patternProperties` makes the property the any type | java, go, rust, python |
| Spec warning pass | `SpecWarnings` runs only inside `AdvancedOpenAPINormalizer` (`AdvancedOpenAPINormalizer.java:normalize`), which nothing under `src/main` instantiates; a plain generation of a document with `webhooks` logged no "Spec warning" line, although `docs/how-it-works.md` says every run has the pass | all |
| `info.license`, `info.summary` | not used: `licenseId` defaults to `MIT` (`AbstractBetterCodegen.java:559`), `info.summary` is only HTML-unescaped (line 4693) | all |
| `openapi: 3.2.0` | the parser returns no document and no message; the generator falls back to the Swagger 2 reader, prints "attribute swagger is missing" and "attribute paths.'/things'.query is unexpected", and ends with a `NullPointerException` on `openAPI.getExtensions()` | all |

Upstream cannot be upgraded out of the 3.2 problem today. `OpenAPIDeserializer.java:334` in swagger-parser 2.1.38 returns null unless the version starts with `3.0` or `3.1` (sources jar). The newest swagger-parser on Maven Central is 2.1.49 and its `OpenAPIDeserializer.class` still carries only the strings `3.0` and `3.1`. The newest swagger-models, 3.0.0, which I downloaded and read with `javap`, has `SpecVersion` with `V30` and `V31` only, an `HttpMethod` enum of eight methods (no `QUERY`), no `additionalOperations` and no `$self`. OpenAPI Generator 7.27.0 exists; I did not read it, but it reads documents through the same parser. The pinned chasm (`mridang/chasm:1.3.0`) cannot serve a 3.2 document either: `crates/chasm-engine/src/loader.rs:130` says "chasm supports 3.0.x and 3.1.x", and the engine is built on `openapiv3 = "2"` (`crates/chasm-engine/Cargo.toml:18`), whose path item has no QUERY.

### 3.2 Construct ledger and plan

Every 3.1 and 3.2 construct, what happens today, and the plan. "Shared" means only the shared generator, the rules, the loader or the fixtures change; "per language" means template edits in the twelve SDKs. Issue numbers are the sub-issues of #96 and #49.

| # | Construct | Version | Today | Plan | Issue |
|---|---|---|---|---|---|
| C1 | `openapi: 3.1.x` document | 3.1 | loads, generates (run) | none; keep petstore at `3.1.0` | |
| C2 | `openapi: 3.2.x` document | 3.2 | `NullPointerException` (run) | parser extension that rewrites a 3.2 tree to 3.1 and keeps 3.2-only data as `x-oas32-*` carriers; shared | #50 |
| C3 | `$self` | 3.2 | cannot load | read and recorded by the loader; used as the base for relative external `$ref`s only when it is absolute, otherwise logged and ignored; shared | #51 |
| C4 | `info.summary` | 3.1 | done (#54): read from the spec; the summary is the description fallback and the info block of the README| one-line description fallback in the manifests that have a description field; shared value, no new template token (`appDescription` exists, name not verified per manifest) | #54 |
| C5 | `info.license` (`name`, `identifier`, `url`) | 3.1 | done (#54): `licenseId` takes `license.identifier` (read from the raw document where the parser drops it), `licenseUrl` takes `license.url`, `licenseName` feeds the README| `license.identifier` becomes the default of `licenseId`, `licenseUrl` default of the SPDX page or `license.url`; an explicit option still wins; shared (templates already print `{{licenseId}}`: `java/pom.mustache:17`, `go/go_mod.mustache:1`, `rust/cargo_toml.mustache:6`, `python/pyproject_toml.mustache:5`) | #54 |
| C6 | `info.contact`, `info.termsOfService` | 3.0 | done (#54): contact name, email and URL are printed in the README and, where the manifest has a slot, in pom.xml (`organization`), build.gradle (`organization`) and the gemspec (`email`); terms of service are printed in the README only| decline: no slot that all twelve manifests share (not verified per manifest); the generated README says "ignored"; needs owner approval | #54 |
| C7 | `jsonSchemaDialect`, schema `$schema` | 3.1 | parsed, unused | accept the OAS 3.1 base dialect and JSON Schema 2020-12; any other value logs a warning that it is read as 2020-12; shared | #52 |
| C8 | document without `paths` or without `servers` | 3.1 | generates (run, java) | fixtures and tests only | #97 |
| C9 | `webhooks` | 3.1 | emitted as operations and overwrites `DefaultApi` (run) | out of scope: remove `webhooks` from the model before generation and warn once with the pointer; shared bug fix | closed #53 |
| C10 | `callbacks`, `links`, `xml`, `xml.nodeType` | 3.0, 3.2 | warned by `SpecWarnings` when the normaliser runs | out of scope; make the warning pass run on every generation (R6); shared | closed #70, #78, #88 |
| C11 | `components.pathItems` and path `$ref` | 3.1 | dropped silently (run) | inline the referenced item into the referencing path in `processOpenAPI` (referencing item's own fields win; a conflicting field logs a warning); an operationId used by more than one path keeps the id on the first path and takes the stock generated id on the others, with a warning; shared | #56, #58 |
| C12 | path item `summary`, `description` | 3.1 | ignored | an operation with no summary or notes inherits them in `preprocessOpenAPI`; no template change; shared | #58 |
| C13 | `PATCH`, `HEAD`, `OPTIONS`, `TRACE` | 3.0 | generated (run, java); fixture marks its patch operations `x-internal` | add non-internal operations to the petstore fixture and goldens; tests | #59 |
| C14 | path-level `servers` | 3.0 | generated (run, java) | test only | #62 |
| C15 | `QUERY` method | 3.2 | cannot load | carrier operation (section 5.2), method string `QUERY`; per language: the empty-body verb set | #60 |
| C16 | `additionalOperations` | 3.2 | cannot load | same carrier with the exact-case token; per language: case-preservation check | #61 |
| C17 | parameter `in: querystring` | 3.2 | `in` is a free string, no code reads it | rewrite to `in: query` plus carrier; the shared decorator marks the parameter `isQueryString`; the request builder writes the serialised `content` after `?` with no `name=`; mixing it with query parameters fails generation; per language: `base_api` query builder | #63 |
| C18 | parameter `style: cookie` | 3.2 | not in `Parameter.StyleEnum`; cookie params use form | carrier; per language: cookie serialiser writes `name=value` pairs without percent-encoding | #65 |
| C19 | `allowReserved` on path and form encodings | 3.0, 3.2 | query only (fixture `revision`) | extend the existing query rule to path parameters; per language: `value_serializer` path branch | #66 |
| C20 | Example Object `dataValue`, `serializedValue`; parameter and media-type examples | 3.2 | schema examples only | carriers read in the shared doc builder; docs only, no runtime code; shared | #64, #79 |
| C21 | `components.mediaTypes` and `$ref` to a media type | 3.2 | cannot load | the loader inlines each reference and drops the map; shared | #57 |
| C22 | media type `itemSchema` | 3.2 | cannot load | the loader sets `schema` to the item schema (the streaming design types `T` as the schema of the media type) and carries `x-oas32-sequential`; behaviour is the streaming design's `<op>Stream` | #68 |
| C23 | sequential media types (`application/jsonl`, `application/x-ndjson`, `application/json-seq`, `text/event-stream`) | 3.2 | `OnlyAllowJsonRule` strips them | exactly `docs/design/streaming.md`; this plan adds only the `itemSchema` mapping and the `ONLY_ALLOW_JSON` exemption it already lists | #71, #72 |
| C24 | response media type chosen among several | 3.0 | `effectiveMediaType` picks the first (`AbstractBetterCodegen.java:3645`) | new per-call choice; needs its own design (R9) | #73 |
| C25 | `prefixEncoding`, `itemEncoding` (ordered multipart) | 3.2 | cannot load | carriers; needs its own multipart design (R9) | #69 |
| C26 | Encoding Object `headers`; `style`, `explode`, `allowReserved` | 3.0 | contentType only | needs its own design (R9) | #74, #75 |
| C27 | Response Object `summary` | 3.2 | cannot load | carrier; docs only; a response with no `description` is accepted; shared | #76 |
| C28 | response `headers` as typed fields | 3.0 | raw map only (`ApiHttpResponse.headers`) | needs its own design (R9) | #77 |
| C29 | Reference Object `summary` and `description` | 3.1 | a schema `$ref` keeps the sibling `description` (run, python); other reference kinds not verified | docs only; shared | #81 |
| C30 | Tag `summary`, `parent`, `kind` | 3.2 | cannot load | carriers consumed by `injectTagMetadata` (`AbstractBetterCodegen.java:~4903`); docs and API grouping only; shared | #80 |
| C31 | Server `name` | 3.2 | cannot load | carrier `x-oas32-name`; `processServers` adds `serverName`; per language: `name` accessor and a lookup by name (section 6) | #55 |
| C32 | server URL and path template syntax | 3.2 | not validated (`processServers`) | fail generation with a clear message on an invalid template; shared | #94 |
| C33 | HTTP schemes beyond basic and bearer (digest, negotiate) | 3.1 | warning F1061 only | keep the warning, and say in the README that a caller supplies the header through the existing custom `Authenticator`; digest and negotiate are challenge-response and are not generated; needs owner approval of the decline | #89 |
| C34 | security scheme `deprecated` | 3.2 | cannot load | carrier; the doc builder marks the scheme in README and SKILLS; per language: the doc token only | #91 |
| C35 | `oauth2MetadataUrl` | 3.2 | cannot load | carrier; read by the OAuth2 authenticators as the discovery URL when no token URL is given; per language: oauth classes | #91 |
| C36 | OAuth2 device authorization flow | 3.2 | cannot load | new authenticator in all twelve; needs its own design (R9) | #92 |
| C37 | `mutualTLS` | 3.1 | warned by F1061 (`unsupportedSchemeMessage`) | out of scope, unchanged | closed #90 |
| C38 | `type` arrays with `null` | 3.1 | works (run) | keep; fixtures for required and optional | #98 |
| C39 | `type` arrays of several types | 3.1 | scalars become an anyOf wrapper with a broken type name; array plus object loses the object (run) | one rule: a type array becomes an `anyOf` with one schema per member (type keywords copied to the member they belong to); plus the naming fix C47 | #98 |
| C40 | `type: "null"` alone | 3.1 | does not compile (run) | maps to the language's any type, nullable; shared rule | #98 |
| C41 | `const` | 3.1 | typed const works; typeless const broken (run) | rule infers the type from the value (string, integer, number, boolean) and emits the existing single-value enum | #82 |
| C42 | `oneOf` of `const` values | 3.1 | empty model (run) | rule turns a `oneOf` whose every member is a `const` (with an optional `title` and `description`) into one enum; titles feed the member descriptions that `enum-names.md` defines | #82 |
| C43 | `enum` containing `null` | 3.1 | typed enum (run) | keep; test | #98 |
| C44 | `$ref` beside other keywords | 3.1 | schema siblings kept (run, python) | keep; test | #81 |
| C45 | `$defs`, `$anchor`, `$dynamicRef` | 3.1 | pointer `$ref` into `$defs` works; `$dynamicRef` becomes any (run); `$anchor` references not verified | warn on `$dynamicRef` and `$anchor`; document the pointer form | #82 |
| C46 | boolean schemas, typeless schemas | 3.1 | any type (run) | keep; test | #98 |
| C47 | component and inline schema names that are not PascalCase | 3.0, 3.1 | property type keeps the raw name in 10 of 12 languages (run) | shared fix first (R1); not an OpenAPI construct but it blocks C39 | #98 |
| C48 | `prefixItems` | 3.1 | degraded to an array of any (existing rule, petstore `GeoPoint`) | keep the decision of `NormalizePrefixItemsRule` (Gap AZ): no portable fixed-length mixed tuple in twelve languages; decline #85 | #85 |
| C49 | `not`, `contains`, `minContains`, `unevaluatedItems`, `dependentRequired`, `dependentSchemas`, `if`/`then`/`else`, `propertyNames`, `contentSchema` | 3.1 | ignored, no warning (run) | validation-only keywords: declined by S3, warn once per keyword with the pointer | #82, #86 |
| C50 | `patternProperties` | 3.1 | any type (run) | warn; the property stays the any type (typed maps belong to `typed-additional-properties.md`) | #84 |
| C51 | `unevaluatedProperties: false`, `exclusiveMinimum` as a number | 3.1 | handled (petstore line ~2484; python `gt=` run) | keep | |
| C52 | discriminator `defaultMapping`, optional `propertyName` | 3.2 | cannot load | carrier on the owning schema; the existing decoders fall back to the default mapping when the key is missing or unknown; per language: the discriminator-dispatching decoders of `unions301`-`unions304` | #87 |
| C53 | opt-in exposure of `x-` extensions | any | none | decline (feature flags declined in #131, templates never read extensions) | #93 |
| C54 | optional client-side validation | any | none | decline (S3) | #83 |
| C55 | `NormalizeOas31NullableTypeRule` | 3.1 | removed in #100: nothing referenced it | n/a | #100 |
| C56 | OpenAPI 3.0 `nullable: true` | 3.0 | works through the stock normaliser | test in the probe fixture | #99 |

### 3.3 Catalogue lines

New areas, so no id collides with other work (`areaNNN | promise`; the area has no digits, as in `authoauthtwoimplicit`). Behaviours that need a response from the mock server are only the ones chasm 1.3.0 can serve (3.1 documents); every 3.2 behaviour is asserted on the recorded request or on the generated source, never on a chasm response (R3).

```
oasschema001 | A property declared with const is sent with its constant value
oasschema002 | A const property whose wire value differs fails to decode with the serialization error
oasschema003 | A oneOf of constants decodes each constant and rejects any other value
oasschema004 | A type array of two scalar types decodes each member and writes it back unchanged
oasschema005 | A type array of an array and an object decodes both members
oasschema006 | A oneOf of a schema and null is nullable and keeps an explicit null
oasschema007 | A schema of type null alone decodes null and writes null
oasschema008 | A schema with no type accepts any JSON value and writes it back unchanged
oasschema009 | A component schema named in snake case is referenced by the name the model is declared with
oasschema010 | A required property typed by a type array with null is written as null when unset
oasoperation001 | A PATCH operation sends its body with the PATCH method
oasoperation002 | A HEAD operation sends HEAD and exposes the response headers with no body
oasoperation003 | An OPTIONS operation sends OPTIONS
oasoperation004 | A TRACE operation sends TRACE
oasoperation005 | An operation reached through a path item reference is sent to the referencing path
oasoperation006 | An operation with no server of its own uses the server of its path item
oasoperation007 | A QUERY operation sends QUERY with its body and its content type
oasoperation008 | A QUERY request with no body is sent with Content-Length 0 like a POST
oasoperation009 | A QUERY request is replayed on a stale connection exactly as a GET is
oasoperation010 | A custom method declared in additionalOperations is sent with the token exactly as declared
oasparam001 | A querystring parameter is sent as the whole query string with no name
oasparam002 | A cookie parameter with style cookie is sent without percent-encoding
oasparam003 | An allowReserved path parameter keeps reserved characters literal
oasserver001 | A server is selected by its name
oasserver002 | An unknown server name raises the configuration error and names the known names
oasdocs001 | The README lists webhooks, callbacks, links, XML and mutual TLS as not supported
oasdocs002 | A deprecated security scheme is marked deprecated in the README and SKILLS
```

Not in the catalogue because they are generator behaviour, not SDK behaviour (they are unit tests, section 7): loading a 3.2 document, dropping webhooks, inlining a path item reference, the verb marker on a carrier operation, every warning, the `licenseId` default, the rules for `const`, `type: "null"`, type arrays and `oneOf` of constants.

`oasparam001` to `oasparam003`, `oasserver001`, `oasserver002` and `oasoperation010` are 3.2 or 3.2-adjacent: their transport-level assertions use the in-process stub that the `DefaultApiClientUnitTest` templates already use (java: `java/test/DefaultApiClientUnitTest.mustache:1950` asserts `isIdempotentMethod("QUERY")` without a server), not chasm.

## 4. The shared generator changes

All in `src/main/java/io/github/mridang/codegen/` unless noted. Order is the order of work (section 9).

1. **`AbstractBetterCodegen.getSchemaType` (line 1723).** Run every referenced schema name through `toModelName`, as the go codegen already does (`BetterGoCodegen.java:870-892`, "Resolves a reference to a component schema through toModelName"). `DefaultCodegen.getSchemaType` of the Java generator in 7.22.0 does `toModelName(openAPIType)` (`AbstractJavaCodegen.java:1846`), `AbstractBetterCodegen` does not, which is why a name that is already PascalCase works and any other does not. Check the constant `getModelNameCollisionPrefix` and the Java `Error` to `ModelError` special case (`BetterJavaCodegen.java:364`) still apply. Add a snake-case component and an inline composed property to the petstore fixture (neutral names, for example `audit_entry`) so the twelve goldens prove it.
2. **`rules/NormalizeTypeArrayRule.java`, `rules/NormalizeNullTypeRule.java`, `rules/NormalizeConstRule.java` (new, each with its mirror test under `src/test/java/.../rules/`).** Applied unconditionally in `processOpenAPI` next to `NormalizePrefixItemsRule` (`AbstractBetterCodegen.java:671`), exactly like it, so no normaliser key is needed. `NormalizeTypeArrayRule`: `type: [A, B, ...]` (after the stock normaliser has taken `null` out) becomes an `anyOf` of one schema per type with the type-specific keywords (`items`, `properties`, `format`, `enum`) copied to the member they belong to. `NormalizeNullTypeRule`: a schema whose only type is `null` becomes an untyped nullable schema, which every language already maps to its any type. `NormalizeConstRule`: infer `type` from a typeless `const`; turn a `oneOf` whose members are all `const` into one `enum` with the titles kept as member descriptions.
3. **`processOpenAPI` (line 658).** Add, before the rules above: (a) `openAPI.setWebhooks(null)` after logging one warning with the pointer; (b) path item inlining for `$ref` into `#/components/pathItems/...` and `#/paths/...` (a new private method, mirrored by a test in `AbstractBetterCodegenTest`); (c) removal of the carrier keys after they are consumed. Add to `preprocessOpenAPI` (line 4685): the path item `summary` and `description` fallback onto operations that have none.
4. **`fromOperation` (line 5566).** When `path` ends with the marker `#oas32:<METHOD>` (set by the loader, section 5), call `super.fromOperation(strippedPath, METHOD, ...)` so `op.path` is the real path and `op.httpMethod` is the exact token (`DefaultCodegen.java:4997` upper-cases the method it is given; for `additionalOperations` the loader passes the token upper-cased and keeps the exact case in the decorator `httpMethodToken`, see R5). The marker never reaches a template.
5. **`processServers` (line 716).** Add `serverName` from the `x-oas32-name` carrier, validate URL and path template syntax (C32), and expose `hasServerNames`.
6. **`licenseId` default (line 559).** Read `info.license.identifier` (and `url`) when the option is absent.
7. **`SpecWarnings.java`.** Add the keywords of C49, C50, C45 to the `UNSUPPORTED` map with their reasons; keep the map keyed by keyword so `NAME_KEYED` still protects property names. Make the pass run on every generation, not only inside `AdvancedOpenAPINormalizer`: call `SpecWarnings.check(openAPI, Boolean.getBoolean(SpecWarnings.STRICT_PROPERTY), LOGGER)` at the start of `processOpenAPI`, before any rule rewrites the document (the same place `warnAmbiguousOneOfs` runs, line 663). Keep the call inside the normaliser too so a normaliser user sees one pass, not two (the check is idempotent).
8. **`Oas32ParserExtension.java`, `Oas32Rewriter.java` and `src/main/resources/META-INF/services/io.swagger.v3.parser.core.extensions.SwaggerParserExtension` (new).** Section 5.
10. **Templates, per language** (sections 6 and 9): the empty-body verb set, `base_api` query and cookie builders, `servers` and `server_configuration`, the authenticators for C35 and C36.
11. **Docs, in the same pull request as each change** (`AGENTS.md`, "README and docs are part of every change"): `docs/how-it-works.md` (a new subsection "Reading OpenAPI 3.2" and the new rules in "Normalising the specification"), `docs/decisions.md` (the declines C6, C33, C48, C49, C53, C54), `docs/types.md` (const, type arrays, `type: "null"` rows), the twelve `docs/languages/<lang>.md` pages for any per-language limit found in section 6, and the "Not supported" section of the twelve README templates (webhooks and the rest are already listed; add the 3.1 keywords of C49 and the declined 3.2 features, in the same words and order in all twelve).

## 5. Reading OpenAPI 3.2 documents (issue #50)

### 5.1 The hook

`CodegenConfigurator` reads the specification with `new OpenAPIParser().readLocation(...)` (`CodegenConfigurator.java:687` in the 7.22.0 sources). `OpenAPIParser.readLocation` asks every `SwaggerParserExtension` in turn until one returns a document (swagger-parser sources, `io/swagger/parser/OpenAPIParser.java`); `OpenAPIV3Parser.getExtensions()` puts the stock parser first and then every extension registered with `ServiceLoader` (`OpenAPIV3Parser.java:60-79`). A 3.2 document makes the stock parser return null, so the next extension gets it. The generator jar can therefore register one extension, with no change to the command line and no new dependency. This is the supported route; forking swagger-models was rejected (section 8, R2).

### 5.2 What the extension does

`Oas32ParserExtension` implements `readLocation` and `readContents`. It reads the text (a file or an URL; how to read an URL without a private swagger class is not verified, `java.net` plus the same authorization values is the fallback), parses it into a Jackson tree, and returns null for any `openapi` value that does not start with `3.2` so 3.0 and 3.1 behave exactly as today. For a 3.2 document it asks `Oas32Rewriter` for a 3.1.0 tree and parses that with `OpenAPIV3Parser.readContents(..., location)`, so every `$ref` resolves as it does today. The rewrite table is the whole contract:

| 3.2 construct | Rewritten to | Consumed by |
|---|---|---|
| `openapi: 3.2.x` | `openapi: 3.1.0` | |
| `$self` | removed; recorded as `x-oas32-self` on the root | C3 |
| path item `query` | a new path `"<path>#oas32:QUERY"` holding the operation, with the path item's `parameters` and `servers` copied and an `operationId` generated from the real path and the method when missing | `fromOperation` |
| path item `additionalOperations` | one such path per entry, `"<path>#oas32:<TOKEN>"`, token kept exact in `x-oas32-method` | `fromOperation` |
| parameter `in: querystring` | `in: query`, `x-oas32-in: querystring` | shared decorator |
| parameter `style: cookie` | `style` removed, `x-oas32-style: cookie` | shared decorator |
| `components.mediaTypes` and `$ref` to one | each reference replaced by a copy, map removed | |
| media type `itemSchema` | `schema` set to it when absent, `x-oas32-sequential: true` | streaming design |
| `itemEncoding`, `prefixEncoding` | `x-oas32-itemEncoding`, `x-oas32-prefixEncoding` | multipart design (R9) |
| response `summary` | `x-oas32-summary` | doc builder |
| Example `dataValue`, `serializedValue` | `x-oas32-dataValue`, `x-oas32-serializedValue` | doc builder |
| Tag `summary`, `parent`, `kind` | `x-oas32-summary`, `x-oas32-parent`, `x-oas32-kind` | `injectTagMetadata` |
| Server `name` | `x-oas32-name` | `processServers` |
| security scheme `deprecated`, `oauth2MetadataUrl` | `x-oas32-deprecated`, `x-oas32-oauth2MetadataUrl` | doc builder, oauth classes |
| OAuth flow `deviceAuthorization` | `x-oas32-deviceAuthorization` on the scheme | device flow design (R9) |
| discriminator `defaultMapping` | `x-oas32-discriminatorDefault` on the owning schema | `markClassicDiscriminatorInheritance` and the decoders |

Anything the rewriter does not know is passed through and the stock parser's own messages apply. The extension is the only place that knows 3.2 exists; when swagger-parser ships 3.2 support, the extension and `Oas32Rewriter` are deleted and the carriers are read from the real fields. A document that mixes a carrier key spelling with a real key is a user error and is reported by the parser's own unexpected-attribute messages.

## 6. Per language

What I read: `java`, `go`, `rust` and `python` (generated goldens, templates and the `AGENTS.md` of the shared generator) for server and transport behaviour; `node`, `ruby`, `kotlin`, `csharp`, `swift`, `dart`, `php` and `elixir` only at the lines named in the cells. A cell says "not verified" where I did not read it. The public names below are proposals for review; all are additive (a new accessor or a new function; no existing signature changes). The server lookup throws the kind of error each language already throws for an invalid server variable (verified: java `IllegalArgumentException` at `ServerConfiguration.java:123`, python `ValueError` at `server_configuration.py:99`, node `TypeError` at `server-configuration.ts:106`, go returns the sentinel `ErrInvalidServerVariable` at `server_configuration.go:20`, rust returns `ConfigurationError::InvalidServerVariable` at `server_configuration.rs:144`); the others not verified.

All paths below are relative to `src/main/resources/templates/<lang>/` for templates, to `src/spec/resources/generated/<lang>/` for goldens. Every language also regenerates every golden model that the new fixture schemas add, so each row's golden list is "the files named, plus the new `models/` files". Every language also touches the twelve test templates named in section 7 for its new catalogue ids.

| Language | Public names or signatures it exposes | Template files and golden files touched | Idiom and real language limit |
|---|---|---|---|
| java | `ServerConfiguration.getName(): @Nullable String`; `Servers.byName(String): ServerConfiguration` throwing `IllegalArgumentException`; QUERY and custom methods are ordinary operations (`queryThings(body)` plus its `...Options` and `...WithHttpInfo` like every operation, verified pattern in the run); union wrappers for type arrays use the existing `ofVariant` and `as...` factories (F878) | `server_configuration.mustache`, `servers.mustache`, `default_api_client.mustache` (empty-body verb comment at the golden `DefaultApiClient.java:510` names POST, PUT, PATCH), `base_api.mustache`, `api/api.mustache`, `models/model.mustache`; tests `test/ServerConfigurationTest`, `test/DefaultApiClientUnitTest`, `test/ComposedSchemaTest`, `test/MetadataTest`, `test/BaseApiTest`; goldens `src/main/java/com/example/petstore/ServerConfiguration.java`, `Servers.java`, `DefaultApiClient.java`, `api/BaseApi.java`, `api/*Api.java`, `models/*` | `HttpRequest.Builder.method(method, publisher)` takes any method string (verified, golden `DefaultApiClient.java:558`); `isIdempotentMethod` already lists `QUERY` (`default_api_client.mustache:1078`, so oasoperation009 already holds); JDK `HttpClient` refuses `CONNECT` only (not relevant, not verified). No real limit found |
| kotlin | `val name: String?` on `ServerConfiguration`; `Servers.byName`; operations as above | `server_configuration.mustache`, `servers.mustache`, `default_api_client.mustache` (empty-body check at line 553: `method in listOf("POST", "PUT", "PATCH")`), `base_api.mustache`, `api/api.mustache`; goldens `ServerConfiguration.kt`, `Servers.kt`, `DefaultApiClient.kt`, `api/BaseApi.kt` | Ktor `HttpMethod(method)` accepts any token (verified, `default_api_client.mustache:289`); the sealed-interface union shape of S4 is used for type arrays (not read for anyOf, not verified) |
| csharp | `string? Name { get; }`; `Servers.ByName`; operations as above | `server_configuration.mustache`, `servers.mustache`, `default_api_client.mustache`, `base_api.mustache`; goldens `ServerConfiguration.cs`, `Servers.cs`, `DefaultApiClient.cs`, `Api/BaseApi.cs` | `new HttpMethod(method)` accepts any token (verified, `default_api_client.mustache:708`); the empty-body verb set was not found by grep, so where `Content-Length: 0` is decided is not verified |
| php | `getName(): ?string`; `Servers::byName(string)`; operations as above | `server_configuration.mustache`, `servers.mustache`, `default_api_client.mustache`, `abstract_api_client.mustache` (line 366: `strtoupper($method)` then `'POST' || 'PUT' || 'PATCH'`), `base_api.mustache`; goldens `lib/ServerConfiguration.php`, `lib/Servers.php`, `lib/AbstractApiClient.php`, `lib/Api/BaseApi.php` | `Symfony HttpClient::request($method, ...)` takes a string (call verified at `default_api_client.mustache:382`; that it accepts arbitrary tokens is not verified). Typed properties reject null for a defaulted non-nullable field (FD-1), so a type-array model keeps the PHP typed-property rule |
| python | `ServerConfiguration.name: Optional[str] = None` (a dataclass field, verified shape in `server_configuration.py`); `servers.by_name(name)` raising `ValueError`; operations as above, all `async def` | `server_configuration.mustache`, `servers.mustache`, `default_api_client.mustache` (line 772: `method.upper() in ("POST", "PUT", "PATCH")`), `base_api.mustache`, `models/model.mustache`; goldens `petstore_client/server_configuration.py`, `servers.py`, `default_api_client.py`, `api/base_api.py` | `urllib3` `PoolManager.request(method, ...)` takes a string (call at `default_api_client.py:799`); whether urllib3 or `http.client` folds the case of a custom token is not verified (R5). `exclusiveMinimum` already becomes pydantic `gt=` (run) and stays, per S3 |
| ruby | `attr_reader :name` on `ServerConfiguration`; `Servers.by_name` raising `ArgumentError`; operations as above | `server_configuration.mustache`, `server_variable.mustache`, `servers.mustache`, `default_api_client.mustache` (line 313 `%w[POST PUT PATCH]`), `base_api.mustache`, `infrastructure_rbs.mustache`, RBS signatures for every new method (Steep) | custom verbs already work: `dispatch` builds the request for any verb Faraday's `run_request` refuses (verified `default_api_client.mustache:584-596`, documented in `ruby/AGENTS.md:316`), so C15 and C16 need no transport change; the Zeitwerk constant test must be extended for any new file (`ruby/AGENTS.md`) |
| node | `readonly name?: string`; `byName(name)` throwing `TypeError`; operations as above | `server_configuration.mustache`, `servers.mustache`, `abstract_api_client.mustache` (line 494: `'POST' \|\| 'PUT' \|\| 'PATCH'`), `base_api.mustache`, `api/apis.mustache` (line 363 passes `'{{httpMethod}}'`), `models/model.mustache`; goldens `src/server-configuration.ts`, `servers.ts`, `abstract-api-client.ts`, `api/base-api.ts` | `fetch` normalises the case of the six standard methods and passes other tokens unchanged (platform fact, not verified in the template); a union of types is a TypeScript union alias (`generators/AGENTS.md` idiom matrix); the model template has `@Type(() => Holder_unionStrInt)` today (run), so C47 must land before type arrays |
| dart | `final String name` with the empty string for none (FD-14: dart, go, rust and swift use the empty string for an absent description, verified `generators/AGENTS.md:133`); `Servers.byName` | `server_configuration.mustache`, `servers.mustache`, `abstract_api_client.mustache` (line 271: `upper == 'POST' \|\| ...`; line 249 `http.Request(method, uri)`), `base_api.mustache`, `default_api_client_io.mustache`, `default_api_client_web.mustache` | `http.Request(method, uri)` takes any string (verified); the web client template is separate (`default_api_client_web.mustache`) and its handling of custom methods is not verified |
| go | `(*ServerConfiguration).Name() string` (empty string for none, FD-14); `ServerByName(name string) (*ServerConfiguration, error)` returning a new sentinel `ErrUnknownServer` next to `ErrInvalidServerVariable`; operations as above | `server_configuration.mustache`, `servers.mustache`, `default_api_client.mustache` (line 283: `upper == "POST" \|\| ...`), `base_api.mustache`, `api/api.mustache` (line 539 `method: "{{httpMethod}}"`), `models/model.mustache`; goldens `pkg/server_configuration.go`, `servers.go`, `default_api_client.go`, `base_api.go` | `http.NewRequestWithContext(ctx, method, ...)` accepts any token (verified, `default_api_client.go:325`); go returns errors by idiom, a forced divergence already recorded for server variables; go already resolves reference names through `toModelName`, so C47 is the one language that needs nothing for it (run: `HolderUnionStrInt` correct) |
| rust | `ServerConfiguration::name(&self) -> &str` (empty string for none, FD-14); `servers::by_name(&str) -> Result<ServerConfiguration, ConfigurationError>` with a new `UnknownServer` variant; operations as above | `server_configuration.mustache`, `servers.mustache`, `default_api_client.mustache` (line 344: `upper == "POST" \|\| ...`; line 299 `method.parse::<reqwest::Method>()`), `base_api.mustache`, `api/api.mustache` (line 908), `models/model.mustache` | `reqwest::Method` parses extension methods (the parse call is verified in the golden `default_api_client.rs:299`; that every token parses is a property of the `http` crate, not verified); `Option<null>` and the union struct names do not compile today (run) |
| swift | `public let name: String` with empty string for none (FD-14); `Servers.byName` | `server_configuration.mustache`, `servers.mustache`, `default_api_client.mustache` (line 482: `upperMethod == "POST" \|\| ...`; line 455 `request.httpMethod = method`), `base_api.mustache` | `URLRequest.httpMethod` is a free string (verified at line 455); Foundation's treatment of custom tokens on the wire is not verified |
| elixir | `name: String.t() \| nil` in the `ServerConfiguration` struct; `Servers.by_name/1` raising `ArgumentError`; operations as above | `server_configuration.mustache`, `servers.mustache`, `default_api_client.mustache` (line 258: `in ["POST", "PUT", "PATCH"]`), `base_api.mustache` (line 227 turns the method into a lowercase atom), `api/api.mustache` (line 527 `:{{httpMethod}}`) | the method becomes an atom folded to lower case before Req is called (verified `base_api.mustache:227`), so an exact-case custom token (C16) may not survive; whether Req and Finch upper-case an atom is not verified. This is the one language where C16 may be a real limit (R5) |

## 7. The tests

Rules applied: unit tests mirror the source file, specs belong to the generated SDK suites and carry catalogue ids, no catch-all file, and every fix is backed by a test that fails without it (`generators/AGENTS.md`, "Test layout"; global rules).

**Generator unit tests** (JUnit, `src/test/java/io/github/mridang/codegen/...`; models are built inline with the fluent API or from an inline YAML string, as `AdvancedOpenAPINormalizerTest` and `AbstractBetterCodegenTest` do):

| Test class | What it asserts |
|---|---|
| `Oas32ParserExtensionTest` (new, mirrors `Oas32ParserExtension`) | a 3.2.0 string loads to a document with `openapi: 3.1.0`; a 3.1 string returns null from the extension; `$self` is recorded and removed; `query` produces a path `/things#oas32:QUERY` with the path item's parameters and servers copied; `additionalOperations` keeps the exact token; `in: querystring`, `style: cookie`, `itemSchema`, a reference to `components.mediaTypes`, Server `name`, Tag `parent`, `deviceAuthorization` and `defaultMapping` each become their carrier; a 3.2 document with no 3.2 construct generates the same model as its 3.1 twin |
| `Oas32RewriterTest` (new, mirrors `Oas32Rewriter`) | one case per row of the table in 5.2, on a tree |
| `rules/NormalizeTypeArrayRuleTest`, `rules/NormalizeNullTypeRuleTest`, `rules/NormalizeConstRuleTest` (new) | `[string, integer]` becomes an anyOf of two; `[array, object]` keeps `items` on the array member and `properties` on the object member; `type: "null"` becomes untyped nullable; typeless `const: 3` becomes an integer enum; a `oneOf` of three `const` members becomes one enum with the titles kept; a schema that is not one of these is untouched |
| `AbstractBetterCodegenTest` (extend) | webhooks are removed and the untagged operations of the document survive (the failing-before case is the `DefaultApi` overwrite found in the run); a `$ref` into `components/pathItems` yields the operations at the referencing path; a path item referenced from two paths yields one operationId and one stock id plus a warning; path item `summary` reaches an operation with none; a carrier path is converted back to the real path and exact method in `fromOperation`; `licenseId` defaults to `info.license.identifier` and an explicit option wins; `processServers` fills `serverName` and rejects an invalid template with the message; `getSchemaType` returns the cased name for `my_widget`, `Holder_loc` and `Holder_unionStrInt` |
| `SpecWarningsTest` (extend) | each keyword of C45, C49, C50 warns once with its pointer; a property named `not` or `contains` does not (`NAME_KEYED`); the pass runs on a plain generation, not only inside the normaliser |
| `GeneratedFileLayoutTest`, `FixtureVocabularyLeakTest` (extend the existing cases, no new class) | the snake-case component and the inline composed property generate in all twelve and no petstore word leaks |

**Catalogue ids and where each is asserted** (each language adds a test named from the id in the template that already holds its area; a language that cannot gets a row in `test-catalogue-exceptions.txt` with the forcing fact, never a dropped id):

| Ids | What they assert | Test template (same name pattern in all twelve, for example java `test/<File>.mustache`) |
|---|---|---|
| `oasschema001`-`oasschema003`, `oasschema007`, `oasschema008` | decode and encode of the new fixture models `OasConstHolder`, `OasColour`, `OasNullHolder` (neutral names, petstore document) through the object serializer | `MetadataTest` (java; same role in the other eleven, `generators/AGENTS.md` and the `bhvmodels` references found in `go/test/metadata_test.mustache`, `python/test/test_metadata.mustache`, `rust/test/metadata_test.mustache` and nine more) |
| `oasschema004`, `oasschema005`, `oasschema006`, `oasschema010` | the union wrapper decodes each member, writes it back unchanged, and a required nullable key is written as null | `ComposedSchemaTest` (java; `go/test/composed_schema_test.mustache`, `python/test/test_composed_schema.mustache`, `rust/test/composed_schema_test.mustache` and nine more) |
| `oasschema009` | a model declared with a snake-case name is reachable under its cased name and decodes | `MetadataTest` |
| `oasoperation001`-`oasoperation004` | the recorded request carries `PATCH`, `HEAD`, `OPTIONS`, `TRACE`; HEAD has no body and exposes the headers (chasm answers HEAD from the GET operation and synthesises OPTIONS, verified in `crates/chasm-engine/src/router.rs`) | `DefaultApiClientTest` plus the api test of the new fixture operations |
| `oasoperation005`, `oasoperation006` | a request for a path item reached through `$ref` and for an operation under a path-level server hits the expected URL | `BaseApiTest` |
| `oasoperation007`-`oasoperation010` | `QUERY` and a custom token reach the transport exactly; the empty body carries `Content-Length: 0`; replay on a stale connection | `DefaultApiClientUnitTest` (java line 1950 already asserts the idempotent part, so java needs only the id on it) |
| `oasparam001`-`oasparam003` | the serialised bytes of a querystring parameter, a cookie parameter with style cookie and an allowReserved path parameter | `ValueSerializerTest` (java; same role in the other eleven) |
| `oasserver001`, `oasserver002` | selection by name and the error for an unknown name | `ServerConfigurationTest` |
| `oasdocs001`, `oasdocs002` | the README and SKILLS text | `ReadmeTest`, `SkillsTest` (java; the other eleven have the same pair, not verified per language) |

**Goldens and gates.** The petstore fixture gains: the snake-case component, an inline type-array property, a `const` property, a `oneOf` of constants, a `type: "null"` property, a typeless property, a `HEAD`, an `OPTIONS`, a `TRACE` and a `PATCH` operation (non-internal), a path item reference, a path-level server, an `info.license.identifier` that differs from `MIT`, and a `webhooks` entry beside an untagged operation (the golden must still contain that operation: the regression for C9). All twelve goldens are regenerated in one commit. `etc/ci-local.sh check <lang>` per language, and `TestParitySpec` and the catalogue check then enforce that every id exists in all twelve. The 3.2 fixture is a second document, `src/spec/resources/specs/oas32/openapi.yaml`, used only by `Oas32ParserExtensionTest` and by one new case in `ProbeGenerator`-based generation that asserts the generated source of all twelve contains the QUERY and custom method strings; it has no goldens and is not served by chasm.

## 8. Risks and open questions

Each item is real; the recommended answer is stated.

- **R1. Any component or inline schema name that is not PascalCase produces a type name that matches no class, in 10 of 12 languages (run).** It blocks type arrays (the stock normaliser names the wrapper `Parent_prop`) and any inline composed property, and it affects 3.0 documents today. No existing test catches it because the petstore fixture has no such name. Recommended: fix `getSchemaType` first (section 4, item 1), before any 3.1 work, and add the two fixture schemas in the same commit. Cause confirmed by comparison with the stock Java generator (`toModelName` applied in `getSchemaType`); the exact point where each of the ten languages loses the cased name is not verified, so the fix is proved by the regenerated goldens, not by reading.
- **R2. The 3.2 loader is bespoke and carries the QUERY and custom-method operations on synthetic path keys.** No released swagger-parser or swagger-models can read 3.2 (verified, section 3.1), and the `HttpMethod` enum is final, so `DefaultGenerator` cannot be given a ninth method. Recommended: keep all of it in `Oas32ParserExtension` and `Oas32Rewriter`, one table (5.2), delete both when upstream ships; the marker `#oas32:` is consumed in `fromOperation` and never reaches a template or a log of operations. Rejected: forking swagger-models (a second copy of 150 model classes to keep in step); rewriting QUERY as POST with a header (a wrong method on the wire).
- **R3. The mock server rejects 3.2 and cannot route QUERY.** Chasm 1.3.0 refuses any version outside 3.0.x and 3.1.x and has no QUERY in its typed path item. Recommended: the petstore stays `3.1.0`; 3.2 behaviour is asserted on the recorded request and on the generated source; extend chasm in its own repository only if the owner wants end-to-end 3.2 tests later. Decision needed: whether the second fixture `specs/oas32/openapi.yaml` is acceptable given the rule that every fixture lives in the petstore document (recommended yes, because the petstore must stay servable by chasm and the 3.2 document is never served).
- **R4. A path item referenced from two paths repeats its operationIds.** `operationId` must be unique per document. Recommended: the first path keeps it, later ones get the stock generated id (`<path><Method>`) and a warning; the alternative (fail generation) would reject documents that the spec allows.
- **R5. A custom method token must keep its case, and three languages may fold it.** Elixir lower-cases the method atom in `base_api.mustache:227`; python's urllib3 and the Foundation and fetch stacks are not verified. Recommended: a first task of phase 2 writes the oasoperation010 test in each language against its transport and records the result; a language that folds the case gets a forced-divergence entry with the library fact (`FD-n`), and the generator warns at generation time when a token is not upper case and the target language folds it. Do not promise exact-case in the README until all twelve are verified.
- **R6. The warning pass does not run on a normal generation today** (run: no "Spec warning" line for a document with `webhooks`), although `docs/how-it-works.md` says it does. Recommended: call it from `processOpenAPI` (section 4, item 7) and correct the page in the same pull request; strict mode (`-DstrictSpec=true`) then fails a run on the new warnings, which is what a user of strict mode asked for.
- **R7. (Resolved in #100.) `NormalizeOas31NullableTypeRule` was unused; it was removed, and the stock normaliser is the code path that sets the flag.** Only its own test references it (grep over `src` and `docs`); the stock normaliser is what makes `nick` nullable in the run. Recommended: do not delete it in #100 until the first test of the nullable-query design (it proves where `isNullable` comes from) has run; then delete the rule and correct that design if the stock path is the source. Either answer keeps the type-array fixtures green, which is the acceptance test of #100.
- **R8. Declines need the owner.** C6 (contact, terms of service), C33 (digest and negotiate), C48 (typed tuples), C49 (validation keywords), C53 (extension exposure) and C54 (validation) are declined on existing decisions (S3, the feature-flag decision, Gap AZ); they go to `docs/decisions.md` and the READMEs only after the owner approves each one.
- **R9. Five features are public API additions that need their own design before any code:** response media type selection (C24), typed response headers (C28), ordered multipart (C25), Encoding Object headers and styles (C26), and the device flow (C36). Their shape (a new argument, a new result type, a new authenticator) is an owner decision and each needs the twelve-language table this document gives only for the small features. Recommended: write one design per feature, in the order C28, C24, C26, C25, C36, after phase 3 lands, and do not size them here beyond L.
- **R10. Public API surface guard missing.** Server names, a querystring argument and the verb sets change public members of twelve languages and the guard of #122 does not exist (`AGENTS.md`, "No public API guard yet"). Recommended: compare the twelve `ServerConfiguration` and `Servers` surfaces by hand once the goldens are regenerated, as the root file asks, and record the result in the pull request.
- **R11. The streaming design is a hard dependency of C22 and C23.** `itemSchema` has no consumer until `docs/design/streaming.md` is accepted. Recommended: land only the loader mapping (C22) with a unit test, and leave the stream methods to that design.
- **R12. Docker formatter drift.** Regenerating on a laptop shows spurious php and swift changes; a run that changes more than a hundred unrelated files is a missing formatter container (`docs/how-it-works.md`). Recommended: regenerate with all twelve formatters, commit all twelve goldens at once.

## 9. The order of work and a size per language

Sizes: S is a few template lines plus test names and a golden; M is several templates, tests and docs for one feature; L is a new subsystem with its own design. A pull request per phase, one commit per batch, all twelve goldens current in each (`AGENTS.md`, "Working method").

**Phase 0, shared only, one pull request.** The naming fix (R1) with the two new fixture schemas; drop webhooks (C9); make the warning pass run (R6) with the new warnings (C45, C49, C50); `info.license` and `summary` (C4, C5, C7); the three schema rules (C39, C40, C41, C42) with their fixtures and the catalogue ids `oasschema001`-`oasschema010`. Per language: tests and goldens only, except that every language needs its golden regenerated and, if R1 is not fixed by the shared change for that language, a template edit (not verified which).

**Phase 1, shared only.** Path item references and `summary` (C11, C12); HEAD, OPTIONS, TRACE, PATCH fixtures (C13); path-level servers (C14); no-paths and no-servers fixtures (C8); `oasoperation001`-`oasoperation006`; docs. Per language: tests and goldens.

**Phase 2, the loader and the methods.** `Oas32ParserExtension` and `Oas32Rewriter` (C2, C3, C21, C27, C30); QUERY and `additionalOperations` (C15, C16) with the empty-body verb set in eleven languages, `oasoperation007`-`oasoperation010`, the R5 verification in all twelve; the `docs/how-it-works.md` section. Per language: one line in the verb set, a test, a golden.

**Phase 3, small per-language features.** Server name (C31, `oasserver001`-`oasserver002`), querystring parameters (C17), cookie style (C18), allowReserved on paths (C19), deprecated scheme and `oauth2MetadataUrl` (C34, C35), discriminator default (C52), documentation features (C20, C29, C30, C32). Per language: M.

**Phase 4, features that need a design first (R9)** and the streaming design. L per language each, none scheduled here.

| Language | Phase 0 and 1 | Phase 2 | Phase 3 | Phase 4 | Why |
|---|---|---|---|---|---|
| java | S | S | M | L | transport already takes any method; idempotent list already has QUERY; servers, `base_api` and the oauth classes to edit |
| kotlin | S | S | M | L | `HttpMethod(method)` accepts any token; one verb-set line (553) |
| csharp | S | S | M | L | `new HttpMethod(method)`; where the empty body is decided is not verified |
| php | S | S | M | L | verb set at line 366; typed properties (FD-1) need a look at nullable type arrays |
| python | S | S | M | L | verb set at line 772; urllib3 case folding to verify (R5) |
| ruby | S | S (none for transport) | M | L | custom verbs already work; RBS declarations and the Zeitwerk constant test for every new member |
| node | S | S | M | L | naming fix lands in the model template (`@Type(() => ...)`); verb set at line 494 |
| dart | S | S | M | L | verb set at line 271; the web client template is separate and not verified |
| go | S (naming already right) | S | M | L | verb set at line 283; unknown server name needs a new sentinel error |
| rust | S | S | M | L | verb set at line 344; new `ConfigurationError` variant |
| swift | S | S | M | L | verb set at line 482; Foundation's handling of custom tokens not verified |
| elixir | S | M | M | L | method atom is lower-cased (`base_api.mustache:227`): C16 may be a forced divergence |

Count: through phase 2, no language is tests or docs only: eleven need a one-line verb-set edit in a template (ruby needs none for the transport but needs the RBS and test work) and elixir needs a decision on the atom; all twelve need template code in phase 3 (server name, parameters, authenticators). The shared generator carries phases 0 and 1 almost entirely.
