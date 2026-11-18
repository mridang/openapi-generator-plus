# Node (TypeScript) SDK generator

The node generator (`node-plus`) emits a TypeScript SDK that runs on Node 22 or newer (`engines.node >=22`) and on fetch-only runtimes (Deno, Bun, Workers, Vercel Edge). It targets ES2024 with `strict: true`, NodeNext modules and legacy `experimentalDecorators` (class-transformer drives model decoding; `emitDecoratorMetadata` stays off to avoid temporal-dead-zone errors on circular model imports). The HTTP stack is the Fetch API: the `#transport` conditional import resolves to `DefaultApiClient` on Node (fetch plus an undici `Agent`/`ProxyAgent` dispatcher for proxy and custom TLS) and to a portable `DefaultApiClient` on other runtimes that uses only Web-standard APIs. Runtime dependencies are `class-transformer`, `reflect-metadata` and `temporal-polyfill`, with optional `undici` and an optional `@opentelemetry/api` peer. Tests run under Jest (ts-jest, `node --experimental-vm-modules`), lint is ESLint 9 flat config with `typescript-eslint` recommended, formatting is Prettier 3, docs are typedoc, and the formatter and spec container is `node:24`.

## Read first

- [Shared generator rules](../AGENTS.md), where every cross-language decision is written once.
- [Repository root rules](../../../../../../../../../AGENTS.md).
- Codegen class: [BetterNodeCodegen.java](BetterNodeCodegen.java).
- Templates: `src/main/resources/templates/node/` (the pointer file there leads back to this one).
- Spec classes: `src/spec/java/io/github/mridang/codegen/spec/node/` (`NodeSpec`, `NodeClientSpec`, `NodeFormattingSpec`, `NodeLintingSpec`, `NodeTypeCheckSpec`, `NodeReservedWordsSpec`).
- Golden: `src/spec/resources/generated/node/` (never hand-edit; regenerate).

## Layout

| Path | What lives there | Edit this to change |
|---|---|---|
| `generators/node/BetterNodeCodegen.java` | type mapping (branded types, Temporal), defaults, enum naming, supporting-file list, per-operation Options classes, scheme authenticators, barrel files | type mapping, enum members, which files are emitted |
| `templates/node/errors/*.mustache` | the error tree (`*Error` suffix, ES2022 `{ cause }` options bag) | error classes and messages; `schema-error.mustache` renders one `<Model>Error` per declared error schema, a subclass of the status error (`ClientError<Model>`, `NotFoundError<Model>`, ...) so `errorBody` is typed through the existing generic |
| `templates/node/auth/**` | authenticators and `auth/oauth/*` token manager, flows, OIDC | auth behaviour |
| `templates/node/abstract_api_client.mustache`, `default_api_client.mustache`, `web_api_client.mustache`, `transport_options.mustache` | redirects, timeout budget, decompression, cancellation, proxy and TLS, the two transports | transport options and wire behaviour |
| `templates/node/object_serializer.mustache`, `value_serializer.mustache`, `base_api.mustache` | JSON decode and encode, path, query, header and form encoding, multipart | serialization |
| `templates/node/models/model.mustache`, `deserialize_arg.mustache`, `brand.mustache`, `deep_input.mustache` | model classes, branded `UUID`/`URI`/`Email`/`Decimal`, `DeepInput` request types | models |
| `templates/node/api/*.mustache`, `client.mustache`, `configuration.mustache`, `server_configuration.mustache`, `servers.mustache` | per-tag API classes, Options classes, facade, builder | operation surface, server variants |
| `templates/node/test/**` | the emitted Jest tests (extend these; never add a test file) | generated tests |
| `templates/node/readme.mustache`, `skills.mustache`, `typedoc.mustache` | README, SKILLS.md, typedoc config | docs |
| `templates/node/package.mustache`, `tsconfig*.mustache`, `eslint_config.mustache`, `prettierrc.mustache`, `makefile.mustache` | package manifest and tooling config | dependencies, lint and compiler flags |
| `src/main/resources/reserved-words/node.txt` | reserved words, pinned by `NodeReservedWordsSpec` | escaped identifiers |

## Idioms (deliberate differences from the other eleven)

| Topic | What this language does | Why |
|---|---|---|
| Format types | `UUID`, `URI`, `Email`, `Decimal` are branded string aliases; unions of primitives (`Array<Buffer> \| Buffer`) are plain aliases with no wrapper class; enums of some operations are literal unions | TypeScript has no nominal types. |
| Construction | `Configuration` and `TransportOptions` take one options object plus a fluent builder | named-argument idiom of TypeScript |
| Error construction | every `*Error` takes the ES2022 `{ cause }` bag as its last argument | that is the signature of `Error` itself |
| Auth header accessor | a distinct `...Async` method beside the sync one; `isNoAuth` and `isHttpAwareAuthenticator` type guards | value-wrapped async (`Promise`) like csharp and dart; no interface check at runtime |
| Cancellation | trailing per-call options object `{ signal?: AbortSignal }` plus an optional client-level signal on `Configuration` | see S7; it is what the OpenAI and Stripe SDKs take |
| Transport swap | `#transport` conditional import: Node gets undici, other runtimes get a fetch-only client that throws `TypeError` for proxy, custom CA or disabled TLS verification | non-Node runtimes have no dispatcher |
| Content-Encoding | pass-through when the name is unknown; fetch already decoded gzip, deflate, br and zstd | forced, see D-FORCED-1 |
| Helper visibility | extra helpers on `ObjectSerializer`, `ValueSerializer`, `HeaderSelector`, `AbstractApiClient`, `DefaultApiClient`, `NO_AUTH`, `markExchanged` and the token-manager hand-over | no package-private visibility across files |
| Model equality and dump | classes with `equals()`; string form is `util.inspect`, with `format: password` fields masked as `***` | JS has no structural equality; inspect already dumps fields |
| Scheme credentials | the client takes `new Client({ <scheme>: authenticator, ..., authenticator })`, one optional credential per distinct security scheme in an object, or a single authenticator | the other SDKs use named arguments or a Credentials struct; TypeScript has no named arguments, so the object is the named-argument idiom |
| Missing-parameter error | `TypeError` with a double-quoted message string | the message embeds `'<param>'`, so a single-quoted literal is a syntax error |
| Dates and times | `Date` for date-time, `Temporal.PlainDate`/`PlainTime` from `temporal-polyfill` | no native date-only or time-only type |
| Decimals | branded `Decimal`; decode keeps the number text via `JSON.rawJSON`/reviver source | JS numbers are binary doubles |

## Settled decisions (do not re-open in review)

### Cancellation is a per-call `{ signal }` plus a client-level signal (S7, 2026-11-16)
Rule: every operation takes a trailing options object `{ signal?: AbortSignal }` and `Configuration` takes an optional client-level `signal`; the two are merged and either cancels the call. Cancellation raises the caller's own abort reason (an `AbortError` by default), never `NetworkTimeoutError`; the timeout is a separate `AbortSignal.timeout`.
Why: this is what the OpenAI and Stripe SDKs take; a shared `RequestOptions` wrapper would be less natural than the native handle. The owner approved it.
Where: `abstract_api_client.mustache` (`isCallerAbort`, merged signals), `configuration.mustache` (`signal`), `api/apis.mustache`.
Test: `test/default-api-client*.test.mustache`, `test/configuration.test.mustache`, `test/base-api.test.mustache`.

### Injected transport is `fetch` plus `fetchOptions` (S9, 2026-11-16)
Rule: the client builder accepts the standard `fetch` function and `fetchOptions`, keeps the SDK defaults applied, never closes a caller-supplied client, and fails fast when it conflicts with the SDK's own proxy, TLS or pool settings.
Why: every SDK generator that was compared (OpenAI, Stainless, Speakeasy, Fern, Azure, Stripe) accepts the standard client object.
Where: `abstract_api_client.mustache` (the `fetchOptions` spread; caller-controlled keys such as headers, body, redirect, timeout and signal always win), `client.mustache`.
Test: `test/default-api-client-unit.test.mustache`.

