# Swift

The `swift-plus` generator (`BetterSwiftCodegen`) produces a Swift package. Models are `Codable` structs, each API tag gets an `async throws` class, and `DefaultApiClient` runs on `URLSession`. There are no third-party runtime dependencies. For the rules shared by all twelve languages see [how it works](../how-it-works.md), for the type table across languages see [types](../types.md), and for settled cross-language choices see [decisions](../decisions.md).

## Versions and support policy

The generated `Package.swift` uses `swift-tools-version: 6.0` and declares these platform minimums: macOS 12, iOS 15, tvOS 15 and watchOS 8. The package builds in the Swift 5 language mode (`swiftLanguageModes: [.v5]`), because the authenticator and `AnyCodable` classes are not yet clean under Swift 6 strict concurrency. The generated README says Swift 6.0 or newer.

Swift has no end-of-life policy for toolchains. Tools version 6.0 is the oldest the generated code and its Swift Testing based tests need, so it is the floor. A toolchain or platform that its vendor stops supporting is dropped in a major release. The generator builds and lints the golden package in a Swift 6.2 image.

On Linux the package uses `FoundationNetworking`.

## Type mapping

| OpenAPI type and format | Swift type | Notes |
|---|---|---|
| `string` | `String` | Also used for `email`, `hostname`, `ipv4`, `ipv6`, `password` and unknown formats. |
| `string` / `date` | `String` | Foundation has no calendar-date type. The value is not validated. |
| `string` / `date-time` | `Date` | An instant. The original UTC offset is not kept. |
| `string` / `time` | `String` | Foundation has no wall-clock time type. |
| `string` / `duration` | `TimeInterval` | Seconds as a `Double`. The wire form is the protobuf-JSON string such as `"3600s"`, converted by `parseProtobufDuration` and `formatProtobufDuration`. |
| `string` / `uuid` | `UUID` | |
| `string` / `byte` | `Data` | Base64 through the standard coder. |
| `string` / `binary`, file bodies | `Data` | Held fully in memory. |
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

- Every operation takes an options struct (`<Operation>Options`) as its last argument, and the argument can be left out. An operation with no query, header, form or cookie parameter and no security requirement has one too, carrying only `timeout` (milliseconds), `headers` and `maxRetries`, so each call can set them.
- The client takes one optional credential per security scheme, named after the scheme: `Client(host:petStoreBearer:apiKeyHeader:...transportOptions:)`, each argument any `Authenticator`. An operation uses the credential of the first scheme its own spec lists that the client holds, and falls back to the single authenticator of `Client(authenticator:)` when it holds none of them; an operation declared with `security: []` carries no credential. There is no per-call authenticator, so an options struct never has an `auth` field.
- `close()` on the client is graceful and idempotent: a request in flight finishes (retries included), the `URLSession` the SDK built is released when the last one ends, and a request made afterwards throws `ConfigurationError.invalidState` (`ApiClient has been closed and can no longer send requests`). A `URLSession` you supplied is never invalidated.
- A cookie parameter is sent raw when every character is an RFC 6265 cookie-octet; any other character throws `ConfigurationError.invalidArgument` before the request, so the caller percent-encodes it. A `content:` cookie holds a typed value and is serialised to JSON and percent-encoded by the SDK.
- Building a model validates nothing and never throws; an unknown enum string builds no enum (`init(rawValue:)` returns `nil`). Wrong values fail when the SDK decodes a payload, with `SerializationError`; an unknown enum string decodes to `.unknownDefaultOpenApi`.
- A `oneOf` or `anyOf` wrapper exposes typed `is<Variant>` and `as<Variant>` members beside `actualInstance`.
- Credentials come from an argument first and the environment second. Each security scheme authenticator takes its credentials as optional arguments and reads `{PREFIX}_{FIELD}` once, when it is built (`{PREFIX}` is `envVarPrefix`; where two schemes share a field the scheme id is part of the name, so `{PREFIX}_{SCHEME}_{FIELD}`); an empty variable counts as unset, and a credential found in neither place throws `ConfigurationError.invalidArgument` at that point. The generated README lists the exact names. A scheme authenticator initializer that cannot find a credential throws, so every one of them is called with `try`. `Client.fromEnvironment()` reads `{PREFIX}_HOST` and `{PREFIX}_TOKEN` for the bearer token, or takes a `tokenProvider` callback that runs before every request.
- A property that is a `$ref` to a string enum with a `default` written beside the reference starts at that member in the initializer and takes it when the key is missing on decode; an explicit `null` stays `nil`.
- Operations are `async throws`. Cancel the Swift `Task` and the in-flight request is cancelled with it; a cancelled call throws `CancellationError`, not a timeout error.
- Error types end in `Error`. Caller mistakes throw `ConfigurationError` and `OAuth2AuthorizationCodeError`. The underlying cause is available as `underlyingError`.
- The default configuration is `Configuration.default`.
- Pass your own `URLSession` with `TransportOptions.session(_:)`.
- The SDK never advertises a `Content-Encoding` and never raises on an unknown one. `URLSession` negotiates and decodes.
- Initialisms are upper case (`URL`, `ID`, `HTTP`).
- Models have a `copy(...)` method instead of a builder. For an optional property the argument is double optional, so `.some(nil)` clears it and a bare `nil` keeps it.
- A property called `type` keeps that name.
- Testing your code: `<Group>ApiProtocol` (file `<Group>ApiProtocol.swift`) and `ClientProtocol` with an associated type per group, so `Client` keeps its concrete group classes. A requirement cannot carry default arguments, so the protocol lists the full form of each operation and an extension adds the form with only the required parameters (`vendorExtensions.op.signatureArgsRequired`); a call through the protocol that passes some but not all optional parameters must pass the others as `nil`.
- The call hook is `CallHook` with `start(info:headers:)`, which may throw and returns a `CallObserver?` whose `finish(_:)` is called once when the call has ended; `CallHooks.chain(...)` combines several. Set it with `ConfigurationBuilder.callHook(_:)` or with the `callHook:` argument of `Client`, `Client.withToken`, `Client.withAuthenticator` and `Client.fromEnvironment`. `start` and `finish` are `throws` because Swift has no other way to refuse a call or to show that an observer failed; an error from `start` reaches the caller and an error from `finish` is swallowed. `CallInfo` carries `operationId` (as written in the spec), `method`, `urlTemplate`, `url` and `serverAddress`; `CallOutcome` carries `statusCode` (0 when no response arrived), `headers` (lower-cased, `nil` when no response arrived) and `error`. The protocols are `Sendable`.
- OpenTelemetry is a recipe, not a shipped adapter: a Swift package cannot declare an optional dependency, and the metrics API of opentelemetry-swift cannot name a unit. The generated README holds the recipe, `OpenTelemetryCallHook`, which records one span per call and hands the request count and the duration in seconds to two closures you supply; the generated test suite compiles and runs that same text.

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

