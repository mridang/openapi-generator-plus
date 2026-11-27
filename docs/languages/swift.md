# Swift

The `swift-plus` generator (`BetterSwiftCodegen`) produces a Swift package. Models are `Codable` structs, each API tag gets an `async throws` class, and `DefaultApiClient` runs on `URLSession`. There are no third-party runtime dependencies. For the rules shared by all twelve languages see [how it works](../how-it-works.md), for the type table across languages see [types](../types.md), and for settled cross-language choices see [decisions](../decisions.md).

## Versions and support policy

The generated `Package.swift` uses `swift-tools-version: 6.0` and declares these platform minimums: macOS 13, iOS 16, tvOS 16 and watchOS 9, the first releases with the standard library `Duration` that `format: duration` uses (D145). The package builds in the Swift 5 language mode (`swiftLanguageModes: [.v5]`), because the authenticator and `AnyCodable` classes are not yet clean under Swift 6 strict concurrency. The generated README says Swift 6.0 or newer.

Swift has no end-of-life policy for toolchains. Tools version 6.0 is the oldest the generated code and its Swift Testing based tests need, so it is the floor. A toolchain or platform that its vendor stops supporting is dropped in a major release. The generator builds and lints the golden package in a Swift 6.2 image.

On Linux the package uses `FoundationNetworking`.

## Type mapping

| OpenAPI type and format | Swift type | Notes |
|---|---|---|
| `string` | `String` | Also used for `email`, `hostname`, `ipv4`, `ipv6`, `password` and unknown formats. |
| `string` / `date` | `String` | Foundation has no calendar-date type. The value is not validated. |
| `string` / `date-time` | `Date` | An instant. The original UTC offset is not kept. |
| `string` / `time` | `String` | Foundation has no wall-clock time type. |
| `string` / `duration` | `Duration` | The standard library type, exact to the nanosecond. The wire form is the protobuf-JSON string such as `"3600s"`, converted by `parseProtobufDuration` and `formatProtobufDuration`. |
| `string` / `uuid` | `UUID` | |
| `string` / `byte` | `Data` | Base64 through the standard coder. |
| `string` / `binary`, file bodies | `Data` for a request body; `ByteStream` (an `AsyncSequence` of `Data`) for a response | the response body is read as you consume it, see Streaming |
| `string` / `uri` | `String` | |
| `integer` / `int32` | `Int` | Not narrowed to `Int32`, so range is not checked. |
| `integer` / `int64` | `Int64` | |
| `integer` with no format | `Int64` | The same as `int64`. |
| `number` / `float` | `Float` | |
| `number` / `double`, `number` with no format | `Double` | |
| `number` / `decimal` | `Double` | Lossy for exact decimal values such as money. |
| `boolean` | `Bool` | |
| `array` | `[T]` | |
| `array` with `uniqueItems` | `Set<T>` | The order on the wire is unspecified. |
| `object` with `additionalProperties` | `[String: T]` | Key order is not kept.. `T` is the declared value type; a free-form bucket keeps the any-type |
| free-form `object`, any type | `AnyCodable` | An SDK type, because `Codable` has no `Any`. |
| nullable | `Optional<T>` | |
| `enum` | `enum: String, Codable, Sendable, CaseIterable` | Integer enum members are named `number1`, `numberMinus1` and so on. |
| `oneOf`, `anyOf` | wrapper with `actualInstance` | Read the value with the generic `actualInstance(as:)`. |

Recursive models use an `@Indirect` wrapper. Model names that would shadow Swift types become `ModelError` and `ModelResult`.

## Dependencies

The library target has no package dependencies. It uses Foundation only. The manifest also lists packages that are used for tests and documentation:

| Package | Version | Used for |
|---|---|---|
| `testcontainers-swift` | from 1.0.5 | Test fixtures |
| `opentelemetry-swift` | from 1.13.0 | Test only: trace-context tests |
| `swift-docc-plugin` | from 1.3.0 | Building documentation |

Response bodies are decoded by `URLSession`, with a bundled inflater for gzip and deflate on Linux.

