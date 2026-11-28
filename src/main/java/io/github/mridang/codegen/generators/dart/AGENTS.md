# Dart SDK generator

This generator (`dart-plus`, class `BetterDartCodegen`) emits a pure Dart package from an OpenAPI document: a `Client` entry class, `Configuration` and `TransportOptions` builders, one API class per tag with an Options class per operation, plain model classes with `fromJson`/`toJson`, an authenticator per security scheme, the `...Exception` hierarchy, and the `package:test` suite, README, SKILLS.md and analyzer config that ship with it. HTTP goes through `package:http` (`http.Client`), split behind a conditional import into `default_api_client_io.dart` (`dart:io`, proxy, TLS, manual gzip/deflate) and `default_api_client_web.dart` (browser fetch, so it compiles to WebAssembly); JSON uses `dart:convert`, with `collection`, `decimal`, `http_parser` and `mime` as the only other runtime dependencies. Tests run with `package:test` (`dart test`, coverage via `coverage`, JUnit via `junitreport`, containers via `testcontainers_core`). The floor is Dart 3.11 (`sdk: '>=3.11.0 <4.0.0'` in `pubspec.mustache`); specs and the formatter run in the digest-pinned `dart:stable` image.

## Read first

- Shared cross-language rules: [../AGENTS.md](../AGENTS.md)
- Repository root: [../../../../../../../../../AGENTS.md](../../../../../../../../../AGENTS.md)
- Codegen class: [BetterDartCodegen.java](BetterDartCodegen.java)
- Templates: [../../../../../../../resources/templates/dart/](../../../../../../../resources/templates/dart/)
- Spec classes: [../../../../../../../spec/java/io/github/mridang/codegen/spec/dart/](../../../../../../../../spec/java/io/github/mridang/codegen/spec/dart/) (`DartSpec` is the shared interface; `DartClientSpec` runs `dart test`, `DartLintingSpec` runs `dart analyze --fatal-warnings --fatal-infos`, `DartFormattingSpec` checks `dart format`, `DartWasmSpec` runs `dart compile wasm`, `DartReservedWordsSpec` checks the keyword list, `DartExamplesSpec` builds and runs `examples/` and analyses the README and SKILLS fences)
- Golden: [../../../../../../../spec/resources/generated/dart/](../../../../../../../../spec/resources/generated/dart/) (never hand-edit; regenerate)

## Layout

| Path (under `src/main/resources/templates/dart/` unless noted) | What lives there | Edit this to change |
|---|---|---|
| `errors/*.mustache` | The exception hierarchy, one class per file under `lib/src/errors/` | Error classes, messages, the root exception |
| `auth/*.mustache`, `auth/oauth/*.mustache` | Authenticator base, api-key, bearer, basic, per-scheme wrappers (rendered by `renderSchemeAuthenticator` in the codegen), OAuth2 flows, token manager, OIDC | Auth behaviour |
| `abstract_api_client.mustache`, `default_api_client*.mustache`, `transport_options.mustache` | Request dispatch, redirects, timeouts, decoding, multipart; the `dart:io` and web transports; transport builder | Transport and wire behaviour |
| `event_stream.mustache`, `json_lines_stream.mustache`, `sse_stream.mustache`, `server_sent_event.mustache`, `stream_options.mustache`, `retry_after.mustache` | The line reader and SSE parser, `JsonLinesStream<T>`, `SseStream<T>`, `ServerSentEvent<T>`, `StreamOptions`, and the shared `Retry-After` parser | JSON Lines and server-sent events |
| `byte_stream.mustache`, `api_stream_response.mustache`, `errors/stream_exception.mustache` | `ByteStream` (the binary response carrier), `ApiStreamResponse` (what `sendStreaming` returns) and `StreamException` | The streaming carriers and the failure after the headers |
| `base_api.mustache`, `object_serializer.mustache`, `value_serializer.mustache`, `header_selector.mustache`, `deserialize_arg.mustache` | Parameter styles, query/form/multipart encoding, JSON (de)serialization, content negotiation | Anything about bytes on the wire |
| `api/api.mustache`, `api/options*.mustache` | Per-tag API classes and per-operation Options classes | Operation signatures and doc comments (D169/D170: `api/operation_doc_examples.mustache` adds the request and response examples and the `exposedExtensions` lines after the parameter examples of the method and the stream method, `api/response_headers.mustache` adds each header's example, and `api/options.mustache` renders a parameter's `pluralExamples`, which `generateOptionsFileContent` does not pass yet) |
| `models/model.mustache` | Models, enums, oneOf/anyOf wrappers | Models and unions |
| `configuration.mustache`, `servers.mustache`, `server_configuration.mustache`, `client.mustache`, `barrel.mustache` | Builder, server constants, entry class, `lib/<package>.dart` barrel | Public entry surface |
| `readme.mustache`, `skills.mustache` | README (Caveats, Not supported, Schema constraints) and SKILLS.md | Docs |
| `pubspec.mustache`, `analysis_options.mustache`, `makefile.mustache`, `editorconfig.mustache`, `dev_dependencies.mustache` | Manifest, analyzer config, make targets | Dependencies, lint rules, SDK floor |
| `call_hook.mustache`, `call_info.mustache`, `call_outcome.mustache`, `call_hook_recipe.mustache` | The call hook types, and the OpenTelemetry recipe partial shared by the README and `test/call_hook_recipe_test.mustache` | The hook API, the recipe |
| `test/*.mustache`, `test/api/` | The generated `package:test` suite (extend these, never add a new file) | Tests |
| `src/main/java/.../generators/dart/BetterDartCodegen.java` | Type mapping, naming, enum/default literals, supporting-file list, formatter commands, `dartString` lambda | Type mapping, reserved words, escaping, file layout |
| `src/main/resources/reserved-words/dart.txt` | Keyword list | Reserved words |
| `src/test/java/.../generators/dart/BetterDartCodegenTest.java` | Unit test of the codegen class | Codegen logic |

## Idioms (deliberate differences from the other eleven)

| Topic | What dart does | Why |
|---|---|---|
| Models | Plain classes with a `fromJson` factory, `toJson`, `==`/`hashCode` through `DeepCollectionEquality`, and a field-dumping `toString` | Dart has no reflection-based deserialiser; `fromJson` is the `dart:convert` convention |
| Operation signature | Every operation takes an explicit positional `options` argument with no default (`findPetsByStatus(null)` when nothing is set) | One uniform shape for every operation; see the settled decision below |
| Async | Sync and async accessors both exist: `authHeaders()` and `authHeadersAsync()` | Value-wrapped async (`Future`) keeps a distinct `...Async` next to the sync one, as csharp and node do |
| Errors | `...Exception` suffix, `cause` is a plain field (`NetworkException(cause:)`) | Dart has no built-in exception chaining; the root `OpenapiException` base is sealed |
| Visibility | `BaseApi.config` and `.apiClient` are public, and helper methods on `BaseApi`, `ObjectSerializer`, `ValueSerializer` and `AbstractApiClient` are public | Dart has no protected visibility across libraries; the generated API classes live in other files |
| Transport split | `default_api_client.dart` selects `_io` or `_web` through a conditional import | `dart:io` does not exist in the browser or under dart2wasm; `DartWasmSpec` pins this |
| Content-Encoding | Advertises only `gzip, deflate`; the web transport does not decompress | Dart has no stdlib brotli or zstd, and in the browser fetch owns Content-Encoding |
| Servers | Module-level constants (`SERVER_0`...) and `allServers` | Dart has no namespace class for constants |
| uniqueItems | `Set<T>` in models | Native set type; order is unspecified (S6) |
| Binary, UUID, duration | `BodySource` for a raw binary request body, `Uint8List` for a multipart file part and a `format: byte` value, `ByteStream` for a binary success response, an SDK-owned `UuidValue`, an SDK-owned `Iso8601Duration` | `package:uuid` and an ISO-8601 duration codec are not in the stdlib, and `UuidValue` and `Iso8601Duration` are allowed as extra types |
| `format: number` | Native `double` | Settled and documented; see the decision below |
| `format: decimal` | `Decimal` from `package:decimal`, number- and string-shaped | Exact, read from the number's source text (#157); see the decision below |
| Optional server description | Non-nullable empty string, not `null` | Same sentinel as go, rust and swift; metadata only, never on the wire |
| Tracing | `TraceContextUtil.propagator` is a registration hook | Dart cannot detect an optional OpenTelemetry dependency at runtime |
| Call hook and telemetry | `CallHook`, `CallObserver`, `CallInfo`, `CallOutcome` and `chainCallHooks` (`call_hook`, `call_info`, `call_outcome`), run by a private `_CallRun` in `base_api.mustache`; no OpenTelemetry adapter, only a README recipe (`call_hook_recipe.mustache`) that `test/call_hook_recipe_test.mustache` includes, so the text a user copies is the text under test | The `opentelemetry` package has no histogram, its SDK counter is a no-op and pub has no optional dependency, so the recipe passes the count and the duration to functions the user supplies; there is no cancellation, so no `cancelled` error type (catalogue exception `hooks015`) |
| Composed schemas | oneOf/anyOf plumbing members (`oneOf0`, `matchesAny`, `openapiOneOf`...) exist on wrappers; the caller-facing accessor is `actualInstance` | The serializer hooks need them |
| Constructors | `Client`, `DefaultApiClient`, `AbstractApiClient` take an extra trailing `http.Client`; auth constructors default optional scopes with default arguments | Injected transport (S9); Dart has default arguments instead of overloads |
| Builder setters | Positional parameter named `val` | The call site is identical |
| `NO_AUTH` | A concrete `NoAuthAuthenticator` instance | A const sentinel needs a class in Dart |

## Settled decisions (do not re-open in review)

Ids D01-D94 are the numbered decisions of the shared file; an Fnnn names an owner decision there or a decision written out next to it; S1-S16 are the 2026-11-16 session decisions; "Forced" and "Resolved" entries come from the shared file. The shared rule is written once in `../AGENTS.md`; each entry here is how dart realises it. Where a Rule line says "intended", the decision is recorded and owner-approved but I could not confirm it in the templates or the golden when this file was written: check before treating the gap as a defect, and fix the template rather than the rule.

### Cancellation (S7, 2026-11-16)
Rule: intended: dart adds no cancellation parameter; the package:http `Client` has no per-request cancel handle, so a deadline is the bound and a timeout raises `NetworkTimeoutException`. Today the README ("Per-call cancellation") says exactly that.
Why: dart's native mechanism (closing the client, or the `http` package's abortable request) is not a per-call handle callers pass, so a wrapper would be less natural.
Where: `readme.mustache`, `abstract_api_client.mustache` (`_readWithin` cancels the unread stream on deadline).
Test: `default_api_client_unit_test.mustache`.

