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

```text
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

```swift statements
import Foundation
import PetstoreClient

let environment = ProcessInfo.processInfo.environment
guard let host = environment["SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL"] else {
    throw ConfigurationError.invalidArgument("SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL is not set")
}
guard let token = environment["SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN"] else {
    throw ConfigurationError.invalidArgument("SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN is not set")
}
let client = try Client.withToken(host: host, accessToken: token)
defer { client.close() }
let result = try await client.store.getDefaults()
print(result)
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

```swift file
import PetstoreClient

func getDefaultsCancelled(client: Client) async {
    let task = Task { _ = try await client.store.getDefaults() }
    task.cancel()
    _ = await task.result
}
```

## Streaming

An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns a `ByteStream` instead of the whole body in memory, so a large download never has to fit in memory. The call returns as soon as the response headers arrive, and the body is read as you consume it. A `ByteStream` has the status code, the lower-cased headers and an idempotent `close()` that aborts the connection instead of draining it, so close it when you stop early (`defer`). `readAll()` reads the rest into memory and leaves the stream closed.

```swift file
import Foundation
import PetstoreClient

func saveDownload(client: Client, petId: Int64, documentId: Int64, to target: URL) async throws {
    let stream = try await client.pet.downloadPetDocument(petId: petId, documentId: documentId)
    defer { stream.close() }
    print(stream.statusCode)
    _ = FileManager.default.createFile(atPath: target.path, contents: nil)
    let handle = try FileHandle(forWritingTo: target)
    defer { try? handle.close() }
    for try await chunk in stream {
        try handle.write(contentsOf: chunk)
    }
}
```

The call `timeout` covers the wait for the response headers and stops there. `idleTimeout` (in `TransportOptions`, default 60 seconds, `0` or `nil` disables it) bounds the gap between two reads of the body and raises `NetworkTimeoutError`. Retries, redirects, token refresh and the error contract apply up to the headers exactly as for a buffered call; a request is never retried after the first body byte, and a non-2xx status raises the usual typed error with its body read up to 64 KiB and never returns a stream. A connection reset, or a body that ends before its declared `Content-Length`, raises `StreamError`, a `NetworkError` with status 0. `client.close()` cancels the streams that are still open, and their next read raises the closed-client error. A custom transport that does not implement `sendStreaming` fails a stream call with `ConfigurationError.unsupported` naming it, and its buffered calls keep working.

An operation whose request body is raw binary (`type: string, format: binary`) takes a `BodySource` instead of buffering the bytes, so a large upload never has to fit in memory. A `BodySource` is replayable, made with `BodySource.of(data)`, `BodySource.ofFile(url)` or `BodySource.fromFactory(factory)` (a factory that opens a fresh sequence each time), or single use, made with `BodySource.once(stream)`. The body is sent as it is read: a known length (bytes, a file, or a length you pass) goes out as `Content-Length` and an unknown length is sent chunked. A replayable body is read again for a retry after a 5xx, a 429 or a network error and for a 307 or 308 redirect. A single-use body is never retried, so the original error is raised unchanged, and a 307 or 308 is refused with `Refusing to replay a streamed request body`; it is never buffered to make a replay possible. The SDK never closes a stream you pass to `once` and closes the streams it opens itself from a file or a factory. Multipart bodies stay buffered. A 401 that refreshes the token follows the same rule, and an event stream request with a single-use body is never reconnected.

An operation whose success response is JSON Lines (`application/x-ndjson`, `application/jsonl`, `application/jsonlines`) or server-sent events (`text/event-stream`) has no buffered method. It has a `<operation>Stream` method that takes the operation's parameters, its options object and a trailing `StreamOptions`, and returns a `JsonLinesStream<T>` of the schema type or an `SseStream<T>` of `ServerSentEvent<T>` (`id`, `event`, `data`, `retry` and the raw `data` text; `T` is the schema of the `data`, or a string). Iterate it, and close it when you stop early. A JSON Lines line that does not decode, and a line or event longer than `maxEventBytes` (1 MiB), raise `SerializationError` and end the stream.

