# Java

The Java generator (`java-plus`, class `BetterJavaCodegen`) produces a Maven project. This page lists the Java versions it supports, how OpenAPI types map to Java types, what the generated project depends on, which generator options it reads, and where it differs from the other eleven SDKs. The shared pipeline is in [how-it-works.md](../how-it-works.md) and the cross-language type table is in [types.md](../types.md).

## Supported versions

| What | Version | Where it comes from |
|---|---|---|
| Minimum Java | 21 | `<release>21</release>` in the generated `pom.xml` |
| Build tool | Maven 3.9 or newer | the generated README |
| JDK the generator's own checks run on | Temurin 25 | the spec container image |

Java 21 is a long-term-support release, and it is the floor because the generated code is compiled with `-Xlint:all`, `failOnWarning`, Error Prone and NullAway, and is written for the language features of 21. The floor is written once, in the pom. The support policy is to keep the floor at the oldest Java release that still receives updates and to move it up when that release reaches end of life; the floor only moves together with a regenerated golden and an update of this page.

## Type mapping

| OpenAPI | Java type | Notes |
|---|---|---|
| `string`, `format: date` | `java.time.LocalDate` | |
| `string`, `format: date-time` | `java.time.OffsetDateTime` | keeps the offset |
| `string`, `format: time` | `java.time.LocalTime` | no offset |
| `string`, `format: duration` | `java.time.Duration` | sent and read as a protobuf-JSON duration string such as `3600s`, the same as the other SDKs; calendar durations (months, years) cannot be held |
| `string`, `format: uuid` | `java.util.UUID` | written in lower case |
| `string`, `format: byte` | `byte[]` | base64 on the wire |
| `string`, `format: binary` | `BodySource` for a raw request body; `ByteStream` (an `InputStream`) for a response | the response body is read as you consume it and the request body is sent as it is read, see Streaming |
| `string`, `format: uri` | `java.net.URI` | `uri-reference` and `uri-template` stay `String` |
| `string`, `format: email`, `hostname`, and plain `string` | `String` | no dedicated type |
| `string`, `format: ipv4`, `ipv6` | `String` | the JDK has no address parser that accepts only a literal before Java 22, so the text is kept as given and read strictly: a leading zero, a prefix length, a zone id, brackets, whitespace or the wrong family fails the decode (D134) |
| `string`, `format: decimal` | `BigDecimal` | read from a JSON string only and written as a JSON string |
| `integer`, `format: int32` | `Integer` | |
| `integer`, `format: int64` | `Long` | |
| `integer`, no format | `Long` | 64 bits, the same as `int64` |
| `number`, `format: float` | `Float` | |
| `number`, `format: double` | `Double` | binary floating point, so not for money |
| `number`, no format, or `format: decimal` | `java.math.BigDecimal` | exact; the serializer round-trips the JSON number so no precision is lost |
| `boolean` | `Boolean` | |
| `array` | `java.util.List` | built as an `ArrayList`, copied into an unmodifiable list when the model is built |
| `array` with `uniqueItems` | `java.util.Set` | built as a `LinkedHashSet`, so it keeps insertion order; the SDK does not sort it |
| `object` with `additionalProperties` | `java.util.Map` | built as a `HashMap`. `T` is the declared value type; a free-form bucket keeps the any-type |
| free-form `object` | `Object` | whatever Jackson decodes: maps, lists and scalars |
| nullable property | the boxed type | no separate "absent" state; a required and nullable property must be present when decoding and is always written, `null` included |
| `enum` | nested Java `enum` | an unknown value decodes to `UNKNOWN_DEFAULT_OPEN_API` (D117), which cannot be written |
| `oneOf`, `anyOf` | a wrapper class | see Idioms |
| file upload and download | `BodySource` upload (a multipart file part stays `InputStream`), `ByteStream` download | same as `binary` |

A schema named `Error` is generated as `ModelError`, because `Error` would shadow `java.lang.Error`. `true`, `false`, `null` and `_` are escaped when used as identifiers.

## Dependencies