### Parameter order (S8, 2026-11-16, owner confirmation pending)
Rule: intended: required parameters, then server, then the Options object, always, whether or not the operation has security. Dart keeps the explicit positional `options` (next decision).
Why: adding security must never move an argument.
Where: `api/api.mustache`, `api/options.mustache`.
Test: `test/api/` and the golden under `dart analyze`.

### Operations take an explicit `options` argument with no default (Forced divergences, D26)
Rule: `findPetsByStatus(FindPetsByStatusOptions? options)`, called `findPetsByStatus(null)` when empty, for every operation. Do not special-case some operations to optional-positional.
Why: one uniform shape; zero wire impact; the other eleven default it. An optional request body is therefore already omittable and D26 needs no extra overload.
Where: `api/api.mustache`, `api/options.mustache`.
Test: `test/api/*`, `base_api_test.mustache`.

### Injected transport is an `http.Client` (S9, 2026-11-16)
Rule: `Client`, `DefaultApiClient` and `AbstractApiClient` take an optional `http.Client`; the SDK defaults stay applied, a supplied client is never closed by the SDK, and a conflict with the SDK's own proxy or TLS settings fails fast.
Why: `package:http` is the ecosystem's standard client; the other SDKs accept theirs the same way.
Where: `client.mustache`, `default_api_client*.mustache`, `abstract_api_client.mustache`.
Test: `default_api_client_test.mustache`, `default_api_client_unit_test.mustache`, `client_test.mustache`.

### Batch 5 landings
Rule: `Client.close()` closes the `DefaultApiClient` it built and never a transport supplied through `TransportOptions.httpClient`; a request after close raises `StateError`. HTTP 429 raises `TooManyRequestsException`, a `ClientException` selected in `ApiException.fromResponse`. The unexpected-Content-Type `SerializationException` names `Class.operationId` (passed as `operation:` to `invokeApiForResult`). A `content:` cookie is JSON through the SDK serializer, then `encodeContentCookie` keeps only A-Z a-z 0-9 - . _ ~ literal. Redirects follow RFC 9110 15.4: 301/302 turn only POST into GET, 303 turns everything but HEAD into GET, 307/308 never change method.
Where: `client.mustache`, `errors/too_many_requests_exception.mustache`, `errors/api_exception.mustache`, `base_api.mustache`, `object_serializer.mustache`, `api/api.mustache`, `abstract_api_client.mustache`.
Test: `client_test`, `api_error_test`, `base_api_test`, `object_serializer_test`, `default_api_client_unit_test` mustache templates.

### Content parameters use the SDK serializer (S2, D80, D50)
Rule: `content: application/json` parameters in query, header, path and cookie go through `ObjectSerializer.serialize`, not `jsonEncode` directly. The query branch is landed; the header, path and cookie branches are intended.
Why: one byte-identical encoder in all twelve (wire names, date-times, null fields omitted).
Where: `base_api.mustache`, `api/api.mustache`, `object_serializer.mustache`.
Test: `object_serializer_test.mustache`, `base_api_test.mustache`.

### Strict containers and integer decoding (S1, D54)
Rule: intended: an array or map of primitives is checked element by element, an integer leaf must be an integer inside the int32 or int64 range, and a fractional, quoted or out-of-range value raises `SerializationException` inside deserialize. Landed in wave 2: `readInt32`, `readInt64` and `readDouble` in `object_serializer.mustache` do the check (see wave 2).
Why: a bad leaf must fail where the document is decoded, not later through a stray `TypeError`.
Where: `models/model.mustache`, `object_serializer.mustache` (planned `readInt32`/`readInt64`), `api/api.mustache`.
Test: `object_serializer_test.mustache` and `test/api/*` rows.

### Schema constraints are not enforced (S3, D55)
Rule: the client does not enforce `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems` or `maxItems`; the server owns validation. The README says so under "Schema constraints".
Why: the model layer has no cheap native validation in dart; enforcing in one language only would diverge.
Where: `readme.mustache`.
Test: `metadata_test.mustache` (README and model surface).
Opt-in validation (D189): `Configuration.validateConstraints` (builder `validateConstraints(bool val)`, default false); the validator is `lib/src/constraint_validator.dart` (`constraint_validator.mustache`, not exported from the barrel), called first in `BaseApi._send`, which every invoke path goes through; it matches `runtimeType` exactly and throws `ArgumentError`. `BetterDartCodegen.tagConstraintNullability` marks non-nullable fields (`dartNonNull`) and the `dartConstraintHas*` flags emit a private helper only when it is called (`unused_element`). Test: `test/api/store_api_test.mustache` (`bhv models 706` to `709`).

### allOf with a discriminator uses inheritance (S4)
Rule: the allOf parent is an `abstract class` and each subtype `extends` it. The base's `fromJson` dispatches on the discriminator to the subtype its mapping names (explicit mapping or implicit schema name), throws `SerializationException` naming a value no mapping lists, and throws when the discriminator is missing or null. A subtype's `toJson` overrides the base's and writes its discriminator value (defaulted through `Gap K`). The base has no `copyWith`. Only children that compose the base are dispatched to, so a mapping to an unrelated schema never becomes a wrong return type. A child's inherited property is bound through `super.`; an inherited enum keeps the base's enum type (`dartOwner`), and only the base declares it. The shared pass's `x-allof-*` markers are copied to `dartAllOfBase`, `dartAllOfChild`, `dartAllOfParent`, `dartInherited`, `dartSubtypes` and `dartOwner` in `BetterDartCodegen.postProcessAllModels`, so the template reads no `x-` key. Imports go both ways (the base imports its subtypes, a subtype imports its base), which Dart allows.
Why: dart classes have single inheritance, so a model reused in two unions still is not re-parented, and unions keep their wrappers.
Test: `composed_schema_test.mustache` (bhv unions 301-304, bhv serializationobject 301).
Why: dart classes have single inheritance and one parent only, as in java, csharp, node, python, ruby and php.
Where: `models/model.mustache`.
Test: `composed_schema_test.mustache`.

### Required plus nullable (S5, D66)
Rule: the key must be present on decode (missing is an error), an explicit null is a legal value and is kept, and encode always writes the key (null when unset). The field is `T? note` with `required this.note`; `fromJson` checks `containsKey`.
Why: the three states (absent, null, value) must stay distinguishable.
Where: `models/model.mustache`.
Test: `object_serializer_test.mustache` (the `required_nullable` fixture).

Query side (D98): the Options field is `required T? name`; the emission block for the `requiredNullable` kind comes first in the query cascade and writes `''` for null.

### uniqueItems order is unspecified (S6, D69)
Rule: sets are sent in the `Set`'s natural order; the README documents it. D69 asked for sorted scalars; S6 supersedes D69, so `wireList` in `object_serializer.mustache` no longer sorts (landed) and models must not sort. Test: `object_serializer_test.mustache` locks insertion order.
Why: the owner chose "unspecified, do not sort" over byte-identical order.
Where: `models/model.mustache`, `object_serializer.mustache`, `readme.mustache`.
Test: `object_serializer_test.mustache`.

### Dependencies (S10)
Rule: a small pure-Dart package or a version bump is fine when it standardises behaviour cleanly; never a dependency that needs a native binary. Runtime dependencies today are `collection`, `decimal`, `http`, `http_parser`, `mime`.
Why: native binaries break `dart compile wasm` and container images.
Where: `pubspec.mustache`, `dev_dependencies.mustache`.
Test: `DartWasmSpec`, `DartClientSpec`.

### Scope order, UUID case, blank keys, error cause (S14, D19, D22)
Rule: OAuth scopes keep the spec's declared order; a uuid is lowercase hyphenated and validated; an api key or bearer token is blank only when empty or ASCII whitespace (explicit set, `RegExp`, not `trim()`), with the `Bearer ` prefix stripped first; every status-specific exception accepts an optional `cause`; the default User-Agent token is the pub package name.
Why: `trim()` strips more than the ASCII set the other eleven use.
Where: `auth/api_key_authenticator.mustache`, `auth/bearer_authenticator.mustache`, `auth/oauth/*`, `object_serializer.mustache`, `errors/*`, `codegen formatDartScopes`.
Test: `api_key_authenticator_test.mustache`, `bearer_authenticator_test.mustache`, `api_error_test.mustache`.

### Secrets are redacted (S15)
Rule: `Configuration` and `TransportOptions` string forms redact the proxy password and any default header named Authorization, Proxy-Authorization, Cookie, or containing key, token or secret, as `***`.
Why: a logged configuration must not leak credentials.
Where: `configuration.mustache`, `transport_options.mustache`.
Test: `configuration_test.mustache`, `transport_options_test.mustache`.

### Timeouts, redirects, proxy and CA validation (S16, D76, D77, D73, D79, D72)
Rule: one end-to-end timeout budget across redirect hops and the body read; a timeout must be positive or null (`ArgumentError` in the builder); proxy URL validation happens in `build()`, not the setter; `caCertPath` is validated whenever it is set, even with `verifySsl` false, and pins trust. Configuration errors append the redacted URL (intended for all three messages). SOCKS schemes are rejected at construction.
Why: a hop-by-hop timeout would let a redirect chain exceed the caller's budget.
Where: `transport_options.mustache`, `abstract_api_client.mustache`, `default_api_client_io.mustache`.
Test: `transport_options_test.mustache`, `default_api_client_unit_test.mustache`.

### Ambient proxy settings are ignored (D74)
Rule: with no proxy configured the `dart:io` client sets `findProxy = (_) => 'DIRECT'`; environment variables are never read.
Why: one policy in all twelve.
Where: `default_api_client_io.mustache`.
Test: `default_api_client_unit_test.mustache`.

### Proxy-Authorization survives redirects (D75)
Rule: the SDK's own `Proxy-Authorization` is put back after the redirect-header strip; only caller-supplied credentials are removed across origins.
Why: stripping the SDK's own header would break an authenticated proxy mid-chain.
Where: `abstract_api_client.mustache` (`_proxyAuthHeader`).
Test: `default_api_client_unit_test.mustache`.

### Query encoding is RFC 3986 with `%20` (D52, D49, D78)
Rule: query and form-body components keep exactly `A-Za-z0-9-._~` literal and percent-encode `* ! ' ( )`; spaces are `%20` in queries. `allowReserved` leaves only the RFC 3986 query-legal reserved set literal, with `#`, `[` and `]` percent-encoded. `encodeRfc3986QueryComponent` in `value_serializer.mustache` is the one encoder; the OAuth copies are to be deleted (intended). A form-urlencoded field whose Encoding Object sets `style` or `explode` is styled by `serializeStyled` like a query parameter and then form-encoded (D165); a multipart field whose Encoding Object declares a part Content-Type or fixed part headers is wrapped in `EncodedPart` (`encodedPart`), which `_buildMultipartBody` writes in place of the chosen Content-Type, followed by the headers in order (D166).
Why: dart was the one SDK still diverging (D52). Forced exception (D78): `dart:core Uri` rewrites a literal `[`, `]` and `'` on dispatch, so the encoder output is the contract, not the final wire bytes.
Where: `value_serializer.mustache`, `base_api.mustache`, `auth/oauth/*`.
Test: `value_serializer_test.mustache`, `base_api_test.mustache`.

