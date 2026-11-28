# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Install the SDK as a dependency in your project. If published to npm, use:

```bash
npm install petstore-client
```

If using locally, add it as a file dependency in your `package.json`.

## Quick Start

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

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```typescript statements
import { BearerAuthenticator, Client } from "petstore-client";

const authenticator = new BearerAuthenticator(
  "https://api.example.com",
  "your-token",
);
const client = new Client(authenticator);
```

### Basic Auth

```typescript statements
import { BasicAuthenticator, Client } from "petstore-client";

const authenticator = new BasicAuthenticator(
  "https://api.example.com",
  "username",
  "password",
);
const client = new Client(authenticator);
```

### API Key

```typescript statements
import { ApiKeyAuthenticator, ApiKeyLocation, Client } from "petstore-client";

const authenticator = new ApiKeyAuthenticator(
  "https://api.example.com",
  "key-name",
  "key-value",
  ApiKeyLocation.HEADER,
);
const client = new Client(authenticator);
```

### OAuth2 Client Credentials

```typescript statements
import { OAuth2ClientCredentialsAuthenticator, Client } from "petstore-client";

const authenticator = new OAuth2ClientCredentialsAuthenticator(
  "https://api.example.com",
  "client-id",
  "client-secret",
  "https://auth.example.com/token",
  ["read"],
);
const client = new Client(authenticator);
```

### OAuth2 Authorization Code

```typescript statements
import { OAuth2AuthorizationCodeAuthenticator, Client } from "petstore-client";

const authenticator = new OAuth2AuthorizationCodeAuthenticator(
  "https://api.example.com",
  "client-id",
  "client-secret",
  "https://auth.example.com/authorize",
  "https://auth.example.com/token",
  "https://app.example.com/callback",
  ["read"],
);
const client = new Client(authenticator);
```

### OAuth2 Password

```typescript statements
import { OAuth2PasswordAuthenticator, Client } from "petstore-client";

const authenticator = new OAuth2PasswordAuthenticator(
  "https://api.example.com",
  "client-id",
  "client-secret",
  "https://auth.example.com/token",
  "username",
  "password",
  ["read"],
);
const client = new Client(authenticator);
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```typescript statements
import { OAuth2ImplicitAuthenticator, Client } from "petstore-client";

const authenticator = new OAuth2ImplicitAuthenticator(
  "https://api.example.com",
  "client-id",
  "https://auth.example.com/authorize",
  ["read"],
);
authenticator.setAccessToken("your-access-token");
const client = new Client(authenticator);
```

### OpenID Connect

```typescript statements
import { OpenIdConnectAuthenticator, Client } from "petstore-client";

const authenticator = new OpenIdConnectAuthenticator(
  "https://api.example.com",
  "https://auth.example.com/.well-known/openid-configuration",
  "client-id",
  "client-secret",
  "https://app.example.com/callback",
  ["openid"],
);
const client = new Client(authenticator);
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators resolve the access token by making an HTTP call to the token endpoint, which happens before the request carrying it is sent.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Renewal on 401

When the API answers `401` to a call authenticated by OAuth2 or OpenID Connect, the authenticator drops the cached access token (`invalidateAccessToken()`, the refresh token is kept), obtains a new one and the call is replayed once. A second `401` is thrown as the usual `UnauthorizedError`. A single-use or multipart body is not replayed, and Bearer, Basic and API key authenticators never replay.

#### PKCE

The Authorization Code and OpenID Connect `buildAuthorizationUrl()` adds a PKCE (RFC 7636) `code_challenge` with `code_challenge_method=S256` and keeps the matching 43-character verifier, which `exchangeCode()` sends as `code_verifier`. Building the URL again starts a new verifier.

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed; tune your authorization server's `expires_in` if it is too tight.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethod.Body` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod.Basic` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```typescript statements
import {
  ClientAuthMethod,
  OAuth2ClientCredentialsAuthenticator,
} from "petstore-client";

