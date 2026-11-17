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
| `integer` with no format | `Int` | `short` is also `Int`. |
| `number` / `float` | `Float` | |
| `number` / `double`, `number` with no format | `Double` | |
| `number` / `decimal` | `Double` | Lossy for exact decimal values such as money. |
| `boolean` | `Bool` | |
| `array` | `[T]` | |
| `array` with `uniqueItems` | `Set<T>` | The order on the wire is unspecified. |
| `object` with `additionalProperties` | `[String: T]` | Key order is not kept. |
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
| `envPrefix` | upper-case snake form of the package name | Prefix of the environment variables the client reads credentials from. |

## Idiom notes

- Operations are `async throws`. Cancel the Swift `Task` and the in-flight request is cancelled with it; a cancelled call throws `CancellationError`, not a timeout error.
- Error types end in `Error`. Caller mistakes throw `ConfigurationError` and `OAuth2AuthorizationCodeError`. The underlying cause is available as `underlyingError`.
- The default configuration is `Configuration.default`.
- Pass your own `URLSession` with `TransportOptions.session(_:)`.
- The SDK never advertises a `Content-Encoding` and never raises on an unknown one. `URLSession` negotiates and decodes.
- Initialisms are upper case (`URL`, `ID`, `HTTP`).
- Models have a `copy(...)` method instead of a builder. For an optional property the argument is double optional, so `.some(nil)` clears it and a bare `nil` keeps it.
- A property called `type` keeps that name.

## Known limits

- On Linux there is no proxy support and no CA pinning. Both throw a `ConfigurationError`. SOCKS proxies are rejected on every platform.
- `format: date` and `format: time` are plain strings, so they are not validated.
- `int32` fields are `Int`, so a value above the 32-bit range is accepted.
- `format: decimal` and `number` are `Double` and lose exactness.
- Query, form and multipart parameters are held in dictionaries, so their wire order is not the order in the spec.
- A body with a declared charset other than UTF-8 (such as `windows-1252` or `shift_jis`) can decode incorrectly.
- An unknown enum value throws when decoding.
- An optional nullable property cannot tell an absent key from an explicit `null`.
- `allOf` with a discriminator is not implemented yet.
- Schema constraints (`pattern`, `minLength`, `maximum` and so on) are not enforced by the client.
- Callbacks and webhooks, XML bodies, Link Objects and mutual TLS are out of scope in every language.

## Under review

These are ideas from a type review in November 2026. None of them is decided, and none changes the generated SDK today.

- SDK-owned `LocalDate` and `LocalTime` types for `date` and `time`, so those values are validated. This needs no dependency and no floor change.
- `Swift.Duration` for `duration`. It is integer-backed with no rounding, but it needs macOS 13 and iOS 16, so it depends on a decision to raise the platform minimums.
- `Foundation.Decimal` for `decimal`, only after a test proves an exact JSON round trip.
- `Int32` for `int32` and `Int64` for an unformatted integer. This is a trade between exactness and convenience for callers, to be decided across languages.
- `URL` for `uri`. The review recommends keeping `String`, because `URL` parsing differs between Apple platforms and Linux and rejects link templates.
- Renaming `ISO8601Duration.swift`, since the wire form it handles is protobuf-JSON and not ISO 8601.
- An unknown-case fallback on enums for forward compatibility.
