# Type mapping

This page shows, for every OpenAPI type and format, the type each of the twelve generated SDKs uses today. The table is read from the generators and the committed example SDKs under `src/spec/resources/generated/<lang>/`; it describes what the SDKs do now, not what they may do later. Ideas that have been proposed but not decided are collected at the end under [Under review](#under-review).

Each column heading links to that language's page, which holds the language version it needs, its dependencies, its options and its idiom notes: [java](languages/java.md), [kotlin](languages/kotlin.md), [csharp](languages/csharp.md), [php](languages/php.md), [python](languages/python.md), [ruby](languages/ruby.md), [node](languages/node.md), [dart](languages/dart.md), [go](languages/go.md), [rust](languages/rust.md), [swift](languages/swift.md), [elixir](languages/elixir.md). The last row of the table gives the minimum language version declared in each SDK's build manifest; the language page is the place that explains it.

## The table

A cell with a name in backticks is the type as it appears in the generated code. "SDK type" means a type the SDK ships itself rather than one from the language or a library. Where one OpenAPI type needs a different type in a request or a response, the cell says so.

| OpenAPI | [java](languages/java.md) | [kotlin](languages/kotlin.md) | [csharp](languages/csharp.md) | [php](languages/php.md) | [python](languages/python.md) | [ruby](languages/ruby.md) | [node](languages/node.md) | [dart](languages/dart.md) | [go](languages/go.md) | [rust](languages/rust.md) | [swift](languages/swift.md) | [elixir](languages/elixir.md) |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `string` `date` | `java.time.LocalDate` | `java.time.LocalDate` | `DateOnly` | `\DateTime` | `datetime.date` | `Date` | `string` | `DateTime` | `string` | `chrono::NaiveDate` | `String` | `Date.t()` |
| `string` `date-time` | `OffsetDateTime` | `OffsetDateTime` | `DateTimeOffset` | `\DateTime` | `OffsetDateTime` (an aware `datetime`) | `Time` | `Date` | `DateTime` | `models.DateTime` (SDK type) | `DateTime` (SDK newtype over `chrono::DateTime<FixedOffset>`) | `Date` | `DateTime.t()` |
| `string` `time` | `LocalTime` | `LocalTime` | `TimeOnly` | `\DateTimeImmutable` | `TimeOfDay` (a `datetime.time`) | `Tod::TimeOfDay` | `Temporal.PlainTime` | `String` | `string` | `chrono::NaiveTime` | `String` | `Time.t()` |
| `string` `duration` | `java.time.Duration` | `java.time.Duration` | `TimeSpan` | `\DateInterval` (a `PreciseDuration` subclass) | `ProtobufDuration` (a `timedelta`) | `ISO8601::Duration` | `Temporal.Duration` | `Duration` | `models.Duration` (SDK type over `time.Duration`) | `chrono::Duration` | `TimeInterval` (seconds as `Double`) | `Duration.t()` |
| `string` `uuid` | `java.util.UUID` | `kotlin.uuid.Uuid` | `Guid` | `Symfony\Component\Uid\Uuid` | `uuid.UUID` | `String` (validated, lowercased) | `UUID` (branded string) | `UuidValue` (SDK type) | `uuid.UUID` | `uuid::Uuid` | `UUID` | `String.t()` |
| `string` `byte` | `byte[]` | `ByteArray` | `byte[]` | `string` (raw bytes) | `Base64Bytes` (annotated `bytes`) | `String` (binary encoding) | `Buffer` | `Uint8List` | `[]byte` | `Vec<u8>` | `Data` | `binary()` |
| `string` `binary`, file upload and download | `InputStream` | `ByteArray` | `System.IO.Stream` | `string` for bodies, `\SplFileObject` for files | `bytes` | `String`, or `File` and `StringIO` for files | `Buffer` (a body also accepts `Blob`) | `Uint8List` | `io.Reader` to upload, `[]byte` to download | `Vec<u8>` | `Data` | `binary()` |
| `string` `uri` | `java.net.URI` | `io.ktor.http.Url` | `System.Uri` | `string` | `UrlStr` (a `str` checked as an absolute URI) | `String` (checked) | `URI` (branded string) | `String` (checked on decode) | `string` | `String` | `String` | `String.t()` |
| `string` `email`, `hostname`, `ipv4`, `ipv6` | `String` | `String` | `string` | `string` | `StrictStr` | `String` | `string` (`Email` is a branded string) | `String` | `string` | `String` | `String` | `String.t()` |
| `integer` `int32` | `Integer` | `Int` | `int` | `int` | `StrictInt` | `Integer` | `number` | `int` | `int32` | `i32` | `Int` | `integer()` |
| `integer` `int64` | `Long` | `Long` | `long` | `int` | `StrictInt` | `Integer` | `number` | `int` | `int64` | `i64` | `Int64` | `integer()` |
| `integer`, no format | `Integer` | `Int` | `int` | `int` | `StrictInt` | `Integer` | `number` | `int` | `int32` | `i32` | `Int` | `integer()` |
| `number` `float` | `Float` | `Float` | `float` | `float` | `LaxFloat` | `Float` | `number` | `double` | `float32` | `f32` | `Float` | `float()` |
| `number` `double` | `Double` | `Double` | `double` | `float` | `LaxFloat` | `Float` | `number` | `double` | `float64` | `f64` | `Double` | `float()` |
| `number`, no format, and `decimal` | `BigDecimal` | `BigDecimal` | `decimal` | `float` | `JsonNumber` (a `Decimal`) | `Float` | `Decimal` (branded string) | `double` | `float64` | `f64` | `Double` | `float()` |
| `boolean` | `Boolean` | `Boolean` | `bool` | `bool` | `StrictBool` | `Boolean` | `boolean` | `bool` | `bool` | `bool` | `Bool` | `boolean()` |
| `array` | `List` | `List` | `List<T>` | `\Ds\Vector` | `List[T]` | `Array` | `Array<T>` | `List<T>` | `[]T` | `Vec<T>` | `[T]` | `list` |
| `array` with `uniqueItems` | `Set` | `Set` | `HashSet<T>` | `\Ds\Set` | `Set[T]` | `Set` | `Set<T>` | `Set<T>` | `models.Set[T]` (SDK type) | `HashSet<T>` | `Set<T>` | `MapSet.t(T)` |
| `object` with `additionalProperties` | `Map` | `Map` | `Dictionary<string, T>` | `\Ds\Map` | `Dict[str, T]` | `Hash` | `{ [key: string]: T }` | `Map<String, T>` | `map[string]T` | `HashMap<String, T>` | `Dictionary<String, T>` | `map()` |
| free-form `object` | `Object` | `Any` | `object` | `mixed` | `object` | `Object` | `unknown` | `Object?` | `any` | `JsonValue` (SDK newtype over `serde_json::Value`) | `AnyCodable` (SDK type) | `map()` |
| `enum` | nested `enum` | `enum class` | `enum` | backed `enum` | `str` `Enum` | constant class | `const` object and string-literal union | enhanced `enum` | named string type | `enum` | `enum` | atoms |
| `oneOf`, `anyOf` | polymorphic base class | wrapper holding `actualInstance` | polymorphic base class | wrapper model | wrapper model | module functions on the built value | TypeScript union and wrapper class | wrapper holding `actualInstance` | struct wrapper | `enum` (`oneOf`), struct of `Option` slots (`anyOf`) | wrapper holding `actualInstance` | module functions on the built value |
| Minimum language version | Java 21 | Kotlin 2.2.0 | net10.0, C# 14 | PHP 8.3 | Python 3.10 | Ruby 3.2 | Node 22 | Dart 3.11 | Go 1.26 | Rust 1.85 | Swift 6.0 | Elixir 1.17 |

The minimum-version row is copied from the build manifest of each committed example SDK: `pom.xml` (`release` 21), `build.gradle.kts` (Kotlin 2.2.0, `jvmTarget` 17), the `.csproj` (`net10.0`, `LangVersion` 14), `composer.json` (`^8.3`), `pyproject.toml` (`requires-python >=3.10`), the `.gemspec` (`>= 3.2`), `package.json` (`engines.node >=22`), `pubspec.yaml` (`>=3.11.0 <4.0.0`), `go.mod` (`go 1.26.0`), `Cargo.toml` (`rust-version 1.85`, edition 2021), `Package.swift` (`swift-tools-version: 6.0`, with macOS 12 and iOS 15 as the lowest platforms) and `mix.exs` (`~> 1.17`). The language pages say why each floor was chosen and which dependencies it brings.

Nullable properties are not a table row because they are the same idea in every language: the property's type becomes the language's optional form (`Optional[T]`, `T?`, `?T`, `T | null`, `Option<T>`, `*T` in Go, `T | nil` in Elixir). A property that is both required and nullable must be present when decoding, may be `null`, and is always written, `null` included.

## Rules that hold in every language

These rules are the same in all twelve SDKs whatever type a language uses. They are settled decisions (see [decisions](decisions.md)); a difference that is not listed on this page is a defect.

Integers are strict. A response or request value for an `integer` property is rejected, not rounded or truncated, when it is fractional, quoted, a boolean or outside the range of the declared size. This holds for fields, for elements of arrays and maps, and for a top-level value.

Dates and times are exact on the wire. A `date-time` is written as `YYYY-MM-DDTHH:MM:SS.SSS+00:00`: three fractional digits, a numeric offset, never `Z`. It is read only as an RFC 3339 value with an offset, and the host clock and time zone are never consulted.

A `uuid` is written in lower case with hyphens and validated in both directions. A `byte` string is decoded with the strict RFC 4648 standard alphabet, padded and without whitespace. A `binary` body is never re-encoded: it goes out as the bytes the caller gave, under the content type the caller chose.

A `uri` keeps each language's own type, and the wire rule is an absolute RFC 3986 URI with any scheme, written as given. The clients do not enforce `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems` or `maxItems`; the server owns validation. Python enforces what pydantic enforces natively and nothing more.

An enum value the spec does not list, and a discriminator value that maps to no variant, are errors (a `SerializationError`), never a silent fallback. Regenerate the client when the API adds a value.

Optional and nullable are one `null` in every model except required-and-nullable properties, as described above.

## Dates and times

`date`, `date-time`, `time` and `duration` are the widest gap between the twelve. Java, Kotlin, C#, Python, Ruby, Rust, Elixir and Node (for `time` and `duration`) use a real date or time type. Three languages keep `date` as a plain string (Node, Go and Swift), and three keep `time` as a plain string (Dart, Go and Swift). The reason is the same each time: the language or its standard library has no date-only or time-only type that is stable and does not need a dependency. Dart maps `date` to `DateTime`, so a date-only value is not distinguished from a date-time by its type.

PHP uses the mutable `\DateTime` for `date` and `date-time` and the immutable `\DateTimeImmutable` for `time`.

A `duration` is written and read in the protobuf JSON form, seconds with an `s` suffix such as `3600s`, not in ISO 8601 `P1DT2H` form. The review asked for this choice between the two forms to be confirmed for all twelve (see Under review). Java and Kotlin use `java.time.Duration`; C# uses `TimeSpan` with a converter; Python, Go, Rust and Elixir use a duration type with the same wire form; Swift uses `TimeInterval`, a `Double` of seconds, because Foundation has no structured duration type. Protobuf durations accept plus or minus 315,576,000,000 seconds; Go's `time.Duration` covers about 292 years, so a larger value is a `SerializationError` there.

## Integers

`int32` and `int64` map to the size they name in every language that has sized integers. Python, Ruby, Dart and PHP use their one integer type, and Node uses `number` for both, which is exact only up to 2^53. An `int64` above that in Node loses precision.

An `integer` with no format is the open gap. The OpenAPI specification treats it as unbounded, but every SDK maps it the way it maps `int32` (Java `Integer`, Kotlin `Int`, C# `int`, Go `int32`, Rust `i32`, Swift `Int`, and the single integer type elsewhere), and the settled rule rejects a value outside the 32-bit range on decode in all twelve. A spec that sends a larger number without declaring `int64` therefore fails to decode. Declare `format: int64` for such a field.

## Decimals and floats

A bare `type: number` and `format: decimal` are exact in Java, Kotlin (`BigDecimal`) and C# (`decimal`), and in Node and Python by decoding to a decimal type. Python decodes exactly and widens to a float on encode. The Node `Decimal` is a branded string whose decode currently goes through plain `JSON.parse`, so a number with more precision than a double is not yet kept exactly.

PHP, Ruby, Dart, Go, Rust, Swift and Elixir use their native float, so a value such as `0.1` is the nearest binary double. That is documented as lossy in each of those SDKs' README, in the section "Decimal / `format: number` precision". Swift and Elixir say so explicitly: Swift maps `decimal` to `Double`, and Elixir returns a `float()` and tells callers to parse the raw value with the `Decimal` library when they need exactness. `float` and `double` map to the language's 32-bit and 64-bit float where one exists; PHP, Ruby, Dart and Elixir have one float type, and Node uses `number`.

## URIs and IP addresses

`uri` is a real URI type in Java (`java.net.URI`), Kotlin (`io.ktor.http.Url`) and C# (`System.Uri`). Python, Ruby, Node and Dart keep a string but check that it is an absolute URI, with a validated or branded type in Python and Node. PHP, Go, Rust, Swift and Elixir keep a plain string. Kotlin's `Url` can normalise some inputs, which is a known difference from the byte-verbatim rule.

`email`, `hostname`, `ipv4` and `ipv6` are plain strings in all twelve SDKs. Only Node distinguishes `email` with a branded `Email` type. No SDK maps `ipv4` or `ipv6` to an address type.

## Binary data

`byte` is a base64 string on the wire and raw bytes in the language: `byte[]`, `ByteArray`, `Buffer`, `Uint8List`, `[]byte`, `Vec<u8>`, `Data`, `binary()`. PHP gives back a raw string, and Ruby a binary-encoded `String`. `binary` and file parts are streams where the platform makes that natural (`InputStream` in Java, `Stream` in C#, `io.Reader` for a Go upload, `\SplFileObject` in PHP) and byte arrays elsewhere. No SDK streams a response: bodies are read whole into memory, so large downloads need your own HTTP client.

## Collections and free-form objects

Arrays, sets and maps use the language's own collection type, except in PHP where they use the `php-ds` types (`\Ds\Vector`, `\Ds\Set`, `\Ds\Map`), and in Go, where a set is an SDK type (`models.Set[T]`) because Go has no set. `uniqueItems` arrays are written in each language's natural collection order, which is not sorted and not specified.

A schema whose `additionalProperties` declares a value type uses that type for the extra keys: `Metadata` with `additionalProperties: {type: string}` exposes a map of strings, a model value gives a map of that model, and an array or map value keeps its element type. The `T` in the `object` with `additionalProperties` row is that value type. A `null` extra is rejected unless the value schema is nullable. A free-form `additionalProperties` (`true`, an empty schema or no `type`) keeps the any-type listed below, so every JSON value is accepted and returned as it arrived.

Free-form objects and the `AnyType` schema map to the most open type the language has. Java uses `Object`, Kotlin `Any`, C# `object`, Node `unknown`, Dart `Object?`, Go `any`, Python and Ruby `object`. Rust and Swift ship their own value type (`JsonValue`, `AnyCodable`) because their standard libraries have none. Elixir uses a map for an object and `any()` for `AnyType`.

## Unions

`oneOf` and `anyOf` have no common form because the languages differ most here. Java and C# use a polymorphic base class. Kotlin, Dart and Swift use a wrapper that holds the matched value as `actualInstance`, with accessors to read it as a variant (Kotlin uses a sealed interface for discriminated parents). Python wraps the union in a pydantic root model. Ruby and Elixir put module functions on the built value, and an `anyOf` may return a `*Composite` struct. Go wraps the value and finds the variant with a type switch. Rust turns a `oneOf` into an enum with payloads and an `anyOf` into a struct of `Option` slots. Node produces a TypeScript union alias plus a wrapper class that records the allowed schemas and the discriminator mapping.

Variants without a discriminator are tried in declaration order. In an `anyOf` the later declared variant wins when several match. An operation with several `2xx` responses of different shapes decodes the body as the schema of the status that arrived and returns a generated union of them.

`allOf` with a discriminator is also language-shaped: inheritance in Java, C#, Node, Python, Ruby, PHP and Dart; a sealed interface with flattened data classes in Kotlin; an embedded base struct and a decoding function in Go; an internally tagged enum in Rust; a protocol and an enum in Swift; flattened structs and a dispatch function in Elixir.

## Under review

Everything in this section is a proposal from a review of the current mappings against each language's latest stable release. None of it is decided and none of it is in the generated SDKs. Several items would change the public type of a field, so each would have to be made as one decision for all twelve SDKs, and several would raise a language floor, which the owner has to approve. The language pages say which of these apply to them.

- Unbounded integers. Map an `integer` without a format to a 64-bit type (Java `Long`, Kotlin `Long`, C# `long`, Go `int64`, Rust `i64`, Swift `Int64`), or to an arbitrary-precision type, as one cross-language decision.
- Dates and times. Switch PHP from `\DateTime` to `\DateTimeImmutable`. Map Node `date` to `Temporal.PlainDate`, which the `temporal-polyfill` dependency already provides. Add SDK-owned date and time-of-day types to Go and Swift in place of `string`. Decide whether a Dart `date` should stay a `DateTime` at midnight UTC or become an SDK-owned type. Add a test that a non-zero offset such as `+05:30` survives decoding in Elixir.
- Durations. Confirm across all twelve whether the wire form should stay the protobuf form or become ISO 8601. Move Kotlin to `kotlin.time.Duration`, and rename the Rust `chrono::Duration` to its current name `chrono::TimeDelta`. Possibly add `java.time.Period` in Java for calendar durations.
- Decimals. Decode Node decimals without loss by using the `JSON.parse` reviver source text, and add an opt-in `bigint` or string for `int64` values beyond 2^53. Use `BigDecimal` in Ruby, `Foundation.Decimal` in Swift, `rust_decimal` or `bigdecimal` in Rust, and, once its floor reaches PHP 8.4, `\BcMath\Number` in PHP.
- URIs and IP addresses. Map `ipv4` and `ipv6` to `ipaddress.IPv4Address` and `IPv6Address` in Python, `std::net` types in Rust, `net/netip` in Go, `IPAddress` in C# and `IPAddr` in Ruby. Map `uri` to `Uri` in Dart, `url::Url` in Rust, `url.URL` in Go and `URI.t()` in Elixir. Swift would stay on `String` because URL parsing differs between Apple and Linux Foundation.
- Unions. Generate sealed interfaces with final variants in Java, Kotlin (for non-discriminated unions) and Dart.
- Free-form objects and binary. Use `kotlinx.serialization.json.JsonElement` in Kotlin and `System.Text.Json.Nodes.JsonNode` in C# in place of `Any` and `object`. Return and accept `Uint8Array` in Node instead of `Buffer`. Offer a streaming path for binary bodies in Kotlin and Dart.
- Maps. Instantiate Java maps with `LinkedHashMap` so decoded `additionalProperties` keep the order on the wire.

The review also raised floor questions that belong to the language pages and the owner: whether C#, Python and Ruby should move to the oldest release that is still supported upstream, and how the Elixir floor in `mix.exs` (`~> 1.17`) relates to the floor named in a comment in the Elixir generator.
