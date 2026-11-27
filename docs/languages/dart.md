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
| `number` with no format, `float`, `double` | `double` | Money loses exact decimal representation: `0.1 + 0.2` is `0.30000000000000004`. The README advises `format: decimal` for money. |
| `decimal`, on a `type: number` or a `type: string` | `Decimal` from the `decimal` package, exported by the SDK | Exact. The digits are read from the source text of the response, never through a `double`, and a number is written back as a bare JSON number in plain notation. The string form is read only from a JSON string and written back as one. A `Decimal` keeps no trailing zero, so `"12.50"` is written back as `"12.5"`. |
| `boolean` | `bool` | |
| `string` | `String` | |
| `string` with `format: date` | `DateTime` | Dart has no date-only type. The value is written as `YYYY-MM-DD` from the `DateTime`'s own year, month and day, so use a UTC value to be sure the day does not shift. |
| `string` with `format: date-time` | `DateTime` | Must be an RFC 3339 string with an offset. Written in a canonical offset form. |
| `string` with `format: time` | `String` | Validated as `HH:MM:SS` with optional fractional seconds. Dart has no time-of-day type outside Flutter. |
| `string` with `format: duration` | `Duration` | The wire form is the protobuf-JSON form such as `3600s`, limited to +-315576000000 seconds (a value beyond that is refused). Helper functions in `iso8601_duration.dart` convert between the two, even though the file name says ISO 8601. |
| `string` with `format: uuid` | `UuidValue` | A class the SDK defines itself, which validates on construction. It deliberately shadows the type of the same name in `package:uuid`, whose `fromString` does not validate. |
| `string` with `format: uri` | `String` | Checked as an absolute RFC 3986 URI when decoded. Not `Uri`, because `dart:core` `Uri` rewrites `[`, `]` and the apostrophe, which would change the text on a round trip. |
| `string` with `format: email`, `hostname` | `String` | |
| `string` with `format: ipv4`, `ipv6` | `String` | `InternetAddress` is in `dart:io`, which the models cannot import when the SDK builds for the web. The text is kept as given and read strictly: a leading zero, a prefix length, a zone id, brackets, whitespace or the wrong family fails the decode (D134). |
| `string` with `format: byte`, and a `format: binary` multipart file part | `Uint8List` | The body is held in memory. |
| `string` with `format: binary` as a raw request body | `BodySource` | The body is sent as it is read, see Streaming. |
| `string` with `format: binary` in a success response | `ByteStream`, a `Stream<List<int>>` | The body is read as you consume it, see Streaming. |
| `array` | `List<T>` | |
| `array` with `uniqueItems` | `Set<T>` | Order is not specified. |
| `additionalProperties` map | `Map<String, T>` | `T` is the declared value type; a free-form bucket keeps the any-type |
| free-form object | `Object` | |
| nullable or optional | `T?` | |
| `oneOf`, `anyOf` | a wrapper class | The caller-facing accessor is `actualInstance`, typed `Object?`. |

## Dependencies

Runtime dependencies are `collection` (`^1.18.0`), `decimal` (`^3.2.0`, for `format: decimal`), `http` (`^1.2.0`), `http_parser` (`^4.0.0`) and `mime` (`^1.0.0`). JSON handling is `dart:convert`. The SDK keeps the dependency list this short on purpose, which is why `UuidValue` is its own class rather than a dependency on `package:uuid`.

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
- The basic, bearer and API key authenticators read a credential you do not pass from an environment variable, once, when they are built. An argument wins, a callback you pass (`tokenProvider`, `apiKeyProvider` or `credentialsProvider`) comes next and is used even when the variable is set, and only a credential you passed neither way is read from the variable (an empty value counts as unset); a credential found nowhere fails with `ArgumentError` when the authenticator is built. The names are `{PREFIX}_{FIELD}` in upper snake case, for example `PETSTORE_TOKEN`; when two schemes have a field of the same name each is qualified with its scheme id, for example `PETSTORE_SERVICE_TOKEN_TOKEN`. The generated README lists the exact names of the API. The OAuth 2 and OpenID Connect authenticators take their client id and secret as required arguments and read no environment variable. The browser has no environment: pass `environment:` a map.
- The client takes one optional credential per distinct security scheme, named after the scheme (`Client(petStoreBearer: ..., adminBasic: ...)`), plus an optional default `authenticator` that serves an operation no scheme credential matches. Each operation uses the credential of the scheme its spec names (the first it lists that you configured); there is no per-call override. The base URL comes from the first credential given, and a client built with none is refused with `ArgumentError`.
- A cookie parameter is sent raw when every character is a legal RFC 6265 cookie-octet; otherwise the call fails with `ArgumentError` before anything is sent, and a `style: form` cookie is percent-encoded. Percent-encode a raw value yourself if you need to send one with other characters.
- `close()` is graceful and idempotent: requests already in flight finish, the HTTP client the SDK built is released when the last one completes, a request started afterwards raises `StateError`, and an `http.Client` you supplied is never closed. There is no per-call cancel handle.
- Constructors never validate: a model builds with any value of the right Dart type. Validation happens only when a document is decoded, and an unknown enum value decodes to `unknownDefaultOpenApi` (D117).
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
- With `generateExamples` on, the package ships `examples/quickstart.dart`, an `async main` that imports the package by its own name, builds a client with `Client.withToken` from `{PREFIX}_BASE_URL` and `{PREFIX}_TOKEN` (a missing variable throws a `StateError`), calls the example operation and closes the client in a `finally` block. `make examples` runs `dart analyze --fatal-warnings --fatal-infos` over `examples/`, and `make examples-run` runs it with `dart run`. The README Usage fence is the quickstart's statements.

