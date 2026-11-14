# Parity fixtures added to the petstore spec

All additions live in `src/spec/resources/specs/petstore/openapi.yaml` (the fixture spec, also served by chasm to every SDK's generated tests). Decision ids are the `D..` rows of `PARITY_DECISIONS.md`; ledger ids are the `F..` ids of `PARITY_FINDINGS.json`. Nothing was removed or renamed. Every new operation answers from the spec alone (JSON/text example or schema), so chasm can serve it. The goldens have NOT been regenerated.

Rules for implementing agents:

- `FixtureVocabularyLeakTest` now also forbids these new multi-word camelCase property names in templates and in the sample data of generated tests: `pageCount`, `lastError`, `trackingId`, `asOf` (plus every `*Pet*` name, i.e. `PetCriteria`, `getPetSummary`). Use neutral names in templates; use the fixture names only in the petstore-derived goldens.
- `remindAt` was NOT used for the D39 form field (see conflicts): `templates/ruby/test/base_api_test.mustache` already contains the literal `remindAt`, so a petstore property of that name would fail the leak test. The field is called `reminder`.
- Wire expectations below assume the existing canonical forms: date-time `2024-01-15T10:30:00.000+00:00`, then percent-encoded in query/form.

## New operations

| operationId | Path / method | Decisions / ledger | What a per-language test asserts |
|---|---|---|---|
| `getPetSummary` | `GET /pet/{petId}/summary/{asOf}` (tag pet; path extended in the shared pass, see below) | D32 / F149 | Mock answers `200 text/plain` `hello`: the call returns the String `hello` in all 12. A `getPetById` stub answered with `200 text/plain` still raises the SDK `SerializationError` naming `text/plain`; `application/problem+json` with a valid body still decodes to the model. |
| `getOrderByTrackingId` | `GET /store/order/by-tracking/{trackingId}` (tag store, `security: []`) | D67 / F217 | Pass the uuid `E621E1F8-C36C-495A-93FC-0C247A3E6E5F`: the path segment carries the lowercase hyphenated form; a non-uuid argument is rejected before dispatch (no request). Returns `Order`. |
| `createAccount` | `POST /store/account` (tag store, `security: []`) | D56 / F025, F055 | Echo/serve `Account`: response decode keeps readOnly `id` (7); the request body for `Account(username:'u', password:'p')` contains `password`; decode of `{"id":7,"username":"u","password":"p"}` keeps `password`. |
| `searchStore` | `GET /store/search` (tag store, `security: []`); returns `array<Order>` | D80 / F054, D50 / F068 (merged), D81 / F071, D82 / F072 | Four parameters, one test each: (1) `q` (query, `content: application/json`, `PhotoMetadata`): with `{caption:'a<b>&c', takenAt:2024-01-02T03:04:05Z, location:{lat:1.5}}` the decoded `q` equals `{"caption":"a<b>&c","takenAt":"2024-01-02T03:04:05.000+00:00","location":{"lat":1.5}}`, no null keys; with caption `a/b<é` the raw query is `q=%7B%22caption%22%3A%22a%2Fb%3C%C3%A9%22%7D` (F068); `lat=NaN` raises `SerializationError` and sends zero requests (also pins swift `try?` and rust `unwrap_or_default`). (2) `X-Filter` (header, `content: application/json`, map of string): header value is `{"k":"v"}`. (3) `labels` (query, array `uniqueItems`, string items): a Set of two elements gives the pairs `labels=red`, `labels=blue` as an unordered multiset, same as an array (F072). (4) `at` (query, `format: time`): 10:30:00.123456789 gives `at=10%3A30%3A00.123`, whole seconds give `10%3A30%3A00` (F071). |
| `testRedirectNoLocation` | `GET /test/redirect/no-location` (x-internal, tag test) | D71 / F089 | With `followRedirects=true` the call returns status 302 (no exception) and body `{"redirect":"no-location"}`. One row in each client spec's redirect block. |
| `testRedirectMalformedLocation` | `GET /test/redirect/malformed-location` (x-internal) | D71 / F089 | `Location: http://[bad` is unparseable: with `followRedirects=true` the call returns status 302 with body `{"redirect":"malformed"}`, never a parse exception. |
| `testRedirectChain1` / `2` / `3` | `GET /test/redirect/chain/{1,2,3}` (x-internal; each `x-chasm-delay-ms: 500`; 1 -> 2 -> 3 -> `/test/echo`) | D76 / F094, F407 | `timeout(1200)` on `/test/redirect/chain/1`: `NetworkTimeout` (status 0) in all 12 (per-hop timers would pass, each hop is 500 ms). Positive control `timeout(5000)` returns 200. |

x-internal operations are dropped from SDK generation (`DropInternalOperationsRule`); only the mock serves them.

## New schemas

| Schema | Decisions / ledger | What a per-language test asserts |
|---|---|---|
| `PetCriteria` (`breed: string`, `min_age: int32`) | D47 / F063 | Used by `getPetTag` (below). `min_age` is snake_case so wire names, not property names, are asserted. |
| `Account` (`id` readOnly int64, `username` required, `password` writeOnly) | D56 / F025, F055 | ObjectSerializer: decode `{"id":7,"username":"u"}` gives `id == 7` (java drops it today); serialize `username:'u', password:'p'` contains `password`; decode with `password` keeps it. |
| `TreatmentRecord` (`anyOf`: `Surgery`, `Medication`) | D64 / F139 | ComposedSchemaTest: decode `{"drugName":"a","procedureName":"b"}`, the `Surgery` variant is the first retained; merge a `Surgery` and a `Medication` that both set `notes`: the later declared (`Medication`) wins. `PetTreatment` tests are unchanged. |
| `RequiredNullable` (`note` required, `[string, "null"]`) | D66 / F215 | ObjectSerializer: decode `{"note":null}` gives `note` null; decode `{}` fails with the missing-required error; serialize `note=null` gives exactly `{"note":null}`. |
| `Separator` (string enum `" "`, `"@@"`, `"a-b"`, `"a_b"`) | D43 / F172, F363 | Referenced by `EdgeCases.separator`. Each of the four wire values decodes to a distinct constant and re-encodes to the original string; the golden compiles. |
| `Error` | D45 / F173 | Referenced by `EdgeCases.lastError`. The generated type is `ModelError` in all 12 (files per language casing, e.g. `model_error.py`, `ModelError.swift`); round-trips `{"code":1,"message":"m"}`. |
| `Result` | D45 / F173 | Referenced by `EdgeCases.outcome`. Generated type `ModelResult` in all 12; round-trips `{"ok":true}`. |

## Parameters and properties added to existing definitions

| Where | Addition | Decisions / ledger | What a per-language test asserts |
|---|---|---|---|
| `getPetByName` | required header `X-Correlation-Id` (string) | D28 / F392 | Call without it: the FULL message `Missing the required parameter 'xCorrelationId' when calling PetApi.getPetByName` (python `x_correlation_id`, etc.: the language's identifier). All existing `getPetByName` tests must now set the header. |
| `findPetsByStatus` | optional query `2ndPage` (int32) | D43 / F172, F363 | The existing `findPetsByStatus` case asserts `2ndPage=2` reaches the wire; the golden compiles (digit-leading identifier gets a legal fallback). |
| `setPetPreferences` form body | `reminder` (date-time) after `renewalDate` | D39 / F216 | Wire body contains `reminder=2024-01-15T10%3A30%3A00.000%2B00%3A00` and still no `%20`. (The nested-object rejection row in the BaseApi suites needs no spec.) |
| `uploadPetDocument` multipart | `isPrimary` (boolean), `pageCount` (int64), `takenAt` (date-time), `swatch` (`Swatch`) after `notes` | D36 / F214 | Parts `isPrimary=true`, `pageCount=3`, `takenAt=2024-01-15T10:30:00.000+00:00`, `swatch=red`; none of the four has a part Content-Type; only object/model parts (e.g. `addPetPhotos.metadata`) are `application/json`. |
| `getPetTag` query/header | `criteria` (form, exploded), `criteriaCsv` (form, `explode: false`), `criteriaDeep` (deepObject), header `X-Criteria` (simple), all `PetCriteria` | D47 / F063 | With `PetCriteria{breed:'lab', min_age:3}` the query contains, in order, `breed=lab&min_age=3`, `criteriaCsv=breed%2Clab%2Cmin_age%2C3`, `criteriaDeep%5Bbreed%5D=lab&criteriaDeep%5Bmin_age%5D=3`, and the header `X-Criteria: breed,lab,min_age,3`. The existing `filter[color]=blue` output is unchanged. A nested-object leaf is a `SerializationError` (unit rows). |
| `getPetTag` query/header | `fingerprint` (query) and `X-Fingerprint` (header), `string`/`byte` | D51 / F069 | Bytes `hi` give `fingerprint=aGk%3D` and `X-Fingerprint: aGk=`; bytes `FB FF` give `fingerprint=%2B%2F8%3D` (standard alphabet, never `-_`). |
| `/pet/{petId}/multi` servers | third server `Mirror`: `https://{region}.mirror.example.com/{channel}/v1`; `region` (enum us, eu; differs from Regional's us, eu, ap) and `channel` (default `a&b`, enum `a&b`, `it's`) | D42 / F122, F145; D40 / F048, F176 | `getMultiServerPetInfo` case builds the Mirror variant with a `MirrorRegion` value and asserts `getUrl()` equals the expected string including `a&b` and `it's` (no HTML entities). Golden compile: one type per variable per operation. |
| top-level `servers` | third entry `https://{tenant}.example.com/api`, description `Bob's "sandbox" & co: $5 #{x} a*/b`, variable `tenant` (default `o'neil`, description `Tenant's "id"`, enum `o'neil`, `a&b`) | D40 / F045, F180, F362, F176, F048 | ServerConfiguration/ServerVariable tests: the third server's url, description, variable default, variable description and enum values round-trip unchanged. Appended so Server0/Server1 indexes are stable. |
| `Defaults.greeting` | string default `it's "quoted" \ back<TAB>slash<NL>next $USD #{x} a*/b` | D40 / F045, F180 | Decode `{}` into `Defaults`: `greeting` equals that exact string (and default-construct gives the same). |
| `Defaults.swatch` + `Swatch` component default `green` | `$ref` to `Swatch` | D60 / F131, F171 | Decode `Defaults` `{}` gives `swatch == green`; default-constructed gives the same. |
| `StockItem.availability` | `default: on-hold` as a sibling of the `$ref` | D60 / F131, F171 | Decode `StockItem` `{"priority":1}` gives `availability == on-hold`; default-constructed the same. |
| `Defaults.ratio`, `scale`, `weight`, `ceiling` | number 1.5; number/float 0.25; number/double 2; int64 9000000000 | D61 / F174 | Default-constructed values: `ratio` 1.5, `scale` 0.25, `weight` 2.0, `ceiling` 9000000000; the model compiles in every golden. |
| `Availability` enum | added values `o'clock`, `a\b`, `$eq`, `#{x}` | D46 / F044, F047 | ObjectSerializer: each new member serializes to the JSON-quoted spec string and deserializes back. |
| `Order.quantity` | `minimum: 1`, `maximum: 100` | D55 / F056, F137 | Decode `{"quantity":500}` succeeds and round-trips (no SDK enforces the bound). |
| `Pet.nickname` | string, `minLength: 2`, `maxLength: 8`, `pattern: ^(?!admin)[a-z']+$`, `example: rex` | D55 / F056, F137 | Decode a `Pet` with `nickname: "ZZZZZZZZZZ"` succeeds and round-trips; python additionally imports the whole models package (look-around pattern must not break import). |
| `Order.trackingId` | string, `format: uuid` | D67 / F217 | Serialize with `E621E1F8-C36C-495A-93FC-0C247A3E6E5F`: the body carries the lowercase form; decoding the uppercase form works (string typed: comes back lowercase); `not-a-uuid` is rejected on decode. |
| `EdgeCases.'2fa'` | boolean, digit-leading | D43 / F172, F363 | Round-trip with `2fa` set: JSON key is `2fa`, decodes back. |
| `EdgeCases.separator` | `$ref` `Separator` | D43 | see `Separator` |
| `EdgeCases.'null'`, `'true'`, `'false'` | string, boolean, boolean | D44 / F309, F364 | Round-trip keeps the wire keys `null`, `true` (and `false`); java must compile with them (no longer excluded as literals); alongside the existing `and`/`class`/`return` keys. |
| `EdgeCases.lastError`, `outcome` | `$ref` `Error`, `Result` | D45 / F173 | Round-trip with `ModelError`/`ModelResult`; swift must compile. |
| `EdgeCases.cutoff` | string, `format: time` | D81 / F071 | Serialize 10:30:00.123456789 as the JSON string `10:30:00.123` (truncation), `10:30:00` as `10:30:00`; decode round-trip (covers kotlin's missing serializer). |

## Edits to existing text (not purely additive)

- `info.description`, the `pet` tag `description` and its `externalDocs.description` now carry hostile text (D41 / F178, F181, F177): `&`, `<admin>`, quotes, `$USD`, `#{x}`, `/pets/*`, `/pets/**/`, `*/`, a block-scalar newline. Metadata tests should assert the manifest description equals `info.description` verbatim; READMEs and comments change. The `*/` and `#{petId}` text is the point: comment emitters must neutralise it, manifests must escape it.
- The stale NOTE about enum defaults above `Availability` was deleted and replaced by a sentence about the new values (D60).
- `Swatch` gained `default: green`. It is also the type of the optional `querySwatch` and `Preferred-Swatch` parameters of `getBySwatch`; a generator that honours component defaults on parameters may now emit a default there, so re-check the existing `getBySwatch` tests.
- `getPetByName` gained a REQUIRED header (D28): this is the one change that breaks existing call sites; every existing `getPetByName` test must pass the header.

## Shared pass (b7): shared-file fixtures

Added so the language agents can close findings that were blocked on a fixture. `getPetSummary` had no caller in any test template, so the new operation-level fixtures sit on it and no existing call site breaks. Every existing `getPetSummary` test (F149/F155 text/plain return) must now pass `asOf` and `session`.

| Where | Addition | Ledger | What a per-language test asserts |
|---|---|---|---|
| `getPetSummary` | REQUIRED cookie `session` (string) | F218, F219 | Call with `session = ''`: the canonical `Missing the required parameter 'session' when calling PetApi.getPetSummary` (language identifier), no request sent. With `session = 'abc'` the request carries `Cookie: session=abc`. php gains the missing `{{#cookieParams}}` guard. |
| `getPetSummary` path | `/pet/{petId}/summary/{asOf}`, `asOf` required `format: date`, `style: matrix` | F070 (1) | `asOf = 2024-01-15` gives the path `/pet/1/summary/;asOf=2024-01-15` in all 12 (node must not bypass the style for date params). |
| `getPetSummary` query | optional `days`, array of `format: date` (form, exploded) | F070 (2) | `[2024-01-15, 2024-02-01]` gives `days=2024-01-15&days=2024-02-01`, never a date-time (node, dart, php). |
| `getPetSummary` summary / description | summary `Get a <plain> "text" summary & it's short`; description `Returns '''plain''' text for /pets/#{id} and <b> & "c" it's done` | F179, F181, F182 | Golden compiles and lints: python docstring survives `'''`, ruby docs show `#{id}` without a leaked backslash, elixir `@doc` does not interpolate `#{id}`, rust `cargo doc -D warnings` passes on `<plain>`/`<b>`, java doclint passes; no HTML entity (named or numeric) in any golden. |
| `getPetSummary.session` description | `Session token: "a<b>" & it's required` | F179, F182 | Parameter doc slot: rendered raw (no `&#39;`, `&quot;`, `&amp;`) in all 12; rust/java/csharp doc gates pass. |
| `Account` model | `externalDocs.description` `Account <docs> & "notes" it's here` | F179 | Model externalDocs slot: no HTML entity in any golden; doc gates pass. |
| `Account.password` | `format: password` (still `writeOnly`) | F046 | Serialize `Account(username:'u', password:'p')`: the body contains `"password":"p"` in all 12 (python must not send `**********`). |
| `EdgeCases.phase` | inline enum `In-Stock`, `on-hold`, `OUT_OF_STOCK` | F024 | Round-trip each wire value through EdgeCases: decodes to a distinct member and re-encodes to the exact original string (elixir must not use the lowercased member name). |
| `EdgeCases.slug` | string, `pattern: ^\\d+/[a-z]+$` (YAML-escaped; the regex is `^\d+/[a-z]+$`) | F027 | Construct/decode EdgeCases with `slug: "12/ab"`: accepted in all 12 (python must not wrap the regex in `/.../` or double the backslash). |

F155 needed nothing new: `getPetSummary` already returns `text/plain` string (`example: hello`).

## Conflicts resolved

- D50 / F068 (`meta`: content-encoded query of `PetCriteria` on `getPetTag`) overlaps D80 / F054 (`q`: content-encoded query of `PhotoMetadata` on `searchStore`, plus a content-encoded header and a failure path). They test the same code path, so only the more thorough one is kept: `meta` was NOT added to `getPetTag`. The F068 assertions run against `searchStore.q` (reserved characters and non-ASCII in `caption`). `PetCriteria` stays because D47 needs it.
- D40 (b)(c) refer to the D42 server YAML: merged into one `Mirror` variant whose `channel` enum carries `a&b` and `it's`.
- D80 / D81 / D82 each supplied a `searchStore` block: one operation, with one parameter per decision plus the content-encoded header.
- D39: the proposed form field name `remindAt` was renamed `reminder` (leak test, above). If you prefer `remindAt`, the ruby template literal must be neutralised first, and the expected body fragment becomes `remindAt=...`.
- D80's response had no body; `searchStore` returns `array<Order>` so the mock can serve JSON. D32's `getPetSummary` got `example: hello` (the decision's stub text) for the same reason.

## Not integrated / gaps

- D51 / F069 title mentions path and form `format: byte` parameters but its YAML only supplies query and header; no path or form byte parameter was added (not proposed).
- D55 / F056, F137 title lists `multipleOf` and `minItems`/`maxItems`, but the YAML only supplies `minimum`/`maximum`, `minLength`/`maxLength` and `pattern`; those are what exist.
- Decisions D39 (nested-object rejection), D46 (`toEnumValue` unit table) and the D40/D41/D42 generator unit tables need no spec change.