| Dependency | Why |
|---|---|
| `jackson-core`, `jackson-annotations`, `jackson-databind` 2.19.4 | JSON encoding and decoding |
| `jackson-datatype-jsr310` 2.19.4 | `java.time` support in Jackson |
| `jackson-databind-nullable` 0.2.6 | the nullable support type used by the models |
| `jsr305` 3.0.2 | `@Nullable` annotations, checked by NullAway at build time |
| `opentelemetry-api` 1.62.0 (optional) | W3C trace context on outgoing requests, and the `OpenTelemetryCallHook` adapter, when OpenTelemetry is on the class path |
| `org.brotli:dec` 0.1.2 and `zstd-jni` 1.5.6-4 (optional) | decoding `br` and `zstd` responses; without them only `gzip` and `deflate` are advertised |
| `spotbugs-annotations`, `jakarta.annotation-api` (provided scope) | compile-time annotations only |

HTTP is the JDK's own `java.net.http.HttpClient`, so there is no HTTP library to depend on. The generated project also wires Spotless (google-java-format), Checkstyle, SpotBugs, JaCoCo, Error Prone and NullAway, and tests run on JUnit with AssertJ and Testcontainers; none of those are runtime dependencies. The generator only accepts dependencies that are pure JVM libraries: `zstd-jni` is the one native-code exception and is optional.

## Generator options

| Option | Default | Effect |
|---|---|---|
| `invokerPackage` | `org.openapitools` | root Java package of the SDK |
| `sourceFolder` | `src/main/java` | source directory inside the project |
| `groupId` | the invoker package | Maven group |
| `artifactId` | last segment of the invoker package plus `-client` | Maven artifact |
| `artifactVersion` | `1.0.0` | Maven version; not derived from the spec |
| `repositoryUrl` | none | written to the pom `scm` and `url` and to the README |
| `homepageUrl` | the repository URL | project home page |
| `authors` | none | comma-separated names, one pom developer each |
| `documentationUrl` | none | linked from the README |
| `issueTrackerUrl` | none | pom `issueManagement` and the README |
| `clientClassName` | `Client` | name of the client facade |
| `errorPrefix` | `OpenAPI` | prefix of the root error type; Java appends `Exception` |
| `envVarPrefix` | the API title without a trailing `SDK`, in upper snake case, else `API` | prefix of the environment variables the per-scheme authenticators read their credentials from |
| `generateTests` | `true` | emit the full test suite, including the spec-coupled tests |
| `generateUnitTests` | `false` | emit only the spec-independent unit tests |

## Idioms