```swift file
import PetstoreClient

func readJsonLinesFeed(client: Client) async throws {
    let feed = try await client.store.exportOrdersStream()
    defer { feed.close() }
    for try await item in feed {
        print(item)
    }
}
```

```swift file
import PetstoreClient

func readSseFeed(client: Client) async throws {
    let feed = try await client.store.watchNoticesStream()
    defer { feed.close() }
    for try await event in feed {
        print(event.data)
    }
}
```

An event stream reconnects when the connection ends, fails or stays silent for the idle timeout: it asks again with fresh headers and `Last-Event-ID`, waits the server's `retry` value or `reconnectDelay` (1 second, doubling to 30 seconds with jitter), and raises the last failure after `maxReconnects` failures in a row without an event (5; 0 for a request with a body; `-1` is unlimited). It never reconnects after a 204, a status other than 408, 429 and 5xx, a response that is not `text/event-stream`, a cancellation or `client.close()`. An event named `error` raises `ApiError` with the response status and the data as its body, and a comment line such as a heartbeat counts as activity for the idle timeout. The generator option `sseTerminator` names a data value (such as `[DONE]`) that ends the stream and is not delivered. `StreamOptions` also takes `idleTimeout` and `lastEventId`.

## Pagination

An operation that returns a list one page at a time has a `<operation>Pages` method next to its plain method. It takes the same parameters in the same order and returns a `Paginator<T, C>`, where `T` is the item type and `C` is the type of the cursor parameter. The method sends no request: the first request goes out when you first read an item or a page, and each next request goes out only when you ask for more, so stopping early sends nothing further and leaves nothing to close. Read the items with `for try await item in paginator`, or the pages with `pages()`, an `AsyncSequence` of `Page<T, C>` whose values have `items`, `nextCursor` and `result`, the same `ApiResult` that the `WithHTTPInfo` variant returns, so the status and headers stay reachable. Reading again starts again from the starting position.

```swift file
import PetstoreClient

func readEverything(client: Client) async throws {
    let list = client.pet.listPetCatalogPages()
    for try await item in list {
        print(item)
    }
    for try await page in list.pages() {
        print("\(page.items.count) items, status \(page.result.statusCode)")
    }
}
```

The cursor you pass to the method is the starting position, and the next cursor of a page resumes the list: pass it as the starting cursor of a new call. The page size and every other parameter, option and header are repeated unchanged on each page request, and each page is an ordinary call with its own retries, timeout, redirects, authentication and redaction. An error on a later page is raised from the read that needed it, after the items of the earlier pages were delivered, and ends the iteration; the SDK never skips a page. A list ends when the next cursor is absent, `nil` or empty, and an empty page with a next cursor continues. A page that returns the cursor that was just sent raises `PaginationError`, a direct child of `OpenAPIError`, after its items were delivered, instead of looping. There is no total limit and no collect-all: use `break` or `prefix`.

How each style ends and resumes: an id list passes the `last_id` or `next_cursor` of each page as the starting cursor of the next request and ends when a declared `has_more` is false. An offset list starts at the offset you pass, sends the offset past the items it received on each next request, and ends on an empty page or when a declared `has_more` is false. A page-number list sends the page you pass, or none, and then asks for the page after each one, and ends on an empty page or when a declared `has_more` is false. A next URL list takes the URL in the body of each page, follows it only within the origin of the client, and ends when the property is absent, `null` or empty. A cursor, offset or page that does not advance raises `PaginationError` as a token cursor does.

A list that is followed by a `Link` header (`rel="next"`, RFC 8288) takes the next URL from the header of each response. A next URL that leaves the scheme, host or port of the client is refused with `PaginationError` and nothing, no credentials either, is sent to it; such a method has no starting cursor, so it cannot resume.