### Path parameters (D48, Resolved: required-parameter message)
Rule: a path value of exactly `.` or `..` is rejected with `ArgumentError`; an empty required path parameter is rejected at the operation layer with `Missing the required parameter '<x>' when calling <Class>.<operation>`, so the serializer's own throw is a dead safety net.
Why: a dot segment changes the URL path; only go is forced to a different message shape.
Where: `value_serializer.mustache`, `api/api.mustache`.
Test: `value_serializer_test.mustache`, `test/api/*`.

### Parameter value rules (D47, D51, D81, D82, D62, D63)
Rule: `format: byte` parameters are base64-encoded (`Uint8List` in `stringify` and before the `List` branch); `format: time` stays a `String` (no change); `Set` parameters expand like arrays; `format: uri` is checked as an absolute RFC 3986 URI in `fromJson`; an additionalProperties key colliding with a declared property is skipped so the declared value wins. D51, D62 and D63 are landed; D47 (object-valued query/header parameters converted through `jsonDecode(serialize(v))` instead of the `as Map<String, Object?>?` cast in `api/api.mustache`) and the `Set` normalisation in D82 are intended.
Rule (D146): `format: ipv4` and `ipv6` stay `String` and are checked in `fromJson` through `parseIpv4Address`/`parseIpv6Address` (list elements included); a malformed address is a `SerializationException`.
Why: one rule per wire form across all twelve.
Where: `value_serializer.mustache`, `object_serializer.mustache`, `models/model.mustache`, `api/api.mustache`.
Test: `value_serializer_test.mustache`, `object_serializer_test.mustache`.

### Decoding strictness (D57, D58, D65, D59, D60)
Rule: date-time decode goes through `ObjectSerializer.parseDateTime` (offset required, never the host zone); `format: byte` decodes through `decodeBase64` (strict RFC 4648, padding required); a null element in a model array or map is rejected, with the incidental `TypeError` wrapped (wrap is intended); a required property with a default is filled with that default when absent (explicit null still rejected); a default on a `$ref` enum becomes the typed member (`defaultEnumVarName`), and dart is the reference rendering for that.
Why: lax decoders accept documents the other SDKs reject.
Where: `object_serializer.mustache`, `models/model.mustache`.
Test: `object_serializer_test.mustache`, `composed_schema_test.mustache`.

### Content negotiation and bodies (D30, D31, D32, D35, D36, D39, D90, D91)
Rule: a binary (`format: binary`) response is always raw bytes via `binaryReturn`; `format: byte` is JSON by default, strictly decoded, every failure a `SerializationException`; a non-JSON success type on a non-binary return gives the text for `String` and a `SerializationException` otherwise (intended where the template still yields null); a binary request body is sent verbatim whatever Content-Type was selected (`rawBody`); multipart scalars are plain text parts and only objects are JSON parts; a nested object in an `x-www-form-urlencoded` value is rejected (intended); the multipart filename fallback iterates runes; declared-charset decoding covers latin1, ascii, utf-16 and windows-1252 and falls back leniently to UTF-8. An absent or empty response Content-Type defaults to JSON.
Why: one rule across JSON, absent, text and binary types.
Where: `abstract_api_client.mustache`, `base_api.mustache`, `api/api.mustache`, `object_serializer.mustache`.
Test: `base_api_test.mustache`, `default_api_client_unit_test.mustache`, `object_serializer_test.mustache`.

### Forced: repeated multipart scalar parts (D38)
Rule: repeated scalar text parts (array fields) are `MultipartFile.fromString` parts, each carrying `content-type: text/plain; charset=utf-8`, while a single scalar part carries none. The JSON part header is fixed.
Why: `package:http`'s `fields` map cannot repeat a name, and every `MultipartFile` part always writes a content-type; a bare part needs a custom `MultipartRequest.finalize` (verified in `package:http` 1.6.0, over 60 lines). Deferred, low priority.
Where: `abstract_api_client.mustache` (`_addMultipartField`).
Test: `default_api_client_unit_test.mustache`.

### Compressed responses (Resolved cross-language fixes, D92, D13)
Rule: an unsupported `Content-Encoding` raises in the `dart:io` decompressor and is wrapped by the caller into the SDK exception carrying the response status; an empty body passes through for any encoding (`if (bytes.isEmpty)`); response headers handed to the caller drop `Content-Encoding` and `Content-Length` once the body was decoded; a response that arrived but was unusable (decompress failure, redirect refusal) carries the real, lower-cased response headers. The web transport is forced to pass through: the browser owns Content-Encoding there and the client cannot tell an already-decoded body from an encoded one.
Why: one contract across the manual decompressors; never return still-encoded bytes.
Where: `default_api_client_io.mustache`, `default_api_client_web.mustache`, `abstract_api_client.mustache`.
Test: `default_api_client_unit_test.mustache`.

### Redirects (D71, D16, Resolved: current-hop guard)
Rule: an unparseable Location behaves like a missing one and returns the 3xx without draining the body; the HTTPS to HTTP body-replay guard anchors on the current hop (`currentUri`), while the cross-origin sensitive-header strip still uses the original URL. The unified refusal wordings (`non-HTTP(S)`, the canonical downgrade text, `Too many redirects (exceeded $maxRedirects)`) are landed: `Refusing to follow redirect to non-HTTP(S) URL: <next>`, `Refusing to replay request body across HTTPS->HTTP redirect: <current> -> <next>`, `Too many redirects (exceeded N)`. All three carry the real status and the lower-cased response headers. A spent budget whose last 3xx has no usable Location returns that 3xx instead of raising.
Why: an original-anchored guard misses an http to https to http chain.
Where: `abstract_api_client.mustache`.
Test: `default_api_client_unit_test.mustache`.

### Error and serializer wording (D15, D17, D18, D07, D09, D29)
Rule: landed (batch 6): one serializer message set (`Failed to deserialize JSON: $e` for list and map variants), a compact sorted-key JSON `Response headers:` line (`jsonEncode(SplayTreeMap.of(headers))`), the exact auth-code precondition text `Must call exchangeCode() before making API requests`, no parser detail on token and discovery errors, and use-after-close as a `StateError` with the canonical text. All are now in the templates: the `Response headers:` line is `jsonEncode` of the key-sorted map, and use after close is a `StateError('ApiClient has been closed and can no longer send requests')`. A leading BOM is stripped inside the shared JSON parse (`_stripBom`, landed).
Why: the error contract is identical in all twelve.
Where: `object_serializer.mustache`, `errors/api_exception.mustache`, `auth/oauth/*`, `abstract_api_client.mustache`.
Test: `api_error_test.mustache`, `object_serializer_test.mustache`, `oauth2_*_test.mustache`.

### OAuth and OIDC behaviour (D01, D02, D03, D04, D05, D06, D08, D10, D11, D12)
Rule: the OIDC authenticator reuses the previous delegate's token manager on rediscovery so the exchanged session survives, with one in-flight `Completer` so concurrent callers share a discovery (landed); OIDC always requests `openid` (landed); the refresh grant carries client authentication and exchangeCode never uses the cache (D02, D03, intended); per-scheme wrappers pass the declared scopes in spec order (D04, generator side); an empty implicit-flow token is unset; `expires_in` and `max-age` use one decimal grammar (`RegExp(r'^[0-9]+(\.[0-9]+)?$')`) with a one-year ceiling before any `Duration` arithmetic; the synchronous `authHeaders()` on token flows throws, `authHeadersAsync()` is the real accessor.
Why: a cache refresh must never discard the session; every grammar is the twelve-language one.
Where: `auth/oauth/openid_connect_authenticator.mustache`, `auth/oauth/oauth2_auth_code_authenticator.mustache`, `auth/oauth/oauth2_token_manager.mustache`, `auth/base_authenticator.mustache`, `codegen renderSchemeAuthenticator`.
Test: `openid_connect_authenticator_test.mustache`, `oauth2_token_manager_test.mustache`, `oauth2_*_authenticator_test.mustache`.

### Headers and request identity (D21, D27)
Rule: User-Agent and X-Request-ID injection checks are case-insensitive and the caller's header wins; `ApiResult.headers` is `Map.unmodifiable`.
Why: HTTP header names are case-insensitive; a result must not be mutated through a shared map.
Where: `abstract_api_client.mustache`, `api_result.mustache`.
Test: `api_result_test.mustache`, `default_api_client_unit_test.mustache`.

### Models: toString, equality, order (D23, D24, D53, D85)
Rule: every model, enum and union wrapper has a field-dumping `toString` and value equality through `DeepCollectionEquality`; query, form and multipart parts follow spec declaration order; request-body keys follow declared property order. The shared D23 text in the root `AGENTS.md` still says dart has no `toString`; that sentence is stale, the golden has one.
Why: debugging and equality parity with the other SDKs.
Where: `models/model.mustache`.
Test: `composed_schema_test.mustache`, `object_serializer_test.mustache`.

### Literals, doc prose and escaping (D40, D41, D43, D44, D45, D46, D56, D93)
Rule: spec strings written into Dart literals are single-quoted with `\\`, `\'`, `\n`, `\r`, `\t` and `$` escaped (`toDefaultValue`, `formatDartScopes`, the `dartString` lambda); enum literals escape the quote instead of stripping it; doc prose uses triple braces plus `unescapeDocComment`; `pubspec` descriptions use `jsonString`; a digit-leading identifier gets a deterministic legal fallback and `escapeReservedWord` stays for keywords only; model names colliding with SDK or language types get the `Model` prefix from the dart reserved-model-name set; the readOnly/writeOnly remark is doc-only; the `findPetsBornOn` comment says a `format: date` parameter is a `DateTime` rendered by `stringifyDate`. The `\$` escaping is landed; the rest is intended where not visible in the golden.
Why: a literal must keep the spec value and compile.
Where: `BetterDartCodegen.java` (`toDefaultValue`, `toEnumVarName`, `escapeReservedWord`, `dartString`), `api/api.mustache`, `servers.mustache`, `pubspec.mustache`, `reserved-words/dart.txt`.
Test: `BetterDartCodegenTest`, `DartReservedWordsSpec`, `DartFormattingSpec`.

### Per-operation servers (D42)
Rule: one type per variable per operation, emitted once; unique variant names.
Why: two operations sharing a variable name must not collide.
Where: `api/api.mustache` (`enumTypeName`).
Test: `test/api/*`.

### Deprecation marker on every variant (Resolved cross-language fixes)
Rule: a deprecated operation carries `@Deprecated(...)` on every public variant, including the result-returning one.
Why: a caller of the `...WithHttpInfo` variant must still get the warning.
Where: `api/api.mustache`.
Test: `test/api/*`.

