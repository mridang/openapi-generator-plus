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
| `string` with `format: binary`, file upload and download | `BodySource` for a raw request body, `ByteStream` for a download | A multipart file part is a `Buffer` or a `Blob`. A download is read as you consume it and a raw upload is sent as it is read, see Streaming. |
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
- `Client.close()` (and `await using`) is graceful and idempotent. It marks the client closed at once; a request issued afterwards fails with `ApiClient has been closed and can no longer send requests`. A request already running finishes, and the connection pool the SDK built (the undici dispatcher) is released when the last one completes, or at once when none is running. `close()` never waits for a running request, cancels the streams that are still open (their next read raises the same closed-client error), and a `fetch` the caller supplied is never closed. Cancellation is per request, through its `signal`.
- **Streaming.** An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns `ByteStream` from the one operation method (there is no second method), and `...WithHttpInfo` returns `ApiResult<ByteStream>` with an empty `rawBody`. `ByteStream` is a Web `ReadableStream<Uint8Array>` (subclassed, so `for await`, `pipeTo` and `getReader()` work) with `statusCode`, the lower-cased `headers`, an idempotent `close()` that aborts the connection instead of draining it, a `readAll(): Promise<Buffer>` that reads the rest and closes the stream, and a `toString()` that shows only the status and the bytes delivered. The transport gets the optional `ApiClient.sendStreaming(method, url, headers, body, options)`, which resolves to an `ApiStreamResponse` (status, headers, open body); an injected `ApiClient` that does not implement it keeps working for buffered calls and fails a stream call with a `TypeError` that names the transport. `DefaultApiClient` (the Node and the portable transport both) runs the buffered call's header phase (retries, redirects, the end-to-end `timeout`, the header rules) and stops there: the `timeout` ends at the response headers, `TransportOptions.builder().idleTimeout(ms)` (default 60 seconds, `0` or `null` disables) bounds the gap between two reads of the body and raises `NetworkTimeoutError`, and nothing is retried after the first body byte. A non-2xx status raises the usual typed error with its body read up to 64 KiB. A reset, or a body shorter than its `Content-Length`, raises `StreamError` (a `NetworkError`, status 0).
- **JSON Lines and server-sent events.** An operation whose success response is `application/x-ndjson`, `application/jsonl`, `application/jsonlines` or `text/event-stream` has no buffered method and no `WithHttpInfo` variant. It has `<operation>Stream(...args, options?, streamOptions?)` returning `Promise<JsonLinesStream<T>>` or `Promise<SseStream<T>>` (the first request is sent by the call, so its failure is raised there). Both are `AsyncIterableIterator`s and `AsyncDisposable`s (`for await`, `await using`) with `statusCode`, `headers`, an idempotent `close()` and a redacted `toString()`. `ServerSentEvent<T>` is a readonly interface (`id`, `event` defaulting to `message`, `data`, `retry`, `raw`). `StreamOptions extends RequestOptions` and adds `idleTimeout`, `maxReconnects`, `reconnectDelay`, `lastEventId` and `maxEventBytes` (milliseconds and bytes), so the stream method takes one trailing object where the other methods take `RequestOptions`. The parser follows the WHATWG grammar (`event-stream.ts`), a decode failure is a `SerializationError` naming the 1-based line (JSON Lines), and the generator option `sseTerminator` ends a feed on a named data value. An event stream reconnects after a clean end, a network error or an idle timeout with fresh headers and `Last-Event-ID`, up to `maxReconnects` times in a row (5; 0 for a request with a body; `-1` unlimited), waiting the server's `retry` value, else `Retry-After` on 429, 502, 503 and 504, else `reconnectDelay` doubling to 30 seconds with jitter; an event named `error` raises `ApiError`, and a 204, a 4xx, a response that is not `text/event-stream`, a cancellation or `client.close()` never reconnects. JSON Lines and binary bodies are decoded by `fetch` (gzip, deflate, br, zstd) as they arrive; an SSE request asks for `Accept-Encoding: identity`.
- **Uploads.** An operation whose request body is raw binary takes a `BodySource` (an exported class) in every signature (the operation, `...WithHttpInfo`, the `...ApiInterface` and the client): `BodySource.of(bytes)` (a `Buffer` or `Uint8Array`), `BodySource.ofFile(path)` and `BodySource.fromFactory(factory, length?)` (a function that returns a fresh Node `Readable`, Web `ReadableStream` or async iterable each time) are replayable, `BodySource.once(stream, length?)` is single use. The body is sent as it is read, never buffered, as a `ReadableStream` with `duplex: 'half'`: a known length sends `Content-Length`, an unknown one is chunked. A replayable body is opened again for a retry after a 5xx, a 429 or a network error and for a 307 or 308; a single-use body is never retried (the original answer or error is returned unchanged), a redirect that would need it again raises `ApiError` with `Refusing to replay a streamed request body`, and a 401 token refresh and an SSE reconnect follow the same rule. The SDK never closes a stream passed to `once` (it reads it with `next()` and never calls `return()`, and releases the lock of a Web stream without cancelling it) and closes what it opened from a file or a factory when the call ends. `toString()` shows the kind and the length, never the content or the path. A custom `ApiClient` receives the `BodySource` as the `body` and reads it with `open()`. Multipart bodies stay buffered.
- **Pagination.** A GET list that the shared detector finds paged (a token cursor, an id cursor, an offset, a page number, a `Link` header declared on the response or a next URL in the body; D123, D124) has `<operation>Pages(...args, options, requestOptions)` beside its plain method, in the class and in the `...ApiInterface`. It returns `Paginator<T, C>` (`T` the item type, `C` the cursor type, `never` for a list that follows a next URL), an `AsyncIterable<T>` (also `items()`) with `pages()`, an `AsyncGenerator` of `Page<T, C>`; `Page<T, C>` is an interface of `items` (frozen), `nextCursor` (`null` on the last page, always `null` for `Link`) and `result` (the `ApiResult` of the page). The cursor is an optional query parameter, so it lives in the `<Operation>Options` object, and each page is a call of `...WithHttpInfo` with `{ ...options, cursor }` and the same `requestOptions`. The method sends nothing; `items()` and `pages()` are async generators that build a fresh run on every call, so a second loop starts again, `break` stops it with nothing to close, and a failed page raises from the read that needed it. A cursor equal to the one just sent, or a `Link` that points back at the page just read or leaves the origin of the client, delivers the page it came with and raises `PaginationError` (a direct child of the root error, not an `ApiError`) from the next read. A `Link` page is requested through a private `<operation>Page(...args, pageUrl)` that replaces the path with the absolute URL and clears the query, so authentication, retries and the call hook apply, and RFC 8288 parsing is `link-header.ts`. Cancel with the `signal` of the `requestOptions`, which aborts the page request that is in flight and raises the abort reason, never a timeout. How each style ends and resumes: an id list passes the `last_id` or `next_cursor` of each page as the starting cursor of the next request and ends when a declared `has_more` is false. An offset list starts at the offset you pass, sends the offset past the items it received on each next request, and ends on an empty page or when a declared `has_more` is false. A page-number list sends the page you pass, or none, and then asks for the page after each one, and ends on an empty page or when a declared `has_more` is false. A next URL list takes the URL in the body of each page, follows it only within the origin of the client, and ends when the property is absent, `null` or empty. A cursor, offset or page that does not advance raises `PaginationError` as a token cursor does. The styles are token, id, offset, page, `Link` and body (a next URL in the body), and the language has no impossibility that changes them.
- A model constructor validates nothing and never throws, whatever it is given. The checks run when a response is decoded: a missing required field, a value of the wrong type and a malformed date, UUID, URI or base64 value each fail with a `SerializationError`, nested models included, and an unknown enum string decodes to the catch-all member `UNKNOWN_DEFAULT_OPEN_API` (D117).
- Authenticators have a synchronous header method and a distinct `...Async` one. The package exports `isNoAuth` and `isHttpAwareAuthenticator` type guards.
- Models are classes with an `equals()` method. Their string form is `util.inspect`, with `format: password` fields shown as `***`.
- A missing required parameter throws a `TypeError`, and its message is written with double quotes because it embeds `'<param>'`.
- Property names keep the wire spelling (`modelPropertyNaming` is `original`) and imports end in `.js`.
- Testing your code: `<Group>ApiInterface` in each group file and `ClientInterface` in `client.ts`, both exported from the entry point. The classes have protected members, so TypeScript treats them nominally and an object literal cannot stand in for them; the interface is what a fake satisfies.
- A call hook runs around every operation call. `CallHook` has one method, `start(info, headers)`, that may edit the request headers and returns a `CallObserver` (one method, `finish(outcome)`) or nothing; `CallInfo` carries `operationId`, `method`, `urlTemplate`, `url` and `serverAddress`, and `CallOutcome` carries `statusCode`, `headers` and `error`. `chain(...hooks)` combines hooks. Set it with `Configuration.builder().callHook(hook)` or the `callHook` client option. It lives in `BaseApi`, above the transport, so it also runs with a supplied `fetch`. The OpenTelemetry adapter is the `./opentelemetry` subpath of the package: `createOpenTelemetryCallHook({ tracerProvider, meterProvider, propagator })`. It is the only file that imports `@opentelemetry/api` statically, and the package root never loads it. Its scope is the package name and version.
- With `generateExamples` on, the package ships `examples/quickstart.ts`, an `async main` that imports the package by its own name, builds a client with `Client.withToken` from `{PREFIX}_BASE_URL` and `{PREFIX}_TOKEN` (a missing variable throws an `Error`), calls the example operation and closes the client in a `finally` block. `make examples` builds the package and compiles the example with `tsconfig.examples.json` into `.out/examples` (the package is CommonJS, so there is no top-level `await`), and `make examples-run` runs it with `node`. The README quick start is now the `Installation` and `Usage` sections, whose Usage fence is the quickstart's statements.

