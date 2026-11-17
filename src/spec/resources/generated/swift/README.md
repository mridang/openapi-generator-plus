# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated Swift SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- **Swift 6.0+** (toolchain available at <https://swift.org/download/>)
- **macOS 12+** or a Linux distribution supported by the Swift toolchain
- [`swift-format`](https://github.com/apple/swift-format) (bundled with the
  Swift 6.0 toolchain) — formatter and basic linter
- [`swiftlint`](https://github.com/realm/SwiftLint) — opinionated linter
  used in addition to `swift-format`
- The Swift 6 compiler is invoked with `-warnings-as-errors` (see the
  `analyse` target) to provide static-analysis coverage on every build

## Installation

Add the package to your `Package.swift`, using the Git URL it is published at:

```swift
dependencies: [
    .package(url: "https://github.com/your-org/PetstoreClient", from: "1.0.0")
]
```

Then add `PetstoreClient` as a dependency of your target.

## Usage

Create a client from an authenticator and call the operations with `try await`
(every operation is an `async throws` function). `SKILLS.md` has the full
reference: every authentication scheme, transport options, error handling and
models.

```swift
import PetstoreClient

let client = try Client(host: "https://api.example.com", accessToken: "your-token")
```

## Build

```bash
swift build
```

## Test

```bash
swift test
```

## Format, lint and analyse

```bash
# Apply formatting in-place
make format

# Fail the build on any formatting or lint violation
make lint

# Static-analyse with strict concurrency and warnings-as-errors
make analyse
```

`swift-format` is configured via `.swift-format` and `swiftlint` via
`.swiftlint.yml` at the package root. Both files ship alongside the
generated sources and can be customised in place.

No generated source file disables a linter or a rule: `.swiftlint.yml`
relaxes nothing, and every `swift-format` rule the generated code cannot
satisfy is named in `.swift-format` — JSON carries no comments, so the
reason for each is recorded here.

| Rule | Why the generated code cannot satisfy it |
|---|---|
| `AlwaysUseLowerCamelCase` | Enum cases come verbatim from the OpenAPI document (`US`, `SANDBOX`, `V2`) and are the identifiers the other SDKs expose for the same schema. |
| `NoBlockComments` | Generated sources comment with `/* ... */` by convention across all twelve SDKs, and the generator's own spec fails the build on a `//` comment in a generated source. The two rules are mutually exclusive. |
| `AllPublicDeclarationsHaveDocumentation` | A schema or operation without a `description` has no documentation to emit, and inventing one would put generator prose in the caller's API docs. |
| `BeginDocumentationCommentWithOneLineSummary` | Doc comments are the document's own `description` text, reproduced unaltered; it is not the generator's to re-punctuate into a one-line summary. |
| `ValidateDocumentationComments` | Same reason: a document's `description` does not list the operation's Swift parameters. |
| `NoLeadingUnderscores` | An underscore-prefixed stored property is the backing store for a same-named computed property, which Swift has no other spelling for. |
| `OmitExplicitReturns`, `UseEarlyExits`, `UseWhereClausesInForLoops`, `NoEmptyLinesOpeningClosingBraces`, `AlwaysUseLiteralForEmptyCollectionInit` | Body shape is emitted uniformly from one template that must cover every document shape, so it cannot be rewritten per call site. |

## Package

- Name: `PetstoreClient`
- Version: managed via git tags (SwiftPM)
- License: MIT

The package follows semantic versioning: a patch release only fixes bugs, a minor
release only adds members, and a major release may change the public API. The
package builds with Swift 6.0 or newer and the platforms listed under
Requirements; a toolchain or platform that its vendor stops supporting is
dropped from the supported set in a major release.

## Cancellation

The SDK has no cancellation API of its own: cancel the Swift `Task` that made
the call and the in-flight `URLSession` task is cancelled with it, the call
throwing `CancellationError`. To bound how long a call can take, set the
request timeout on `TransportOptions`; a call that exceeds it fails with
`NetworkTimeoutError`. A call that is waiting for an OAuth2 token another call
is already fetching stops waiting at once; the fetch finishes for the others.

```swift
let task = Task { try await client.someApi.someOperation() }
task.cancel()
```

## Custom HTTP client

`TransportOptionsBuilder.session(_:)` sends every request through your own `URLSession`
you supply. The timeout, `User-Agent`, default headers and request id still
apply on top. You own the session: `close()` never invalidates it. A session
cannot be combined with the SDK's own `proxy`, `caCertPath`, `verifySsl(false)`,
`followRedirects(false)` or `maxRedirects`, because those are properties of the
session itself; `build()` throws a `ConfigurationError` naming the conflict.

## Raw responses, timeouts and request ids

Every operation has a `*WithHTTPInfo` variant that returns an `ApiResult`
holding the status code, the response headers, the raw body and the decoded
data; use it when you need to read a header.

`TransportOptionsBuilder.timeout(_:)` is in milliseconds (default 10000) and is
one end-to-end budget for the whole call: connection, every redirect hop and
the response body. When it elapses the call throws `NetworkTimeoutError`.
Unless `injectRequestID(false)` is set, the SDK sends a generated
`X-Request-ID` header on every request, and a header of the same name that you
supply always wins.

## Retries

`TransportOptionsBuilder.maxRetries(_:)` sets how many times a failed call is
retried (default 2; 0 disables retries). A call is retried after a network
error that happened before any response, and after a response with status 408,
409, 429 or 500 to 599 except 501. The wait starts at 0.5 s, doubles up to 8 s
and loses up to 25% to jitter; a `Retry-After` header (seconds or an HTTP date,
capped at 60 s) is used instead when the server sends one. The request timeout
is the single end-to-end budget: retries never extend it, and a wait that does
not fit in what is left is skipped. Every retry is a fresh request that asks the
authenticator for its headers again. A stream body and the OAuth2 token request
are never retried, and a request that timed out is not retried.

## Per-call options

Every operation takes an options struct (`<Operation>Options`) as its last
argument, and the argument can be left out. Next to the operation's own query,
header, form and cookie parameters it carries three per-call settings: `timeout`
(milliseconds, greater than zero) replaces the transport timeout for that call,
`maxRetries` replaces the retry limit (`0` turns retries off) and `headers` adds
request headers, sent after the default and authentication headers and replacing
a header of the same name. Anything left `nil` keeps the transport's setting. An
operation with no parameters and no security requirement still has the struct,
with only these three settings: `try await api.operation(options: OperationOptions(timeout: 2000))`.

## One credential per security scheme

An API that declares several security schemes does not need one authenticator that
fits them all. `Client(host:...)` takes one optional credential per scheme,
each named after its scheme, and any ``Authenticator`` will do:

```swift
let client = try Client(host: "https://api.example.com", petStoreBasic: authenticator)
```

| Scheme | Argument | Typical authenticator |
|---|---|---|
| `petStoreBasic` | `petStoreBasic` | `PetStoreBasicAuthenticator` |
| `adminBasic` | `adminBasic` | `AdminBasicAuthenticator` |
| `petStoreBearer` | `petStoreBearer` | `PetStoreBearerAuthenticator` |
| `serviceToken` | `serviceToken` | `ServiceTokenAuthenticator` |
| `apiKeyHeader` | `apiKeyHeader` | `ApiKeyHeaderAuthenticator` |
| `internalApiKey` | `internalApiKey` | `InternalApiKeyAuthenticator` |
| `apiKeyQuery` | `apiKeyQuery` | `ApiKeyQueryAuthenticator` |
| `sessionCookie` | `sessionCookie` | `SessionCookieAuthenticator` |
| `machineAuth` | `machineAuth` | `MachineAuthClientCredentialsAuthenticator` |
| `legacyAuth` | `legacyAuth` | `LegacyAuthPasswordAuthenticator` |
| `userAuth` | `userAuth` | `UserAuthAuthorizationCodeAuthenticator` |
| `browserAuth` | `browserAuth` | `BrowserAuthImplicitAuthenticator` |
| `ssoAuth` | `ssoAuth` | `SsoAuthAuthenticator` |

Each operation uses the credential of the first scheme its own spec lists that you
gave the client, so a credential never goes to an operation that does not accept its
scheme, and a call cannot pick another scheme: there is no per-call authenticator. An
operation declared without security (`security: []`) never carries a credential. An
operation none of whose schemes has a credential uses the single authenticator of
`Client(authenticator:)` when the client was built from one, and is sent
without a credential otherwise. A credential that makes HTTP calls of its own (OAuth2,
OpenID Connect) is given the client's transport, so it shares its proxy, TLS and
timeout settings.

## Closing the client

`client.close()` is graceful and idempotent. It marks the client closed at once: a
request made afterwards throws `ConfigurationError.invalidState` with the text
`ApiClient has been closed and can no longer send requests`. A request already in
flight finishes, retries included, and the `URLSession` the SDK built is released when
the last of them ends (at once when none is running). A second `close()` does nothing,
and a `URLSession` you supplied is never invalidated.

## Cookie parameters

A cookie parameter is sent exactly as you pass it when every character is an RFC 6265
cookie-octet: printable ASCII except space, double quote, comma, semicolon and
backslash (a percent sign is allowed). Any other character throws
`ConfigurationError.invalidArgument` before a request is sent, so percent-encode a
value that needs it yourself; the SDK never encodes a cookie value for you. A cookie
declared with `content:` holds a typed value, which the SDK serialises to JSON and
percent-encodes.

## Error details

An `ApiError` and every subclass carry `requestMethod` and `requestPath` (the
path without its query string) next to `statusCode` and `responseBody`, and the
message names them: `API returned status code 404 for GET /items/7`. The query
string is left out so a key or token sent in it never reaches a log.

## Immutable models

Model properties are `let`, so a model is never changed after it is built. Copy
it with `copy(...)`, which replaces the properties you pass and keeps the rest:
`let next = item.copy(name: "x")`. A bare `nil` also means "keep"; to clear an
optional property pass `.some(nil)`. The copy is shallow, so an array or
dictionary property still holds the same elements. A union (`oneOf`) wrapper is
immutable too: build it with `Wrapper.fromVariant(value)` or `Wrapper(value)`
and read it back with `isVariant` and `asVariant`.

## Building a model checks nothing

A model's initializer never validates and never throws: a value the schema would
refuse is still a value you can build, and an unknown enum string builds no enum at
all (`Status(rawValue: "x")` is `nil`). Checking happens when the SDK decodes a payload: an
unknown enum string, a missing required property or a `format: uri` that is not an
absolute URI throws `SerializationError`.

## Several success responses

An operation that declares more than one 2xx response with different schemas
returns a union, and the body is decoded as the schema of the status that
arrived: `200` as the first, `201` as the second. Ask the union which one it
holds with its `is...` and `as...` members.

## Credentials from the environment

`Client.fromEnvironment()` reads the host from `SWAGGER_PETSTORE_OPEN_API_3_0_HOST` and
the bearer token from `SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN`, once, when the client is built; an empty
value counts as unset. The `host`, `token` and `tokenProvider` arguments win over the
environment, and a `tokenProvider` callback is called before every request, so a token
refreshed elsewhere is picked up. A missing host or credential fails the call that
builds the client with `ConfigurationError.invalidArgument`.

Each security scheme's authenticator reads its own credentials the same way: the
argument you pass wins, else the environment variable below, read once when the
authenticator is built (an empty value counts as unset). A credential found in neither
place throws `ConfigurationError.invalidArgument` there, before any request. The
variable names start with `SWAGGER_PETSTORE_OPEN_API_3_0`; the `envVarPrefix` generator option changes it.

| Scheme | Credential | Environment variable |
|---|---|---|
| `adminBasic` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` |
| `adminBasic` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` |
| `apiKeyHeader` | `apiKey` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY` |
| `apiKeyQuery` | `apiKey` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY` |
| `browserAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID` |
| `internalApiKey` | `apiKey` | `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY` |
| `legacyAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID` |
| `legacyAuth` | `clientSecret` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET` |
| `legacyAuth` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME` |
| `legacyAuth` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD` |
| `machineAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID` |
| `machineAuth` | `clientSecret` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET` |
| `petStoreBasic` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME` |
| `petStoreBasic` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD` |
| `petStoreBearer` | `token` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN` |
| `serviceToken` | `token` | `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN` |
| `sessionCookie` | `apiKey` | `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY` |
| `ssoAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID` |
| `ssoAuth` | `clientSecret` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET` |
| `ssoAuth` | `redirectUri` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI` |
| `userAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID` |
| `userAuth` | `clientSecret` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET` |
| `userAuth` | `redirectUri` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI` |

## Response compression

`URLSession` negotiates `gzip` and `deflate` and decodes the body itself, so the
body you read is always the decoded one. The SDK never sets `Accept-Encoding`
(setting it would switch the decoding off), and a body it finds still gzip
encoded after the transport is decoded by the SDK.

## Logging

The SDK writes no logs. To see requests and responses, pass your own `URLSession`
through `TransportOptionsBuilder.session(_:)` with a `URLProtocol` that records
them, or wrap the `ApiClient` you inject.

## Optional versus nullable

A property that is optional is `T?` and `nil` means it was absent. A property that
is optional and also `nullable: true` collapses into the same `T?`: an absent key
and an explicit `null` read the same, and neither is written back. A required
nullable property keeps its key and is written as `null`.

## Unknown enum values and discriminators

An enum value, or a `oneOf` discriminator value, that the SDK does not know fails
loudly with a decoding error (a `SerializationError` at the call) instead of being
kept as a raw string. Regenerate the SDK when the API adds a value.

## Response headers

`ApiResult.headers` and `ApiError.responseHeaders` are a plain dictionary with
lower-cased names. A header the server repeats (`Link`, `Set-Cookie`) is joined
into one value with `, ` in the order it arrived, so a `Set-Cookie` value is not
parsed for you.

## Streaming

Request and response bodies are read and sent whole; there is no streaming of a
response body. Inject your own transport through `TransportOptionsBuilder.session(_:)`
or `ApiClient` when you need to stream.

## Configuration and transport options

`Configuration` holds what belongs to the API (the base URL and default headers)
and `TransportOptions` holds how bytes travel (proxy, TLS, timeouts, retries). They
stay separate: the transport is shared by every API group, the configuration is
per group.

## Caveats

### Decimal / `format: number` precision

Foundation's `JSONDecoder` parses JSON numbers into `Double` for
`Decodable` model fields typed as `Double`/`Float`. `format: decimal`
/ `format: number` values are therefore stored as `Double`, so
monetary values lose exact decimal representation. `0.1 + 0.2` in
Swift is `0.30000000000000004`.

Do not do arithmetic on prices, balances, or other money-typed
fields. If you need exact decimal arithmetic, parse the raw response
body yourself and decode the field into a Foundation `Decimal` (declare
the property as `Decimal` in your own `Decodable` type).

`format: int64` is unaffected — Swift's `Int` is 64-bit on all
supported platforms and represents the full range without loss.

## Not supported

### Webhooks and callbacks

This SDK is **client → server** only. Spec entries describing
server-initiated calls — OAS 3.1 top-level `webhooks` and OAS 3.0
per-operation `callbacks` — are intentionally skipped during code
generation. If you need to receive webhook deliveries, write the
handler yourself and use this SDK only to deserialize the incoming
payload (e.g. by reusing the relevant request-body model).

### XML bodies and `nodeType` (#70, #88)

Only JSON, form, multipart and binary bodies are generated. An operation that
offers only `application/xml` has no usable request or response body; encode
and decode the XML yourself.

### Link Objects (#78)

Response `links` are ignored; build the follow-up call yourself.

### Mutual TLS security schemes (#90)

A `mutualTLS` security scheme is not generated. Configure the client
certificate on the `URLSession` you pass to
`TransportOptionsBuilder.session(_:)`.

### Conditional-required validation (`dependentRequired` / `dependentSchemas`)

JSON Schema 2019-09 keywords for "if field X is present, field Y is
also required" are **not enforced** by this SDK. No mainstream
OpenAPI client codegen implements them. The server is the authoritative
validator; if you want client-side checking, plug in a JSON Schema
validator library for your language.

### Schema constraints

The constraints `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`,
`multipleOf`, `minItems` and `maxItems` are validated by the server. The
generated models do not check them: a model is a `Codable` struct, and Swift's
decoder enforces only the declared types (a quoted number, a fractional value
for an integer or an out-of-range integer is rejected), not the value
constraints. The one value check the models make themselves is that a
`format: uri` property is an absolute URI. If you want fast-fail validation
before the network round trip, plug in a JSON Schema validator library.

`uniqueItems` arrays are sent in the collection's natural order and are not
sorted.

### SOCKS proxies

`TransportOptionsBuilder.proxy(_:)` accepts only `http://` and `https://` URLs.
Passing a `socks://`, `socks4://`, or `socks5://` scheme is rejected
when the proxy is set, with a `ConfigurationError.invalidProxy`. SOCKS support would
require enabling extra dependencies / feature flags on the underlying
HTTP library in every one of the 12 SDKs we generate, with non-trivial
API divergence; we explicitly chose not to. If you need SOCKS, route
through a local HTTP-CONNECT bridge or configure it at the OS level.

### `LICENSE` file is not auto-emitted

The package manifest declares MIT, but no `LICENSE` / `LICENSE.md` file
is generated alongside the sources. Drop the appropriate license text
into the generated tree as part of your release pipeline before
publishing to a registry — most registries warn or block on a missing
file, and the GitHub license auto-detect cannot pick up a manifest-only
declaration.
