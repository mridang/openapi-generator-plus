# Dart

The `dart-plus` generator produces a pure Dart package built on `package:http`, with plain model classes that have `fromJson` and `toJson`. This page covers what the generated package needs, how it maps OpenAPI types, the options the generator accepts, and where Dart differs from the other eleven languages. For the type table across all languages see [../types.md](../types.md); for the pipeline see [../how-it-works.md](../how-it-works.md).

## Versions and support policy

The generated `pubspec.yaml` sets `environment.sdk` to `'>=3.11.0 <4.0.0'`. The generator's tests and formatter run in a digest-pinned `dart:stable` image.

Dart has no long-term-support line: only the current stable release is supported. The floor is therefore a deliberate choice rather than "oldest supported", and the owner decision is to keep it at 3.11. A review of the type mapping (see "Under review") found nothing in the Dart releases since 3.10 that the SDK needs, so there is no reason to raise it.

The package compiles for the Dart VM, for Flutter, and for the web including WebAssembly. A conditional import selects `default_api_client_io.dart` (built on `dart:io`, with proxy, TLS and manual gzip and deflate) or `default_api_client_web.dart` (browser fetch).

## Type mapping

| OpenAPI | Dart type | Notes |
|---|---|---|
| `integer`, `int32`, `int64` | `int` | Exact on the Dart VM. On Flutter web and dart2js, `int` is a double, so integers above 2^53 lose precision. The generated README documents this. |
| `number` with any format, including `decimal` | `double` | Money loses exact decimal representation: `0.1 + 0.2` is `0.30000000000000004`. The README advises the `decimal` package or passing values as strings. |
| `boolean` | `bool` | |
| `string` | `String` | |
| `string` with `format: date` | `DateTime` | Dart has no date-only type. The value is written as `YYYY-MM-DD` from the `DateTime`'s own year, month and day, so use a UTC value to be sure the day does not shift. |
| `string` with `format: date-time` | `DateTime` | Must be an RFC 3339 string with an offset. Written in a canonical offset form. |
| `string` with `format: time` | `String` | Validated as `HH:MM:SS` with optional fractional seconds. Dart has no time-of-day type outside Flutter. |
| `string` with `format: duration` | `Duration` | The wire form is the protobuf-JSON form such as `3600s`, limited to +-315576000000 seconds (a value beyond that is refused). Helper functions in `iso8601_duration.dart` convert between the two, even though the file name says ISO 8601. |
| `string` with `format: uuid` | `UuidValue` | A class the SDK defines itself, which validates on construction. It deliberately shadows the type of the same name in `package:uuid`, whose `fromString` does not validate. |
| `string` with `format: uri` | `String` | Checked as an absolute RFC 3986 URI when decoded. Not `Uri`, because `dart:core` `Uri` rewrites `[`, `]` and the apostrophe, which would change the text on a round trip. |
| `string` with `format: email`, `hostname`, `ipv4`, `ipv6` | `String` | |
| `string` with `format: byte`, `binary`, file upload and download | `Uint8List` | Bodies are held in memory. |
| `array` | `List<T>` | |
| `array` with `uniqueItems` | `Set<T>` | Order is not specified. |
| `additionalProperties` map | `Map<String, T>` | `T` is the declared value type; a free-form bucket keeps the any-type |
| free-form object | `Object` | |
| nullable or optional | `T?` | |
| `oneOf`, `anyOf` | a wrapper class | The caller-facing accessor is `actualInstance`, typed `Object?`. |

## Dependencies

Runtime dependencies are `collection` (`^1.18.0`), `http` (`^1.2.0`), `http_parser` (`^4.0.0`) and `mime` (`^1.0.0`). JSON handling is `dart:convert`. The SDK keeps the dependency list this short on purpose, which is why `UuidValue` is its own class rather than a dependency on `package:uuid`.

Development dependencies are `lints`, `test`, `coverage`, `junitreport`, `opentelemetry` and `testcontainers_core`.

The client advertises only `gzip, deflate`. Dart has no standard-library brotli or zstd, and in the browser `fetch` owns content decoding, so the web transport does not decompress itself.

## Options