### `format: number` is native `double` (D83, Forced divergences)
Rule: a bare `number` with no format decodes to `double`; the README ("Decimal / `format: number` precision") says it is lossy. Only `format: decimal` is exact (next entry).
Why: the owner decision of #157 covers `format: decimal`; a bare number stays the language's float.
Where: `readme.mustache`, `BetterDartCodegen.java` type mapping.
Test: `object_serializer_test.mustache`.

### `format: decimal` is `Decimal` from `package:decimal` (#157)
Rule: a `format: decimal` value is `Decimal` (re-exported from `object_serializer.dart` and the package barrel), whether the schema is `type: number` or `type: string`. The number-shaped form never passes through a double: `parseJson` walks the text `jsonDecode` accepted and records, per holding map or list, the source text of every number whose decoded `num` does not reproduce it (`_numberSources`, an `Expando`); `readDecimal`/`readDecimalOrNull` read a model field from it and `readDecimalValue` reads a container element through the source `deserializeArray`/`deserializeMap` set for it, and `readDecimalBody` reads a response body that is one decimal from the body's own text; a list of primitives returned by an operation goes through `deserializeArrayFromJson` with the element deserializer (#187). `serialize` writes a `Decimal` as a bare JSON number in plain notation: `jsonEncode` only writes doubles, so the `toEncodable` hook writes the value as a string behind a random per-call marker and the quotes are then removed. The string-shaped form is read only from a JSON string matching `^-?[0-9]+(\.[0-9]+)?$` (`parseDecimalString`; a bare number fails) and written back as a JSON string. Query, path and header values use `decimalText` (plain notation). A `Decimal` keeps no scale, so the decimal places each decoded value was written with live in an `Expando` (`_writtenScale`) and `decimalText` writes them back: `"12.50"` stays `"12.50"` (misc088). A `format: decimal` default is dropped, because `Decimal` has no const constructor. A schema named `Decimal` is prefixed `ModelDecimal` (D45).
Why: owner decision on #157: exact decimals in all twelve SDKs, decoded without going through a float.
Where: `object_serializer.mustache` (readers, `_NumberSourceWalker`, `serialize`), `models/model.mustache`, `deserialize_arg.mustache`, `BetterDartCodegen.tagDecimal` and `getSchemaType`, `pubspec.mustache`.
Test: `object_serializer_test.mustache` ids serializationobject 410 to 413.

### Forced: optional server description is an empty string (Forced divergences)
Rule: `ServerConfiguration` and `ServerVariable` keep a non-nullable `description` defaulting to `''`.
Why: metadata only, never on the wire; not worth churning four SDKs' public types.
Where: `server_configuration.mustache`.
Test: `server_variable_test.mustache`, `server_configuration_test.mustache`.

### Forced: Content-Encoding set (Forced divergences, T3-6)
Rule: advertise `gzip, deflate` only, no probe; see the Idioms table.
Why: stdlib availability of codecs; the web transport has no say.
Where: `abstract_api_client.mustache`, `default_api_client_io.mustache`.
Test: `default_api_client_unit_test.mustache`.

### Strict static typing (Resolved cross-language fixes, R8)
Rule: `analysis_options.yaml` sets `strict-casts`, `strict-inference` and `strict-raw-types`; only `constant_identifier_names` and `unintended_html_in_doc_comment` are turned off, each with a reason in the file; no generated file carries `// ignore_for_file:`.
Why: match node `strict`, csharp analyzers and php level 9; the flag caught a raw `TextMapPropagator`.
Where: `analysis_options.mustache`.
Test: `DartLintingSpec`.

### README requirements floor tracks the manifest (Resolved cross-language fixes)
Rule: the README states the Dart SDK floor from `pubspec` `sdk:`; never let it drift below.
Why: dart enforces the floor as a hard build gate.
Where: `readme.mustache`, `pubspec.mustache`.
Test: `metadata_test.mustache`.

### Oneof wrapper wording (Resolved cross-language fixes)
Rule: the central helper throws `No oneOf/anyOf variant matched the JSON`; do not harmonise it with the per-model wordings in other languages.
Why: harmonising would unify unrelated code paths.
Where: `object_serializer.mustache`.
Test: `composed_schema_test.mustache`.

### Multi-consumes selector rejects unknown values (Resolved cross-language fixes)
Rule: an unrecognised request content type throws `ArgumentError`; null defaults to the first declared type.
Why: a typo must not become an undeclared Content-Type on the wire.
Where: `base_api.mustache`, `header_selector.mustache`.
Test: `header_selector_test.mustache`, `base_api_test.mustache`.

### Test runner speed and CI constraints (S13)
Rule: spec parallelism is 3 (`junit-platform.properties`); the pub cache lives under `/root/.cache/dart/pub` (`PUB_CACHE`); no edits to `.github` workflows from this repo.
Why: the CI test job is CPU-bound; workflow issues belong in mridang/action-runner-common #47-#50.
Where: `DartSpec.getCacheEnv`.
Test: `DartClientSpec`.

### Idiom differences from the other eleven
Rule: each rule exists for a stated reason and must not be removed to "fix" a report: `fromJson`; explicit `options` with no default; `Iso8601Duration`, `UuidValue`, `NoAuthAuthenticator`; module-level `servers` and `allServers`; composed-schema plumbing members; `TraceContextUtil.propagator`; public `BaseApi.config`/`apiClient`; the `cause` field and optional named `cause` argument; sealed `OpenapiException` constructor; extra helper methods on internal classes; `val` setter names; default-argument scopes; extra injected-client constructor argument; `oauth2tokenmanager.exchanged` kept on the authorization-code authenticator; `isNoAuth`; `ApiClient.close` optional; `getTypedErrorBody` takes a decoder.
Why: Dart's lack of reflection, protected visibility and overloads forces each one.

### Mock seam (mocking001 to 009, issue #122 and the Invoker line of #131)
Rule: every API group has an interface and the client has one where the language can state it, additive next to the existing classes, using only SDK and standard-library types. `abstract interface class <Group>ApiInterface` in the group file and `ClientInterface` in `client.dart`. `BaseApi` is public, so the implicit interface of the class would force a fake to implement its members; the explicit interface avoids that. A fake implements the interface and forwards `noSuchMethod`. The tests read the source files, because Dart has no run-time reflection. Tests: test/client_test.mustache (001, 002, 007, 008), test/api/pet_api_test.mustache (003 to 006). Where: the templates named above, `BetterDartCodegen`, the README template (`Testing your code`), `docs/languages/dart.md`.

## Streaming: binary responses (capability 1 of #114, D118 to D122)

### The transport returns an unread body
Rule: `StreamingApiClient.sendStreaming(method, url, headers, body, {noRedirect, timeout, noDefaultHeaders, idleTimeout})` returns an `ApiStreamResponse` (status, lower-cased headers, the open `Stream<List<int>>` body, `readBounded(maxBytes)`, `close()`, a `toString()` of the status only). It is a second interface checked at call time, like `RetryingApiClient` and `AdmittingApiClient`, and not a default method on `ApiClient` (deviation from the first design: a default body is not inherited by a class that `implements ApiClient`, so it would break every existing transport and fake). `BaseApi` throws `UnsupportedError('<runtimeType> cannot stream: it does not implement sendStreaming, ...')` for a transport without it, and buffered calls keep working. `AdmittingStreamingApiClient.sendAdmittedStreaming` lets the first attempt ride the admission of `beginRequest`, as `sendAdmittedRequest` does. `AbstractApiClient` implements both, so the `dart:io` and web transports stream.
Where: `api_client.mustache`, `api_stream_response.mustache`, `base_api.mustache` (`invokeApiForStream` shares `_send` with `invokeApi`, so the hook, the headers, the authentication, the retries and the 401 replay are the buffered code), `abstract_api_client.mustache`.
Test: `default_api_client_unit_test` (`Streaming transport`: 001, 010, 011, 013, 016, 019, 020, 022, 029, 031, 032, 033), `base_api_test` (`BaseApi binary streams`: 008, 012, 014, 015, 017, 018, 030), `client_test` (021), golden `pet_api_test` (009).

### One header phase for both paths
Rule: `AbstractApiClient._open` merges the headers, builds the request and runs the redirect loop under the end-to-end deadline, and returns the response with its body unread; `_mapped` turns what it raises into the SDK errors. The buffered `_send` is `_open` plus a deadline-bounded read of the body; the stream call is `_open` plus the guard below, so the call `timeout` stops at the headers. Retries live in `BaseApi` as before: a stream attempt that fails before the headers is retried by the same loop, and no retry is possible once the stream has been returned, so nothing is retried after the first body byte. A non-2xx attempt reads at most 64 KiB of its (decoded) body, closes it and returns it as an ordinary buffered response, so the loop raises the typed error, retries it or replays after a 401 exactly as for a buffered call, and no stream is returned.
Where: `abstract_api_client.mustache`, `base_api.mustache` (`_openStream`, `_StreamReply`).
Test: every existing `default_api_client_unit_test` and `base_api_test` row, 014, 016.

### The body: idle timeout, truncation, decoding, cancellation
Rule: the body is handed out behind a private `_OpenBody` that subscribes to the response at once but paused, so `abort` and `close` can drop the connection before anything is read. It resumes when the consumer listens and pauses with it (backpressure), restarts an idle timer on each chunk while the consumer is not paused (`TransportOptions.idleTimeout`, default 60000 ms, `0` or `null` disables, a per-call override on `sendStreaming`) and raises `NetworkTimeoutException` when it fires. A failure of the response is mapped by `_bodyFailure`: a typed SDK error or a `StateError` stays, a decode failure of a compressed body is an `ApiException` with the real status, and anything else (a reset, a body that ends before its `Content-Length`, which `dart:io` reports as an `HttpException`) is a `StreamException` carrying the method and the path. `gzip` and `deflate` are decoded on the fly by `decompressStream` (a seam like `decompressBytes`: the `dart:io` transport binds the codec, the web transport passes the body through) unless the response has no body (HEAD, 204, 304, `Content-Length: 0`). `ByteStream` is a `final` class extending `Stream<List<int>>` (single subscription, `statusCode`, `headers`, `bytesDelivered`, `isClosed`, an idempotent `close()`, `readAll()` that closes, a `toString()` of status and bytes delivered); cancelling its subscription closes it, and listening to a closed one throws a `StateError`. `close()` never awaits the controller's done event, because a paused listener would never receive it.
Where: `abstract_api_client.mustache`, `byte_stream.mustache`, `api_stream_response.mustache`, `transport_options.mustache`, `errors/stream_exception.mustache`, `errors/api_exception.mustache` (`withRequest` keeps a `StreamException`).
Test: `default_api_client_unit_test` (011, 013, 019, 020, 029, 031, 032, 033), `base_api_test` (012, 015).