A list whose page body carries the next URL in a property (a next URL list) is followed in the same way as a `Link` list: the URL is taken from the body of each response, a next URL that leaves the scheme, host or port of the client is refused with `PaginationError`, nothing is sent to it, and such a method cannot resume from a saved position.

The generator finds a paged operation by its names and shape. The operation must be a GET with one JSON 2xx response, and it must match exactly one of these styles. A token cursor is one query parameter among `cursor`, `after`, `page_token`, `next_token`, `continuation_token` and `marker`, paired with a next-cursor property among `next_cursor`, `next_page_token`, `next_token`, `next_marker`, `next_continuation_token`, `continuation_token` and `next` of the same type. An id cursor is one query parameter among `starting_after`, `after_id` and `after`, paired with a boolean `has_more` and a string `last_id` or `next_cursor` property. A `Link` list has a `Link` header declared on the response. An offset list has an `offset` query parameter with a `limit`, and a page-number list has a `page` query parameter with one of `per_page`, `page_size`, `size` or `limit`. A next URL list has no cursor parameter and a string property with `format: uri` or `uri-reference`, or one named `next_url`, `next_uri`, `nextlink` or `next_link`, that is optional or nullable. An operation that matches more than one style is ambiguous, and one that is streamed is never paged; either keeps only its plain method, and the generation log names it and says why. The `x-fern-pagination` and `x-speakeasy-pagination` (cursor type) extensions are read as hints. The generator option `paginationOverrides` (a JSON object that maps an operation id to `none` or to `{style, requestParam, responseProperty, itemsProperty, hasMoreProperty}`, where the style is `token`, `link`, `id`, `offset`, `page` or `body`) wins over a hint and over inference, and `pagination=off` switches the feature off.

## Custom HTTP client

`TransportOptionsBuilder.session(_:)` sends every request through your own `URLSession`
you supply. The timeout, `User-Agent`, default headers and request id still
apply on top. You own the session: `close()` never invalidates it. A session
cannot be combined with the SDK's own `proxy`, `caCertPath`, `verifySsl(false)`,
`followRedirects(false)` or `maxRedirects`, because those are properties of the
session itself; `build()` throws a `ConfigurationError` naming the conflict.

## Testing your code

Every API group has an interface (`PetApiProtocol`) and the client has one (`ClientProtocol`), so a test can replace the SDK with a hand-written fake: no mocking library, no network.

```swift file
import PetstoreClient

// Code under test takes the group interface, never the generated class.
func use<A: PetApiProtocol>(_ api: A) {}

// A fake conforms to the interface; the compiler lists the operations it still has to implement.
// use(MyFake())
```

A new operation in the API adds a requirement to the interface, so a fake that conforms to the whole interface stops compiling after you regenerate; keep the fake in one place, or wrap the generated type and forward the operations you do not fake, so only the operation you fake is written.

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
authenticator for its headers again. A single-use `BodySource` and the OAuth2 token request
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

```swift file
import PetstoreClient

func clientWithSchemeCredential(authenticator: any Authenticator) throws -> Client {
    try Client(host: "https://api.example.com", petStoreBasic: authenticator)
}
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
the last of them ends (at once when none is running). Streams that are still open are
cancelled. A second `close()` does nothing,
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

## Hooks and telemetry

Every operation calls one small, optional hook around the call: it can edit the request headers before the request is sent and observe the status or the error afterwards, and it does nothing when no hook is set. Set it once on the client or on the `Configuration`, and combine several with `CallHooks.chain`, which starts them in the order given and finishes their observers in the reverse order.

```swift file
import Foundation
import PetstoreClient

struct SourceHook: CallHook {
    func start(info: CallInfo, headers: inout [String: String]) throws -> (any CallObserver)? {
        headers["X-Source"] = "nightly-job"
        return nil
    }
}

