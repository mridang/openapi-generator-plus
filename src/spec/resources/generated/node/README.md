# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated TypeScript SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## API information

Pets, owners and orders for integration tests

- **Contact:** Petstore API support
- **Contact email:** support@example.com
- **Contact URL:** https://example.com/support
- **License:** Apache 2.0 (https://spdx.org/licenses/Apache-2.0.html)
- **Terms of service:** https://example.com/terms

## Requirements

- **Node.js**: 22 LTS or newer (`engines.node >= 22`)
- **Package manager**: npm 10.x (bundled with Node 22) or pnpm
- **TypeScript**: 5.7 or newer (installed as a devDependency)

## Runtime support

The HTTP transport ships in two variants resolved automatically by the
package's [subpath imports](https://nodejs.org/api/packages.html#subpath-imports)
(`#transport`); both share a single `AbstractApiClient` built on Web-standard
`fetch` / `crypto` / `DecompressionStream`, so redirect handling, decompression,
charset decoding, and header injection are identical everywhere:

- **Node.js** resolves to the `undici`-backed transport, with full support for
  proxy routing, custom CA certificates, and TLS-verification toggling. `undici`
  is an `optionalDependency` — installed on Node, skipped elsewhere.
- **Deno, Bun, and edge runtimes** (Cloudflare Workers, Vercel Edge) resolve to a
  portable transport that uses only the Fetch API and imports no `undici` or
  `node:*` module, so the package loads on runtimes that provide `fetch` but no
  Node built-ins. Proxy / custom-CA / TLS-off are not expressible through the
  standard Fetch API there, so requesting one throws a `TypeError` rather
  than being silently ignored; everything else (including the `maxRedirects`
  cap) works unchanged. Browsers are best-effort only — the manual redirect loop
  relies on reading `Location` from a `redirect: 'manual'` response, which CORS
  makes opaque.

## Installation

```bash
npm install petstore-client
```

## Usage

```typescript statements
import { Client } from "petstore-client";

const host = process.env.SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL;
if (host === undefined) {
  throw new Error("SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL is not set");
}
const token = process.env.SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN;
if (token === undefined) {
  throw new Error("SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN is not set");
}
const client = Client.withToken(host, token);
try {
  const result = await client.store.getDefaults();
  console.log(result);
} finally {
  await client.close();
}
```

Every error the SDK raises derives from `OpenAPIError`; an `ApiError` carries the HTTP status, with a subclass per status class. Authentication
schemes, servers, custom transports, cancellation and testing are covered in
[SKILLS.md](SKILLS.md).

The package declares `sideEffects`, so a bundler drops what you do not use. For a
size-sensitive edge bundle, import a single API class (for example
`PetApi`) instead of the `Client` facade, which
constructs every API group.

### Raw responses

Every operation has a `...WithHttpInfo` variant returning an `ApiResult<T>` with
`statusCode`, `data`, `rawBody` and the lower-cased `headers` (a frozen copy). It
is the way to read a response header. An operation whose success response
declares headers also has a `<Operation>ResponseHeaders` type;
`<Operation>ResponseHeaders.from(result.headers)` reads them in their declared types.

### Timeouts

`TransportOptions.builder().timeout(5000)` sets one end-to-end budget in
milliseconds (a positive number) for the whole call, redirects and retries
included. A call that exceeds it fails with `NetworkTimeoutError`. The default is
60 seconds; `timeout(null)` waits indefinitely.

`TransportOptions.builder().maxResponseBytes(n)` bounds a buffered response body to `n` bytes (off by default): a body that passes it, on the wire or once decompressed, fails with `ResponseTooLargeError` and is never retried. A streamed response is not bounded by it.

### Automatic retries

Every call is retried up to twice by default (`TransportOptions.builder().maxRetries(n)`, `0`
disables it). A request is retried after a network error that happened before any
response arrived and after a `408`, `409`, `429` or `5xx` response other than `501`.
The wait is 0.5 s doubling to 8 s with up to 25% jitter, or the server's `Retry-After`
(seconds or an HTTP date, at most 60 s). The retries share the one end-to-end `timeout`
(they never extend it), stop at once when the call is cancelled, rebuild every header and
ask the authenticator for its headers again. A timeout and a failure while reading a
response body that did arrive are not retried, and neither is a request whose body is a
single-use `BodySource`.

### Expired tokens

When an OAuth2 or OpenID Connect call is answered with 401, the cached access
token is dropped (the refresh token is kept) and the request is sent once more
with fresh headers. A second 401 is raised as `UnauthorizedError`. A single-use or multipart
body is not replayed, and API key, basic and bearer authenticators never replay.

### Response compression

The SDK asks for a compressed response (`Accept-Encoding: gzip, deflate, br`, and `zstd` where
the running Node.js decodes it) unless you set your own `Accept-Encoding`, and decodes the body
before it reaches you, so `ApiResult.rawBody` is always the decoded text. A body whose
`Content-Encoding` does not match its bytes fails with an `ApiError` instead of being passed on
corrupt.

### Logging

The SDK writes nothing to the console or to a logger. To log requests, wrap the `fetch` you pass
to `TransportOptions.builder().fetch(...)`; log the method and URL, never the headers, which carry
credentials. An `ApiError` renders its response headers with the credential-bearing ones masked
(`***`), so logging `error.toString()` is safe.

### Versioning and support

The package follows [semantic versioning](https://semver.org): a patch release fixes bugs, a minor
release adds API without breaking any, and a breaking change is a major release. The supported
runtimes are the Node.js releases that are not end-of-life (`engines.node >= 22`); a release that
drops one is a major release.

### Request id

`TransportOptions.builder().injectRequestId(true)` adds a unique `X-Request-ID`
header to every request unless you set your own (header names are matched
case-insensitively). Quote it when you report a problem with a call.

### Per-call options

Every operation takes a trailing `RequestOptions` object. Besides the `signal` below it
carries three overrides for that one call: `timeout` (milliseconds, a positive number,
or `null` to wait indefinitely), `maxRetries` (a non-negative integer, `0` disables
retries) and `headers` (extra request headers). They replace the transport's `timeout`
and `maxRetries` for the call; `headers` replace a client default or an operation header
of the same name, whatever its case, but never the authentication headers. A value that
is not valid throws a `TypeError` before anything is sent.

```typescript file
import { Client } from "petstore-client";

async function getDefaultsWithinTwoSeconds(client: Client): Promise<void> {
  await client.store.getDefaults({
    timeout: 2000,
    maxRetries: 0,
    headers: { "X-Trace-Id": "abc" },
  });
}
```

### Credentials

An authenticator takes its credential from the argument you pass. When you pass none, it
reads an environment variable once, when it is built (an empty variable counts as unset):
`SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN` for `Client.withToken` and `BearerAuthenticator`,
`SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY` for `ApiKeyAuthenticator`, and `SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME` with
`SWAGGER_PETSTORE_OPEN_API_3_0_PASSWORD` for `BasicAuthenticator`. The authenticator of a security scheme
reads the variable named for that scheme, so two schemes with a field of the same name never
share a variable:

- `PetStoreBasicAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD`
- `AdminBasicAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD`
- `ArchiveDigestAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_ARCHIVE_DIGEST_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_ARCHIVE_DIGEST_PASSWORD`
- `PetStoreBearerAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN`
- `ServiceTokenAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN`
- `ApiKeyHeaderAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY`
- `InternalApiKeyAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY`
- `ApiKeyQueryAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY`
- `SessionCookieAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY`
- `MachineAuthClientCredentialsAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID`, `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET`
- `LegacyAuthPasswordAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID`, `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET`, `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD`
- `UserAuthAuthorizationCodeAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID`, `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET`, `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI`
- `BrowserAuthImplicitAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID`
- `DeviceAuthDeviceAuthorizationAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_DEVICE_AUTH_CLIENT_ID`
- `SsoAuthAuthenticator`: `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID`, `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET`, `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI`

The OAuth 2.0 and OpenID Connect authenticators take their credentials as arguments only. A
credential that is missing from both throws a `TypeError` when the authenticator (and so the client)
is built, never at the first call. `BearerAuthenticator` and `withToken` also take a function in place
of the token; it is called for every request, so a token that rotates is always current:

```typescript file
import { Client } from "petstore-client";

function clientWithRotatingToken(readToken: () => Promise<string>): Client {
  return Client.withToken(
    "https://api.example.com",
    async () => await readToken(),
  );
}
```

The device authorization flow (RFC 8628) gets an OAuth2DeviceAuthorizationAuthenticator: call requestDeviceAuthorization() to get the user code and verification URI to show to the user, then pollForToken() to wait for the approval.

HTTP Digest schemes get a DigestAuthenticator built from a username and a password: the first request goes without credentials, and the SDK answers the server's 401 challenge and replays the request once. The Negotiate scheme is not supported, because it needs a native Kerberos library.

### Credentials per security scheme

`Client` takes one optional credential for each security scheme of the API, named after the scheme: `petStoreBasic`, `adminBasic`, `archiveDigest`, `petStoreBearer`, `serviceToken`, `apiKeyHeader`, `internalApiKey`, `apiKeyQuery`, `sessionCookie`, `machineAuth`, `legacyAuth`, `userAuth`, `browserAuth`, `deviceAuth`, `ssoAuth`. Pass them as an object in place of the authenticator:

```typescript file
import { Client, type Authenticator } from "petstore-client";

function clientWithSchemeCredential(
  schemeAuthenticator: Authenticator,
): Client {
  return new Client({ petStoreBasic: schemeAuthenticator });
}
```

Each operation uses the credential of the first scheme in its own security requirement that you gave, so a credential is only sent to an operation whose spec names its scheme, and there is no per-call override. An optional `authenticator` in the same object serves any operation none of your scheme credentials matches. An operation the spec marks as needing no authentication never sends a credential. The client takes its base URL from the first credential given (`authenticator` first), and is refused with a `TypeError` when it gets none.

### Cookie parameters

A cookie parameter is sent exactly as you give it when every character is an RFC 6265 cookie-octet: printable ASCII except space, double quote, comma, semicolon and backslash. The SDK does not encode it for you, so percent-encode a value that holds any other character before you pass it. A value that holds one is refused with a `TypeError` before anything is sent.

### Closing the client

`await client.close()` (or `await using client = ...`) closes the client gracefully. It marks the client closed at once; a call started afterwards fails with the error `ApiClient has been closed and can no longer send requests`. A call already running finishes, and the connection pool the SDK created for itself is released when the last of them completes, or straight away when none is running. Closing never waits for a running call, so a call with no timeout cannot make it hang; cancel one call with its own `signal`. Streams that are still open are cancelled. A `fetch` you supplied is never closed, and closing a second time does nothing.

### Models

Models are immutable: every field is `readonly`, so a changed model is a new one. `with` returns a
copy with the given fields replaced and leaves the original alone. Building a model validates
nothing: the constructor accepts any value and never throws, so the types you wrote are the only
check until the model is decoded (see "Unknown enum values and discriminators").

```typescript file
import { Account } from "petstore-client";

function copyOf(original: Account): Account {
  return original.with({});
}
```

### Dates

A `format: date` value is a plain `YYYY-MM-DD` string, in models and in parameters alike. It is
validated (it must name a real calendar day; a date-time is refused), sent exactly as given and
read back as the same string, so it never moves with a time zone. A `Date` is also accepted where
a date goes in, and is sent as its UTC date. A `format: date-time` value is a `Date`.

### Cancellation

Every operation takes a trailing `RequestOptions` object carrying an
`AbortSignal`; aborting it cancels that call:

An operation with optional parameters takes them before the signal: pass `undefined` to skip
them. The cancelled call rejects with the signal's reason (an `AbortError` by default).

```typescript file
import { Client } from "petstore-client";

async function getDefaultsCancelled(client: Client): Promise<void> {
  const controller = new AbortController();
  const pending = client.store.getDefaults({ signal: controller.signal });
  controller.abort();
  await pending;
}
```

Pass a client-level signal as the third constructor argument
(`new Client(authenticator, undefined, { signal })`) to cancel every call made
through the client. The client-level and the per-call signals are merged: either
one cancels the call. Cancellation is distinct from the timeout, which still fails
with `NetworkTimeoutError`.

### Streaming

An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns a `ByteStream` instead of the whole body in memory, so a large download never has to fit in memory. The call returns as soon as the response headers arrive, and the body is read as you consume it. A `ByteStream` has the status code, the lower-cased headers and an idempotent `close()` that aborts the connection instead of draining it, so close it when you stop early (`try`/`finally`). `readAll()` reads the rest into memory and leaves the stream closed.

```typescript file
import { createWriteStream } from "node:fs";
import { Writable } from "node:stream";
import { Client } from "petstore-client";

async function saveTo(
  client: Client,
  path: string,
  ...args: Parameters<Client["pet"]["downloadPetDocument"]>
): Promise<void> {
  const stream = await client.pet.downloadPetDocument(...args);
  try {
    console.log(stream.statusCode);
    await stream.pipeTo(Writable.toWeb(createWriteStream(path)));
  } finally {
    await stream.close();
  }
}
```

The call `timeout` covers the wait for the response headers and stops there. `idleTimeout` (in `TransportOptions`, default 60 seconds, `0` or `null` disables it) bounds the gap between two reads of the body and raises `NetworkTimeoutError`. Retries, redirects, token refresh and the error contract apply up to the headers exactly as for a buffered call; a request is never retried after the first body byte, and a non-2xx status raises the usual typed error with its body read up to 64 KiB and never returns a stream. A connection reset, or a body that ends before its declared `Content-Length`, raises `StreamError`, a `NetworkError` with status 0. `client.close()` cancels the streams that are still open, and their next read raises the closed-client error. A custom transport that does not implement `sendStreaming` fails a stream call with `TypeError` naming it, and its buffered calls keep working.

An operation whose request body is raw binary (`type: string, format: binary`) takes a `BodySource` instead of buffering the bytes, so a large upload never has to fit in memory. A `BodySource` is replayable, made with `BodySource.of(bytes)`, `BodySource.ofFile(path)` or `BodySource.fromFactory(factory)` (a factory that opens a fresh stream each time), or single use, made with `BodySource.once(stream)`. The body is sent as it is read: a known length (bytes, a file, or a length you pass) goes out as `Content-Length` and an unknown length is sent chunked. A replayable body is read again for a retry after a 5xx, a 429 or a network error and for a 307 or 308 redirect. A single-use body is never retried, so the original error is raised unchanged, and a 307 or 308 is refused with `Refusing to replay a streamed request body`; it is never buffered to make a replay possible. The SDK never closes a stream you pass to `once` and closes the streams it opens itself from a file or a factory. Multipart bodies stay buffered. A 401 that refreshes the token follows the same rule, and an event stream request with a single-use body is never reconnected.

An operation whose success response is JSON Lines (`application/x-ndjson`, `application/jsonl`, `application/jsonlines`) or server-sent events (`text/event-stream`) has no buffered method. It has a `<operation>Stream` method that takes the operation's parameters, its options object and a trailing `StreamOptions`, and returns a `JsonLinesStream<T>` of the schema type or an `SseStream<T>` of `ServerSentEvent<T>` (`id`, `event`, `data`, `retry` and the raw `data` text; `T` is the schema of the `data`, or a string). Iterate it, and close it when you stop early. A JSON Lines line that does not decode, and a line or event longer than `maxEventBytes` (1 MiB), raise `SerializationError` and end the stream.

```typescript file
import { Client } from "petstore-client";

async function readFeed(
  client: Client,
  ...args: Parameters<Client["store"]["exportOrdersStream"]>
): Promise<void> {
  const feed = await client.store.exportOrdersStream(...args);
  try {
    for await (const item of feed) {
      console.log(item);
    }
  } finally {
    await feed.close();
  }
}
```

```typescript file
import { Client } from "petstore-client";

async function readEvents(
  client: Client,
  ...args: Parameters<Client["store"]["watchNoticesStream"]>
): Promise<void> {
  const feed = await client.store.watchNoticesStream(...args);
  try {
    for await (const event of feed) {
      console.log(event.data);
    }
  } finally {
    await feed.close();
  }
}
```

An event stream reconnects when the connection ends, fails or stays silent for the idle timeout: it asks again with fresh headers and `Last-Event-ID`, waits the server's `retry` value or `reconnectDelay` (1 second, doubling to 30 seconds with jitter), and raises the last failure after `maxReconnects` failures in a row without an event (5; 0 for a request with a body; `-1` is unlimited). It never reconnects after a 204, a status other than 408, 429 and 5xx, a response that is not `text/event-stream`, a cancellation or `client.close()`. An event named `error` raises `ApiError` with the response status and the data as its body, and a comment line such as a heartbeat counts as activity for the idle timeout. The generator option `sseTerminator` names a data value (such as `[DONE]`) that ends the stream and is not delivered. `StreamOptions` also takes `idleTimeout` and `lastEventId`.

### Pagination

An operation that returns a list one page at a time has a `<operation>Pages` method next to its plain method. It takes the same parameters in the same order and returns a `Paginator<T, C>`, where `T` is the item type and `C` is the type of the cursor parameter. The method sends no request: the first request goes out when you first read an item or a page, and each next request goes out only when you ask for more, so stopping early sends nothing further and leaves nothing to close. Iterate the paginator with `for await` for the items (or call `items()`), call `pages()` for `Page<T, C>` values with `items`, `nextCursor` and `result`, the same `ApiResult` that the `WithHttpInfo` variant returns, so the status and headers stay reachable. Iterating again starts again from the starting position.

```typescript file
import { Client } from "petstore-client";

async function listEverything(
  client: Client,
  ...args: Parameters<Client["pet"]["listPetCatalogPages"]>
): Promise<void> {
  const list = client.pet.listPetCatalogPages(...args);
  for await (const item of list) {
    console.log(item);
  }
  for await (const page of list.pages()) {
    console.log(`${page.items.length} items, status ${page.result.statusCode}`);
  }
}
```

The cursor you pass to the method is the starting position, and the next cursor of a page resumes the list: pass it as the starting cursor of a new call. The page size and every other parameter, option and header are repeated unchanged on each page request, and each page is an ordinary call with its own retries, timeout, redirects, authentication and redaction. An error on a later page is raised from the read that needed it, after the items of the earlier pages were delivered, and ends the iteration; the SDK never skips a page. A list ends when the next cursor is absent, `null` or empty, and an empty page with a next cursor continues. A page that returns the cursor that was just sent raises `PaginationError`, a direct child of `OpenAPIError`, after its items were delivered, instead of looping. There is no total limit and no collect-all: use `break`.

How each style ends and resumes: an id list passes the `last_id` or `next_cursor` of each page as the starting cursor of the next request and ends when a declared `has_more` is false. An offset list starts at the offset you pass, sends the offset past the items it received on each next request, and ends on an empty page or when a declared `has_more` is false. A page-number list sends the page you pass, or none, and then asks for the page after each one, and ends on an empty page or when a declared `has_more` is false. A next URL list takes the URL in the body of each page, follows it only within the origin of the client, and ends when the property is absent, `null` or empty. A cursor, offset or page that does not advance raises `PaginationError` as a token cursor does.

A list that is followed by a `Link` header (`rel="next"`, RFC 8288) takes the next URL from the header of each response. A next URL that leaves the scheme, host or port of the client is refused with `PaginationError` and nothing, no credentials either, is sent to it; such a method has no starting cursor, so it cannot resume.

A list whose page body carries the next URL in a property (a next URL list) is followed in the same way as a `Link` list: the URL is taken from the body of each response, a next URL that leaves the scheme, host or port of the client is refused with `PaginationError`, nothing is sent to it, and such a method cannot resume from a saved position.

The generator finds a paged operation by its names and shape. The operation must be a GET with one JSON 2xx response, and it must match exactly one of these styles. A token cursor is one query parameter among `cursor`, `after`, `page_token`, `next_token`, `continuation_token` and `marker`, paired with a next-cursor property among `next_cursor`, `next_page_token`, `next_token`, `next_marker`, `next_continuation_token`, `continuation_token` and `next` of the same type. An id cursor is one query parameter among `starting_after`, `after_id` and `after`, paired with a boolean `has_more` and a string `last_id` or `next_cursor` property. A `Link` list has a `Link` header declared on the response. An offset list has an `offset` query parameter with a `limit`, and a page-number list has a `page` query parameter with one of `per_page`, `page_size`, `size` or `limit`. A next URL list has no cursor parameter and a string property with `format: uri` or `uri-reference`, or one named `next_url`, `next_uri`, `nextlink` or `next_link`, that is optional or nullable. An operation that matches more than one style is ambiguous, and one that is streamed is never paged; either keeps only its plain method, and the generation log names it and says why. The `x-fern-pagination` and `x-speakeasy-pagination` (cursor type) extensions are read as hints. The generator option `paginationOverrides` (a JSON object that maps an operation id to `none` or to `{style, requestParam, responseProperty, itemsProperty, hasMoreProperty}`, where the style is `token`, `link`, `id`, `offset`, `page` or `body`) wins over a hint and over inference, and `pagination=off` switches the feature off.

### Custom HTTP client

The transport is `fetch`: `TransportOptions.builder().fetch(myFetch).fetchOptions({ ... })` routes every
request through your own `fetch` implementation. The SDK still applies its
timeout, user agent, redirect policy and header handling on top, and never
closes anything you supply. Combining a custom `fetch` with `proxy`,
`caCertPath` or `verifySsl(false)` throws a `TypeError` at `build()`, because
those belong on the `fetch` you provide.

### Testing your code

Every API group has an interface (`PetApiInterface`) and the client has one (`ClientInterface`), so a test can replace the SDK with a hand-written fake: no mocking library, no network.

```typescript file
import type {
  PetApiInterface,
  ProblemsApiInterface,
  StoreApiInterface,
  ClientInterface,
} from "petstore-client";

// Code under test takes the group interface, never the generated class.
function use(api: PetApiInterface): void {}

// A fake is any object of that shape; Partial keeps the operations you do not fake out of the way.
const calls: string[] = [];
const fake = {} as Partial<PetApiInterface> as PetApiInterface;
use(fake);

const client: ClientInterface = {
  pet: fake as unknown as PetApiInterface,
  problems: fake as unknown as ProblemsApiInterface,
  store: fake as unknown as StoreApiInterface,
  close: async () => {
    calls.push("close");
  },
};
void client;
```

A new operation in the API adds a member to the interface, so a fake typed as the whole interface stops compiling after you regenerate; type it as `Partial` of the interface and cast, as above, so only the operation you fake is written.

### Hooks and telemetry

A call hook is one small, optional object that runs around every operation call. Before the request is sent it can read and edit the request headers, and when the call ends it is told the status or the error. A client with no hook does nothing extra. The hook runs once per call, however many sends happen inside it (transport retries, the one replay after a 401), and also when you supply the transport.

```typescript file
import { BearerAuthenticator, Client, type CallHook } from "petstore-client";

const authenticator = new BearerAuthenticator(
  "https://api.example.com",
  "your-token",
);

const timing: CallHook = {
  start(info, headers) {
    headers["X-Caller"] = "billing";
    const started = performance.now();
    return {
      finish(outcome) {
        console.log(
          info.operationId,
          outcome.statusCode,
          performance.now() - started,
        );
      },
    };
  },
};

const client = new Client(authenticator, undefined, { callHook: timing });
```

`start` receives a `CallInfo` (`operationId` exactly as written in the spec, the upper-case `method`, the `urlTemplate`, the `url` without its query string and fragment, and the `serverAddress`) and the final request headers, which it may edit: an edit is on every send of the call. It returns an observer, or nothing. `finish` receives a `CallOutcome` (`statusCode`, 0 when no response arrived, the lower-cased `headers`, null when no response arrived, and the `error` the caller receives, or null) exactly once, whatever the outcome. An error thrown by `start` reaches the caller unchanged and nothing is sent; an error thrown by `finish` is swallowed. `chain(...hooks)` combines hooks: it starts them in the order given and finishes their observers in the reverse order. The hook goes on the `Configuration` builder (`.callHook(hook)`) or in the client options.

For OpenTelemetry, the `petstore-client/opentelemetry` entry point provides `createOpenTelemetryCallHook()`. It records one span named with the operation id (kind client), one `http.client.request.count` and one `http.client.request.duration` (seconds) per call, with `http.request.method`, `url.template`, `server.address`, `http.response.status_code` and `error.type` attributes, and writes `traceparent` itself. It is built on `@opentelemetry/api`, an optional peer dependency, and records nothing when no provider is registered. Pass `{ tracerProvider, meterProvider }` to use providers other than the global ones.

```typescript file
import { BearerAuthenticator, Client } from "petstore-client";
import { createOpenTelemetryCallHook } from "petstore-client/opentelemetry";

const authenticator = new BearerAuthenticator(
  "https://api.example.com",
  "your-token",
);

const traced = new Client(authenticator, undefined, {
  callHook: createOpenTelemetryCallHook(),
});
```

## Develop

```bash
npm install
```

## Test

```bash
npm test
```

## Tooling

The package ships with a formatter, linter and static analyser, all
runnable via the matching npm scripts:

```bash
npm run format     # prettier (uses .prettierrc.json)
npm run lint       # eslint 9 flat config (eslint.config.js)
npm run typecheck  # tsc --noEmit (strict mode)
npm run build      # tsc (emit to ./dist)
```

## Dependency audit

```bash
npm audit --omit=dev   # or: make audit
```

## Package

- Name: `petstore-client`
- Version: `1.0.0`
- License: Apache-2.0

## Caveats

### Decimal / `format: number` precision

A bare `type: number` and a `format: decimal` value, number- or
string-shaped, are a `Decimal` from the `decimal.js` package
(re-exported by this SDK as `Decimal`). The value is read from the
digits of the JSON text, never through a binary float, so
`12.345678901234567890123` keeps every digit, and it is written back in
plain notation (never with an exponent) with the decimal places it was
read with. A number-shaped field travels as a bare JSON number and a
string-shaped one as a JSON string. Use the exact operators
(`plus`, `times`, `eq`) for prices, balances and other money-typed
fields; build a value with `new Decimal('12.50')`.

JavaScript's only number type is a 64-bit IEEE-754 float, which can
exactly represent integers only up to `Number.MAX_SAFE_INTEGER`
(2^53 − 1 = 9 007 199 254 740 991). API responses containing a
`format: int64` value larger than this — Twitter / X IDs, Snowflake
IDs, Discord IDs, and similar 64-bit auto-increment IDs — are
**silently rounded** by `JSON.parse`.

If you call an endpoint that returns such an ID, treat the returned
number as opaque (do not do arithmetic on it) or fetch it as a string
when the API offers it. Fixing this language-level limitation would
require returning `bigint` instead of `number`, which breaks the
arithmetic operators (`+`, `<`, etc.) for every existing consumer.

Source: ECMA-262 §6.1.6.1.

### Optional versus nullable

A property that is optional and nullable cannot tell "absent" from "null" on the way out: a
`null` or `undefined` value is left out of the request body in both cases. Only a property
that is required and nullable is always sent, as `null` when it has no value. On the way in,
a `null` on the wire is kept as `null` and a missing key stays `undefined`. A required nullable query parameter is kept the same way: when its value is `null` the key is still sent, with an empty value (`?key=`).

### Unknown enum values and discriminators

Validation happens when a response is decoded, never when a model is built. A missing required field, a value of the wrong type and a discriminator value
that maps to no model fail loudly with a `SerializationError`.

An enum value the spec does not list decodes to the enum's catch-all member (`UNKNOWN_DEFAULT_OPEN_API`) instead of failing the
response, so a server that adds a value does not break a client built before it. The catch-all member has no
wire value, so writing it raises `SerializationError`. A discriminator value that maps to no variant still fails
loudly with `SerializationError`.

### Repeated response headers

`ApiResult.headers` is a map of lower-cased names to one string each. A header the server
repeats (`Set-Cookie`, `Link`, `Vary`) is joined into one value with `", "`; the SDK does not keep
a list of the separate values.

### Configuration and TransportOptions are separate

`Configuration` holds what belongs to the API (the base URL, the default headers, the client
signal) and `TransportOptions` holds what belongs to the connection (TLS, proxy, timeout, retries,
redirects, the request id). They stay two objects on purpose: the same API can be reached through
different transports and one transport can serve several APIs. Pass each to the place that takes it.

### Schema constraints

The constraints `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`,
`multipleOf`, `minItems` and `maxItems` are validated by the server. The
generated models do not check them: the class-transformer model layer has no
native constraint validation, so none of them is enforced client-side (only
Python validates them natively, through pydantic). If you want fast-fail
validation before the network round trip, plug in a JSON Schema validator
library.

`uniqueItems` arrays are sent in the natural order of the collection
(a `Set` keeps its insertion order), not sorted.

Constraint validation can be turned on with the `validateConstraints` configuration option, which is off by default. The SDK then checks every request body against the `minLength`, `maxLength`, `pattern`, `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf`, `minItems`, `maxItems`, `minProperties` and `maxProperties` keywords of its schema and of the models it holds before the request is sent, and refuses a body that breaks one with an argument error such as `Invalid request body: label must be at least 2 characters long`. Responses are never checked, and a pattern the regular expression engine cannot compile is not enforced.

## Not supported

### Webhooks and callbacks

This SDK is **client → server** only. Spec entries describing
server-initiated calls — OAS 3.1 top-level `webhooks` and OAS 3.0
per-operation `callbacks` — are intentionally skipped during code
generation. If you need to receive webhook deliveries, write the
handler yourself and use this SDK only to deserialize the incoming
payload (e.g. by reusing the relevant request-body model).

### Conditional-required validation (`dependentRequired` / `dependentSchemas`)

JSON Schema 2019-09 keywords for "if field X is present, field Y is
also required" are **not enforced** by this SDK. No mainstream
OpenAPI client codegen implements them. The server is the authoritative
validator; if you want client-side checking, plug in a JSON Schema
validator library for your language.

### XML bodies and `nodeType`

Request and response bodies are JSON (or form and multipart); XML bodies and the
`xml.nodeType` annotation are not generated.

### Link Objects

OpenAPI `links` on a response are ignored; follow-up calls are made explicitly.

### Mutual TLS

Client certificates (mutual TLS) are not configurable. `caCertPath` and
`verifySsl` cover server verification only.

### SOCKS proxies

`TransportOptionsBuilder.proxy()` accepts only `http://` and `https://` URLs.
Passing a `socks://`, `socks4://`, or `socks5://` scheme is rejected
at construction time with a clear error. SOCKS support would
require enabling extra dependencies / feature flags on the underlying
HTTP library in every one of the 12 SDKs we generate, with non-trivial
API divergence; we explicitly chose not to. If you need SOCKS, route
through a local HTTP-CONNECT bridge or configure it at the OS level.

### `LICENSE` file is not auto-emitted

The package manifest declares Apache-2.0, but no `LICENSE` / `LICENSE.md` file
is generated alongside the sources. Drop the appropriate license text
into the generated tree as part of your release pipeline before
publishing to a registry — most registries warn or block on a missing
file, and the GitHub license auto-detect cannot pick up a manifest-only
declaration.
