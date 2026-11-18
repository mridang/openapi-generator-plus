# Node (TypeScript)

The `node-plus` generator produces a TypeScript package built on the Fetch API, with class-transformer driving model decoding. This page covers what the generated package needs, how it maps OpenAPI types, the options the generator accepts, and where Node differs from the other eleven languages. For the type table across all languages see [../types.md](../types.md); for the pipeline see [../how-it-works.md](../how-it-works.md).

## Versions and support policy

The generated `package.json` sets `engines.node` to `>=22`. The package targets ES2024 with `strict: true`, and the TypeScript dependency is `^5.7`. The generator's tests and formatters run in the `node:24` image.

The policy is the oldest Node line that is not end of life. A review of the type mapping (see "Under review") found that Node 22 is that line today, in maintenance until 2027-04-30, so the floor matches the policy for now.

The package runs on Node and on runtimes that have only `fetch`: Deno, Bun, Cloudflare Workers and Vercel Edge. An import map entry named `#transport` selects the HTTP client. On Node it resolves to a client built on `fetch` plus an undici dispatcher, which provides proxy and custom TLS. On other runtimes it resolves to a client that uses only web-standard APIs and throws a `TypeError` at construction if you ask for a proxy, a custom CA or disabled TLS verification.

## Type mapping

| OpenAPI | TypeScript type | Notes |
|---|---|---|
| `integer`, `int32`, `int64`, `number` with `format: float` or `double` | `number` | A JavaScript number is a double, so integers above 2^53 - 1 are rounded by `JSON.parse`. The generated README documents this. |
| `number` with no format, `format: decimal` | `Decimal` (a branded string) | Decoding reads the source text of the number with the `JSON.parse` reviver and keeps every digit; encoding writes it as a bare JSON number in plain notation. A `type: string` decimal is read from a JSON string only (a bare number is refused) and written as a JSON string. |
| `boolean` | `boolean` | |
| `string` | `string` | |
| `string` with `format: date` | `string` | A plain `YYYY-MM-DD` string, because a `Date` would carry a time and a zone the wire value never had. |
| `string` with `format: date-time` | `Date` | Millisecond precision. A wire value that is not an RFC 3339 date-time with an offset fails when the response is decoded. |
| `string` with `format: time` | `Temporal.PlainTime` | From `temporal-polyfill`. |
| `string` with `format: duration` | `Temporal.Duration` | From `temporal-polyfill`. The wire form is the protobuf-JSON form such as `3600s`. |
| `string` with `format: uuid` | `UUID` (a branded string) | Decoding validates the canonical 8-4-4-4-12 form. |
| `string` with `format: uri` | `URI` (a branded string) | Absolute URIs only. `uri-reference` and `uri-template` stay `string`. |
| `string` with `format: email` | `Email` (a branded string) | |
| `string` with `format: hostname`, `ipv4`, `ipv6` | `string` | |
| `string` with `format: byte` | `Buffer` | Base64 on the wire, converted by the serializer. |
| `string` with `format: binary`, file upload and download | `Buffer` | Uploads also accept a `Blob`. Downloads are fully buffered. |
| `array` | `Array<T>` | |
| `array` with `uniqueItems` | `Set<T>` | Written in insertion order. |
| `additionalProperties` map | `{ [key: string]: T }` | `T` is the declared value type; a free-form bucket keeps the any-type |
| free-form object | `unknown` | |
| nullable | `T \| null` | A property that is required and nullable is always sent, as `null` when empty. |
| `enum` | a `const` object plus a string-literal union | No TypeScript `enum`. |
| `oneOf`, `anyOf` | a union type alias plus a wrapper class | The class exists for runtime decoding and carries the discriminator mapping, and has an `isX()` and an `asX()` (the variant, or `null`) for each model variant beside `getActualInstance()`. A union of primitives is a plain alias with no wrapper. |

A branded string is a `string` with a compile-time tag, so a plain string does not pass where a `UUID` is expected, but there is no cost at run time.

## Dependencies

Runtime dependencies are `class-transformer` (`^0.5`), `reflect-metadata` (`^0.2`) and `temporal-polyfill` (`^0.3`). `undici` (`^6`) is an optional dependency used by the Node transport. `@opentelemetry/api` (`^1.0.0`) is an optional peer dependency, used for trace propagation and by the OpenTelemetry adapter when you install it.

The package uses legacy `experimentalDecorators`, because class-transformer needs them. It leaves `emitDecoratorMetadata` off, because turning it on causes temporal-dead-zone errors when models import each other in a cycle.