func hookedClient() throws -> Client {
    try Client.withToken(
        host: "https://api.example.com",
        accessToken: "your-token",
        callHook: SourceHook()
    )
}
```

`start` runs once per call, however many sends the call takes, right after the trace context is injected, so `headers` is the final header set: authentication and default headers included, and any of them can be replaced. A header you set is on every send of the call, retries and the replay after a 401 included; the fresh authentication headers of a replay still replace a header of the same name. `start` returns a `CallObserver`, or `nil` when it has nothing to observe, and `CallObserver.finish` is called exactly once when the call has ended, with a `CallOutcome`: the `statusCode` (0 when no response arrived), the lower-cased response `headers` (`nil` when no response arrived) and the `error`, the very error the caller receives. `CallInfo` carries the `operationId` as written in the spec, the upper-case `method`, the `urlTemplate`, the `url` without its query string and the `serverAddress`. An error thrown by `start` reaches the caller unchanged, nothing is sent and `finish` is not called; an error thrown by `finish` is swallowed. The hook runs above the transport, so it also fires with a `URLSession` you supplied.

To record one span, one request count and one duration per call, give the SDK a hook built on the OpenTelemetry API. The SDK itself has no OpenTelemetry dependency and a Swift package cannot declare an optional one, so this is a recipe you copy into your project rather than a class the SDK ships. It names the span after the operation id with kind client, sets `http.request.method`, `url.template`, `server.address`, `http.response.status_code` (only when a response arrived) and `error.type` (the status code for a status of 400 or more, else `timeout`, `network_error`, `serialization_error`, `cancelled` or `_OTHER`), and writes the `traceparent` of the client span into the request itself. The metrics API of opentelemetry-swift has no way to name a unit, so the count and the duration go to the closures you pass as `onRequest` and `onDuration`; forward them to your metrics library. The function at the end of the recipe passes it as the `callHook`, with your tracer provider and propagator.

```swift file
import Foundation
import OpenTelemetryApi
import PetstoreClient

/// Records one span for every call, and hands its count and its duration to
/// the closures you give it.
public final class OpenTelemetryCallHook: CallHook, @unchecked Sendable {
    private let tracerProvider: (any TracerProvider)?
    private let propagator: (any TextMapPropagator)?
    private let activeSpan: (@Sendable () -> (any Span)?)?
    private let onRequest: (@Sendable ([String: AttributeValue]) -> Void)?
    private let onDuration: (@Sendable (Double, [String: AttributeValue]) -> Void)?

    /// - Parameters:
    ///   - tracerProvider: The provider the spans come from; the global provider when nil.
    ///   - propagator: Writes the trace context into the request; the global one when nil.
    ///   - activeSpan: The span the call's span is a child of; the active span when nil.
    ///   - onRequest: Called once per call with the attributes of its span.
    ///   - onDuration: Called once per call with its duration in seconds and the attributes of its span.
    public init(
        tracerProvider: (any TracerProvider)? = nil,
        propagator: (any TextMapPropagator)? = nil,
        activeSpan: (@Sendable () -> (any Span)?)? = nil,
        onRequest: (@Sendable ([String: AttributeValue]) -> Void)? = nil,
        onDuration: (@Sendable (Double, [String: AttributeValue]) -> Void)? = nil
    ) {
        self.tracerProvider = tracerProvider
        self.propagator = propagator
        self.activeSpan = activeSpan
        self.onRequest = onRequest
        self.onDuration = onDuration
    }

