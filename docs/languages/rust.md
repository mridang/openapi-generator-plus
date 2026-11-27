# Rust

The `rust-plus` generator (`BetterRustCodegen`) produces an async Rust crate with one module per API tag, one struct or enum per model, an `ApiClient` trait with a `DefaultApiClient`, authenticators, typed errors and a test suite. It uses `reqwest` on `tokio`, with `serde` for JSON. For the rules shared by all twelve languages see [how it works](../how-it-works.md), for the type table across languages see [types](../types.md), and for settled cross-language choices see [decisions](../decisions.md).

## Versions and support policy

The generated `Cargo.toml` sets `edition = "2021"` and `rust-version = "1.85"`, and the generated README says Rust 1.85 or newer. Rust has no end-of-life policy, only a current stable release, so 1.85 is a chosen minimum supported Rust version rather than a support window. It is kept as long as it is not newer than the oldest release that is still supported elsewhere. `clippy.toml` carries the same `msrv`. The generator builds and checks the golden crate in a Rust 1.96 toolchain image.

The crate forbids `unsafe` code (`unsafe_code = "forbid"`).

## Type mapping

| OpenAPI type and format | Rust type | Notes |
|---|---|---|
| `string` | `String` | Also used for `email`, `hostname`, `password` and unknown formats. |
| `string` / `ipv4`, `ipv6` | `std::net::Ipv4Addr`, `std::net::Ipv6Addr` | Read and written through the standard library's own serde implementation: a leading zero, a prefix length, a zone id, brackets, whitespace and the wrong family fail the decode, and an address is written in canonical text. |
| `string` / `date` | `chrono::NaiveDate` | |
| `string` / `date-time` | `crate::date_time::DateTime` | An SDK newtype over `chrono::DateTime<FixedOffset>`. The offset is kept. The wire form has a numeric offset (never `Z`) and a fixed three-digit millisecond fraction. |
| `string` / `time` | `chrono::NaiveTime` | A time string with a zone offset does not round trip. |
| `string` / `duration` | `chrono::TimeDelta` | Serialised through `crate::proto_duration` as the protobuf-JSON string such as `"3600s"`, not ISO 8601. The standard library's `std::time::Duration` is unsigned and cannot hold a negative duration, so `chrono` is the documented fallback (D133). |
| `string` / `uuid` | `uuid::Uuid` | |
| `string` / `byte` | `Vec<u8>` | Base64 through a serde adapter. |
| `string` / `binary`, file bodies | `BodySource` for a raw request body (a multipart file part stays `Vec<u8>`); `ByteStream` (a `Stream` of `Bytes`) for a response | The response body is read as you consume it and the request body is sent as it is read, see Streaming. |
| `string` / `uri` | `String` | |
| `integer` / `int32` | `i32` | `short` maps to `i16`. |
| `integer` / `int64` | `i64` | |
| `integer` with no format | `i64` | |
| `number` / `float` | `f32` | |
| `number` / `double`, `number` with no format | `f64` | |
| `number` / `decimal` | `rust_decimal::Decimal` | Exact (D135). Read from the digits of the JSON number, never through an `f64`, and written back as a bare JSON number in plain notation. A value with more digits than `Decimal` holds (28 significant digits) fails with `SerializationError` instead of being rounded. |
| `string` / `decimal` | `rust_decimal::Decimal` | Exact (D135). Read only from a JSON string (a bare number fails with `SerializationError`) and written back as a JSON string that keeps its scale, so `"12.50"` stays `"12.50"`. |
| `boolean` | `bool` | |
| `array` | `Vec<T>` | |
| `array` with `uniqueItems` | `std::collections::HashSet<T>` | Written in sorted order by a serde adapter so the bytes are stable. |
| `object` with `additionalProperties` | `std::collections::HashMap<String, T>` | `T` is the declared value type; a free-form bucket keeps the any-type |
| free-form `object`, any type | `crate::json_value::JsonValue` | An SDK newtype over `serde_json::Value`, so `serde_json` types do not appear in public signatures. |
| nullable | `Option<T>` | A required nullable property is handled by a dedicated serde adapter. |
| `enum` | Rust `enum` with `serde` renames | Integer enums use `serde_repr` and variants named `Number1`, `NumberMinus1` and so on. |
| `oneOf` | `enum` with payloads | Deserialised by hand-written code. |
| `anyOf` | struct of `Option` slots | |

