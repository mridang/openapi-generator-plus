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

When the API answers `401` to a call authenticated by OAuth2 or OpenID Connect, the authenticator drops the cached access token (`invalidateAccessToken()`, the refresh token is kept), obtains a new one and the call is replayed once. A second `401` is thrown as the usual `UnauthorizedError`. A multipart body is not replayed, and Bearer, Basic and API key authenticators never replay.

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

`TransportOptions.builder().maxRetries(n)` sets the automatic retries (default `2`, `0` disables them). A request is retried after a network error before any response and after a `408`, `409`, `429` or `5xx` other than `501`, waiting 0.5 s doubling to 8 s with up to 25% jitter or the server's `Retry-After` (at most 60 s). The retries share the single `timeout` and stop when the call is cancelled; each one rebuilds the headers and asks the authenticator again.

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

An authenticator without an explicit credential reads an environment variable once, when it is built (an empty variable is unset): `SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN` (`Client.withToken`, `BearerAuthenticator`), `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY` (`ApiKeyAuthenticator`), `SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME` and `SWAGGER_PETSTORE_OPEN_API_3_0_PASSWORD` (`BasicAuthenticator`). The authenticator of a security scheme reads the variable named for that scheme: `PetStoreBasicAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD`; `AdminBasicAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD`; `PetStoreBearerAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN`; `ServiceTokenAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN`; `ApiKeyHeaderAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY`; `InternalApiKeyAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY`; `ApiKeyQueryAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY`; `SessionCookieAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY`; `MachineAuthClientCredentialsAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID`, `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET`; `LegacyAuthPasswordAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID`, `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET`, `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME`, `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD`; `UserAuthAuthorizationCodeAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID`, `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET`, `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI`; `BrowserAuthImplicitAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID`; `SsoAuthAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID`, `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET`, `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI`; the OAuth 2.0 and OpenID Connect authenticators take their credentials as arguments only. A missing credential throws a `TypeError` when the authenticator is built. `BearerAuthenticator` and `withToken` also take a function returning the token (or a promise of it), called for every request.

### Credentials per security scheme

`Client` takes one optional credential for each security scheme, named after the scheme: `petStoreBasic`, `adminBasic`, `petStoreBearer`, `serviceToken`, `apiKeyHeader`, `internalApiKey`, `apiKeyQuery`, `sessionCookie`, `machineAuth`, `legacyAuth`, `userAuth`, `browserAuth`, `ssoAuth`. Pass an object in place of the authenticator: `new Client({ petStoreBasic: schemeAuthenticator })`. An operation uses the credential of the first scheme in its own security requirement that was given; there is no per-call override. An optional `authenticator` in the same object serves an operation no scheme credential matches, and an operation that needs no authentication never sends a credential. A client with no credential at all throws a `TypeError`.

### Cookie parameters

A cookie parameter is sent raw when every character is an RFC 6265 cookie-octet (printable ASCII except space, double quote, comma, semicolon and backslash); otherwise the call throws a `TypeError` before sending, so percent-encode the value first.

### Closing

`await client.close()` (or `await using client = ...`) is graceful and idempotent: new calls fail with `ApiClient has been closed and can no longer send requests`, a call already running finishes, the connection pool the SDK built is released when the last one completes, and a `fetch` you supplied is never closed. Cancel one call with its own `signal`.

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

Each operation has a `...WithHttpInfo` variant returning `ApiResult<T>` (`statusCode`, `data`, `rawBody`, `headers`). `timeout` is one end-to-end budget in positive milliseconds (default 10000; `null` waits indefinitely). `TransportOptions.builder().injectRequestId(true)` adds an `X-Request-ID` header to each request unless you set one.

### Hooks and telemetry

A call hook is one small, optional object that runs around every operation call. Before the request is sent it can read and edit the request headers, and when the call ends it is told the status or the error. A client with no hook does nothing extra. The hook runs once per call, however many sends happen inside it (transport retries, the one replay after a 401), and also when you supply the transport.

`CallHook.start(info, headers)` may edit the headers and returns a `CallObserver` or nothing; `CallObserver.finish(outcome)` runs exactly once with `statusCode` (0 when no response arrived), the lower-cased `headers` (null when none arrived) and the `error`. Set it with `new Client(authenticator, undefined, { callHook })` or `Configuration.builder().callHook(hook)`; combine hooks with `chain(...hooks)`. `createOpenTelemetryCallHook()` from `petstore-client/opentelemetry` records the span, the count and the duration histogram.

## API Methods

Each API group is exposed as a typed property on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are asynchronous; call them with `await`.

## Models

Models are generated as TypeScript classes in the `src/models/` directory. Every field is `readonly`; `model.with({ field: value })` returns a copy with the fields replaced. A model constructor validates nothing; a missing required field, a wrong type or an unknown enum string fails when a response is decoded. A `format: date` field or parameter is a plain `YYYY-MM-DD` string (a `Date` is accepted on input and sent as its UTC date); `format: date-time` is a `Date`.

```typescript file
import { Account } from "petstore-client";

function copyOf(model: Account): Account {
  return model.with({});
}
```

## Binary / File Uploads

File upload parameters are typed as `Buffer | Blob` (a `File` is a `Blob`). Binary response bodies are returned as `Buffer`.

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
