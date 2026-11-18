# Rust SDK vs progenitor: benchmark (issue #134)

## Method

All work is under /tmp/progenitor-audit. Nothing in the repository was changed; it was only read (the checkout used for reading generated Rust and docs/languages/rust.md is the ta7 worktree, the main checkout has no docs/ directory).

Tool: oxidecomputer/progenitor, cloned with `git clone --depth 1` to src/progenitor (HEAD 9471dec, 2026-11-17). Run for real in Docker `rust:1` (rustc/cargo 1.100.0) with `cargo install cargo-progenitor` -> "Installed package `cargo-progenitor v0.16.1`" (release build 3m07s). The crates it generates depend on progenitor-client 0.16.1, reqwest 0.13, edition 2024.

Fixture: src/spec/resources/specs/petstore/openapi.yaml copied to openapi.yaml (1632 lines, `openapi: 3.1.0`, 61 paths, 68 operations).

Steps and commands (scripts in the folder: run1.sh, mk30.sh, split.rb, perpath.sh, prune.rb, gen.sh, tree.sh; logs: run1.log, perpath.log, gen.log):

1. `cargo progenitor -i openapi.yaml -o out-31 -n petstore -v 0.1.0` on the 3.1 fixture. Verbatim: `gen fail: UnexpectedFormat("invalid version: 3.1.0")` then `Error: generation experienced errors`.
2. 3.0 copy openapi-3.0.yaml (mk30.sh, only these edits): `openapi: 3.1.0` -> `3.0.3`; deleted the lines `contentEncoding: base64`, `contentMediaType: application/octet-stream` (biometricChip) and `unevaluatedProperties: false` (StrictTag). `prefixItems` (GeoPoint) and the rest were left as is; progenitor did not complain.
3. Full 3.0 copy, positional and builder: both fail with `gen fail: UnexpectedFormat("unsupported style of query parameter DeepObject")`. progenitor stops at the first error, so I generated each of the 61 paths alone (split.rb, perpath.sh, perpath.log). 54 pass, 7 fail (table below).
4. openapi-pruned.yaml (prune.rb): the 3.0 copy minus the 7 failing paths. NOTE: this drops /pet/{petId} entirely (get, post, delete too), /pet/findByStatus, avatar, photos, certificate, documents and tag, so the pruned crate is a partial comparison, not the whole fixture.
5. Generated `--interface positional` and `--interface builder` into out-positional and out-builder (gen.sh), then `cargo build` of both in the container.

What failed or needed a workaround while running the tool:
- `cargo progenitor` formats with rustfmt. With stable rustfmt: `Unstable("wrap_comments, normalize_doc_attributes")` panic at cargo-progenitor main.rs:95, and with no rustfmt `NoRustfmt`. It only worked after installing nightly rustfmt and running with RUSTUP_TOOLCHAIN=nightly. The generator CLI therefore needs a nightly component.
- `cargo build` of both crates printed `Finished dev profile ... in 23.11s` (positional) and `25.42s` (builder) with no errors and no warnings in the filtered output, plus `error: rustc interrupted by SIGBUS, printing backtrace` which is Docker-VM noise (see the known Docker VMM wedge); the build still finished. Compile errors: none. These are dev-profile times with dependencies compiled in the same run on a shared cached volume, so they are indicative only.

Not verified: our crate was not rebuilt or timed here (its numbers come from reading the committed golden crate); no runtime requests were sent through either client; throughput vs #107 not measured; progenitor's CLI and httpmock generators and its Stream/websocket output were read in source only because the fixture has no `x-dropshot-*` extensions; progenitor release-profile build time not measured. Line counts compare unlike things (see table).

Per-path failures of progenitor 0.16.1 on our fixture (verbatim first line):

| Path | Error |
|---|---|
| /pet/findByStatus | `UnexpectedFormat("unsupported style of query parameter DeepObject")` |
| /pet/{petId} | `UnexpectedFormat("cookie parameters are not supported Cookie {...`  |
| /pet/{petId}/avatar | panic `not yet implemented: more media types than expected for setPetAvatar: 3` (method.rs:2057) |
| /pet/{petId}/photos | `UnexpectedFormat("unexpected content type: multipart/form-data")` |
| /pet/{petId}/certificate | `UnexpectedFormat("unexpected content type: multipart/form-data")` |
| /pet/{petId}/documents | panic `not yet implemented: more media types than expected for uploadPetDocument: 2` (method.rs:2057) |
| /pet/{petId}/tag/{tagName} | `UnexpectedFormat("unsupported style of query parameter PipeDelimited")` |

The 54 other paths generate, including text/plain, octet-stream, form-urlencoded, compressed and status routes. Of 61 paths, 7 (11%) are rejected, which is 7 of our operation groups; the 3.1 document is rejected outright.

## Result

Evidence is file:line in progenitor (src/progenitor/...) and in our golden crate (G = src/spec/resources/generated/rust).