const authenticator = new OAuth2ClientCredentialsAuthenticator(
  "https://api.example.com",
  "client-id",
  "client-secret",
  "https://auth.example.com/token",
  [],
  ClientAuthMethod.Basic,
);
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `Servers` class exposes each as a `ServerConfiguration` static property (e.g., `Servers.SERVER_0`, `Servers.SERVER_1`, ...) plus a `Servers.ALL` array. Pass the desired server's URL to the client:

```typescript statements
import { Client, Servers } from "petstore-client";

const client = Client.withToken(Servers.SERVER_0.getUrl(), "your-token");
```

## Testing

To stub responses, pass your own `fetch` through `TransportOptions` and return a canned `Response`; to control credentials, substitute a fake `Authenticator` that returns a known header map. Together they let a test run without a network.

```typescript statements
import { Client, type Authenticator } from "petstore-client";

const fake: Authenticator = {
  getHost(): string {
    return "https://api.example.com";
  },
  getAuthHeaders(): Record<string, string> {
    return { Authorization: "Bearer test-token" };
  },
  getAuthHeadersAsync(): Promise<Record<string, string>> {
    return Promise.resolve(this.getAuthHeaders());
  },
  getQueryParams(): Record<string, string> {
    return {};
  },
  getCookieParams(): Record<string, string> {
    return {};
  },
};

const client = new Client(fake);
```

Stubbing the transport so that every call receives a canned response:

```typescript file
import { Client, TransportOptions, type Authenticator } from "petstore-client";

function stubbedClient(fake: Authenticator): Client {
  const stub: typeof fetch = async () =>
    new Response("{}", {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  const transport = TransportOptions.builder().fetch(stub).build();
  return new Client(fake, transport);
}
```

## Error Handling

Every error the SDK raises derives from `OpenAPIError`. The error hierarchy is:

- `OpenAPIError` (root)
  - `ApiError` (an HTTP response came back, or none did)
    - `ClientError` (4xx)
      - `BadRequestError` (400)
      - `UnauthorizedError` (401)
      - `ForbiddenError` (403)
      - `NotFoundError` (404)
      - `ConflictError` (409)
      - `UnprocessableEntityError` (422)
      - `TooManyRequestsError` (429)
      - `PaymentRequiredError` (402)
      - `MethodNotAllowedError` (405)
      - `RequestTimeoutError` (408)
      - `GoneError` (410)
      - `PayloadTooLargeError` (413)
      - `UnsupportedMediaTypeError` (415)
      - `LockedError` (423)
    - `ServerError` (5xx)
      - `InternalServerError` (500)
      - `NotImplementedError` (501)
      - `BadGatewayError` (502)
      - `ServiceUnavailableError` (503)
      - `GatewayTimeoutError` (504)
    - `NetworkError` (no HTTP response, status 0)
      - `NetworkTimeoutError` (the request timed out, status 0)
  - `SerializationError` (a request could not be encoded or a response could not be decoded)
  - `OAuth2ServerError` (the token endpoint answered with a non-2xx status)
  - `OAuth2TokenError` (the token endpoint answered 2xx with an unusable body)

```typescript file
import {
  ApiError,
  ClientError,
  Client,
  NotFoundError,
  ServerError,
  OpenAPIError,
  Pet,
} from "petstore-client";

async function addPetOrReport(client: Client, pet: Pet): Promise<void> {
  try {
    const result = await client.pet.addPet(pet);
    console.log(result);
  } catch (error) {
    if (error instanceof NotFoundError) {
      console.log(`Not found: ${error.message}`);
    } else if (error instanceof ClientError) {
      console.log(`Client error ${error.statusCode}: ${error.message}`);
    } else if (error instanceof ServerError) {
      console.log(`Server error: ${error.message}`);
    } else if (error instanceof ApiError) {
      console.log(`API error: ${error.message}`);
    } else if (error instanceof OpenAPIError) {
      console.log(`SDK error: ${error.message}`);
    } else {
      throw error;
    }
  }
}
```

## Configuration

### Custom Transport Options

