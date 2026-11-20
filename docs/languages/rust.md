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
| `string` / `duration` | `chrono::TimeDelta` | Serialised through `crate::proto_duration` as the protobuf-JSON string such as `"3600s"`, not ISO 8601. |
| `string` / `uuid` | `uuid::Uuid` | |
| `string` / `byte` | `Vec<u8>` | Base64 through a serde adapter. |
| `string` / `binary`, file bodies | `Vec<u8>` | Held fully in memory, for uploads and downloads. |
| `string` / `uri` | `String` | |
| `integer` / `int32` | `i32` | `short` maps to `i16`. |
| `integer` / `int64` | `i64` | |
| `integer` with no format | `i32` | |
| `number` / `float` | `f32` | |
| `number` / `double`, `number` with no format | `f64` | |
| `number` / `decimal` | `f64` | Lossy for exact decimal values such as money. |
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
| `serde`, `serde_repr`, `serde_json` | 1, 0.1, 1 | JSON. `serde_json` uses `unbounded_depth` and `preserve_order` |
| `chrono` | 0.4 | Dates and times |
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

- Operations return `Result<_, Box<dyn Error>>`. Caller mistakes are a typed `ConfigurationError`.
- There is no cancellation API. Drop the future to cancel a call, and use the `timeout` setting to bound one.
- `auth_headers()` is infallible, and `try_auth_headers()` is the variant that returns a `Result`.
- Rust has no overloads or default arguments, so variants carry their own names, such as `*_with_content_type`, `get_access_token_with_refresh_url` and `send_request_with_options`.
- `Configuration` and the builders implement `Default`.
- `ApiResult::into_data(self)` moves the payload out of the result.
- `CallHook` is one small hook around every operation call: `start(&CallInfo, &mut HashMap<String, String>)` runs once per call after the headers are final and may edit them, and returns an `Option<Box<dyn CallObserver>>` whose `finish(self: Box<Self>, &CallOutcome)` runs exactly once on every way the call can end. Set it with `ConfigurationBuilder::call_hook(Arc<dyn CallHook>)` or `Client::with_call_hook(hook)` (Rust has no client builder, so the client takes it as a consuming method); `call_hook::chain(vec![a, b])` combines hooks. `CallInfo` carries `operation_id` (as written in the spec), `method`, `url_template`, `url` (no query string or fragment) and `server_address`; `CallOutcome` carries `status_code` (`0` when no response arrived), the lower-cased `headers` (`None` when none arrived) and the `error` the caller receives, borrowed. There are no exceptions to throw, so a panic in `start` unwinds to the caller with nothing sent, and a panic in `finish` is swallowed. Cancellation is dropping the future, so a dropped call finishes with `CallCancelled`.
- `OpenTelemetryCallHook::new()` (global providers) or `with_providers(&tracer_provider, &meter_provider)`, behind the `opentelemetry` feature, is a `CallHook` that records one span (named with the operation id, kind client), the counter `http.client.request.count` (unit `{request}`) and the histogram `http.client.request.duration` (unit `s`) per call. It needs only the OpenTelemetry API; the application installs the SDK. It writes `traceparent` itself in the W3C format.
- Lint exclusions live in one place, the `[lints]` tables of `Cargo.toml`. Generated source files carry no `allow` attributes, and `cargo clippy -- -D warnings` is the gate.
- Testing your code: `<Group>ApiTrait` in the group file and `ClientTrait` in `client.rs`, re-exported from the crate root. The trait methods are `fn ... -> impl Future<Output = ...>` and not `async fn`, which keeps the `async_fn_in_trait` lint quiet; they are not required to be `Send`, because the per-operation server traits have no `Send + Sync` bound and requiring it would be a breaking change. No `async-trait`, no `dyn`. `ClientTrait` has one associated type per group so `Client` keeps its concrete group structs. The Rust gate compiles only on CI.

## Credentials per security scheme

`Credentials` holds one optional credential for each distinct security scheme of the API, set with a `with_<scheme>` method named after the scheme. `Client::with_credentials(host, credentials, transport_options)` builds a client from it. Each operation uses the credential of one of the schemes its specification requires that is set, the first in the order the generated operation lists them, and goes out unauthenticated when none is. A single call cannot pick another scheme: the Options struct carries no authenticator.

## Closing the client

`close()` is graceful and can be called more than once. A request already in flight finishes. A request started afterwards fails with `ConfigurationError::InvalidState("ApiClient has been closed and can no longer send requests")`. Only the transport the SDK built is released; a `reqwest::Client` you supplied is never closed. Cancel one call by dropping its future.

## Cookie parameters

A cookie parameter value is sent exactly as given when every character is a legal RFC 6265 cookie-octet. Otherwise the call fails with `ConfigurationError::InvalidArgument` before anything is sent. The SDK does not percent-encode the value, so encode a raw value yourself first.

## Models

Constructors and `Default` never validate. A wrong value or an unknown enum string fails only when a response or a payload is decoded.

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
    if let Some(api) = err.as_api_error() {
        println!("{} {:?} {:?}", api.status_code(), api.response_headers(), api.response_body());
    }
}
```

## Known limits

- An operation whose security requirement names two schemes sends both credentials; an alternative is used only when the client holds every scheme in it.

- An unformatted `integer` decodes into `i32`, so a larger value fails to decode. Declare `format: int64` where needed.
- An `integer` with no format is 32 bits, the same as `int32`, by decision D95 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)). A value outside the range fails the decode with a `SerializationError` and is never wrapped or truncated. Declare `format: int64` where a field needs more.
- `format: decimal` and `number` are `f64` and lose exactness.
- `Vec<u8>` bodies are in memory, so streaming large uploads or downloads is not possible.
- Typed maps (`HashMap`) do not keep key order, while `JsonValue` does.
- An optional nullable property cannot tell an absent key from an explicit `null`. Both are `None`.
- `allOf` with a discriminator is not implemented yet.
- SOCKS proxies are rejected when the client is built. Only `http://` and `https://` proxies are accepted.
- A number such as `1e400` that overflows is rejected, where several other SDKs accept it as infinity.
- Schema constraints (`pattern`, `minLength`, `maximum` and so on) are not enforced by the client.
- Callbacks and webhooks, XML bodies, Link Objects and mutual TLS are out of scope in every language.

## Under review

These are ideas from a type review in November 2026. None of them is decided, and none changes the generated SDK today.

- Widen an unformatted `integer` to `i64`, because OpenAPI treats it as unbounded. This would be decided together with the other languages.
- Exact decimals through `rust_decimal` or `bigdecimal`, with a serde adapter. This adds a dependency. The review advises against turning on `serde_json`'s `arbitrary_precision` feature globally.
- `url::Url` for `uri`. It is already in the dependency tree through `reqwest`, but it normalises on parse, so values would not round trip byte for byte.
- `#[non_exhaustive]` on generated enums, so a new server value is not a breaking change for callers who match on them.
- Edition 2024, which 1.85 already supports.
- `jiff` in place of chrono is not recommended by the review for now.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. `invoke_api` reads `Context::current()` once on its first poll and uses that value at every injection point, so a span attached on the caller's thread survives a move to another runtime thread; a caller may also use `FutureExt::with_context`.