- **Errors.** Every failure derives from the root `...Exception` and, for HTTP failures, from `ApiException`, which carries the status, the lower-cased response headers, the body, the request method and the request path without its query string; every error constructor, from `ApiException` down to each status exception, takes the method and path as its last two arguments. `NetworkException` and `NetworkTimeoutException` mean no response arrived; `TooManyRequestsException` is HTTP 429. A response body that does not decode raises `SerializationException`, which carries the status code, the headers, the raw body and the request method and path without its query string, so you can log the response that could not be read. `SerializationException` raised with no response behind it, such as a bad request body, has status `0` and none of those fields. Mistakes by the caller raise `IllegalArgumentException` or `IllegalStateException`.
- **Retries.** Two retries by default (`TransportOptions.builder().maxRetries(n)`, `0` turns them off), after a network error before any response and after 408, 409, 429 and 5xx except 501. The wait starts at 0.5 seconds, doubles up to 8 seconds, loses up to 25% to jitter and is replaced by `Retry-After` (at most 60 seconds). Retries share the one end-to-end timeout, and a request with a stream body is never retried.
- **Timeouts and cancellation.** `timeout` is one end-to-end budget in milliseconds with no default. A thread interrupt raises `java.util.concurrent.CancellationException`, never the timeout error.
- **Authentication.** Basic, bearer and API key authenticators, plus OAuth 2 (client credentials, password, authorization code, implicit) and OpenID Connect. After a 401 from an OAuth 2 or OpenID Connect call the cached access token is dropped and the request is sent once more. The authenticator generated for each security scheme reads a credential that is left out (`null` or empty) from an environment variable, read once when the authenticator is built: `{PREFIX}_{FIELD}` in upper snake case, such as `PETSTORE_TOKEN`, where the prefix is the `envVarPrefix` option or the API title, and the scheme id is added to the name when two schemes share a field name. A value you pass wins, an empty variable counts as unset, and a credential found nowhere fails the constructor with `IllegalArgumentException`. `BearerAuthenticator(host, tokenProvider)` takes a `Supplier<String>` instead of a token, which runs before every request, and `BearerAuthenticator(host, token, tokenProvider)` takes exactly one of the two; passing both is refused. The three general authenticators never read the environment, and the generated README lists the variable of every scheme. **Credentials per scheme.** `Client.builder(host)` takes one optional credential per distinct security scheme, a setter named after the scheme in camel case (`petStoreBasic`, `apiKeyHeader`) typed as that scheme's authenticator, and `transportOptions(...)`. Each operation uses the credential of the first scheme in its own security list that the client holds; an operation none of whose schemes the client holds falls back to the single authenticator of `new Client(authenticator)` when there is one and is sent without a credential otherwise, and an operation that declares `security: []` never sends one. There is no per-call scheme override, so a `<Operation>Options` object carries no authenticator.
- **Immutability.** Model fields are `final`, built with `Model.builder()` and copied with `toBuilder()`. Collections are copied into unmodifiable ones. A property that is a `$ref` to a string enum with a `default` written next to the `$ref` starts at that default, so a model built without it and a payload that omits it both carry it; a `$ref` to anything other than an enum with a sibling default is not initialised.
- **Unions.** A `oneOf` or `anyOf` model is a wrapper with a typed factory per variant (`Union.ofVariant(v)`), `isVariant()`, and `asVariant()` returning an `Optional`; the wrapper also exposes `getActualInstance()`. A discriminated parent is an abstract base class with Jackson subtype annotations. An operation with several `2xx` responses returns a union decoded by the status that arrived.
- **Optional arguments.** Java has no default arguments, so optional parameters are `@Nullable` and per-operation settings go in a generated `<Operation>Options` object passed last. Every operation has one, even an operation with no query, header, form or cookie parameter and no security. Per-call `timeout`, `maxRetries` and `headers` live there, and omitting the object (or passing `null`) uses the transport's settings.
- **Lifecycle.** The client is `AutoCloseable`, and `close()` is graceful and idempotent. The first call marks the client closed and returns at once; requests already in flight finish, and the transport the SDK created is released when the last of them completes (immediately when none is in flight). A later `close()` does nothing. A request issued after closing throws `IllegalStateException` with the message `ApiClient has been closed and can no longer send requests`. A `java.net.http.HttpClient` you passed in through `TransportOptions` is never closed.
- **Streaming.** An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns `ByteStream`, a `final` class that extends `InputStream`, from the one operation method (there is no second method), and `...WithHttpInfo` returns `ApiResult<ByteStream>` with an empty `rawBody()`. A `ByteStream` has `getStatusCode()`, the lower-cased `getHeaders()`, an idempotent `close()` that aborts the connection instead of draining it, a `readAllBytes()` that reads the rest and leaves the stream closed, and a `toString()` that shows only the status and the bytes delivered. The transport gets `ApiClient.sendStreaming(method, url, headers, body, timeoutMillis, maxRetries, idleTimeoutMillis)`, which returns an `ApiStreamResponse` (status, headers, open body); its default implementation throws `UnsupportedOperationException` naming the transport, so an injected `ApiClient` that only implements `sendRequest` keeps working for buffered calls. `DefaultApiClient` runs the buffered call's header phase (retries, redirects, the end-to-end `timeout`, the header rules) and stops there: the `timeout` ends at the response headers, `TransportOptions.builder().idleTimeout(millis)` (default 60 seconds, `0` or `null` disables) bounds the gap between two reads of the body and raises `NetworkTimeoutException`, and nothing is retried after the first body byte. A non-2xx status raises the usual typed error with its body read up to 64 KiB. A reset, or a body shorter than its `Content-Length`, raises `StreamException` (a `NetworkException`, status 0). `client.close()` cancels the streams still open; their next read raises `IllegalStateException` ("ApiClient has been closed and can no longer send requests").
- **JSON Lines and server-sent events.** An operation whose success response is `application/x-ndjson`, `application/jsonl`, `application/jsonlines` or `text/event-stream` has no buffered method and no `WithHttpInfo` variant. It has `<operation>Stream(...args, options, @Nullable StreamOptions)` (and an overload without the `StreamOptions`) returning `JsonLinesStream<T>` (an `Iterator<T>` and `AutoCloseable`, with `stream()`) or `SseStream<T>` (an `Iterator<ServerSentEvent<T>>` and `AutoCloseable`, with `asPublisher()` for a `Flow.Publisher`), both with `getStatusCode()`, `getHeaders()`, an idempotent `close()` and a redacted `toString()`. `ServerSentEvent<T>` is a record (`id`, `event` defaulting to `message`, `data`, `retry`, `raw`). `StreamOptions` is a fluent class (`idleTimeout`, `maxReconnects`, `reconnectDelay`, `lastEventId`, `maxEventBytes`; milliseconds and bytes). The parser follows the WHATWG grammar (`EventStream`), a decode failure is a `SerializationException` naming the 1-based line (JSON Lines), and the generator option `sseTerminator` ends a feed on a named data value. An event stream reconnects after a clean end, a network error or an idle timeout with fresh headers and `Last-Event-ID`, up to `maxReconnects` times in a row (5; 0 for a request with a body; `-1` unlimited), waiting the server's `retry` value, else `Retry-After` on 429, 502, 503 and 504, else `reconnectDelay` doubling to 30 seconds with jitter; an event named `error` raises `ApiException`, and a 204, a 4xx, a response that is not `text/event-stream`, a cancellation or `client.close()` never reconnects.
- **Uploads.** An operation whose request body is raw binary takes a `BodySource` (`final`, in the SDK package) in every signature (the operation, `...WithHttpInfo`, the `...Api` interface and the client): `BodySource.of(byte[])`, `BodySource.ofFile(Path)` and `BodySource.fromSupplier(Supplier<? extends InputStream>)` (optionally with a length) are replayable, `BodySource.once(InputStream)` (optionally with a length) is single use. The body is sent as it is read, never buffered: a known length sends `Content-Length`, an unknown one is chunked. A replayable body is opened again for a retry after a 5xx, a 429 or a network error and for a 307 or 308; a single-use body is never retried (the original error is raised unchanged), a redirect that would need it again raises `ApiException` with `Refusing to replay a streamed request body`, and a 401 token refresh and an SSE reconnect follow the same rule. The SDK never closes a stream passed to `once`, and closes the streams it opened from a file or a factory when the call ends. `toString()` shows the kind and the length, never the content. A custom `ApiClient` receives the `BodySource` as the `body` and reads it with `open()`. Multipart bodies, including their `InputStream` file parts, stay buffered.
- **Pagination.** A GET list that the shared detector finds paged (a token cursor query parameter with a next-cursor property of the same type, or a `Link` header declared on the response; D123, D124) has `<operation>Pages(...args, options)` (and an overload without the options) beside its plain method, in the class and in the `...Api` interface. It returns `Paginator<T, C>` (`T` the item type, `C` the cursor type, `Void` for a `Link` list), which is an `Iterable<T>` with `stream()` and `pages()` (an `Iterable<Page<T, C>>`); `Page<T, C>` is a record of `items`, `nextCursor` (null on the last page, always null for `Link`) and `result` (the `ApiResult` of the page). The cursor is an optional query parameter, so it lives in the `<Operation>Options` object, whose `copy()` (generated for a paged operation only) gives each page its own cursor and leaves the caller's options untouched. The method sends nothing; each iteration builds a fresh page iterator, so a second loop starts again, a page is an ordinary `...WithHttpInfo` call, and a failed page raises from the read that needed it. A cursor equal to the one just sent, or a `Link` that points back at the page just read or leaves the origin of the client, delivers the page it came with and raises `PaginationException` (a direct child of the root exception, not an `ApiException`) from the next read. A `Link` page is requested through a private `<operation>Page(...args, pageUrl)` that replaces the path with the absolute URL, so authentication, retries and the call hook apply, and `RFC 8288` parsing is `LinkHeader`. Cancel by interrupting the reading thread.
- **Cookie parameters.** A cookie parameter is sent exactly as given when every character is a legal RFC 6265 cookie-octet (that includes `%`, so a value you percent-encoded yourself goes out unchanged). A value with a space, double quote, comma, semicolon, backslash, control character or non-ASCII character throws `IllegalArgumentException` before any request, and you percent-encode it first. The SDK never encodes a cookie value for you.
- **Models are not validated when built.** The builder and the constructors never check a value against a schema constraint, and the type system already stops a wrong type or an enum string that is not a constant from reaching them. A payload is checked only when it is decoded: a wrong-typed value, an enum string outside the declared set and a missing or `null` required property fail with `SerializationException`. A model built without a required property still fails in `build()` (the required-argument constructor and the builder are the way to make one).
- **Raw responses.** Every operation has a `...WithHttpInfo` variant returning `ApiResult<T>`.
- **Custom transport.** `TransportOptions.builder().httpClient(client)` sends requests through your own `java.net.http.HttpClient`; combining it with `proxy`, `caCertPath` or `verifySsl(false)` fails at `build()`.
- **Call hook.** `CallHook` is one small hook around every operation call: `start(CallInfo, Map<String, String> headers)` runs once per call after the headers are final and may edit them, and returns a `CallObserver` (or `null`) whose `finish(CallOutcome)` runs exactly once on every way the call can end. Set it with `Configuration.builder().callHook(hook)` or `Client.builder(host).callHook(hook)`; `CallHook.chain(a, b)` combines hooks. `CallInfo` is a record of `operationId` (as written in the spec), `method`, `urlTemplate`, `url` (no query string or fragment) and `serverAddress`; `CallOutcome` is a record of `statusCode` (`0` when no response arrived), the lower-cased `headers` (`null` when none arrived) and the `error` the caller receives. The hook runs on the calling thread, above the transport, so it also fires when you supply an `HttpClient`. An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed.
- **OpenTelemetry adapter.** `OpenTelemetryCallHook.create()` (the global instance) or `create(openTelemetry)` is a `CallHook` that records one span (named with the operation id, kind client), the counter `http.client.request.count` (unit `{request}`) and the histogram `http.client.request.duration` (unit `s`) per call, with `http.request.method`, `url.template`, `server.address`, `http.response.status_code` and `error.type`. It uses the OpenTelemetry API only, writes `traceparent` itself and needs `opentelemetry-api` on the class path; the rest of the SDK does not.
- **GraalVM.** The SDK ships reflection metadata for every model, so it works in a `native-image` build; the optional brotli and zstd decoders do not.
- Testing your code: `<Group>ApiInterface` (a `...Interface.java` file per group, registered in `BetterJavaCodegen` with the suffix `Interface.java`) and `ClientInterface extends AutoCloseable` with `get<Group>()` accessors beside the public fields. The convenience overloads are `default` methods that delegate to the full form, so a fake implements one form per operation; every class method carries `@Override` because Error Prone's `MissingOverride` is an error at `failOnWarning`.
- With `generateExamples` on, the project ships `src/examples/java/<package>/examples/Quickstart.java`, a `main` that builds a client from `{PREFIX}_BASE_URL` and `{PREFIX}_ACCESS_TOKEN`, calls the example operation in a try-with-resources block and closes the client. The pom adds `src/examples/java` as a test source root, so `make examples` (`mvn test-compile`) builds it with Error Prone and NullAway, Checkstyle and Spotless cover it, and it never ships in the jar; `make examples-run` runs it with `mvn exec:java`.
- `OAuth2TokenManager.stop()` ends the manager: a later token request throws `OAuth2TokenException` with the message "The token manager has been stopped." and sends nothing. Stopping twice does nothing.