Every operation has a `...WithHTTPInfo` variant that returns the status code, the lower-cased response headers, the raw body and the decoded data. Every failure that carries an HTTP response is an `ApiError` that holds the status code, the response headers and the response body. A live response object is not exposed, because that would need streaming (see [decisions](../decisions.md)); to see or change what goes over the wire, use the call hook above or the transport you supply.

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
- Schema constraints (`pattern`, `minLength`, `maximum` and so on) are not enforced by the client.
- Callbacks and webhooks, XML bodies, Link Objects and mutual TLS are out of scope in every language.
- An optional and nullable property has no third state: an absent value and an explicit `null` are the same, by decision D112. A three-state type for PATCH-style APIs is declined for now.
- Operations are asynchronous only, as `async throws` methods, by decision D111. There is no blocking variant of an operation, because each SDK follows the style of its own language. Call it from your language's async machinery.
- An `integer` with no format is 64 bits, the same as `int64`, by decision D113 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)). A value above 2^31, such as a large identifier, decodes exactly.
- A security requirement that names several schemes together (for example a bearer token and an API key) sends the credentials of all of them, by decision D116. The client must hold every credential of the requirement; an alternative the client holds only in part is used only when no other alternative is complete. A requirement that names one scheme works as before.

## Under review

These are ideas from a type review in November 2026. None of them is decided, and none changes the generated SDK today.

- SDK-owned `LocalDate` and `LocalTime` types for `date` and `time`, so those values are validated. This needs no dependency and no floor change.
- `Swift.Duration` for `duration`. It is integer-backed with no rounding, but it needs macOS 13 and iOS 16, so it depends on a decision to raise the platform minimums.
- `Foundation.Decimal` for `decimal`, only after a test proves an exact JSON round trip.
- `URL` for `uri`. The review recommends keeping `String`, because `URL` parsing differs between Apple platforms and Linux and rejects link templates.
- Renaming `ISO8601Duration.swift`, since the wire form it handles is protobuf-JSON and not ISO 8601.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. The hook runs in the operation's own task after the credential await, so a task-local context is intact; the base-api tests prove this with a test-owned `@TaskLocal`.
