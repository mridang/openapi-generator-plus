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
| `number` with no format, `format: decimal` | `Decimal` (a branded string) | Encoding writes the exact digits as a bare JSON number. Decoding currently goes through plain `JSON.parse`, so digits beyond double precision are lost. |
| `boolean` | `boolean` | |
| `string` | `string` | |
| `string` with `format: date` | `string` | A plain `YYYY-MM-DD` string, because a `Date` would carry a time and a zone the wire value never had. |
| `string` with `format: date-time` | `Date` | Millisecond precision. An unparseable wire value becomes an invalid `Date`. |
| `string` with `format: time` | `Temporal.PlainTime` | From `temporal-polyfill`. |
| `string` with `format: duration` | `Temporal.Duration` | From `temporal-polyfill`. The wire form is the protobuf-JSON form such as `3600s`. |
| `string` with `format: uuid` | `UUID` (a branded string) | The model constructor validates the canonical 8-4-4-4-12 form. |
| `string` with `format: uri` | `URI` (a branded string) | Absolute URIs only. `uri-reference` and `uri-template` stay `string`. |
| `string` with `format: email` | `Email` (a branded string) | |
| `string` with `format: hostname`, `ipv4`, `ipv6` | `string` | |
| `string` with `format: byte` | `Buffer` | Base64 on the wire, converted by the serializer. |
| `string` with `format: binary`, file upload and download | `Buffer` | Uploads also accept a `Blob`. Downloads are fully buffered. |
| `array` | `Array<T>` | |
| `array` with `uniqueItems` | `Set<T>` | Written in insertion order. |
| `additionalProperties` map | `{ [key: string]: T }` | |
| free-form object | `unknown` | |
| nullable | `T \| null` | A property that is required and nullable is always sent, as `null` when empty. |
| `enum` | a `const` object plus a string-literal union | No TypeScript `enum`. |
| `oneOf`, `anyOf` | a union type alias plus a wrapper class | The class exists for runtime decoding and carries the discriminator mapping. A union of primitives is a plain alias with no wrapper. |

A branded string is a `string` with a compile-time tag, so a plain string does not pass where a `UUID` is expected, but there is no cost at run time.

## Dependencies

Runtime dependencies are `class-transformer` (`^0.5`), `reflect-metadata` (`^0.2`) and `temporal-polyfill` (`^0.3`). `undici` (`^6`) is an optional dependency used by the Node transport. `@opentelemetry/api` (`^1.0.0`) is an optional peer dependency, used for tracing when you install it.

The package uses legacy `experimentalDecorators`, because class-transformer needs them. It leaves `emitDecoratorMetadata` off, because turning it on causes temporal-dead-zone errors when models import each other in a cycle.

Content decoding is left to `fetch`, which already decodes gzip, deflate, `br` and `zstd`. The SDK passes through any encoding name it does not know.

Development tools are Jest 30 with ts-jest, ESLint 9, Prettier 3 and typedoc, all in `devDependencies`.

## Options

Pass options with `--additional-properties` or a config file. Options that apply to every language, such as `clientClassName`, `errorPrefix`, `generateTests`, `generateUnitTests` and `licenseId`, are shared by all twelve generators and are not repeated here. The Node generator adds these:

| Option | Meaning |
|---|---|
| `npmName` | The package name. Defaults to `openapi-typescript-client`. It also sets the product token in the default `User-Agent`. |
| `packageVersion` | The version written to `package.json`. Defaults to `1.0.0`. |
| `license` | The SPDX identifier, as a synonym for `licenseId`. If both are given, `licenseId` wins. Defaults to `MIT`. |
| `authors` | Written to the manifest and README. |
| `repositoryUrl`, `homepageUrl`, `issueTrackerUrl` | Written to the manifest and README only when given. |
| `documentationUrl` | Written to the README only when given. |
| `envPrefix` | The prefix of the environment variables the authenticators read when no credential is passed. Defaults to the package name in upper snake case. |

## Idiom notes

- `Configuration` and `TransportOptions` take one options object and also have a fluent builder.
- Every error class takes the ES2022 `{ cause }` bag as its last argument, which is how `Error` itself chains causes.
- Each operation takes a trailing per-call options object, which can carry `{ signal?: AbortSignal }`. `Configuration` can also hold a client-level signal. Cancellation raises the abort error, never the timeout error.
- Authenticators have a synchronous header method and a distinct `...Async` one. The package exports `isNoAuth` and `isHttpAwareAuthenticator` type guards.
- Models are classes with an `equals()` method. Their string form is `util.inspect`, with `format: password` fields shown as `***`.
- A missing required parameter throws a `TypeError`, and its message is written with double quotes because it embeds `'<param>'`.
- Property names keep the wire spelling (`modelPropertyNaming` is `original`) and imports end in `.js`.

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

- Decode `Decimal` losslessly with the `JSON.parse` reviver's `context.source`, which Node 22 supports. This closes the known decimal gap and needs no floor change.
- Offer an opt-in `bigint` (or string) for int64 values beyond `Number.MAX_SAFE_INTEGER` through the same mechanism, but not as the default, because that would break arithmetic.
- Accept and return `Uint8Array` instead of `Buffer` for `byte` and `binary`, so fetch-only runtimes do not need Workers' `nodejs_compat`. A `Buffer` would still be assignable. This is a public type change.
- A streaming download, and `Blob` or `ReadableStream` uploads, would need a new response type.