Recursive properties are wrapped in `Box<T>`. Model names that would shadow the prelude (`Result`, `Option`, `Vec`, `Box`, `Some`, `None`, `Ok`, `Err`) get a `Model` prefix. Property names that are Rust keywords become raw identifiers (`r#type`).

## Dependencies

Runtime dependencies, from the generated `Cargo.toml`:

| Crate | Version | Used for |
|---|---|---|
| `reqwest` | 0.12 | HTTP, with `json`, `multipart`, `http2`, `charset` and `rustls-tls-native-roots` |
| `tokio` | 1 | Async runtime |
| `serde`, `serde_repr`, `serde_json` | 1, 0.1, 1 | JSON. `serde_json` uses `unbounded_depth`, `preserve_order` and `raw_value` (the source text of a decimal number) |
| `chrono` | 0.4 | Dates and times |
| `rust_decimal` | 1 | `format: decimal`, with `std` and `serde` only |
| `uuid` | 1 | The `uuid` type |
| `base64` | 0.22 | `byte` fields |
| `flate2`, `brotli`, `zstd` | 1, 7, 0.13 | Decoding gzip, deflate, `br` and `zstd` responses |
| `sha2`, `regex`, `urlencoding`, `encoding_rs` | 0.10, 1, 2, 0.8 | Hashing, matching, parameter encoding and charsets |
| `opentelemetry` | 0.32, optional | Trace-context propagation and the `OpenTelemetryCallHook` adapter (API only, traces and metrics), behind the `opentelemetry` feature |

Test-only dependencies are `opentelemetry_sdk` and `testcontainers`. The default feature set is empty.

## Options

| Option | Default | What it does |
|---|---|---|
| `packageName` | none | Name of the generated crate. |
| `packageVersion` | `1.0.0` | Version written to the manifest. |
| `repositoryUrl` | none | Source repository URL, written to the manifest and README. |
| `homepageUrl` | none | Project homepage, written to the manifest and README. |
| `authors` | none | Comma-separated author names, written to the manifest. |
| `documentationUrl` | none | Documentation URL, written to the manifest and README. |
| `issueTrackerUrl` | none | Issue tracker URL, written to the manifest and README. |
| `envVarPrefix` | The API title without a trailing `SDK`, in upper snake case | Prefix of the environment variables the credentials default from (see "Credentials and environment variables"). Shared by all twelve generators. |

## Credentials and environment variables

The authenticator the generator writes for each security scheme (for example `PetStoreBearerAuthenticator`) takes every credential as an `Option<&str>`. The order is fixed:

1. The value you pass wins, even an empty string (the underlying authenticator then rejects it with its own message).
2. Otherwise the environment variable, read once when the authenticator is built. An empty variable counts as unset.
3. A Bearer scheme's authenticator also has `with_token_provider(host, provider)`, which takes the token from a callback that runs for every request and reads no variable. `Client::with_token_provider` does the same for the client.

A credential found in none of these places makes `new` return `ConfigurationError::InvalidArgument` naming the variable, so the failure comes when the authenticator is built and not on the first request.

The variable for a field is `{PREFIX}_{FIELD}` in upper snake case, for example `ACME_TOKEN`, `ACME_API_KEY`, `ACME_USERNAME`, `ACME_CLIENT_ID`. `{PREFIX}` is the `envVarPrefix` option, or else the API title without a trailing `SDK`. When two schemes have a field of the same name, each name also carries the scheme id so that no scheme reads another's secret. The generated README lists the exact variable of every scheme and field. `Client::with_token` keeps reading `{PREFIX}_BASE_URL` and `{PREFIX}_ACCESS_TOKEN` as before.

