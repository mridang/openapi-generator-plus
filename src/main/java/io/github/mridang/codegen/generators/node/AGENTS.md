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
| `templates/node/errors/*.mustache` | the error tree (`*Error` suffix, ES2022 `{ cause }` options bag) | error classes and messages |
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
| Format types | `UUID`, `URI`, `Email`, `Decimal` are branded string aliases; unions of primitives (`Array<Buffer> \| Buffer`) are plain aliases with no wrapper class; enums of some operations are literal unions | TypeScript has no nominal types. The api-surface allow-list names these as node-only. |
| Construction | `Configuration` and `TransportOptions` take one options object plus a fluent builder | named-argument idiom of TypeScript (the allow-list entry shared with elixir) |
| Error construction | every `*Error` takes the ES2022 `{ cause }` bag as its last argument | that is the signature of `Error` itself |
| Auth header accessor | a distinct `...Async` method beside the sync one; `isNoAuth` and `isHttpAwareAuthenticator` type guards | value-wrapped async (`Promise`) like csharp and dart; no interface check at runtime |
| Cancellation | trailing per-call options object `{ signal?: AbortSignal }` plus an optional client-level signal on `Configuration` | see S7; it is what the OpenAI and Stripe SDKs take |
| Transport swap | `#transport` conditional import: Node gets undici, other runtimes get a fetch-only client that throws `TypeError` for proxy, custom CA or disabled TLS verification | non-Node runtimes have no dispatcher |
| Content-Encoding | pass-through when the name is unknown; fetch already decoded gzip, deflate, br and zstd | forced, see D-FORCED-1 |
| Helper visibility | extra helpers on `ObjectSerializer`, `ValueSerializer`, `HeaderSelector`, `AbstractApiClient`, `DefaultApiClient`, `NO_AUTH`, `markExchanged` and the token-manager hand-over | no package-private visibility across files |
| Model equality and dump | classes with `equals()`; string form is the default `util.inspect` | JS has no structural equality; inspect already dumps fields |
| Missing-parameter error | `TypeError` with a double-quoted message string | the message embeds `'<param>'`, so a single-quoted literal is a syntax error |
| Dates and times | `Date` for date-time, `Temporal.PlainDate`/`PlainTime` from `temporal-polyfill` | no native date-only or time-only type |
| Decimals | branded `Decimal`; decode keeps the number text via `JSON.rawJSON`/reviver source | JS numbers are binary doubles |

## Settled decisions (do not re-open in review)

### Cancellation is a per-call `{ signal }` plus a client-level signal (S7, F228, 2026-11-16)
Rule: every operation takes a trailing options object `{ signal?: AbortSignal }` and `Configuration` takes an optional client-level `signal`; the two are merged and either cancels the call. Cancellation raises the caller's own abort reason (an `AbortError` by default), never `NetworkTimeoutError`; the timeout is a separate `AbortSignal.timeout`.
Why: this is what the OpenAI and Stripe SDKs take; a shared `RequestOptions` wrapper would be less natural than the native handle. The owner approved it.
Where: `abstract_api_client.mustache` (`isCallerAbort`, merged signals), `configuration.mustache` (`signal`), `api/apis.mustache`.
Test: `test/default-api-client*.test.mustache`, `test/configuration.test.mustache`, `test/base-api.test.mustache`.

### Injected transport is `fetch` plus `fetchOptions` (S9, F405, 2026-11-16)
Rule: the client builder accepts the standard `fetch` function and `fetchOptions`, keeps the SDK defaults applied, never closes a caller-supplied client, and fails fast when it conflicts with the SDK's own proxy, TLS or pool settings.
Why: every SDK generator that was compared (OpenAI, Stainless, Speakeasy, Fern, Azure, Stripe) accepts the standard client object.
Where: `abstract_api_client.mustache` (the `fetchOptions` spread; caller-controlled keys such as headers, body, redirect, timeout and signal always win), `client.mustache`.
Test: `test/default-api-client-unit.test.mustache`.

### Strict containers (S1, F006, 2026-11-16)
Rule: a response that is a container of primitives is checked element by element like a single primitive.
Why: the old `x as T` casts check nothing at runtime. Open leftovers F469 and F470 are the plain string, `Decimal`, `UUID` and `Buffer` leaves in `deserialize_arg.mustache` and the bare primitive return in `apis.mustache` (`json as <returnType>`), which the petstore cannot lock until it gains such operations.
Where: `deserialize_arg.mustache`, `object_serializer.mustache` (`deserializeLeaf`), `api/apis.mustache`.
Test: `test/object-serializer.test.mustache`.

