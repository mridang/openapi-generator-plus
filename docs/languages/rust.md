# Rust

The `rust-plus` generator (`BetterRustCodegen`) produces an async Rust crate with one module per API tag, one struct or enum per model, an `ApiClient` trait with a `DefaultApiClient`, authenticators, typed errors and a test suite. It uses `reqwest` on `tokio`, with `serde` for JSON. For the rules shared by all twelve languages see [how it works](../how-it-works.md), for the type table across languages see [types](../types.md), and for settled cross-language choices see [decisions](../decisions.md).

## Versions and support policy

The generated `Cargo.toml` sets `edition = "2021"` and `rust-version = "1.85"`, and the generated README says Rust 1.85 or newer. Rust has no end-of-life policy, only a current stable release, so 1.85 is a chosen minimum supported Rust version rather than a support window. It is kept as long as it is not newer than the oldest release that is still supported elsewhere. `clippy.toml` carries the same `msrv`. The generator builds and checks the golden crate in a Rust 1.96 toolchain image.

The crate forbids `unsafe` code (`unsafe_code = "forbid"`).

## Type mapping

| OpenAPI type and format | Rust type | Notes |
|---|---|---|
| `string` | `String` | Also used for `email`, `hostname`, `ipv4`, `ipv6`, `password` and unknown formats. |
| `string` / `date` | `chrono::NaiveDate` | |
| `string` / `date-time` | `crate::date_time::DateTime` | An SDK newtype over `chrono::DateTime<FixedOffset>`. The offset is kept. The wire form has a numeric offset (never `Z`) and a fixed three-digit millisecond fraction. |
| `string` / `time` | `chrono::NaiveTime` | A time string with a zone offset does not round trip. |
| `string` / `duration` | `chrono::Duration` | Serialised through `crate::proto_duration` as the protobuf-JSON string such as `"3600s"`, not ISO 8601. |
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
| `object` with `additionalProperties` | `std::collections::HashMap<String, T>` | |
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
| `opentelemetry` | 0.32, optional | Trace-context propagation, behind the `opentelemetry` feature |

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

## Idiom notes

- Operations return `Result<_, Box<dyn Error>>`. Caller mistakes are a typed `ConfigurationError`.
- There is no cancellation API. Drop the future to cancel a call, and use the `timeout` setting to bound one.
- `auth_headers()` is infallible, and `try_auth_headers()` is the variant that returns a `Result`.
- Rust has no overloads or default arguments, so variants carry their own names, such as `*_with_content_type`, `get_access_token_with_refresh_url` and `send_request_with_options`.
- `Configuration` and the builders implement `Default`.
- `ApiResult::into_data(self)` moves the payload out of the result.
- Lint exclusions live in one place, the `[lints]` tables of `Cargo.toml`. Generated source files carry no `allow` attributes, and `cargo clippy -- -D warnings` is the gate.

## Known limits

- An unformatted `integer` decodes into `i32`, so a larger value fails to decode. Declare `format: int64` where needed.
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

- Map `ipv4` and `ipv6` to `std::net::Ipv4Addr` and `Ipv6Addr`. They are in the standard library, so there is no new dependency or floor change, but it needs a decision on how strict to be with server input.
- Widen an unformatted `integer` to `i64`, because OpenAPI treats it as unbounded. This would be decided together with the other languages.
- Exact decimals through `rust_decimal` or `bigdecimal`, with a serde adapter. This adds a dependency. The review advises against turning on `serde_json`'s `arbitrary_precision` feature globally.
- Use the current chrono name `chrono::TimeDelta` for durations. It is the same type that `chrono::Duration` aliases.
- `url::Url` for `uri`. It is already in the dependency tree through `reqwest`, but it normalises on parse, so values would not round trip byte for byte.
- `#[non_exhaustive]` on generated enums, so a new server value is not a breaking change for callers who match on them.
- Edition 2024, which 1.85 already supports.
- `jiff` in place of chrono is not recommended by the review for now.