## Idiom notes

- Every operation takes an optional trailing `options` argument (`Option<&...Options>`), even one with no parameters and no security. Besides the operation's own parameters the Options struct carries `timeout(Duration)`, `max_retries(u32)` (`0` turns retrying off) and `header(name, value)` for that one call. An Options struct for an operation with no parameters and no security has no `auth` field.

- Operations return `Result<_, <crate>::Error>` (D136): a `#[non_exhaustive]` enum with one variant per error kind, such as `Error::NotFound`, `Error::Network` and `Error::Configuration` for a caller mistake, one variant per declared error schema, and `Error::Other` for anything else. It implements `std::error::Error`, so `?` works, and `api_error()` returns the `ApiError` of any HTTP failure. The items of a byte, JSON Lines or event stream still fail with a boxed error; `Error::from` sorts one into its variant.
- Streaming. An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns `ByteStream` from the one operation method (there is no second method), and `..._with_http_info` returns `ApiResult<ByteStream>` with an empty `raw_body()`. `ByteStream` is a `futures_core::Stream` of `Result<Bytes, Box<dyn Error + Send + Sync>>` with `status_code()`, the lower-cased `headers()`, an idempotent `close()` that aborts the connection instead of draining it (dropping it does the same), `is_closed()`, `bytes_delivered()` and `read_all(&mut self)` that reads the rest and leaves the stream closed; its `Debug` and `Display` show only the status and the bytes delivered. The transport gets `ApiClient::send_streaming(method, url, headers, body, &RequestOptions)`, which returns an `ApiStreamResponse` (status, headers, an unread body); its default implementation returns an `std::io::ErrorKind::Unsupported` error that names the transport, so an injected `ApiClient` keeps working for buffered calls. `TransportOptions::idle_timeout` (default `Some(60000)` ms, `Some(0)` or `None` disables it) bounds the gap between two reads of the body and raises `NetworkTimeoutError`; the call `timeout` stops at the headers. A reset or a body shorter than its `Content-Length` raises `StreamError`, a `NetworkError` with status 0. `Client::close()` cancels the open streams, whose next read raises `ConfigurationError::InvalidState`. A body the server compressed (gzip, deflate, br, zstd) is decoded as it arrives, incrementally, so the first items are available before the body ends; a body that does not decode ends the stream with an `ApiError` that carries the real status and the decoder error. `bytes_delivered()` counts decoded bytes, and the two headers that described the compressed body are dropped.
- Uploads. An operation whose request body is raw binary takes a `BodySource` (`pub`, in the crate root) in every signature (the operation, `..._with_http_info`, the `...Trait` and the client): `BodySource::from_bytes(impl Into<Bytes>)` (`Vec<u8>` and `Bytes` convert with `.into()`), `BodySource::from_path(impl Into<PathBuf>)` and `BodySource::from_factory(|| stream)` are replayable, `BodySource::once(stream)` is single use, and `.with_length(n)` declares the length of a factory or single-use body. A stream is a `futures_core::Stream` of `std::io::Result<Bytes>`; it is the caller's to build, and it is moved into the SDK, so there is no handle left to close: the SDK reads it and drops it when the call ends, and anything the stream wraps that the caller still holds (a shared handle) is left open. The body is sent as it is read, never buffered: a known length sends `Content-Length`, an unknown one is chunked. A replayable body is opened again for a retry after a 5xx, a 429 or a network error and for a 307 or 308; a single-use body is never retried (the original error is returned unchanged), a redirect that would need it again raises `ApiError` with `Refusing to replay a streamed request body`, and a 401 token refresh and an SSE reconnect follow the same rule. `Debug` shows the kind and the length, never the content or a path. A custom `ApiClient` receives `RequestBody::Source(BodySource)` and reads it with `open().await`. Multipart bodies stay buffered. A file that cannot be opened fails the call with `ConfigurationError::InvalidArgument`.
- Pagination. A GET list that the shared detector finds paged (one of the styles in the next bullet; D123, D124) has `<operation>_pages(...args, options)` beside its plain method, in the struct and in the `...Trait`. It is a plain method, not an `async fn`, and returns `Paginator<T, C, B>` (`T` the item type, `C` the cursor type, `()` for a `Link` or next URL list, `B` the page body type), whose `items()` is a `Stream` of `Result<T, Box<dyn Error + Send + Sync>>` and whose `pages()` is a `Stream` of `Result<Page<T, C, B>, ...>`; each call builds a fresh stream, so a second loop starts again, and dropping a stream stops the requests. `Page<T, C, B>` has `items()`, `into_items()`, `next_cursor()` (`None` on the last page, always `None` for `Link` and next URL lists) and `result()` (the `ApiResult<B>` of the page). The cursor is an optional query parameter, so it lives in the `<Operation>Options` struct (which is `Clone`): each page is an ordinary `_with_http_info` call with a clone of the options that carries the cursor, so the caller's options are never changed. A failed page is the last item of the stream, after the items of the earlier pages. A cursor equal to the one just sent, or a `Link` that points back at the page just read or leaves the origin of the client, delivers the page it came with and then yields `PaginationError` (a direct child of the SDK root error, not an `ApiError`). A `Link` page is requested through the private `<operation>_invoke`, which takes the absolute URL as `page_url`, replaces the path with it and clears the query; `BaseApi` joins an authentication query parameter to a URL that already has a query with `&`. The group structs are `Clone` (they hold `Arc`s) so the paginator owns its own copy and outlives the call. `ItemsOf` and `CursorOf` (hidden) read the items and the next cursor of a page whether the property is optional or not.
- Pagination styles. The generator finds a paged operation by its names and shape. The operation must be a GET with one JSON 2xx response, and it must match exactly one of these styles. A token cursor is one query parameter among `cursor`, `after`, `page_token`, `next_token`, `continuation_token` and `marker`, paired with a next-cursor property among `next_cursor`, `next_page_token`, `next_token`, `next_marker`, `next_continuation_token`, `continuation_token` and `next` of the same type. An id cursor is one query parameter among `starting_after`, `after_id` and `after`, paired with a boolean `has_more` and a string `last_id` or `next_cursor` property. A `Link` list has a `Link` header declared on the response. An offset list has an `offset` query parameter with a `limit`, and a page-number list has a `page` query parameter with one of `per_page`, `page_size`, `size` or `limit`. A next URL list has no cursor parameter and a string property with `format: uri` or `uri-reference`, or one named `next_url`, `next_uri`, `nextlink` or `next_link`, that is optional or nullable. An operation that matches more than one style is ambiguous, and one that is streamed is never paged; either keeps only its plain method, and the generation log names it and says why. The `x-fern-pagination` and `x-speakeasy-pagination` (cursor type) extensions are read as hints. The generator option `paginationOverrides` (a JSON object that maps an operation id to `none` or to `{style, requestParam, responseProperty, itemsProperty, hasMoreProperty}`, where the style is `token`, `link`, `id`, `offset`, `page` or `body`) wins over a hint and over inference, and `pagination=off` switches the feature off. How each style ends and resumes: an id list passes the `last_id` or `next_cursor` of each page as the starting cursor of the next request and ends when a declared `has_more` is false. An offset list starts at the offset you pass, sends the offset past the items it received on each next request, and ends on an empty page or when a declared `has_more` is false. A page-number list sends the page you pass, or none, and then asks for the page after each one, and ends on an empty page or when a declared `has_more` is false. A next URL list takes the URL in the body of each page, follows it only within the origin of the client, and ends when the property is absent, `null` or empty. A cursor, offset or page that does not advance raises `PaginationError` as a token cursor does. A list whose page body carries the next URL in a property (a next URL list) is followed in the same way as a `Link` list: the URL is taken from the body of each response, a next URL that leaves the scheme, host or port of the client is refused with `PaginationError`, nothing is sent to it, and such a method cannot resume from a saved position.
- JSON Lines and server-sent events. An operation whose success response is `application/x-ndjson`, `application/jsonl`, `application/jsonlines` or `text/event-stream` has no buffered method and no `..._with_http_info` variant. It has `<operation>_stream(...args, options, stream_options: Option<&StreamOptions>)` returning `JsonLinesStream<T>` (a `Stream` of `Result<T, Box<dyn Error + Send + Sync>>`) or `SseStream<T>` (a `Stream` of `Result<ServerSentEvent<T>, ...>`), both with `status_code()`, `headers()`, an idempotent `close()` and a redacted `Debug` and `Display`. `ServerSentEvent<T>` has the public fields `id`, `event` (`message` by default), `data`, `retry` and `raw`. `StreamOptions` is a consuming builder (`idle_timeout`, `max_reconnects`, `reconnect_delay`, `last_event_id`, `max_event_bytes`). The parser follows the WHATWG grammar (`event_stream.rs`, crate-private), a decode failure is a `SerializationError` naming the 1-based line (JSON Lines), and the generator option `sseTerminator` ends a feed on a named data value. An event stream reconnects after a clean end, a network error or an idle timeout with fresh headers and `Last-Event-ID`, up to `max_reconnects` times in a row (5; 0 for a request with a body; `-1` unlimited), waiting the server's `retry` value, else `Retry-After` on 429, 502, 503 and 504, else `reconnect_delay` doubling to 30 seconds with jitter; an event named `error` raises `ApiError`, and a 204, a 4xx, a response that is not `text/event-stream`, a cancellation or `client.close()` never reconnects.
- There is no cancellation API. Drop the future to cancel a call, and use the `timeout` setting to bound one.
- `auth_headers()` is infallible, and `try_auth_headers()` is the variant that returns a `Result`.
- Rust has no overloads or default arguments, so variants carry their own names, such as `*_with_content_type`, `get_access_token_with_refresh_url` and `send_request_with_options`.
- `Configuration` and the builders implement `Default`.
- `ApiResult::into_data(self)` moves the payload out of the result.
- `CallHook` is one small hook around every operation call: `start(&CallInfo, &mut HashMap<String, String>)` runs once per call after the headers are final and may edit them, and returns an `Option<Box<dyn CallObserver>>` whose `finish(self: Box<Self>, &CallOutcome)` runs exactly once on every way the call can end. Set it with `ConfigurationBuilder::call_hook(Arc<dyn CallHook>)` or `Client::with_call_hook(hook)` (Rust has no client builder, so the client takes it as a consuming method); `call_hook::chain(vec![a, b])` combines hooks. `CallInfo` carries `operation_id` (as written in the spec), `method`, `url_template`, `url` (no query string or fragment) and `server_address`; `CallOutcome` carries `status_code` (`0` when no response arrived), the lower-cased `headers` (`None` when none arrived) and the `error` the caller receives, borrowed. There are no exceptions to throw, so a panic in `start` unwinds to the caller with nothing sent, and a panic in `finish` is swallowed. Cancellation is dropping the future, so a dropped call finishes with `CallCancelled`.
- `OpenTelemetryCallHook::new()` (global providers) or `with_providers(&tracer_provider, &meter_provider)`, behind the `opentelemetry` feature, is a `CallHook` that records one span (named with the operation id, kind client), the counter `http.client.request.count` (unit `{request}`) and the histogram `http.client.request.duration` (unit `s`) per call. It needs only the OpenTelemetry API; the application installs the SDK. It writes `traceparent` itself in the W3C format.
- Lint exclusions live in one place, the `[lints]` tables of `Cargo.toml`. Generated source files carry no `allow` attributes, and `cargo clippy -- -D warnings` is the gate.
- Testing your code: `<Group>ApiTrait` in the group file and `ClientTrait` in `client.rs`, re-exported from the crate root. The trait methods are `fn ... -> impl Future<Output = ...>` and not `async fn`, which keeps the `async_fn_in_trait` lint quiet; they are not required to be `Send`, because the per-operation server traits have no `Send + Sync` bound and requiring it would be a breaking change. No `async-trait`, no `dyn`. `ClientTrait` has one associated type per group so `Client` keeps its concrete group structs. The Rust gate compiles only on CI.
- With `generateExamples` on, the crate ships `examples/quickstart.rs`, a `#[tokio::main]` program that reads `{PREFIX}_BASE_URL` and `{PREFIX}_ACCESS_TOKEN` with `std::env::var`, builds the client with `Client::with_token`, calls the example operation, closes the client and lets any error escape with `?`. Cargo finds it with no manifest entry; `make examples` runs `cargo build --examples`, `make examples-run` runs it with `cargo run --example quickstart`, and `cargo clippy --all-targets` lints it. The README Usage fence is the quickstart's statements.