Pass options with `--additional-properties` or a config file. Options that apply to every language, such as `clientClassName`, `errorPrefix`, `generateTests`, `generateUnitTests` and `licenseId`, are shared by all twelve generators and are not repeated here. The Dart generator adds these:

| Option | Meaning |
|---|---|
| `packageName` | The package name, which is also the barrel file `lib/<packageName>.dart` and the product token in the default `User-Agent`. |
| `packageVersion` | The version written to the pubspec. Defaults to `1.0.0`. |
| `repositoryUrl`, `homepageUrl`, `documentationUrl`, `issueTrackerUrl` | Written to the pubspec fields `repository`, `homepage`, `documentation` and `issue_tracker`, and to the README. |
| `authors` | Written to the README only, because a pubspec has no authors field. |
| `envVarPrefix` | The prefix of the environment variables the authenticators read when no credential is passed. Defaults to the API title in upper snake case without a trailing `SDK`, the same in every language. |

## Idiom notes

- Models are plain classes with a `fromJson` factory, `toJson`, `==` and `hashCode` through `DeepCollectionEquality`, and a `toString` that lists the fields.
- An `allOf` schema with a discriminator is inheritance: the base is an `abstract class` whose `fromJson` decodes the subtype its discriminator value names, and each subtype `extends` it and writes its discriminator value. An unknown or missing discriminator value throws `SerializationException`. A base has no `copyWith`, because each subtype's `copyWith` takes more arguments than the base's would.
- Every operation takes an explicit positional `options` argument with no default, so a call with nothing to set is written `findPetsByStatus(null)`. This holds for an operation with no parameters of its own too, such as `getInventory(null)`: its options class holds only the per-call fields below, and adding a parameter to the operation later does not change the signature.
- Every options class carries `timeout` (milliseconds, end to end), `headers` (a header already set is replaced, in any case) and `maxRetries` (`0` turns retries off) for that call only. There is no per-call authenticator.
- The basic, bearer and API key authenticators read a credential you do not pass from an environment variable, once, when they are built. An argument wins, then the variable (an empty value counts as unset), then a callback (`tokenProvider`, `apiKeyProvider` or `credentialsProvider`) where there is one; a credential found nowhere fails with `ArgumentError` when the authenticator is built. The names are `{PREFIX}_{FIELD}` in upper snake case, for example `PETSTORE_TOKEN`; when two schemes have a field of the same name each is qualified with its scheme id, for example `PETSTORE_SERVICE_TOKEN_TOKEN`. The generated README lists the exact names of the API. The OAuth 2 and OpenID Connect authenticators take their client id and secret as required arguments and read no environment variable. The browser has no environment: pass `environment:` a map.
- The client takes one optional credential per distinct security scheme, named after the scheme (`Client(petStoreBearer: ..., adminBasic: ...)`), plus an optional default `authenticator` that serves an operation no scheme credential matches. Each operation uses the credential of the scheme its spec names (the first it lists that you configured); there is no per-call override. The base URL comes from the first credential given, and a client built with none is refused with `ArgumentError`.
- A cookie parameter is sent raw when every character is a legal RFC 6265 cookie-octet; otherwise the call fails with `ArgumentError` before anything is sent, and a `style: form` cookie is percent-encoded. Percent-encode a raw value yourself if you need to send one with other characters.
- `close()` is graceful and idempotent: requests already in flight finish, the HTTP client the SDK built is released when the last one completes, a request started afterwards raises `StateError`, and an `http.Client` you supplied is never closed. There is no per-call cancel handle.
- Constructors never validate: a model builds with any value of the right Dart type. Validation and the unknown-enum `ArgumentError` happen only when a document is decoded.
- A property that is a `$ref` to an enum with a `default` written next to the reference (OpenAPI 3.1) starts at that member, decodes to it when the key is absent and encodes it. An explicit `null` stays `null`.
- Authenticators have a synchronous `authHeaders()` and a `authHeadersAsync()` that returns a `Future`.
- Error classes end in `Exception`. Their `cause` is a plain field, for example `NetworkException(cause:)`, because Dart has no built-in exception chaining. The root exception class is sealed.
- `Client`, `DefaultApiClient` and `AbstractApiClient` take an extra trailing `http.Client`, which lets you supply your own.
- `BaseApi` and the serializers expose public helpers, because Dart has no visibility between libraries other than private-to-library.
- Server URLs are the top-level constants `SERVER_0`, `SERVER_1` and so on, with `allServers`.
- Builder setters name their parameter `val`.
- `TraceContextUtil.propagator` is a registration hook that you set if you want trace headers, because Dart cannot detect an optional OpenTelemetry dependency at run time.
- The call hook is `CallHook` with `start(CallInfo info, Map<String, String> headers)` returning a `CallObserver?`, whose `finish(CallOutcome outcome)` is called once when the call has ended; `chainCallHooks(List<CallHook>)` combines several. Set it with `ConfigurationBuilder.callHook(hook)` or with the `callHook:` argument of `Client` and `Client.withToken`. `CallInfo` carries `operationId` (as written in the spec), `method`, `urlTemplate`, `url` and `serverAddress`; `CallOutcome` carries `statusCode` (0 when no response arrived), `headers` (lower-cased, `null` when no response arrived) and `error`.
- OpenTelemetry is a recipe, not a shipped adapter: the `opentelemetry` package is pre-1.0, offers no histogram, its SDK counter records nothing and Dart cannot declare an optional dependency. The generated README holds the recipe, `OpenTelemetryCallHook`, which records one span per call and hands the request count and the duration in seconds to two functions you supply; the generated test suite compiles and runs that same text.
- An absent optional server description is an empty string, not `null`. It never goes on the wire.
- Testing your code: `abstract interface class <Group>ApiInterface` in the group file and `ClientInterface` in `client.dart`. `BaseApi` is public, so the implicit interface of the class would force a fake to implement its members; the explicit interface avoids that. A fake implements the interface and forwards `noSuchMethod`. The tests read the source files, because Dart has no run-time reflection.

