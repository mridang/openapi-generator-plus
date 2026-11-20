# Enum member names and descriptions design (issues #149 and #150)

Status: proposal for owner review. Nothing here is built. It fixes one shape for all twelve SDKs (java, kotlin, csharp, php, python, ruby, node, dart, go, rust, swift, elixir) so that no language is done first and the other eleven made to follow. Written against the worktree `feat/ogen-designs` at commit `3ed45f1ac`, with OpenAPI Generator 7.22.0 (the version in `pom.xml`; its `DefaultCodegen.java` was read from the sources jar). The root has no `CLAUDE.md`; the rule "templates never read vendor extensions" is in the root `AGENTS.md`, section "Template and generated-prose rules". `docs/comparisons/orval.md`, which both issues cite, is not in this worktree and not on `origin/master`, so it cannot be updated here; `docs/faq.md` does not exist either.

## Goal

An author who writes `x-enum-varnames`, `x-enumNames` or `x-enumnames` on an enum gets exactly those members, in the casing and the form each language's developers expect, in all twelve SDKs, and a spec without the keys generates byte-identical code. An author who writes `x-enum-descriptions`, `x-enumDescriptions` or `x-enumdescriptions` gets each description as the one-line doc comment directly above its member, again in all twelve. The generator reads the keys once, in `AbstractBetterCodegen`, and hands the result to the templates as plain `enumVars` fields (`name`, `enumDescription`), so no template ever reads an `x-` key.

## What the code does today (read, not assumed)

This changes the picture the issues paint, so it comes first.

- Upstream already reads two of the six keys. `DefaultCodegen.updateEnumVarsWithExtensions` (7.22.0, line 7033) applies `x-enum-varnames` to `name` and `x-enum-descriptions` to `enumDescription`, an array by index and an object by value. It runs for top-level enum models (`postProcessModelsEnum`, line 850) and for inline and `$ref`'d property enums (`updateCodegenPropertyEnum`, line 6926; a `$ref` property reads the referenced schema's extensions). `AbstractBetterCodegen` does not override it (no `x-enum` text anywhere under `src/main`), so it is live in all twelve today.
- It is wrong in three ways. (1) The supplied name replaces the derived one after sanitising, casing and de-duplication, so `x-enum-varnames: [on-hold]` writes `on-hold` into Java as a constant name, and two equal names are never separated. (2) It never reads `x-enumNames`, `x-enumnames`, `x-enumDescriptions` or `x-enumdescriptions`. (3) `enumDescription` reaches `enumVars` but no template reads it (no hit for `enumDescription` under `src/main/resources/templates`).
- Names derived from the wire value are also computed again by hand in six places, so a supplied name would make a default point at a member that does not exist: `captureRefSiblingDefault` (`AbstractBetterCodegen.java:4905`, feeds `siblingDefaultEnumVar`, which java, kotlin, csharp, php, go, rust, ruby, node, swift and elixir templates read), `BetterNodeCodegen.fixEnumDefaultValue` (line 908), `BetterRustCodegen.toDefaultValue` (line 702) and `seedSiblingDefault`/inline rewrite (line 943), `BetterDartCodegen.resolveEnumRefDefault` (line 914) and `BetterGoCodegen` inline enum parameters (line 978). Java, kotlin, csharp and python take an inline default from the final `enumVars` (upstream, `DefaultCodegen.java:6930-6941`), so they follow the supplied names for free.
- `enumNameMapping` (the upstream `--enum-name-mappings` option) is honoured only by `BetterNodeCodegen.toEnumVarName` (line 857); no other codegen reads it.
- No spec under `src/spec/resources/specs` carries any of the six keys. The golden enums (`Priority`, `Availability`, `Separator`, `Color`, `Swatch`) are referenced by many existing tests by their derived names (for example `Priority.NUMBER_1`, `Availability.ON_HOLD`), so the keys must not be added to them.

## Owner decisions that bind this work