| Axis | progenitor 0.16.1 | Ours | Verdict | Evidence |
|---|---|---|---|---|
| Client shape | Two styles chosen by the build: positional (`client.place_order(&body)`) or builder (`client.place_order().body(b).send()`), `InterfaceStyle` lib.rs:90; `--interface` flag. Single `Client` struct, optional tags-as-modules (`TagStyle` lib.rs:105). Generated lib.rs: 3124 lines positional, 7382 builder (pruned fixture, 54 paths) | One fixed shape: per-tag API structs (`PetApi`...), optional `&Options` last arg, `_with_http_info` variants | decline | owner decision "one fixed shape for twelve languages"; G src/api/pet_api.rs:999,1023 |
| Typed errors per response | `Error<E>` generic over ONE error type per operation (`Error<()>` or `Error<Map<..>>` in the output, no per-status types), variants InvalidRequest, CommunicationError, ErrorResponse, ResponseBodyError, InvalidResponsePayload, UnexpectedResponse(raw response), InvalidUpgrade, progenitor_client.rs:332-353; `is_retryable()` 385. Returned as values (Result) | Per-status error structs (BadRequestError, ConflictError, ...) with ApiError base, returned as `Box<dyn Error>` and downcast. Typed errors thrown, not returned as values | already-covered (per-status is ahead); shape of return type tracked in #113/#123 | G src/errors/*.rs, G src/errors/api_error.rs:95; progenitor_client.rs:332 |
| Error classification helper | `Error::is_retryable()` (408, 429, 502, 503, 504, communication errors) | `ApiErrorKind` enum and status accessors | recipe-only (docs: how to decide retry on an error) | progenitor_client.rs:377-399; G src/errors/api_error.rs:729 |
| Pagination | Only for Dropshot `x-dropshot-pagination` operations (needs `page_token`/`limit`), emits a `futures::Stream` method (method.rs:636-760) | None generated. Spec has `Link` header only (petstore openapi.yaml:1890) | decline (vendor extension; templates never read vendor extensions) | method.rs:1237 reads `x-dropshot-pagination` |
| Streaming and byte bodies | `application/octet-stream` response is `ByteStream` (a `Stream<Item=Result<Bytes>>`), request `Body` accepted (progenitor_client.rs:15-47, 209; method.rs:588,2062). Request bodies only json, form-urlencoded, text/plain, octet-stream (method.rs:135-158) | `Vec<u8>` fully in memory, no streaming (docs/languages/rust.md "Known limits") | defer to #114 (streaming, owner decision per language) | #114 |
| Multipart and multiple media types | Not supported: `unexpected content type: multipart/form-data`; multiple request media types panic `not yet implemented` (method.rs:2057) | multipart, per-call content type, `_with_content_type` variants | already-covered (we are ahead) | perpath.log; G src/api/pet_api.rs:1027 |
| Server-Sent Events | None (no `text/event-stream` in src) | None | defer to #72 | grep of progenitor-impl/src |
| WebSocket | Dropshot channel endpoints: `x-dropshot-websocket` returns `reqwest::Upgraded` (method.rs:407,278; progenitor_client.rs:188-205) | None | decline (vendor extension and not a client-SDK feature across twelve languages) | method.rs:407 |
| Authentication and security schemes | None generated. No `security` handling anywhere in progenitor-impl (grep returns nothing); the caller supplies a configured `reqwest::Client` (README, docs/implementing-client.md) | Authenticators per scheme (bearer, basic, api key in header/query/cookie, OAuth2 flows), env defaults, `Credentials` | already-covered (we are far ahead) | G src/auth/*.rs |
| Retries | None built in; only `is_retryable()` helper | `max_retries` default 2 with backoff, jitter, Retry-After; per call `max_retries` | already-covered | G src/transport_options.rs:102-105,254; progenitor_client.rs:385 |
| Timeouts | Fixed 15 s connect and total on the client built by `new`, `Generator::with_timeout(u64)` (lib.rs:297, 440, 531-532); none per call; caller's client otherwise | end-to-end timeout, per-call `timeout` option | already-covered; per-call timeout also #116 | G src/transport_options.rs:57-63 |
| Proxy and TLS | None in generator; caller's `reqwest::Client` | `proxy`, `verify_ssl`, `ca_cert_path` options; SOCKS rejected | already-covered; SOCKS is a known limit | G src/transport_options.rs:36-55,294-309 |
| Request and response hooks | `pre_hook`, `post_hook` and async variants plus `inner_type` set on the Generator (lib.rs:157-204); generated code calls `self.pre()`, `self.exec()`, `self.post()` with `OperationInfo{operation_id}` (out-positional lib.rs:819-1330, `ClientHooks` trait progenitor_client.rs:106-150). Build time TokenStreams only, in build.rs/macro use; in CLI output the hooks default to no-ops | Injectable `ApiClient` trait (api_client.rs:91); no per-call hook | adopt (one hook around every call, all twelve languages): already issue #158 / #140 recipe | G src/api_client.rs:91; #108, #158, #140 |
| OpenTelemetry | None | Optional `opentelemetry` Cargo feature, W3C trace context injection | already-covered (#108/#136 for spans) | G Cargo.toml:63; G src/trace_context_util.rs:12 |
| Optional and nullable | `Option<T>` with `skip_serializing_if`; nullable and absent not distinguished (typify) ; `default` values become `defaults::` fns | `Option<T>`; required nullable has adapter; absent vs null not distinguished (known limit) | already-covered (same limit) | out-builder lib.rs:231-251; docs/languages/rust.md "Known limits" |
| Enums, oneOf, allOf, anyOf | typify: native enums, `#[serde(untagged)]` for oneOf/anyOf (out-builder lib.rs:395,505,703), allOf merged into structs; per-type derive/patch/replacement/conversion settings (lib.rs:209-250) | enums; oneOf with hand-written deserialisation; anyOf struct of Options; allOf+discriminator not implemented (#104) | already-covered; allOf discriminator #104 | docs/languages/rust.md |
| additionalProperties | typed `HashMap<String,T>` (map type configurable, lib.rs:285-300) | typed `HashMap` | already-covered; typing gaps #103 | G docs |
| date-time, uuid | `chrono::DateTime<Utc>` (offset lost), `uuid::Uuid` by typify; date as `NaiveDate` | `crate::date_time::DateTime` keeping offset, `uuid::Uuid`, `NaiveDate` | already-covered (we keep the offset); typed dates #126 | out-builder lib.rs:247; docs/languages/rust.md |
| Numeric formats | `i32`, `i64`, `f32`, `f64` by format; unformatted integer maps to i64 (NOT verified in output, from typify knowledge) | unformatted integer is `i32` (known limit), decimal is lossy `f64` | adopt-in-subset: #157 for decimals; widening i64 is "Under review" in docs, defer to owner | docs/languages/rust.md "Known limits" and "Under review" |
| Path and query styles | `deepObject`, `pipeDelimited` (and others) rejected; cookie parameters rejected (method.rs:394-400) | styles and cookie parameters handled | already-covered (we are ahead) | perpath.log; #109 |
| CLI and httpmock generators | `Generator::cli` (cli.rs 759 lines) and `httpmock` (httpmock.rs 415 lines), build.rs only, not from `cargo progenitor` (README:109-110) | none | decline for CLI (not client SDK); httpmock mirrors #122 (mockable client): defer to #122 | cli.rs, httpmock.rs; #122 |
| OpenAPI versions | 3.0.x only: `invalid version: 3.1.0` (README:4); uses the `openapiv3` crate | 3.0, 3.1 (#96), 3.2 planned (#49) | already-covered (ahead); keep 3.0-only error out | run1.log |
| Generator integration | proc macro `generate_api!`, build.rs, or `cargo progenitor`; ties the build to progenitor and nightly rustfmt for CLI | Maven generator producing a crate | decline | README:34-81, run gen.log |
| Compile time and size | pruned fixture (54 paths): 3124 lines positional, 7382 builder lib.rs; dev build 23-25 s | full fixture (68 operations, models, auth, tests excluded): 33459 lines in src/ over many files | n/a; sizes not like for like (we ship auth, errors, retries, transport, docs; progenitor ships none) | wc -l; gen.log |
| Dependencies | 108 unique crates in `cargo tree` (both styles) with reqwest 0.13 (json, query, stream; no TLS feature selected) | reqwest 0.12 + tokio, chrono, uuid, base64, flate2, brotli, zstd, sha2, regex, encoding_rs, opentelemetry (optional) | defer: reqwest 0.12 vs 0.13 is an owner decision | out-builder/Cargo.toml; docs/languages/rust.md |

## Findings worth an issue

Existing issues cover most of this; none of the points below needs a new issue, and anything shared goes into #131 rather than #134:

1. Hooks around every call: progenitor ships pre/post/exec hooks with the operation id (progenitor_client.rs:106-150, generated out-positional/src/lib.rs:819). Link to #158 and #108; the recipe is #140. No duplicate.
2. Typed errors: progenitor has one error type per operation and returns it as a value; ours is per status but boxed. Link to #113/#123; nothing new.
3. Streaming bodies: progenitor has `ByteStream` for octet-stream responses; ours holds `Vec<u8>`. Link #114.
4. Mockable client: progenitor's httpmock generator and our `ApiClient` trait both point at #122.
5. Rust-only defect candidate: an unformatted `integer` decodes to i32 while progenitor probably maps it to i64 (unverified; OpenAPI integers are unbounded). It is in docs "Known limits" and "Under review" but no open issue names it. If the owner wants it, it needs a new issue (all twelve languages, add to #131); otherwise it stays documented.
6. progenitor rejects our fixture in several places (3.1, deepObject, pipeDelimited, cookie parameters, multipart, multiple media types): worth recording in docs/comparisons/progenitor.md as the "why not progenitor" evidence for #134.

Close #134 once docs/comparisons/progenitor.md is committed from this report; it is a documentation task, not a code change.