```typescript statements
import { BearerAuthenticator, Client, TransportOptions } from "petstore-client";

const authenticator = new BearerAuthenticator(
  "https://api.example.com",
  "your-token",
);
const transport = TransportOptions.builder()
  .proxy("http://proxy:3128")
  .timeout(5000)
  .build();

const client = new Client(authenticator, transport);
```

### Retries

`TransportOptions.builder().maxRetries(n)` sets the automatic retries (default `2`, `0` disables them). A request is retried after a network error before any response and after a `408`, `409`, `429` or `5xx` other than `501`, waiting 0.5 s doubling to 8 s with up to 25% jitter or the server's `Retry-After` (at most 60 s). The retries share the single `timeout` and stop when the call is cancelled; each one rebuilds the headers and asks the authenticator again; a single-use `BodySource` is never retried.

`TransportOptions.builder().maxResponseBytes(n)` bounds a buffered response body to `n` bytes (off by default): a body that passes it, on the wire or once decompressed, fails with `ResponseTooLargeError` and is never retried. A streamed response is not bounded by it.

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

Pagination covers the token cursor, id cursor, offset, page number, `Link` header and next URL in the body styles. The override option and the vendor hints are as described in the readme.

### Custom HTTP client

```typescript statements
import { TransportOptions } from "petstore-client";

const transport = TransportOptions.builder()
  .fetch((input, init) => fetch(input, init))
  .build();
```

`TransportOptions.builder().fetch(myFetch).fetchOptions({ keepalive: true })` sends every request through your own `fetch`. SDK defaults (timeout, user agent, redirect policy) still apply; the SDK never closes what you supply, and a custom `fetch` combined with `proxy`, `caCertPath` or `verifySsl(false)` throws a `TypeError`.

### Per-call options

Every operation takes a trailing `RequestOptions` object: `{ signal?, timeout?, maxRetries?, headers? }`. `timeout` (positive milliseconds, or `null` to wait indefinitely) and `maxRetries` (a non-negative integer) replace the transport's values for that call; `headers` are extra request headers that replace a client default or operation header of the same name but never the authentication headers. An invalid value throws a `TypeError` before the request is sent.

### Credentials

An authenticator without an explicit credential reads an environment variable once, when it is built (an empty variable is unset): `SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN` (`Client.withToken`, `BearerAuthenticator`), `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY` (`ApiKeyAuthenticator`), `SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME` and `SWAGGER_PETSTORE_OPEN_API_3_0_PASSWORD` (`BasicAuthenticator`). The authenticator of a security scheme reads the variable named for that scheme: `PetStoreBasicAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD`; `AdminBasicAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD`; `ArchiveDigestAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_ARCHIVE_DIGEST_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_ARCHIVE_DIGEST_PASSWORD`; `PetStoreBearerAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN`; `ServiceTokenAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN`; `ApiKeyHeaderAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY`; `InternalApiKeyAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY`; `ApiKeyQueryAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY`; `SessionCookieAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY`; `MachineAuthClientCredentialsAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID`, `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET`; `LegacyAuthPasswordAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID`, `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET`, `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD`; `UserAuthAuthorizationCodeAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID`, `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET`, `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI`; `BrowserAuthImplicitAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID`; `DeviceAuthDeviceAuthorizationAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_DEVICE_AUTH_CLIENT_ID`; `SsoAuthAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID`, `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET`, `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI`; the OAuth 2.0 and OpenID Connect authenticators take their credentials as arguments only. A missing credential throws a `TypeError` when the authenticator is built. `BearerAuthenticator` and `withToken` also take a function returning the token (or a promise of it), called for every request.

### Credentials per security scheme

`Client` takes one optional credential for each security scheme, named after the scheme: `petStoreBasic`, `adminBasic`, `archiveDigest`, `petStoreBearer`, `serviceToken`, `apiKeyHeader`, `internalApiKey`, `apiKeyQuery`, `sessionCookie`, `machineAuth`, `legacyAuth`, `userAuth`, `browserAuth`, `deviceAuth`, `ssoAuth`. Pass an object in place of the authenticator: `new Client({ petStoreBasic: schemeAuthenticator })`. An operation uses the credential of the first scheme in its own security requirement that was given; there is no per-call override. An optional `authenticator` in the same object serves an operation no scheme credential matches, and an operation that needs no authentication never sends a credential. A client with no credential at all throws a `TypeError`.