### Closing the client cancels open streams
Rule: `AbstractApiClient` tracks every open body. `close()` marks the client closed, aborts each open body (the consumer's next read raises `StateError('ApiClient has been closed and can no longer send requests')`) and releases the transport once no request is in flight; requests in flight that are not streams finish as before. A stream whose headers arrive after `close()` is refused with the same `StateError` and its connection dropped.
Where: `abstract_api_client.mustache`.
Test: `client_test` (021).

### Binary operations return `ByteStream`
Rule: `BetterDartCodegen.markBinaryStream` sets `streamsBinary` on `vendorExtensions.op` and rewrites `returnType` to `ByteStream` for an operation whose `streamKind` is `binary`, so every template (operation, interface, docs, `WithHttpInfo`) says `ByteStream` and `api.mustache` calls `invokeApiForStream`; the `returnType`, `deserialize` and `rawBody`-decode arguments are not emitted for it. The operation stays one method, `Future<ByteStream>`, that completes when the headers arrive; `ApiResult<ByteStream>` has an empty `rawBody`. `postProcessSupportingFileData` hands the README and SKILLS the first such operation (`binaryStreamOperation`) for the Streaming example. A `format: byte` response (JSON string) is not a stream and stays `Uint8List`. `ByteStream` is also a class name in `package:http`; the SDK always prefixes that import, and the language page tells users to do the same. An injected transport without `sendStreaming` makes a binary operation fail with the unsupported error: the accepted breaking edge of D119.
Where: `BetterDartCodegen`, `api/api.mustache`, `readme.mustache`, `skills.mustache`.
Test: golden `pet_api_test` (009; the avatar, document and photo rows close their stream; `downloadBinaryMock` reads its bytes), `default_api_client_unit_test` (029).

## Streaming: JSON Lines and server-sent events (capability 2 of #114, D120 to D122)

### The operation methods
Rule: `BetterDartCodegen.markBinaryStream` also sets `streamsSse` and `streamsJsonLines` for an operation whose `streamOnly` decorator is true, rewrites `returnType` to `SseStream<T>` or `JsonLinesStream<T>` and adds `streamItemDart`, `streamTyped` and `streamItemIsModel`. `api.mustache` renders such an operation as `Future<...> <op>Stream(params, options, [StreamOptions? streamOptions])` and nothing else (no buffered method, no `WithHttpInfo`, in the class and in the interface); the shared preamble (security, parameter checks, path, query, header and body building) moved to `api/operation_prelude.mustache` so both shapes use it. A model item type is decoded with `deserialize(data, T.fromJson)`; any other non-String type with `deserializeRaw(data) as T`; `String` hands over the raw text. `postProcessSupportingFileData` gives the README and SKILLS the first operation of each kind (`jsonLinesStreamOperation`, `sseStreamOperation` and their groups).
Where: `BetterDartCodegen`, `api/api.mustache`, `api/operation_prelude.mustache`, `readme.mustache`, `skills.mustache`.
Test: golden `store_api_test` (028).

### Parser and streams
Rule: `LineReader` and `SseParser` (`event_stream.mustache`, internal, not exported) implement the WHATWG grammar over a `Stream<List<int>>` with a skip-LF flag for CR; a cut-off last line is dropped by the SSE parser and decoded by JSON Lines. `JsonLinesStream` and `SseStream` are `final` classes extending `Stream`, single subscription, built as an `async*` generator behind a controller whose cancel closes the source at once instead of waiting for the generator (a pending read ends because the closed `ByteStream` ends). `SseStream.open` awaits the first connection; a reconnect waits `reconnectWaitMillis` (server `retry`, else `Retry-After`, else `reconnectDelay` doubling to 30 s less up to 25% jitter) in a timer that `close()` completes, and a clean end counts as a failure against `maxReconnects` (a `StreamException` when exhausted, a quiet end for 0). `BaseApi.invokeApiForSse` builds each connection with `invokeApiForStream`, so the hook, the headers, the credential and the retries are the buffered code; it adds `Cache-Control: no-cache` and `Accept-Encoding: identity` unless the caller or the configuration set them. The idle timeout of a call is `StreamOptions.idleTimeout`, passed to `sendStreaming`. `Retry-After` parsing moved to `retry_after.dart` (`retryAfterOf`) so `BaseApi` and `SseStream` share it.
Where: `event_stream.mustache`, `json_lines_stream.mustache`, `sse_stream.mustache`, `server_sent_event.mustache`, `stream_options.mustache`, `retry_after.mustache`, `base_api.mustache` (`invokeApiForJsonLines`, `invokeApiForSse`).
Test: `event_stream_test` (the shared parser table: streaming 002 to 005, sse 001 to 005, 008 to 011, 014, 016, 017, 019, 021 to 023), `default_api_client_unit_test` (streaming 006, 007, sse 007, 015, 018), `base_api_test` (sse 006, 012, 013, 020).

## Streaming: uploads (capability 3 of #114, D121 item 6)

### `BodySource` is the body type of a raw binary request
Rule: `BetterDartCodegen.markBinaryUpload` (run before the shared pass, which copies the body type into the signature arguments) rewrites the body parameter of an operation whose body is `format: binary` and neither multipart nor a form to `BodySource`, in every signature (operation, interface, `WithHttpInfo`); every other body kind keeps its type. `BodySource` (`body_source.mustache`, a `final` class, exported) has the replayable constructors `of(List<int>)` (copied, length known), `ofFile(String path)` (length is the file size, reopened per send) and `fromFactory(Stream<List<int>> Function()[, {int? length}])`, and the single-use `once(Stream<List<int>>[, {int? length}])`; `isReplayable`, `length` (null when unknown), `open()` (a second `open()` of a single-use body throws `StateError`) and a `toString()` of the kind and the length. A file is read through `body_source_files_io.dart` or `body_source_files_web.dart` (conditional import, like the environment): the browser has no file system, so `ofFile` raises `UnsupportedError` there. A Dart stream has no `close()`, so the S16 rule is: the SDK reads a stream passed to `once` to its end and cancels its subscription only when the request is abandoned (a deadline, an abort); a file or factory stream it opened is released by the transport. The path is a `String`, not a `dart:io` `File`, so the file stays out of the shared code.
Where: `BetterDartCodegen`, `body_source.mustache`, `body_source_files_io.mustache`, `body_source_files_web.mustache`, `api/api.mustache`, `barrel.mustache`.
Test: golden `pet_api_test` (operations 070 to 073, 076), `base_api_test` (misc 217).

### The transport sends it as it is read
Rule: `AbstractApiClient._open` builds a `_SourceRequest` (an `http.BaseRequest` whose `finalize()` opens the source lazily) for a `BodySource`, with `contentLength` set when the length is known and left null (chunked) otherwise; nothing is buffered. Retries live in `BaseApi`: `serializedBody` is the `BodySource` itself, so each attempt opens a replayable source again, and `replayable` is false for a single-use one, so it is never retried and never replayed after a 401, and the original error is raised unchanged. A 307 or 308 (and a 301 or 302 that keeps the method) opens a replayable source again for the hop and refuses a single-use one with `ApiException('Refusing to replay a streamed request body')` carrying the redirect status, after the HTTPS-to-HTTP check. In the browser `BrowserClient` collects the request body before it sends it (fetch cannot stream one), so the body is held in memory there; that is a limit of the platform, documented in the class comment of `BodySource` and the language page.
Where: `abstract_api_client.mustache`, `base_api.mustache`.
Test: `default_api_client_unit_test` (`Streaming uploads`: streaming 023, 024, 025, 027), `base_api_test` (streaming 026, misc 163).

### An event stream request with a single-use body never reconnects
Rule: `SseStream.open` takes `singleUseBody`; when it is true `maxReconnects` is 0 whatever the options say. `invokeApiForSse` passes `body is BodySource && !body.isReplayable`.
Where: `sse_stream.mustache`, `base_api.mustache`.
Test: `event_stream_test` (sse 016).

## Pagination (#163, D123, D124)

### The detector is shared, the Dart templates only render it
Rule: `AbstractBetterCodegen.populatePaginationDecorators` computes `vendorExtensions.op.pagination` (`style`, `isToken`, `isId`, `isOffset`, `isPage`, `isLink`, `isBody`, `isUrlPaged`, `source`, `requestParam`, `requestParamName`, `cursorType`, `responseProperty`, `responsePropertyName`, `itemsProperty`, `itemsPropertyName`, `itemsFromBody`, `itemType`, `itemModel`) and `hasPagesMethod`/`pagesMethodName`; Dart adds `dartPaginatorType` (`Paginator<Item, Cursor>`, `void` as the cursor of a `Link` or next-URL list, which is `isUrlPaged`) in `markPaginator`, and `addSuccessVariantImports` imports the item model, which the operation's own imports may not name. Model properties are final fields in Dart, so the templates read `page.<propertyName>`; an optional array property is `List<T>?`, so the paginator takes `Iterable<T>? Function(B)` and treats null as an empty page. The detector runs before the Options file is written, so a token-paged operation's Options class gets `withCursor(cursor)` (the same value for every other field), which gives each page its own cursor and leaves the caller's options untouched.
Where: `AbstractBetterCodegen`, `BetterDartCodegen` (`markPaginator`, `generateOptionsFileContent`, `postProcessSupportingFileData` for the README example), `api/options.mustache`.
Test: `OperationDecoratorTest`, `base_api_test` (pagination 024, 025, 026, 030, 032 read the generated API source for the methods that exist and those that do not, because Dart has no reflection).

### `Paginator<T, C>`, `Page<T, C>`, `PaginationException`
Rule: `Paginator` (`paginator.mustache`, a `final` class) has `items()` (a `Stream<T>`) and `pages()` (a `Stream<Page<T, C>>`), both built as `async*` generators, so a stream is lazy (no request before the first listen), one request is made per page and the generator waits at each `yield` while the listener is paused or has cancelled (`await for`, a `StreamIterator` and `take` therefore never read ahead). `ofCursor` (token and id cursors, the id style with `hasMore`), `ofOffset`, `ofPage`, `ofLink` and `ofNextUrl` are the static factories the generated `...Pages` methods call; each `items()` or `pages()` call starts again at the starting position. An end is an absent, null or empty cursor, or no `rel="next"`. A cursor equal to the one just sent (or a `Link` to the page just read, or a foreign origin) is held back: the page it arrived with is delivered and the next read raises `PaginationException`. Any failure of a fetch ends the stream. `Page` is a `final` class (`items` copied unmodifiable, `nextCursor`, `result`, a `toString()` of the item count and the status only). `PaginationException` (`errors/pagination_exception.mustache`) extends the root exception directly and carries `requestPath`. Cancelling a subscription cannot abort the request that is in flight (a Dart future cannot be cancelled and there is no call handle, see S7): it ends the iteration and nothing further is sent; the catalogue exception for pagination019 says so.
Where: `paginator.mustache`, `page.mustache`, `errors/pagination_exception.mustache`, `api/operation_pages.mustache`, `api/operation_pages_interface.mustache`.
Test: `base_api_test` (`BaseApi paged lists`: pagination 001 to 032 except 019).

### Tiers: id, offset, page number and next URL in the body
Rule: the readme's pagination section describes every style in the same words, and this generator follows it. The id style ends when `has_more` is false and the cursor it passes is the `last_id` of the page. The offset and page-number styles send the offset or page past the items received and end on an empty page or when a declared `has_more` is false; they are int32 only. A next URL in the body is followed like a `Link`, with `ofNextUrl`. No Dart language impossibility: the four styles need only `int`, `String` and `Stream`.
Where: `paginator.mustache`, `api/operation_pages.mustache`, `api/operation_pages_interface.mustache`, `api/api.mustache`, `api/options.mustache` (`withCursor` for the id, offset and page styles too).
Test: `base_api_test` (pagination 004, 005, 006, 029).

### Each page is an ordinary call; a `Link` page replaces the path
Rule: a token page calls `<op>WithHttpInfo` with the options of the call (or `withCursor(next)` for a later page), so retries, timeouts, redirects, authentication, redaction and per-call options apply to every page. A `Link` operation's `WithHttpInfo` delegates to a private `_<op>Page(...args, String? pageUrl)` whose body is the buffered call; when `pageUrl` is set it replaces `path` and clears the query, and `BaseApi` joins an authentication query to a URL that already has one with `&`. The origin of a next URL is checked against `config.baseUrl` (a relative reference on the first page resolves against it); an operation with a server of its own is not paged. `LinkHeader` (`link_header.mustache`, internal, not exported) is the RFC 8288 parser.
Where: `api/api.mustache`, `base_api.mustache`, `paginator.mustache`, `link_header.mustache`.
Test: `base_api_test` (pagination 007, 008, 023, 027).

## Compiled examples (#111)

- `examples/quickstart.dart` (`templates/dart/examples/quickstart.mustache`, registered by `BetterDartCodegen.getSupportingFileSpecs` when `generateExamples` is on) imports `dart:io` and the package barrel and runs the statements of `usage_statements.mustache` in `Future<void> main() async`; an error escapes uncaught with its own type. The README Usage fence and the SKILLS Quick Start fence include the same partial (`langdart089`), and the README and SKILLS per-call fences share `per_call_options_fence.mustache` (a function calling the example operation with a `const` options object; omitted when there is none).
- `dart analyze` already covers `examples/` (only dot directories are skipped), so `make analyze`, `make lint` and `DartLintingSpec` see the quickstart with the package rules; no analysis config changed. The golden formatter's `dart fix` and `dart format` also run over it, but never over the README or SKILLS, so fence text must be clean as written.
- `unused_local_variable` is a warning and `--fatal-warnings` makes it fatal, so a fence that only builds a value is a `file` fence with a function returning it (`newClient()`, `newTransport()`, `newAuthenticator()`), the form the go fences use; the testing fence declares the fake and a `newTestClient()` function. The model fence takes a model and calls `copyWith()`, so no fence builds a model from nothing; trailing `//` comments in fences became own-line block comments, as the SKILLS comment style asks.
- `DartExamplesSpec` writes every `dart` fence to `.out/fences/<file>_<nn>.dart`: a `file` fence as written, a `statements` fence with its leading imports on top and the rest inside `Future<void> fence() async`. The files sit inside the package, so `package:` imports resolve through `.dart_tool/package_config.json` and the root `analysis_options.yaml` applies; it runs `dart analyze --fatal-warnings --fatal-infos .out/fences`. Behaviours `langdart087` to `langdart092` are in `test/metadata_test.mustache` (group `examples`), under `{{#generateExamples}}`; `langdart091` reads the model class names from `lib/src/models`.
- SKILLS rendered "Credentials from the environment" twice; the duplicate block is gone.

## Known limitations and out of scope

Cancellation has no per-call handle in dart today (see S7 above). Declared-charset decoding handles latin1, ascii, utf-16 and windows-1252 only; `shift_jis` and the other multi-byte or iso-8859-x labels fall back to lenient UTF-8, because dart has no multi-charset stdlib (partial). Repeated multipart scalar parts carry `content-type: text/plain; charset=utf-8` (deferred, low priority). Bare `number` is a lossy `double` (`format: decimal` is an exact `Decimal`). A top-level response body that is a single bare decimal number is read from its double, because the source text is recorded per holding map or list. `format: byte` and `binary` map to `Uint8List`. SOCKS proxies are rejected. The warnings-fatal `make docs` target exists in the generated Makefile but no spec runs it (deferred). Model reuse across oneOf unions is under S4 (intended, not landed); allOf inheritance has landed.

Out of scope, per the closed backlog issues #53, #70, #78, #88 and #90 (S11): callbacks and webhooks, XML bodies and XML `nodeType`, Link Objects, mutual TLS; the README says so under "Not supported". OpenAPI 3.2 and 3.1 support is tracked in epics #49 (3.2) and #96 (3.1) with sub-issues in #50-#100; 3.2 documents load through the shared parser extension (D150).

## Build-breaking rules

- `dart analyze --fatal-warnings --fatal-infos` over `lib/` and `test/` must pass; an info-level finding fails the build.
- `strict-casts`, `strict-inference` and `strict-raw-types` are on; a new implicit downcast, un-inferrable type or raw generic fails.
- Never add `// ignore_for_file:` or a blanket suppression to a template; name the lint in `analysis_options.mustache` with a reason (only two are off today).
- `DartSpec.getSetupCommands` must stay `dart pub get`; a `dart format` or `dart fix --apply` there makes the formatting and linting specs check a rewritten tree. The generator's own allowlist (`dart fix --apply`, `dart format .`) runs at generation time inside the pinned image.
- The web path must compile with `dart compile wasm`: nothing from `dart:io` outside `default_api_client_io.mustache`.
- Do not hand-edit the golden under `src/spec/resources/generated/dart/`; regenerate all twelve and commit them current.
- A mustache `{{! ... }}` comment must not contain a literal `{{tag}}`; templates use `/* */` inside code and `///` for doc comments, so follow the neighbouring code.
- Do not use `trim()` for credential blankness; use the ASCII whitespace `RegExp`.
- Add dependencies to a generated test only if every client's keep-listed manifest gets them too.
- No `.github` workflow edits from this repo (S13).

## Verify

- `etc/ci-local.sh check Dart` (add `--quick` on a clean tree for the main session)
- `mvn verify -Dgroups=dart` for the language end to end
- Regenerate the golden: `mvn test -Dtest='Generate*ClientTest,FixtureVocabularyLeakTest' -DforkCount=0 -Djacoco.skip=true -DfailIfNoTests=false`, then commit all twelve goldens current
- Inside a generated tree: `dart pub get`, `dart analyze --fatal-warnings --fatal-infos`, `dart test`, `dart format .`, `make docs`

## Decisions taken in batch 6

- Charset: `_charsetOf` strips the quotes RFC 9110 allows around a parameter value, so `charset="windows-1252"` decodes like the unquoted form.
- Multipart filename fallback: one underscore per code point, not per UTF-16 unit (D90).
- OAuth2 expiry: one margin, `min(60 s, expires_in / 2)`, applied once when the expiry is stored and not again on read; a response with no `expires_in` clears the expiry (no expiry means cached until invalidated). The audit's proposal was taken as the default.
- Timeout (D77): the message is `Invalid timeout: must be a positive number of milliseconds (got N)`. Dart still accepts `0` (existing test, no behaviour change); only negatives are refused.
- `ApiHttpResponse` copies its headers into an unmodifiable map, so its constructor is no longer `const`.
- uniqueItems: no sort anywhere; README states the Set's own order.
- SKILLS "Models" sample builds the first model with its required primitive properties and reads one back.
- Test parity: the D17, D29, D07, D20, D09 and D15 messages are pinned in the mirror test templates.
- Already conforming: F278 (`escapeDartString` covers `'`, `$` and `\`, with a Java unit test) and F290 (`authHeaders()` throws `StateError` on every token-based flow).

## Decisions taken in batch 7

- Proxy credential: `Proxy-Authorization` is added to a request only when the target is plain http, and on a redirect hop only when the next URL is not https. On an https target `dart:io` answers the proxy's 407 on the CONNECT request through `addProxyCredentials`, so the header never travels inside the tunnel to the origin or the identity provider.
- Per-call authenticators: `BaseApi.invokeApi` injects its own `ApiClient` into a per-call `HttpAwareAuthenticator` before asking for headers, so a per-call OAuth2 or OIDC authenticator no longer fails with 'API client has not been injected'.
- Object-valued parameters (D47): a model or map passed to a styled parameter is flattened through its `toJson()` map (`parameterObject`). Form-exploded query objects become one pair per property (`ExplodedObject`, expanded by `BaseApi._buildQueryString`); non-exploded form, simple, label and matrix join the properties as `key,value,...`; deepObject takes the same map. A nested object or array leaf throws `SerializationException`.
- Redirect strip: besides the fixed and spec-declared lists, a header whose name contains `key`, `token` or `secret` (the S15 redaction rule) is dropped on a cross-origin hop.
- Header merge (in part): a call header replaces a default header of the same name in any case. Token and discovery requests still carry the transport default headers: stopping that needs a flag on the public `ApiClient.sendRequest`, which is a cross-language decision not taken here.
- SKILLS.md: the Error Handling section renders the root `...Exception` tree, including `SerializationException`, `OAuth2ServerException` and `OAuth2TokenException`, and the sample ends with a catch of the root.
- DEV-DEPENDENCIES: `metadata_test` checks every package in `.openapi-generator/DEV-DEPENDENCIES` against the pubspec `dev_dependencies`.
- Test parity added in the mirror templates: F1111 (StrictTag), F1126 (byte parameters), F1113 (2ndPage), F1114, F1115, F1116, F1130, F1131, F1144, F1145, F1146, F1148, F1149, F1175 (model wire shapes in `object_serializer_test`), F1127, F1142, F1147, F1128, F1069, F1155 (operation tests), F1075 (token request headers), F1063 and F1067, F1121, F1124, F1169 (the cookie test now asserts the header on the wire).

## Decisions taken in batch 9

- Rendered error: `ApiException.toString()` caps the body at 1 KiB of UTF-8 (never cutting a code point) with `... (N more bytes)`, escapes control characters as `\xNN`, and shows `***` for the value of authorization, proxy-authorization, cookie, set-cookie, www-authenticate, proxy-authenticate and any header name containing key, token or secret. `responseBody` and `responseHeaders` keep the full values.
- PKCE: `OAuth2AuthorizationCodeAuthenticator.buildAuthorizationUrl` adds `code_challenge` and `code_challenge_method=S256` (32 random bytes as unpadded base64url, 43 characters), keeps the verifier, and `exchangeCode` sends `code_verifier` once and drops it. SHA-256 is written in plain Dart in the authenticator (no `package:crypto`, no native code) and pinned by the RFC 7636 appendix B vector through the public static `pkceChallenge`. The OIDC authenticator hands the pending verifier (`pendingPkceVerifier`, `resumePkce`) to a delegate that a rediscovery with new endpoints replaces. OIDC (D140): `buildAuthorizationUrl` appends a fresh 43-character `nonce` last, held in the OIDC authenticator; `exchangeCode` consumes it and validates the `id_token` through the `idTokenCheck` hook of `exchangeCode` and `OAuth2TokenManager.exchange` (nonce, discovered issuer, client id in `aud`, `exp` with 60 s skew; no signature check), throwing `OAuth2TokenException` and restoring the previous session on failure. Test: `bhv authoidc 034` to `041`.
- 401 replay: the new public interface `AccessTokenInvalidating` (in `http_aware_authenticator.dart`) is implemented by the OAuth2 client-credentials, password, auth-code and OIDC authenticators (`invalidateAccessToken()` keeps the refresh token). `BaseApi.invokeApi` drops the token on a 401, fetches fresh headers and replays once; a failed re-authentication or a second 401 is the usual `UnauthorizedException`. API-key, basic, bearer and implicit authenticators do not implement it, so they never replay; a multipart body is never replayed.
- Retries: `TransportOptions.maxRetries` (builder `maxRetries(n)`, default 2, 0 disables, negative refused). The loop lives in `BaseApi.invokeApi`, not in the transport, because every attempt must rebuild the headers and ask the authenticator again. The transport exposes its policy through the new `RetryingApiClient` interface (`maxRetries`, `timeoutMillis`), implemented by `AbstractApiClient`; an `ApiClient` that does not implement it is never retried, so existing fakes keep working. Retried: `NetworkException` (including `NetworkTimeoutException`, which only repeats when budget is left) and statuses 408, 409, 429, 500-599 except 501. Backoff 0.5 s doubling to 8 s less up to 25% jitter; `Retry-After` (seconds or HTTP date) capped at 60 s replaces it. A wait that would outlast the end-to-end `timeout` is not taken, and later attempts are bounded by the time left. `BaseApi.sleepBeforeRetry` is the seam tests override. Dart has no cancellation token: closing the client makes the next attempt raise `StateError`, which is not retried. A body read that fails after the headers arrived is also a `NetworkException` in this SDK and is retried.
- Tests that expect one failing request through `DefaultApiClient` build it with `maxRetries(0)`.
- Response size limit (D138): `TransportOptions.maxResponseBytes` (builder `maxResponseBytes(n)`, default null for no limit, zero or negative refused with `ArgumentError` `Invalid maxResponseBytes: must be a positive number of bytes (got N)`). `AbstractApiClient._send` fails before reading when a declared `contentLength` is over the limit (not for HEAD, 204, 304), else `_readWithin(maxBytes:)` counts the bytes received and cancels the subscription (dropping the connection, not draining it) once the count passes the limit. Decoding goes through the seam `decompressBytesWithin`: the native transport inflates gzip and deflate through `startChunkedConversion` into a counting sink that throws as soon as the output passes the limit; the base default (used by the web transport, where the browser already decoded the body and the read was capped) checks the decoded length, and keeps `decompressBytes` unchanged so existing subclasses compile. The failure is `ResponseTooLargeException` (a direct `ApiException` subclass with the real status, the lower-cased headers, an empty body and `limit`; `isRetryable` is false, `withRequest` keeps the type, and `BaseApi` never retries a non-network `ApiException`). Streamed bodies are not counted. Test: `transport_options_test` (`bhv options 069`), `default_api_client_unit_test` (`bhv transport 095`, `096`), `api_error_test` (`bhv errors 007`).
- F825 is Rust only; skipped.

## Decisions taken in batch 10

- Token type: a token response whose `token_type` is present and not `bearer` (any case) fails with `OAuth2TokenException('Unsupported token_type in token response')`, never echoing the value.
- OIDC discovery: an `issuer` that differs from the discovery URL without `/.well-known/openid-configuration` (trailing slash ignored) throws `SerializationException`; a document without an issuer is accepted. An authorization or token endpoint must be https or an http loopback host.
- Passwords: a `format: password` model property prints as `***` in `toString()`; `toJson()` keeps the value.
- CA errors (D79): `failed to read CA certificate from "<path>": <cause>` and `failed to parse CA certificate from "<path>": <cause or no PEM blocks found>`, both `ArgumentError`.
- Server URLs: `ServerConfiguration.url` substitutes in one pass, so a value containing `{other}` is never expanded again. Constants are const, so the collections cannot be modified (is pinned by an `UnsupportedError` test; a copy at construction is impossible with const constructors).
- Names: schemas named like a `dart:core` type (D45 seed set) get the `Model` prefix, including at `$ref` sites; path or body parameters named like an injected argument or method local (`options`, `server`, `requestContentType`, `path`, ...) get a `Param` suffix. Both are unit-tested in `BetterDartCodegenTest`.
- Lint: `make lint` also runs `dart format --output=none --set-exit-if-changed .`.
- File header: `api_info.mustache` unescapes the info description.
- Docs: README has Installation and Usage (raw responses, timeouts, retries, expired tokens, request id, cancellation, custom HTTP client) above Caveats and Not supported (XML, Link Objects, mutual TLS added); SKILLS gained Unauthorized answers, PKCE, Cancellation and Raw responses.
- Tests added in the mirror templates: F927 (exact D16 messages), F1133 (redirect-chain timeout), F1135 (identity), F1141, F1158, F1161 (`/test/response-headers`), F1167 (D22), F1065, F1119, F1124 (third server, nickname).

## Decisions taken in wave 2

Implemented, in dart's idiom:

- Request context: `ApiException` and every subclass (including `NetworkException` and `NetworkTimeoutException`) carry `requestMethod` and `requestPath`, the path with the query string and fragment cut. `ApiException.fromResponse(status, headers, body, requestMethod:, requestPath:)` builds the message `<METHOD> <path> returned status code <n>`, and `API returned status code <n>` when no request is given (the OIDC discovery names its own `GET` and path). `BaseApi.invokeApi` is the one place that sets them; a failure that arrived without a response is rebuilt through `ApiException.withRequest`. Test: `api_error_test`, `base_api_test` (`BaseApi request context on errors`).
- Typed errors (issue 123): one `<Schema>Exception` per distinct declared error schema, written to `lib/src/errors/` from `errors/schema_exception.mustache` and exported from the barrel. It extends the status class of its status (the lowest common one when declared under several; `4XX` is `ClientException`, `5XX` is `ServerException`, `default` is `ApiException`). Its `errorBody` getter is the typed model (covariant override of the `Object?` field); `ApiException` is not generic. Each operation passes `errorSchemas`, a const map of declared status to `<Schema>Exception.fromErrorResponse`, through `invokeApiForResult` and `invokeApi` to `ApiException.fromResponse`, which picks the exact status, then `4XX`/`5XX`, then `default`. A body that does not decode returns the status class with the raw body and the `SerializationException` as `cause`. `isRetryable` is status only (408, 409, 429, 5xx but 501) and `requestId` reads `x-request-id` case-insensitively. A response body that cannot be decoded raises a `SerializationException` carrying `statusCode`, `responseHeaders`, `responseBody`, `requestMethod` and `requestPath` without the query (`withResponse`). Test: `api_error_test` (bhv errors 201-204), `base_api_test` (bhv errors 205).
- Per-call options: every generated Options class has `timeout` (milliseconds, end to end), `headers` (replace a same-named header in any case) and `maxRetries`. `BaseApi.invokeApi` takes `timeout`, `callHeaders` and `maxRetries`, refuses a negative `timeout` or `maxRetries` with `ArgumentError`, and `ApiClient.sendRequest` gained the named `timeout` argument (a custom `ApiClient` must add it). The retry budget uses the per-call values. A parameter named `timeout`, `headers` or `maxRetries` is suffixed `Param` like the other injected names. Every operation has an Options class: `BetterDartCodegen.mintsOptionsForEveryOperation()` returns `true`, so an operation with no query, header, form or cookie parameter and no security gets a class holding only the three per-call fields (no `auth`), and its signature is `(path and body parameters, [server], options)`; the barrel exports it. A multi-content operation keeps `requestContentType` as the optional trailing argument after `options`. Test: `base_api_test` (`Per-call options`), `default_api_client_unit_test`, `BetterDartCodegenTest`.
- Immutable models: every model field is `final` and every model with properties has `copyWith`. A required non-null field takes `T?` (null keeps the value); a nullable field takes `Object?` defaulting to the private sentinel `copyUnset`, so an explicit `null` clears it (`copyField<T>` does the cast). `copyUnset` and `copyField` live in `object_serializer.dart`, which the barrel does not export. Collections inside a model are shared with the copy, not duplicated. Test: `object_serializer_test`.
- Several 2xx responses: `successResponses` from the shared rule drive a `deserializeByStatus` callback on `invokeApiForResult`; the body is decoded as the schema of the status the server answered with and wrapped in the synthesised union through its public constructor, never by trying the variants in turn. Test: `base_api_test` (`Multiple 2xx responses`).
- Typed union members (dart's idiom): a oneOf or anyOf wrapper has, for every model or primitive variant, a named constructor (`PetFood.dryFood(v)`), `isDryFood` and `asDryFood`; list, map and byte variants keep the untyped constructor. `fromJson` is unchanged. Test: `composed_schema_test`.
- Package metadata: the `repositoryUrl`, `homepageUrl`, `documentationUrl`, `issueTrackerUrl` and `authors` options (plain additional properties, registered as CLI options in `BetterDartCodegen`) go to the pubspec (`repository`, `homepage`, `documentation`, `issue_tracker`) and the README; a pubspec has no authors or license field, so `authors` is README only and the SPDX id stays the existing `licenseId` comment line. `GenerateDartClientTest` sets all five so the golden shows them. Test: `metadata_test` (`package links`).
- Credentials: `BearerAuthenticator`, `ApiKeyAuthenticator` and `BasicAuthenticator` take, in order, the explicit argument, a passed provider (D108: it outranks the variable), then an environment variable read once at construction (an empty value is unset). The names come from the shared generator (`securitySchemeEnvVars`, `envVarPrefix`, `EnvVarNames`), so all twelve SDKs read the same one: the per-scheme authenticator template passes each `{{envName}}` of `envVars` to its base authenticator as `usernameVariable`, `passwordVariable` or `environmentVariable` (`BetterDartCodegen.environmentArguments` picks the argument), and the base authenticators default to `<envVarPrefix>_TOKEN`, `_API_KEY`, `_USERNAME` and `_PASSWORD`. The OAuth2 and OpenID Connect authenticators read the same way (#119): `clientId`, `clientSecret`, `username`, `password` and `redirectUri` are optional arguments, each read from its shared variable when not passed, through `requireCredential` in `environment.mustache` (the explicit argument wins, an empty variable counts as unset, and a missing one throws `ArgumentError` naming the variable); the per-scheme wrapper passes the names as `clientIdVariable`, `clientSecretVariable`, `usernameVariable`, `passwordVariable` and `redirectUriVariable` (`environmentArguments`), and the base defaults are `<envVarPrefix>_CLIENT_ID`, `_CLIENT_SECRET`, `_USERNAME`, `_PASSWORD` and `_REDIRECT_URI`. `tokenUrl`, `authorizationUrl` and `openIdConnectUrl` stay fixed from the spec. The per-scheme test group (`bearer_authenticator_test`) carries the catalogue cases authscheme 008 (OpenID Connect) and 013 to 016 (client credentials, password, authorization code, implicit), each setting one variable empty and naming it. A callback (`tokenProvider`, which may be async, `apiKeyProvider`, `credentialsProvider`) is asked on every request, and passing both a value and a callback is refused. A credential found nowhere throws `ArgumentError` where the authenticator (and so the client) is built. `environment:` replaces the process environment (the browser has none; `environment_io.dart` is the only other library file that imports `dart:io`, selected through a conditional import). `Client.withToken` takes `accessToken` or `tokenProvider`. Test: the three authenticator tests, `client_test`, and the `per-scheme authenticators` group of `bearer_authenticator_test` (the shared names, a missing and an empty variable for every scheme).
- Strict integers and containers: `readInt32` and `readInt64` (and the `OrNull` forms) replace `as int` for `isInteger` and `isLong` properties and for container leaves, so a fractional, quoted, null or out-of-range leaf raises `SerializationException`. Test: `base_api_test` (`Strict integers`).
- Non-finite doubles: `readDouble` and `readDoubleOrNull` replace `(x as num).toDouble()` and refuse an infinite result (`1e400`) with `SerializationException`. Test: `object_serializer_test`.
- Identity provider requests: `ApiClient.sendRequest` has `noDefaultHeaders`; the OAuth2 token POST and the OIDC discovery GET pass it, so the transport default headers (an `Authorization` or API-key header meant for the API) never reach the identity provider. Test: `default_api_client_unit_test`, `oauth2_token_manager_test`, `openid_connect_authenticator_test`.
- `ExplodedObject`: `pairs` is now `entries`, a `List<(String, String)>`, like node's; the three allow rows were removed.
- File header: the barrel's library doc unescapes and wraps the info description. Test: `metadata_test`.
- README: sections on per-call options, errors, credentials, copying models, several success responses, response compression, logging and versioning, and caveats for optional-versus-nullable, unknown enum or discriminator values, the joined response-header map, streaming and `Configuration` versus `TransportOptions`. Test: `metadata_test`.
- F1124: the out-of-pattern `nickname` row (`ZZZZZZZZZZ`) round-trips unchanged because dart enforces no constraint (S3). Test: `object_serializer_test`.
- Owner decisions of 2026-11-17: F857 the client takes one optional credential per scheme (`clientSchemes`, `BaseApi.schemeAuthenticator`, no `auth` on Options; test `base_api_test` `Security scheme credentials`); F840 cookies are raw when every character is an RFC 6265 cookie-octet, else `ArgumentError` before sending (test `pet_api_test` `cookie parameters`); F1078 `AbstractApiClient.close()` is graceful through an in-flight counter (test `default_api_client_unit_test`); F316 constructors never validate (test `object_serializer_test`).
- F1085 (test TLS keys): done as a shared change; the run generates the certificates and mounts them at `test/fixtures/certs`, so the local TLS tests read the generated key and none is committed.

Recorded, not implemented here:

- F1087 (runtime floor): dart keeps `>=3.11.0`. Dart has no long-term support line, only the current stable is supported, so the floor is not newer than the oldest non-EOL release.
- F866 (`$ref` sibling default) is landed in dart: `BetterDartCodegen.applySiblingEnumDefault` feeds the shared `siblingDefault` to `resolveEnumRefDefault`, so `StockItem.availability` starts at `Availability.onHold`, decodes to it when the key is absent and encodes it. Test: `object_serializer_test` and `BetterDartCodegenTest`.
- F868 for collections: the lists and maps inside a model stay mutable; a `copyWith` shares them.

## OpenAPI 3.2 round 2 (D167, D168)

- D167: an operation sends every declared response media type in `Accept`, and a per-call `Accept` in the Options `headers` replaces it whatever its case (`BaseApi._mergeHeaders`), so the wire carries exactly the one the caller names. Test: `pet_api_test` (bhv operations 501).
- D168: an operation whose 2xx responses declare headers gets `<OperationId>ResponseHeaders` in `lib/src/api/options/` (template `api/response_headers.mustache`, written by `BetterDartCodegen.writeResponseHeadersFile` and exported from the barrel), with a nullable `final` field per header typed by its kind (`String`, `int`, `double`, `bool` or a `List` of one) and `factory .from(Map<String, String> headers)` that reads each header case-insensitively and raises `SerializationException` on a value that does not parse. Test: `store_api_test` (bhv headers 501, 502).

## Gate lessons (what broke the gate and how to avoid it)

- Generated code is verified only by the Docker specs. Run `etc/ci-local.sh check Dart` before every push; the quick per-language checks skip the lint specs, which is how Dart lint failed on CI. Never run only `DartClientSpec`.
- `dart analyze --fatal-infos` is the gate (`DartLintingSpec`). `unnecessary_import` appears after batch merges (`value_serializer.dart`); remove imports that another import already exports. Cheapest check: `dart analyze --fatal-infos` in the generated SDK.
- Every symbol a template uses needs an import and a definition. Every method called from `BaseApi` must exist (`_buildQueryString`, `_generateUuid`, `_buildMultipartBody`, `_serializeBody`, `_throwApiError` were once undefined), and test mock authenticators must implement newly added interface members (`authHeadersAsync`). `dart analyze` finds both.
- Web-safe imports: library code under `lib/` must not import `dart:io`. Only `default_api_client_io.mustache` and `environment_io.mustache` may, selected through a conditional import against `default_api_client_web.mustache`. Check: `grep -rn "dart:io" src/main/resources/templates/dart` outside the `_io` variant and `test/`; `DartWasmSpec` (`dart compile js` and `dart compile wasm`) is the real gate.
- Strict analysis modes (`strict-casts`, `strict-inference`, `strict-raw-types`) reject raw generics in tests (the trace-context test). Write `List<int>`, `Map<String, Object?>`. Do not hide findings behind `ignore_for_file`; check with `grep -rn ignore_for_file`.
- Never run `dart fix --apply` unfiltered: it once added 219 issues and produced `final var x`. Use `--code=<rule>` per rule, then `dart format .`, and compare the `dart analyze` issue count before and after.
- `dart format` is part of lint (`--set-exit-if-changed`) and its version churns (`}) :` versus `})  :`, 70 files). Pin the formatter version, regenerate once, and commit all twelve goldens current.
- junit.xml must be valid XML: a form feed in a test name produced `&#xC` and hid every failure. Keep control characters out of test names (name the case by key length). A log line `No test suites found` means the XML was unreadable.
- `dart test | junit` hides failures in the harness; use `set -o pipefail` and check the exit status.
- No `//` comments under `lib/` or in templates (`DartFormattingSpec`); use `///` doc comments or `/* */`. A Mustache `{{! }}` comment ends at the first `}}`, so never put `{{#items}}` inside one.
- Set and `uniqueItems` changes need both deserialization (`.toList()` versus Set) and serialization updated. Changing a type mapping (`date` to `DateTime`) needs the generated tests changed together. `Uint8List` is a `List<int>`, so `isNot(isA<List>)` is wrong.
- Never rely on a Java field default for names (`packageName = "petstore_client"` leaked the fixture name). Dart cannot skip brotli or zstd in pure Dart: skip those tests with a reason.