## Options

| Option | Default | What it does |
|---|---|---|
| `packageName` | none | Name of the generated package. |
| `packageVersion` | `1.0.0` | Version of the package. |
| `repositoryUrl` | none | Source repository URL, listed in the README Package section. |
| `homepageUrl` | none | Project homepage, listed in the README Package section. |
| `authors` | none | Package authors, listed in the README Package section. |
| `documentationUrl` | none | Documentation URL, listed in the README Package section. |
| `issueTrackerUrl` | none | Issue tracker URL, listed in the README Package section. |
| `envVarPrefix` | the API title without a trailing `SDK`, in upper snake case | Prefix of the environment variables the client and the security scheme authenticators read credentials from. The same option and the same default in every language. |

## Idiom notes

- Every operation takes an options struct (`<Operation>Options`) as its last argument, and the argument can be left out. An operation with no query, header, form or cookie parameter and no security requirement has one too, carrying only `timeout` (milliseconds), `headers` and `maxRetries`, so each call can set them. The per-call timeout, like the transport one (default 60 seconds), is one end-to-end deadline that covers connecting, every redirect hop and the whole body read of a buffered call (D137).
- The client takes one optional credential per security scheme, named after the scheme: `Client(host:petStoreBearer:apiKeyHeader:...transportOptions:)`, each argument any `Authenticator`. An operation uses the credential of the first scheme its own spec lists that the client holds, and falls back to the single authenticator of `Client(authenticator:)` when it holds none of them; an operation declared with `security: []` carries no credential. There is no per-call authenticator, so an options struct never has an `auth` field.
- `close()` on the client is graceful and idempotent: a request in flight finishes (retries included), the `URLSession` the SDK built is released when the last one ends, streams that are still open are cancelled, and a request made afterwards throws `ConfigurationError.invalidState` (`ApiClient has been closed and can no longer send requests`). A `URLSession` you supplied is never invalidated.
- Streaming. An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns `ByteStream`, a `final` class that is an `AsyncSequence` of `Data`, from the one operation method (there is no second method), and `...WithHTTPInfo` returns `ApiResult<ByteStream>` with an empty `rawBody`. A `ByteStream` has `statusCode`, the lower-cased `headers`, an idempotent `close()` that aborts the connection instead of draining it, a `readAll()` that reads the rest and leaves the stream closed, and a `description` that shows only the status and the bytes delivered. A stream that is dropped unread is aborted too. The transport gets `ApiClient.sendStreaming(method:url:headers:body:timeout:maxRetries:idleTimeout:)`, which returns an `ApiStreamResponse` (status, headers, an `AsyncThrowingStream<Data, Error>` body, `close()`); its default implementation in a protocol extension throws `ConfigurationError.unsupported` naming the transport, so an injected `ApiClient` that only implements `sendRequest` keeps working for buffered calls. `DefaultApiClient` feeds the body from a `URLSessionDataDelegate` (`URLSession.AsyncBytes` is not available on Linux) and holds the transfer back while more than 1 MiB of the body is unread. It runs the buffered call's header phase (retries, redirects, the end-to-end `timeout`, the header rules) and stops there: the `timeout` ends at the response headers, `TransportOptionsBuilder.idleTimeout(_:)` (default 60000 ms, `0` or `nil` disables) bounds the gap between two reads of the body and throws `NetworkTimeoutError`, and nothing is retried after the first body byte. A stream call asks for the identity encoding unless you set an `Accept-Encoding` of your own, so a body that ends short of its `Content-Length` is detected. A non-2xx status throws the usual typed error with its body read up to 64 KiB. A reset, or a body shorter than its `Content-Length`, throws `StreamError` (a `NetworkError`, status 0). `client.close()` cancels the streams still open; their next read throws the closed-client `ConfigurationError.invalidState`. An operation whose request body is raw binary (`type: string, format: binary`) takes a `BodySource` instead of `Data`, in the operation, its `WithHTTPInfo` form, the protocol and the client facade; multipart and every other body kind keep their types. `BodySource` is a `final` class: `of(_ data:)`, `ofFile(_ url:)` and `fromFactory(length:_:)` (a closure that makes a fresh `AsyncSequence` of `Data`) are replayable, `once(_ stream: InputStream, length:)` and `once(_ sequence:, length:)` are single use. The body is sent as it is read, never buffered: a known length is the `Content-Length`, an unknown one is chunked. A replayable body is read again for a retry and for a 307 or 308; a single-use body is never retried, never replayed after a 401, and a 307 or 308 is refused with `Refusing to replay a streamed request body`. The SDK never closes an `InputStream` you pass to `once`.
- Pagination. A paged operation (D123, D124) gets `<operation>Pages(...)` with the parameters of the base method, in its group class, its `Protocol` and the client facade, returning `Paginator<T, C>`: an `AsyncSequence` of its items (`for try await item in paginator`, also `items()`) whose `pages()` is an `AsyncSequence` of `Page<T, C>` (`items`, `nextCursor` typed like the cursor parameter, and `result`, the `ApiResult` that `WithHTTPInfo` returns with the decoded body in `data`). `C` is the type of the request parameter that starts the next page (a cursor, an offset or a page number), and `Void` for a list whose next page is a URL, the `Link` header's or one in the body. The styles are the token cursor, the id cursor (which ends when a declared `has_more` is false), the offset (the next offset is the one past the items received), the page number (the next page is the one after the page just requested), the `Link` header and the next URL in the body (followed only within the client's origin); a cursor, offset or page that does not advance raises `PaginationError`. The iterators call the page request on demand from the reading task, so nothing is requested before the first read or ahead of it, cancelling the task aborts the request in flight with `CancellationError`, and each loop starts again from the starting position. A cursor, id, offset or page-number `Options` struct gets `withCursor(_:)` so every page is sent with its own value and everything else unchanged. A cursor equal to the one just sent, or a next URL (`Link` or body) to another origin, throws `PaginationError`, a struct that conforms to the root error protocol and is neither an `ApiError` nor a `NetworkError`.
- JSON Lines and server-sent events. An operation whose success response is JSON Lines or `text/event-stream` has no buffered method and no `...WithHTTPInfo` variant. It has `<operation>Stream`, an `async throws` method that takes the operation's parameters, its options struct and a trailing `streamOptions: StreamOptions?` (`idleTimeout` and `reconnectDelay` in milliseconds, `maxReconnects`, `lastEventId`, `maxEventBytes`), and returns a `JsonLinesStream<T>` or an `SseStream<T>`; both are `final` classes that are an `AsyncSequence` (`for try await`) with an idempotent `close()`, and the call returns when the first response headers arrive, a failure of that request being thrown there. Both can be read once, by one task at a time. An `SseStream<T>` yields `ServerSentEvent<T>` structs. The item decoder is the SDK's `ObjectSerializer` for a schema type (a payload that does not decode is a `SerializationError`) and the text itself for a string. An SSE stream reconnects with a fresh call (hook, headers and token again) and `Last-Event-ID`; the wait is the server's `retry`, then `Retry-After` on 429, 502, 503 and 504, then `reconnectDelay` doubling to 30 seconds with up to 25% jitter, and cancelling the reading task never reconnects. A JSON Lines call leaves `Accept-Encoding` to URLSession, which asks for a compressed body and decodes it as it arrives (a corrupt compressed body is an `ApiError` with the real status and the `URLError` as `underlyingError`); an SSE call asks for the identity encoding unless you set one, so a quiet feed is never buffered by a decoder. The transport is unchanged: both streams are built on `sendStreaming`.
- A cookie parameter is sent raw when every character is an RFC 6265 cookie-octet; any other character throws `ConfigurationError.invalidArgument` before the request, so the caller percent-encodes it. A `content:` cookie holds a typed value and is serialised to JSON and percent-encoded by the SDK.
- Building a model validates nothing and never throws; an unknown enum string builds no enum (`init(rawValue:)` returns `nil`). Wrong values fail when the SDK decodes a payload, with `SerializationError`; an unknown enum string decodes to `.unknownDefaultOpenApi`.
- A `oneOf` or `anyOf` wrapper exposes typed `is<Variant>` and `as<Variant>` members beside `actualInstance`.
- Credentials come from an argument first and the environment second. Each security scheme authenticator takes its credentials as optional arguments and reads `{PREFIX}_{FIELD}` once, when it is built (`{PREFIX}` is `envVarPrefix`; where two schemes share a field the scheme id is part of the name, so `{PREFIX}_{SCHEME}_{FIELD}`); an empty variable counts as unset, and a credential found in neither place throws `ConfigurationError.invalidArgument` at that point. The generated README lists the exact names. A scheme authenticator initializer that cannot find a credential throws, so every one of them is called with `try`. `Client.fromEnvironment()` reads `{PREFIX}_HOST` and `{PREFIX}_TOKEN` for the bearer token, or takes a `tokenProvider` callback that runs before every request.
- A property that is a `$ref` to a string enum with a `default` written beside the reference starts at that member in the initializer and takes it when the key is missing on decode; an explicit `null` stays `nil`.
- Operations are `async throws`. Cancel the Swift `Task` and the in-flight request is cancelled with it; a cancelled call throws `CancellationError`, not a timeout error.
- Error types end in `Error`. Caller mistakes throw `ConfigurationError` and `OAuth2AuthorizationCodeError`. The underlying cause is available as `underlyingError`.
- The default configuration is `Configuration.default`.
- Pass your own `URLSession` with `TransportOptions.session(_:)`.
- `TransportOptions` takes `maxResponseBytes` (off by default): a buffered response whose body, raw or decompressed, passes it fails with `ResponseTooLargeError` (D138); a zero or negative value throws `ConfigurationError.invalidMaxResponseBytes` at `build()`. The body is read through a delegate that counts each chunk URLSession delivers (decoded when it decoded gzip or deflate) and the task's received bytes, refuses a larger `Content-Length` up front, and cancels the task as soon as a count passes the limit; with the limit set, a session the SDK built is copied per call so the counting delegate can be its delegate.
- The SDK never advertises a `Content-Encoding` and never raises on an unknown one. `URLSession` negotiates and decodes.
- Initialisms are upper case (`URL`, `ID`, `HTTP`).
- Models have a `copy(...)` method instead of a builder. For an optional property the argument is double optional, so `.some(nil)` clears it and a bare `nil` keeps it.
- A property called `type` keeps that name.
- Testing your code: `<Group>ApiProtocol` (file `<Group>ApiProtocol.swift`) and `ClientProtocol` with an associated type per group, so `Client` keeps its concrete group classes. A requirement cannot carry default arguments, so the protocol lists the full form of each operation and an extension adds the form with only the required parameters (`vendorExtensions.op.signatureArgsRequired`); a call through the protocol that passes some but not all optional parameters must pass the others as `nil`.
- The call hook is `CallHook` with `start(info:headers:)`, which may throw and returns a `CallObserver?` whose `finish(_:)` is called once when the call has ended; `CallHooks.chain(...)` combines several. Set it with `ConfigurationBuilder.callHook(_:)` or with the `callHook:` argument of `Client`, `Client.withToken`, `Client.withAuthenticator` and `Client.fromEnvironment`. `start` and `finish` are `throws` because Swift has no other way to refuse a call or to show that an observer failed; an error from `start` reaches the caller and an error from `finish` is swallowed. `CallInfo` carries `operationId` (as written in the spec), `method`, `urlTemplate`, `url` and `serverAddress`; `CallOutcome` carries `statusCode` (0 when no response arrived), `headers` (lower-cased, `nil` when no response arrived) and `error`. The protocols are `Sendable`.
- Constraint validation: `ConfigurationBuilder().validateConstraints(true)` checks every request body against the `minLength`, `maxLength`, `pattern`, `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf`, `minItems`, `maxItems`, `minProperties` and `maxProperties` keywords of its schema and of the models it holds, before it is serialized and before anything is sent (D189). It is off by default. The first violation throws `ConfigurationError.invalidRequestBody`, whose message is `Invalid request body: <path> <rule>`. Lengths count Unicode scalars, a pattern runs in `NSRegularExpression` (ICU, so a look-ahead such as the one in a `^(?!admin)` pattern is enforced) and is skipped when it cannot be compiled, and responses are never checked.
- OpenTelemetry is a recipe, not a shipped adapter: a Swift package cannot declare an optional dependency, and the metrics API of opentelemetry-swift cannot name a unit. The generated README holds the recipe, `OpenTelemetryCallHook`, which records one span per call and hands the request count and the duration in seconds to two closures you supply; the generated test suite compiles and runs that same text.
- With `generateExamples` on, the package declares `examples/` as the executable target `Quickstart`: `examples/quickstart.swift` is an `@main` struct that reads `{PREFIX}_BASE_URL` and `{PREFIX}_TOKEN` (a missing variable throws `ConfigurationError.invalidArgument`), builds a client with `Client.withToken`, calls the example operation and closes the client with `defer`. `make examples` builds that target under the strict flags and `make examples-run` runs it. `make format` and `make lint` run swift-format on `examples/` too.
- `OAuth2TokenManager.stop()` ends the manager: a later token request throws `OAuth2TokenError.stopped` ("The token manager has been stopped.") and sends nothing. Stopping twice does nothing.