## Known limits

- There is no per-call cancellation handle, so the call hook is never told of a cancelled call and the recipe has no `cancelled` error type.
- Declared-charset decoding handles latin1, ascii, utf-16 and windows-1252 only. Other charsets such as `shift_jis` fall back to lenient UTF-8, because Dart has no multi-charset standard library.
- Bare `number` is a lossy `double`, and `int64` beyond 2^53 is lossy on the web.
- Uploads and downloads are fully buffered.
- A union holding a value that is not one of its variants fails to serialize with a `SerializationException` naming the union. A string result under a binary content type is the decoded body. A deadline that passes while credentials are being obtained is a `NetworkTimeoutException` and the request is never sent.
- SOCKS proxies are rejected.
- Callbacks, webhooks, XML bodies, Link Objects and mutual TLS are out of scope; the generated README lists them under "Not supported". OpenAPI 3.1 support is tracked in epic #96, and 3.2 in epic #49.
- The warnings-fatal `make docs` target is in the generated Makefile but no test runs it.

## Under review

These are ideas from a review of the type mapping against current Dart. None is decided, and none changes what the SDK does today.

- Keep the floor at 3.11. The review found that no Dart release from 3.10 to 3.14 added a date, time, UUID, decimal or duration type to `dart:core`. Using the 3.10 dot shorthands or the 3.13 primary constructors in generated code would need a higher floor and is not a type improvement, so the review recommends against it.
- Decide what `format: date` should be. One option is to keep `DateTime` and document that it is UTC midnight, with a test that a host in a non-UTC zone never shifts the day. Another is an SDK-owned `LocalDate` class, which would be a public API addition that has to match the other eleven SDKs.
- Optionally map `format: uri` to `dart:core` `Uri`, if the rewriting of `[`, `]` and the apostrophe is acceptable. Otherwise record the decision to keep `String`.
- Move the `oneOf` wrappers to sealed classes with pattern matching, which Dart has had since 3.0. The current wrapper cannot be switched exhaustively. This is a public API change.
- Offer a streaming path for uploads and downloads, using `ByteStream` from `package:http`, which needs no new dependency.
- The `iso8601_duration.dart` file name is misleading, since it holds protobuf-JSON helpers around `Duration`. The generator's AGENTS file also describes an SDK-owned duration class that does not exist.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. The hook runs in the operation's own zone, after the credential await, so a hook that reads the zone's context sees the caller's span; the base-api tests prove this with a zone value.