Content decoding is left to `fetch`, which already decodes gzip, deflate, `br` and `zstd`. The SDK passes through any encoding name it does not know.

Development tools are Jest 30 with ts-jest, ESLint 9, Prettier 3 and typedoc, all in `devDependencies`.

## Options

Pass options with `--additional-properties` or a config file. Options that apply to every language, such as `clientClassName`, `errorPrefix`, `envVarPrefix`, `generateTests`, `generateUnitTests` and `licenseId`, are shared by all twelve generators and are not repeated here. The Node generator adds these:

| Option | Meaning |
|---|---|
| `npmName` | The package name. Defaults to `openapi-typescript-client`. It also sets the product token in the default `User-Agent`. |
| `packageVersion` | The version written to `package.json`. Defaults to `1.0.0`. |
| `license` | The SPDX identifier, as a synonym for `licenseId`. If both are given, `licenseId` wins. Defaults to `MIT`. |
| `authors` | Written to the manifest and README. |
| `repositoryUrl`, `homepageUrl`, `issueTrackerUrl` | Written to the manifest and README only when given. |
| `documentationUrl` | Written to the README only when given. |

## Idiom notes

- `Configuration` and `TransportOptions` take one options object and also have a fluent builder.
- Every error class takes the ES2022 `{ cause }` bag as its last argument, which is how `Error` itself chains causes.
- Every operation, including one that has no parameters and no security, takes a trailing `RequestOptions` object: `{ signal?, timeout?, maxRetries?, headers? }`. `timeout` (positive milliseconds, or `null` to wait indefinitely), `maxRetries` (a non-negative integer) and `headers` replace the transport's values for that one call, and an invalid value throws a `TypeError` before anything is sent. This one object is where node keeps the per-call settings that the other SDKs put on a per-operation Options type, so node does not generate an empty Options type for an operation that has no parameters of its own. `Configuration` can also hold a client-level signal. Cancellation raises the abort error, never the timeout error.
- An authenticator whose credential is not passed reads it from the environment once, when it is built. The variable is `<PREFIX>_<FIELD>` in upper snake case (`ACME_TOKEN`, `ACME_API_KEY`, `ACME_USERNAME`, `ACME_PASSWORD`). `<PREFIX>` is the `envVarPrefix` option, else the API title without a trailing "SDK". When two security schemes have a field of the same name, each is qualified with its scheme id (`ACME_PARTNER_TOKEN_TOKEN`), and the generated README lists the names for the spec. An explicit argument wins over the variable, an empty variable counts as unset, and a credential that neither supplies throws a `TypeError` when the authenticator, and so the client, is built. `BearerAuthenticator` and `Client.withToken` also accept a function returning the token (or a promise of it), called for every request. OAuth 2.0 and OpenID Connect authenticators take their credentials as arguments only.
- A property that is a `$ref` to an enum and carries a `default` beside the reference starts at that default: the field is initialised to the enum member, and decoding a payload that omits the key gives the same member. An explicit value or an explicit `null` is never replaced.
- The client takes one optional credential per distinct security scheme of the API, named after the scheme (`new Client({ petStoreBearer: bearer, apiKeyHeader: apiKey })`), as an object in place of the single authenticator. An optional `authenticator` in the same object is the default credential. Each operation uses the credential of the first scheme in its own security requirement that the client holds, so a credential only goes to an operation whose spec names its scheme; when none of its schemes is held the operation uses the default `authenticator`, and when there is none it is sent without a credential. An operation declared `security: []` never sends one. The base URL comes from the first credential given, `authenticator` first, and a client given no credential at all throws a `TypeError`. There is no per-call authenticator: an Options interface has no `auth` member, and an operation with no parameters of its own has no Options interface, only the trailing `RequestOptions`. The single-authenticator constructor, `Client.withToken` and `Client.withAuthenticator` stay.
- A cookie parameter is sent exactly as given when every character is an RFC 6265 cookie-octet (printable ASCII except space, double quote, comma, semicolon and backslash). Any other character throws a `TypeError` before anything is sent, so the caller percent-encodes first. A `content` cookie is serialised and percent-encoded by the SDK.
- `Client.close()` (and `await using`) is graceful and idempotent. It marks the client closed at once; a request issued afterwards fails with `ApiClient has been closed and can no longer send requests`. A request already running finishes, and the connection pool the SDK built (the undici dispatcher) is released when the last one completes, or at once when none is running. `close()` never waits for a running request, and a `fetch` the caller supplied is never closed. Cancellation is per request, through its `signal`.
- A model constructor validates nothing and never throws, whatever it is given. The checks run when a response is decoded: a missing required field, a value of the wrong type, an unknown enum string and a malformed date, UUID, URI or base64 value each fail with a `SerializationError`, nested models included.
- Authenticators have a synchronous header method and a distinct `...Async` one. The package exports `isNoAuth` and `isHttpAwareAuthenticator` type guards.
- Models are classes with an `equals()` method. Their string form is `util.inspect`, with `format: password` fields shown as `***`.
- A missing required parameter throws a `TypeError`, and its message is written with double quotes because it embeds `'<param>'`.
- Property names keep the wire spelling (`modelPropertyNaming` is `original`) and imports end in `.js`.
- Testing your code: `<Group>ApiInterface` in each group file and `ClientInterface` in `client.ts`, both exported from the entry point. The classes have protected members, so TypeScript treats them nominally and an object literal cannot stand in for them; the interface is what a fake satisfies.
- A call hook runs around every operation call. `CallHook` has one method, `start(info, headers)`, that may edit the request headers and returns a `CallObserver` (one method, `finish(outcome)`) or nothing; `CallInfo` carries `operationId`, `method`, `urlTemplate`, `url` and `serverAddress`, and `CallOutcome` carries `statusCode`, `headers` and `error`. `chain(...hooks)` combines hooks. Set it with `Configuration.builder().callHook(hook)` or the `callHook` client option. It lives in `BaseApi`, above the transport, so it also runs with a supplied `fetch`. The OpenTelemetry adapter is the `./opentelemetry` subpath of the package: `createOpenTelemetryCallHook({ tracerProvider, meterProvider, propagator })`. It is the only file that imports `@opentelemetry/api` statically, and the package root never loads it. Its scope is the package name and version.