### Content parameters use the SDK serializer (S2, F054, D50, D80, 2026-11-16)
Rule: `content: application/json` parameters in query, header, path and cookie go through `ObjectSerializer.serialize`, never `JSON.stringify`.
Why: wire names, canonical date-times, omitted nulls and rejected NaN must match the body path.
Where: `api/apis.mustache`, `value_serializer.mustache`.
Test: `test/value-serializer.test.mustache`, `test/base-api.test.mustache`.

### Schema constraints are not enforced (S3, F056, D55, 2026-11-16)
Rule: node does not enforce pattern, length, range, multipleOf or item-count keywords; the server owns validation. The README has a "Schema constraints" section.
Why: only python enforces them, natively via pydantic; class-transformer gives no cheap equivalent.
Where: `readme.mustache`, `models/model.mustache`.
Test: golden README and `test/metadata.test.mustache`.

### allOf with a discriminator uses class inheritance (S4, F057, F802, 2026-11-16)
Rule: the allOf parent with a discriminator is a base class that subtypes extend; a model reused in two unions is never re-parented, unions are type aliases or interfaces.
Why: a class has one parent. Inheritance only where the schema is allOf plus discriminator.
Where: `models/model.mustache`, `BetterNodeCodegen.setsDiscriminatorDefaultOnChildren`, `demotesDiscriminatorFromRequiredVars`.
Test: `test/composed-schema.test.mustache`.

### Required plus nullable keeps the key (S5, F215, D66, 2026-11-16)
Rule: decode rejects a missing key and keeps an explicit `null`; encode always writes the key, `null` when unset.
Why: it is the one reading of OpenAPI that distinguishes required from nullable.
Where: `models/model.mustache` (constructor and decode), `object_serializer.mustache` (serialize keeps the key).
Test: `test/object-serializer.test.mustache`.

### `uniqueItems` order is left unspecified (S6, F319, 2026-11-16)
Rule: a `Set` is sent in its insertion order; the README says so. Do not sort. This supersedes the sorted-wire proposal D69.
Why: the owner decided that sets are sent in each language's natural collection order.
Where: `object_serializer.mustache` (serialize replacer), `readme.mustache`.
Test: `test/object-serializer.test.mustache`.

### Parameter order is fixed (S8, F803, 2026-11-16)
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

### `format: number` is a branded Decimal that is lossy on decode (D83, F075, F133, F202, F221, F224)
Rule: bare `type: number` is the branded `Decimal`. Encode keeps the text through `JSON.rawJSON` (an unquoted JSON number); decode currently uses a plain `JSON.parse`, so the text of an already rounded double is kept. The README carries the precision caveat. Do not change the type to a native `number`.
Why: this is documented; making decode lossless with a `JSON.parse` reviver using `context.source` is open, not decided as forced. Node also accepts any string for a `Decimal`.
Where: `object_serializer.mustache`, `models/model.mustache`, `readme.mustache`.
Test: `test/object-serializer.test.mustache`.

### Model names that collide get the `Model` prefix (D45, F173)
Rule: node already prefixes colliding names (`ModelError`); the reserved set is `languageSpecificPrimitives`, and `getModelNameCollisionPrefix()` returns `Model`.
Why: the shared decision adopts node's convention in all twelve.
Where: `BetterNodeCodegen.getModelNameCollisionPrefix`, `languageSpecificPrimitives`.
Test: `NodeReservedWordsSpec`.

### Unions of primitives are aliases with no wrapper (api-surface allow-list)
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
Rule: int32 values are range-checked beside `Number.isInteger` (int64 is integer-only, a forced exception since JS numbers lose precision beyond 2^53); date-time decode needs RFC 3339 with an offset (`parseDateTime`); base64 decode requires the standard alphabet, padding and no whitespace (`decodeBase64`); `format: uri` must be an absolute RFC 3986 URI; a null model element in an array or map is a `SerializationError`; a constructor given data rejects missing required fields, and `validateRequired()` runs before dispatch.
Why: one accepted-input set across twelve SDKs.
Where: `models/model.mustache`, `object_serializer.mustache`.
Test: `test/object-serializer.test.mustache`.

### Binary, byte and non-JSON responses (D30, D31, D32, D35, D39)
Rule: a `format: binary` return is always raw bytes via a `binaryReturn` flag and never JSON-parsed; a `format: byte` return uses the strict `decodeBase64`; a non-JSON success type on a non-binary return is a `SerializationError` unless the return is a string and the type is text; a binary request body is sent verbatim (`rawBody`) under any selected content type; a plain object in a form body is a `SerializationError`.
Why: no silent re-encoding or fallthrough.
Where: `base_api.mustache`, `api/apis.mustache`.
Test: `test/base-api.test.mustache`.