## Credentials per security scheme

`Credentials` holds one optional credential for each distinct security scheme of the API, set with a `with_<scheme>` method named after the scheme. `Client::with_credentials(host, credentials, transport_options)` builds a client from it. Each operation uses the credential of one of the schemes its specification requires that is set, the first in the order the generated operation lists them, and goes out unauthenticated when none is. A single call cannot pick another scheme: the Options struct carries no authenticator.

## Closing the client

`close()` is graceful and can be called more than once. A request already in flight finishes. A request started afterwards fails with `ConfigurationError::InvalidState("ApiClient has been closed and can no longer send requests")`. Only the transport the SDK built is released; a `reqwest::Client` you supplied is never closed. Cancel one call by dropping its future.

## Cookie parameters

A cookie parameter value is sent exactly as given when every character is a legal RFC 6265 cookie-octet. Otherwise the call fails with `ConfigurationError::InvalidArgument` before anything is sent. The SDK does not percent-encode the value, so encode a raw value yourself first.

## Models

Constructors and `Default` never validate. A wrong value fails only when a response or a payload is decoded, and an unknown enum string decodes to the `UnknownDefaultOpenApi` variant (D117).

## Recipes

These are answers to questions that come up often, each built from what the generated SDK already has. None of them adds API.

### Hooks around every call

