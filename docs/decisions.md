# Decisions

This page lists the choices that are settled for all twelve SDKs, written for people who use the generated clients. Each one says what the SDK does and where to read the details for your language. The language pages are in [docs/languages](languages), and the type table is in [types.md](types.md). Work that is still open is listed at the end, with a link to its issue.

## Retries are on by default

Every client retries a failed call up to two times unless you change it. A call is retried after a network error that happened before any response arrived, and after a response with status 408, 409, 429 or any 5xx except 501. The end-to-end timeout covers all attempts together, so a wait that no longer fits in it ends the retrying. The wait starts at half a second and doubles up to eight seconds, with up to a quarter taken off at random, and a `Retry-After` header from the server is used when it is present. Set the retry count to `0` on the transport options to turn retries off, or change it for a single call with a per-call option (below).

A client that you build around your own HTTP client keeps the retry rules of that client in some languages: the Go page says that such a client never retries, for example. See the Retries or Automatic retries section of your language page: [Java](languages/java.md), [Kotlin](languages/kotlin.md), [C#](languages/csharp.md), [Node.js](languages/node.md), [Python](languages/python.md), [Ruby](languages/ruby.md), [PHP](languages/php.md), [Go](languages/go.md), [Rust](languages/rust.md), [Swift](languages/swift.md), [Dart](languages/dart.md), [Elixir](languages/elixir.md).

## Models are immutable

A model is never changed after it is built. To change one, you make a copy with the new value and leave the original as it was. The way to copy follows the language: a builder in Java (`toBuilder()`), a `with` expression on a record in C#, `copyWith` in Dart, `copy(...)` in Swift, `with` in Node.js and Ruby, `With<Property>` methods in Go and PHP (`withX()`), `model_copy` in Python, `with_<field>` methods in Rust, and the struct update syntax in Elixir. Kotlin models are data classes with read-only properties. Copies are shallow: a list or map inside the copy is the same one the original holds, so clone it before you change it in place.

## Enum member names and descriptions

An author can name the members of an enum with `x-enum-varnames`, `x-enumNames` or `x-enumnames` (an array by position in the `enum` list, or an object keyed by wire value) and describe them with `x-enum-descriptions`, `x-enumDescriptions` or `x-enumdescriptions`. If more than one spelling of a key is present the first in the order written here wins and generation logs a warning. A supplied name is treated as a source word: it is cleaned and cased exactly like a name derived from the wire value (so `on hold` is `ON_HOLD`, `OnHold`, `onHold` or `on_hold` by language), and two names that collide after casing get a numbered suffix. The wire value never changes, and only it is written and accepted on the wire; decoding a member name fails like any unknown value. A member with a description carries it as the one-line doc comment directly above it; a member without one keeps the comment it has today, so a spec without these keys generates identical code. A default on an enum property names the member that has the supplied name. A specification that already carried one of these keys may see members renamed (for example `LOW` becoming `Low` in a language that does not keep the author's casing).

## Unknown enum values fail loudly

When a response carries an enum value, or a discriminator value, that the SDK does not know, decoding fails with the SDK's serialization error and the message names the value. The SDK never maps an unknown value to a default and never passes it through silently. The reason is that a specification that is out of date should fail where you can see it, not produce a wrong value later. Read the section on unknown values in your language page to see the exact error type.

## Repeated response headers are joined

A response header that the server sends on several lines, such as `Link` or `Set-Cookie`, arrives as one value in which the lines are joined with `, ` in the order received. Header names are lower-cased. The headers are available from the `...WithHttpInfo` variant of each operation, which returns the status, the headers and the decoded body together.

## Streaming is not supported

The SDKs read a response body in full before decoding it, and send a request body whole. There is no API for server-sent events, chunked downloads or streaming uploads. When you need to stream, every client accepts your own HTTP client or transport and sends its requests through it: `java.net.http.HttpClient` in Java, a `urllib3.PoolManager` in Python, a `Faraday::Connection` in Ruby, an `ApiClient` or `http.RoundTripper` in Go, and the standard client object of each other language. The SDK keeps its defaults on top of the client you supply and never closes it. Streaming support is decided and not built yet: decisions D118 to D122 below give the shape (binary responses become streams, JSON Lines and server-sent events get a `Stream` method), and issue #114 stays open until it ships.

## Options for a single call

Every operation accepts three settings that apply to that one call and replace the client-wide value: a timeout in milliseconds, extra headers (which win over a default header of the same name) and a retry count. Where a language has a native way to cancel a call, it is used instead of a shared wrapper: a `context.Context` in Go, a `CancellationToken` in C#, an `AbortSignal` in the Node.js per-call options, coroutine cancellation in Kotlin, thread interruption in Java, and so on. Cancelling is never reported as a timeout.

## Several success responses decode by status

When an operation declares more than one 2xx response with different schemas, it returns a union of those schemas, and the body is decoded as the schema of the status that actually arrived, never by guessing from the shape of the body. Each language page shows how to ask the union which variant it holds.

## Unions are built with typed constructors where the language allows it

A `oneOf` or `anyOf` model is built from a variant, not from an untyped value, and read back through a per-variant test and accessor, wherever the language allows: `ofVariant`, `isVariant` and `asVariant` in Java, `FromVariant` in C#, `fromVariant` in Kotlin, Swift and PHP, `from_<variant>` in Ruby, a constructor per variant such as `NewDryFoodPetFood` in Go, a named constructor per variant in Dart, a sum type in Rust, a union type in TypeScript and module functions in Elixir. In Python the union is a pydantic wrapper that takes the variant as its one argument and rejects anything that is not a declared variant, so you read it with `isinstance` on `actual_instance`. A model that uses `allOf` with a discriminator is a different shape and is still open (issue #104).

## Credentials come from an argument, then the environment

An authenticator takes its credential as an argument. When you pass none, most SDKs read an environment variable once, when the client or authenticator is built, and an empty value counts as unset. An argument always wins over the environment. A credential that is found in neither place fails at construction time, not on the first request. A bearer token or an API key that rotates can instead come from a provider function that is called for every request. The variable names differ by language and by generated package, so they are listed on each language page. The OAuth 2 and OpenID Connect authenticators of Dart and PHP do not read the environment yet; that is issue #119.

## Errors say which request failed

An API error carries the HTTP method and the path of the request, without the query string and the fragment because a query may hold a credential, and the message shows them: `API returned status code 404 for GET /items/7`. The same fields exist on a network error and on a timeout, so a log line from a failed call tells you which call it was.

## Closing a client finishes what is running

Closing a client is safe to do twice. A request that is already in flight finishes, a request that starts afterwards fails with the error for a closed client, and an HTTP client that you supplied is never closed by the SDK. To stop one request, cancel that request (see the options for a single call above), not the client.

## Every API group and the client can be replaced in your tests

Every API group has an interface (an interface, protocol, trait or behaviour, whichever the language has) listing the operations of the group, and the client has one where the language can state it, so a test can replace the SDK with a hand-written fake: no mocking library, no network. The interfaces sit next to the existing classes and change nothing about them. The forms of an operation that leave out the optional parameters are default methods or extensions, so a fake writes one form per operation. A new operation in the API adds a member to the interface, and a fake that implements the whole interface stops compiling after you regenerate; the README of each SDK says how to write a fake that does not. Go has no client interface, because a Go interface cannot hold fields, so its `Client` fields are typed as the group interfaces and a struct literal of fakes closes cleanly; Elixir calls modules, so its fake is a module that implements the group behaviour.

## Configuration and transport options are separate

`Configuration` says what the API is (the base URL, the default headers and the server). `TransportOptions` says how bytes travel (proxy, TLS, timeout, retries and redirects). They overlap a little on purpose, because merging them would be a larger break than the overlap costs. The README of each SDK says which setting goes where.

## Optional and nullable are not told apart on the way out

A property that is optional and nullable is the language's own optional type, and an absent value and an explicit `null` are sent the same way, as absent, in every language except TypeScript. A property that is required and nullable is always written, as `null` when it has no value. A three-state type (absent, `null`, value) for PATCH-style APIs stays declined for now (decision [D112](#d112-the-three-state-optional-stays-declined)).

A query parameter that is required and nullable is kept the same way: when its value is `null` the key is still sent, with an empty value (`?note=`), never dropped, in every language. Its Options field is the language's nullable type with no default, so the caller must still state it. An empty string for a required nullable string parameter is still refused, and a required parameter that is not nullable still refuses `null`. Header, cookie, form, `content` and `deepObject` parameters keep their current behaviour.

## D110: Rust does not target the browser

**Context.** The Rust SDK is built on `reqwest` with `rustls-tls-native-roots`, Tokio, and native compression crates. The progenitor comparison ([#134](https://github.com/mridang/openapi-generator-plus/issues/134)) asked whether the crate should also build for `wasm32-unknown-unknown`. Go builds for `wasip1` (a different target, covered by `GoWasmSpec`) and Dart has a web transport; Rust had no stated position.

**Options considered.** (1) Support the browser target: it needs `cfg` branches for the transport, the TLS stack, the timers and the proxy and redirect options (none of which exist in a browser `fetch`), and a separate test harness. (2) Declare it unsupported and say so. (3) Say nothing.

**What was compared.** The progenitor comparison (#134) and `docs/comparisons/ogen.md` do not show a peer that promises browser support for a native-HTTP client. No browser build was benchmarked, because every transport option the SDK promises (proxy, CA, redirects, retries per socket) has no browser equivalent.

**Decision.** The `wasm32-unknown-unknown` browser target is not supported (owner decision of 2026-11-20). No code changes.

**Consequences.** Documented in [the Rust page](languages/rust.md#known-limits), the Rust `AGENTS.md` and the generated Rust README. Nobody adds `cfg` branches for the browser. A browser user calls the API through a server-side process.

## D111: Each SDK keeps the call style of its language

**Context.** ogen and progenitor each expose one style (ogen: blocking calls with a `context`; progenitor: `async`). Our twelve SDKs follow their language today, and the question was whether to add the other variant.

**Options considered.** (1) Offer a blocking and an async variant of every operation in every language. (2) Offer both only in languages with a split ecosystem (Rust, Python). (3) Keep one style per language, the style its developers expect.

**What was compared.** `docs/comparisons/ogen.md` (Go, blocking with `context.Context`), progenitor (Rust, async over `reqwest`) and swift-openapi-generator (`async throws` only). Doubling the operations would double the public surface of every SDK and every test suite, and the parity rules require the same shape in all twelve.

**Decision.** There are no sync and async variants. Node.js, Python, Dart, Rust, Swift, Kotlin and C# are asynchronous only; Java, PHP, Ruby, Go and Elixir are blocking only (owner decision of 2026-11-20). No code changes.

**Consequences.** Each language page lists its style under known limits. A caller who wants the other style wraps the call in its language's own machinery (a thread, a task or a runtime `block_on`).

## D112: The three-state optional stays declined

**Context.** ogen models optional and nullable values as `OptT`, `NilT` and `OptNilT` with `Set` and `Null` flags, so a PATCH-style call can send an absent key, an explicit `null` or a value ([docs/comparisons/ogen.md](comparisons/ogen.md), row "Optional and nullable"). Our SDKs use the language's own optional type and send absent and `null` the same way (F834).

**Options considered.** (1) A generator-owned wrapper type in all twelve languages. (2) Native three-state types where a language has one (TypeScript `undefined` versus `null`) and a wrapper elsewhere. (3) Keep the native optional and decline.

**What was compared.** ogen's `OptNil` types in Go, and the reference SDKs named in the root `AGENTS.md`, which keep the native optional. A wrapper changes every model signature in all twelve SDKs, and the shape must be identical across languages; no design was found that is idiomatic in each.

**Decision.** The three-state optional stays declined for now (owner decision of 2026-11-20). An optional and nullable property keeps the language's own optional type, and absent and `null` encode the same. A required nullable property is still written as `null`.

**Consequences.** A PATCH API that needs to clear a field with `null` cannot do so through a model; it needs a raw request. Revisit only with one design that works in all twelve languages. Documented in every language page's known limits.

## D113: An integer with no format is 64-bit

**Context.** An unformatted `type: integer` was 32-bit in all twelve SDKs (D54), so a server value above 2,147,483,647, such as a large identifier, failed to decode. The ogen comparison ([docs/comparisons/ogen.md](comparisons/ogen.md), row "Integer without format") found ogen maps it to `int`, which is 64-bit on the platforms that matter, and the progenitor comparison ([#134](https://github.com/mridang/openapi-generator-plus/issues/134)) found the same question. OpenAPI treats an integer without a format as unbounded.

**Options considered.** (1) Keep 32-bit and tell users to declare `int64`. (2) Widen it only where the language's native integer is already 64-bit. (3) Widen it everywhere.

**What was compared.** Option 2 leaves Java, Kotlin, C#, Go, Rust and Swift with a different type from Python, Ruby, PHP, Dart, Elixir and Node for the same schema, which the parity rules forbid. Option 3 changes the public type of every such field in six SDKs (`Integer` to `Long`, `int32` to `int64`, `i32` to `i64`, `Int` to `Int64` and so on), but the other six already hold 64-bit values.

**Decision.** An unformatted `integer` is 64-bit in all twelve SDKs (owner decision of 2026-11-20, supersedes D54 for the unformatted case). The generator writes `format: int64` on the schema before generation, so the range check and the error for a value outside the range are the ones `int64` already has. `format: int32` stays 32-bit.

**Consequences.** This is a breaking change to the type of every unformatted integer field, parameter and response in Java, Kotlin, C#, Go, Rust and Swift. A test in every language decodes a value above 2^31 (serialization131). In Node a value above 2^53 loses precision, as for `int64`. [docs/types.md](types.md) and each language page state the rule.

## D114: Go operations take the context as their first argument

**Context.** Issue [#146](https://github.com/mridang/openapi-generator-plus/issues/146) asked whether Go operations should take the cancellation context as the first argument, as Go code and ogen do, instead of inside the per-call options. [docs/comparisons/ogen.md](comparisons/ogen.md) lists the difference between the two clients.

**Options considered.** (1) Keep the context inside the options and default to a background context. (2) Take `ctx context.Context` as the first parameter of every operation.

**What was compared.** The reference clients named in the root `AGENTS.md` (the AWS SDK for Go v2, client-go, Stainless Go SDKs) and ogen all take the context first. Settled decision S7 (cancellation) already chose option 2 for the Go SDK, and the generated operations and every Go test already have the shape `api.AddPet(ctx, pet, options, callOptions...)`; the issue text described an older shape.

**Decision.** Go operations take `ctx` first, before the path parameters, the body and the options. No code changes were needed; the issue is answered by S7 and this record.

**Consequences.** A caller that has no context passes `context.Background()`. The trace context comes from the same argument (D101).

## D115: Header parameters named Accept, Content-Type or Authorization are ignored

**Context.** The OpenAPI specification says a header parameter named `Accept`, `Content-Type` or `Authorization` shall be ignored, because those headers are defined by the media-type and security objects. The SDKs turned such a parameter into an option that overrode the negotiated header ([#125](https://github.com/mridang/openapi-generator-plus/issues/125)).

**Options considered.** (1) Keep the override. (2) Ignore the parameter in every generator and let the SDK own the three headers. (3) Fail generation on such a parameter.

**What was compared.** ogen and the other generators that follow the specification drop these parameters; failing generation would block specs that exist in the wild.

**Decision.** `IgnoreReservedHeadersRule` removes a header parameter with one of the three names, in any case, from every operation and path item, whether it is written inline or by `$ref`, and the generation log warns about each one. The SDK negotiates `Accept` and `Content-Type` and the authenticators write `Authorization`. A caller who must send a different value still passes a per-call header.

**Consequences.** The Options type of an operation no longer has such a field, a breaking change for a caller that set one. The fixture operation `getPetByNote` declares the three parameters, so the petstore tests in all twelve languages prove they are gone: its generated signature and tests are unchanged.

## D116: A security requirement that names several schemes sends all of their credentials

**Context.** An operation's `security` is an OR of requirement objects, and each object is an AND of schemes: `security: [{bearer: [], apiKey: []}]` means one request must carry both credentials. Only the Rust, Dart, Go and PHP SDKs sent both; the other eight used the first scheme they held and sent a single credential (fixture operation `getAuditTrail`, catalogue id misc074). [docs/comparisons/ogen-source-review.md](comparisons/ogen-source-review.md) lists the question as a user need.

**Options considered.** (1) Keep one credential per operation and document it. (2) Send every credential of the requirement, with the same selection rule everywhere.

**What was compared.** The four SDKs that already did it share one rule, and the other eight had only a missing feature, no language reason: each has a per-scheme credential table and a composite is a small class over the language's own authenticator interface.

**Decision.** Every SDK picks the first requirement whose every scheme the client holds and sends the headers, query parameters and cookies of all of them; when no requirement is complete it uses the first one the client holds in part; when none matches it falls back to the client-level authenticator. A composite that includes a token authenticator hands the transport to it and drops its cached token on a 401, as a single token authenticator does. The shape is the same in each language: a requirement-aware resolver next to the existing scheme resolver (`resolveRequirementAuth`, `requirementAuthenticator`, `requirement_authenticator`, `requirement_auth`) and a composite authenticator class.

**Consequences.** An operation whose requirement names two schemes now sends both credentials in all twelve languages, with a tagged test per language (misc074). The exceptions for misc074 are removed.

## D118: Streaming goes through one transport method that returns an unread body

**Context.** The transport contract of all twelve SDKs (`sendRequest` returning a response whose body is a string) reads every response in full before it returns, so it cannot carry a server-sent event feed, a JSON Lines export or a large download, and the finding F837 deferred streaming for that reason ([#114](https://github.com/mridang/openapi-generator-plus/issues/114)). The injected transport is the only escape hatch and it makes every user rebuild signing, error mapping and decoding.

**Options considered.** (1) Keep the buffered contract and document the injected transport as the only way to stream. (2) Replace the buffered method with a streaming one everywhere. (3) Add one method next to the buffered one that returns the response with its body not read yet, with a default implementation that throws. (4) A callback or reactive style (the AWS Java event stream handlers).

**What was compared.** The Swift OpenAPI runtime makes every body an `HTTPBody` (an `AsyncSequence` of chunks) and says transports need no extra support, and progenitor makes the stream the only form: option 2 would break every custom transport in twelve languages. Guzzle says streaming "must be implemented by the HTTP handler used by a client" and PSR-18 cannot express it, so a transport has to opt in. The AWS Java handlers are a reactive adapter, not a base. The full table of about two dozen SDKs and generators, with sources, is in [docs/design/streaming.md](design/streaming.md).

**Decision.** The transport gets one new method, `sendStreaming` in the language's casing, that returns the status, the headers and a body that has not been read. Its default implementation throws the language's built-in unsupported error with a message that names the transport, so a transport someone injected keeps working for buffered calls and a streaming call on it fails clearly instead of falling back to buffering. A language whose interfaces cannot carry a default method (Go, PHP) reaches the same behaviour with a second interface that the SDK checks at call time. The buffered `sendRequest` is unchanged.

**Consequences.** Retries before the first body byte, redirects, authentication refresh, redaction and the error contract run in the SDK on top of the new method, so a caller keeps them. A transport that does not implement the method fails binary operations too (D119). Supersedes the part of F837 that deferred the transport contract. Nothing is built yet.

## D119: Binary responses are streams in all twelve SDKs

**Context.** Today the shape of a binary response differs: java, csharp and ruby return an object that looks like a stream but is memory-backed, and the other nine return bytes in memory. Either way the whole body is read before the caller sees it, so a large download can exhaust the memory of the caller's process ([#114](https://github.com/mridang/openapi-generator-plus/issues/114), findings F837 and F332).

**Options considered.** (1) Bytes in the base method and a stream only in a separate method. (2) A stream in the base method in all twelve, with a helper to read it all. (3) Leave the three different shapes.

**What was compared.** Stainless-generated OpenAI and Anthropic SDKs return a buffered result and offer a separate streaming call; the Swift runtime and progenitor make the stream the only form; Go's `net/http` hands out a `Body` the caller must close. The earlier proposals recommended option 1 because it matches the Stainless pattern and breaks three languages instead of nine. The owner weighed that against memory safety: with option 1 the easy call is the unsafe one.

**Decision.** Option 2. An operation whose success response is binary (`application/octet-stream`, an image, `type: string, format: binary`, any binary media type) returns a byte-stream carrier (`ByteStream`) in every language. The carrier has the status code, the lower-cased headers, an idempotent `close()` and a read-all helper for the caller who wants the bytes in memory. There is no second `Stream` method for a binary operation. JSON and text responses stay buffered. The carrier per language is in [docs/design/streaming.md](design/streaming.md) section 6.1.

**Consequences.** A breaking change for all twelve, which is allowed before the first release: java, csharp and ruby change the type they return and the other nine change from bytes to a stream. A caller that wants the old behaviour calls the read-all helper. A transport that does not implement the streaming method (D118) fails binary operations with the clear unsupported error. This replaces the recommendation of the earlier drafts (bytes in the base method). Nothing is built yet.

## D120: JSON Lines and server-sent events get a `<op>Stream` method that returns a typed sequence of items

**Context.** Specifications that describe an endless or very long response (`text/event-stream`, `application/x-ndjson`, `application/jsonl`) are generated as an operation that reads the whole body, which never ends for an event feed ([#114](https://github.com/mridang/openapi-generator-plus/issues/114), [#71](https://github.com/mridang/openapi-generator-plus/issues/71), [#72](https://github.com/mridang/openapi-generator-plus/issues/72)).

**Options considered.** (1) A generator option or a vendor extension that switches streaming on. (2) A method chosen by a request parameter such as `stream: true` (Speakeasy fragments, Fern `stream-condition`). (3) A method generated from the response media type alone.

**What was compared.** The content type is the signal in ogen, the Swift runtime, Speakeasy, Fern (auto-detected since February 2026), liblab and APIMatic; no vendor needs a switch except for a mixed endpoint. OpenAPI 3.2 added `itemSchema` for sequential types. Stock openapi-generator has no streaming support at all. The existing decision to decline opt-in feature flags (#131) points at option 3.

**Decision.** Option 3, with no rollout flag. For a JSON Lines or SSE success response the generator adds `<op>Stream`, which returns the language's lazy sequence of items: the schema type `T` for JSON Lines and `ServerSentEvent<T>` (with `id`, `event`, `data`, `retry`) for SSE. Such an operation has no buffered method. TypeScript returns `SseStream<T> extends AsyncIterable<ServerSentEvent<T>>` with an idempotent `close()`; Java returns `SseStream<T> extends Iterator<ServerSentEvent<T>>, AutoCloseable` with an idempotent `close()` and `asPublisher()`; the carriers of the other ten are in [docs/design/streaming.md](design/streaming.md) section 6.1. The schema is read per version: in OpenAPI 3.0 and 3.1 `schema` is the item (JSON Lines) or the `data` payload (SSE), in 3.2 `itemSchema` is the item (for SSE the event envelope with `contentSchema` on `data`), and the registered extension `x-oai-itemSchema` is accepted as an input by the Java detector and never by a template. `application/json-seq` stays excluded. An operation that is streamed is never also given a paged method (D123).

**Consequences.** The surface of every SDK grows by a small family of stream types and `ServerSentEvent`. `OnlyAllowJsonRule` must keep the streaming media types. An endpoint that returns JSON or a stream depending on a request parameter is not covered in v1. Nothing is built yet.

## D121: Streams share one set of lifecycle rules

**Context.** A stream outlives the call that opened it, so the buffered rules for timeouts, retries, closing and uploads do not carry over unchanged.

**Options considered.** For the timeout: keep the end-to-end timeout over the body, or split it. For errors: reuse the network error or add a class. For closing the client: wait for open streams or cancel them. For uploads: native stream types with a native replay rule, or one small wrapper.

**What was compared.** Go's `Client.Timeout` "will interrupt reading of the Response.Body", .NET's `ResponseHeadersRead` bounds only the headers, Anthropic exposes a per-read timeout, Guzzle has `read_timeout` and APIMatic has a 60 second stream read timeout: an end-to-end timeout cannot apply to a body. Stainless, Anthropic, ogen and Speakeasy close the response when the consumer stops early. The Swift runtime's `HTTPBody` has the same replayable-or-single-use pair this design adopts for uploads. Go 1.27 drains an HTTP/1 body on `Close()`, which an endless feed would never finish.

**Decision.** (1) Stopping early releases the connection, even when the consumer never reads to the end. (2) `close()` is idempotent in every language. (3) There is one `StreamError`, a subclass of the SDK's network error, for a failure after the headers (a reset, a body that ends short); an idle timeout stays the network timeout error and a decode failure stays the serialization error. (4) The call timeout covers dial, TLS, send, the wait for the headers, redirects and retry waits and stops at the headers; an idle timeout bounds the gap between two reads of the body, 60 seconds by default for every kind, `0` or none turns it off, and an SSE comment line counts as activity. (5) Nothing is retried after the first body byte has been handed to the caller. (6) An upload takes a `BodySource`, replayable (bytes, a file or path, a factory that opens a fresh stream) or single use (a stream handed over); a single-use body is never buffered and never retried, and a 307 or 308 is refused for it. (7) `client.close()` cancels the streams that are open, where it used to finish what is running; requests in flight that are not streams still finish, and the Go stream `Close()` cancels the context before it closes the body.

**Consequences.** The sentence about closing a client in [Closing a client finishes what is running](#closing-a-client-finishes-what-is-running) gains an exception for open streams when this is built. `StreamError` is one new public class in each SDK. A 60 second idle default means a quiet feed needs a heartbeat or a larger setting. Nothing is built yet.

## D122: Server-sent events reconnect on a GET, end on a terminator, and raise in-band errors

**Context.** A server-sent event feed can drop, can end with a marker value, and can report a failure as an event after the status line already said 200.

**Options considered.** For reconnect: automatic by default (browsers, ogen, go-sse), opt-in (Fern `resumable`, Ktor), never (OpenAI, Anthropic, httpx-sse, the .NET parser, the Swift runtime). For the end marker: hard-coded (OpenAI's `[DONE]`), a spec setting (Speakeasy, Fern, APIMatic), a generator option, nothing. For an error event: deliver it as an item (Anthropic's wire design) or raise it (the OpenAI Python SDK raises on an `error` payload).

**What was compared.** The WHATWG text reconnects on a network error, sends `Last-Event-ID` when the last id is not empty and stops on a 204 or a non-`text/event-stream` response. Replaying a request that has a body is the hazard, which is why Fern's switch is off by default. The 2025 and 2026 releases add parser cases: a comment line must not crash the stream, LF, CR and CRLF all end a line, an event with an id and no data is not dispatched but records the id. Details and sources: [docs/design/streaming.md](design/streaming.md) sections 11, 12 and 16.

**Decision.** Reconnect is off for a request that has a body, and for a GET it gives up after five consecutive failures without an event; both can be changed by the caller. Each reconnect is a fresh request that sends `Last-Event-ID`. The terminator is a generator option, default none, and the terminator value ends the stream and is not delivered. An in-band event named `error` raises the SDK's API error (the response status, the event data as the body) and is not reconnected; any other event is an item. The parser follows the WHATWG grammar and the test table includes comments and heartbeats, the three line endings and the id-only event.

**Consequences.** The GET default is the more eager end of the field (Fern ships it off), which the owner may revisit. A feed that sends no heartbeat is cut by the 60 second idle timeout (D121) and reconnects. Nothing is built yet.

## D123: A paged operation gets a `<op>Pages` method that returns a paginator

**Context.** None of the twelve SDKs helps with a paged list: the caller reads the next-page token, offset or `Link` header from each response and writes the loop again ([#163](https://github.com/mridang/openapi-generator-plus/issues/163)). F1100 (java) and the C# note already say that pagination, when added, is a separate additive method and never a change to the return type of the existing one.

**Options considered.** (A) The existing method returns a page object. (B) A new method returns only an item sequence. (C) A new method returns a paginator with an item view and a page view. (D) An explicit cursor object. (E) Callbacks. (F) Reactive publishers. (G) Eager collect-all.

**What was compared.** Mature SDKs split into two families that both offer both views: AWS makes the page primary with item helpers, and Stripe, OpenAI, Anthropic, Azure, Octokit and Fern make the item primary with a page escape hatch; Google does both through one object. Each one uses the language's own sequence protocol, makes laziness free and treats page size and a total cap as different knobs; Stripe forces a limit on its collect-all. AWS Go and Soto stop on a repeated token, silently. The table of about two dozen SDKs and generators with sources is in [docs/design/pagination.md](design/pagination.md).

**Decision.** Option C. For each paged operation the generator adds `<operation>Pages` (C# `<Operation>PagesAsync`) with the same parameters in the same order as the base method; it returns a paginator with `items()` and `pages()` and no page wrapper as the primary type, and `pages()` yields a `Page<T>` value (items, next cursor, the same result `...WithHttpInfo` returns). The method makes no request until the first read, and each page is an ordinary buffered call with every retry, timeout, redirect, authentication and redaction rule. The cursor type follows the request parameter. A failed page raises the SDK error after the earlier items were delivered. A cursor equal to the one just sent, or an offset that did not advance, raises a new `PaginationError`, a direct child of the SDK's root error. There is no `maxItems` and no collect-all in v1. Iterating again restarts from the starting position. Elixir raises on a failed page instead of returning a tuple. Supersedes F1100 in its first form and the C# "Batch 7 follow-up" note.

**Consequences.** The base methods and the transport do not change, so this is additive. Each SDK gains `Paginator`, `Page` and `PaginationError`. Resuming is passing the next cursor as the starting parameter, with no separate resume API. Nothing is built yet.

## D124: Pagination is detected conservatively, with vendor extensions as hints and an override

**Context.** An OpenAPI document has no pagination keyword, and the repository forbids a template to read a spec-sourced `x-*` key, forbids client-side validation and says an ambiguous spec must leave the manual call ([#163](https://github.com/mridang/openapi-generator-plus/issues/163)).

**Options considered.** A vendor extension read by the generator (Speakeasy, Fern, Stainless, Kiota), a service-model trait (Smithy), a structural convention (Google AIP-158), the run-time `Link` header (Octokit), conservative inference with an override (the Spring option `autoXSpringPaginated` is the only precedent in openapi-generator).

**What was compared.** Nobody infers freely: Stainless, Speakeasy, Fern, liblab and APIMatic all need a declaration, and Stainless's id cursor silently ends when the last item has no `id`, which is the failure #163 forbids. OpenAPI 3.2 added no pagination keyword. In 2026 Fern and Speakeasy added next-URL pagination, HTTP QUERY became RFC 10008, and nobody added inference. See [docs/design/pagination.md](design/pagination.md) sections 4, 7 and 9.

**Decision.** Detection is a Java rule in the shared generator that computes a generator-owned model; templates never read an `x-*` key. An operation is eligible only when it is a GET with one 2xx JSON response, exactly one array property, exactly one matching style and exactly one candidate each for the request and the response parameter; otherwise it gets no paged method and the generation log says why. Tiers 1 (token cursor) and 3 (`Link` header, RFC 8288, same origin only) ship first. Tiers 2 (id cursor with `has_more`), 4 (offset or page number) and 5 (next URL in the body) come later behind the explicit override. The Java generator reads `x-ms-pageable`, `x-fern-pagination`, `x-speakeasy-pagination`, `x-stainless-pagination-property`, `x-liblab-pagination`, APIMatic `x-pagination` and `x-dropshot-pagination` as hints, and a generator option overrides a hint and inference, for one operation or for the whole feature. A cursor in a POST body and a cursor in a response header other than `Link` are out of v1; HTTP QUERY (RFC 10008) is the later path for a read-only search with a body.

**Consequences.** Inference leaves some real paged operations manual, and the override and the hints exist for them. The generation log names every operation that was skipped. Link-header support needs an RFC 8288 parser and an origin check. A paged test fixture is an open item: whether the Chasm mock can answer a different page per cursor is not settled, and Go can use the in-memory test server of Go 1.27. Nothing is built yet.

## Models are not validated when you build them

Building a model does not check anything: the wrong values are caught where the data crosses the wire. A response with an enum value that the SDK does not know, a missing required property or a `format: uri` that is not absolute fails when it is decoded. The SDK does not enforce schema constraints such as `pattern`, length or range, because the server is the authority. Java also checks that the required properties are set when you call `build()` on a builder.

## Native types for durations, addresses, URIs and decimals

A format that a language's standard library can represent is that language's own type, and the wire form underneath is the same in all twelve SDKs: a duration is the protobuf JSON form such as `3600s`, an IP address is its canonical text, a `uri` is written byte for byte, and a number is written in plain notation with every digit. A language without such a type keeps a documented fallback (a small library type or the plain string), and `docs/types.md` says which. An address that does not parse, like any other typed value that cannot be read, fails the decode with the SDK's `SerializationError` and never turns a response into a string; an address is preserved and its text is canonical, so `2001:0DB8:0:0:0:0:0:1` is written back as `2001:db8::1`.

## Extra properties have the type their schema declares

A schema whose `additionalProperties` declares a value type (a string, an `int32`, a `date-time`, a `byte`, a model, an array or a map) exposes the extra keys as a string-keyed map of that type, so you read a `Map<String, String>` and never cast. Each extra value is decoded like an element of a map of the same type: a value of the wrong JSON type, an `int32` out of range, an unknown enum value or a model with a missing required property fails the decode with the SDK's serialization error, and a `null` is rejected unless the value schema is nullable. Extras are written back at the top level in their wire form, after the declared properties, and a declared property wins over an extra with the same name. A free-form `additionalProperties` (`true`, an empty schema or no `type`) keeps accepting and returning any JSON value. Constraint keywords such as `pattern` or `maximum` are not checked on extras, as for declared properties.

## Language version floors

The minimum version of each language is the oldest release that its maintainers still support, and it is written in the build manifest of the generated SDK (`pom.xml`, `build.gradle.kts`, the `.csproj`, `package.json`, `pyproject.toml`, the gemspec, `composer.json`, `go.mod`, `Cargo.toml`, `Package.swift`, `pubspec.yaml` and `mix.exs`). A generated README states the same floor as its manifest, and the language page for each SDK is the single place where the floor is explained. The generated READMEs describe how a floor moves: the Node.js, C# and Swift READMEs say that dropping a runtime is a major release, and the Ruby README says that dropping an end-of-life Ruby is a minor release. The current floors are in the table in the [README](../README.md#languages).

## Examples are compiled

Every code fence in a generated README or SKILLS file that is tagged with the SDK language is built by the SDK's own compiler or type checker in the test suite, and every SDK ships one complete program, `examples/quickstart`, that is built and run against the mock server. A fence is therefore self-contained: it declares its kind (`file` or `statements`) after the language, imports what it uses and builds no model from nothing. The oldest supported runtime of node, rust, elixir, python, kotlin and csharp is also exercised by running that SDK's test suite in its image.

## Out of scope

These OpenAPI features are not supported: callbacks and webhooks, Link Objects, XML bodies and the `xml` object, and mutual TLS. Callbacks and links are ignored when the specification contains them. The reasons are in [What is not supported and why](#what-is-not-supported-and-why).

## What is not supported and why

Each item below was weighed and left out on purpose, or deferred to an open issue. The reason is given so that the question does not need to be asked again. Items with an issue number are listed under review in the next section; the others are settled.

### Left out on purpose

* **Callbacks, webhooks, Link Objects, XML bodies and mutual TLS.** They need a design of their own and no caller has asked for them, so the generators ignore callbacks and links and the READMEs say so. The tracking issues are #53, #70, #78 and #90.
* **SOCKS proxies.** Only `http://` and `https://` proxy URLs are accepted, and a `socks` URL fails when the client is built. The HTTP libraries underneath do not agree on SOCKS support, and a client that quietly ignored the setting would send traffic outside the proxy.
* **Proxy settings on Swift for Linux.** `URLSession` in swift-corelibs-foundation does not implement the proxy dictionary, so the Swift client raises a `ConfigurationError` instead of ignoring the setting. Supporting it would mean replacing the transport.
* **Ambient proxy settings.** The SDKs use only the proxy you pass in the transport options and ignore proxy environment variables, with one exception: the Kotlin client runs on the Ktor CIO engine, which falls back to the JVM proxy selector (`http.proxyHost` and the system proxy) and cannot be told not to. This is the one settled difference in the proxy behaviour.
* **A `LICENSE`, a `CHANGELOG.md`, CI workflows, pre-commit hooks and a devcontainer in a generated SDK.** The generator does not know which licence text you want or what changed since the last run, and the CI setup belongs to the project that publishes the SDK. Add them in your release pipeline.
* **Client-side `dependentRequired` and `dependentSchemas`.** No mainstream client generator checks them and the server is the authority. Use a JSON Schema validator if you need it.
* **HTTP/2 and chunked transfer encoding.** The libraries underneath choose the protocol version and reassemble chunks themselves, and the caller sees the same bytes either way, so there is nothing for the SDK to set.
* **Replaying a multipart body on a 307 or 308 redirect in Kotlin, C# and PHP.** The libraries underneath drop or rewrite the body, and a fix means following redirects by hand as the Rust client does. The tests are skipped in those three languages and say why in the test file.

### Deferred to an issue

* **Streaming responses and uploads (#114).** The transport contract reads a whole response and sends a whole body in all twelve SDKs, so server-sent events, JSON Lines and very large files cannot be expressed. The shape has to be chosen once for all twelve, so nothing was built in one language first. Until then you can pass your own HTTP client.
* **Validating the `id_token` and sending a nonce in the OIDC authenticator (#110).** PKCE is in place for the authorization-code flow, but replay of an `id_token` is not detected by the client.
* **A maximum response size (#112).** No transport limits how much it reads or decompresses, so a hostile server can make a client read a very large body. The option and its default must be the same in all twelve.
* **An end-to-end timeout that holds while the body is read (#116).** The default is a 10 second budget for the whole call, and a per-call timeout is in the options for a single call. Python and Ruby still apply the timeout per socket read, which needs a streaming reader, so a server that trickles a body can hold a call past the budget.
* **A typed error from Rust operations (#113).** Every Rust operation returns `Box<dyn Error>`. A concrete error type is a public API break for that SDK alone.
* **Error bodies decoded against the declared error schema (#117).** `errorBody` holds the parsed JSON, not a model. Typing it changes the `ApiError` generics in every SDK and needs a shared model in the generator.
* **Object-valued query and header parameters (#109), classic `allOf` with a discriminator (#104), single-pass Go models (#107), Go tracer and meter providers (#108), a dependency audit (#105), compiled README examples (#111) and the Dart browser client (#115).** Each changes generated output in all or many languages and is waiting for its own change.
* **A date type for Go (#126), keep-alive connections in the Ruby client (#127) and escaping of reserved property names in Swift and Elixir (#128).** Each is one language and waits for an owner decision on the shape.

### Settled differences between languages

* **Cookie parameters.** A value is sent as given when every character is allowed in a cookie, and an illegal character fails before the request is sent. Nothing is percent-encoded for you, because the shared parameter model cannot tell an explicit `style: form` from the default.
* **One credential per security scheme.** Where a client takes one credential per scheme, an operation uses the credential of the first requirement the client holds in full (D116); a requirement that names several schemes sends all of their credentials. A per-call override of the scheme is deferred, and Rust has no such override.
* **Test TLS keys.** No private key is committed. The test CA and the server key are generated once per test run by the shared harness and mounted into the containers that run the generated tests. The Dart and Swift standard libraries cannot create X.509 certificates, so the generated SDK tests only read the material and do not create it.
* **Trace context comes from the caller's context (#136).** A request carries the span active in the context of the call. Go takes the context as the `ctx` argument; the other eleven read the ambient OpenTelemetry context in the caller's thread, task, fiber, process or zone. No SDK builds a context of its own, and a failure to inject never fails a call.
* **A response that lies about its encoding is an error.** When the server declares `Content-Encoding: gzip` (or another compression) and the body is not that encoding, the call fails with the SDK's API error. The body is never returned as if it were plain text, because that hides a broken server behind a success.
* **A time of day in a query keeps its milliseconds.** `10:20:30.250` is sent as `10:20:30.250`, a whole second as `10:20:30`, and anything finer than a millisecond is cut off. This is the same rule as for a time in a body.
* **readOnly and writeOnly have no effect on the wire.** The SDK writes and reads such a property like any other; the schema keyword is documentation. The server owns the decision to ignore it.
* **A duration fraction has at least three digits.** `1.5` seconds is written `1.500s`; the fraction grows to six or nine digits only when it needs them.
* **Multipart names that cannot be written are rejected.** A field name or filename with a carriage return, line feed or NUL fails before any request is sent; a double quote is escaped with a backslash.
* **A string result under a binary content type is decoded.** The internal base64 carrier the transport uses for binary bodies never reaches your code.
* **Blank credentials fail; blank variables are unset.** A credential you pass as an empty string is refused, never swapped for the environment. An environment variable that is set but empty counts as unset. A proxy is cleared by passing no value, never an empty string.

## Under review

Nothing below is decided. Each item is an open issue, and the list can change.

* [#104](https://github.com/mridang/openapi-generator-plus/issues/104): support classic `allOf` inheritance with a discriminator in every SDK.
* [#105](https://github.com/mridang/openapi-generator-plus/issues/105): run a vulnerability and unused-dependency audit on every generated SDK.
* [#107](https://github.com/mridang/openapi-generator-plus/issues/107): decode Go models in a single pass.
* [#108](https://github.com/mridang/openapi-generator-plus/issues/108): add optional tracer and meter providers to the Go client.
* [#109](https://github.com/mridang/openapi-generator-plus/issues/109): serialize object-valued query and header parameters per the OpenAPI style rules.
* [#110](https://github.com/mridang/openapi-generator-plus/issues/110): generate a nonce and validate `id_token` claims in the OIDC authenticator.
* [#111](https://github.com/mridang/openapi-generator-plus/issues/111): compile the README and `SKILLS.md` code fences and test the minimum supported runtimes.
* [#112](https://github.com/mridang/openapi-generator-plus/issues/112): add a maximum response size transport option.
* [#113](https://github.com/mridang/openapi-generator-plus/issues/113): return a typed error from Rust operations instead of `Box<dyn Error>`.
* [#114](https://github.com/mridang/openapi-generator-plus/issues/114): support streaming responses and uploads in the generated clients (shape decided in D118 to D122, not built).
* [#115](https://github.com/mridang/openapi-generator-plus/issues/115): cover the Dart browser client and derive the keyword lists from the toolchains.
* [#116](https://github.com/mridang/openapi-generator-plus/issues/116): add a per-call timeout and make the end-to-end timeout hold while the body is read.
* [#117](https://github.com/mridang/openapi-generator-plus/issues/117): decode error bodies against the operation's declared error schema.
* [#118](https://github.com/mridang/openapi-generator-plus/issues/118) and [#121](https://github.com/mridang/openapi-generator-plus/issues/121): compare the generated test suites of the twelve languages and close the gaps.
* [#119](https://github.com/mridang/openapi-generator-plus/issues/119): read OAuth credentials from the environment in the Dart and PHP authenticators.
* [#120](https://github.com/mridang/openapi-generator-plus/issues/120): build the generated documentation in every language as part of the specs.
* [#122](https://github.com/mridang/openapi-generator-plus/issues/122): guard the public API surface of every SDK and stop internals from leaking.
* [#123](https://github.com/mridang/openapi-generator-plus/issues/123): generate a typed error hierarchy per status and per declared error schema.
* [#124](https://github.com/mridang/openapi-generator-plus/issues/124): sweep the build, lint, format, test and docs logs of every SDK for warnings.
* [#163](https://github.com/mridang/openapi-generator-plus/issues/163): generate a paged method for every paged operation (shape decided in D123 and D124, not built).
* [#126](https://github.com/mridang/openapi-generator-plus/issues/126): type `format: date` values in the Go SDK.
* [#127](https://github.com/mridang/openapi-generator-plus/issues/127): keep connections alive in the Ruby client.
* [#128](https://github.com/mridang/openapi-generator-plus/issues/128): stop escaping reserved property names that need no escape in the Swift and Elixir SDKs.
* [#129](https://github.com/mridang/openapi-generator-plus/issues/129): re-check the audit leads that were deferred without an issue.
