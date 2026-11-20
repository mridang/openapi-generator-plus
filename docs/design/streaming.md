# Streaming design (issue #114, items F18 and F19 of #131)

Status: accepted by the owner on 2026-11-21 and recorded as decisions D118 to D122 in [docs/decisions.md](../decisions.md) and in the shared `generators/AGENTS.md`. Nothing here is built yet; this page is the design the implementation follows. It replaces the second proposal that stood here, keeps its shared behaviour, catalogue ids, per-language notes and order of work (amended where the owner decided otherwise, sections 1 to 10), and adds the benchmark of other SDKs and generators and the sources (sections 11 to 17) and the check against the language and specification releases of 2025 and 2026 (section 12). Where a language or library fact could not be read in this repository it is marked **not verified**; "verified in <file>" means the file was read when the proposal was written.

## 0. What the owner decided

### 0.1 One transport method

The transport contract gets one new method that returns a response whose body has not been read yet (`sendStreaming` in this design; each language spells it in its own casing and in its own interface kind). The default implementation throws the language's built-in "unsupported" error with a message that names the transport and says that it cannot stream, so a transport that someone injected keeps working for every call that does not need it, and a call that does need it fails clearly and never falls back to buffering (a buffered fallback would hang on an endless event feed). The existing buffered `sendRequest` is unchanged.

### 0.2 Binary responses are streams in all twelve SDKs

An operation whose success response is binary (`application/octet-stream`, an image, `type: string, format: binary`, any binary media type) returns a stream in every language, and the carrier has a read-all helper for the caller who wants the bytes in memory. The reason is memory safety: a buffered method turns a large download into an out-of-memory failure in the caller's process, and the three SDKs that look like streams today (java, csharp, ruby) are memory-backed, so their shape promises something they do not do. Breaking changes are allowed now, so this replaces the recommendation of the earlier drafts (bytes in the base method and a stream only in a separate method, called O1 in this page's second proposal and Q-S1 in the review). JSON and text responses stay buffered.

There is no second `Stream` method for a binary operation: the one operation returns the byte-stream carrier (`ByteStream`), which has the status code, the lower-cased headers, an idempotent `close()` and the read-all helper (spelt `readAll`, `ReadAll`, `read_all` and so on per language). The upload side is unchanged: a request body is already typed with the language's native stream where one exists.

### 0.3 `<op>Stream` returns a typed iterator of items

For JSON Lines (`application/x-ndjson`, `application/jsonl`, `application/jsonlines`) and server-sent events (`text/event-stream`) the generator adds `<op>Stream`, which returns the language's lazy sequence. A JSON Lines item is the schema type `T`; an SSE item is `ServerSentEvent<T>` with `id`, `event`, `data` and `retry` (the standard envelope, `T` being the type of `data`). There is no buffered method for such an operation, because a buffered call on an endless feed never ends. Carriers per language are in section 6.1; two are fixed by the owner as accepted examples:

- TypeScript: `SseStream<T> extends AsyncIterable<ServerSentEvent<T>>`, with an idempotent `close()`.
- Java: `SseStream<T> extends Iterator<ServerSentEvent<T>>, AutoCloseable`, with an idempotent `close()` and `asPublisher()` (a `Flow.Publisher` adapter for reactive callers; the base stays blocking).

### 0.4 Common rules

1. Stopping early releases the connection, even when the consumer never reads to the end (break out of the loop, leave the scope, cancel the subscription, drop the value).
2. `close()` is idempotent in every language.
3. There is one `StreamError`, a subclass of the SDK's network error, for a failure after the headers: a connection reset, or a body that ends before its declared length. An idle timeout stays the SDK's network timeout error, and a line, event or payload that cannot be decoded stays the serialization error. This reverses the second proposal's "no new class".
4. Two timeouts: the call `timeout` covers dial, TLS, send, the wait for the headers, redirect hops and retry waits and stops at the headers; `idleTimeout` bounds the gap between two reads of the body, default 60 seconds for every kind, `0` or none disables it. An SSE comment line counts as activity, so a server that sends `: heartbeat` lines keeps a quiet feed alive.
5. No retry after the first body byte has been handed to the caller. Before that the buffered retry rules apply to the stream call.
6. Uploads take a `BodySource`, replayable or single use (section 4.3, "Upload"). A single-use body is never buffered and never retried.

### 0.5 Accepted picks

- SSE reconnect is off for a request that has a body, and for a GET it gives up after five consecutive failures without an event. The user can change both. Fern ships reconnect as an opt-in (`resumable`, default false, CLI 5.21.0, section 12); the GET default here is the browser `EventSource` rule and the one the Go libraries use, so it is the more eager end of the field, and the owner may still choose the Fern default.
- The SSE terminator (the value that ends a feed, such as `[DONE]`) is a generator option, default none. It is a setting of the generator, not a spec extension read by a template.
- An in-band `error` event (a 200 response whose stream carries an event named `error`) raises the SDK's API error with the status of the response and the event data as its body, and is neither reconnected nor retried. An event with any other name is an ordinary item.
- The stream method is generated from the spec signal alone; there is no rollout flag.
- `client.close()` cancels the streams that are open. This replaces "close waits for open streams" (O8 of the second proposal), because an endless feed would hold a graceful close open forever. Requests in flight that are not streams still finish, as today ([Closing a client finishes what is running](../decisions.md)).
- `application/json-seq` (RFC 7464) stays excluded.

### 0.6 Folded in from the check against the 2025 and 2026 releases

- Go 1.27 drains unread content from a response body on `Close()`. The Go stream `Close()` therefore cancels the request context first and then closes the body, and a test shows that closing a live feed returns promptly (`streaming031`).
- The schema reading rule is written down in section 4.1 (OpenAPI 3.0 and 3.1 use `schema`, 3.2 uses `itemSchema`; `x-oai-itemSchema` is accepted as an input by the Java detector, never by a template).
- The SSE parser test table gains the comment and heartbeat line, the three line endings (LF, CR, CRLF) and the event that has an id and no data (`sse017` to `sse020`).
- The Elixir transport sets Req's decompression explicitly for buffered and streaming calls, because Req stopped decompressing by default (section 6.2, elixir row).
- The test fixture question is open (section 7): the shared Chasm mock cannot serve per-cursor pages or a dropping SSE feed today, and the Go in-memory test server of Go 1.27 is the precedent that Go does not need it.

### 0.7 Experimental and unstable features: what a consumer would have to switch on

The owner is fine with experimental features. Each case is judged on merit. A feature counts as usable when an SDK consumer needs no special flag or toolchain; where the consumer would need one, it is recorded here with a recommendation and left as an open point for the owner instead of being decided silently.

| Feature | State (as of 2026-11-21) | What the consumer needs | Recommendation | Open for the owner |
|---|---|---|---|---|
| Rust `AsyncIterator` | nightly only; tracking issue rust-lang/rust#79024 open, no stabilisation PR | a nightly toolchain | keep `futures::Stream` (stable, what reqwest returns); revisit when it stabilises | yes: say if a nightly-only feature is ever acceptable for the Rust carrier |
| Node global `EventSource` | Stability 1 in the Node 25 docs, needs `--experimental-eventsource`; no POST and no custom headers | a runtime flag | do not use; own parser over `fetch` (needed anyway for POST and headers) | no, the limits are in the API itself |
| .NET `System.Net.ServerSentEvents` (`SseParser<T>`) | documented for net10 and net11; whether it is in the shared framework or a NuGet package per target is **not verified** | nothing if in the shared framework, one package otherwise | own parser, so all twelve share one behaviour and one test table (heartbeat counting, BOM, id with NUL); revisit once the in-box status is verified | yes: platform parser (less code, behaviour tested only for eleven) or own parser |
| Req 0.8 (`Req.stream/4`, built-in SSE and NDJSON, `into: fun` deprecated) | the 0.8 release candidate is listed on `main`; the newest published release is 0.7.5 | a Req 0.8 dependency, which is not released | keep `Req ~> 0.5` with the SDK's own parsers; wrap `into:` so a later move is internal | no |
| `BodyHandlers.limiting` (Java) | stable API, new in JDK 25 | JDK 25 or newer; the generated Java SDK's floor is 21 (verified in `java/pom.mustache`) | count bytes in the SDK for the bounded read-all so the floor stays 21 | yes, if the owner wants to raise the Java floor |
| Java `StructuredTaskScope` | preview in JDK 25 and 26 (from memory, **not verified**) | `--enable-preview` | do not use | no |
| Java Gatherers | final in JDK 24 (from memory, **not verified**) | JDK 24 or newer, above the floor | an optional adapter, never the base | no |
| Go `encoding/json/v2` and `jsontext` | in the Go 1.27 release notes; the SDK's floor is Go 1.25 | Go 1.27, above the floor | not used; the line reader and `Unmarshal` stay | yes, if the owner raises the Go floor |
| Dart `package:http` `AbortableRequest` | stable since 1.5.0; the connection-release fix for an abort before the body is read is in 1.7.0-wip | `http` 1.5.0 or newer; the SDK's floor is `^1.2.0` (verified in `pubspec.mustache`) | raise the floor to 1.5.0 and use `AbortableRequest`; a version bump that makes the SDK cleaner is allowed | confirm the bump |
| Swift typed throws on `AsyncSequence` (SE-0421) | Swift 6.0 (from memory, **not verified** here); the tools floor is 6.0 | nothing | use a typed failure where it reads cleanly | no, verify at build time |
| Swift `URLSession.AsyncBytes` on Linux | absent on Linux (a November 2026 package index build log, low confidence) | not a flag, a platform gap | a `URLSessionDataDelegate` feeding an `AsyncThrowingStream` everywhere | no |
| reqwest `read_timeout` | stable since 0.12.4 | nothing | use it as the Rust `idleTimeout` instead of a hand-written timer | no |
| reqwest `retry` policy and HTTP/3 streaming | `retry` stable since 0.12.23; h3 behind reqwest's unstable h3 flag | nothing for retry; an unstable cfg for h3 | never enable reqwest's retry (retries must stop at the first body byte, which only the SDK can know); do not enable h3 | no |
| Ktor SSE plugin | stable | nothing | Ktor's SSE plugin is not used; own parser (Ktor reconnect does not document `Last-Event-ID`) | no |
| PHP 8.5 URI extension | stable in PHP 8.5; the SDK floor is 8.3 | PHP 8.5 | not used | yes, if the owner raises the PHP floor |
| HTTP resumable uploads | Internet-Draft `draft-ietf-httpbis-resumable-upload-15`, no RFC | a server that implements a draft | not used; revisit when it is an RFC | no |


## 1. Goal

Every one of the twelve SDKs gets the same streaming surface in its own idiom: a response body can be consumed as it arrives (binary, JSON Lines, and server-sent events with reconnect and `Last-Event-ID`), and a binary request body can be sent as it is read instead of being buffered. Retries, redirects, authentication refresh, redaction and the error contract keep working exactly as for a buffered call, and a call that is never opted into behaves exactly as today. The shape is chosen once for all twelve, so no language is built first and the other eleven made to follow.

## 2. Owner decisions that bind this work