## Recipes

These are answers to questions that come up often, each built from what the generated SDK already has. None of them adds API.

### Hooks around every call

The call hook added by [#158](https://github.com/mridang/openapi-generator-plus/issues/158) is the one place to edit the request headers before the send and to observe the status or the error afterwards, for every operation and for the client as a whole. It is optional and does nothing when it is not set. It runs once per operation call however many sends happen inside it (retries, the one replay after a `401`), once for the plain and the `WithHttpInfo` variant alike, and also when you supply your own transport. `start` receives the call information (`operationId` as written in the spec, `method`, `urlTemplate`, `url` without its query string, `serverAddress`) and the final headers, which it can read and replace, and it returns the observer to call when the call ends or nothing. The observer's `finish` receives the status code (0 when no response arrived), the lower-cased response headers (none when no response arrived) and the error the caller receives (none on success). An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed; the hook never changes the result or the error. `CallHook` is the name to look for in this SDK.

```typescript
const timing: CallHook = {
  start(info, headers) {
    headers["X-Caller"] = "billing";
    const started = performance.now();
    return {
      finish(outcome) {
        console.log(info.operationId, outcome.statusCode, performance.now() - started);
      },
    };
  },
};

const client = new Client(authenticator, undefined, { callHook: timing });
```

To see every send of a call rather than the call as a whole (for example each retry), wrap the transport you hand to the SDK instead: see "Custom HTTP client" in the generated README. A transport you supply is never closed by the SDK, and the hook above still fires with it.

### Serving another region or tenant

A client is cheap, and its host, credentials, retry settings and connections belong to it. To call another region or another tenant, build another client; there is no per-call server URL or per-call HTTP client, and the per-call options stay `timeout`, `headers` and `maxRetries`.

```typescript
const eu = new Client(new BearerAuthenticator("https://eu.api.example.com", "eu-token"));
const us = new Client(new BearerAuthenticator("https://us.api.example.com", "us-token"));
```

### Renaming types, fields and parameters

The names come from the specification, so the seam is the normaliser: the `RUN_SCRIPT` rule runs your own JavaScript over the document before generation (see [how it works](../how-it-works.md#renaming-with-a-script)). A schema renamed to `Animal` is generated as `src/models/animal.ts`, and this language's own casing, escaping of reserved words and file naming are applied to the new name like to any other. The SDK has no configuration map of names and never reads a vendor extension for a name. The name of a property or a parameter is also its name on the wire, so renaming one in the script renames it in the requests and responses as well: there is no rename that changes only the SDK name.

### Status, headers and the raw body

Every operation has a `...WithHttpInfo` variant that returns the status code, the lower-cased response headers, the raw body and the decoded data. Every failure that carries an HTTP response is an `ApiError` that holds the status code, the response headers and the response body. A live response object is not exposed (a binary download is the one stream, see Streaming); to see or change what goes over the wire, use the call hook above or the transport you supply.

```typescript
const result = await client.pet.addPetWithHttpInfo(pet);
const status = result.statusCode;
const etag = result.headers["etag"];
const raw = result.rawBody;

try {
  await client.pet.addPet(pet);
} catch (e) {
  if (e instanceof ApiError) {
    console.log(e.statusCode, e.responseHeaders, e.responseBody);
  }
}
```

## Known limits

- `Decimal` decode is lossy until a reviver that reads the source text lands. int64 values beyond 2^53 are not preserved.
- The fetch-only transport cannot do proxies, a custom CA or disabled TLS verification.
- A JSON, text, form or multipart request body and a JSON, text or form response body are read whole into memory. Only a raw binary request body and a binary, JSON Lines or server-sent events response are streams.
- An optional nullable property cannot tell "absent" from "null" on the way out: both are left out of the request body.
- Callbacks, webhooks, XML bodies, Link Objects and mutual TLS are out of scope. OpenAPI 3.1 support is tracked in epic #96, and 3.2 in epic #49 (the pinned swagger-parser rejects 3.2 documents, #50).
- An optional and nullable property has no third state: an absent value and an explicit `null` are the same, by decision D112. A three-state type for PATCH-style APIs is declined for now.
- Operations are asynchronous only, as Promises, by decision D111. There is no blocking variant of an operation, because each SDK follows the style of its own language. Call it from your language's async machinery.
- An `integer` with no format is 64 bits, the same as `int64`, by decision D113 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)). A value above 2^31, such as a large identifier, decodes exactly.
- A security requirement that names several schemes together (for example a bearer token and an API key) sends the credentials of all of them, by decision D116. The client must hold every credential of the requirement; an alternative the client holds only in part is used only when no other alternative is complete. A requirement that names one scheme works as before.

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

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. `injectTraceContext` reads `context.active()`; async context follows `await` when the application registers an async-local-storage context manager (`context.with(ctx, fn)`).