## Recipes

These are answers to questions that come up often, each built from what the generated SDK already has. None of them adds API.

### Hooks around every call

The call hook added by [#158](https://github.com/mridang/openapi-generator-plus/issues/158) is the one place to edit the request headers before the send and to observe the status or the error afterwards, for every operation and for the client as a whole. It is optional and does nothing when it is not set. It runs once per operation call however many sends happen inside it (retries, the one replay after a `401`), once for the plain and the `WithHttpInfo` variant alike, and also when you supply your own transport. `start` receives the call information (`operationId` as written in the spec, `method`, `urlTemplate`, `url` without its query string, `serverAddress`) and the final headers, which it can read and replace, and it returns the observer to call when the call ends or nothing. The observer's `finish` receives the status code (0 when no response arrived), the lower-cased response headers (none when no response arrived) and the error the caller receives (none on success). An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed; the hook never changes the result or the error. `CallHook` is the name to look for in this SDK.

```dart
class SourceHook implements CallHook {
  @override
  CallObserver? start(CallInfo info, Map<String, String> headers) {
    headers['X-Source'] = 'nightly-job';
    return null;
  }
}

final client = Client.withToken(
  host: 'https://api.example.com',
  accessToken: 'your-token',
  callHook: SourceHook(),
);
```

To see every send of a call rather than the call as a whole (for example each retry), wrap the transport you hand to the SDK instead: see "Custom HTTP client" in the generated README. A transport you supply is never closed by the SDK, and the hook above still fires with it.

### Serving another region or tenant

A client is cheap, and its host, credentials, retry settings and connections belong to it. To call another region or another tenant, build another client; there is no per-call server URL or per-call HTTP client, and the per-call options stay `timeout`, `headers` and `maxRetries`.

```dart
final eu = Client.withToken(host: 'https://eu.api.example.com', accessToken: 'eu-token');
final us = Client.withToken(host: 'https://us.api.example.com', accessToken: 'us-token');
```

Call `close()` on each when you are done.

### Renaming types, fields and parameters

The names come from the specification, so the seam is the normaliser: the `RUN_SCRIPT` rule runs your own JavaScript over the document before generation (see [how it works](../how-it-works.md#renaming-with-a-script)). A schema renamed to `Animal` is generated as `lib/src/models/animal.dart`, and this language's own casing, escaping of reserved words and file naming are applied to the new name like to any other. The SDK has no configuration map of names and never reads a vendor extension for a name. The name of a property or a parameter is also its name on the wire, so renaming one in the script renames it in the requests and responses as well: there is no rename that changes only the SDK name.

### Status, headers and the raw body

Every operation has a `...WithHttpInfo` variant that returns the status code, the lower-cased response headers, the raw body and the decoded data. Every failure that carries an HTTP response is an `ApiException` that holds the status code, the response headers and the response body. A live response object is exposed only for a binary response: its operation returns a `ByteStream` that carries the status code and the lower-cased headers, and `...WithHttpInfo` returns it in `data` with an empty `rawBody`. To see or change what goes over the wire, use the call hook above or the transport you supply.

```dart
final result = await client.pet.addPetWithHttpInfo(pet);
final status = result.statusCode;
final etag = result.headers['etag'];
final raw = result.rawBody;

try {
  await client.pet.addPet(pet);
} on ApiException catch (e) {
  print('${e.statusCode} ${e.responseHeaders} ${e.responseBody}');
}
```

### Streaming

An operation whose success response is binary returns a `Future<ByteStream>` that completes when the response headers arrive; the body is read as the stream is consumed, so a large download never has to fit in memory. `ByteStream` is a single-subscription `Stream<List<int>>` with `statusCode`, the lower-cased `headers`, `bytesDelivered`, an idempotent `close()` that aborts the connection instead of draining it (cancelling the subscription closes it too), and `readAll()`, which reads the rest into memory and leaves the stream closed. Its string form shows only the status code and the bytes delivered.

`package:http` exports a class named `ByteStream` too, so a file that imports `package:http/http.dart` without a prefix and this package meets an ambiguous name; import one of them with `as`.

The transport gains one optional capability, `StreamingApiClient.sendStreaming`, next to the buffered `sendRequest`. It is a second interface that the SDK checks at call time, as the capability interfaces `RetryingApiClient` and `AdmittingApiClient` already are, and not a default method on `ApiClient`: Dart does not inherit a default body into a class that `implements` the interface, so a default would stop every existing custom transport from compiling. A transport that does not implement it keeps serving buffered calls, and a stream call through it throws an `UnsupportedError` naming the transport. `DefaultApiClient` implements it on both the `dart:io` and the web transport. `TransportOptions.builder().idleTimeout(ms)` (default 60000, `0` or `null` disables it) bounds the gap between two chunks of the body and raises `NetworkTimeoutException`; the call `timeout` stops at the headers. A connection reset or a body shorter than its `Content-Length` raises `StreamException`, a `NetworkException` with status 0. `client.close()` cancels the open streams, and the next read of each raises the `StateError` of a closed client.

An operation whose request body is raw binary takes a `BodySource`, made with `BodySource.of(bytes)`, `BodySource.ofFile(path)`, `BodySource.fromFactory(factory)` or, for a stream that can be read once, `BodySource.once(stream)`. The body is read as it is sent and never buffered: a known length goes out as `Content-Length` and an unknown length is sent chunked. A replayable body is opened again for a retry and for a 307 or 308; a single-use body is never retried and a 307 or 308 is refused with `Refusing to replay a streamed request body`. A Dart stream cannot be closed, so the SDK reads a stream you pass to `once` to its end and cancels it only when the request is abandoned. `BodySource.ofFile` takes a path and is not available in the browser, where the transport also has to collect the body before sending it.

### JSON Lines and server-sent events

An operation whose success response is JSON Lines or `text/event-stream` has no buffered method and no `WithHttpInfo` variant. It has `<operation>Stream`, which takes the operation's parameters, its options object and an optional trailing `StreamOptions`, and returns a `Future<JsonLinesStream<T>>` or a `Future<SseStream<T>>` of `ServerSentEvent<T>`; the future completes when the first response headers arrive, and a failure of that request is raised there. Both streams are single-subscription `Stream`s: iterate them with `await for`, and call `close()` (or cancel the subscription) when you stop early. `StreamOptions` is a const class with named arguments (`idleTimeout`, `maxReconnects`, `reconnectDelay`, `lastEventId`, `maxEventBytes`, all in milliseconds or bytes). An SSE stream reconnects with a fresh call (hook, headers and token again) and `Last-Event-ID`; the wait is the server's `retry`, then `Retry-After` on 429, 502, 503 and 504, then `reconnectDelay` doubling to 30 seconds with up to 25% jitter. The item decoder is the model's `fromJson` for a model type and the raw JSON value cast to the type for a primitive; an array or map item type is decoded the same raw way and is not typed element by element. Gzip and deflate bodies are decoded as they arrive.

### Pagination

An operation that returns a list one page at a time has a `<operation>Pages` method beside its plain method, in the class and in the interface. It takes the same parameters in the same order and returns a `Paginator<T, C>` (`T` the item type, `C` the type of the cursor parameter: a string for a token or id cursor, an int for an offset or a page number, and `void` for a list whose next page is named by its `Link` header or by a next URL in its body). `items()` is a `Stream<T>` and `pages()` a `Stream<Page<T, C>>`; `Page` has `items`, `nextCursor` and `result`, the `ApiResult` of that page. Both streams are lazy and send one request per page, never ahead of the reader, so `await for`, `StreamIterator` and `take` read exactly what they need and stopping early leaves nothing to close. The cursor is an optional query parameter, so it lives in the operation's options class, which gains a `withCursor` method for a token, id, offset or page-number operation. A failed page raises its SDK error after the earlier items were delivered and ends the stream, and a repeated cursor or a `Link` or next URL to another origin raises `PaginationException`, a direct child of the root exception. Cancelling a subscription ends the iteration but cannot abort a request that is in flight, because a Dart `Future` cannot be cancelled.

The styles are the token cursor, the id cursor, the offset, the page number, the `Link` header and the next URL in the body. How each style ends and resumes: an id list passes the `last_id` or `next_cursor` of each page as the starting cursor of the next request and ends when a declared `has_more` is false. An offset list starts at the offset you pass, sends the offset past the items it received on each next request, and ends on an empty page or when a declared `has_more` is false. A page-number list sends the page you pass, or none, and then asks for the page after each one, and ends on an empty page or when a declared `has_more` is false. A next URL list takes the URL in the body of each page, follows it only within the origin of the client, and ends when the property is absent, `null` or empty. A cursor, offset or page that does not advance raises `PaginationException` as a token cursor does.

A list whose page body carries the next URL in a property (a next URL list) is followed in the same way as a `Link` list: the URL is taken from the body of each response, a next URL that leaves the scheme, host or port of the client is refused with `PaginationException`, nothing is sent to it, and such a method cannot resume from a saved position.

## Known limits

- There is no per-call cancellation handle, so the call hook is never told of a cancelled call and the recipe has no `cancelled` error type.
- Declared-charset decoding handles latin1, ascii, utf-16 and windows-1252 only. Other charsets such as `shift_jis` fall back to lenient UTF-8, because Dart has no multi-charset standard library.
- Bare `number` is a lossy `double` (`format: decimal` is an exact `Decimal`), and `int64` beyond 2^53 is lossy on the web.
- A multipart upload is fully buffered. A raw binary upload is sent as it is read, except in the browser, where `fetch` cannot stream a request body and the transport collects the body first, and where a file path is not available.
- A union holding a value that is not one of its variants fails to serialize with a `SerializationException` naming the union. A string result under a binary content type is the decoded body. A deadline that passes while credentials are being obtained is a `NetworkTimeoutException` and the request is never sent.
- SOCKS proxies are rejected.
- Callbacks, webhooks, XML bodies, Link Objects and mutual TLS are out of scope; the generated README lists them under "Not supported". OpenAPI 3.1 support is tracked in epic #96, and 3.2 in epic #49.
- The warnings-fatal `make docs` target is in the generated Makefile but no test runs it.
- An optional and nullable property has no third state: an absent value and an explicit `null` are the same, by decision D112. A three-state type for PATCH-style APIs is declined for now.
- Operations are asynchronous only, as `Future`s, by decision D111. There is no blocking variant of an operation, because each SDK follows the style of its own language. Call it from your language's async machinery.
- An `integer` with no format is 64 bits, the same as `int64`, by decision D113 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)). A value above 2^31, such as a large identifier, decodes exactly.

## Under review

These are ideas from a review of the type mapping against current Dart. None is decided, and none changes what the SDK does today.

- Keep the floor at 3.11. The review found that no Dart release from 3.10 to 3.14 added a date, time, UUID, decimal or duration type to `dart:core`. Using the 3.10 dot shorthands or the 3.13 primary constructors in generated code would need a higher floor and is not a type improvement, so the review recommends against it.
- Decide what `format: date` should be. One option is to keep `DateTime` and document that it is UTC midnight, with a test that a host in a non-UTC zone never shifts the day. Another is an SDK-owned `LocalDate` class, which would be a public API addition that has to match the other eleven SDKs.
- Move the `oneOf` wrappers to sealed classes with pattern matching, which Dart has had since 3.0. The current wrapper cannot be switched exhaustively. This is a public API change.
- The `iso8601_duration.dart` file name is misleading, since it holds protobuf-JSON helpers around `Duration`. The generator's AGENTS file also describes an SDK-owned duration class that does not exist.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. The hook runs in the operation's own zone, after the credential await, so a hook that reads the zone's context sees the caller's span; the base-api tests prove this with a zone value.

A security requirement that names two schemes together (for example a bearer token and an API key) sends the credentials of both; the client must hold both. `multipart/mixed` and other declared multipart subtypes keep their subtype and gain a boundary. `additionalProperties` returns a copy on every read, and `OAuth2TokenManager.stop()` makes later token requests fail with `OAuth2TokenException`.