## Known limits

- `Decimal` decode is lossy until a reviver that reads the source text lands. int64 values beyond 2^53 are not preserved.
- The fetch-only transport cannot do proxies, a custom CA or disabled TLS verification.
- Downloads are fully buffered into a `Buffer`; there is no streaming.
- An optional nullable property cannot tell "absent" from "null" on the way out: both are left out of the request body.
- Callbacks, webhooks, XML bodies, Link Objects and mutual TLS are out of scope. OpenAPI 3.1 support is tracked in epic #96, and 3.2 in epic #49 (the pinned swagger-parser rejects 3.2 documents, #50).

## Under review

These are ideas from a review of the type mapping against current Node and TypeScript. None is decided, and none changes what the SDK does today.

### The Temporal story

The SDK already depends on `temporal-polyfill` and uses it for `format: time` and `format: duration`. It does not use Temporal for `format: date` or `format: date-time`: dates are plain strings and date-times are `Date`. The generator's AGENTS file says "no native date-only type", which is the reason `date` is a string, but the review points out that the same dependency could provide `Temporal.PlainDate`. Everything below is a proposal.

- Map `format: date` to `Temporal.PlainDate`. It needs `@Transform` wiring like the one for `PlainTime`, serializer and README changes, and a golden regeneration. It changes the public type of every date field, so it is an API change.
- For `format: date-time`, `Date` stays the least surprising choice with class-transformer. `Temporal.Instant` would keep nanoseconds and `Temporal.ZonedDateTime` would keep the offset, but only if sub-millisecond or offset fidelity is wanted.
- Native Temporal arrives in Node 26 (on by default), but Node 22 and Node 24 lack it. So `temporal-polyfill` would have to stay until Node 24 is end of life (2028-04-30) at the earliest, and the web transport needs it as well because browsers do not all ship Temporal. The review does not suggest raising `engines.node`.
- The review also asks the owner to confirm whether `duration` should stay protobuf-JSON (`3600s`, which every language uses today) or follow ISO 8601 as the OpenAPI format describes.

### Other ideas

- Offer an opt-in `bigint` (or string) for int64 values beyond `Number.MAX_SAFE_INTEGER` through the same mechanism, but not as the default, because that would break arithmetic.
- Accept and return `Uint8Array` instead of `Buffer` for `byte` and `binary`, so fetch-only runtimes do not need Workers' `nodejs_compat`. A `Buffer` would still be assignable. This is a public type change.
- A streaming download, and `Blob` or `ReadableStream` uploads, would need a new response type.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. `injectTraceContext` reads `context.active()`; async context follows `await` when the application registers an async-local-storage context manager (`context.with(ctx, fn)`).