- Clients only, twelve languages, identical capabilities and API shape; only idiom differs. A difference between two languages is a defect until a language feature forces it (root `AGENTS.md`).
- No client-side validation: nothing about names or descriptions is checked at construction; unknown enum values still fail on decode with the SDK's `SerializationError` naming the value (decision F835, `docs/decisions.md`).
- Typed errors are thrown, never returned; no new error type is needed here.
- Native types where the language has one: members stay the language's native enum, const object, constants, atoms or constant class (`docs/types.md`, enum row). Nothing is added to model them differently.
- Templates never read vendor extensions and are Mustache files. Computed values reach a template through a native field; `enumVars` entries are plain maps and already carry computed keys (`nameLowercase`, set at `AbstractBetterCodegen.java:2212`), which is the precedent used here.
- Never edit `.github/workflows`.
- The shared test catalogue `src/spec/resources/test-catalogue.txt`: one behaviour id per behaviour, each language names a test from the id or has a documented line in `test-catalogue-exceptions.txt`.
- Do not add test files: extend the test templates that exist; a fix must make the petstore tests of all twelve catch the bug class. Fixtures live only in `src/spec/resources/specs/petstore/openapi.yaml`; nothing is removed or renamed.
- Goldens are committed all twelve current, regenerated, never hand-edited. Docs are updated in the same pull request (root `AGENTS.md`, "README and docs are part of every change").
- Decisions are written into `AGENTS.md` (shared file: ids continue after D94) and never into an issue alone; `docs/comparisons` and per-language sections get the owner-approved text.

## The shared behaviour

Catalogue lines (new area `enums`; the id pattern `bhv<area><nnn>` accepts a new area). All twelve languages carry a test for each unless an exception line says otherwise. The fixture is described under "The tests".

```text
enums001 | A string enum member named by an array of names (x-enumNames) exposes the given name and keeps its wire value
enums002 | An integer enum member named by an array of names (x-enum-varnames) exposes the given name and keeps its number
enums003 | An enum named by an object keyed by wire value (x-enumnames) names the members it lists and derives the rest
enums004 | A supplied name that is not a legal identifier is cleaned and cased like a derived name
enums005 | Two members given names that collide after casing keep distinct names and their own wire values
enums006 | An inline property enum takes its member names from its own keys
enums007 | A default on a $ref enum property starts at the member that has the supplied name
enums008 | Encoding writes the wire value and decoding rejects a supplied name with a SerializationError naming it
enums009 | A member with a description carries it as the one-line doc comment directly above that member
enums010 | A member without a description keeps the comment it has today
enums011 | A description holding comment terminators or interpolation sigils still yields a compiling doc comment
```

Rules behind the lines (written once into the shared `AGENTS.md` as D95 and D96 when accepted):