### Wire encoding details (D36, D47, D48, D49, D51, D81, D82, D89, D90)
Rule: multipart scalars are text parts using `ObjectSerializer.stringify`, only plain objects are JSON parts; object-valued params go through `toWireMap`; a path value of `.` or `..` throws `TypeError`; allowReserved keeps only the RFC 3986 query-legal set literal; `Buffer` values are base64 in parameters; `format: time` is `HH:mm:ss` plus `.SSS` only when milliseconds are non-zero; a `Set` parameter expands like an array; the multipart filename directive uses a byte-wise unreserved-only encoder.
Why: byte-identical wire.
Where: `abstract_api_client.mustache` (`buildContentDisposition`), `value_serializer.mustache`, `object_serializer.mustache`.
Test: `test/value-serializer.test.mustache`, `test/base-api.test.mustache`.

### Codegen escaping and names (D40, D41, D42, D43, D46, D60, D61)
Rule: string literals use the shared escaper; doc prose and manifest descriptions use triple braces with a `*/` guard and `lambda.jsonString` in `package.mustache`; one `export enum` per server variable; a `$ref` enum default becomes the typed member (`Availability.OnHold`); a branded decimal default is `'1.5' as Decimal`.
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

### FORCED: fetch re-escapes `'` in allowReserved (D78, F387)
Rule: the encoder keeps `'` literal but WHATWG URL parsing in fetch rewrites it to `%27`. Documented, not a defect.
Why: it is how fetch accepts a URL.
Where: `value_serializer.mustache`.
Test: `test/value-serializer.test.mustache`.

### FORCED: required-check gated on supplied data (F227, D68)
Rule: `new Pet()` must succeed, so the constructor checks required fields only when `data !== undefined`; required fields are enforced on the deserialize path and by `validateRequired()` before dispatch.
Why: class-transformer builds every model with a no-argument constructor.
Where: `object_serializer.mustache`, `models/model.mustache`.
Test: `test/object-serializer.test.mustache`.

### Free-form null retention, OAuth2 repr, per-model union text (root AGENTS.md closed items)
Rule: node's serializer drops `null` entries from a free-form map (java and kotlin keep them); the OAuth2 auth-code repr shows only host, clientId and clientSecret; `ApiKeyAuthenticator` repr includes `location`; union no-match uses the central `No oneOf/anyOf variant matched the JSON`. Do not flag them.
Why: fixture-only, needs per-field metadata the schema lacks (F314 for the free-form item); the rest are documented and unharmonised (F285, F396).
Where: `object_serializer.mustache`, `auth/oauth/*.mustache`.
Test: none beyond the golden.

## Known limitations and out of scope

Callbacks, webhooks, XML bodies, Link Objects and mutual TLS are out of scope (#53, #70, #78, #88, #90). OpenAPI 3.1 and 3.2 are tracked as epics #96 and #49; the pinned swagger-parser rejects 3.2 documents (#50). The fetch-only transport cannot do proxies, a custom CA or disabled TLS verification and throws `TypeError` at construction. `Decimal` decode is lossy (F075, F133) until a reviver using `context.source` lands. int64 values beyond 2^53 are not preserved. The F469 and F470 container leaf and bare primitive checks cannot be locked until the petstore has those operations (spec change needed). The F446 raw operation-server description interpolated into a block comment, and F278 (api key scheme name pasted raw), are open. F782 (the `additionalProperties` accessor shares a class with an index signature of another type) and F779 (the petstore needs a model with `additionalProperties: {type: string}`) are open. F749 (a flaky base-api 400 test on a cold start) is open. F191: typedoc is warnings-fatal in the Makefile but no spec runs it. Integer-enum casing is parked (F308), and the missing discriminator default (F129), the object-valued parameters (F063) and `format: time` parameters (F071) are parked as shared steps. The sorted-`Set` proposal (D69) and F090 environment proxy handling are not applied to node: node ignores ambient proxy variables because it only uses `TransportOptions.proxy`.

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
python3 etc/api-surface.py check
```

Language-specific: `mvn verify -Dgroups=node`; inside a generated SDK `npm test`, `npx tsc --noEmit`, `npx eslint .`, `npx prettier --check .` and `npx typedoc --treatWarningsAsErrors`.