### Strict containers (S1, 2026-11-16)
Rule: a response that is a container of primitives is checked element by element like a single primitive.
Why: the old `x as T` casts check nothing at runtime. Open leftovers F469 and F470 are the plain string, `Decimal`, `UUID` and `Buffer` leaves in `deserialize_arg.mustache` and the bare primitive return in `apis.mustache` (`json as <returnType>`), which the petstore cannot lock until it gains such operations.
Where: `deserialize_arg.mustache`, `object_serializer.mustache` (`deserializeLeaf`), `api/apis.mustache`.
Test: `test/object-serializer.test.mustache`.

### Content parameters use the SDK serializer (S2, D50, D80, 2026-11-16)
Rule: `content: application/json` parameters in query, header, path and cookie go through `ObjectSerializer.serialize`, never `JSON.stringify`.
Why: wire names, canonical date-times, omitted nulls and rejected NaN must match the body path.
Where: `api/apis.mustache`, `value_serializer.mustache`.
Test: `test/value-serializer.test.mustache`, `test/base-api.test.mustache`.

### Schema constraints are not enforced (S3, D55, 2026-11-16)
Rule: node does not enforce pattern, length, range, multipleOf or item-count keywords; the server owns validation. The README has a "Schema constraints" section.
Why: only python enforces them, natively via pydantic; class-transformer gives no cheap equivalent.
Where: `readme.mustache`, `models/model.mustache`.
Test: golden README and `test/metadata.test.mustache`.

