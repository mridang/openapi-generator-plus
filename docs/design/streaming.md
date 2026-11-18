# Streaming design (issue #114, items F18 and F19 of #131)

Status: proposal for owner review. Nothing here is built. This document fixes one shape for all twelve SDKs (java, kotlin, csharp, php, python, ruby, node, dart, go, rust, swift, elixir) so that no language is implemented first and the other eleven made to follow. It replaces decision F837 ("streaming is deferred") once the owner accepts it.

Scope: streaming of response bodies (binary and line-delimited JSON), streaming of request bodies, and a server-sent-events (SSE) client with reconnect and `Last-Event-ID`.

## 1. What exists today

The transport contract in all twelve SDKs reads a whole response and sends a whole request body. Three clients were read to ground this design.

- Java (`java/api_client.mustache`, `java/default_api_client.mustache`). `ApiClient.sendRequest(method, url, headers, body)` returns the record `ApiHttpResponse(int statusCode, String body, Map headers)`. The body is a `String`. The default client calls `HttpClient.send` with a `byte[]` body handler, drives redirects by hand (HttpClient's own redirects are off), decompresses after the whole body is in memory, and decodes by charset. A binary response is carried as base64 inside that `String` and rebuilt into `byte[]` or an `InputStream` by `base_api.mustache` (it checks `returnType == InputStream.class || byte[].class`). A request body of type `InputStream` or `File` is read fully into a `byte[]` first, on purpose: the redirect loop and the retry loop must be able to send the body again, and a raw `InputStream` cannot be replayed. A multipart body (`Map`) is never retried.
- Node (`node/api_client.mustache`, `node/abstract_api_client.mustache`). `sendRequest(method, url, headers, body: string | Buffer | Record | null, options)` returns `ApiHttpResponse` whose body is a string; binary is base64 and `base_api.mustache` turns it into a `Buffer` (`returnKind: 'binary'`). Retries and the end-to-end deadline are in `sendCounted`; a multipart body is never retried; `AbortSignal` is already the cancellation handle. Decompression is left to `fetch`.
- Go (`go/api_client.mustache`, `go/default_api_client.mustache`). `SendRequest(method, url, headers, body any)` returns `*ApiHttpResponse` with `Body string`. The client does `io.ReadAll(resp.Body)`, then `decompressBody` on the full byte slice, then base64-encodes binary into the string. Redirects run in `CheckRedirect` with a `GetBody`-based replay; the end-to-end timeout is a `context.WithTimeout` around everything including the body read.

The other nine follow the same contract on their libraries: kotlin Ktor (`bodyAsBytes()`), csharp `HttpClient.SendAsync` with `ReadAsByteArrayAsync` and `AutomaticDecompression = None`, php an abstract client over PSR-18 plus a default curl/native transport, python urllib3 (`preload_content=False`, `decode_content=False`, then one `response.read()`), ruby Faraday with the net_http adapter, dart `package:http` over `IOClient` (and a web variant), rust reqwest, swift `URLSession` with a delegate and `dataTask`, elixir Req.

Consequences that the design has to respect: (a) the body is a `String` in `ApiHttpResponse`, so streaming cannot be expressed by changing a type in place; (b) retries, redirects, the end-to-end deadline, decompression and redaction all assume the body is complete before they finish; (c) several things the SDKs already promise (S7 cancellation, S16 one budget across redirect hops, F1078 graceful `close()`, F823 retries, S15 redaction, the error tree of #123) must keep holding.

## 2. Principles

1. Additive. No existing operation, option or type changes. Streaming is a second method per selected operation and a second method on the transport interface. A caller who never opts in sees nothing new except types.
2. One shape in twelve languages. Names, option names, error classes and behaviours are the same; only the stream type follows the language idiom (section 4).
3. Same safety as the buffered path. A streamed response passes through the same redirect rules, header stripping, error mapping and redaction. Nothing weaker is allowed because "it is a stream".
4. Never retry once the caller has been handed bytes. Never buffer an unbounded body to make a retry possible.
5. The caller owns the stream and closing it is always safe and idempotent. A stream that is never closed is a leak the docs name, not something the SDK hides.
6. No vendor extension and no petstore vocabulary in templates. The spec signals are standard OpenAPI (section 3).

## 3. Spec signals and the opt-in

Signals (read from the operation, no `x-` extension, per the root AGENTS.md rule on spec-sourced extensions):

| Where | Signal | Kind selected |
| --- | --- | --- |
| response, 2xx content type | `text/event-stream` | SSE: stream of `ServerSentEvent<T>` (the `data` of each event is decoded as the media type's schema when it is `application/json`-shaped, else string) |
| response, 2xx content type | `application/x-ndjson`, `application/jsonl`, `application/jsonlines` (all treated as one kind) | line-delimited JSON: stream of the schema of the media type, one item per line |
| response, 2xx content type | `application/octet-stream`, or any content type whose schema is `type: string, format: binary` | binary: byte stream |
| request body | `application/octet-stream` or `type: string, format: binary` (already carried as a raw body today) | binary upload from a source of unknown length |

Not selected: `application/json-seq` (record separator framing; add later on request), `multipart/mixed` and `multipart/x-mixed-replace` (no consumer in the corpus), WebSocket (not HTTP).

Opt-in. The generator option `streaming` (boolean, default `false`) adds the streaming variants. With `false` the output is byte-identical to today, which keeps every existing golden unchanged and lets each language stage land without touching the others. With `true`, each operation that matches a signal in the table gets a `<operation>Stream` method (casing per language) next to its existing method; the existing method keeps buffering. An operation that declares `text/event-stream` as its only success content type gets only the stream method (a buffered SSE call never ends). Rejected alternative: a per-operation vendor extension, which the repository forbids.

Open point for the owner: whether the default flips to `true` after one release. Recommendation: keep `false` until stage 6 has run for a release.

## 4. API shape per language

Common vocabulary used in every language:

- `ByteStream` (binary), `JsonLinesStream<T>`, `EventStream<T>`: the idiomatic stream type of the language, plus a handle with `status`, `headers`, and `close()`.
- `StreamOptions` extends the per-call options (F827: `timeout`, `headers`, `maxRetries`) with `idleTimeout` and, for SSE, `maxReconnects`, `reconnectDelay`, `lastEventId`.
- `ServerSentEvent<T>`: `id` (string or absent), `event` (string, default `message`), `data` (decoded `T`, or the raw string when no schema), `retry` (duration or absent), `raw` (the joined data string).

Snippets use the neutral operations `exportItems` (GET `/items/export`, `application/x-ndjson`, item schema `Item`), `downloadContent` (GET `/items/{id}/content`, `application/octet-stream`), `uploadContent` (PUT `/items/{id}/content`, `application/octet-stream`), `watchEvents` (GET `/events`, `text/event-stream`).

Library claims were checked against the library documentation on 2026-11-18 unless marked "to verify in stage 0".

### java (Java 21, `java.net.http`)

Binary and JSON Lines use `HttpResponse.BodyHandlers.ofInputStream()` (the body "can be read as it is received"; closing it before the body is complete cancels the exchange). `ofLines()` exists but cannot be closed on a decode error with our error mapping, so the SDK wraps `ofInputStream()` itself. `Flow.Publisher` is offered through `ofPublisher()` only as an adapter; the primary type is blocking, which suits Java 21 virtual threads.

```java
// binary: caller closes
try (ByteStream in = api.downloadContentStream(id)) {        // extends InputStream
    in.transferTo(out);
}
// json lines: AutoCloseable iterator
try (JsonLinesStream<Item> items = api.exportItemsStream()) { // Iterator<Item>, AutoCloseable, stream()
    items.forEachRemaining(handle);
}
// sse
try (EventStream<Change> ev = api.watchEventsStream(StreamOptions.builder().maxReconnects(5).build())) {
    for (ServerSentEvent<Change> e : (Iterable<ServerSentEvent<Change>>) ev::iterator) { ... }
}
// adapter for reactive callers
Flow.Publisher<ServerSentEvent<Change>> p = ev.asPublisher();
// upload: a supplier makes the body replayable; a bare InputStream is sent once
api.uploadContent(id, BodySource.of(() -> Files.newInputStream(path)), opts);
api.uploadContent(id, BodySource.once(inputStream), opts);
```

### kotlin (Ktor 3, multiplatform)

Ktor streams with `prepareGet(url).execute { response -> response.body<ByteReadChannel>() }`; "by default `body()` loads the full response into memory" so the SDK must use `execute`, and the channel is valid only inside the block. Therefore the public type is a cold `Flow` that opens the request on collection and closes it when collection ends or is cancelled. The Ktor SSE plugin (`install(SSE)`, `sse()`) was not confirmed from the documentation page read, so the SDK ships its own parser (section 8) on the byte channel (to verify in stage 0 whether the plugin offers reconnect hooks worth using).

```kotlin
api.downloadContentStream(id).collect { chunk: ByteArray -> out.write(chunk) }   // Flow<ByteArray>
api.exportItemsStream().collect { item: Item -> ... }                            // Flow<Item>
api.watchEventsStream(StreamOptions(maxReconnects = 5)).collect { e: ServerSentEvent<Change> -> ... }
api.uploadContent(id, BodySource.of { channel() })                               // () -> ByteReadChannel
```

Cancelling the collecting coroutine cancels the request (S7 already maps interrupts and cancellation).

### csharp (.NET 10)

`HttpClient.SendAsync(request, HttpCompletionOption.ResponseHeadersRead, token)` returns when headers are read and leaves the content unread. Microsoft's remarks matter here: with `ResponseHeadersRead` the `HttpClient.Timeout` applies only until the headers end, and `MaxResponseContentBufferSize` is not enforced. So the SDK must enforce idle timeout and size limits itself (section 6). Types: `Stream` for binary (`ByteStream : Stream`), `IAsyncEnumerable<T>` for the rest.

```csharp
await using ByteStream s = await api.DownloadContentStreamAsync(id, ct);
await s.CopyToAsync(file, ct);
await foreach (Item item in api.ExportItemsStreamAsync(ct: ct)) { ... }            // IAsyncEnumerable<Item>
await foreach (var e in api.WatchEventsStreamAsync(new StreamOptions { MaxReconnects = 5 }, ct)) { ... }
await api.UploadContentAsync(id, BodySource.FromFactory(() => File.OpenRead(path)), cancellationToken: ct);
```

The trailing `CancellationToken` stays (S7); `[EnumeratorCancellation]` threads it into the iterator.

### php (8.3, PSR-7/PSR-18, curl or native transport)

Guzzle's `stream => true` request option streams a response instead of downloading it up front and the body is read with `getBody()->read($n)` until `eof()`; `read_timeout` bounds one read. The abstract PSR-18 client cannot stream (PSR-18 returns a `ResponseInterface` whose body stream may or may not be lazy depending on the implementation), so streaming needs its own transport method; a PSR-18 client whose response body is not lazy degrades to buffered with a documented warning, never a silent hang. Types: `Psr\Http\Message\StreamInterface` for binary, `\Generator` (or `iterable`) for the rest.

```php
$body = $api->downloadContentStream($id);            // StreamInterface, caller calls $body->close()
foreach ($api->exportItemsStream() as $item) { ... }  // Generator<int, Item>; leaving the loop or unset() closes it
foreach ($api->watchEventsStream(new StreamOptions(maxReconnects: 5)) as $event) { ... } // Generator<ServerSentEvent>
$api->uploadContent($id, BodySource::fromCallable(fn () => fopen($path, 'rb')));
```

A generator closes its connection in a `finally`, which runs on `break` when the generator is destroyed.

### python (3.10, urllib3 2.x; sync plus the async facade)

The default client already calls urllib3 with `preload_content=False`, so the connection is open and unread after the request; the guide shows wrapping it for line iteration. Today it then does one `response.read()`; streaming returns the response wrapped instead. Sync types are context managers that are also iterators; the async facade offers `AsyncIterator` because it already runs requests off-thread (it cannot be genuinely non-blocking without a new dependency, see section 12).

```python
with api.download_content_stream(id) as body:            # io.RawIOBase-like: .read(n), iter_chunks()
    shutil.copyfileobj(body, out)
with api.export_items_stream() as items:                  # Iterator[Item]
    for item in items: ...
with api.watch_events_stream(StreamOptions(max_reconnects=5)) as events:  # Iterator[ServerSentEvent[Change]]
    for e in events: ...
async with async_api.watch_events_stream() as events:     # AsyncIterator, async facade
    async for e in events: ...
api.upload_content(id, BodySource.from_factory(lambda: open(path, "rb")))
```

### ruby (3.2, Faraday 2)

Faraday supports a per-request `on_data` callback that receives each chunk, on adapters that implement it; this is the mechanism needed, but its exact signature and the adapter support list could not be confirmed from the documentation page that was fetched (to verify in stage 0; if net_http lacks it the default client drops to `Net::HTTP#request` with `read_body { |chunk| }` for streaming calls only). Because `on_data` is push-style, an `Enumerator` is built over a producer thread with a bounded queue, which gives back-pressure and lets `break` close the connection.

```ruby
api.download_content_stream(id) { |chunk| out.write(chunk) }   # block form: IO-like, no enumerator
io = api.download_content_stream(id)                           # no block: an IO-like with #read(n), #close
api.export_items_stream.each { |item| ... }                    # Enumerator::Lazy[Item]; #close available
api.watch_events_stream(max_reconnects: 5).each { |e| ... }    # Enumerator[ServerSentEvent]
api.upload_content(id, BodySource.from_proc { File.open(path, "rb") })
```

### node (Node 20+/22, fetch/undici, plus the web transport)

`fetch` returns after the headers and `response.body` is a Web `ReadableStream`; it is also async-iterable in Node. The SDK passes the caller's `AbortSignal` and the decompression is already done by `fetch` (undici), so it stays out of the SDK; the portable web transport relies on the runtime the same way.

```ts
const body: ReadableStream<Uint8Array> = await api.downloadContentStream(id, { signal });
for await (const item of api.exportItemsStream({ signal })) { ... }          // AsyncIterable<Item> & AsyncDisposable
for await (const e of api.watchEventsStream({ maxReconnects: 5, signal })) { ... } // AsyncIterable<ServerSentEvent<Change>>
await api.uploadContent(id, { body: bodySource(() => fs.createReadStream(path)) }); // ReadableStream | AsyncIterable | factory
```

Breaking out of `for await` calls the iterator's `return()`, which cancels the stream.

### dart (3.11, package:http)

`Client.send` returns `Future<StreamedResponse>`; `StreamedResponse.stream` is a `ByteStream`. Caution: the package documentation page read covers only the request side, so the response-side contract (a never-listened stream keeps the socket) is taken from the library's README and must be verified in stage 0. Types are plain `Stream`s; cancelling the subscription cancels the request.

```dart
final Stream<List<int>> body = api.downloadContentStream(id);          // Stream<List<int>>
await for (final item in api.exportItemsStream()) { ... }              // Stream<Item>
await for (final e in api.watchEventsStream(StreamOptions(maxReconnects: 5))) { ... } // Stream<ServerSentEvent<Change>>
await api.uploadContent(id, BodySource.fromFactory(() => File(path).openRead()));
```

### go (1.25)

`resp.Body` is already an `io.ReadCloser` after `Do` returns; the SDK simply does not `ReadAll` it. Go 1.23+ `iter.Seq2` gives the range-over-func form, so no custom iterator type is needed. `ctx` stays the first parameter (S7); cancelling it aborts the body read.

```go
body, err := api.DownloadContentStream(ctx, id)        // io.ReadCloser; defer body.Close()
for item, err := range api.ExportItemsStream(ctx) {    // iter.Seq2[Item, error]; break closes the stream
    if err != nil { return err }
}
events := api.WatchEventsStream(ctx, WithMaxReconnects(5)) // iter.Seq2[ServerSentEvent[Change], error]
err = api.UploadContent(ctx, id, BodyFromFactory(func() (io.ReadCloser, error) { return os.Open(path) }))
```

The go end-to-end `context.WithTimeout` that currently wraps the body read cannot be used for streams (section 6); the streaming path wraps only the dial and header phase.

### rust (reqwest 0.12, tokio)

`reqwest::Response` has `bytes_stream()` (requires the `stream` crate feature, which `rust/cargo_toml.mustache` does not enable today; stage 4 adds it) and `chunk()`. Types: `impl Stream<Item = Result<Bytes, Error>>`, and for convenience `AsyncRead` via `tokio_util::io::StreamReader` (a new dependency, pure Rust). Dropping the stream cancels the request.

```rust
let mut body = api.download_content_stream(&id, opts).await?;          // ByteStream: Stream<Item = Result<Bytes, Error>> + .into_async_read()
while let Some(chunk) = body.next().await { out.write_all(&chunk?).await?; }
let mut items = api.export_items_stream(opts).await?;                  // impl Stream<Item = Result<Item, Error>>
let mut events = api.watch_events_stream(StreamOptions::default().max_reconnects(5)).await?;
api.upload_content(&id, BodySource::from_factory(|| async { Ok(File::open(path).await?) }), opts).await?;
```

### swift (6.0, Foundation)

`URLSession.bytes(for:delegate:)` returns `(URLSession.AsyncBytes, URLResponse)`, an `AsyncSequence` of `UInt8` that has `.lines`; it is available from iOS 15 and macOS 12. The current default client uses `dataTask` plus a delegate (and the delegate carries the redirect and proxy logic), so streaming adds a second delegate-driven path that yields through an `AsyncThrowingStream`; this also covers Linux, where `AsyncBytes` support in swift-corelibs-foundation must be verified in stage 0.

```swift
let body: ByteStream = try await api.downloadContentStream(id: id)             // AsyncSequence<Data>
for try await item in try await api.exportItemsStream() { ... }                // AsyncThrowingStream<Item, Error>
for try await e in try await api.watchEventsStream(options: .init(maxReconnects: 5)) { ... }
try await api.uploadContent(id: id, body: .factory { try FileHandle(forReadingFrom: url) })
```

Task cancellation cancels the stream.

### elixir (1.17, Req 0.5)

Req's `:into` option: `nil` reads the whole body; a function receives `{:data, chunk}` and returns `{:cont, {req, resp}}` or `{:halt, ...}`; a collectable is used only when the status is 200; `:self` sends chunks to the caller's mailbox, sets `response.body` to a `Req.Response.Async` that is `Enumerable`, and `Req.cancel_async_response/1` cancels. The `:self` mode fits a lazy `Stream`; the docs do not say how `:retry` behaves with `:into` (to verify in stage 0, and the SDK disables Req retry for streaming calls and keeps its own, section 5).

```elixir
{:ok, body} = Client.download_content_stream(client, id)          # Enumerable of binaries (Stream.resource-backed); Enum.each/Stream.into
{:ok, items} = Client.export_items_stream(client)                 # Enumerable of %Item{}
{:ok, events} = Client.watch_events_stream(client, max_reconnects: 5)   # Enumerable of %ServerSentEvent{}
Enum.each(events, &handle/1)                                      # leaving the enumeration early (Enum.take, halt) cancels the response
Client.upload_content(client, id, BodySource.from_factory(fn -> File.stream!(path, 2048) end))
```

## 5. Retries

The rule in one line: the SDK may retry a streaming call until the response headers have been accepted for delivery; after the first byte of the body has been handed to the caller it never retries.

- Phase 1, before the handle is returned: connect, send, wait for headers. Identical to F823: a network error before any response, and 408, 409, 429 and 5xx except 501, with backoff, jitter, `Retry-After` and the cap, bounded by `maxRetries`. A retryable status body is read and discarded (bounded) and the connection closed before waiting.
- Phase 2, after the handle is returned: no retries, no reconnects (except SSE, below). A failure is raised to the consumer as a stream error.
- Request bodies: a streamed upload is retried (phase 1) only when its `BodySource` is replayable (a factory, a byte array, a file path). A source created with `once(...)` is sent a single time; a connection error or a retryable status then surfaces immediately with the original error and the message names the cause (`request body is not replayable`). The SDK never buffers a stream to make it replayable. This reverses today's Java behaviour (buffering `InputStream` to `byte[]`) for the streaming method only; the buffered methods keep their current behaviour.
- SSE reconnect is a separate mechanism (section 8) with its own counter. It never reuses `maxRetries`, and a reconnect never happens after a clean 204 or a non-retryable status.
- The end-to-end budget in phase 1 is the existing one (F823: one budget across all attempts and redirect hops, S16).

## 6. Timeouts

The end-to-end timeout (default 10 seconds) is meaningless for a stream that may legitimately last an hour, and it is also the thing that #116 wants to hold for the buffered path while the body is read. For the streaming methods:

- `timeout` (the F827 per-call value, or the transport's) bounds phase 1 only: dial, TLS, send, headers, including redirect hops and retry waits. In csharp this is the documented behaviour of `ResponseHeadersRead`; in go the `context.WithTimeout` is cancelled as soon as headers arrive (it must not be `defer cancel()` over the lifetime of the stream, or it would cut the stream).
- `idleTimeout` (new, per call and on `TransportOptions`) bounds the gap between two reads of the body. Default `60s` for binary and JSON Lines, and `none` for SSE (a server may be quiet; heartbeats are the server's business). `0` or `none` disables it. Idle expiry raises `NetworkTimeoutError` (status 0) with the same message prefix as today and is not retried.
- A maximum total duration is not provided. A caller that wants one cancels (section 7).
- Size: the future maximum response size option (#112) applies to the buffered path; for streams it applies per line (JSON Lines and SSE: default 1 MiB per line or event, `StreamError` when exceeded) and not to byte streams.

## 7. Cancellation and closing

Every stream handle has `close()` in the language's idiom (`AutoCloseable`, `IDisposable`/`IAsyncDisposable`, context manager, `defer`, `Drop`, `Stream` cancellation, `return()`/`AsyncDisposable`). It is idempotent, safe from another thread, and it aborts the connection rather than draining it (a drained SSE stream never ends). Language-native cancellation (S7: go ctx, csharp token, node signal, kotlin coroutine, swift task, python task) cancels the same way. A stream abandoned without closing is released when the language can notice (Rust `Drop`, Elixir process exit, Python `__del__` with a `ResourceWarning`); in the others the docs state it is a leak.

Client `close()` (F1078 says graceful and idempotent): open streams are not "in flight finishing", they may never finish. Proposal, for the owner to confirm: `client.close()` cancels every open stream of that client, and the consumer gets a `ClientClosedError` (the closed-client error, D29). The buffered calls keep finishing as today.

A caller-supplied stream or file handle for an upload is never closed by the SDK (S16); one made by a factory the SDK called is closed by the SDK.

## 8. SSE client

Parser (one grammar, written once in the Java reference and mirrored; implemented per the WHATWG HTML Living Standard, section "Server-sent events", which the MDN page summarises):

- UTF-8; a leading BOM is dropped; lines end in CRLF, LF or CR; a blank line dispatches the buffered event; an event with an empty data buffer is not dispatched.
- Fields `event`, `data`, `id`, `retry`; a line starting with `:` is a comment (kept as a heartbeat, never delivered); one optional space after the colon is removed; unknown fields are ignored; consecutive `data` lines join with a newline.
- `id` containing NUL is ignored; the last event id persists across events until changed; `retry` accepts only digits and sets the reconnect delay in milliseconds, otherwise ignored.
- `data` is decoded as the media type's schema if one is declared, otherwise delivered as the raw string. A decode failure raises `SerializationError` naming the event id and ends the stream (an out-of-date spec fails where it can be seen, F835).

Request: method and body as declared (usually GET); `Accept: text/event-stream`; `Cache-Control: no-cache`; `Accept-Encoding: identity` (compressing proxies buffer small events; see section 10); `Last-Event-ID` when an id is known (from the caller's `lastEventId` option on the first request, afterwards the last received id).

Reconnect policy (the EventSource standard leaves the default delay to the implementation; these are the SDK's choices):

- Reconnect after a clean end of body and after a network error or idle timeout, up to `maxReconnects` consecutive failures without receiving a single event (default 5; `-1` unlimited). Receiving an event resets the counter.
- Delay: the last `retry` value from the server, else `reconnectDelay` (default 1 second); on consecutive failures it doubles with jitter up to 30 seconds. `Retry-After` on a 429, 502, 503, 504 response is honoured as in F823.
- Do not reconnect after: 204 (the standard's "stop"), any non-2xx other than 408/429/5xx (raised as the typed error from #123, status class preserved), a 2xx with a content type other than `text/event-stream` (raised as `SerializationError`), a `401` unless the authenticator can refresh, in which case one refreshed attempt is made and then the error is raised, caller cancellation, and client close.
- A request body that is not replayable disables reconnect (the stream ends with `StreamError` after a break, message says why).
- Redirects on reconnect re-apply the same cross-origin stripping; a 3xx at reconnect time that changes origin is followed only if the transport follows redirects, with the id header kept (it is not a credential).
- Each reconnect is observable: a `reconnect` hook `(attempt, delay, lastEventId, cause)` on `StreamOptions`, so applications can log or stop; returning "stop" ends the stream.

## 9. Redirects, errors, error hierarchy

- Redirects before the body use the existing loop unchanged (S16, D76, scheme refusal, HTTPS to HTTP body-replay refusal, header stripping). A 307 or 308 with a non-replayable streamed upload is refused with the same `NetworkError` message used for the HTTPS to HTTP refusal (`Refusing to replay a streamed request body`), never buffered.
- Non-2xx at headers: the SDK reads the error body, bounded to 64 KiB, closes the connection and throws the typed error from #123 exactly as the buffered path does (`NotFoundError` with `errorBody`, method, path without query, etc.). The caller never receives a stream for a non-2xx.
- Failures after the handle exists (connection reset, truncated body, bad chunk framing, decompression failure, idle timeout, line too long) are raised from the stream's read or next call. Proposed new class `StreamError`, a child of `NetworkError` (so existing `catch NetworkError` code still catches it), carrying `method`, `path`, `status` (the 2xx status), `bytesDelivered` and `cause`. Idle timeout stays `NetworkTimeoutError`. Mapping per language mirrors the existing tree (java exception, go error type with `errors.As`, elixir tagged tuple inside the enumerable raised as an exception, rust enum variant). This adds one class to twelve surfaces, so it is the single API-surface change; flagged for the owner (D-S3).
- Content-Length shorter than the body or a chunked stream that ends without the final chunk is a truncated body: `StreamError`, never a silent short success.
- Decode errors in JSON Lines: `SerializationError` with the 1-based line number; the stream ends (no skip-and-continue option in v1).

## 10. Compression

- Binary and JSON Lines: the same `Accept-Encoding` as the buffered path, decoded incrementally with the language's streaming decoder over the same set (gzip and deflate everywhere; br and zstd where the buffered path has them, raising the same "library absent" error). The raw-deflate fallback of transport049 must also work incrementally: decide from the first two bytes, as the buffered path does.
- SSE: `Accept-Encoding: identity`. If the server compresses anyway the SDK still decodes, and flushes per event; for libraries that buffer decoder output (to verify per language in stage 0) the SSE kind documents the limitation.
- A corrupt compressed stream raises `StreamError` with cause the decoder error, not a transport failure that triggers a retry.
- Content-Length for a compressed body describes encoded bytes, so `bytesDelivered` counts decoded bytes and no progress percentage is promised.
- node and the web transports leave decoding to `fetch`; dart's web variant to the browser; this stays a forced divergence (already listed for Content-Encoding).

## 11. Redaction

Extends S15. The string form of a stream handle and of `StreamOptions` shows kind, method, path without query, status, bytes delivered and last event id; never headers' values, never a body or event data, never the query. The `Authorization`, `Proxy-Authorization`, `Cookie` and key/token/secret-named headers stay masked as `***` in `StreamOptions.headers`. Log or hook output of events uses the redacted form by default; `Last-Event-ID` is not a secret but is not logged by the SDK either. Errors from a stream follow the #123 rule: no query string and no fragment in the message.

## 12. Request streaming (uploads)

`BodySource` (name per language idiom) is a small type with three constructors: `of(bytes)` (replayable, known length), `fromFactory(() -> stream)` (replayable, unknown or known length, the SDK closes what the factory returns), `once(stream)` (single use, caller keeps ownership, S16). Behaviour:

- Known length (bytes, a file by path): send `Content-Length`. Unknown length: chunked transfer, or the library's equivalent (`HttpRequest.BodyPublishers.fromPublisher` without a length in Java; `ReadOnlyStream` content without length in .NET; reqwest `Body::wrap_stream`; Go `Body` with `ContentLength = -1`).
- Multipart bodies containing a stream part stay buffered in v1 (the multipart envelope needs the part boundaries and every language builds it in memory today). Only the raw binary body of an operation streams in v1; a streamed multipart part is a later item.
- Phase-1 retry and redirect rules as in section 5 and 9. The request deadline for a long upload is the `timeout` of phase 1 extended by progress: the idle timeout applies to the write side as well (no bytes written for `idleTimeout`).
- Python's urllib3 and Ruby's Faraday net_http adapter accept an iterable or an IO body with `Transfer-Encoding: chunked`; to verify in stage 0 for each library the exact body types, and where a library cannot stream a body the method says so at build time of the client, not at call time.

## 13. Transport contract change

One new method on each `ApiClient` interface, with a default that throws a clear unsupported error so custom and injected transports keep compiling and keep working for buffered calls:

```java
// java
default ApiStreamResponse sendStreaming(String method, String url, Map<String, String> headers,
                                        @Nullable Object body, StreamRequestOptions options) {
    throw new UnsupportedOperationException("This transport does not support streaming");
}
public interface ApiStreamResponse extends AutoCloseable {   // status, headers, InputStream body(), close()
    int statusCode(); Map<String, String> headers(); InputStream body(); void close();
}
```

The existing `ApiHttpResponse` and `sendRequest` are untouched. Everything the streaming path shares with the buffered one (redirect loop, header merge, request id, tracing, auth refresh, retry phase 1, redaction) is moved behind one internal function that stops before the body, so there is no second copy of the redirect and retry logic (the maintenance risk in all twelve). The streaming wrappers (line splitter, SSE parser, NDJSON decoder, idle timer) are a template set of their own, `stream/` in each language, written for java first as the reference and reviewed for parity.

## 14. Parity test plan

New catalogue lines, in the 300 range of the existing areas (format `areaNNN | promise [| multi]`; the exception file `test-catalogue-exceptions.txt` is not touched by this document). Where a behaviour holds in all twelve it is a plain line; `multi` marks one id satisfied by several tests in one language. Tests use the chasm test server of the shared fixture and neutral operations; the fixture spec needs three new `x-internal` operations (a binary body that arrives in chunks, an NDJSON feed, an SSE feed that drops after N events and honours `Last-Event-ID`); they are added to `specs/petstore/openapi.yaml` without removing or renaming anything, as the fixture rule requires.

| Id | Promise |
| --- | --- |
| results301 | A binary streaming call delivers the bytes in order without reading the whole body first |
| results302 | A JSON Lines streaming call yields one decoded item per line, skipping blank lines |
| results303 | A JSON Lines line that fails to decode raises a serialization error naming the line and ends the stream |
| results304 | A JSON Lines stream tolerates CRLF line ends and a final line without a newline |
| results305 | An SSE stream yields id, event, data and retry fields of each event |
| results306 | An SSE stream joins consecutive data lines with a newline and skips comments |
| results307 | An SSE stream drops a leading byte order mark and accepts CR, LF and CRLF line ends |
| results308 | An SSE event with an empty data buffer is not delivered |
| results309 | An SSE data payload is decoded as the declared schema |
| transport301 | A streaming call sends Accept text/event-stream for SSE and identity encoding |
| transport302 | A gzip encoded JSON Lines stream is decoded incrementally |
| transport303 | A corrupt compressed stream raises a stream error, not a retry |
| transport304 | A streamed upload of unknown length is sent chunked and arrives intact |
| transport305 | A streamed upload with a known length sends Content-Length |
| transport306 | A caller supplied upload stream is never closed by the SDK |
| transport307 | A factory made upload stream is closed by the SDK |
| transport308 | The idle timeout raises a network timeout error when the server stalls mid body |
| transport309 | The call timeout applies only until the headers and not to the body |
| transport310 | A line longer than the limit raises a stream error |
| retries301 | A streaming call retries a 503 before the headers and succeeds |
| retries302 | A streaming call never retries after the first body byte was delivered |
| retries303 | A replayable streamed upload is retried after a 503 |
| retries304 | A single use streamed upload is not retried and reports why |
| retries305 | SSE reconnects after the connection drops and sends Last-Event-ID |
| retries306 | SSE reconnect waits the server retry value |
| retries307 | SSE reconnect stops after the maximum number of consecutive failures |
| retries308 | SSE does not reconnect after 204 or a 4xx status |
| retries309 | SSE reconnect counter resets after an event is received |
| redirects301 | A streaming call follows a redirect before the body and strips credentials across origins |
| redirects302 | A 307 with a single use streamed upload is refused and not buffered |
| errors301 | A non-2xx streaming call raises the typed error with the error body and never returns a stream |
| errors302 | A connection reset mid body raises a stream error that is a network error |
| errors303 | A body shorter than its Content-Length raises a stream error |
| errors304 | The string form of a stream and its options hides credentials, query and event data |
| lifecycle301 | Closing a stream twice is safe and aborts the connection |
| lifecycle302 | Cancelling the call context or token aborts the stream and is not a timeout |
| lifecycle303 | Closing the client cancels its open streams with the closed client error |
| options301 | The streaming option set accepts idle timeout, max reconnects, reconnect delay and last event id |
| operations301 | An operation with an NDJSON, SSE or binary response gets a streaming method only when streaming is enabled |

Unit tests mirror source files and the specs stay one per command, per the test-layout rules (the SSE parser unit test lives with the parser template's test file; the behaviours above live in the existing `DefaultApiClientTest` and `ClientTest` templates, not in new files). A Java test for the generator option checks the gating (operations301) and that `streaming=false` output is byte-identical to the current golden. All 39 ids must be listed in every language's test name; a language that cannot do one (for example node cannot show decoding done by `fetch`) lists the gap with its forced reason in `test-parity-allow.json`, never silently.

## 15. Staged implementation plan

Effort is one engineer-day for a reviewed, gated language unless stated; the Docker gates run in CI.

| Stage | Content | Effort |
| --- | --- | --- |
| 0 | Verify every "to verify" item above against the libraries (ruby `on_data`, dart response stream, swift `AsyncBytes` on Linux, elixir `:retry` with `:into`, kotlin SSE plugin, python and ruby chunked request bodies, php PSR-18 laziness); extend the chasm fixture with the three operations; owner decisions D-S1 to D-S5 | 2 days |
| 1 | Generator: option `streaming`, signal detection (`BetterCodegen` base, one place), model flags for the three kinds, gating test, docs page; no template output yet | 3 days |
| 2 | Java reference: refactor the redirect/retry code behind a header-phase function, `sendStreaming`, `ByteStream`, JSON Lines, SSE parser and reconnect, `BodySource`, `StreamError`, tests for the 39 ids, README "Streaming" section replacing "Not supported" | 8 days |
| 3 | The three closest to Java by library shape: kotlin, csharp, go (go and csharp already have the lowest-level hooks) | 3 x 4 days |
| 4 | node, python, rust (rust adds the `stream` feature and `tokio-util`; python adds the async facade iterator) | 3 x 4 days |
| 5 | The remaining five: php, ruby, dart, swift, elixir (the five with a library question from stage 0) | 5 x 5 days |
| 6 | Cross-language parity review pass against the canonical Java surface (the "APIs harmonized" rule), docs/decisions.md update (F837 superseded), README and language pages, flip the default decision | 3 days |

Total about 65 engineer-days, or about three weeks of calendar time if stages 3, 4 and 5 run in parallel worktrees. The first release that ships the option should include java, kotlin, csharp and go only if the owner accepts a staggered release; the standing rule is that all twelve expose the same shape, so the recommendation is to release all twelve together and keep `streaming=false` as the default until then.

## 16. Decisions for the owner

- D-S1: the opt-in is the generator option `streaming` (default false), not a vendor extension. Alternative: always on for matching content types.
- D-S2: a streaming method is additive (`<operation>Stream`); the buffered method stays and, for an SSE-only operation, does not exist.
- D-S3: one new error class `StreamError` under `NetworkError`, the only surface addition.
- D-S4: `timeout` covers only the header phase for stream calls; a new `idleTimeout` covers body gaps (default 60s, none for SSE). The end-to-end budget no longer applies once the stream starts.
- D-S5: `client.close()` cancels open streams, an intentional exception to the "in flight finishes" wording of F1078.
- D-S6: SSE reconnect defaults (5 consecutive failures, 1 second start, doubling to 30 seconds, jitter) and `Accept-Encoding: identity` for SSE.
- D-S7: a non-replayable streamed upload is never buffered and never retried; Java's current buffering stays only for the buffered methods.
- D-S8: multipart with a stream part is not streamed in v1.
- D-S9: python's async facade is thread-backed and not a true async transport; true async would need a new dependency (see S dependency rule: a small pure library is acceptable if it standardises behaviour cleanly).