- Client SDKs only, twelve languages, identical capabilities and the same API shape; a difference between two languages is a defect until a named language feature forces it (root `AGENTS.md`).
- No client-side validation (S3, owner decision of the #131 debate): the stream layer enforces framing limits only, never schema constraints.
- Typed errors are thrown, never returned as values (#131); the error tree of `generators/AGENTS.md` ("The error contract") is the only one.
- Native types where the language has one (#156 and the type table in `docs/types.md`); no invented wrapper where a native stream type exists.
- Templates never read a spec-sourced `x-*` extension and are Mustache files; the generator computes values and hands them over under a non-`x-` name (`vendorExtensions.op.*`).
- F827: the per-call options are `timeout`, `headers`, `maxRetries` on the operation's Options object; a fourth option reopens F827 (so stream settings live elsewhere, section 5).
- F828: `TransportOptions` says how bytes travel, `Configuration` what the API is.
- F823 retries on by default (two, backoff, jitter, `Retry-After`, one end-to-end budget); S16 one budget across redirect hops and a caller-supplied stream is never closed by the SDK; S7 cancellation is never a timeout error; F1078 `close()` is graceful and idempotent; S15 redaction; D13 an unusable response is an `ApiError` with the real status; D29 use after close; D35 binary bodies are never re-encoded.
- Feature flags are declined (#131, "Opt-in feature flags ... one fixed, opinionated shape"): this design adds no feature flag. The one generator option it adds, the SSE terminator (0.5), sets a value and does not switch a feature on or off.
- Never edit `.github/workflows` (S13); the shared test catalogue (`src/spec/resources/test-catalogue.txt`) names every behaviour once, and each language needs a test carrying the id or an entry in `test-catalogue-exceptions.txt` with the forcing reason.
- F837 ("streaming is deferred") is superseded by this work once the owner accepts it; the owner request in #131 (F18, F19) asks for streaming request and response bodies and an SSE client with reconnect and `Last-Event-ID` in all twelve, keeping retries and error handling intact.

## 3. Critique of the first proposal, and what changed

Facts the first proposal got wrong or missed (each checked in this branch):

1. **Python is async-first.** Every generated operation is `async def` (`python/base_api.mustache`: `async def _invoke_api_for_result`), and the sync urllib3 transport runs under `asyncio.to_thread`. There is no "sync API plus async facade". The stream types are therefore async iterators and a thread bridges each read.
2. **PHP's default transport is Symfony HttpClient**, not curl or Guzzle (verified in `php/default_api_client.mustache` and `php/composer.mustache`: `symfony/http-client ^6.4|^7.4`). The Guzzle `stream => true` option does not apply.
3. **The retry loop and the 401 replay live in different layers.** Transport layer: java (`sendWithBackoff`), kotlin (`default_api_client.mustache`), csharp, node (`sendCounted`), php (`AbstractApiClient::sendWithRetries`), ruby (`transmit_with_retries`). Operation layer (`BaseApi`): go (`invokeApiForResult`, with `retryPolicyProvider`) and python (`_send_with_retries`). Rust exposes `max_retries()` and `request_timeout()` on its `ApiClient` trait (verified in `rust/api_client.mustache`), which points to the operation layer too, but its loop was not read. Swift, elixir and dart were not read for the loop. So "one internal function that stops before the body" is not one refactor but a per-language one, and the phase-1 retry has to be reused where it lives.
4. **Every existing timeout also covers the body read**, which the first proposal did not see: java `sendBounded` wraps the whole `sendAsync` future; csharp sets `HttpClient.Timeout = Infinite` and arms its own `CancellationTokenSource` that is passed to `SendAsync` and to `ReadAsByteArrayAsync`; go sets `http.Client.Timeout` (which includes the body) and a context deadline in `BaseApi`; node passes `AbortSignal.timeout(...)` to `fetch` (it cannot be disarmed); python uses a monotonic deadline; kotlin installs `HttpTimeout` with `requestTimeoutMillis`. For a stream each of these must be disarmed at the headers, so the claim that csharp's `HttpClient.Timeout` already stops at the headers is irrelevant here.
5. **Elixir already sets `retry: false` and `raw: true`** on Req (verified in `elixir/default_api_client.mustache`), so the open question about `:retry` with `:into` disappears; the SDK keeps its own retry.
6. **The test fixture cannot serve streams.** The shared server (the `chasm` project, read in `~/Code/mridang/chasm`) supports only `x-chasm-delay-ms` (a delay before the response), `x-chasm-content-encoding` and `x-chasm-echo`; it is stateless. A chunked body, a connection dropped after N events and a `Last-Event-ID`-aware feed are impossible with it. The first proposal's three "x-internal operations" would not work. The behaviours go in the existing `*ApiClientUnitTest` templates that already start an in-process server (verified for java `com.sun.net.httpserver`, and by file for dart, go, node, python, ruby; php, rust, swift, elixir have the file but the in-process server was not verified). The spec only needs real (non-internal) operations so that the generator emits the methods.
7. **Superseded by the owner (0.4 item 6): uploads take a `BodySource`.** The second proposal called it an invented wrapper in twelve languages and kept native stream types with a native replay rule; the owner chose the small `BodySource` type, with constructors for replayable sources (bytes, a file or path, a factory that opens a fresh stream) and single-use sources (a stream the caller hands over), and its constructors accept the native stream types, so the wrapper costs one line at the call site. The old text follows. Seven languages already type a raw binary body with a native stream (java `InputStream`, csharp `Stream`, go `io.Reader`, php `\SplFileObject|StreamInterface|string`, ruby `File`; verified in the goldens). The new design uses the native stream types and a single native rule for replay (section 6), and adds no wrapper type.
8. **The `streaming` generator option contradicts the declined "feature flags" verdict** and doubles goldens. Removed: the stream method is generated whenever the spec declares a streaming media type (section 4).
9. **`StreamOptions` extending the per-call options would reopen F827.** Stream settings are a separate trailing argument that exists only on stream methods, plus one `TransportOptions.idleTimeout` (F828: how bytes travel).
10. **Superseded by the owner (0.4 item 3): one `StreamError` under `NetworkError` is added.** The second proposal argued that no class was needed. Today a failure after the headers (reset, truncation) is already a `NetworkError` with status 0 (go: "a failure reading the body after headers ... is a transport failure"; node marks it in `afterResponse`), and a body that arrived but cannot be decoded is an `ApiError` with the real status (D13). Reusing them changes no public surface.
11. **`OnlyAllowJsonRule` strips the streaming media types** (verified in `rules/OnlyAllowJsonRule.java`: `ALLOWED_TYPES` has no `text/event-stream` or NDJSON type, and a validation step throws on any remaining other type). A user who enables `ONLY_ALLOW_JSON` would lose the signal. This is a shared-generator change the first proposal missed.
12. **The question of #114 "stream or bytes for binary responses" was left open.** Today the buffered binary return type differs: java `InputStream`, csharp `Stream`, ruby `File`/`StringIO` (memory-backed), against `ByteArray` (kotlin), `Buffer` (node), `Uint8List` (dart), `[]byte` (go), `Vec<u8>` (rust), `Data` (swift), `bytes` (python), `string` (php), `binary()` (elixir). This is decided in 0.2: binary is a stream everywhere.

Decisions of the first proposal kept: additive second method (`<operation>Stream`), a separate transport entry point with a safe default, never retry after the first body byte is delivered, never buffer a body to make it replayable, SSE parser per the WHATWG grammar, SSE reconnect separate from `maxRetries`, `Accept-Encoding: identity` for SSE, an idle timeout, one bounded error-body read, redaction.

## 4. The shared behaviour

### 4.1 What is streamed (spec signals, no extension)

| Where | Signal | Method |
| --- | --- | --- |
| 2xx response media type | `text/event-stream` | `<op>Stream` returning `SseStream<T>`, an iterator of `ServerSentEvent<T>`; there is no buffered method (a buffered SSE call never ends) |
| 2xx response media type | `application/x-ndjson`, `application/jsonl`, `application/jsonlines` (one kind) | `<op>Stream` returning `JsonLinesStream<T>`, `T` the schema of the media type; no buffered method |
| 2xx response media type | `application/octet-stream`, a schema `type: string, format: binary`, or any other binary media type | the operation itself returns `ByteStream` (0.2); no buffered method and no `Stream` twin |
| request body | a raw binary body (`requestBodyKind == "binary"`) | the operation itself takes the body as a `BodySource` (0.4 item 6), sent without buffering |

An operation with a binary request body takes a `BodySource` whatever its response kind; the response kind alone decides whether the operation is `<op>Stream` (JSON Lines, SSE), returns `ByteStream` (binary) or stays buffered. Not selected: `application/json-seq`, `multipart/mixed`, WebSocket; multipart bodies stay buffered (decision O5). The `data` of an SSE event is decoded as the schema section 4.1.1 names when one is declared and the type is not a plain string; otherwise it is the raw string. An operation that declares both a streaming and a JSON 2xx media type is handled as section 5 says.

### 4.1.1 Reading `schema` and `itemSchema` (the rule, per OpenAPI version)

Decided now so that 3.2 is additive later (the specification page is in section 12, change 2).

| Document version | JSON Lines (and the other sequential types) | `text/event-stream` |
|---|---|---|
| 3.0 and 3.1 | `schema` is the schema of each item; a spec that wrote `schema` as an array of the item reads as the item type | `schema` is the schema of the `data` payload |
| 3.2 | `itemSchema` is the schema of each item; `schema`, when also present, describes the whole body read as an array and is ignored by the SDK | `itemSchema` is the event envelope (`data`, `event`, `id`, `retry`); `contentSchema` on `data` is the schema of the decoded payload |
| any | the registered extension `x-oai-itemSchema` is read as `itemSchema` by the Java detector (never by a template), so a 3.0 or 3.1 document can carry it | same |

The pinned swagger-parser rejected 3.2 when this was first written (issue #50); that premise is probably stale and is re-checked when the parser is next bumped. A document that has neither schema gives a stream of raw lines (JSON Lines) or raw strings (`data`).

### 4.2 Catalogue lines

Two new areas, `streaming` and `sse` (new areas, so no id collides with other work; the format is `areaNNN | promise`). The test name carries `bhvstreaming001` in the language's casing (`bhvStreaming001...`, `test_bhv_streaming_001_...`, `TestBhvStreaming001_...`).

```
streaming001 | A binary stream call delivers the bytes in order and returns before the body has ended
streaming002 | A JSON Lines stream yields one decoded item per line and skips blank lines
streaming003 | A JSON Lines stream accepts CRLF line ends and a last line without a newline
streaming004 | A JSON Lines line that does not decode raises a serialization error naming the 1-based line and ends the stream after the items before it
streaming005 | A line or event longer than the limit raises a serialization error and ends the stream
streaming006 | A gzip encoded stream is decoded incrementally, so the first item arrives before the body ends
streaming007 | A corrupt compressed stream raises an API error with the real status and the decoder error as its cause, and is not retried
streaming008 | A non-2xx streaming call raises the typed error with the status and the error body and never returns a stream
streaming009 | A stream exposes the status and the lower-cased response headers
streaming010 | The call timeout bounds only the wait for the response headers, not the body
streaming011 | The idle timeout raises a network timeout error when the server stalls in the middle of the body
streaming012 | A connection reset in the middle of the body raises a network error and is not retried
streaming013 | A body shorter than its Content-Length raises a network error and is never a silent short success
streaming014 | A streaming call retries a 503 before the headers and then streams the retried response
streaming015 | A streaming call never retries after the first body byte was delivered
streaming016 | A streaming call follows a redirect before the body and strips credentials across origins
streaming017 | A streaming call sends the same User-Agent, request id, trace context and authentication headers as a buffered call
streaming018 | A 401 on a streaming call authenticated by OAuth2 refreshes the token and replays once before the stream is returned
streaming019 | Closing a stream twice is safe and aborts the connection
streaming020 | Cancelling the call aborts the stream and is never raised as a timeout
streaming021 | Closing the client cancels the streams that are open, the next read of each raises the closed-client error, and new calls are refused
streaming022 | The string form of a stream and its options hides credentials, the query and the data
streaming023 | A streamed upload of unknown length is sent chunked and arrives intact
streaming024 | A streamed upload with a known length sends Content-Length
streaming025 | A caller supplied upload stream is never closed by the SDK
streaming026 | A seekable upload is rewound and retried after a 503, and a single use upload is not retried and surfaces the original error
streaming027 | A 307 or 308 with a single use upload is refused and the body is never buffered
streaming028 | An operation with a streaming media type gets a stream method, and an SSE or JSON Lines only operation gets no buffered method
streaming029 | The read-all helper of a byte stream returns every byte in order and leaves the stream closed
streaming030 | A transport that does not implement the streaming method makes a stream call fail with the unsupported error that names the transport, and buffered calls on it keep working
streaming031 | Closing a live endless stream returns promptly and does not wait for the server to finish
streaming032 | A reset or truncation after the headers raises a StreamError, and a StreamError is a network error
streaming033 | Stopping a stream early without reading it to the end releases the connection
sse001 | An SSE stream yields the id, event, data and retry fields of each event, with message as the default event
sse002 | An SSE stream joins consecutive data lines with a newline and skips comment lines
sse003 | An SSE stream drops a leading byte order mark, accepts CR, LF and CRLF line ends (one table row each) and removes one space after the colon
sse004 | An SSE event with an empty data buffer is not delivered but its id is kept
sse005 | An SSE data payload is decoded as the declared schema, and a payload that does not decode raises a serialization error and ends the stream
sse006 | An SSE request sends Accept text/event-stream, Cache-Control no-cache and identity encoding unless the caller set them
sse007 | An SSE stream reconnects after the connection drops and sends the last event id in Last-Event-ID
sse008 | An SSE reconnect waits the server retry value, else the reconnect delay doubling to a cap
sse009 | An SSE GET stream stops after five consecutive failed reconnects by default and raises the last failure
sse010 | An SSE reconnect counter resets after an event is received
sse011 | An SSE stream does not reconnect after a 204, a 4xx status or a response that is not text/event-stream
sse012 | An SSE stream sends the initial last event id of the call on its first request
sse013 | An SSE reconnect rebuilds the headers so a refreshed token is the one sent
sse014 | An SSE retry field that is not digits and an id containing NUL are ignored
sse015 | An idle timeout on an SSE stream counts as a lost connection and reconnects
sse016 | An SSE request that has a body is not reconnected unless the caller turns reconnect on, and a single use body is never reconnected
sse017 | An SSE event with an id and no data is not dispatched but updates the last event id, and a reconnect resumes from it
sse018 | An SSE comment line such as a heartbeat is skipped and counts as activity for the idle timeout
sse019 | An SSE event cut off by the end of the connection is discarded
sse020 | An SSE reconnect sends Last-Event-ID only when the last event id is not empty
sse021 | The configured SSE terminator ends the stream and is not delivered
sse022 | An SSE event named error raises the SDK API error with the response status and the data as its body and is not reconnected, and an event with any other name is an item
sse023 | An unknown SSE field is ignored
```

All 56 ids apply to all twelve languages (the first 44 are the second proposal's, `streaming029` to `streaming033` and `sse017` to `sse023` follow from the owner decisions and the 2026 check). The catalogue ids are proposed here and are added to `src/spec/resources/test-catalogue.txt` with the implementation, not before. Expected exceptions (to be written in `test-catalogue-exceptions.txt` with the reason, never silently): node `streaming006` and `streaming007` (fetch owns Content-Encoding, precedent `transport049` and `errors042`); swift `streaming006`/`streaming007` if `URLSession` hides the decoder the same way (not verified); dart web transport for the same reason. No other gap is expected.

### 4.3 The promises in prose

- **Phase 1 and phase 2.** The SDK may retry a streaming call until the response headers are accepted for delivery, with the F823 rules (network error before any response, 408, 409, 429, 5xx except 501, backoff, jitter, `Retry-After`, `maxRetries`, one budget across hops). A retryable status body is read bounded (64 KiB) and discarded, and the connection closed, before the wait. After the first body byte is handed to the caller the SDK never retries; a failure is raised from the stream's next read.
- **Non-2xx.** The error body is read bounded to 64 KiB, the connection closed, and the typed error of #123 thrown exactly as for a buffered call. A caller never receives a stream for a non-2xx.
- **Timeouts.** `timeout` (the F827 per-call value, or the transport's) covers dial, TLS, send, headers, redirect hops and retry waits, and stops at the headers. `idleTimeout` (new) bounds the gap between two reads of the body, default 60 s for every kind (one default, 0.4 item 4; an SSE comment line counts as activity, `sse018`), `0` or none disables it, and expiry raises `NetworkTimeoutError` and is not retried (for SSE it is a lost connection and reconnects, `sse015`). No total-duration option: a caller cancels.
- **Errors.** A reset or truncation after the headers: `StreamError`, a subclass of `NetworkError` (status 0), added by the owner decision 0.4 item 3. Idle expiry: `NetworkTimeoutError`. Corrupt compression: `ApiError` with the real status and the cause (D13). Line, event or size over the limit (default 1 MiB per line or event): `SerializationError`. JSON Lines or SSE decode failure: `SerializationError` with the 1-based line number or the event id. An in-band SSE event named `error`: `ApiError` with the real status and the event data as its body (0.5). `StreamError` is the only new class.
- **Cancellation and closing.** Every handle has an idempotent `close()` in the language's idiom that aborts the connection instead of draining it, and stopping early without reading to the end does the same (`streaming033`). In Go the stream's `Close()` cancels the request context first and then closes the body, because Go 1.27 drains an HTTP/1 body on `Close()` and an endless feed would block (`streaming031`). Language-native cancellation (S7) aborts too and is never a timeout error. `Client.close()` cancels the streams that are open (0.5, `streaming021`); requests in flight that are not streams finish, and new calls fail with D29. A stream abandoned without closing is a leak the docs name, except where the language releases it (rust `Drop`, elixir process exit, php generator destruction).
- **Redaction (S15).** The string form of a stream and of `StreamOptions` shows kind, method, path without query, status, bytes delivered and last event id; never header values, body, event data or the query.
- **Compression.** Binary and JSON Lines send the same `Accept-Encoding` as the buffered path and decode incrementally with the language's streaming decoder (gzip and deflate everywhere; br and zstd where the buffered path has them). SSE sends `Accept-Encoding: identity` unless the caller set one (D21); if the server compresses anyway the stream still decodes. `bytesDelivered` counts decoded bytes.
- **Upload (`BodySource`).** A request body is a `BodySource`: replayable (bytes, a file or path that can be reopened, a factory that opens a fresh stream) or single use (a stream handed over). A replayable body is retried after a 5xx or 429 and replayed across 307 and 308. A single-use body is never buffered and never retried (the original error is raised unchanged), and a 307 or 308 is refused with `Refusing to replay a streamed request body`. A known length sends `Content-Length`; an unknown length is chunked. The SDK never closes a stream the caller passed (S16). Multipart bodies stay buffered (O5). The Swift `HTTPBody` pair (`length`, `iterationBehavior`) is the precedent (section 11.2 item 9).
- **SSE terminator and in-band errors.** A generator option `sseTerminator` (default none; proposed name) names a `data` value that ends the stream when it arrives and is not delivered (`sse021`); there is no hard-coded sentinel. An event named `error` raises the SDK's API error (`sse022`) and is not reconnected.
- **SSE grammar** (WHATWG HTML, "Server-sent events"): UTF-8, leading BOM dropped, lines end in CRLF, LF or CR, a blank line dispatches, an empty data buffer is not dispatched, fields `event` `data` `id` `retry`, a `:` line is a comment, one optional space after the colon is removed, unknown fields ignored, data lines join with a newline, an `id` with NUL is ignored, `retry` only digits, the last event id persists.
- **SSE reconnect.** After a clean end, a network error or an idle timeout, up to `maxReconnects` consecutive failures without an event (default 5 for a GET, 0 (off) for a request that has a body, `-1` unlimited; an event resets the counter, `sse010`; a caller can turn it on for a request with a body knowing that the request is sent again, and a single-use body is never sent again, `sse016`). Delay: the last `retry` value, else `reconnectDelay` (default 1 s) doubling with jitter to 30 s; `Retry-After` on 429, 502, 503, 504 as in F823. Each reconnect is a fresh request: headers rebuilt (authentication asked again, `sse013`), `Last-Event-ID` set, redirects and header stripping as for any call. No reconnect after 204, any non-2xx other than 408/429/5xx (typed error), a 2xx that is not `text/event-stream` (`SerializationError`, D32 wording), cancellation, client close, or a single-use request body. A 401 on reconnect follows the one-replay rule of `streaming018`. There is no reconnect hook in v1 (O4).

## 5. The shared generator changes

| File | Change |
| --- | --- |
| `src/main/java/io/github/mridang/codegen/generators/AbstractBetterCodegen.java`, `populateOperationDecorators` (one place, all twelve) | Compute and put on `vendorExtensions.op`: `streamKind` (`sse`, `jsonlines`, `binary`, `none`), `hasStreamMethod`, `streamOnly` (no buffered method), `streamItemType` (the type declaration of the item or event schema), `streamEventIsText`, `hasStreamRequestBody` (from the existing `requestBodyKind == "binary"`), `streamMethodName` per `NamingConvention`. Reading the 2xx media type: whether `CodegenResponse` exposes its content map in generator 7.22 was **not verified**; the fallback is the swagger `Operation` the class already reads around line 4754 for `r.getContent()`. Non-`x-` names, per the repository rule. |
| same file, buffered-method emission | An operation with `streamOnly` emits no buffered method, no `WithHttpInfo` variant and no Options-class members that only the buffered path needs (stream methods keep the Options class: it carries the operation's parameters and F827 values). |
| Java detector (`AbstractBetterCodegen`, same place) | Reads `schema` and `itemSchema` per 4.1.1 and accepts `x-oai-itemSchema` as an input. Adds the generator option `sseTerminator` (default none) and hands its value to the templates under a non-`x-` name. |
| `rules/OnlyAllowJsonRule.java` | Add `text/event-stream`, `application/x-ndjson`, `application/jsonl`, `application/jsonlines` to `ALLOWED_TYPES` (verified: the list and the validation step use it). |
| `AdvancedOpenAPINormalizer.java` | No change. |
| `src/spec/resources/specs/petstore/openapi.yaml` | Add (nothing removed or renamed, fixture rule) one NDJSON export operation, one SSE feed operation with a JSON schema, and one with a plain string payload; the existing binary download, binary upload and avatar operations already trigger `binary`. They are real operations, not `x-internal`, because internal ones are dropped from generation. |
| `src/spec/resources/specs/probe/openapi.yaml` | Add the shapes: stream-only operation with path and query parameters, both a request and a response stream, an SSE response with two success media types (decision on which wins is "the first declared streaming type"; an operation mixing streaming and JSON 2xx media types is rejected at generation with a warning through `SpecWarnings`). |
| Per-language Codegen (`Better<Lang>Codegen.java`) | Wire the new template files and reserved names (`SseStream`, `ServerSentEvent`, `ByteStream`, `JsonLinesStream`, `StreamOptions`, `BodySource`, `StreamError`); swift and kotlin model-name collision prefixes (D45) are checked against them. Rust adds the `stream` feature (see the table). |
| `src/test/java` unit tests | A generator unit test mirroring `AbstractBetterCodegen`: the five decorator values for each signal, `streamOnly`, the `OnlyAllowJsonRule` list, and a golden diff proving a spec without streaming signals gives an unchanged output except for the binary operations' new `...Stream` method (this is the one intended change to the existing goldens). |
| `FixtureVocabularyLeakTest` | Templates and generated-test sample data use neutral names (`/items`, `/events`); the fixture names live only in petstore-derived goldens. |
| Docs in the same change (root `AGENTS.md` rule) | `docs/decisions.md` (replace "Streaming is not supported" and the #114 line, link the issue), `docs/types.md` (line on binary and the "no SDK streams a response" sentence), `docs/how-it-works.md` (the retries paragraph: "a request whose body is a stream is never retried" becomes the replay rule), twelve `docs/languages/<lang>.md` (the "no streaming" lines, verified present in csharp, dart, elixir, go, java, kotlin, node, php, rust; ruby and python and swift pages not checked), the twelve `readme.mustache` and `skills.mustache` "Streaming" sections (character-aligned prose), `generators/AGENTS.md` (supersede F837, add the decisions of section 9, matrix rows) and each `<lang>/AGENTS.md`. |

## 6. Per language

### 6.1 Carriers and signatures after the owner decisions

This table wins over the cells of 6.2 wherever the two differ (the buffered binary methods of java and csharp, the bytes return of the other languages, the native-stream upload types). Names are the design names and are final when built. Every carrier has the status code, the lower-cased headers and an idempotent `close()` in the language's idiom; a byte-stream carrier also has the read-all helper.

| Language | Binary operation returns (read-all helper) | JSON Lines item sequence | SSE item sequence |
|---|---|---|---|
| java | `ByteStream extends InputStream` (`readAllBytes()` is inherited) | `JsonLinesStream<T> implements Iterator<T>, AutoCloseable`, `stream()` | `SseStream<T> extends Iterator<ServerSentEvent<T>>, AutoCloseable`, idempotent `close()`, `asPublisher()` (accepted example) |
| kotlin | `ByteStream`: cold `Flow<ByteArray>` plus `suspend fun readAll(): ByteArray` | cold `Flow<T>` | `SseStream<T>`: cold `Flow<ServerSentEvent<T>>` with `close()` |
| csharp | `ByteStream : Stream` with `ReadAllAsync(ct)` returning `byte[]` | `IAsyncEnumerable<T>` | `SseStream<T> : IAsyncEnumerable<ServerSentEvent<T>>, IAsyncDisposable` |
| php | `ByteStream implements StreamInterface` with `readAll(): string` | `\Generator` of `T` | `SseStream` implements `\IteratorAggregate` of `ServerSentEvent`, with `close()` |
| python | `ByteStream`, an async context manager and `AsyncIterator[bytes]`, with `await read_all()` | `JsonLinesStream[T]`, an `AsyncIterator[T]` | `SseStream[T]`, an `AsyncIterator[ServerSentEvent[T]]` with `aclose()` |
| ruby | `ByteStream` responding to `read(n)`, `each`, `read_all` and `close` | `JsonLinesStream`, an `Enumerator::Lazy` of items with `close` | `SseStream`, an `Enumerable` of `ServerSentEvent` with `close` |
| node | `ByteStream`: a web `ReadableStream<Uint8Array>` with `statusCode`, `headers` and `readAll(): Promise<Buffer>` | `JsonLinesStream<T> extends AsyncIterable<T>` with `close()` | `SseStream<T> extends AsyncIterable<ServerSentEvent<T>>`, idempotent `close()` (accepted example) |
| dart | `ByteStream`, a `Stream<List<int>>` with `readAll(): Future<Uint8List>` | `Stream<T>` | `Stream<ServerSentEvent<T>>` (cancelling the subscription closes) |
| go | `*ByteStream`, an `io.ReadCloser` with `ReadAll() ([]byte, error)` | `iter.Seq2[T, error]` | `iter.Seq2[ServerSentEvent[T], error]` (`break` or context cancellation closes; see `streaming031`) |
| rust | `ByteStream: Stream<Item = Result<Bytes, Error>>` with `read_all(self)` | `Stream<Item = Result<T, Error>>` | `SseStream<T>: Stream<Item = Result<ServerSentEvent<T>, Error>>` (drop closes) |
| swift | `ByteStream`, an `AsyncSequence` of `Data` with `readAll() async throws -> Data` | `AsyncThrowingStream<T, Error>` | `SseStream<T>`, an `AsyncSequence` of `ServerSentEvent<T>` with `close()` |
| elixir | `{:ok, stream}` where `stream` is an `Enumerable` of binaries; `ByteStream.read_all/1`; `!` variants raise | `Enumerable` of items | `Enumerable` of `%ServerSentEvent{}` |

An injected transport that does not implement the streaming method fails a binary operation with the unsupported error of 0.1; this is the breaking edge of 0.2 and is accepted.

### 6.2 Templates, library facts and limits per language

Common vocabulary in the table: `ByteStream`, `JsonLinesStream<T>`, `SseStream<T>` are the idiomatic stream types (each also exposes `statusCode`, `headers`, `close`); `ServerSentEvent<T>` has `id`, `event` (default `message`), `data`, `retry`, `raw`; `StreamOptions` has `idleTimeout`, `maxReconnects`, `reconnectDelay`, `lastEventId`, `maxEventBytes`. Names follow each language's casing. Every language gets new template files `api_stream_response` (the transport result), `event_stream` (line splitter, JSON Lines decoder, SSE parser, reconnect), `stream_options`, and edits `api_client`, `default_api_client` (or the abstract client where the loop is there), `base_api`, `api/api`, `transport_options`, `readme`, `skills`, `client` (close accounting), the test templates and the golden of the same language (`src/spec/resources/generated/<lang>/`, regenerated, all twelve committed together).

"Idiom and limit" says what the language uses and any real limit; "verified in" names what was read.

| Language | Public names and signatures | Templates touched (under `src/main/resources/templates/<lang>/`) | Idiom and real limit |
| --- | --- | --- | --- |
| java | `InputStream downloadX(...)` stays; `ByteStream downloadXStream(args, @Nullable Options, @Nullable StreamOptions)` (`ByteStream extends InputStream`, `AutoCloseable`); `JsonLinesStream<T>` and `SseStream<T>` are `Iterator<T>` plus `AutoCloseable` plus `stream()`; upload takes `InputStream` or `File` unbuffered; transport `default ApiStreamResponse sendStreaming(method, url, headers, body, timeoutMillis, maxRetries, idleTimeoutMillis)` throwing `UnsupportedOperationException` | `api_client`, `default_api_client`, `base_api`, `api/api`, `transport_options`, `client`, new `api_stream_response`, `event_stream`, `stream_options`, `test/DefaultApiClientUnitTest`, `test/BaseApiTest`, `test/ClientTest`, `readme`, `skills`; golden `generated/java` | Blocking types suit Java 21 virtual threads (floor 21, verified in `java/pom.mustache`). Use `HttpResponse.BodyHandlers.ofInputStream()` instead of `ofByteArray()` in a second send path; `sendBounded`'s whole-future timeout must not wrap it (verified in `default_api_client.mustache`). The redirect loop (verified, lines 600-740) is entangled with the buffered body, so it is extracted into a header-phase function used by both paths; today an `InputStream` upload is buffered on purpose for replay (verified): the stream method stops buffering and obeys the replay rule. Java `HttpClient.close()` waits for in-flight exchanges (floor reason, verified in AGENTS floor note). Library behaviour of `ofInputStream` cancel-on-close: not verified. Size L (reference implementation). |
| kotlin | `suspend fun downloadXStream(args, options, streamOptions): ByteStream`; types are cold `Flow<ByteArray>`, `Flow<T>`, `Flow<ServerSentEvent<T>>` (each a `StreamResult` with status and headers read before the first emission via a suspending `open()`); upload takes `ByteReadChannel` or `Flow<ByteArray>`; `ApiClient` gains `suspend fun sendStreaming(...)` with a default that throws | `api_client`, `default_api_client`, `base_api`, `http_client_factory_jvm`, `api/api`, `transport_options`, new stream files, tests `DefaultApiClientUnitTest`, `DefaultApiClientTest`, readme, skills; golden `generated/kotlin` | Ktor 3.5.0 with CIO (verified in `build_gradle.mustache`); the SDK installs `HttpTimeout` with `requestTimeoutMillis` equal to the whole budget (verified), so streaming needs a per-request override where the request timeout is infinite and the socket timeout is the idle timeout. Whether Ktor's socket timeout measures the read gap, and the `prepareGet().execute { }` channel-valid-only-inside-the-block rule, are **not verified** here. Multiplatform (`expect fun buildPlatformHttpClient`, verified): the parser stays in common code. Size M. |
| csharp | `Task<System.IO.Stream> DownloadXAsync(...)` stays (O1: becomes `byte[]`); `ValueTask<ByteStream> DownloadXStreamAsync(args, options, StreamOptions?, CancellationToken)` with `ByteStream : Stream`; `IAsyncEnumerable<T>` for JSON Lines and `IAsyncEnumerable<ServerSentEvent<T>>` with `[EnumeratorCancellation]`; upload takes `Stream`; `IApiClient` gains `SendStreamingAsync(...)` as a default interface method throwing `NotSupportedException` (default interface methods in this interface: **not verified**) | `api_client`, `default_api_client`, `base_api`, `api/api`, `transport_options`, new stream files, tests `DefaultApiClientUnitTest`, `DefaultApiClientTest`, `ClientTest`; golden `generated/csharp` | .NET 10 (verified in `csproj.mustache`). `SendAsync(request, token)` buffers the content by default, so streaming needs `HttpCompletionOption.ResponseHeadersRead`; the SDK's own `timeoutSource` token (verified) is passed to the body read too, so it is replaced by a timer cancelled at the headers and an idle token per read. `AutomaticDecompression = None` (verified): decode incrementally with `GZipStream`/`ZLibStream`/`BrotliStream` over the content stream. Size M. |
| php | `downloadXStream($id, ?Options, ?StreamOptions): StreamInterface`; JSON Lines and SSE return `\Generator`; upload takes `\SplFileObject|StreamInterface`; transport interface `StreamingApiClient` (separate, `instanceof`-checked, so the `ApiClient` interface keeps compiling for custom clients) with `sendStreaming(...)` | `api_client`, new `streaming_api_client`, `abstract_api_client`, `default_api_client`, `psr18_api_client`, `base_api`, `api/api`, `transport_options` (+ builder), new stream files, tests `DefaultApiClientUnitTest`, `Psr18ApiClientUnitTest`, `StubbedDefaultApiClient`; golden `generated/php` | PHP 8.3 (verified in `composer.mustache`); default transport Symfony HttpClient (verified), whose response streaming (`stream()` over lazy responses) is **not verified**. A PSR-18 client returns a `ResponseInterface` whose body may be lazy or buffered, **not verified**: `Psr18ApiClient` implements `sendStreaming` only as far as the response body is read lazily and documents that a buffering PSR-18 client delivers all at once, never a hang. A generator closes its connection in `finally` when destroyed. Retries and redirects are in `AbstractApiClient` (verified), shared by both transports. Size M. |
| python | `async def download_x_stream(...)` returning `ByteStream`, an async context manager and `AsyncIterator[bytes]`; `JsonLinesStream[T]` and `SseStream[T]` are `AsyncIterator`; `stream_options: Optional[StreamOptions] = None`; upload takes `bytes` or a binary file object or an iterable of bytes; transport `Protocol` `StreamingApiClient` with `send_streaming(...)` returning a handle with `read(n)` and `close()` | `api_client`, `default_api_client`, `base_api`, `api/api`, `transport_options`, `errors`-free, new stream files, tests `test_default_api_client_unit`, `test_base_api`, `test_default_api_client`; golden `generated/python` | Floor 3.10, urllib3 `>=2.1,<3` (verified in `pyproject_toml.mustache`). The SDK is async-first over a sync urllib3 client run with `asyncio.to_thread` (verified): each read is one `to_thread` call, and the existing `RequestHandle.cancel()` (verified) closes the socket on cancellation or `close()`. The client already requests with `preload_content=False, decode_content=False` (verified), so the response is open and unread; the decode is done by the SDK with `zlib.decompressobj`. Retries and the 401 replay are in `BaseApi._send_with_retries` (verified) and are reused. Whether urllib3 sends an iterable body chunked: **not verified**. Size M. |
| ruby | `download_x_stream(id, options = nil, stream_options = nil) { \|chunk\| }` block form, or without a block an `Enumerator`-backed object (`ByteStream` responds to `read(n)`, `each`, `close`); `JsonLinesStream` and `SseStream` are `Enumerator::Lazy` with `close`; upload takes `File` or any `IO`; transport `send_streaming(method, url, headers, body, ...)` found by `respond_to?` | `api_client`, `default_api_client`, `base_api`, `api/api`, `transport_options`, new stream files, rbs signatures (`faraday_rbs`, `infrastructure_rbs`, `types`), tests `default_api_client_unit_test`, `base_api_test`; golden `generated/ruby` | Faraday `~> 2.0` is the only HTTP dependency (verified in `gemspec.mustache`); the SDK's own comment says `net_http` opens a socket per request (verified). Faraday's `on_data` callback and which adapter supports it: **not verified** (a documentation fetch of the Faraday site returned no adapter details); if the default adapter lacks it the stream path drops to `Net::HTTP#request` with `read_body` for streaming calls only. Push-style callbacks become an `Enumerator` through a producer thread and a bounded queue. Steep must type the new files (RBS required, KT-3). Size L. |
| node | `downloadXStream(id, requestOptions?): Promise<ByteStream>` where `ByteStream` is a Web `ReadableStream<Uint8Array>` plus `statusCode` and `headers`; `exportXStream(requestOptions?)` returns `JsonLinesStream<T>` (`AsyncIterable<T> & AsyncDisposable`); `watchXStream(streamRequestOptions?)` returns `SseStream<T>`; `StreamRequestOptions extends RequestOptions` (adds the stream settings, used only by stream methods); upload body accepts `ReadableStream`, `AsyncIterable<Uint8Array>` and a Node `Readable` besides `Buffer`; transport `sendStreaming?` optional method on `ApiClient` | `api_client`, `abstract_api_client`, `default_api_client`, `web_api_client`, `base_api`, `api/api`, `transport_options`, new stream files, tests `default-api-client-unit.test`, `default-api-client.test`, `web-api-client.test`, api tests; golden `generated/node` | Node `>=22` (verified in `package.mustache`). Retries and redirect loop in `AbstractApiClient.sendCounted` (verified). `AbortSignal.timeout(timeout)` is passed to `fetch` and cannot be disarmed (verified usage), so the stream path uses an `AbortController` plus `setTimeout` cleared at the headers and a per-read idle timer. `fetch` owns Content-Encoding (verified in the exceptions file), so incremental decode is not the SDK's (exceptions `streaming006/007`). A stream request body needs `duplex: 'half'` in undici: **not verified**; the portable `web_api_client` shares the code with runtimes that may not support stream bodies (documented, refused at call time with a built-in `TypeError`). Size M. |
| dart | `Stream<List<int>> downloadXStream(id, [options, streamOptions])` for bytes (`ByteStream` = a `Stream<List<int>>` with `statusCode`, `headers`); `Stream<T>` and `Stream<ServerSentEvent<T>>`; upload takes `Stream<List<int>>` as well as `Uint8List` (the call type becomes a union through an extra named method `setXFromStream`, because Dart has no overloads); `ApiClient` gains a concrete default `sendStreaming` that throws `UnsupportedError` | `api_client`, `abstract_api_client`, `default_api_client_io`, `default_api_client_web`, `base_api`, `api/api`, `transport_options`, new stream files, tests `default_api_client_unit_test`, `default_api_client_test`, `base_api_test`; golden `generated/dart` | SDK `>=3.11`, `http ^1.2.0` (verified in `pubspec.mustache`). The client already calls `_httpClient.send(request)` and gets an `http.StreamedResponse` before reading it (verified in `abstract_api_client.mustache`), the smallest gap of all twelve; the retry loop is in `RetryingApiClient` (name verified, body not read). Cancelling the subscription cancels the request. The web variant cannot set the idle timeout or a request stream (documented). The response-side contract that an unlistened stream keeps the socket: **not verified**. Size M. |
| go | `DownloadXStream(ctx, id, callOptions ...RequestOption) (*ByteStream, error)` (`ByteStream` is an `io.ReadCloser` with `StatusCode()` and `Headers()`); `ExportXStream(ctx, ...) (iter.Seq2[T, error])`; `WatchXStream(ctx, ...) iter.Seq2[ServerSentEvent[T], error]`; stream settings are `RequestOption`s (`WithIdleTimeout`, `WithMaxReconnects`, `WithReconnectDelay`, `WithLastEventID`) like `WithRequestContentType`, which already applies to some operations only (verified in `request_option.mustache`); upload `io.Reader`, seekable via `io.Seeker` is replayable; transport interface `StreamingApiClient` (separate; a method added to `ApiClient` would break every custom implementation, as Go has no default methods) found by type assertion, the pattern `retryPolicyProvider` already uses (verified) | `api_client`, `default_api_client`, `base_api`, `api/api` (group interface `<Group>ApiInterface` gains the stream methods, F879), `request_option`, `transport_options`, new stream files, tests `default_api_client_unit_test`, `default_api_client_test`, `base_api_test`, `client_test`, api tests; golden `generated/go` | Go 1.25 (verified in `go_mod.mustache`), so `iter.Seq2` is available. The policy `http.Client` sets `Timeout`, which includes the body read (verified), so streams use a copy with `Timeout = 0` (the same copy trick the per-call timeout already uses, verified) and a timer that cancels the context unless stopped at the headers; an injected non-`*http.Client` `HttpDoer` has no client timeout, so the context timer does it (verified). Retries and the 401 replay are in `baseApi.invokeApiForResult` (verified), which a custom `ApiClient` never gets (so a custom streaming client is never retried, consistent with today). `Close()` does not cancel (comment verified in `default_api_client.mustache`; the matrix cell "cancelled by `Close()`" is stale: to correct). Size M. |
| rust | `download_x_stream(&self, id, options, stream_options) -> Result<ByteStream, Error>` where `ByteStream: Stream<Item = Result<Bytes, Error>>`; `JsonLinesStream<T>` and `SseStream<T>` are `Stream<Item = Result<T, Error>>`; `StreamOptions` builder (`StreamOptions::default().max_reconnects(5)`); upload takes `impl Stream<Item = io::Result<Bytes>> + Send + 'static` as well as `Vec<u8>` (through a second method name `set_x_from_stream`, no overloads); `ApiClient` trait gets `fn send_streaming(...)` with a default returning an error (the trait already has default methods `max_retries`, `request_timeout`, `close`, verified) | `api_client`, `default_api_client`, `base_api`, `api/api`, `cargo_toml`, `transport_options`, new stream files, tests `default_api_client_unit_test`, `default_api_client_test`, `base_api_test`; golden `generated/rust` | reqwest 0.12 with `default-features = false` and features `json multipart http2 charset rustls-tls-native-roots` (verified in `cargo_toml.mustache`): the `stream` feature is not enabled, and `Response::bytes_stream` requires it (verified against docs.rs 0.12). So the manifest gains `features = [..., "stream"]` (a version bump of a feature, no native binary, S10 holds) and likely `tokio-util` or `futures-util`. Decompression today is whole-buffer with `flate2`/`brotli`/`zstd` (verified in the manifest): an incremental async decode needs either `async-compression` (new pure-Rust dependency) or bridging the sync decoders; this is the one place where S10 is exercised (recommendation O6). `chunk()` needs no feature (verified in docs.rs). Dropping the stream cancels the request. Size L. |
| swift | `func downloadXStream(id:options:streamOptions:) async throws -> ByteStream` where `ByteStream` is an `AsyncSequence` of `Data` with `statusCode` and `headers`; `AsyncThrowingStream<T, Error>` for JSON Lines and `AsyncThrowingStream<ServerSentEvent<T>, Error>`; upload takes `InputStream` or an `AsyncSequence` of `Data` besides `Data` (an overload, as Swift has them); `ApiClient` protocol extension default `sendStreaming(...)` that throws, the pattern already used for `sendRequest(... noRedirect:)` (verified in `api_client.mustache`) | `api_client`, `default_api_client`, `base_api`, `api/api`, `transport_options`, new stream files, tests `DefaultApiClientUnitTests`, `DefaultApiClientTests`; golden `generated/swift` | Swift tools 6.0, macOS 12 and iOS 15 floors (verified in `package_swift.mustache`), which matches the OS floor of `URLSession.bytes(for:)`. The default client is delegate-driven on a `URLSession` with `dataTask` (verified), and imports `FoundationNetworking` on Linux (verified): `bytes(for:)` is **not verified** on Linux, so the recommended path is a `URLSessionDataDelegate` feeding an `AsyncThrowingStream` in all environments. URLSession negotiates and decodes encoding itself (verified comment), so incremental decode is not the SDK's (possible exceptions `streaming006/007`). Task cancellation cancels the stream. Size L. |
| elixir | `download_x_stream(api, id, options \\ nil, opts \\ [])` returns `{:ok, stream}` where `stream` is an `Enumerable` of binaries (a lazy `Stream.resource`) and `download_x_stream!` raises; `{:ok, enumerable of %Item{}}`; `{:ok, enumerable of %ServerSentEvent{}}`; stream settings in the existing `opts` keyword list; upload accepts an `Enumerable` of binaries besides a binary; `ApiClient` behaviour gets `send_streaming/6` as an optional callback (the behaviour already declares `@optional_callbacks`, verified) | `api_client`, `default_api_client`, `base_api`, `api/api`, `transport_options`, new stream files, tests `default_api_client_unit_test`, `default_api_client_test`, `base_api_test`; golden `generated/elixir` | Elixir `~> 1.17`, Req `~> 0.5` (verified in `mix_exs.mustache`); the SDK already sets `retry: false` and `raw: true` (verified) and owns retries and decompression, so the Req `:into` mode applies without a retry interaction. Req's `:into` modes (`:self`, `Req.Response.Async`, cancel) are **not verified** here (a documentation fetch returned only a note that decode and decompress steps are disabled when streaming). An error tuple is the language's own way (matrix: Elixir uses tagged tuples with `!` variants), unlike the other eleven's throws; this is an existing forced divergence. Leaving the enumeration early cancels the response. Size M. |

Idle-timeout and cancellation hooks per language are the same names the language already uses for the timeout (section 5.1 of each template is the existing deadline code, replaced for the stream path as the table says).

## 7. The tests

All behaviour ids of section 4.2 apply to all twelve. Where each lives (the repository rule: do not add test files; extend the templates that exist):

- Wire framing, timeouts, retry phase 1, redirects, reset and truncation, upload, close and cancellation (`streaming001-007`, `009-013`, `015-016`, `019-020`, `023-027`, `sse001-016` against the transport): the existing `DefaultApiClientUnitTest` template of each language (the in-process-server one), as table rows or sibling tests named with the ids. One local server per test, stateful where needed (the Nth request answers differently, a body dropped after N events, `Last-Event-ID` recorded).
- Behaviour through the operation (`streaming008`, `014`, `017`, `018`, `021`, `022`, `028`, `sse013`): `BaseApiTest` and `ClientTest` templates, plus the petstore API tests for the generated methods; `streaming028` asserts, per golden, the presence of the stream methods on the fixture operations.
- Prompt close (`streaming031`) is a Go test that closes a live endless feed and asserts the call returns within a short bound; the same test shape is run in every language as a close-while-blocked case.
- Parser and decoder pieces (`streaming002-005`, `sse002-005`, `sse014`, `sse017-sse023`) are driven through the transport rows above and asserted on the public stream types; the SSE grammar is one table of raw byte inputs and expected events, so each language runs the same table.
- A new mirror test file for the new source file `event_stream` (`<SseStream>Test` in each language) would be the global default (mirror the source file), but the repository rule says no new test files. Recommendation (O7): add it in all twelve in this one change; the catalogue compares by id, so twelve identically named files cannot drift, and the table of raw inputs belongs there, not in a transport test.
- Generator unit tests in `src/test/java` as in section 5; goldens regenerated for all twelve and committed together; `etc/ci-local.sh check <Lang>` per language, then the whole run, with the Docker caveats of the shared `AGENTS.md`.

Each id asserts what its promise says and nothing more; for example `streaming010` has a server that holds the headers for less than the call timeout and the body for longer, and asserts the stream completes; `streaming015` has a server that sends one chunk, closes, and counts requests; `sse007` has a server that drops after two events and asserts the second request carries `Last-Event-ID` equal to the id of the second event; `sse009` has a server that always drops without an event and asserts exactly `maxReconnects + 1` requests then `NetworkError`.

## 8. Risks and open questions

### 8.1 What the owner settled, and what is still open

| Item | Status |
|---|---|
| O1 binary stream or bytes | settled the other way: stream in all twelve (0.2) |
| O2 no generator option | settled: generated from the spec signal; one option, `sseTerminator` (0.5) |
| O3 one idle default | settled: 60 s, comment lines count (0.4 item 4) |
| O4 no `onReconnect` hook in v1 | not discussed; stays as recommended |
| O5 multipart stays buffered | not discussed; stays as recommended |
| O6 dependencies (rust `stream` feature and `async-compression`) | open |
| O7 one mirror test file per language for the stream source file | open |
| O8 `close()` and open streams | settled the other way: `client.close()` cancels streams (0.5) |
| O9 stream methods on the Go group interfaces | not discussed; stays as recommended |
| O10 custom transport without streaming | settled: the default method throws a clear error (0.1) |
| Reconnect default for a GET (five) against Fern's default of off | accepted as picked; confirm (0.5) |
| Which experimental or above-floor features to use | open points in 0.7 |
| The Chasm mock cannot serve a dropping SSE feed or a chunked body (verified in its source when this was written) | open: extend Chasm or keep in-process servers in the test templates; Go needs neither (`httptest.NewTestServer` and `testing/synctest` in Go 1.27, section 12) |

### 8.2 The second proposal's open questions (kept for the reasons)

The recommendations below were written before the owner decisions; where 8.1 differs, 8.1 wins.

Open questions with recommended answers (the first is the one #114 itself asks):

- **O1. Binary responses: stream or bytes in the buffered method?** Recommendation: bytes in every language, and the stream only in `<op>Stream`. Java, csharp and ruby return a memory-backed stream today (verified: `InputStream`, `Stream`, `File`/`StringIO`), which looks like streaming and is not, and the csharp skills text already says `byte[]` while the language page says `Stream`. The change is breaking for those three (`byte[]`, `byte[]`, binary `String`) and must land before the first release; if a release already went out, keep the types and document the memory backing. Upload parameter types stay native streams.
- **O2. No generator option.** Recommendation: none; the method appears when the spec signals it (the first proposal's `streaming=false` default is dropped). Cost: every existing golden gains the stream method for its binary operations. Alternative if the owner dislikes growing the surface of binary operations: generate the binary stream method only when the spec also declares `format: binary` responses over a size hint (not available), so no.
- **O3. One idle default.** Recommendation: 60 s for every kind including SSE, and for SSE an idle expiry is a lost connection that reconnects (`sse015`). This removes the "none for SSE" special case; a quiet server needs a heartbeat or a larger `idleTimeout` on the call.
- **O4. No `onReconnect` hook in v1.** Recommendation: leave it out (new callback API in twelve languages with retry semantics to settle, as for the declined #140 hooks); a reconnect is visible through the tracing hook of #108 when that lands. Add it later additively.
- **O5. Multipart with a stream part stays buffered in v1.** Recommendation: yes, as in the first proposal.
- **O6. Dependencies.** Recommendation: rust adds reqwest `stream` and `async-compression` (pure Rust, S10); swift, php, ruby add none; python none; node none. If rust prefers no new crate, decode br and zstd through blocking decoders on a `spawn_blocking` bridge (more code, less clean).
- **O7. One new mirror test file per language** for `event_stream` (see section 7). Recommendation: allow it for this change only.
- **O8. `close()` and open streams.** Recommendation: graceful (F1078): a stream is a request in flight and the transport is released when the last stream ends or is closed; a never-ending SSE stream keeps the transport until the caller closes it (documented). The first proposal's "close cancels streams" would contradict F1078 and Go's documented behaviour (verified); fix the stale Go matrix cell.
- **O9. Streaming methods and the per-group interfaces.** Adding methods to Go's `<Group>ApiInterface` (F879) breaks users' own fakes that implement it. Recommendation: accept (pre-release, the interface is generated), state it in the Go page.
- **O10. A custom `ApiClient` without streaming.** Recommendation: the stream call fails with the language's built-in unsupported-operation error naming the transport, never a silent buffered fallback (an SSE call would hang).

Real risks:

1. **The fixture server cannot stream** (verified in the chasm source). Every behaviour needs an in-process server in twelve test templates, and four languages (php, rust, swift, elixir) were not checked for an existing in-process server in their unit-test templates. Mitigation: stage 0 checks them; if one has none, a minimal server helper is added to that language's test template, not to the fixture.
2. **Two architectures for retries and deadlines** (section 3, points 3 and 4). A streaming path that reuses phase 1 must be written twice in shape (transport layer versus `BaseApi`), and each timeout facility must be disarmed at the headers. A mistake here either kills a long stream at the call timeout (the first proposal's note on csharp and go) or leaves a hung connection. Mitigation: `streaming010`, `011`, `012` and `015` are table rows in every language's unit test and are the first thing implemented after the reference.
3. **Unverified library facts in the languages with the least certain path:** ruby (Faraday `on_data`), elixir (Req `:into`), swift (Linux `bytes(for:)` and delegate streaming), php (Symfony response streaming and PSR-18 laziness), node (`duplex: 'half'`), kotlin (Ktor socket timeout), java (`ofInputStream` close), python (urllib3 chunked iterable bodies). Mitigation: stage 0 proves each with a ten-line probe in its language container before the stage that depends on it.
4. **The public surface grows by about fifty names per language** (three stream types, `ServerSentEvent`, `StreamOptions`, the transport entry, methods per operation) and the API guard (#122) does not exist yet; the twelve surfaces must be compared by hand (root `AGENTS.md`).
5. **The binary change (0.2)** is breaking in all twelve languages if a release has shipped: java, csharp and ruby change the type they return, and the other nine change from bytes to a stream.

## 9. Decisions recorded

Recorded on 2026-11-21 as D118 (one transport method), D119 (binary responses are streams), D120 (stream methods, carriers and the schema reading), D121 (the lifecycle rules: errors, timeouts, retries, uploads, closing) and D122 (server-sent events: reconnect, terminator, in-band errors, parser). F837 ("streaming is deferred") is superseded by them. The pagination decisions D123 and D124 are in [pagination.md](pagination.md).

## 10. Order of work and size per language

All twelve need real code; none can be done with tests or docs alone.

| Stage | Content | Size |
| --- | --- | --- |
| 0 | Probe the unverified library facts of risk 3 and the unit-test servers of php, rust, swift, elixir; verify `CodegenResponse` content exposure in generator 7.22; owner answers O1 to O10 | 2 days |
| 1 | Shared generator (decorators, `OnlyAllowJsonRule`), fixture operations in the petstore and probe specs, catalogue lines and the `test-catalogue-exceptions.txt` draft, generator unit tests, doc skeletons; goldens regenerated (binary operations gain the method only after stage 2) | M |
| 2 | java reference (the binary carrier of 0.2 lands here for java and in each later stage for the others): header-phase refactor of the redirect and retry code, `sendStreaming`, the three stream types, SSE parser and reconnect, upload without buffering, the 44 ids, readme and skills sections | L |
| 3 | The languages closest to java by shape: kotlin (M), csharp (M), go (M), dart (M, smallest gap) | M each |
| 4 | node (M), python (M), php (M), elixir (M) | M each |
| 5 | The languages with the heaviest library questions: ruby (L), rust (L), swift (L) | L each |
| 6 | Cross-language parity pass against the java surface (public members compared by hand), `docs/decisions.md`, `docs/types.md`, `docs/how-it-works.md`, twelve language pages, `AGENTS.md` decisions, supersede F837, stale Go matrix cell | M |

Sizes: java L, kotlin M, csharp M, php M, python M, ruby L, node M, dart M, go M, rust L, swift L, elixir M. Releasing all twelve together is the standing rule; a partial release is possible only if the owner accepts it. Stages 3 to 5 can run in parallel worktrees, at most four gates at once (shared `AGENTS.md`), and each stage ends with `etc/ci-local.sh check <Lang>` per language and a push once per round.


## 11. Benchmark

### 11.1 Comparison table

Columns: API shape, sync versus async, how a reconnect or next chunk is surfaced, how errors and early close behave.

| SDK / generator | API shape | Sync / async | Reconnect and resume | Errors and early close | Source |
|---|---|---|---|---|---|
| OpenAI Python (Stainless) | `client.responses.create(..., stream=True)` returns a `Stream`; `for event in stream`; async client `async for`; `.with_streaming_response` for raw bodies | both | none: the decoder keeps `last_event_id` state but no code sends a `Last-Event-ID` header | The `Stream` is a context manager with `close()`; the generator closes the response in a `finally` "even if the consumer doesn't read all data"; a `[DONE]` data line ends the stream (hard-coded); an `error` payload raises `APIError` with the message; timeouts and connection failures raise `APITimeoutError` and `APIConnectionError` | [T1] [T2] |
| OpenAI Node | `stream: true` returns an async iterable: `for await (const event of stream)` | async | none documented | `.asResponse()` returns the raw `Response` before the body is consumed | [T3] |
| Anthropic Python | `create(..., stream=True)` iterates events; helper `client.messages.stream(...)` as a context manager with `text_stream` and `get_final_message()`; `.with_streaming_response` "requires a context manager" so the response "is reliably closed" | both | none in the SDK; the API docs describe manual recovery: capture the partial response and start a new request that continues it | Retries default to 2 for connection errors, 408, 409, 429 and 5xx; default timeout 10 minutes; `httpx2.Timeout(read=5.0)` shows a per-read timeout option; the SDK refuses a non-streaming request expected to exceed about 10 minutes and recommends streaming for long requests | [T4] [T5] |
| Anthropic TypeScript | `stream: true` async iterable; helper `.stream(...).on('text', ...)` and `finalMessage()` | async | none | "you can `break` from the loop or call `stream.controller.abort()`" | [T6] |
| Anthropic API (the wire) | SSE with `ping` events scattered through the stream; an `error` event can arrive in-band after a 200 (example: `overloaded_error`, normally HTTP 529) | n/a | recovery is by a new request, not `Last-Event-ID` | an in-band error is data, so a client must handle it after the status line was already 200 | [T7] |
| ogen (Go) | SSE client generated for `text/event-stream`: `Client[E]` with `Next(ctx)`, `All(ctx) iter.Seq2[E, error]`, `State()`, `Close()`; "only SSE client generation is supported" | sync with ctx | automatic: options for the initial `Last-Event-ID`, retry delay, maximum reconnect attempts, decoder buffer size, maximum event size, a retry error handler; the last id and retry are updated even from undispatched events | an event larger than the maximum returns `ErrEventTooLarge` and the rest is drained without closing the stream; `Close()` | [T8] |
| ogen JSON | optional `x-ogen-json-streaming: true` per media type for streaming JSON encoding; by default the whole body is loaded before decoding | sync | n/a | n/a | [T8] |
| progenitor (Rust) | response kind `ByteStream` for `application/octet-stream` (`OperationResponseKind` mapped to a `ByteStream` type), `reqwest::Upgraded` for Dropshot websockets; a raw body parameter can be streamed only once, so such operations are excluded from `_stream` pagination | async | none | not documented | [T9] |
| swift-openapi-generator and runtime | every body is an `HTTPBody`, an `AsyncSequence` of byte chunks with `length` (`.known(n)` or `.unknown`) and `iterationBehavior` (`.single` or `.multiple`); streaming added in 0.3.0; `asDecodedJSONLines`, `asDecodedJSONSequence`, `asDecodedServerSentEvents`, `asDecodedServerSentEventsWithJSONData` convert the byte sequence; "Transport providers don't need to add any support"; some transports may still buffer | async | none: parsing only; `id` and `retry` are exposed as data | a body must be collected with a size limit (`collecting(upTo:)`); parsing a JSON line still buffers the whole line | [T10] [T11] |
| Rust reqwest | `Response::bytes_stream()` needs the `stream` feature; `chunk()` returns `None` at the end | async | n/a | n/a | [T12] |
| reqwest-eventsource | `EventSource` yields `Event::Open` and `Event::Message`; errors arrive as `Err` items; the crate "retries failed requests"; `close()` | async | retries (policy not described); Last-Event-ID not mentioned | the example closes after an error | [T13] |
| Java `HttpClient` | `BodyHandlers.ofInputStream()`: headers read, body "can be read as it is received"; `ofLines()`; `ofPublisher()` is a `Publisher<List<ByteBuffer>>` that "can and must be subscribed to only once"; `ofByteArrayConsumer` is "not flow controlled" | sync stream or reactive | n/a | closing and cancellation are described under `BodySubscribers`, which the page did not cover | [T14] |
| Go `net/http` | `resp.Body` is non-nil; "Caller should close"; unread and unclosed bodies can prevent connection reuse; `Client.Timeout` "will interrupt reading of the Response.Body" | sync | n/a | an end-to-end `Client.Timeout` cuts a stream | [T15] (partial) |
| .NET | `System.Net.ServerSentEvents.SseParser<T>` with `EnumerateAsync(CancellationToken)`, `LastEventId`, `ReconnectionInterval`: a parser only, it does not reconnect | async | none (properties are exposed for the caller) | cancellation token | [T16] |
| Ktor client (Kotlin) | body streaming through `prepareGet(url).execute { response.body<ByteReadChannel>() }` ("By default, calling `body()` loads the full response into memory"); SSE plugin `install(SSE)`, `sse()`, `incoming` flow, `showCommentEvents()`, `showRetryEvents()` | coroutines | opt-in: `maxReconnectionAttempts` above 0 enables it, `reconnectionTime`; `Last-Event-ID` not mentioned on the page | session ends when the block finishes | [T17] [T18] |
| Python `httpx-sse` | `connect_sse` / `aconnect_sse` yield an `EventSource`; `iter_sse()`; sets `Accept: text/event-stream` and `Cache-Control: no-store`; raises `SSEError` when the type is not `text/event-stream` | both | none: "does not handle reconnection"; a broken connection raises `httpx.ReadError` and the caller can use `id` and `retry` | error on wrong content type | [T19] |
| Go `go-sse` | `sse.Client`; `retry` field sets the delay, else `Backoff` (default 500 ms); `MaxRetries` of -1 disables reconnection; cancelling the request context stops the connection and pending reconnection | sync | automatic | `Connect()` returns the context error | [T20] |
| PHP Guzzle | request option `stream: true`: "stream a response rather than download it all up-front"; read with `getBody()->read(n)` until `eof()`; "Streaming response support must be implemented by the HTTP handler used by a client"; `read_timeout` applies to each read | sync | n/a | a read that times out returns `false` | [T21] |
| Python urllib3 | `preload_content=False` leaves the response unread; wrap with `io.TextIOWrapper` and set `auto_close = False` for line iteration | sync | n/a | the guide says streaming is better "for responses of large or unknown length" | [T22] |
| Ruby Faraday | request option `on_data` receives `(chunk, overall_received_bytes, env)`; "currently only supported by some adapters"; the `net_http` adapter has a streaming branch (`stream_response`) | sync, callback | n/a | the `env` argument is recent, so some adapters pass only two | [T23] |
| Dart `package:http` | `StreamedResponse.stream` is a `ByteStream`; `contentLength` may be null | async | n/a | listening and closing guidance not found on the page | [T24] |
| Elixir Req | when `:into` is a function or collectable the decode and decompress steps are off (the docs for `compressed`, `decode_body`, `decompress_body` say so) | sync with callbacks | n/a | `:self` mode and retry behaviour with `:into` were not on the page fetched | [T25] (partial) |
| AWS SDK Java v2 event streams | async client: `client.startStreamTranscription(request, new AudioStreamPublisher(...), responseHandler)` returns `CompletableFuture<Void>`; handler built with `onResponse`, `onError`, `onComplete`, `subscriber(event -> ...)`; input stream is a reactive `Publisher` | async reactive | none | `onError` callback | [T26] |
| AWS SDK Rust event streams | `Receiver<T, E>::recv()` returns `Result<Option<T>, SdkError<E, RawMessage>>`: `Ok(Some)` message, `Ok(None)` end, `Err` transport failure such as `DispatchFailure`; modeled service errors "will be a part of the returned messages" | async | none | an in-band modeled error is a message, a transport error is `Err` | [T27] |

### 11.2 What the table shows

1. Reconnect is rare as a default. Of the SDKs above that consume SSE, the ones that reconnect on their own are ogen (automatic, configurable), go-sse (automatic), reqwest-eventsource (retries), Fern (opt-in `resumable: true`, section 7) and Ktor (opt-in `maxReconnectionAttempts`). The ones that do not are OpenAI and Anthropic (Stainless), httpx-sse, .NET `SseParser`, the Swift runtime and Speakeasy (not documented). Anthropic's own docs recover an interrupted stream by sending a new request [T7]. [T1][T4][T6][T8][T13][T16][T17][T19][T20]
2. The browser standard says to reconnect, so libraries that reconnect follow it. WHATWG: on a network error the user agent reconnects; the `Last-Event-ID` header is sent when the last event id is non-empty; HTTP 204 and any non-200 or non-`text/event-stream` response fail the connection with no reconnect; `retry` sets the delay when it is all digits; an incomplete event at end of file is discarded; a `data`-less event is not dispatched [T28]. This design's parser rules (its section 8) match this text.
3. Replaying a request that has a body is the hazard. OpenAI and Anthropic stream over POST with a body (`create(..., stream=True)`), where a reconnect means sending the request again. Fern's `resumable` flag is explicit and opt-in for this reason (section 7), and Anthropic documents a manual continuation instead [T7]. A browser `EventSource` is GET only. My reading: automatic reconnect is safe for a GET without a body and a risk for anything else.
4. Early close is solved by language constructs, not by a close call alone: a Python generator with `finally: response.close()`, a context manager, `break` calling `return()` in Node, `controller.abort()`, Go `Close()`, Java try-with-resources. The one rule every SDK in the table follows is that stopping early must release the connection. [T1][T6][T8]
5. Errors come in two kinds and the benchmark separates them: a failure of the transport (reset, timeout, truncated body), and an in-band error event after a 200 (Anthropic `overloaded_error`, OpenAI `error` payloads that the Python SDK turns into `APIError`). AWS Rust makes the same split (`Err` versus an error inside the message) [T27]. [T1][T7]
6. A terminal sentinel is a vendor concern. OpenAI's SDK hard-codes `[DONE]`; Speakeasy, APIMatic and Fern each expose it as a spec-level setting (section 7). It cannot be hard-coded in a generic generator.
7. The end-to-end timeout does not fit streams. Go's `Client.Timeout` "will interrupt reading of the Response.Body" [T15]; .NET `ResponseHeadersRead` bounds only the headers (this design cites the same Microsoft remark); Anthropic exposes a per-read timeout in `httpx2.Timeout(read=...)` [T4]; Guzzle has `read_timeout` per read [T21]; APIMatic has a stream read timeout (default 60 seconds in TypeScript and .NET, [V6]). This supports this design's split into a header-phase `timeout` and a per-gap `idleTimeout`.
8. Streaming a body is usually a separate, opt-in call in Stainless SDKs and the default in others. Python's `.with_streaming_response` is separate and needs a context manager [T5]; the TypeScript `.asResponse()` returns before the body is consumed [T6]; Swift and progenitor make the stream the only form [T10][T9]; ogen's JSON default is the whole body [T8].
9. The Swift `HTTPBody` pair `(length: known or unknown, iterationBehavior: single or multiple)` is the same distinction as this design's `BodySource` constructors (`of(bytes)` and `fromFactory` are replayable and `once(stream)` is single use). That is independent support for the proposal's section 12. [T11]


## 12. Check against the 2025 and 2026 releases (as of 2026-11-21)

Scope: verify the streaming and pagination designs against language, runtime, spec and vendor changes released or stabilised in 2025-2026. No repository file was changed. Method: WebSearch plus WebFetch on primary sources where reachable. Confidence tags: HIGH = fetched a primary page that says it; MED = primary page partly read, or reliable secondary; LOW = search summary only; UNVERIFIED = could not be confirmed (stated as such). Several primary pages returned 403 (openjdk.org, oracle.com), so the JDK items are weaker than the rest.

### Changes to the proposal (ordered by importance)

1. Go 1.27 changes early close (HIGH). Go 1.27 (August 2026) drains unread content from an HTTP/1 `Response.Body` on `Close()`, up to a limit, to improve connection reuse. Our rule "stopping a stream early releases the connection" (lifecycle304) still holds, but for an endless SSE feed a drain would block or buffer, so the Go stream `Close()` must cancel the request context first and then close the body, and a test must show that closing a live SSE feed returns promptly. Same release adds `httptest.NewTestServer` on an in-memory network for `testing/synctest`, which answers section 7 of this page and Q10 of the pagination design for Go: a dropping SSE feed and per-cursor pages can be tested deterministically without chasm. Source: https://go.dev/doc/go1.27.
2. OpenAPI 3.2.0 is final and tooling has caught up (HIGH for the spec, MED for tooling). Released September 2025. `itemSchema` describes each item of a sequential type (JSON Lines, NDJSON, `application/json-seq`, `text/event-stream`), `schema` applies to the whole body read as an array, and both may appear. For SSE the generic event schema requires `data` (string), `event` and `id` are strings, `retry` is an integer with minimum 0, and `contentMediaType` plus `contentSchema` describe JSON inside `data`. A registered extension `x-oai-itemSchema` backports `itemSchema` to 3.0 and 3.1 specs (LOW, registry page seen only in search results: https://spec.openapis.org/registry/extension/x-oai-itemSchema). Swagger announced 3.2 support across its open-source tools on 2026-04-10, and the Microsoft OpenAPI .NET library exposes `ItemSchema` for 3.2. Effect: the "pinned swagger-parser rejects 3.2 (issue #50)" premise in section 4.1.1 of this page is probably stale; re-check the parser version. Write the reading rule now as this page already recommends (3.0/3.1: `schema` is the item for JSON Lines and the `data` payload for SSE; 3.2: `itemSchema` is the item, for SSE the envelope), and add `x-oai-itemSchema` as an accepted input in the Java detector, never in a template (same rule as pagination Q8). Sources: https://spec.openapis.org/oas/v3.2.0.html, https://learn.openapis.org/upgrading/v3.1-to-v3.2, https://swagger.io/blog/swagger-launches-support-for-openapi-3-2-0/.
3. Elixir: Req changed its defaults and is about to change its streaming API (MED; one conflict, see 8). Req v0.6.1 (2026-06-08) disabled automatic decompression, which now needs `compressed: true`; v0.6.0 removed automatic zip, tar, gz, zst and csv decoding. The Elixir transport must set decompression explicitly for buffered and streaming calls rather than rely on defaults. The Req `main` CHANGELOG lists v0.8.0-rc.0 (2026-08-18) adding `Req.stream/4`, streaming decompression, request body streaming via `body: fun`, built-in SSE and NDJSON decoding, and deprecating `into: fun`; but the hexdocs changelog shows v0.7.5 (2026-10-06) as the newest release and no 0.8 entry. Treat 0.8 as unreleased: do not build on `into: fun` without a wrapper, pin Req, and keep our own SSE and JSON Lines parsers (a Req built-in parser would be a dependency we do not control). Sources: https://raw.githubusercontent.com/wojtekmach/req/main/CHANGELOG.md, https://req.hexdocs.pm/changelog.html.
4. HTTP QUERY is now RFC 10008 (June 2026, Proposed Standard) (HIGH). OpenAPI 3.2 added the `query` operation. This changes pagination Q9: a search endpoint that takes a body and is read-only should be a QUERY operation, and the `Pages` detector can accept QUERY with a cursor in the body as a later tier. swift-openapi-generator 1.14.0 already added QUERY and other methods (HIGH). Keep it out of v1 but record that the "GET only" eligibility rule has a standards-backed successor. Sources: https://www.rfc-editor.org/info/rfc10008, https://api.github.com/repos/apple/swift-openapi-generator/releases.
5. SSE parser test cases to add, from vendor bug fixes (MED). Fern 2026-03-30: a comment line such as `: heartbeat` crashed the stream, and all line endings (LF, CR, CRLF) had to be handled. Speakeasy v1.751.1: events with no `data` are skipped in Python, TypeScript, Java, Go, C# and Ruby. WHATWG spec text (re-read 2026-11-21): a data-less event is not dispatched, but the last event id is still recorded, an id containing NUL is ignored, and the valid id value space is an open spec issue (whatwg/html #7363). MCP SEP-1699 (search result only, LOW) relies on a server sending an id-only priming event with empty data and then closing, so reconnect must resume from an id that never produced a dispatched event. Suggested ids: `results315 | An event with an id and no data is not dispatched but updates the last event id`, `results316 | LF, CR and CRLF line endings are all accepted`, `transport312 | A comment line is accepted and counts as activity`. Sources: https://html.spec.whatwg.org/multipage/server-sent-events.html, https://buildwithfern.com/learn/sdks/generators/go/changelog/2026/3/30.md, https://www.speakeasy.com/changelog/release/v1.751.1.
6. Rust: `AsyncIterator` is still nightly (HIGH). Tracking issue rust-lang/rust#79024 is open, last updated 2026-10-24, the "Stabilization PR" box is unchecked and open questions (restoring `Stream::next`, `poll_next` versus `async fn next`) remain. Keep `futures::Stream` as the carrier. reqwest notes: 0.13 made `query` and `form` opt-in features and made rustls the default TLS; `ClientBuilder::read_timeout` (0.12.4) is per read and resets after each read, which is exactly our `idleTimeout` and should be used instead of a hand-written timer; `ClientBuilder::retry(policy)` (0.12.23) is a built-in retry layer, so the Rust transport must make sure it is not enabled alongside our own retries (retries must stop at the first body byte, our rule). `bytes_stream` still needs the `stream` feature. Source: https://api.github.com/repos/rust-lang/rust/issues/79024 and https://raw.githubusercontent.com/seanmonstar/reqwest/master/CHANGELOG.md (the changelog has no dates, so exact release dates are unverified).
7. Node: global `EventSource` is still experimental and off by default (HIGH). v25.9 docs: Stability 1, needs `--experimental-eventsource`, added v22.3.0. So the node SDK must not depend on it; our own parser over `fetch` is right (and `EventSource` cannot do POST or custom headers anyway; the WHATWG constructor has only `withCredentials`). `ReadableStream` is stable (v23.11.0, v22.15.0); `AbortSignal.any` exists since v20.3.0 and v18.17.0; `fetch` stable since v21.0.0; global `WebSocket` stable since v22.4.0. Source: https://nodejs.org/docs/latest-v25.x/api/globals.html and https://nodejs.org/docs/latest/api/globals.html.
8. Dart `package:http` gained cancellation (HIGH). 1.5.0 added `AbortableRequest` (abort before completion); 1.6.0 fixed cancelling a body subscription on the web; 1.7.0-wip fixes the connection not being released when aborted before the body was read, changes `IOClient` so a bodyless GET/HEAD with null `contentLength` is no longer chunked, and throws for a GET/HEAD that streams a non-empty body with unknown length. Effects: (a) use `AbortableRequest` (or the `abortTrigger`) for `close()` and early stop rather than relying only on cancelling the stream subscription, which also resolves the open "never-listened stream keeps the socket" item; (b) our `BodySource` upload path must send a known `contentLength` or a non-GET method. Source: https://raw.githubusercontent.com/dart-lang/http/master/pkgs/http/CHANGELOG.md.
9. .NET: SSE parser is official (MED). `System.Net.ServerSentEvents` (`SseParser<T>`, `SseItem<T>`, `EnumerateAsync`, `LastEventId`, `ReconnectionInterval`) is documented for net-10.0 and net-11.0 with a package version 10.x and 11.0.0 previews; whether it is in the shared framework or a NuGet package for each TFM is UNVERIFIED. It is a parser only, no reconnect (the draft already says so). Option for csharp: use it instead of an own parser, which gives the platform-maintained WHATWG behaviour; costs one package dependency and a different error model (the draft's "own parser written to WHATWG rules" would then be tested only for the other 11). `GetFromJsonAsAsyncEnumerable` and `ReadFromJsonAsAsyncEnumerable` stream a JSON array, not JSON Lines, so they do not replace our JSON Lines reader. Sources: https://learn.microsoft.com/en-us/dotnet/api/system.net.serversentevents.sseparser-1, https://www.nuget.org/packages/System.Net.ServerSentEvents/10.0.10.
10. Java read-all cap (HIGH) and HTTP/3 (LOW). `HttpResponse.BodyHandlers.limiting(handler, capacity)` is new in 25: fails with `IOException` and cancels upstream if the body exceeds the capacity. Use it for the bounded read-all helper on binary responses. `ofInputStream` requires the caller to close the stream to release the exchange (the cancel-on-early-close detail is still in `BodySubscribers`, not fetched, so the Java close behaviour stays UNVERIFIED). HTTP/3 in `HttpClient` is JEP 517 in JDK 26 (LOW, search snippet only; JEP page returned 403). `HttpClient` is `AutoCloseable` (JDK 21 onward; seen in the JDK 26 API listing). Source: https://docs.oracle.com/en/java/javase/25/docs/api/java.net.http/java/net/http/HttpResponse.BodyHandlers.html.
11. Vendors (MED): see section "Vendors" below. Net effect on our drafts: Fern now auto-detects SSE and ships `resumable` (default false) and `terminator`, confirming reconnect opt-in; Speakeasy extended SSE to Ruby and C# and URL pagination to C#; ogen 1.21.0 added an SSE client for OpenAPI 3.0; Kiota has no SSE yet (open issue); nobody added pagination inference.

### Findings per language

#### Java (JDK 25, 26)
- `BodyHandlers.limiting` since 25, HIGH (above). Use for the bounded read-all helper.
- JEP 517 HTTP/3 in `java.net.http`, JDK 26, LOW. No change to `ofInputStream` shape; our transport should not assume HTTP/1.1 framing.
- `HttpClient` implements `AutoCloseable`, MED (JDK 26 API listing; first release not confirmed). A JDK 17 backport fixed "HttpClient with StructuredTaskScope does not close when a task fails" (JDK-8350010), so closing the client is now safe inside scopes.
- Structured concurrency and Gatherers: could not fetch JDK 25/26 project pages (403). From memory only (UNVERIFIED): Gatherers final in JDK 24; `StructuredTaskScope` still a preview in 25 and 26. Neither changes the proposal: the Java carrier stays a blocking `AutoCloseable` iterator, and a Gatherer-based `Stream<T>` view is an optional adapter, not the base.
- Source: https://cr.openjdk.org/~alanb/sc-jdk26/api/java.net.http/java/net/http/HttpClient.html, https://bugs.openjdk.org/browse/JDK-8350010.

#### Kotlin (Ktor 3.4 to 3.6)
- Ktor SSE and streaming fixes through 3.6.1 (2026-10-30), HIGH from the release notes: 3.4.0 (2026-01-23) Java engine did not close the connection when an SSE session was cancelled, and SSE handler added a `Connection: Keep-Alive` header incompatible with HTTP/2; 3.5.0 added a custom SSE heartbeat function and fixed cancelling a `ByteReadChannel` body not reaching the engine; 3.5.2 CIO SSE client sent a spurious `Content-Length` on body-less GET; 3.6.0 Android could hang when cancelling a streaming response during a blocking read, OkHttp duplex streaming did not release the connection; 3.6.1 `HttpStatement.execute` can hang when its block abandons a response body. None mention `Last-Event-ID`.
- Effect: confirms the draft's decision to ship our own SSE parser and own reconnect (Ktor reconnect still does not document `Last-Event-ID`). The hang and leak fixes are a reason to put Ktor-engine-specific early-close tests (cancel mid-read on JVM, Android if supported) in the Kotlin tests; lifecycle304 should run against more than one engine. Source: https://api.github.com/repos/ktorio/ktor/releases.

#### C# (.NET 9, 10, 11 preview)
- See change 9. Server side `TypedResults.ServerSentEvents` exists for minimal APIs (LOW, blog posts), irrelevant to a client SDK.
- `IAsyncEnumerable` itself: no change found. `ConfigureAwait`, `[EnumeratorCancellation]` unchanged (not re-checked, UNVERIFIED).

#### PHP (8.5, Guzzle, PSR)
- PHP 8.5.0 released 2025-11-20 (HIGH for date): URI extension (`Uri\Rfc3986\Uri`, `Uri\WhatWg\Url`), pipe operator, `#[\NoDiscard]`, clone-with. Nothing for streams or generators. The URI extension could parse the Link header `rel="next"` target and compare origins (pagination008) without a hand-written URL parser, but the SDK floor decides if it is usable. Source: https://www.php.net/releases/8.5/index.php.
- Guzzle: both 7.16.x and 8.5.x lines are current, latest 8.5.3 and 7.16.4 both on 2026-11-20, HIGH. 8.5.2 / 7.16.3 (2026-11-17) excluded URI fragments from stream handler request targets (security), 8.5.1 / 7.16.2 cancel cURL multi transfers whose synchronous wait throws and reject CR, LF, NUL in header fields. So Guzzle 8 exists and is the maintained major; streaming (`stream: true`) still depends on the handler. Source: https://api.github.com/repos/guzzle/guzzle/releases.
- PSR-18: no successor or revision found, MED (search). PSR-18 cannot express streaming, which supports our own transport method. `aternos/curl-psr` 2.0.0 (2026-01-28) streams requests and responses over PSR-18 and `puff/http-client` is Fiber-aware: LOW, third party, not evaluated.
- amphp/revolt/Fibers: no 2025-2026 change found (UNVERIFIED, one search).

#### Python (3.14, 3.15)
- Python 3.14 released 2025-10-07, 3.15 on 2026-10-09 (HIGH). 3.14: asyncio introspection (`python -m asyncio ps/pstree`, `capture_call_graph`), `create_task` kwargs, free-threaded build officially supported by PEP 779 (body not read, MED), `concurrent.interpreters` (PEP 734), `http.server.HTTPSServer`. 3.15: `TaskGroup.cancel`, `contextlib` decorators now keep the context open across a generator or async generator (gh-125862), PEP 798 star-unpacking in async comprehensions, `threading.serialize_iterator`, `synchronized_iterator`, `concurrent_tee`, `http.client` `max_response_headers`. The 3.15 page was read only to character 100000 (MED). Effect: none changes the carriers. `TaskGroup.cancel` and the introspection tools are useful for the thread-backed async facade tests (the python row of 6.2); `threading.serialize_iterator` could protect a shared sync stream iterator but is not needed.
- httpx: latest release 0.28.1 (2024-12-06); `1.0.0` is listed as unreleased with minimum Python 3.10 (HIGH). No 2025-2026 streaming or timeout changes. So `httpx-sse` and `with client.stream()` remain current; the proposal's Python transport must not assume httpx 1.0 yet. Source: https://raw.githubusercontent.com/encode/httpx/master/CHANGELOG.md. aiohttp changes: not checked (UNVERIFIED).

#### Ruby (4.0)
- Ruby 4.0.0 released 2025-12-25, HIGH. `Net::HTTP` default gem 0.9.1 (from 0.6.0), details of the bump not read (UNVERIFIED for streaming-related changes; the NEWS page was read only to character 100000 and the Net::HTTP entry lists no feature). `Enumerator.produce` accepts `size:` (Feature #21701), useful for the paginator `Enumerator` (an infinite or unknown size can be declared). Fiber scheduler: new `fiber_interrupt` hook (a fiber blocked on an IO is interrupted when the IO is closed, feature #21166), `io_close` hook returned, `io_write` called when the buffer is flushed. Effect: if the Ruby stream `close` is called from another fiber, the scheduler can now interrupt a blocked read; add a test under a Fiber scheduler (optional). `Ractor::Port` replaces `Ractor.yield` and `take`; irrelevant (our producer is a thread). `Socket.tcp` and `TCPSocket.new` accept `open_timeout:` and a user timeout raises `IO::TimeoutError` consistently, which simplifies the header-phase `timeout` mapping. Source: https://docs.ruby-lang.org/en/4.0/NEWS_md.html.
- Faraday `on_data` status: not re-checked (UNVERIFIED since the draft).

#### Node / TypeScript (22, 24, 25)
- See change 7. Node 25.0.0 notes: `http.writeHeader` deprecated, `_stream_*` internal modules deprecated, client leak fix when a response is delivered twice (#60062), `--allow-net` permission flag. No change to fetch, web streams or undici mentioned in those notes (HIGH for what the page says; not exhaustive). Node 24 bundles undici 7.x (undici 7.16.0 in 24.11.0, LOW). `Readable.fromWeb` and `AbortSignal.any`: not separately re-verified beyond the globals table (UNVERIFIED for `Readable.fromWeb` stability).

#### Dart (3.10, 3.11)
- Dart 3.11 released 2026-02-11 (MED): Unix domain sockets on Windows in `dart:io`, `dart:js_util` removed in dart2wasm, no language changes. Dart 3.10 contents not read (UNVERIFIED). See change 8 for `package:http`. Sources: https://dart.cn/blog/announcing-dart-3-11 (mirror).

#### Go (1.25, 1.26, 1.27)
- 1.26 (released 2026-02-10, HIGH): `io.ReadAll` about 2x faster with half the allocations (our read-all helper can use it), `errors.AsType` generic, `Transport.NewClientConn` (own connection management), `HTTP2Config.StrictMaxConcurrentRequests`, `ReverseProxy.Director` deprecated, cookie scoping change in `Client`. No `iter` or `Response.Body` change.
- 1.27 (August 2026, HIGH for the page content): auto-draining on `Body.Close()` (change 1); generic methods (a method may now declare type parameters, so `func (c *Client) ListItemsPages` can be generic without a free function; but interface methods cannot), `encoding/json/v2` and `jsontext` with streaming `Encoder`/`Decoder` over token sequences and `UnmarshalRead`/`UnmarshalDecode` (a lighter way to decode JSON Lines items than `bufio.Scanner` plus `Unmarshal`; v2 rejects duplicate names and invalid UTF-8 by default, so it changes error text), `httptest.NewTestServer` plus `synctest.Sleep`.
- 1.25 (August 2025): `testing/synctest` stable (no streaming changes found). The draft's "Go floor 1.25" still holds; range-over-func (1.23) and `iter.Seq2` unchanged. Sources: https://go.dev/doc/go1.26, https://go.dev/doc/go1.27.

#### Rust
- See change 6. Not checked (UNVERIFIED): async closures in this context, hyper 1.x specifics, `futures` crate release notes, `tokio-stream`.

#### Swift (6.x)
- swift-openapi-generator releases in 2026 (HIGH): 1.12.0 (2026-04-27, dropped Swift 6.0), 1.13.0 (2026-07-06, Swift 6 language mode), 1.14.0 (2026-10-06, minimum tools 6.2, QUERY and other HTTP methods, JSON query parameters), 1.14.4 (2026-11-05). No release note mentions streaming, SSE, itemSchema or JSON Lines changes, so our benchmark of SOAR-0004 and SOAR-0010 still stands; OpenAPI 3.2 `itemSchema` support in the Swift generator is UNVERIFIED (OpenAPIKit v6 arrived in 1.12.1 and could carry it).
- `URLSession.AsyncBytes` is still not on Linux: Swift Package Index build logs (LOW, a November 2026 log from a Swift 6.4 build) report `'AsyncBytes' is not a member type of ... FoundationNetworking.URLSession`. This resolves the open "AsyncBytes on Linux" item as still true: the Swift transport on Linux needs `URLSessionDataDelegate` or AsyncHTTPClient. Swift 6.2 and 6.3 release notes and swift-http-api-proposal: not found (UNVERIFIED). Typed throws for `AsyncSequence` (SE-0421 `Failure` primary associated type, Swift 6.0) is from memory (UNVERIFIED here) and means `AsyncThrowingStream<T, SomeError>` can be typed. Source: https://api.github.com/repos/apple/swift-openapi-generator/releases, https://forums.swift.org/t/asyncbytes-and-asynclinesequence-not-available-on-linux/73601.

#### Elixir (1.19, 1.20 RC)
- Elixir 1.19.0 released 2025-10-16 (HIGH for date): stronger type checking incl. protocol dispatch and anonymous functions, faster compile; a non-`Enumerable` `for` generator now warns. 1.20.0 is at RC (search result, LOW). Effect: the paginator struct implementing `Enumerable` and the `Stream.resource` streams are type-checked more strictly, so the structs and `defimpl` must be warning-free under 1.19 and 1.20-rc. Nothing found on `Stream.resource` or `Enumerable` changes. Source: https://elixir-lang.org/blog/2025/10/16/elixir-v1-19-0-released/.
- Req: see change 3. Finch streaming not re-checked (UNVERIFIED).

### Specs and standards

- OpenAPI 3.2.0 (Sep 2025): see change 2. Also: `in: querystring` parameter location, `additionalOperations` for custom methods, the `query` operation, tags `summary`, `parent`, `kind`, `itemEncoding` and `prefixEncoding` for streamed multipart (apply only to `multipart`, mutually exclusive with `encoding`). Pagination: no pagination keyword. The only pagination-shaped text is a collection example in 4.21.2 (Modeling Link Headers) showing `first`, `prev`, `next`, `last` as a linkset in a body or a `Link` header (HIGH from the spec table of contents and fetched text). That supports tier 3 (Link header) but not a spec-level cursor. OpenAPI Overlay for adding pagination parameters: an older roadmap item, status unknown (LOW).
- Arazzo 1.1.0 (2026-05-17, MED): AsyncAPI support, workflow chaining, Selector Object, OpenAPI 3.2 `querystring` alignment; pagination appears only in an example. Not a pagination standard. Source: https://spec.openapis.org/arazzo/latest.html.
- "Moonwalk" and OpenAPI 3.3: nothing found (UNVERIFIED, absence of results only). No standardised `x-pagination`: none found; vendors still use their own (`x-fern-pagination`, `x-speakeasy-pagination`, `x-stainless-pagination-property`, `x-liblab-pagination`, APIMatic `x-pagination`, `x-ms-pageable`, `x-dropshot-pagination`).
- WHATWG HTML SSE section, page dated 2026-11-20 (HIGH): reconnect, `retry` digits only, 204 stops, non-200 or wrong type fails and is not retried, `Last-Event-ID` sent when non-empty, only `withCredentials` is configurable. The one open item is the id value space (issue #7363). No change to the algorithm; our parser rules stay.
- HTTP QUERY: RFC 10008, June 2026 (HIGH). See change 4.
- Resumable uploads for HTTP: still an Internet-Draft, `draft-ietf-httpbis-resumable-upload-15`, updated 2026-11-02, WG state "WG Document", no RFC number (HIGH). So no `Upload-Offset` style resumable upload in the SDKs; revisit when it is an RFC. Source: https://datatracker.ietf.org/doc/draft-ietf-httpbis-resumable-upload/.
- RFC 8288 (Link header): no update found (UNVERIFIED, no search result either way). pagination.md's RFC 8288 parser requirement stands.
- HTTP/3 streaming: reqwest enabled h3 streaming request and response bodies in 0.12.15/0.12.16 behind its unstable h3 flag, and JDK 26 adds HTTP/3 (LOW): transports should not assume HTTP/1.1 chunking, but no API change is needed.

### Vendors

| Vendor | New in 2025-2026 | Does it change our drafts | Source, confidence |
|---|---|---|---|
| Stainless | Joined Anthropic (the pagination and streaming doc pages return an announcement; contents unreachable). PHP generator GA 2026-01-20 with streaming and pagination claimed; CLI generator pagination 2026-01-05 | No new detail to compare; the V1/S26 sources stay SECONDARY | https://www.stainless.com/changelog/products/sdks, LOW |
| Speakeasy | v1.751.1 skips data-less SSE events in Python, TypeScript, Java, Go, C#, Ruby; v1.747.0 URL pagination for C# and Java SSE pattern-matching examples; v1.726.0 cursor input inside a nested request body; v1.731.2 Go URL pagination import fix; SSE now includes Ruby and C# | Cursor in a request body is the case pagination Q9 defers; Speakeasy handles it via explicit `inputs` location. Nothing for inference | https://www.speakeasy.com/changelog/release/v1.751.1, v1.747.0, v1.726.0, MED |
| Fern | CLI 5.21.0 (2026-05-08) `resumable` on `x-fern-streaming`, default false, generators emit a reconnect loop with `Last-Event-ID` and `retry`; SSE inferred from `text/event-stream` without the extension (2026-02-28); `terminator` through the whole pipeline (2026-02-20); SSE comment and line-ending fixes (2026-03-30); pagination `nextUri` and `nextPath` conversion (Feb 2026), TS fix for page property nested in a request body | Confirms: content type is the signal, reconnect opt-in, terminator is a setting. Add the parser test cases (change 5) | https://buildwithfern.com/learn/cli-api-reference/cli-reference/changelog/2026/5/8.md and the generator changelogs, MED (snippets) |
| Kiota | Issue #7435 (SSE for generated clients) open, labelled triage, 2026-04-09; v1.30.0 mentions OpenAPI 3.2 support (secondary). No pagination or `IAsyncEnumerable` news found | None; Kiota remains without SSE | https://api.github.com/repos/microsoft/kiota/issues/7435, MED |
| ogen | v1.21.0 (2026-06-15) SSE client generation for OpenAPI 3.0 (#1689), drains the response body before closing (#1688); v1.23.0 reports unimplemented SSE server encoding at IR time; v1.25.0 on 2026-11-13. No pagination. OpenAPI 3.2 / `itemSchema` not mentioned | The draft already cites the SSE client from the README; the drain-before-close fix is the same hazard as Go 1.27 (change 1) | https://api.github.com/repos/ogen-go/ogen/releases, HIGH |
| Orval | Has an NDJSON streaming guide (typed only with the fetch client); SSE and 3.2 support not found | None | https://orval.dev/guides/stream-ndjson, LOW |
| OpenAPI Generator | Version 7.24.0 stable, master is 7.25.0 per the README; no SSE or client pagination feature found in the results; the open issues in streaming.md V9 not re-checked | Still no client streaming or pagination; keep V9 | https://newreleases.io/project/github/OpenAPITools/openapi-generator/release/v7.24.0, LOW |
| APIMatic | June 2026 product highlights page exists; SSE for TypeScript and .NET (v4 beta) per the earlier read | Not re-read (UNVERIFIED for 2026) | https://apimatic.io/blog/june-2026-product-highlights, LOW |
| liblab, Konfig, Postman | Not re-checked (UNVERIFIED) | None | n/a |
| openapi-to-rust | 0.12.0 lists experimental OpenAPI 3.2 support and a streaming module | A Rust generator that reads `itemSchema`; not evaluated | https://docs.rs/crate/openapi-to-rust/latest/source/CHANGELOG.md, LOW |

Nothing a vendor now does that our drafts lack, except: (a) a `nextUri`/`nextPath` pagination type is in Fern (our tier 1 covers token cursors and tier 3 covers Link headers, but a next-URL-in-body style is not a tier; `x-ms-pageable` and Speakeasy `url` have it too, so consider adding "next URL in body" as a later tier); (b) cursor inside the request body (Speakeasy, Fern TS fix), already deferred in Q9.

### Checked, nothing new (per language and spec)

- java: no change to `ofInputStream` or `ofLines` shape; no JDK 25/26 change to `Flow.Publisher` handlers found; `ofPublisher` still subscribe-once (not re-verified for 25).
- kotlin: no Flow or kotlinx.coroutines change found (kotlinx.coroutines 1.10 release notes not fetched, UNVERIFIED); Ktor reconnect still without documented `Last-Event-ID`.
- csharp: no `IAsyncEnumerable` language or runtime change found; `HttpClient` `ResponseHeadersRead` guidance not re-checked.
- php: no PSR-7 or PSR-18 successor; no generator or stream stdlib change in 8.5; amphp/revolt not found.
- python: no new httpx release since 0.28.1; no asyncio stream API change found; PEP 779 detail unread.
- ruby: Faraday and `net-http` streaming details unchanged as far as read; Enumerator behaviour unchanged except `produce(size:)`.
- node: no change to web streams or `AbortSignal.any`; `EventSource` unchanged (experimental).
- dart: no `dart:io` HTTP change in 3.11; no new `Stream` API.
- go: no `iter` package change in 1.26 or 1.27; `iter.Seq2` and range-over-func unchanged.
- rust: no std async iterator stabilisation; no `futures` change checked.
- swift: no generator change to streaming in 2026; no Linux `AsyncBytes`.
- elixir: no `Stream.resource` or `Enumerable` change found; Finch not checked.
- OpenAPI: no pagination keyword in 3.2; no 3.3 or Moonwalk found.
- IETF: no RFC for resumable uploads; no RFC 8288 revision found.

### Unverified summary

JDK 25/26 project pages and JEP 517 (403), Gatherers and structured concurrency status (memory only), Java `BodySubscribers` early-close behaviour, `Readable.fromWeb` status, Faraday `on_data`, Finch streaming, kotlinx.coroutines 1.10 notes, Swift 6.2/6.3 release notes and typed-throws details, Dart 3.10 contents, whether `System.Net.ServerSentEvents` is in-box per TFM, whether Req 0.8 is released, `x-oai-itemSchema` registry text, whether the Swift generator reads 3.2 `itemSchema`, aiohttp and amphp changes, liblab, APIMatic 2026, Konfig. The Python 3.14/3.15, Node 25, Ruby 4.0 and Go 1.26 pages were read only in part.


## 13. Options and how they map to the twelve languages

This design fixes the carrier per language in its section 4. The benchmark gives these alternatives and verdicts.

| Question | Options seen | Verdict |
|---|---|---|
| Carrier for a byte stream | the language's stream type (Go `ReadCloser`, Java `InputStream`, Rust `Stream<Bytes>`, Swift `AsyncSequence` of chunks, Dart `Stream<List<int>>`, Node `ReadableStream`, PSR-7 `StreamInterface`, Python file-like with context manager, .NET `Stream`, Ruby chunk block, Kotlin `Flow<ByteArray>` or `ByteReadChannel` inside a scope, Elixir `Stream`) | The proposal's table is right and every row is backed: [T12] Rust `bytes_stream` with the `stream` feature, [T14] Java `ofInputStream`, [T17] Ktor scoped channel, [T21] Guzzle `stream`, [T22] urllib3, [T23] Faraday `on_data`, [T24] Dart. |
| Carrier for items (JSON Lines, SSE) | iterator / async iterator (OpenAI, Anthropic, Speakeasy, liblab), `iter.Seq2` (ogen), callback handlers (AWS Java), parser over bytes (Swift, .NET) | A lazy sequence of typed items per language, as the proposal says. Callback-handler and publisher styles (AWS Java) are an adapter for Java, not the base. |
| One type or two for SSE | a typed data stream only (Speakeasy TS iterates events with a typed `data`), or an event envelope with id, event, data, retry (ogen `full`, Fern SSE metadata, APIMatic `withMetadata()`) | Deliver the envelope `ServerSentEvent<T>` as the proposal does. APIMatic and Fern both offer it as an opt-in beside the plain data stream [V6][V4]. |
| Stream versus bytes for binary downloads | stream as the default (Swift, progenitor), buffered bytes as the default with a separate streaming call (Stainless Python and TypeScript) | Settled in 0.2: stream in all twelve. |

Language mapping table (carrier, early stop, close), for quick reference; the signatures are in this design section 4.

| Language | Binary | Items (JSON Lines, SSE) | Early stop |
|---|---|---|---|
| java | `InputStream` subclass | `AutoCloseable` iterator, `asPublisher()` adapter | try-with-resources |
| kotlin | cold `Flow<ByteArray>`, request opened on collection | `Flow<T>` | cancel the collector |
| csharp | `Stream` | `IAsyncEnumerable<T>` | `await using`, `break`, token |
| php | `StreamInterface` | `Generator` | `break`, destroy, `close()` |
| python | context manager, `.read(n)` | `Iterator`, async facade `AsyncIterator` | `with` exit |
| ruby | block with chunks, or an IO-like | `Enumerator` over a producer thread | `break`, `#close` |
| node | `ReadableStream` | `AsyncIterable` | `break` calls `return()`, `AbortSignal` |
| dart | `Stream<List<int>>` | `Stream<T>` | cancel subscription |
| go | `io.ReadCloser` | `iter.Seq2[T, error]` | `break`, `Close()` |
| rust | `Stream<Item=Result<Bytes, Error>>` | `Stream` | drop |
| swift | `AsyncSequence` of chunks | `AsyncThrowingStream` | `break`, task cancellation |
| elixir | `Enumerable` (`Stream.resource`) | `Enumerable` | stop enumeration |


## 14. How streaming is detected from a specification

Benchmark of the signals:

| Generator | Signal | Source |
|---|---|---|
| OpenAPI 3.2 (the standard) | sequential media types (`application/jsonl`, `application/x-ndjson`, `application/json-seq`, `text/event-stream`) "map into the JSON Schema data model as an array in the same order"; `itemSchema` is "applied to each item in the stream independently" (unlike `schema`); for SSE, events follow the HTML specification, `retry` is a number, the other fields are strings, and `contentMediaType` plus `contentSchema` validate JSON in `data`; `itemEncoding` allows streaming multipart | [T29] (the page was read in chunks and summarised by a tool, not copied, so wording is paraphrased) |
| ogen | `text/event-stream` response; the schema is the `data` payload by default (`x-ogen-sse-event-shape: data-only`), or the full envelope (`full`, `full-array`); there is "no official standard" before 3.2 | [T8] |
| Swift runtime | content types `application/jsonl`, `application/x-ndjson`, `application/json-seq`, `text/event-stream`; the generator still vends the raw byte sequence and the caller chains `asDecoded...` | [T11] |
| Speakeasy | `text/event-stream` with the event schema; JSON and stream on one path need a URL fragment (`/chat#streamed`) or `inferSSEOverload` (TypeScript and Python only); `application/jsonl` or `application/x-ndjson`; `x-speakeasy-sse-sentinel` for a terminator | [V3] [V2] |
| Fern | `text/event-stream` auto-detected; `x-fern-streaming` with `format: sse`, `terminator`, `resumable`, `stream-condition: $request.stream` | [V4] |
| liblab | `text/event-stream` or `x-liblab-streaming: true` (for JSON that is sent incrementally) | [V5] |
| APIMatic | success response with `text/event-stream`; `x-sse-sentinel` | [V6] |
| progenitor | `application/octet-stream` becomes `ByteStream`; `x-dropshot-websocket` for upgrades | [T9] |
| openapi-generator (stock) | no SSE or streaming support: open requests #6123 (Java SSE), #17442 (TypeScript fetch event streams), #6750 (dart-dio stream response type), #21967 (rust-axum) | [V9] |

Reading: the content type is the signal everywhere, and the two things that need a declaration beyond it are the terminator sentinel (three vendors) and an operation that returns either JSON or a stream depending on a request parameter (Speakeasy fragments and `inferSSEOverload`, Fern `stream-condition`). This design's section 3 uses the content type only, with no vendor extension, which matches the repository rule.

Two points the benchmark raised, both settled in this design:

- OpenAPI 3.2 `itemSchema` versus the 3.0 and 3.1 `schema`. In 3.2, `itemSchema` describes each item; for SSE it describes the event object (`data`, `event`, `id`, `retry`) with `data` a string that may carry `contentSchema`. Today, `schema` on a sequential type is read as the item schema by ogen's default and by the Swift proposal's example. This design reads `schema` as the schema of `data`. These agree for 3.0 and 3.1 specs and diverge once 3.2 loads (the pinned swagger-parser rejects 3.2, issue #50). Rule, written down in 4.1.1 (3.0 and 3.1: `schema` is the item for JSON Lines and the `data` payload for SSE; 3.2: `itemSchema` is the item, and for SSE the event envelope with `contentSchema` on `data`), and the 3.2 reading is added now, with the parser question left to #50).
- The sentinel (`[DONE]`) is needed to support OpenAI-style APIs and is spec-level in three vendors. A generic generator should not hard-code it: the terminator is a generator option, default none (0.5).


## 15. Resolving this design's "verify in stage 0" list

| Item in this design | Finding | Source |
|---|---|---|
| Ruby: Faraday `on_data` signature and adapter support | Confirmed: `on_data` receives `(chunk, overall_received_bytes, env)`, only some adapters support it, `env` may be missing on older ones; the `net_http` adapter has a `stream_response` branch. The fallback to `Net::HTTP#read_body` stays a precaution for adapters other than `net_http`. | [T23] |
| Dart: response stream contract | Confirmed that `StreamedResponse.stream` is a `ByteStream` and `contentLength` may be null. Not confirmed: the "never-listened stream keeps the socket" claim. | [T24] |
| Kotlin: Ktor SSE plugin reconnect | The plugin has `maxReconnectionAttempts` (reconnect when above 0) and `reconnectionTime`; the page does not mention `Last-Event-ID`. This design's choice to ship its own parser keeps the id behaviour under our control; using the plugin is possible only if a test shows it sends the header. | [T18] |
| Elixir: `:retry` with `:into` | Not resolved. The Req docs fetched say decoding and decompression are off when `:into` streams; nothing about retry. The SDK must therefore decompress by hand for streaming calls (already planned in the proposal's section 10) and disable Req's retry for them (also planned). | [T25] |
| PHP: PSR-18 laziness | Guzzle supports `stream: true` but "must be implemented by the HTTP handler", which supports the proposal's "own transport method" and "degrade with a warning" plan. | [T21] |
| Python: chunked request bodies | Not resolved: the urllib3 page fetched does not cover request-body streaming. | [T22] |
| Swift: `AsyncBytes` on Linux | Not resolved. The runtime's `HTTPBody` abstraction does not depend on it, but the transport does. | [T10] |
| Rust: `stream` feature | Confirmed that `bytes_stream` needs the optional `stream` feature. `Body::wrap_stream` for uploads was not on the page fetched. | [T12] |
| Java: `ofInputStream` close behaviour | The `BodyHandlers` page confirms streaming; the close and cancel behaviour is in `BodySubscribers`, not fetched. | [T14] |


## 16. Vendor generators

Added at the owner's request.

### 16.1 Comparison table

| Vendor | How the generator learns an operation streams | Shape per language | Reconnect and sentinel | Errors and early close | Source |
|---|---|---|---|---|---|
| Stainless | The OpenAI and Anthropic SDKs are Stainless-generated; the streaming switch is the request parameter `stream` (an overload returns a `Stream` / `AsyncStream`); the spec-level mechanism was not found in docs | Python `Stream[T]` / `AsyncStream[T]` (iterable, context manager, `close()`); TypeScript async iterable with `break` or `controller.abort()`; raw body via `.with_streaming_response` (Python, context manager) and `.asResponse()` (TS) | no reconnect; `[DONE]` is hard-coded in the OpenAI package; a `ServerSentEvent` has `event`, `data`, `id`, `retry` | `finally` closes the response; an `error` payload raises `APIError`; `APITimeoutError` and `APIConnectionError` | [T1] [T2] [T4] [T6] |
| Speakeasy | `text/event-stream` response with an event schema (`id`, `event`, `data`, `retry` only; other fields fail generation); `application/jsonl` or `application/x-ndjson`; URL fragment or `inferSSEOverload` for a mixed endpoint; `x-speakeasy-sse-sentinel` | SSE: TypeScript `for await` with typed `data`, Python iterator, Go, Java, C# listed as supported (Go, Java and C# code not shown on the page); the changelog adds PHP and Ruby. JSONL: TypeScript async iterable, Python in a `with` block, Go `Next()` and `Value()`, Java `JsonLStream<T>` | no reconnect or `Last-Event-ID` documented; sentinel hidden from the application and ends the loop | Java JSONL stream "must be closed after use"; Python `with`; errors "not covered" | [V2] [V3] |
| Fern | `text/event-stream` auto-detected; `x-fern-streaming` (`format: sse` or JSON, `terminator`, `resumable`, `stream-condition`); SSE SDK generation requires the Enterprise plan | SSE exposes event metadata (id, event, retry); JSON streaming is an async iterable splitting on newlines | `resumable: true` enables automatic reconnection and resends `Last-Event-ID`; pair it with `terminator` so only dropped connections reconnect; default off | not documented on the page | [V4] |
| Kiota | not covered by my research | not covered | not covered | not covered | none |
| liblab | `text/event-stream` or `x-liblab-streaming: true` | TypeScript `AsyncGenerator<HttpResponse<T>>`; Python `Generator`; Java `Stream<T>` plus `CompletableFuture<Stream<T>>`; Go `Stream[T]` with `Next()`; PHP `\Generator`; C# `IAsyncEnumerable<T>` with a `CancellationToken` | none documented | Go: `io.EOF` is normal completion and the stream closes after any error; C#: malformed JSON lines are skipped, cancellation throws `OperationCanceledException`; Python raises `ApiError` after retries (default 4) | [V5] |
| APIMatic | success response with `text/event-stream`; `x-sse-sentinel` | TypeScript `SseStream<T>` (single-use `AsyncIterable`, `withMetadata()` gives `SseEvent<T>` with `data`, `id`, `event`, `retry`); .NET (v4 beta) `Task<IAsyncEnumerable<T>>` with a throwing variant and an `AsResult` variant | no reconnect documented; `id` and `retry` are exposed | `close()` or `break` aborts the request; `streamReadTimeout` 60 s default (TypeScript) and `StreamReadTimeout` 60 s (.NET) fail an idle stream; a timeout error and a decode error carrying the raw frame | [V6] |
| Konfig | marketing page lists "streaming"; nothing else found | not verified | not verified | not verified | [V7] (FAILED for the docs site) |
| Postman SDK Generator | deprecated; docs point to Fern; the page mentions WebSocket SDKs from AsyncAPI only | n/a | n/a | n/a | [V8] |
| openapi-generator (stock) | none; open requests only (#6123, #17442, #6750, #21967) | none | none | none | [V9] |

### 16.2 What the vendors change in the recommendation

1. The content type is enough; no vendor needs a flag to turn streaming on (except Fern for SSE and liblab for JSON sent incrementally). This supports this design's spec-driven signals, and weakens the case for the opt-in `streaming` option as a design goal. The owner decided against a rollout flag (0.5).
2. Sentinels are everywhere (Speakeasy, Fern, APIMatic) beside the in-band error events seen at Anthropic, so the generator needs an SSE terminator setting, and the error-event policy must be written down (0.5).
3. Reconnect: only Fern ships `Last-Event-ID` resume, and as an opt-in flag; no other vendor documents reconnect. This argues for the "off by default for requests with a body" change in section 6.
4. Metadata: three vendors expose the SSE envelope (id, event, retry) next to the typed data (Fern, APIMatic `withMetadata()`, Stainless `ServerSentEvent`). The proposal's `ServerSentEvent<T>` is aligned.
5. Early close: Speakeasy's Java docs say the stream "must be closed after use"; liblab Go closes after any error; APIMatic aborts on `break`. The proposal's rule (close is idempotent, `break` or scope exit closes, an error closes) is the intersection and is correct.
6. Coverage: vendors do not cover all languages (Speakeasy JSONL: TypeScript, Python, Go and Java only; liblab pagination: three language families), which again is where a twelve-language parity rule differs.
7. Lenient parsing (liblab C# skipping malformed lines) conflicts with this repository's fail-loudly principle; not adopted.

### 16.3 Vendor sources

| Tag | URL | Status |
|---|---|---|
| V1 | https://www.stainless.com/docs/sdks/configure/pagination/ | SECONDARY, not used for streaming |
| V2 | https://www.speakeasy.com/docs/sdks/customize/runtime/jsonl-events | OK |
| V3 | https://www.speakeasy.com/docs/sdks/customize/runtime/server-sent-events.md | OK; Go, Java and C# code not on the page; a changelog statement that C# joins TypeScript, Python, Go, Java, PHP and Ruby came from a search-result summary |
| V4 | https://buildwithfern.com/learn/api-definition/openapi/endpoints/sse | OK |
| V5 | https://liblab.com/docs/concepts/streaming | OK |
| V6 | https://docs.apimatic.io/generate-sdks/sdk-features/server-sent-events-streaming/ | OK |
| V7 | https://docs.konfigthis.com and a search | FAILED for the docs site (TLS error) |
| V8 | https://learning.postman.com/docs/sdk-generator/overview | OK |
| V9 | `gh search issues` on OpenAPITools/openapi-generator for "server-sent events" and "text/event-stream" | OK: issues #6123, #17442, #6750, #21967, #17271 (all open) |


## 17. Sources

Status words: OK, PARTIAL (fetched, relevant part thin or truncated), SECONDARY (search-result summary), FAILED.

| Tag | URL | Status |
|---|---|---|
| T1 | https://github.com/openai/openai-python/blob/main/src/openai/_streaming.py | OK (Stainless-generated source, read through the GitHub API) |
| T2 | same file, lines for `[DONE]`, `APIError`, `finally: response.close()` | OK |
| T3 | https://raw.githubusercontent.com/openai/openai-node/master/README.md | OK; the stream-cancel text the owner may expect (`controller.abort`) is not in this README, it is in the Anthropic page [T6] |
| T4 | https://platform.claude.com/docs/en/cli-sdks-libraries/sdks/python | OK |
| T5 | same page, "Streaming response body" | OK |
| T6 | https://platform.claude.com/docs/en/cli-sdks-libraries/sdks/typescript | OK |
| T7 | https://platform.claude.com/docs/en/build-with-claude/streaming | OK (read from a saved copy: ping events, error events, error recovery) |
| T8 | https://raw.githubusercontent.com/ogen-go/ogen/main/README.md (section "SSE" and "Streaming JSON encoding") | OK |
| T9 | https://github.com/oxidecomputer/progenitor/blob/main/progenitor-impl/src/method.rs | OK (source) |
| T10 | https://github.com/apple/swift-openapi-generator/blob/main/Sources/swift-openapi-generator/Documentation.docc/Proposals/SOAR-0004.md and https://github.com/apple/swift-openapi-runtime/blob/main/Sources/OpenAPIRuntime/Interface/HTTPBody.swift | OK |
| T11 | https://github.com/apple/swift-openapi-generator/blob/main/Sources/swift-openapi-generator/Documentation.docc/Proposals/SOAR-0010.md | OK; the forum thread https://forums.swift.org/t/proposal-soar-0010-support-for-json-lines-json-sequence-and-server-sent-events/69098 gave the status and "Transport providers don't need to add any support" |
| T12 | https://docs.rs/reqwest/latest/reqwest/struct.Response.html | OK; `Body::wrap_stream` not on the page |
| T13 | https://raw.githubusercontent.com/jpopesculian/reqwest-eventsource/master/README.md | OK |
| T14 | https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpResponse.BodyHandlers.html | PARTIAL |
| T15 | https://pkg.go.dev/net/http#Response | PARTIAL (the page was truncated before `Response`; quotes come from `Client.Get`, `Client.Do` and `Client.Timeout`) |
| T16 | https://learn.microsoft.com/en-us/dotnet/api/system.net.serversentevents.sseparser-1 | OK |
| T17 | https://ktor.io/docs/client-responses.html | OK |
| T18 | https://ktor.io/docs/client-server-sent-events.html | OK |
| T19 | https://raw.githubusercontent.com/florimondmanca/httpx-sse/master/README.md | OK |
| T20 | https://raw.githubusercontent.com/tmaxmax/go-sse/master/README.md | OK |
| T21 | https://docs.guzzlephp.org/en/stable/request-options.html | OK |
| T22 | https://urllib3.readthedocs.io/en/stable/user-guide.html | PARTIAL |
| T23 | https://github.com/lostisland/faraday/blob/main/docs/advanced/streaming-responses.md and https://github.com/lostisland/faraday-net_http/blob/main/lib/faraday/adapter/net_http.rb | OK |
| T24 | https://pub.dev/documentation/http/latest/http/StreamedResponse-class.html | PARTIAL |
| T25 | https://req.hexdocs.pm/Req.Steps.html | PARTIAL (the `:into` option text itself was not on the page) |
| T26 | https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/java_transcribe_code_examples.html | OK |
| T27 | https://docs.rs/aws-smithy-http/latest/aws_smithy_http/event_stream/struct.Receiver.html | OK; the AWS Rust developer guide page for event streams returned an empty page |
| T28 | https://html.spec.whatwg.org/multipage/server-sent-events.html | OK |
| T29 | https://spec.openapis.org/oas/v3.2.0.html | PARTIAL: read in 100,000-character chunks and summarised by the fetch tool; the third chunk (after character 200,000) was not read; wording is paraphrased |

Not verified at all: Kiota streaming, Stainless's spec-level streaming configuration, the OkHttp SSE listener contract (the interface file fetched did not define it), how the AWS Go v2 SDK exposes event streams, and the behaviour of chasm for dropping SSE feeds.