The call hook added by [#158](https://github.com/mridang/openapi-generator-plus/issues/158) is the one place to edit the request headers before the send and to observe the status or the error afterwards, for every operation and for the client as a whole. It is optional and does nothing when it is not set. It runs once per operation call however many sends happen inside it (retries, the one replay after a `401`), once for the plain and the `WithHttpInfo` variant alike, and also when you supply your own transport. `start` receives the call information (`operationId` as written in the spec, `method`, `urlTemplate`, `url` without its query string, `serverAddress`) and the final headers, which it can read and replace, and it returns the observer to call when the call ends or nothing. The observer's `finish` receives the status code (0 when no response arrived), the lower-cased response headers (none when no response arrived) and the error the caller receives (none on success). An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed; the hook never changes the result or the error. `CallHook` is the name to look for in this SDK.

```rust,ignore
struct Tenant;

impl CallHook for Tenant {
    fn start(&self, _info: &CallInfo, headers: &mut HashMap<String, String>) -> Option<Box<dyn CallObserver>> {
        headers.insert("X-Tenant".to_string(), "a".to_string());
        None
    }
}

let client = Client::with_token(Some("https://api.example.com"), Some("token"), None)?
    .with_call_hook(Arc::new(Tenant));
```

To see every send of a call rather than the call as a whole (for example each retry), wrap the transport you hand to the SDK instead: see "Custom HTTP client" in the generated README. A transport you supply is never closed by the SDK, and the hook above still fires with it.

### Serving another region or tenant

A client is cheap, and its host, credentials, retry settings and connections belong to it. To call another region or another tenant, build another client; there is no per-call server URL or per-call HTTP client, and the per-call options stay `timeout`, `headers` and `maxRetries`.

```rust,ignore
let eu = Client::with_token(Some("https://eu.api.example.com"), Some("eu-token"), None)?;
let us = Client::with_token(Some("https://us.api.example.com"), Some("us-token"), None)?;
```

### Renaming types, fields and parameters

The names come from the specification, so the seam is the normaliser: the `RUN_SCRIPT` rule runs your own JavaScript over the document before generation (see [how it works](../how-it-works.md#renaming-with-a-script)). A schema renamed to `Animal` is generated as `src/models/animal.rs`, and this language's own casing, escaping of reserved words and file naming are applied to the new name like to any other. The SDK has no configuration map of names and never reads a vendor extension for a name. The name of a property or a parameter is also its name on the wire, so renaming one in the script renames it in the requests and responses as well: there is no rename that changes only the SDK name.

### Status, headers and the raw body

Every operation has a `..._with_http_info` variant that returns the status code, the lower-cased response headers, the raw body and the decoded data. Every failure that carries an HTTP response is an `ApiError` that holds the status code, the response headers and the response body. A live response object is not exposed, because that would need streaming (see [decisions](../decisions.md)); to see or change what goes over the wire, use the call hook above or the transport you supply.

```rust,ignore
let result = client.pet.add_pet_with_http_info(pet.clone(), None).await?;
let status = result.status_code();
let etag = result.headers().get("etag");
let raw = result.raw_body();

if let Err(err) = client.pet.add_pet(pet, None).await {
    if let Some(api) = err.api_error() {
        println!("{} {:?} {:?}", api.status_code(), api.response_headers(), api.response_body());
    }
}
```

## Known limits

- An operation whose security requirement names two schemes sends both credentials; an alternative is used only when the client holds every scheme in it.

- A `number` with no format, `float` or `double` is a binary float (`f32`, `f64`) and loses exactness; use `format: decimal` for money.
- A `format: decimal` value holds at most 28 significant digits (the limit of `rust_decimal`); a longer value fails to decode rather than being rounded. A decimal inside a `oneOf` or `anyOf` member, or in a response with a repeated key, passes through `serde_json::Value`, which holds a number as an `f64`, so only about 17 significant digits survive there. A `uniqueItems` set or a map of decimals uses `rust_decimal`'s own serde form, which writes a JSON string.
- A JSON, text, form or multipart request body and a JSON, text or form response body are read whole into memory. Only a raw binary request body and a binary, a JSON Lines and a server-sent events response is a stream.
- Typed maps (`HashMap`) do not keep key order, while `JsonValue` does.
- An optional nullable property cannot tell an absent key from an explicit `null`. Both are `None`.
- `allOf` with a discriminator is not implemented yet.
- SOCKS proxies are rejected when the client is built. Only `http://` and `https://` proxies are accepted.
- A number such as `1e400` that overflows is rejected, where several other SDKs accept it as infinity.
- Schema constraints (`pattern`, `minLength`, `maximum` and so on) are not enforced by the client.
- Callbacks and webhooks, XML bodies, Link Objects and mutual TLS are out of scope in every language.
- An optional and nullable property has no third state: an absent value and an explicit `null` are the same, by decision D112. A three-state type for PATCH-style APIs is declined for now.
- The `wasm32-unknown-unknown` browser target is not supported, by decision D110. The client is built on a native HTTP stack and a Tokio runtime, and nothing in the build or the tests targets the browser.
- Operations are asynchronous only, as `async fn` futures, by decision D111. There is no blocking variant of an operation, because each SDK follows the style of its own language. Call it from your language's async machinery.
- An `integer` with no format is 64 bits, the same as `int64`, by decision D113 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)). A value above 2^31, such as a large identifier, decodes exactly.

## Under review

These are ideas from a type review in November 2026. None of them is decided, and none changes the generated SDK today.

- `url::Url` for `uri`. It is already in the dependency tree through `reqwest`, but it normalises on parse, so values would not round trip byte for byte.
- `#[non_exhaustive]` on generated enums, so a new server value is not a breaking change for callers who match on them.
- Edition 2024, which 1.85 already supports.
- `jiff` in place of chrono is not recommended by the review for now.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. `invoke_api` reads `Context::current()` once on its first poll and uses that value at every injection point, so a span attached on the caller's thread survives a move to another runtime thread; a caller may also use `FutureExt::with_context`.