    public func start(info: CallInfo, headers: inout [String: String]) throws -> (any CallObserver)? {
        let provider = tracerProvider ?? OpenTelemetry.instance.tracerProvider
        if provider is DefaultTracerProvider {
            /* No provider is configured: record nothing and leave the call alone. */
            return nil
        }
        let tracer = provider.get(
            instrumentationName: "PetstoreClient", instrumentationVersion: "1.0.0")
        let attributes: [String: AttributeValue] = [
            "http.request.method": .string(info.method),
            "url.template": .string(info.urlTemplate),
            "server.address": .string(info.serverAddress)
        ]
        let builder = tracer.spanBuilder(spanName: info.operationId).setSpanKind(spanKind: .client)
        for (key, value) in attributes {
            builder.setAttribute(key: key, value: value)
        }
        if let parent = (activeSpan ?? { OpenTelemetry.instance.contextProvider.activeSpan })() {
            builder.setParent(parent)
        }
        let span = builder.startSpan()
        var carrier: [String: String] = [:]
        (propagator ?? OpenTelemetry.instance.propagators.textMapPropagator).inject(
            spanContext: span.context, carrier: &carrier, setter: HeaderSetter())
        for (name, value) in carrier where !value.isEmpty {
            headers[name] = value
        }
        return SpanObserver(
            hook: self, span: span, started: DispatchTime.now().uptimeNanoseconds,
            attributes: attributes)
    }

    fileprivate func recorded(seconds: Double, attributes: [String: AttributeValue]) {
        onRequest?(attributes)
        onDuration?(seconds, attributes)
    }
}

private struct HeaderSetter: Setter {
    func set(carrier: inout [String: String], key: String, value: String) {
        carrier[key] = value
    }
}

private final class SpanObserver: CallObserver, @unchecked Sendable {
    private let hook: OpenTelemetryCallHook
    private let span: any Span
    private let started: UInt64
    private let attributes: [String: AttributeValue]

    init(hook: OpenTelemetryCallHook, span: any Span, started: UInt64, attributes: [String: AttributeValue]) {
        self.hook = hook
        self.span = span
        self.started = started
        self.attributes = attributes
    }

    func finish(_ outcome: CallOutcome) throws {
        var attributes = self.attributes
        if outcome.statusCode > 0 {
            attributes["http.response.status_code"] = .int(outcome.statusCode)
        }
        if let errorType = SpanObserver.errorType(of: outcome) {
            attributes["error.type"] = .string(errorType)
            span.status = .error(description: errorType)
        }
        span.setAttributes(attributes)
        span.end()
        let seconds = Double(DispatchTime.now().uptimeNanoseconds - started) / 1_000_000_000
        hook.recorded(seconds: seconds, attributes: attributes)
    }

    private static func errorType(of outcome: CallOutcome) -> String? {
        if outcome.statusCode >= 400 {
            return String(outcome.statusCode)
        }
        guard let error = outcome.error else {
            return nil
        }
        if error is CancellationError {
            return "cancelled"
        }
        if error is NetworkTimeoutError {
            return "timeout"
        }
        if error is NetworkError {
            return "network_error"
        }
        if error is SerializationError {
            return "serialization_error"
        }
        return "_OTHER"
    }
}

func tracedClient() throws -> Client {
    try Client.withToken(
        host: "https://api.example.com",
        accessToken: "your-token",
        callHook: OpenTelemetryCallHook(
            tracerProvider: OpenTelemetry.instance.tracerProvider,
            propagator: W3CTraceContextPropagator()
        )
    )
}
```

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
all (`Status(rawValue: "x")` is `nil`). Checking happens when the SDK decodes a payload: a
missing required property or a `format: uri` that is not an
absolute URI throws `SerializationError`, and an unknown enum string decodes to the catch-all case.

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
nullable property keeps its key and is written as `null`. A required nullable query parameter is kept the same way: when its value is `nil` the key is still sent, with an empty value (`?key=`).

## Unknown enum values and discriminators

An enum value the spec does not list decodes to the enum's catch-all member (`.unknownDefaultOpenApi`) instead of failing the
response, so a server that adds a value does not break a client built before it. The catch-all member has no
wire value, so writing it raises `SerializationError`. A discriminator value that maps to no variant still fails
loudly with `SerializationError`.

## Response headers

`ApiResult.headers` and `ApiError.responseHeaders` are a plain dictionary with
lower-cased names. A header the server repeats (`Link`, `Set-Cookie`) is joined
into one value with `, ` in the order it arrived, so a `Set-Cookie` value is not
parsed for you.

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