### allOf with a discriminator uses class inheritance (S4, 2026-11-16)
Rule: the allOf parent with a discriminator is a base class that subtypes extend; a model reused in two unions is never re-parented, unions are type aliases or interfaces.
Why: a class has one parent. Inheritance only where the schema is allOf plus discriminator.
Where: `models/model.mustache`, `BetterNodeCodegen.setsDiscriminatorDefaultOnChildren`, `demotesDiscriminatorFromRequiredVars`.
Landed (#104): the base class carries `DISCRIMINATOR_PROPERTY` and `DISCRIMINATOR_MAPPING`, and `ObjectSerializer.deserialize` routes a decode of the base (own static property, since subtypes inherit statics) to the subtype the value names; a missing or unlisted value throws `SerializationError` naming it. Not done: a property typed as the base inside another model is still decoded as the base by class-transformer (`@Type`), not routed.
Test: `test/composed-schema.test.mustache` (bhv unions 301-304, bhv serializationobject 301).

### Required plus nullable keeps the key (S5, D66, 2026-11-16)
Rule: decode rejects a missing key and keeps an explicit `null`; encode always writes the key, `null` when unset.
Why: it is the one reading of OpenAPI that distinguishes required from nullable.
Where: `models/model.mustache` (constructor and decode), `object_serializer.mustache` (serialize keeps the key).
Test: `test/object-serializer.test.mustache`.

Query side (D95): the Options field was already `T | null`; the guard no longer refuses `null` for a required nullable query parameter and the emission sends `''`. An omitted key and `undefined` are indistinguishable from `null` at run time (`== null`), so both send the key with an empty value.

### `uniqueItems` order is left unspecified (S6, 2026-11-16)
Rule: a `Set` is sent in its insertion order; the README says so. Do not sort. This supersedes the sorted-wire proposal D69.
Why: the owner decided that sets are sent in each language's natural collection order.
Where: `object_serializer.mustache` (serialize replacer), `readme.mustache`.
Test: `test/object-serializer.test.mustache`.

### Parameter order is fixed (S8, 2026-11-16)
Rule: required parameters, then server, then the Options object, then the per-call `{ signal }`, whether or not the operation has security. Adding security never moves an argument. Owner confirmation is still pending.
Why: adding `security` used to insert a positional parameter and break existing server-only call sites.
Where: `api/apis.mustache`, `BetterNodeCodegen.populateRequestWrapper`.
Test: `test/api/pet-api.test.mustache`.

### Dependencies: pure JS only (S10, 2026-11-16)
Rule: a small pure library or a version bump is acceptable when it standardises behaviour; never a dependency that needs a native binary.
Why: native binaries break the fetch-only runtimes and the container builds. `undici` is optional and pure JS.
Where: `package.mustache`.
Test: `NodeClientSpec` (`npm test`).

### Secrets are redacted in string forms (S15, 2026-11-16)
Rule: `Configuration` and `TransportOptions` string forms print the proxy password and any default header named Authorization, Proxy-Authorization, Cookie or containing key, token or secret as `***`.
Why: logs must not leak credentials.
Where: `configuration.mustache`, `transport_options.mustache`.
Test: `test/configuration.test.mustache`, `test/transport-options.test.mustache`.

### Timeouts, redirects and proxy validation (S16, D76, D77, D71, D73, D79, 2026-11-16)
Rule: one end-to-end timeout budget across redirect hops and the body read; a caller-supplied stream or file handle is never closed by the SDK; proxy URL validation happens in `build()`, not in the builder setter; `caCertPath` is validated even when `verifySsl` is false; a non-positive timeout throws `TypeError`; an unparseable `Location` behaves like a missing one and returns the 3xx.
Why: one budget stops a slow hop chain from outliving the caller's timeout; early validation reports caller mistakes at construction.
Where: `abstract_api_client.mustache` (resolve `new URL(location, currentUrl)` in try/catch before discarding the body), `transport_options.mustache`.
Test: `test/transport-options.test.mustache`, `test/default-api-client-unit.test.mustache`.

### Scope order, UUID case, blank keys, error cause (S14, D04, D19, D67, 2026-11-16)
Rule: OAuth scopes keep the spec's declared order; `UUID` is written lowercase and validated; an api key or bearer token is blank only when empty or ASCII whitespace (`/^[ \t\n\v\f\r]*$/`, never `trim()`, which also strips U+00A0 and U+FEFF), and a leading `Bearer ` is stripped once before the check; every status error accepts an optional cause.
Why: byte-identical wire across twelve SDKs; JS `trim()` is wider than the shared rule.
Where: `auth/*.mustache`, `BetterNodeCodegen.formatNodeScopes`, `models/model.mustache`, `value_serializer.mustache`, `errors/*.mustache`.
Test: `test/bearer-authenticator.test.mustache`, `test/api-key-authenticator.test.mustache`, `test/api-error.test.mustache`.

### Out of scope features (S11, #53, #70, #78, #88, #90)
Rule: callbacks and webhooks, XML bodies and nodeType, Link Objects and mutual TLS are not generated; the README says so.
Why: closed backlog issues.
Where: `readme.mustache`.
Test: golden README.

### Test runner speed and caches (S13, 2026-11-16)
Rule: the node spec caches npm under `/root/.cache/node/npm` (`npm_config_cache`), keeps `node_modules` project-local, and does not touch `.github` workflows.
Why: the CI test job is CPU-bound; workflow issues are filed in mridang/action-runner-common #47-#50.
Where: `NodeSpec.getCacheEnv`.
Test: the spec run itself.

### `format: number` is a branded Decimal that is lossy on decode (D83)
Rule: bare `type: number` is the branded `Decimal`. Encode keeps the text through `JSON.rawJSON` (an unquoted JSON number); decode currently uses a plain `JSON.parse`, so the text of an already rounded double is kept. The README carries the precision caveat. Do not change the type to a native `number`.
Why: this is documented; making decode lossless with a `JSON.parse` reviver using `context.source` is open, not decided as forced. Node also accepts any string for a `Decimal`.
Where: `object_serializer.mustache`, `models/model.mustache`, `readme.mustache`.
Test: `test/object-serializer.test.mustache`.

### Model names that collide get the `Model` prefix (D45)
Rule: node already prefixes colliding names (`ModelError`); the reserved set is `languageSpecificPrimitives`, and `getModelNameCollisionPrefix()` returns `Model`.
Why: the shared decision adopts node's convention in all twelve.
Where: `BetterNodeCodegen.getModelNameCollisionPrefix`, `languageSpecificPrimitives`.
Test: `NodeReservedWordsSpec`.

### Unions of primitives are aliases with no wrapper
Rule: `setPetAvatarThumbnailRequest` is `Array<Buffer> | Buffer`; there is no `actualInstance` accessor. Union classes for object variants carry `equals()` like plain models (D24), and variants are tried in declaration order via `ONE_OF_SCHEMAS`/`ANY_OF_SCHEMAS` (D64).
Why: TypeScript has no wrapper class for a primitive union.
Where: `models/model.mustache`, `BetterNodeCodegen.getTypeDeclaration`.
Test: `test/composed-schema.test.mustache`.

### Response headers on failure carry the real map (D13, D92)
Rule: an `ApiError` for a response that arrived but was unusable carries the lower-cased header record, never `null`; the default of `responseHeaders` is `null` in the error leaves. When fetch has already decoded a Content-Encoding, `Content-Encoding` and `Content-Length` are removed from the returned headers.
Why: `null` means "no HTTP response arrived", an empty map means "no headers".
Where: `abstract_api_client.mustache`, `errors/client-error.mustache` and the leaf errors.
Test: `test/api-error.test.mustache`.

### Message grammar and rendering (D15, D16, D17, D20, D29, D28)
Rule: `Failed to deserialize JSON:` and `Failed to serialize object to JSON:`; `JSON nesting depth <N> exceeds limit 1000`; `Refusing to replay request body across HTTPS->HTTP redirect: <from> -> <to>`; ``Too many redirects (exceeded ${n})``; `Response headers:` is compact JSON with sorted keys; lists render `[a, b, c]`; use-after-close is `ApiClient has been closed and can no longer send requests`; required Options parameters are named by the caller-facing identifier.
Why: one grammar across twelve SDKs so tests and users see identical text.
Where: `object_serializer.mustache`, `abstract_api_client.mustache`, `errors/api-error.mustache`, `server_configuration.mustache`, `api/apis.mustache`.
Test: `test/object-serializer.test.mustache`, `test/api-error.test.mustache`, `test/server-configuration.test.mustache`.

### Header injection is case-insensitive (D21, D22)
Rule: `User-Agent` and `X-Request-ID` are injected only when no case variant exists in the merged headers (an empty value counts as present); the default User-Agent token is the npm name.
Why: header names are case-insensitive (RFC 9110).
Where: `abstract_api_client.mustache`, `package.mustache`.
Test: `test/default-api-client-unit.test.mustache`.

### OAuth behaviour (D01, D02, D03, D05, D08, D12)
Rule: OIDC rediscovery rebuilds the delegate around the existing token manager (optional `tokenManager` constructor argument, `markExchanged` on the manager) and shares one in-flight discovery promise; the refresh grant carries client authentication and takes an optional `refreshUrl`; `exchangeCode` always POSTs the authorization-code grant and replaces the session; OIDC always requests `openid`; `expires_in` and discovery max-age are clamped to 31,536,000 seconds; the sync accessor on token flows throws and tells the caller to use the `...Async` method.
Why: a cache refresh must never discard a session; the shared RFC 6749 reading.
Where: `auth/oauth/*.mustache`.
Test: `test/oauth2-*.test.mustache`, `test/openid-connect-authenticator.test.mustache`.

### Decode strictness (D54, D57, D58, D62, D65, D68)
Rule: int32 values are range-checked beside `Number.isInteger` (int64 is integer-only, a forced exception since JS numbers lose precision beyond 2^53); date-time decode needs RFC 3339 with an offset (`parseDateTime`); base64 decode requires the standard alphabet, padding and no whitespace (`decodeBase64`); `format: uri` must be an absolute RFC 3986 URI; a null model element in an array or map is a `SerializationError`; a missing required field is rejected when the response is decoded (`__validate()`), never by the constructor.
Why: one accepted-input set across twelve SDKs.
Where: `models/model.mustache`, `object_serializer.mustache`.
Test: `test/object-serializer.test.mustache`.

### Binary, byte and non-JSON responses (D30, D31, D32, D35, D39)
Rule: a `format: binary` return is always raw bytes via a `binaryReturn` flag and never JSON-parsed; a `format: byte` return uses the strict `decodeBase64`; a non-JSON success type on a non-binary return is a `SerializationError` unless the return is a string and the type is text; a binary request body is sent verbatim (`rawBody`) under any selected content type; a plain object in a form body is a `SerializationError`.
Why: no silent re-encoding or fallthrough.
Where: `base_api.mustache`, `api/apis.mustache`.
Test: `test/base-api.test.mustache`.

### Wire encoding details (D36, D47, D48, D49, D51, D81, D82, D89, D90)
Rule: multipart scalars are text parts using `ObjectSerializer.stringify`, only plain objects are JSON parts; object-valued params go through `ValueSerializer.toWireMap` (batch 7); a path value of `.` or `..` throws `TypeError`; allowReserved keeps only the RFC 3986 query-legal set literal; `Buffer` values are base64 in parameters; `format: time` is `HH:mm:ss` plus `.SSS` only when milliseconds are non-zero; a `Set` parameter expands like an array; the multipart filename directive uses a byte-wise unreserved-only encoder.
Why: byte-identical wire.
Where: `abstract_api_client.mustache` (`buildContentDisposition`), `value_serializer.mustache`, `object_serializer.mustache`.
Test: `test/value-serializer.test.mustache`, `test/base-api.test.mustache`.

### Codegen escaping and names (D40, D41, D42, D43, D46, D60, D61)
Rule: string literals use the shared escaper; doc prose and manifest descriptions use triple braces with a `*/` guard and `lambda.jsonString` in `package.mustache`; one const object plus literal union per server variable; a `$ref` enum default becomes the typed member (`Availability.OnHold`); a branded decimal default is `'1.5' as Decimal`.
Why: a literal must evaluate to exactly the spec's bytes.
Where: `BetterNodeCodegen.toDefaultValue`, `toEnumVarName`, `api/apis.mustache`, `servers.mustache`, `package.mustache`.
Test: `NodeReservedWordsSpec`, golden diff.

### Doc and README metadata (round-8 sweep)
Rule: README `Name` uses `{{npmName}}`; model doc comments render descriptions through `{{#lambda.unescapeDocComment}}`; deprecated operations carry a doc-comment `@deprecated`, and deprecated fields carry it too; `format: byte` map values are `@Transform`ed from base64.
Why: the other variables render blank or escaped text.
Where: `readme.mustache`, `models/model.mustache`, `api/apis.mustache`.
Test: golden.

### FORCED: unknown Content-Encoding passes through (D-FORCED-1)
Rule: the fetch layer owns Content-Encoding, so node does not raise on an unrecognised coding; an empty body passes through for any declared coding via the gzip magic-byte gate.
Why: the client cannot tell an unknown but already-decoded body from a still-encoded one. Do not make node raise.
Where: `abstract_api_client.mustache`.
Test: `test/default-api-client-unit.test.mustache`.

### FORCED: fetch re-escapes `'` in allowReserved (D78)
Rule: the encoder keeps `'` literal but WHATWG URL parsing in fetch rewrites it to `%27`. Documented, not a defect.
Why: it is how fetch accepts a URL.
Where: `value_serializer.mustache`.
Test: `test/value-serializer.test.mustache`.

### Required fields are checked at decode, not at construction (D68)
Rule: `new Pet()` and `new Pet({})` succeed: the constructor assigns the data and applies the declared defaults and checks nothing (superseding the earlier required-check gated on `data !== undefined`). Required fields are enforced by `__validate()`, which `ObjectSerializer.deserialize` runs on the model it built.
Why: class-transformer builds every model with a no-argument constructor, and F316 says validation belongs at the wire boundary.
Where: `object_serializer.mustache`, `models/model.mustache`.
Test: `test/object-serializer.test.mustache`.

### Free-form null retention, OAuth2 repr, per-model union text (root AGENTS.md closed items)
Rule: node's serializer drops `null` entries from a free-form map (java and kotlin keep them); the OAuth2 auth-code repr shows only host, clientId and clientSecret; `ApiKeyAuthenticator` repr includes `location`; union no-match uses the central `No oneOf/anyOf variant matched the JSON`. Do not flag them.
Why: fixture-only, needs per-field metadata the schema lacks (for the free-form item); the rest are documented and unharmonised.
Where: `object_serializer.mustache`, `auth/oauth/*.mustache`.
Test: none beyond the golden.

### Mock seam (mocking001 to 009, issue #122 and the Invoker line of #131)
Rule: every API group has an interface and the client has one where the language can state it, additive next to the existing classes, using only SDK and standard-library types. `<Group>ApiInterface` in each group file and `ClientInterface` in `client.ts`, both exported from the entry point. The classes have protected members, so TypeScript treats them nominally and an object literal cannot stand in for them; the interface is what a fake satisfies. Tests: test/client.test.mustache (001, 002, 007, 008), test/api/pet-api.test.mustache (003 to 006). Where: the templates named above, `BetterNodeCodegen`, the README template (`Testing your code`), `docs/languages/node.md`.

## Known limitations and out of scope

Callbacks, webhooks, XML bodies, Link Objects and mutual TLS are out of scope (#53, #70, #78, #88, #90). OpenAPI 3.1 and 3.2 are tracked as epics #96 and #49; the pinned swagger-parser rejects 3.2 documents (#50). The fetch-only transport cannot do proxies, a custom CA or disabled TLS verification and throws `TypeError` at construction. `Decimal` decode is lossy until a reviver using `context.source` lands. int64 values beyond 2^53 are not preserved. The F469 and F470 container leaf and bare primitive checks cannot be locked until the petstore has those operations (spec change needed). The F446 raw operation-server description interpolated into a block comment is open. F779 (the petstore needs a model with `additionalProperties: {type: string}`) is open and needs a shared spec change; until then the index signature and the `additionalProperties` accessor are `unknown`-typed (: the typed branches were dead code and are gone, so the accessor can no longer conflict with the index signature). F278 is closed (`quoteSingle` escapes the scheme name, pinned by `BetterNodeCodegenTest`). F749 is closed (the base-api test warms the connection up in a `beforeAll`). F848 (error bodies decoded against a declared error schema) and F852 (`prefixItems` tuples, under epic #96) are open. F191: typedoc is warnings-fatal in the Makefile but no spec runs it. Integer-enum casing is parked, and the missing discriminator default, and `format: time` parameters are parked as shared steps. The sorted-`Set` proposal (D69) and F090 environment proxy handling are not applied to node: node ignores ambient proxy variables because it only uses `TransportOptions.proxy`.

## Build-breaking rules

- Prettier check (`npx prettier --check .`) must pass on generated sources and tests; fix the template, never format by hand.
- ESLint recommended rules (`typescript-eslint` recommended) apply to every file in `src/` and `test/`; no file may disable a whole rule set or be excluded, and only `eslint.config.js` itself is CommonJS.
- `tsc --noEmit` runs with `strict`, then again with `tsconfig.jest.json` and `type=module`; a relative import without its file extension fails (`{{importFileExtension}}`).
- `emitDecoratorMetadata` stays false; do not add eager `design:type` references.
- Reserved words are pinned by `NodeReservedWordsSpec` against `reserved-words/node.txt`.
- Do not use vendor extensions in templates; build files with Mustache, not string concatenation.
- Every generated unit test must be added to an existing test template, never a new file.
- The formatter runs in Docker (`node:24`), so local regeneration can drift; commit all twelve goldens current.

## Verify

```
etc/ci-local.sh check Node
etc/ci-local.sh mvn -B test
```

Language-specific: `mvn verify -Dgroups=node`; inside a generated SDK `npm test`, `npx tsc --noEmit`, `npx eslint .`, `npx prettier --check .` and `npx typedoc --treatWarningsAsErrors`.

### Client lifecycle, 429 and redirects (2026-11-16)
Rule: the `Client` facade has `close()` and `[Symbol.asyncDispose]()`, which close only the `DefaultApiClient` the SDK built (a caller-supplied `fetch` is never called or closed; later requests fail with the closed-client error). HTTP 429 maps to `TooManyRequestsError`, a `ClientError`. Redirects follow RFC 9110 15.4: on 301/302 only POST becomes GET, on 303 everything but HEAD becomes GET, 307/308 keep method and body; a body kept on a non-GET/HEAD method is refused across an https to http downgrade.
Why: byte-identical behaviour across the twelve SDKs.
Where: `client.mustache`, `errors/too-many-requests-error.mustache`, `abstract_api_client.mustache`.
Test: `test/client.test.mustache`, `test/api-error.test.mustache`, `test/base-api.test.mustache`, `test/default-api-client-unit.test.mustache`. F833 (content cookie bytes) and F832 (operation name in the content-type error) already conformed; `test/value-serializer.test.mustache` pins the cookie bytes.

## Decisions taken in batch 6

Each uses the audit's own proposal as the default; none was re-opened with the owner.

### Request bodies keep their required fields
Rule: `DeepInput<T>` no longer makes every property optional; it keeps the model's own optionality and only relaxes class-ness (method members dropped, nested models plain). `addPet({})` is a compile error; a constructed `Pet` still type-checks. A required field that has a default stays required in the type.
Where: `deep_input.mustache`. Test: `test/base-api.test.mustache` (`@ts-expect-error` on a body without `photoUrls`).

### Binary uploads accept a `Blob`
Rule: file and binary parameters of an Options class are typed `Buffer | Blob` (`Array<Buffer | Blob>`); a `File` is a `Blob`. Response bodies stay `Buffer`. Request-body parameters are unchanged.
Where: `BetterNodeCodegen.uploadType`, `api/apis.mustache` (`requestBody` union), `skills.mustache`. Test: `BetterNodeCodegenTest`.

### Constraints are documented, not enforced (S3 stands)
Rule: `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems` and `maxItems` render as doc-comment tags on a model property.
Where: `models/model.mustache`. Test: `test/metadata.test.mustache`.

### Bundling
Rule: `package.json` declares `sideEffects: ["./dist/src/object-serializer.js"]` (the only module with an import-time side effect, `reflect-metadata`); the README tells size-sensitive callers to import one API class. Standalone operation functions would change the API surface and are not done.
Where: `package.mustache`, `readme.mustache`. Test: `test/metadata.test.mustache`.

### Documentation
Rule: the README opens with a Quick start (install by name, construct, one call, catch `ApiError`) and links SKILLS.md; its "Not supported" part lists XML bodies, Link Objects and mutual TLS; the cancellation sample uses a real first operation with the per-call `{ signal }` and no `Configuration` route; SKILLS Testing shows a stub `fetch` through `TransportOptions`.
Where: `readme.mustache`, `skills.mustache`. Test: `test/client.test.mustache` (`generated documentation`).

### Wire and error details
- F913: a bare `charset=utf-16` honours a byte-order mark and defaults to big-endian only without one (`decodeBody`).
- F914: `deserializeMap` defines each key with `Object.defineProperty`, and model constructors skip an own `__proto__` key, so the wire key cannot re-prototype the result.
- F915: a `Uint8Array`, other typed array or `ArrayBuffer` body is sent as raw bytes, never as multipart.
- F916, F917, F921: the redirect refusals carry the real lower-cased response headers; the too-many-redirects error is raised only when the last 3xx still has a `Location`; the messages follow D16 (`Refusing to replay request body across HTTPS->HTTP redirect: <from> -> <to>`, `Too many redirects (exceeded <N>)`).
- F861: the two redirect messages mask every query value as `***` (`AbstractApiClient.redactUrl`).
- F920: header merging is case-insensitive in the transport and in `BaseApi`; a later layer replaces an earlier one of any case, and the body-less `Content-Type` removal ignores case.
- F860: `SendRequestOptions.noDefaultHeaders`; the OAuth2 token request and OIDC discovery set it, so `TransportOptions.defaultHeaders` never reach the identity provider.
- F854, F910: one expiry margin, applied when the cached token is read, `min(60 s, lifetime / 2)`; the stored expiry is the real one. A token with `expires_in` of 90 s or less is reused.
- F911: a token response without `expires_in` clears the stored expiry (no `expires_in` means no expiry).
- F863: a `token_type` other than `bearer` (any case) is an `OAuth2TokenError` with the fixed message `Unsupported token_type in token response`; an absent one is accepted.
- F924: a non-positive or NaN timeout throws `TypeError` `Invalid timeout: must be a positive number of milliseconds (got N)` (S16).
- F925: use-after-close says `ApiClient has been closed and can no longer send requests` (D29).
- F927, F931, F933: the redirect refusal messages, the empty-code message and the depth and parse messages are pinned by exact-message tests; `parseJson` says `Failed to deserialize JSON:` (D15).
- F290 already conformed (the sync accessor throws and names `...Async`).
Where: `abstract_api_client.mustache`, `base_api.mustache`, `object_serializer.mustache`, `models/model.mustache`, `transport_options.mustache`, `auth/oauth/oauth2_token_manager.mustache`, `auth/oauth/openid_connect_authenticator.mustache`, `api_client.mustache`.
Test: `test/default-api-client-unit.test.mustache`, `test/base-api.test.mustache`, `test/object-serializer.test.mustache`, `test/oauth2-token-manager.test.mustache`, `test/transport-options.test.mustache`, `test/oauth2-auth-code-authenticator.test.mustache`.

## Decisions taken in batch 7

Each uses the audit's own proposal as the default; none was re-opened with the owner.

### Object-valued parameters expand per style (D47)
Rule: an object-valued query, header, path or cookie parameter (a model or a free-form map) is expanded from `ValueSerializer.toWireMap`, which writes wire names in declaration order, drops nulls and rejects a nested object or array leaf with a `SerializationError`. `form` with `explode: true` becomes one query entry per property (`ExplodedObject`, expanded by `BaseApi`), `form` with `explode: false` and `simple` join `k,v,k,v`, `simple` with `explode: true` joins `k=v,k=v`, matrix, label, `spaceDelimited` and `pipeDelimited` follow RFC 6570. `deepObject` uses the same wire map.
Where: `value_serializer.mustache`, `base_api.mustache`. Test: `test/value-serializer.test.mustache`, `test/api/pet-api.test.mustache` (`getPetTag` criteria).

### Wire and auth details
- F1138 (D08): the OAuth2 `expires_in` and the OIDC discovery `max-age` are clamped to 31,536,000 seconds (`oauth2_token_manager`, `openid_connect_authenticator`). Test: `test/oauth2-token-manager.test.mustache`, `test/openid-connect-authenticator.test.mustache`.
- F1159 (D05): OIDC always requests `openid`, pinned by a table of caller scope lists.
- F856: `BaseApi` hands its own `ApiClient` to a per-call `HttpAwareAuthenticator` before it asks for headers. Test: `test/base-api.test.mustache`.
- F1059: any `multipart/*` request type is sent as a multipart envelope with the declared subtype (`multipart/mixed`, `multipart/related`) and the SDK boundary (`abstract_api_client.mustache`, `base_api.serializeBody`).
- F1086 (D79): proxy URL errors print the userinfo as `***@`, and the unparseable-URL error no longer carries the parser error as `cause` (it holds the raw input). Test: `test/transport-options.test.mustache`.
- F1093: headers the SDK attached from an authenticator or from default headers (tracked in `SendRequestOptions.credentialHeaderNames`, plus `TransportOptions.defaultHeaders`) are removed on a cross-origin redirect hop beside the fixed list. Test: `test/default-api-client-unit.test.mustache`.
- F1080: the rendered `ApiError.toString()` prints the body capped at 1024 characters with a `... (N more bytes)` suffix, escapes CR, LF and other control characters, and prints credential-bearing response headers (Authorization, Proxy-Authorization, Cookie, Set-Cookie, WWW-Authenticate, Proxy-Authenticate, and names containing key, token or secret) as `***`, keys sorted. `responseBody` and `responseHeaders` stay whole. Test: `test/api-error.test.mustache`.
- F1106: Temporal values are recognised by `Symbol.toStringTag` (`isTemporalValue`), so a native `Temporal` or a second polyfill copy is accepted.

### Names and models
- F173, F1131 (D45): a schema named `Error` or `Result` is generated as `ModelError` or `ModelResult` (`BetterNodeCodegen.toModelName`, not `languageSpecificPrimitives`: a primitive is never rewritten where a property refers to it); the global `Error` is never shadowed. Test: `BetterNodeCodegenTest`, `test/object-serializer.test.mustache`.
- A property whose name differs from its wire name (`n2fa` for `2fa`) is written under the wire name: the model declares `__wireNames` and `ObjectSerializer.serialize` re-keys the instance, looking the field registries up by property name. Test: `test/object-serializer.test.mustache` (`keep their wire keys`).
- F1055: a parameter named `options`, `requestOptions` or `server` becomes `optionsParam`, `requestOptionsParam`, `serverParam` (`BetterNodeCodegen.toParamName`). Test: `BetterNodeCodegenTest`.
- F867: a model with a `format: password` field gets a `util.inspect` hook that prints the field as `***`; the field and the wire value are real. Test: `test/object-serializer.test.mustache`.
- F1156: the file header prints the info description through `lambda.unescapeDocComment`. Test: `test/metadata.test.mustache`.

### Documentation
F1084: README and SKILLS name the root `<Prefix>Error`, the full tree (`ApiError`, `SerializationError`, `OAuth2ServerError`, `OAuth2TokenError`) and end the example with a root-error arm. Test: `test/client.test.mustache` (`generated documentation`).

### Tests added to close parity gaps
F1063, F1166 (`default-api-client-unit`), F1075 (`oauth2-token-manager`), F1114, F1116, F1117, F1144, F1146, F1148, F1149, F1067 (`object-serializer`), F1128, F1141, F1161 (`store-api`, the typed `/test/status/<code>/store/order/<id>` routes), F1113, F1127, F1158, F1142, F1125, F1124 (hostile third global server, in `pet-api` because the spec-independent `server-configuration` test cannot name `Servers`) (`pet-api`), F1133, F1161 (`/test/response-headers`) (`default-api-client`), F1151 (`default-api-client-unit`: a `Blob` handle stays readable), F1124, F1175 (`object-serializer`: `Separator`, `quantity: 500`, the `location` tuple), F1121, F1169 (`base-api`; the cookie test now asserts, a per-call `Accept` wins over the default and negotiated one).
F1150 is not tested: node never builds a `Proxy-Authorization` header, undici's `ProxyAgent` applies the proxy credentials itself, so there is nothing a redirect strip could remove.


## Decisions taken in batch 9

Each uses the owner's batch 9 defaults; none was re-opened.

### A 401 renews the OAuth2 token and replays once
Rule: `invalidateAccessToken()` is public on the OAuth2 client-credentials, password, authorization-code and OpenID Connect authenticators (it drops the access token and keeps the refresh token, through `OAuth2TokenManager.invalidateAccessToken`). When an authenticator that has it is the effective one and the call answers 401, `BaseApi` invalidates, asks `getAuthHeadersAsync()` again (a refresh grant first) and sends the request once more with the fresh headers; a second 401 is the usual `UnauthorizedError`. A multipart body (a plain object) is not replayed, a failed re-authentication (any `<Prefix>Error`) leaves the 401, and API key, basic, bearer and implicit authenticators have no such method and never replay. node has no stream body type, so every other body is replayable.
Where: `base_api.mustache`, `auth/oauth/*_authenticator.mustache`. Test: `test/base-api.test.mustache` (`a 401 on an OAuth2 call`), the four `test/oauth2-*`/`openid-connect` authenticator tests.

### PKCE S256 on the authorization code flow
Rule: `buildAuthorizationUrl()` makes a 43-character base64url verifier (32 random bytes), keeps it and adds `code_challenge` and `code_challenge_method=S256` (last in the query); `exchangeCode()` sends `code_verifier` and then forgets it. Building the URL again starts a new verifier. The verifier lives in a `PkceState` object the OpenID Connect authenticator shares with every delegate, so rediscovery keeps it; no nonce, since no ID token is validated. SHA-256 is hand-written in `oauth2_auth_code_authenticator.mustache` (the URL builder is synchronous and the portable client runs without `node:crypto`).
Where: `auth/oauth/oauth2_auth_code_authenticator.mustache`, `openid_connect_authenticator.mustache`. Test: `test/oauth2-auth-code-authenticator.test.mustache` (`PKCE`), `test/openid-connect-authenticator.test.mustache`, `test/client.test.mustache` (the exact authorize URL minus the challenge).

### Automatic retries
Rule: `TransportOptions.maxRetries` (builder `maxRetries(n)`, default 2, 0 disables, a non-negative integer or `TypeError`). `AbstractApiClient.sendRequest` loops over `sendOnce`: it retries after a `NetworkError` that happened before a response (not a `NetworkTimeoutError`, not a failure while reading a body that arrived) and after 408, 409, 429 and 5xx except 501. Backoff is 0.5 s doubling to 8 s with up to 25% taken off; `Retry-After` (seconds or HTTP date, capped at 60 s) wins. One `AbortSignal.timeout` made before the first attempt covers every attempt, a wait that would reach the deadline ends the retries and returns the last result, and the caller's signal stops the wait with its own reason. Each attempt rebuilds the headers (new `X-Request-ID`, new multipart boundary) and `SendRequestOptions.refreshAuthHeaders` (set by `BaseApi`) asks the authenticator for its headers again before every retry. The transport owns the retry, so an injected `ApiClient` stub and a token request through the transport behave the same. Tests that expect one failing request use `maxRetries(0)`.
Where: `abstract_api_client.mustache`, `transport_options.mustache`, `api_client.mustache`, `base_api.mustache`, `readme.mustache`, `skills.mustache`. Test: `test/default-api-client-unit.test.mustache` (`automatic retries`), `test/transport-options.test.mustache`.

### Already conforming
F1080 (the rendered `ApiError.toString()` cap, escapes and header masks; control characters are `\n`, `\r`, `\t` and `\uNNNN`, the JS idiom). F825 is rust only.

## Decisions taken in batch 10

Each uses the audit's own proposal as the default; none was re-opened with the owner.

### Enums are a const object plus a literal union
Rule: every generated enum (model, inline model field, operation parameter, server variable) is `export const X = { Member: 'wire' } as const;` with `export type X = (typeof X)[keyof typeof X];`. Member access is unchanged (`Availability.OnHold`) and a plain literal (`'on-hold'`, `2`) is assignable. Numeric enums lose the TypeScript reverse mapping, so the membership checks read `Object.values` directly with no reverse-mapping filter.
Where: `models/model.mustache`, `api/apis.mustache`. Test: `test/object-serializer.test.mustache` (`a model enum accepts its literal wire values`).

### Closing aborts running requests, superseded
Superseded on 2026-11-17 by "Closing is graceful and idempotent" below: `close()` no longer destroys the dispatcher under a running request.

### Path values are never replacement patterns
Rule: the path parameter token is replaced with a function, so `$&`, `$$`, `$'` and `` $` `` in a value reach the URL as written. Test: `test/api/pet-api.test.mustache` (`getPetByName puts the value ... in the path as written`).

### OIDC discovery is checked
Rule: the discovery URL must be https (or an http loopback host: `localhost`, `127.0.0.1`, `[::1]`), else `TypeError('OIDC discovery URL must use https')` before any request; a document `issuer` that differs from the discovery URL minus `/.well-known/openid-configuration` (trailing slash ignored) is a `SerializationError`; `authorization_endpoint` and `token_endpoint` must be https or loopback (`OIDC discovery document field must be an https URL: <field>`). The base OAuth2 constructors do not check their URLs yet.
Where: `auth/oauth/openid_connect_authenticator.mustache`. Test: `test/openid-connect-authenticator.test.mustache`.

### Error and message details
- F831: `TooManyRequestsError.retryAfter` is the `Retry-After` header as seconds (the header's seconds, or the time left to its HTTP date, never negative); `null` when absent or unparseable. Test: `test/api-error.test.mustache`.
- F1104: the model decode failure says `Failed to deserialize JSON: <detail>` (it said `Failed to deserialize object`), the D15 stem. A serialize failure keeps its cause. Test: `test/object-serializer.test.mustache`, `test/composed-schema.test.mustache`.
- F1103: the CA read failure appends the cause (`failed to read CA certificate from "<path>": <cause>`); the parse message is unchanged. Test: `test/default-api-client-unit.test.mustache` (missing file, directory, empty file, garbage).
- F1160: `ApiResult.headers` is a frozen copy of the transport map (D27). Test: `test/base-api.test.mustache`.
- F1071: the doc comment names the parameter (`@example petId: Small breed ID - 1`, `@example options.<name>: ...` for options). Test: `NodeFormattingSpec.pluralExamplesShouldBeAttributedToParameter`.

### Tooling and documentation
- F1088: `make lint` also runs `prettier --check .`. F1077: `make audit` runs `npm audit --omit=dev`; the README has a "Dependency audit" section.
- F884, F889, F890: the README has Raw responses, Timeouts, Automatic retries, Expired tokens, Request id, Cancellation and Custom HTTP client under Quick start (before Develop), and Schema constraints under Caveats; SKILLS has Custom Transport Options, Retries, Custom HTTP client, Cancellation and Raw responses, timeouts and the request id under Configuration. Test: `test/client.test.mustache` (`generated documentation`).

### Tests added to close parity gaps
F926 (the three token-response messages as a table), F1065 (IPv6 proxy), F1074 (`bearerxyz` kept whole, doubled prefix stripped once), F1167 (default user agent is `<name>/<version> (node)` read from `package.json`), F1170 (null body sends no body and no `Content-Type`, GET and POST), F1112 (BinaryVault equality, bare, list and map), F1124 (`nickname` `ZZZZZZZZZZ`, `slug` `12/ab`), F1135 (identity `Content-Encoding`), F1141 (`getDefaults`), F1154 (`BetterNodeCodegenTest`: server before the options object, the `| undefined` form, no server).

## Decisions taken in wave 2

Owner decisions of 2026-11-17, realised for node. Every item of the node work list is listed once: implemented, already satisfied, forced by the language, or needing a shared file.

### Implemented

- **F830, error request context.** `ApiError` and every subclass (the status errors, `NetworkError`, `NetworkTimeoutError`) carry `requestMethod` and `requestPath`, both `string | null`. They arrive through the constructor's `options` object (`ApiErrorOptions`: `cause`, `requestMethod`, `requestPath`); `BaseApi` passes the method and the path of the request URL without its query string (the base path of the server stays, the host goes) to `ApiError.fromResponse`. `message` is unchanged (`API returned status code N`); `toString()` renders `Request: <METHOD> <path>` on its own line after `HTTP status code: N` and before the headers, control characters escaped like the body, and prints nothing when either half is unknown, so every older format test holds. A transport failure has no request context (the transport does not know the operation path). Tests: `test/api-error.test.mustache`, `test/base-api.test.mustache` (`BaseApi error request context`).
- **F827, per-call options.** `RequestOptions` gains `timeout` (positive milliseconds, or `null` for no deadline), `maxRetries` (non-negative integer) and `headers`. `BaseApi` validates them before anything is sent (`TypeError`, the messages of `TransportOptions`), passes `timeout` and `maxRetries` to the transport in `SendRequestOptions`, where they replace the transport's values for that call, and overlays `headers` after the client defaults and the operation headers (case-insensitive) but before the authentication headers, so a per-call header never replaces a credential; their names join `credentialHeaderNames` so a cross-origin redirect drops them. Tests: `test/base-api.test.mustache` (`BaseApi per-call request options`), `test/default-api-client-unit.test.mustache` (per-call timeout, retries).
- **F868, immutable models.** Every model field is `readonly` and every model has `with(patch)`, which builds the copy through the constructor (which validates nothing) and keeps the free-form keys and the `additionalProperties` wire key of a model that declares them. There is no runtime freeze: a subclass constructor assigns after `super()`, and class-transformer assigns fields. Tests: `test/object-serializer.test.mustache` (`model with (copy)`).
- **F862, credentials.** `BearerAuthenticator`, `ApiKeyAuthenticator`, `BasicAuthenticator` and the per-scheme authenticators built on them take the credential as an argument, else read an environment variable once at construction (empty string is unset; a runtime without `process` is unset), else throw a `TypeError` naming the variable. The variable names are the shared ones: `EnvVarNames` computes `{PREFIX}_{FIELD}` (qualified with the scheme id when two schemes share a field name) and hands them to the per-scheme template as `envVars`; the prefix is the shared `envVarPrefix` option, else the API title without a trailing SDK. The node-only `envPrefix` option is gone. The base classes default to `{PREFIX}_TOKEN`, `{PREFIX}_API_KEY`, `{PREFIX}_USERNAME` and `{PREFIX}_PASSWORD`; a per-scheme class resolves its own variable before `super()` (`resolveCredential(token, 'ACME_PARTNER_TOKEN_TOKEN', ...)`), so the base constructors take no environment argument. `BearerAuthenticator` and `Client.withToken` also take a `CredentialProvider` (`() => string | Promise<string>`), which counts as an explicit credential and is called for every request; `getAuthHeaders()` refuses an asynchronous provider, `getAuthHeadersAsync()` (the one `BaseApi` uses) takes either. The failure is at authenticator construction, which precedes the client, so the client is never built with a missing credential. API key and basic take no provider (the key location and `getQueryParams`/`getCookieParams` are synchronous); the OAuth2 and OpenID Connect per-scheme authenticators read their credentials (client id, client secret, username, password, redirect URI) from the scheme's environment variables when they are not passed, through `resolveOAuthCredential`, which also refuses a value with CR, LF or NUL; the explicit argument always wins, and the base OAuth2 and OpenID Connect classes stay explicit-only, so the token URL and scopes are never read from the environment. Tests: `test/credentials.test.mustache`, the three authenticator tests and `test/scheme-authenticators.test.mustache` (precedence, empty and missing for every generated scheme class), `BetterNodeCodegenTest`.
- **F866, `$ref` with a sibling default.** A property that is a `$ref` to an enum with a `default` beside it is initialised from `vendorExtensions.siblingDefault*` (shared `captureRefSiblingDefault`): the field initialiser and the constructor's absent-key fallback use `<Enum>.<Member>`, an explicit value or `null` is kept. Tests: `test/object-serializer.test.mustache` (`a $ref property with a sibling default`, `StockItem.availability`), `BetterNodeCodegenTest`.
- **F827, every operation takes per-call settings (adopted without minting).** The shared `mintsOptionsForEveryOperation()` stays `false` for node. The per-call `timeout`, `maxRetries` and `headers` live on the trailing `RequestOptions` argument that every operation already takes, so an operation with no parameters and no security needs no Options type to carry them; minting an empty one would only add a second place to put the same settings. Test: `test/api/pet-api.test.mustache` (an operation without an Options object takes the per-call settings), `test/base-api.test.mustache`.
- **F1082, `format: date`.** A date is a plain `YYYY-MM-DD` string in models, options and parameters (`typeMapping` `date` is `string`). `plainDate` (exported from `object-serializer`, imported by a model that has a date field) validates a real calendar day and reads a `Date` as its UTC date; the decode check of the model, `ValueSerializer.stringifyDate` and the `plainDate` leaf of `deserializeLeaf` use it, a date-time string is refused. Parameters (and arrays of them) are typed `string | Date` in the operation signatures and the options objects (`dateInputType`); model fields are `string`. `format: date-time` stays `Date`. Tests: `test/object-serializer.test.mustache` (`format: date`), `test/value-serializer.test.mustache`, `BetterNodeCodegenTest` (a spec with a date model, because the petstore fixture has no `format: date` model field).
- **F894, package metadata.** `BetterNodeCodegen` takes `repositoryUrl`, `homepageUrl`, `authors`, `license` (the SPDX id; `licenseId` wins when both are set), `documentationUrl` and `issueTrackerUrl`. `package.json` gets `author`, `homepage`, `repository` and `bugs` (JSON-escaped through the `*Json` properties), the README `Package` section lists them. The options are declared in the node generator class because the shared plumbing is in `AbstractBetterCodegen`; see the shared needs below.
- **F308, integer enum members.** `Priority.Number1` instead of `NUMBER_1`, the casing of the string members (`BetterNodeCodegen.toEnumVarName`). Tests: `BetterNodeCodegenTest`, the object-serializer and store-api tests.
- **F390, non-finite doubles.** A `format: double` or `float` field rejects a value that is not finite (`1e400` decodes to `Infinity` in `JSON.parse`): the decode check of the model throws, and the new `double` leaf of `deserializeLeaf` does the same for array and map items. A `type: number` without a format is an arbitrary-precision `Decimal` and keeps its text. Tests: `test/object-serializer.test.mustache` (`format: double decode`, the canonical #4 block).
- **F834, F835, F836, F837, F828, F889.** README sections: optional versus nullable, unknown enum and discriminator values, repeated response headers joined with `", "`, no streaming (inject your own transport or send that one request yourself), `Configuration` and `TransportOptions` stay separate, plus response compression, logging and versioning. SKILLS.md has the per-call options, credentials and model copy.

### Already satisfied or forced

- **F1062** (several 2xx responses): `apis.mustache` already decodes by the actual status code (`hasMultiple2xx`); `test/api/store-api.test.mustache` covers the 200 and 201 bodies.
- **F878** (typed variant constructors and accessors): the accessors are implemented (2026-11-17): a discriminated `oneOf` wrapper and a non-discriminated `anyOf` composite have `isX()` and `asX()` (the variant, or `null`) for each model variant, beside `getActualInstance()` and `getMatchedInstances()`. Test: `test/composed-schema.test.mustache`. The per-variant constructors stay forced: the wrapper constructor takes the typed union, so the type system narrows and there is no constructor-per-variant to add.
- **F1087** (runtime floor): `engines.node` is `>=22`, the oldest Node.js release that is not end-of-life.
- **F1160** (`ApiResult.headers` is a defensive frozen copy): `test/base-api.test.mustache` (`the headers are a frozen copy of the response map`) mutates the transport map afterwards and the frozen result; the work list entry looked in `api-result.test.mustache`, which tests only the type.
- **F1151** (a caller-supplied upload is never closed): forced. An upload is a `Buffer`, `Uint8Array` or `Blob`, never a stream or a file handle; `test/default-api-client-unit.test.mustache` (`a Blob sent as a multipart part or as the whole body is still readable afterwards`) proves the SDK leaves the caller's value intact.
- **F1182** (`ExplodedObject` naming): node already exposes `entries`, the canonical name; dart renames `pairs` in its own worktree.
- **F879, F877, F1178, F878 (go)**: go and java only.

### Needing a shared file

- F894 for the other eleven languages: the six options should move into `AbstractBetterCodegen` next to `licenseId`; node declares them itself until then.
- A `format: date` model field in the petstore or probe spec, so the date model path is generated and type-checked in all twelve goldens (node tests it through an inline spec in `BetterNodeCodegenTest`).

## Gate lessons (what broke the gate and how to avoid it)

- Generated code is verified only by the Docker specs. Run `etc/ci-local.sh check Node` before every push, and run the whole set (Client, Build, Linting, TypeCheck, Formatting), never only `NodeClientSpec`: that alone hid import bugs that CI later found.
- Read the generated file, not the log. A "prettier exit 2" or a `.prettierrc` failure was a syntax error in one generated file. Cheapest check: `node --check <file>` or `npx tsc --noEmit` inside the generated SDK.
- Strings with apostrophes: never emit `'Missing the required parameter 'pet' ...'` (6 sites). Use a double-quoted literal or `quoteSingle`; `node --check` catches it.
- tsc strict: stubs and fixtures need real return types (no implicit `any`, `TS7006` on test callbacks such as `trace-context-util.test.ts`). Give every callback parameter an explicit type. Check with `npx tsc --noEmit --strict` on `src/` and `test/`.
- tsc strict: `Set` versus array mismatches (`uniqueItems`) and `null` versus `string | undefined` fail; a literal union (`ParameterLocation`) rejects new members like `"cookie"`; `Buffer[]` needs a cast; branded types (`SetPetAvatarThumbnailRequest`) reject plain `string` fixtures. Prefer const objects with a derived union over `enum`, and keep the union and the object in sync.
- Relative imports need the `{{importFileExtension}}` suffix. Paths built in Java (`BetterNodeCodegen`) must follow the same naming as the templates: after any file rename or kebab/snake change, run `tsc --noEmit` and grep the `*.java` path builders, because the template sweep never sees them. Clean stale generated dirs (`git clean -n`) after renaming, or tsc reports thousands of bogus errors.
- Renamed schemas: `Error` becomes `ModelError`, `Result` becomes `ModelResult`. Every `$ref` property type, import, `getSchemaType` result and `@Type(() => X)` must use the renamed name, or tsc, eslint (unused import) and jest all fail. Fix it in the codegen, not per template. Referenced enums report `isEnum == false`, so `{{^isEnum}}` must not emit `@Type(() => Enum)` (circular-import crash).
- Wire names: serialize with the wire name (`baseName`), not the TS property name, and assert it in `NodeClientSpec` against the mock server. A changed contract updates its test in the same commit.
- ESLint (`NodeLintingSpec`): `no-empty` rejects comment-only `catch` blocks; chain `{ cause: e }` instead of swallowing. Remove unused or duplicate imports after merges. The lint job must run `eslint`, not only `prettier --write`. Regexes with control characters use character classes (`/[\r\n\x00]/`).
- Prettier: run `npx prettier --check .` on the generated output; fix the template, never the golden. A killed regeneration leaves half-formatted goldens: `git checkout -- <goldens>` and rerun cleanly.
- No `//` comments in templates: `NodeFormattingSpec` rejects `^\s*//($|[^/])`; use block comments. Check: `grep -rnE '^\s*//([^/]|$)' src/main/resources/templates/node`. A Mustache `{{! }}` comment ends at the first `}}`, so never put `{{x}}` inside one.
- Jest cross-realm errors: `instanceof Error` and `toThrow(SomeError)` fail when the error comes from another realm (undici, the ESM and CJS copies of a package). Assert on `name`, `code` and `message`, and load libraries (OpenTelemetry) the same way the SDK does. Do not assert an absent User-Agent header; undici injects one.
- When a test fails, check the test first (time margins, fake headers, `.000` formats) before changing the generator. Cover a fix in `BetterNodeCodegenTest` plus the existing node test templates, never a new file.
- Docker hygiene: check `docker ps -q | wc -l` before each run and clear leaked containers above about 20; run at most three languages at once.

## Decisions of 2026-11-17, round 4

### One optional credential per security scheme
Rule: the client takes one optional credential per distinct security scheme, named after the scheme, as `ClientCredentials` (`{ authenticator?, <schemeParam>?: Authenticator }`) in place of the single authenticator: `new Client({ petStoreBearer: bearer })`, with the transport and client options after it as before. The shared generator supplies `clientSchemes` (`schemeId`, `paramName`, `className`) for the client and, per operation, `vendorExtensions.op.securitySchemeOrder` and `hasSecuritySchemes`. An API class takes the credentials as a fourth `schemeAuthenticators` argument (a record keyed by the scheme's name in the spec); each operation passes `this.resolveSchemeAuth([...its schemes in its own order])` to `invokeApiForResult`, and `BaseApi` returns the credential of the first scheme the client holds. When none is held the operation uses the client-level `authenticator`, and with none of that it sends no credential; `security: []` still passes `NO_AUTH`. The base URL comes from the first credential given, `authenticator` first, and a client with no credential throws `TypeError('At least one credential is required')`. Every credential that is HTTP aware gets the shared `ApiClient`. There is no per-call authenticator: `injectAuthFieldContext` gives no Options interface an `auth` field, an operation with no query, header, form or cookie parameter gets no Options file (`generateOptionsFileContent` returns null) and no `options` argument, and security adds no argument to the signature.
Why: an operation that needs Basic must never put an API key into a URL because the client was built for the API key; the owner chose the per-scheme client over a per-call override.
Where: `client.mustache`, `base_api.mustache`, `api/apis.mustache`, `api/options.mustache`, `BetterNodeCodegen` (`populateRequestWrapper`, `injectAuthFieldContext`, `generateOptionsFileContent`).
Test: `test/api/pet-api.test.mustache` (`credentials per security scheme`), `test/client.test.mustache`, `test/base-api.test.mustache` (`BaseApi scheme credentials`), `BetterNodeCodegenTest`.

### Cookie values are raw or refused
Rule: an operation cookie value is sent exactly as given when every character is an RFC 6265 cookie-octet (`%x21 / %x23-2B / %x2D-3A / %x3C-5B / %x5D-7E`); any other character throws a `TypeError` before anything is sent (`Cookie value for '<name>' contains characters outside the RFC 6265 cookie-octet set; percent-encode the value before passing it`), so the caller pre-encodes. A `content` cookie is serialised and percent-encoded by the SDK, so it always passes. The check was already there; the message and the tests are new.
Where: `api/apis.mustache`. Test: `test/api/pet-api.test.mustache` (`deletePet sends the cookie value ... exactly as given`, `deletePet refuses the cookie value ... before sending`).

### Closing is graceful and idempotent
Rule: `close()` marks the client closed at once and returns without waiting. A request already running finishes; the transport the SDK created (the undici dispatcher, closed with `dispatcher.close()`, not `destroy()`) is released when the last running request completes, or at once when none is running, and only once. A request issued after `close()` fails with the one error `ApiClient has been closed and can no longer send requests`. A caller-supplied `fetch` is never called or closed. A second `close()` is a no-op. Cancellation is per request, through its `signal`. A request counts as running from the moment it reaches the transport, so an operation still waiting for an authenticator's token when `close()` is called, and a replayed request after a 401, fail with the closed error.
Where: `abstract_api_client.mustache` (`inFlight`, `releaseTransportOnce`, `releaseTransport`), `default_api_client.mustache`, `client.mustache`.
Test: `test/default-api-client-unit.test.mustache` (`graceful close`), `test/client.test.mustache` (`closing`).

### No validation at construction
Rule: a model constructor assigns, applies defaults and never throws, whatever it is given (wrong types, an unknown enum string, missing required fields). Every check and coercion that used to run there moved to the instance method `__validate()` (required fields, primitive types, int32 range, finite doubles, UUID, URI, date, time, duration, base64, `uniqueItems` to a `Set`, enum membership), which `ObjectSerializer.deserialize` runs (`validateDecoded`) on the root model, on every nested model, and on each candidate variant of a `oneOf` or `anyOf`. A subclass calls `super.__validate()` first. Coercions assign through `Object.assign` because the fields are `readonly`. An unknown enum string fails loudly at decode with `SerializationError` and the allowed values.
Where: `models/model.mustache`, `object_serializer.mustache` (`validateDecoded`, `revalidateNestedValue`).
Test: `test/object-serializer.test.mustache` (`validation happens at decode, never at construction`), `BetterNodeCodegenTest` (`theConstructorValidatesNothingAndTheDecodeCheckDoes`).

### Surface rules after round 4
The transitional F830 rows for node are gone (the conditions are met). The idiom differences that remain: the trailing `requestOptions` in the optional-parameter list, no Options interface for an operation without parameters of its own, the `ClientCredentials` type and the credentials constructor shape, the `schemeAuthenticators` argument of an API class, and the absent per-call `auth` member (dropped by the other languages in their own adopt branches). F1085 (private keys in the shared fixture) is a shared change and is not done here.

- Trace context from the caller (#136): `injectTraceContext` reads `context.active()`; async context follows `await` when the application registers an async-local-storage context manager (`context.with(ctx, fn)`) (D95).

- **F15 (typed `additionalProperties`, #103).** The getter is `Record<string, V>`; the class index signature stays `unknown` (a declared `Date` property is not assignable to `string`). The model carries `__decodeAdditionalProperties`, which runs `ObjectSerializer.deserializeMap` with the element decoder of the value type (`deserialize_arg`, now with `string` and `byte` leaves), and `reattachAdditionalProperties` calls it.