1. Spellings. Names: `x-enum-varnames`, `x-enumNames`, `x-enumnames`. Descriptions: `x-enum-descriptions`, `x-enumDescriptions`, `x-enumdescriptions`. Exact spelling, no case folding. If more than one spelling is present the first in the order written here wins as a whole (no merge across spellings) and generation logs one warning naming the schema and the ignored keys. The order puts upstream's own key first because it already wins today.
2. Forms. An array applies by index against the schema's own `enum` array, including a `null` entry that a nullable 3.0 enum lists (so the index of a name is the index of its value in the spec, not in the generated list). An object applies by wire value: the key is compared with the value's canonical text (`1` for the integer 1). A member the key does not cover keeps its derived name and its current comment. A missing, `null`, blank or non-string entry means "not supplied".
3. Names. A supplied name is treated as a source word, not as final text: it goes through the same pipeline as a derived name (symbol map, sanitising, the language's enum casing, the digit-leading `n` letter of D43, reserved-word escape of D44, the language's own `toEnumVarName` overrides), then through `dedupeEnumNames`. So `NUMBER_1` is `NUMBER_1` in Java and `Number1` in Rust; `on hold` is `ON_HOLD`, `OnHold`, `onHold` or `on_hold`; two names equal after casing become `X` and `X_2` (`X2` where the casing has no underscore). The wire value is never touched.
4. Descriptions. The text is HTML-unescaped and escaped exactly like a schema `description` (D41), so each template wraps it in the same lambdas it already uses for its description (`unescapeDocComment` plus the language's own escaper: `escapeXml`, `phpdocDescription`, `rubyDocComment`, `escapeKdoc`/`rustdoc`/`docLines`). It is folded onto one line with the shared `singleLine` lambda (`AbstractBetterCodegen.java:2249`), so a `///`, `#` or `//` comment never breaks and the twelve read alike. An empty or blank description is never written into `enumVars`: JMustache treats an empty string as true unless the compiler sets `emptyStringIsFalse`, so `{{#enumDescription}}` would open an empty comment.
5. Comment content. A member with a description gets that text and nothing else. The generic per-member comments that java (`The {@code 1} value.`), dart, go and rust (`Represents the value X.`) write today stay for members without a description, so a spec with no keys is byte-identical. Open question 2 asks whether to keep the wire value in the comment.
6. Defaults. Every place that names the member for a default (a `$ref` default, a sibling default, an inline default, an inline parameter enum) resolves it through one lookup, so a default can never name a member that was not declared.
7. Warnings, never failures: an array longer than the enum, an object key that matches no value, two spellings present, and a supplied name that cleans to nothing (it falls back to the derived name and is reported). They go through the existing spec warning pass (`SpecWarningsTest` exists; where the pass lives is not verified).

## The shared generator changes

All in `src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java` unless stated. Changing it changes every SDK, which is the point.

| Change | What and why |
|---|---|
| Key tables | Two ordered constant lists for the six spellings. |
| `buildEnumVars(values, dataType)` override | Calls `super`, then writes the raw wire text under a key the templates never use (`wire`) on each entry, index-aligned with the schema's `enum` array. An object-form key needs the raw value; upstream's `value` is already quoted and escaped. |
| `updateEnumVarsWithExtensions(enumVars, vendorExtensions, dataType)` override (a `protected` method in 7.22.0, line 7033) | Replaces upstream's behaviour for both families: picks the winning spelling, resolves an array by index or an object by `wire`, sets `name` through the new method below, sets `enumDescription` only for non-blank text, then runs `dedupeEnumNames(enumVars, "name")` again because upstream de-duplicates before this step. With no key present it does nothing, which keeps every current golden unchanged. |
| `toSuppliedEnumVarName(String supplied, String datatype)` | Calls `toEnumVarName(supplied, "String")` so every language override applies (Dart camel, Go empty-name guard, Rust and Swift casing). `String` is passed so a numeric enum does not prefix `NUMBER_`. |
| `enumMemberName(Schema enumSchema, String wire, String datatype)` and `enumMemberNameForWire(CodegenProperty, String wire)` | One lookup used by default resolution: the first reads the same key tables, the second reads a property's final `enumVars` (the existing `enumWireValueForVariant` at line 2345 is its reverse). Both sit next to that helper. |
| `captureRefSiblingDefault` (line 4905) | `siblingDefaultEnumVar` is computed with `enumMemberName(target, wire, "String")` instead of `toEnumVarName(wire, "String")`. This one change fixes the default for java, kotlin, csharp, php, go, rust, ruby, node, swift and elixir, whose templates read that field. |
| Warnings | The seven cases in rule 7. |
| `BetterNodeCodegen.fixEnumDefaultValue` (line 893) | Looks the member up with `enumMemberNameForWire` instead of `toEnumVarName(raw, ...)`. `enumNameMapping` stays first (open question 4). |
| `BetterRustCodegen` (lines 702 and 943) | Both call the lookup; line 702 has the schema, line 943 has the property. |
| `BetterDartCodegen.resolveEnumRefDefault` (line 914) | Calls `enumMemberName(referenced, value, datatype)`. |
| `BetterGoCodegen` inline enum parameter (lines 960 to 995) | Builds `constName` from the supplied name when the parameter's schema carries one. Whether `CodegenParameter` carries the schema extensions here is not verified; the fallback is to read the parameter schema from `openAPI`. |
| Reserved member names | Ruby `VALUES`, Dart `value`, Swift `description`, Elixir `all_values` are declared by the enum template itself (ruby `models/model.mustache:30`, dart `models/model.mustache:101`, swift `models/model.mustache:22`, elixir `models/model.mustache:28`). A member with that name collides. This is latent for derived names today (no `VALUES` match in `BetterRubyCodegen.java`; the others not verified); supplied names make it easy to hit, so each of the four codegens adds the name to its enum reserved set. |

No `x-` key is read outside `AbstractBetterCodegen`. No template is given an `x-` key. The enum name lookups never touch `vendorExtensions` on the property.

## Language table

"Names" is what a spec-author sees for `Tier` (values `s`, `m`, `l`, `x-enumNames: [Small, Medium, Large]`). "Templates" lists the enum sites that must carry the description comment (all under `src/main/resources/templates/<lang>/`, line numbers are today's `{{#enumVars}}` blocks). "Goldens" lists what moves in `src/spec/resources/generated/<lang>/`: for every language the existing model files stay byte-identical, and new files appear for the four new fixture models (`tier`, `rank`, `stage`, `subscription`, in the language's file naming) plus its model index or barrel and the regenerated serializer test file. "Limit" says `verified in <file>` only where the file was read.

| Language | Names exposed | Templates (enum sites) | Idiom and real limit |
|---|---|---|---|
| java | `Tier.SMALL`, `Tier.MEDIUM` | `models/model.mustache`: 22, 76, 129 (top level), 705, 759, 812 (inline); six sites | UPPER_SNAKE (verified in `BetterJavaCodegen.java:427`). A `/** */` per member already exists with the generic text (verified in `models/model.mustache:23`); `escapeText` flattens newlines and turns `*/` into `*_/` (verified in `java/AGENTS.md:198`). Defaults follow `enumVars` (verified `DefaultCodegen.java:6930`). No limit found. |
| kotlin | `Tier.SMALL` | `models/model.mustache`: 29, 66, 102, 922, 957, 991; six sites | KDoc must sit above the `@SerialName(...)` line on the string form (verified in `models/model.mustache:103`); `escapeKdoc` neutralises both `*/` and `/*` because Kotlin comments nest (verified in `BetterKotlinCodegen.java:317` and `kotlin/AGENTS.md:191`). `fixEnumDefaultValue` is a no-op (verified at line 851), so defaults follow `enumVars`. |
| csharp | `Tier.Small` | `models/model.mustache`: 21, 458, 758 and `integer_enum.mustache`: 12; four sites, each with its converter that switches on `{{name}}` (those need no change) | `/// <summary>` through the `escapeXml` lambda (verified in `models/model.mustache:13` and `BetterCSharpCodegen.java:331`). PascalCase (verified at line 221); the integer path lowercases then cases (line 273). Existing note that integer-member casing is "parked" (`csharp/AGENTS.md:283`) conflicts with the code at line 273; not reconciled here. |
| php | `Tier::SMALL` | `models/model.mustache`: 23, 44, 64 and `models/inline_enum.mustache`: 10 (written from `BetterPHPCodegen.java:2093-2105`); four sites | Native backed `enum`, UPPER_SNAKE, `V_` prefix for digit-leading names (verified in `php/AGENTS.md:43`). A docblock before `case` is valid PHP; the `phpdocDescription` lambda (verified at `BetterPHPCodegen.java:467-481`) is the escaper. Server-variable enum cases use a separate path (`toPhpEnumCaseName`, line 580) and are out of scope. |
| python | `Tier.SMALL` | `models/model.mustache`: 18, 359; two sites | `str, Enum` or `int, Enum` (verified in `models/model.mustache:12`). Python has no per-member doc syntax, so the comment is a `#:` line above the member (open question 5); the generated `make docs` is Sphinx with napoleon (verified in `python/Makefile` lines 26 to 51), and that autodoc reads `#:` is Sphinx behaviour, not verified in this repo. Defaults are class-prefixed references over the final `enumVars` (verified in `BetterPythonCodegen.java:1241`). |
| ruby | `Tier::SMALL` | `models/model.mustache`: 26, 457; two sites, both written as one line `class X{{#enumVars}}` that must be restructured so a comment line can precede each constant | `#` comments, YARD with `--fail-on-warning` (verified in `ruby/Makefile:21`), so a description that begins with `@` would raise a tag warning; the `rubyDocComment` lambda (verified at `BetterRubyCodegen.java:1058`) is the guard. `VALUES` collision above. The RBS file (`model_rbs.mustache:7`) needs no change. |
| node | `Tier.Small` | `models/model.mustache`: 30 and `api/apis.mustache`: 411 (parameter enums); two sites | A `const` object plus a literal union (verified in `node/AGENTS.md:365`); `/** */` above a key is TSDoc. The model template ends every key with a comma, the apis template does not (lines 31 and 409). `enumNameMapping` is read first (verified in `BetterNodeCodegen.java:857`). Default fix needed (line 908). Integer members `Number1` (verified in `BetterNodeCodegenTest.java:185`). |
| dart | `Tier.small` | `models/model.mustache`: 27, 61, 94 (top level use `{{name}}`), 144, 174, 203 (inline use `{{nameLowercase}}`); six sites | A `/// Represents the value X.` already exists per member (verified in `models/model.mustache:28`). camelCase, and the analyzer's `constant_identifier_names` is why `NUMBER_1` becomes `number1` (verified in `BetterDartCodegen.java:296-303`). `value` collision above (template declares `final String value;`, verified). Default fix needed (line 914). Top level and inline use different name keys; not harmonised here. |
| go | `models.TierSmall` | `models/model.mustache`: 23 and 34 (top level; comment is a `/* */` block above each constant, line 24), 115 and 125 (inline); two sites carry the comment | Constants are `<Type><Name>` so a supplied name rarely collides (verified in `models/model.mustache:25`). The inline enum parameter constants are built in Java (`BetterGoCodegen.java:960-995`) from `p._enum`, not from `enumVars`, so they need the lookup. The generic comment is `<Const> represents the value X.`; a described member replaces it. Go deprecation of `/* */` in favour of `//` is not applied here. |
| rust | `Tier::Small` | `models/model.mustache`: 28, 87 (string enums), `integer_enum.mustache`: 17; three comment sites | `/// Represents the value X.` exists per variant (verified at `models/model.mustache:29`); a doc comment must stay on one line (the `docLines` and `rustdoc` lambdas are already used for descriptions, verified in `models/model.mustache:21`). Names PascalCase, integer members `Number<n>` (verified in `BetterRustCodegen.java:1255`). Default fixes needed (lines 702 and 943). An inline integer enum is not handled by the template (it writes `#[serde(rename = ...)]`): not verified whether it can occur. |
| swift | `Tier.small` | `models/model.mustache`: 15, 47; two sites | `///` before `case`; lowerCamel for integer members (verified in `BetterSwiftCodegen.java:190`). A member named `description` collides with `public var description` (verified in `models/model.mustache:22`). A `$ref` default is `Tier(rawValue: ...)` by wire (verified in `BetterSwiftCodegen.java:828`), but the sibling default (`models/model.mustache:202`, `390`) uses `siblingDefaultEnumVar`, so it gets the shared fix. |
| elixir | `:small` and `Tier.small/0` | `models/model.mustache`: 20 to 24 (`@doc` per function) and 455 to 459 (inline); two sites | Atoms are the public API: `nameLowercase` is `name` lowercased (verified at `AbstractBetterCodegen.java:2212`), so supplied names flow into the atoms, `value/1`, `from_value/1`, `@type` and the `Enum<name=wire,...>` string in `openapi_types` (verified in `models/model.mustache:36, 52, 609`). The description replaces the text of `@doc "Enum value ..."`; escaping of `"` and `#{` inside that string is not verified (`quotedLiteral` is `toServerEnumLiteral`; its Elixir escaping not read). `Elixir.toDefaultValue` writes the wire atom (`BetterElixirCodegen.java:631-636`), which is not the declared member when the name differs from the wire; the sibling default already goes through `siblingDefaultEnumVar`. Not verified: how `ObjectSerializer` turns an atom field back into the wire when the atom name differs from the wire in a non-inline field (`object_serializer.mustache:627-642` was only skimmed). `all_values` collision above. |

## The tests

### Fixture (additive; the existing enums are not touched)

Four new schemas in `src/spec/resources/specs/petstore/openapi.yaml`, no new operation (serializer tests build models from JSON). Whether an unreferenced component is generated in all twelve is not verified; the model `Subscription` is therefore also given an optional property on an existing, already referenced model only as a fallback, and the first run decides.

- `Tier`: string, values `s`, `m`, `l`, `x-enumNames: [Small, Medium, Large]`, `x-enumDescriptions: ["The smallest tier.", "The medium tier.", "The largest tier. Ends with */ and #{x} and $x."]`.
- `Rank`: integer int32, values `1 2 3`, `x-enum-varnames: [LOW, MEDIUM, HIGH]`, `x-enum-descriptions` as an object `{"1": "The lowest rank.", "3": "The highest rank."}` (member 2 has none).
- `Stage`: string, values `paused`, `active`, `live`, `x-enumnames` as an object `{"paused": "on hold", "active": "Active", "live": "active"}`, `x-enumdescriptions: ["Waiting.", null, "Running."]`.
- `Subscription`: object with `tier: $ref Tier` plus `default: m` beside the `$ref`, required `rank: $ref Rank`, optional `stage: $ref Stage`, optional inline `interval` string enum `[month, year]` with `x-enum-varnames: [MONTHLY, YEARLY]` and `x-enumDescriptions: [Billed every month., Billed every year.]`.

Also: add the same three spelling families to one enum in the `probe` spec so `FixtureVocabularyLeakTest` generates a path that reads the keys (the forbidden vocabulary is derived from the petstore spec, so the new words are forbidden automatically).

### Generated suites (catalogue ids, one test per language each, in the serializer test template of that language)

Test templates: `java/test/ObjectSerializerTest.mustache`, `kotlin/test/ObjectSerializerTest.mustache`, `csharp/test/ObjectSerializerTest.mustache`, `php/tests/ObjectSerializerTest.mustache`, `python/test/test_object_serializer.mustache`, `ruby/test/object_serializer_test.mustache`, `node/test/object-serializer.test.mustache`, `dart/test/object_serializer_test.mustache`, `go/test/object_serializer_test.mustache` (and `model_test.mustache` for the constants), `rust/test/object_serializer_test.mustache`, `swift/test/ObjectSerializerTests.mustache`, `elixir/test/object_serializer_test.mustache`.

| Id | What the test asserts |
|---|---|
| enums001 | `Tier` has members named `Small`, `Medium`, `Large` in the language's casing (java `Tier.SMALL`), each with wire `s`, `m`, `l`, and `deserialize` of `{"tier":"m"}` gives the `Medium` member. |
| enums002 | `Rank.LOW`, `MEDIUM`, `HIGH` carry 1, 2, 3; `{"rank":3}` decodes to `HIGH` and encodes back to a bare `3` (the number, not a string). |
| enums003 | `Stage` members come from the object keyed by wire value: wire `paused` is the member named from `on hold`, wire `active` is `Active`, and decoding each wire gives that member. The unlisted-value fallback to the derived name is a unit row, not a generated test. |
| enums004 | The member for `on hold` exists under the cleaned, cased name (java `ON_HOLD`, go `StageOnHold`, dart `onHold`, elixir `:on_hold`) and has wire `paused`. |
| enums005 | `Stage` has both `Active` and the suffixed `Active_2`-style member (java `ACTIVE`, `ACTIVE_2`; go `StageActive`, `StageActive2`) with wires `active` and `live`; `live` decodes to the second. |
| enums006 | `Subscription.interval` inline enum has `MONTHLY` and `YEARLY` (casing per language) with wires `month`, `year`. |
| enums007 | `new Subscription` without a tier starts at `Medium` (wire `m`), and decoding `{"rank":1}` gives `Medium`, written the way the existing `bhvSerializationObject001` test does for `StockItem.availability`. |
| enums008 | Decoding `{"tier":"Medium"}` (the name) fails with `SerializationError` naming `Medium`; encoding a `Tier` writes `"m"`, never the name. |
| enums009 | The model source file has, on the line directly above each described member, a comment equal to the description text on one line (`Tier.Small`: `The smallest tier.`). Read from the generated source tree. Elixir asserts through `Code.fetch_docs` instead, which sees `@doc`. |
| enums010 | `Rank` member 2 (no description) keeps the comment it has today in the languages that write one (java `The {@code 2} value.`, dart, go, rust), and has no comment in the others. |
| enums011 | The comment of `Tier` member `l` (text holding `*/`, `#{x}`, `$x`) is present, contains the text with the comment terminator neutralised in the languages that need it, and the SDK compiled and loaded (the test itself running is the proof). |

Possible exceptions to pre-decide (to avoid hiding a gap later): none planned. If a runner cannot read its own source tree (swift's sandbox is the only candidate; not verified), that language gets one line per id in `test-catalogue-exceptions.txt` naming the platform limit, never a dropped id.

### Unit tests (no catalogue id)

- `AbstractBetterCodegenTest` (shared, one file, `CODEGENS` has all twelve): rows for each of the six spellings; array against object; first spelling wins and two spellings warn; array shorter and longer than the enum; a `null` entry; a nullable enum (`enum: [a, null, b]`) with names aligned by the spec's index; an integer object key `"1"`; a supplied name that cleans to empty; casing of `NUMBER_1`, `on hold`, `1st`, a reserved word per language; collision suffixes; `enumDescription` absent for empty, blank and `null`; description escaping per language for `*/`, `/*`, `#{x}`, `$x`, `\`, `"`, and a newline; `toEnumVarName` unchanged for a schema with no keys; `enumMemberName` agreeing with the final `enumVars` for the same schema.
- `BetterNodeCodegenTest`: `fixEnumDefaultValue` names the supplied member; `enumNameMapping` still wins. `BetterRustCodegenTest`: both default sites. `BetterDartCodegenTest`: `resolveEnumRefDefault`. `BetterGoCodegenTest`: an inline enum parameter constant. `BetterRubyCodegenTest`, `BetterDartCodegenTest`, `BetterSwiftCodegenTest`, `BetterElixirCodegenTest`: the reserved member name (`VALUES`, `value`, `description`, `all_values`) gets a suffix.
- `SpecWarningsTest`: the seven warnings of rule 7.
- Golden gate: after regeneration `git diff` on the twelve goldens may show only new files and the new regions of the serializer tests; any change to an existing model file is a failure of "no keys, same output".
- The existing docs and lint specs (`JavaDocsSpec`, `GoDocsSpec`, `ElixirDocsSpec` and their siblings, `*FormattingSpec`, `*LintingSpec`) build the goldens, so a malformed comment fails there.

## Risks and open questions

Only real ones; each with a recommended answer.

1. Casing of supplied names, and a rename for specs that already use `x-enum-varnames`. Upstream writes that key verbatim today in all twelve, so a spec with `x-enum-varnames: [LOW]` gets `LOW` in Node today and `Low` after this change. Verbatim use would keep that output but breaks languages that cannot take the author's spelling (Ruby needs an uppercase first letter, Dart's `constant_identifier_names` rejects `LOW`, verified in `BetterDartCodegen.java:296`, and `on-hold` is no identifier anywhere), and would make the twelve disagree. Recommendation: clean and case (rule 3), accept that Node and similar languages rename members for specs that already carried the key, state it in `docs/decisions.md`, and ship it as a minor change with a release note. Not verified: whether the six real clients' specs (Zitadel and others) carry any of the keys; check their specs before merging.
2. Replace or keep the generic comment. Recommendation: a described member gets the description only; the wire value is already in the code on the same line.
3. How to prove a doc comment. A comment is not observable at run time in most languages, so enums009 to 011 read the generated source file (and `Code.fetch_docs` in Elixir). Recommendation: accept, and keep the read to one helper per language; the alternative (golden only) leaves the behaviour unpinned.
4. `enumNameMapping` is read by Node only. Recommendation: leave it, keep it first in Node (an integrator's mapping beats an author's key), and open a separate issue to either remove it or honour it in all twelve; widening it here is a different feature.
5. Python member comment form (`#:` Sphinx comment against a bare string after the assignment). Recommendation: `#:` (a bare string statement may trip lint rules; not verified), confirmed by one `make docs` run on the golden.
6. A nullable enum and index alignment. If upstream drops the `null` entry before `enumVars`, an array by index shifts by one. Recommendation: align against the spec's own `enum` array (rule 2) and keep the unit row; not verified how 7.22.0 orders a `null` entry in `values`.
7. The inline-enum resolver. If the inline model resolver lifts an inline enum into its own model, the extensions might not travel with it. Recommendation: the `Subscription.interval` fixture and the golden run decide; fix in the shared generator, not per language. Not verified.
8. Whether an unreferenced fixture model is generated in all twelve (see Fixture). Recommendation: reference it from the new model only and read the first golden run.
9. `docs/comparisons/orval.md` is missing. Recommendation: record the closed gaps in `docs/decisions.md` and write the comparison line when that file exists.

## The order of work and a size per language

Sizes: S is one or two template sites and no Java; M is three to six template sites or one Java site; L is Java changes in the codegen plus template sites.

1. Shared first, one batch, one commit: fixture and probe additions; `AbstractBetterCodegen` changes; `AbstractBetterCodegenTest` and `SpecWarningsTest` rows; regenerate all twelve goldens and check that only new files appear (this proves rule 5 and "no keys, same output"). Size L for the shared part.
2. Names that depend on Java in a codegen, one batch (a change found in one language is applied to all, so these four go together): node, rust, dart, go call sites, with their unit tests.
3. Description templates, grouped by comment syntax so one reading serves a group: block comments (java, kotlin, csharp, php, node, go), line comments (dart, rust, swift, python, ruby), then elixir. Regenerate and read each group's goldens.
4. Test templates and the catalogue lines for all twelve in one batch; `etc/ci-local.sh check` on each touched language, then one push (CI is about thirty minutes, so do not wait between batches).
5. Docs in the same pull request: `docs/decisions.md` (a new section "Enum member names and descriptions come from the spec"), `docs/types.md` (the enum row and a short note), `docs/how-it-works.md` (the models paragraph), the twelve `docs/languages/<lang>.md` pages (member naming, the doc-comment form, the reserved member name), the shared `AGENTS.md` (D95, D96, an idiom-matrix row for the member comment form) and the `AGENTS.md` of node, dart, go, rust, python, ruby, swift and elixir where a site or limit above is recorded. Root `README.md` and generated `README`/`SKILLS` prose stay unchanged (no new section may appear in one language only).

| Language | Size | Why |
|---|---|---|
| java | M | six template sites, no Java beyond shared |
| kotlin | M | six sites, KDoc above the annotation |
| csharp | M | four sites and the integer partial |
| php | M | four sites over two templates |
| python | S | two sites, `#:` comment |
| ruby | M | two sites that need restructuring, `VALUES` reserved |
| node | M | two templates, `fixEnumDefaultValue` |
| dart | L | six sites, `resolveEnumRefDefault`, `value` reserved |
| go | L | inline parameter enum built in Java, block-comment sites |
| rust | L | three template sites, two Java default sites |
| swift | S | two sites, `description` reserved |
| elixir | M | two sites, `@doc` escaping, `all_values` reserved, default atom check |
| shared | L | `AbstractBetterCodegen`, fixtures, catalogue, unit tests, docs |

Count: all twelve languages need template code (descriptions); four of them (node, rust, dart, go) also need Java in their codegen for default and parameter names; none is only tests or docs.