- Trace context from the caller (#136): the hook runs in the operation's own zone, after the credential await, so a hook that reads the zone's context sees the caller's span; the base-api tests prove this with a zone value (D95).

- **F15 (typed `additionalProperties`, #103).** The bucket is `Map<String, V>`; `fromJson` runs `deserializeMap` with the `deserialize_arg` decoder of the value type, and `toJson` writes date-time, uuid and byte values in their wire form.
- Round 2 additions: `BaseApi.requirementAuthenticator` resolves an operation whose security block has a requirement naming several schemes (`vendorExtensions.op.securityGroups`, `hasAllOfGroup`, from the shared generator) into one composite that sends every credential; operations without such a group keep `schemeAuthenticator`. `_multipartTypeOf` keeps a declared `multipart/<token>` subtype and falls back to `multipart/form-data` for an unsafe value. Model `additionalProperties` is a getter that copies. `HeaderSelector` accepts null accept and content type.
- D190 (#91): `auth/scheme_authenticator.mustache` adds the deprecated-scheme sentence to the class comment when `schemeDeprecated` (never `@Deprecated`), and `static const String metadataUrl` (an `escapeDartString` literal) when `hasOAuth2MetadataUrl`. Test: `bhv authscheme 700` in the `per-scheme metadata url` group of `bearer_authenticator_test`, rendered over `metadataUrlSchemes` (the first scheme with a metadata URL).
- D191 (#92): `auth/oauth/oauth2_device_authorization_authenticator.mustache` holds both `DeviceAuthorization` and the authenticator (exported from the barrel under `hasOAuth2DeviceAuthorization`); the device request goes through `OAuth2TokenManager.postForm` and the static `lifetimeSeconds`, the poll through `exchange`; `sleepBeforePoll` and `monotonicMilliseconds` are the overridable test seams. Test: `bhv authoauthtwodevice 700`-`706` in `oauth2_token_manager_test` (706 rendered over `deviceSchemes`, the first device wrapper).
- D192 (#89): `auth/digest_authenticator.mustache` (`DigestAuthenticator` with plain-Dart MD5 and SHA-256, no `package:crypto`; `cnonceSource` the documented internal test seam; no lock, one isolate) exported from the barrel under `hasDigestAuth`; `BaseApi._send` checks `effectiveAuth is DigestAuthenticator` (import and code under `{{#hasDigestAuth}}`): `sendOnce` adds the answer for the method and request target, and a 401 with a challenge it accepts is replayed once through the same loop, buffered and streamed. The scheme wrapper takes the Basic constructor without `credentialsProvider`. Test: `bhv authdigest 700`, `701`, `704`-`706` in `basic_authenticator_test`, `702`, `703` in `base_api_test` (`_DigestStreamingClient`).