### Cookie parameters

A cookie parameter is sent raw when every character is an RFC 6265 cookie-octet (printable ASCII except space, double quote, comma, semicolon and backslash); otherwise the call throws a `TypeError` before sending, so percent-encode the value first.

### Closing

`await client.close()` (or `await using client = ...`) is graceful and idempotent: new calls fail with `ApiClient has been closed and can no longer send requests`, a call already running finishes, the connection pool the SDK built is released when the last one completes, and a `fetch` you supplied is never closed. Streams that are still open are cancelled. Cancel one call with its own `signal`.

### Cancellation

Every operation takes a trailing `RequestOptions` object (`{ signal?: AbortSignal }` plus the per-call options above). A client-level signal goes in the third constructor argument and is merged with the per-call signal; either one cancels the call. Cancellation rejects with the signal's reason (an `AbortError` by default), never `NetworkTimeoutError`.

An operation with optional parameters takes them before the signal: pass `undefined` to skip them.

```typescript file
import { Client, type Authenticator } from "petstore-client";

async function getDefaultsCancelled(
  client: Client,
  authenticator: Authenticator,
): Promise<void> {
  const controller = new AbortController();
  const perCall = client.store.getDefaults({ signal: controller.signal });
  const cancellable = new Client(authenticator, undefined, {
    signal: controller.signal,
  });
  controller.abort();
  await perCall;
}
```

### Raw responses, timeouts and the request id

Each operation has a `...WithHttpInfo` variant returning `ApiResult<T>` (`statusCode`, `data`, `rawBody`, `headers`). `timeout` is one end-to-end budget in positive milliseconds (default 60000; `null` waits indefinitely). `TransportOptions.builder().injectRequestId(true)` adds an `X-Request-ID` header to each request unless you set one.

### Hooks and telemetry

A call hook is one small, optional object that runs around every operation call. Before the request is sent it can read and edit the request headers, and when the call ends it is told the status or the error. A client with no hook does nothing extra. The hook runs once per call, however many sends happen inside it (transport retries, the one replay after a 401), and also when you supply the transport.

`CallHook.start(info, headers)` may edit the headers and returns a `CallObserver` or nothing; `CallObserver.finish(outcome)` runs exactly once with `statusCode` (0 when no response arrived), the lower-cased `headers` (null when none arrived) and the `error`. Set it with `new Client(authenticator, undefined, { callHook })` or `Configuration.builder().callHook(hook)`; combine hooks with `chain(...hooks)`. `createOpenTelemetryCallHook()` from `petstore-client/opentelemetry` records the span, the count and the duration histogram.

## API Methods

Each API group is exposed as a typed property on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are asynchronous; call them with `await`.

## Models

Models are generated as TypeScript classes in the `src/models/` directory. Every field is `readonly`; `model.with({ field: value })` returns a copy with the fields replaced. A model constructor validates nothing; a missing required field or a wrong type fails when a response is decoded, and an unknown enum string decodes to the catch-all member `UNKNOWN_DEFAULT_OPEN_API`. A `format: date` field or parameter is a plain `YYYY-MM-DD` string (a `Date` is accepted on input and sent as its UTC date); `format: date-time` is a `Date`.

```typescript file
import { Account } from "petstore-client";

function copyOf(model: Account): Account {
  return model.with({});
}
```

## Binary / File Uploads

A raw binary request body is a `BodySource` (see Streaming). Multipart file parameters are typed as `Buffer | Blob` (a `File` is a `Blob`). A binary response body is returned as a `ByteStream`, a Web `ReadableStream` of `Uint8Array` (see Streaming).

## Comment Style

Never place a comment on the same line as code. Use block comments (`/* ... */`); JSDoc (`/** ... */`) is fine.

```good
/* This explains the logic */
const x = 1;
```

```bad
// This explains the logic
const x = 1;
```