## Recipes

These are answers to questions that come up often, each built from what the generated SDK already has. None of them adds API.

### Hooks around every call

The call hook added by [#158](https://github.com/mridang/openapi-generator-plus/issues/158) is the one place to edit the request headers before the send and to observe the status or the error afterwards, for every operation and for the client as a whole. It is optional and does nothing when it is not set. It runs once per operation call however many sends happen inside it (retries, the one replay after a `401`), once for the plain and the `WithHttpInfo` variant alike, and also when you supply your own transport. `start` receives the call information (`operationId` as written in the spec, `method`, `urlTemplate`, `url` without its query string, `serverAddress`) and the final headers, which it can read and replace, and it returns the observer to call when the call ends or nothing. The observer's `finish` receives the status code (0 when no response arrived), the lower-cased response headers (none when no response arrived) and the error the caller receives (none on success). An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed; the hook never changes the result or the error. `CallHook` is the name to look for in this SDK.

```java
CallHook hook = (info, headers) -> {
    headers.put("X-Caller", "billing");
    long started = System.nanoTime();
    return outcome -> System.out.println(
            info.operationId() + " " + outcome.statusCode() + " "
                    + (System.nanoTime() - started) / 1_000_000 + " ms");
};
Client client = Client.builder("https://api.example.com")
        .callHook(hook)
        .build();
```

To see every send of a call rather than the call as a whole (for example each retry), wrap the transport you hand to the SDK instead: see "Custom HTTP client" in the generated README. A transport you supply is never closed by the SDK, and the hook above still fires with it.

### Serving another region or tenant

A client is cheap, and its host, credentials, retry settings and connections belong to it. To call another region or another tenant, build another client; there is no per-call server URL or per-call HTTP client, and the per-call options stay `timeout`, `headers` and `maxRetries`.

```java
try (Client eu = Client.withToken("https://eu.api.example.com", "eu-token");
     Client us = Client.withToken("https://us.api.example.com", "us-token")) {
    eu.pet.addPet(new Pet());
    us.pet.addPet(new Pet());
}
```

Closing one client does not affect the other.

### Renaming types, fields and parameters

The names come from the specification, so the seam is the normaliser: the `RUN_SCRIPT` rule runs your own JavaScript over the document before generation (see [how it works](../how-it-works.md#renaming-with-a-script)). A schema renamed to `Animal` is generated as `models/Animal.java`, and this language's own casing, escaping of reserved words and file naming are applied to the new name like to any other. The SDK has no configuration map of names and never reads a vendor extension for a name. The name of a property or a parameter is also its name on the wire, so renaming one in the script renames it in the requests and responses as well: there is no rename that changes only the SDK name.

### Status, headers and the raw body

Every operation has a `...WithHttpInfo` variant that returns the status code, the lower-cased response headers, the raw body and the decoded data. Every failure that carries an HTTP response is an `ApiException` that holds the status code, the response headers and the response body. A live response object is not exposed, because that would need streaming (see [decisions](../decisions.md)); to see or change what goes over the wire, use the call hook above or the transport you supply.

```java
ApiResult<Pet> result = client.pet.addPetWithHttpInfo(new Pet());
int status = result.statusCode();
String etag = result.headers().get("etag");
String raw = result.rawBody();

try {
    client.pet.addPet(new Pet());
} catch (ApiException e) {
    int failed = e.getStatusCode();
    Map<String, String> headers = e.getResponseHeaders();
    String body = e.getResponseBody();
}
```

## Known limits

- A request body and a JSON, text or form response body are read whole into memory. Only a binary response is a stream; there is no JSON Lines or server-sent events API yet.
- Schema constraints (`pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems`, `maxItems`) are not checked on the client; the server validates them.
- `dependentRequired` and `dependentSchemas` are not enforced.
- Webhooks and callbacks, XML bodies, Link Objects and `mutualTLS` security schemes are not generated. For mutual TLS, configure the certificate on the `HttpClient` you pass in.
- Only `http://` and `https://` proxies are accepted; SOCKS is rejected.
- A property that is optional and one that is nullable both read as `null`.
- A header sent on several lines is joined into one string with `, `.
- The SDK writes no logs and has no logging hook.
- No `LICENSE` file is generated next to the pom.
- Operations are blocking only, as a plain method call that blocks the calling thread, by decision D111. There is no asynchronous variant of an operation, because each SDK follows the style of its own language. Run the call on your own thread or task when you need concurrency.
- An optional and nullable property has no third state: an absent value and an explicit `null` are the same, by decision D112. A three-state type for PATCH-style APIs is declined for now.
- An `integer` with no format is 64 bits, the same as `int64`, by decision D113 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)). A value above 2^31, such as a large identifier, decodes exactly.
- A security requirement that names several schemes together (for example a bearer token and an API key) sends the credentials of all of them, by decision D116. The client must hold every credential of the requirement; an alternative the client holds only in part is used only when no other alternative is complete. A requirement that names one scheme works as before.

## Under review

These are proposals from a review of the type mapping against current Java. None of them is decided, and none changes what the generator produces today.

- Model unions as a sealed interface with record variants. The floor of 21 already allows `sealed` types and pattern-matching `switch`, so the floor would not change, but the public shape of every union would.
- Build maps as `LinkedHashMap` instead of `HashMap` so that `additionalProperties` keep their wire order. This is an implementation detail, not a type change.
- Keep `java.time` types as they are. The review found nothing in newer JDKs that replaces `LocalDate`, `OffsetDateTime`, `LocalTime` or `Duration`. Adding `java.time.Period` for calendar durations would need a format decision, not a newer JDK.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. Reads `Context.current()` on the caller's thread (`span.makeCurrent()`); the call is blocking, so no hand-off precedes the read.

## Behaviour notes

- A bearer scheme wrapper also takes a token provider, `new <Scheme>Authenticator(host, supplier)`, which never reads the environment variable.
- An explicitly passed empty credential is refused; an empty environment variable counts as unset.
- A string result under a binary content type is the decoded body, and a time of day in a query keeps non-zero milliseconds.
- Serializing an anyOf union that holds a value which is not one of its variants fails with the serialization error naming the union.