## Recipes

These are answers to questions that come up often, each built from what the generated SDK already has. None of them adds API.

### Hooks around every call

The call hook added by [#158](https://github.com/mridang/openapi-generator-plus/issues/158) is the one place to edit the request headers before the send and to observe the status or the error afterwards, for every operation and for the client as a whole. It is optional and does nothing when it is not set. It runs once per operation call however many sends happen inside it (retries, the one replay after a `401`), once for the plain and the `WithHttpInfo` variant alike, and also when you supply your own transport. `start` receives the call information (`operationId` as written in the spec, `method`, `urlTemplate`, `url` without its query string, `serverAddress`) and the final headers, which it can read and replace, and it returns the observer to call when the call ends or nothing. The observer's `finish` receives the status code (0 when no response arrived), the lower-cased response headers (none when no response arrived) and the error the caller receives (none on success). An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed; the hook never changes the result or the error. `CallHook` is the name to look for in this SDK.

```swift
struct SourceHook: CallHook {
    func start(info: CallInfo, headers: inout [String: String]) throws -> (any CallObserver)? {
        headers["X-Source"] = "nightly-job"
        return nil
    }
}

let client = try Client.withToken(
    host: "https://api.example.com",
    accessToken: "your-token",
    callHook: SourceHook()
)
```

To see every send of a call rather than the call as a whole (for example each retry), wrap the transport you hand to the SDK instead: see "Custom HTTP client" in the generated README. A transport you supply is never closed by the SDK, and the hook above still fires with it.

### Serving another region or tenant

A client is cheap, and its host, credentials, retry settings and connections belong to it. To call another region or another tenant, build another client; there is no per-call server URL or per-call HTTP client, and the per-call options stay `timeout`, `headers` and `maxRetries`.

```swift
let eu = try Client(host: "https://eu.api.example.com", accessToken: "eu-token")
let us = try Client(host: "https://us.api.example.com", accessToken: "us-token")
```

### Renaming types, fields and parameters

The names come from the specification, so the seam is the normaliser: the `RUN_SCRIPT` rule runs your own JavaScript over the document before generation (see [how it works](../how-it-works.md#renaming-with-a-script)). A schema renamed to `Animal` is generated as `Models/Animal.swift`, and this language's own casing, escaping of reserved words and file naming are applied to the new name like to any other. The SDK has no configuration map of names and never reads a vendor extension for a name. The name of a property or a parameter is also its name on the wire, so renaming one in the script renames it in the requests and responses as well: there is no rename that changes only the SDK name.

### Status, headers and the raw body

Every operation has a `...WithHTTPInfo` variant that returns the status code, the lower-cased response headers, the raw body and the decoded data. Every failure that carries an HTTP response is an `ApiError` that holds the status code, the response headers and the response body. A buffered call hands back its whole body as the raw body, and a binary operation hands back a `ByteStream` (see Streaming under Idiom notes); to see or change what goes over the wire, use the call hook above or the transport you supply.

```swift
let result = try await client.pet.addPetWithHTTPInfo(pet: pet)
let status = result.statusCode
let etag = result.headers["etag"]
let raw = result.rawBody

do {
    _ = try await client.pet.addPet(pet: pet)
} catch let error as ApiError {
    print(error.statusCode, error.responseHeaders as Any, error.responseBody as Any)
}
```

## Known limits

- A request body and a JSON, text or form response body are read whole into memory. Only a binary, a JSON Lines and a server-sent events response is a stream. A stream call on a `URLSession` you supplied uses that session's own timeouts and delegate rules. Apple's `URLSession` does not fail on a compressed body it cannot decode but hands it over as it arrived, so a corrupt compressed JSON Lines body ends with a `SerializationError` at its first line there, and with an `ApiError` carrying the real status on Linux.
- On Linux there is no proxy support and no CA pinning. Both throw a `ConfigurationError`. SOCKS proxies are rejected on every platform.
- The redirect rules are the SDK's on every platform. On Linux, `URLSession` sends no body on a redirect it follows, so a 307 or 308 that keeps its method and has a body is sent again by the SDK itself.
- `format: date` and `format: time` are plain strings, so they are not validated.
- `format: decimal` is `Foundation.Decimal` (38 digits, trailing zeros are not kept); a bare `type: number` is `Double` and loses exactness.
- Query, form and multipart parameters are held in dictionaries, so their wire order is not the order in the spec.
- A body with a declared charset other than UTF-8 (such as `windows-1252` or `shift_jis`) can decode incorrectly.
- An unknown enum value decodes to the catch-all case `.unknownDefaultOpenApi` (D117), which cannot be written.
- An optional nullable property cannot tell an absent key from an explicit `null`.
- `allOf` with a discriminator is not implemented yet.
- A `default` written beside a `$ref` is applied only when the reference points at a string enum; for any other referenced type the property stays `nil` until it is set.
- Schema constraints (`pattern`, `minLength`, `maximum` and so on) are not enforced by the client unless `validateConstraints` is on (D189, see Idiom notes).
- Callbacks and webhooks, XML bodies, Link Objects and mutual TLS are out of scope in every language.
- An optional and nullable property has no third state: an absent value and an explicit `null` are the same, by decision D112. A three-state type for PATCH-style APIs is declined for now.
- Operations are asynchronous only, as `async throws` methods, by decision D111. There is no blocking variant of an operation, because each SDK follows the style of its own language. Call it from your language's async machinery.
- An `integer` with no format is 64 bits, the same as `int64`, by decision D113 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)). A value above 2^31, such as a large identifier, decodes exactly.
- A security requirement that names several schemes together (for example a bearer token and an API key) sends the credentials of all of them, by decision D116. The client must hold every credential of the requirement; an alternative the client holds only in part is used only when no other alternative is complete. A requirement that names one scheme works as before.
- The OpenID Connect authenticator sends a nonce and checks the claims of the `id_token` (nonce, issuer, audience, expiry), but it does not verify the token's signature, by decision D140. Signature checking can be added later without changing the public API, because the discovery document already names the provider's keys (`jwks_uri`).

## Under review

These are ideas from a type review in November 2026. None of them is decided, and none changes the generated SDK today.

- SDK-owned `LocalDate` and `LocalTime` types for `date` and `time`, so those values are validated. This needs no dependency and no floor change.
- `Foundation.Decimal` for `decimal`, only after a test proves an exact JSON round trip.
- `URL` for `uri`. The review recommends keeping `String`, because `URL` parsing differs between Apple platforms and Linux and rejects link templates.
- Renaming `ISO8601Duration.swift`, since the wire form it handles is protobuf-JSON and not ISO 8601.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. The hook runs in the operation's own task after the credential await, so a task-local context is intact; the base-api tests prove this with a test-owned `@TaskLocal`.
