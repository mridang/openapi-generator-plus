# Rust SDK vs progenitor, second pass (issue #134)

Source-level pass. Read-only on the repository. Everything is under /tmp/progenitor-audit2. This pass adds a real run against the official Swagger Petstore 3.0 and a faithful 3.0 version of our fixture, observed runtime behaviour, and a line-by-line read of progenitor 0.16.1. Corrections to the first pass are in the last section.

## Method and commands

- Tool: `cargo-progenitor 0.16.1` from the persistent Docker volume progenitor-bin, in container `rust:1` (cargo 1.100.0), `RUSTUP_TOOLCHAIN=nightly` (nightly rustfmt installed once per container). Container kept as `pg2` with /tmp/progenitor-audit2 mounted at /work. Cargo targets go to /tmp/tgt-* inside the container because building into the bind mount fails with `Bad address (os error 14)`.
- Line numbers below are from the PUBLISHED crates the binary was built from, copied to /tmp/progenitor-audit2/crates/{progenitor-impl-0.16.1,progenitor-client-0.16.1}/src. The git checkout (9471dec) is nine lines off (its todo! is at method.rs:2048, the published one at 2057), so cite the published numbers.
- Official petstore: `curl -sL https://petstore3.swagger.io/api/v3/openapi.json -o petstore3.json` (`openapi: 3.0.4`, 19 operations).
- Our fixture to 3.0: `ruby to30.rb fixture31.yaml fixture30.yaml`. The 3.1 fixture turned out to use few 3.1-only constructs, so the script (a recursive rewriter that also handles type arrays with null, const, schema `examples`) logged only: `remove contentEncoding: 1`, `remove contentMediaType: 1`, `remove unevaluatedProperties: 1`, `prefixItems->items:{}: 1` (3.0 has no tuple; mirrors what our own normaliser does). Type arrays, const and schema-level examples: 0 occurrences. All 61 paths kept. Header `openapi: 3.0.3`.
- Per path: `ruby split.rb fixture30.yaml perpath` then `perpath.sh` (one spec per path, `cargo progenitor -i N.yaml -o /tmp/o-N`). Per operation for the failing paths: `ops.rb` + `perop.sh`. A first attempt of perop wrongly reported OK because /tmp/o-NN from the previous run still existed; it was rerun after `rm -rf /tmp/o-*` (results below are from the rerun). Official petstore per operation: `ops2.rb` + `ps3op.sh`.
- Salvage spec: `salvage.rb` removes only the 7 failing operations (not whole paths), 56 paths and 61 operations remain: `fixture30-salvage.yaml`. Generated `--interface positional` and `--interface builder` (`gen.sh`), built with `cargo build`, then `cargo clippy` (`build.sh`, `clippy.sh`).
- Runtime: tests/runtime.rs in each generated crate (copies: runtime_builder.rs, runtime_positional.rs; outputs runtime_builder.out, runtime_positional.out). A `std::net::TcpListener` thread answers: 400 with a JSON body, 404 with a JSON body, 503 with a JSON body, 200, and a 17 s sleep.
- Typify behaviour: `types30.yaml` (oneOf+discriminator, anyOf, allOf, enums, nullable, defaults, constraints, formats), generated to out-types/src/lib.rs (401 lines).
- Panic probes: pan/*.yaml via `pan.sh` and mkpan.rb.
- Existing issues: `gh issue list --repo mridang/openapi-generator-plus --state open --limit 250` (101 open, saved in issues.txt).

## Part A results

### A1. The official Swagger Petstore 3.0.4 document

Whole document, positional and builder: both fail with a PANIC, not an error:

```
thread 'main' panicked at .../progenitor-impl-0.16.1/src/method.rs:2057:23:
not yet implemented: more media types than expected for updatePet: 3
```

Per operation (ps3op.log): 4 of 19 operations generate (DELETE /pet/{petId}, DELETE /store/order/{orderId}, GET /user/logout, DELETE /user/{username}), 15 fail: 10 with `assertion failed: response_types.len() <= 1` (method.rs:1220) and 5 with `not yet implemented: more media types than expected` (method.rs:2057: updatePet, addPet, placeOrder, createUser, updateUser).

Why the assertion fires: the petstore declares `200` with a JSON body plus `default: description: Unexpected error` with no content. `OperationResponseStatus::Default` counts as both a success and an error response (method.rs:222-232 is_success_or_default), so the success set holds {typed, none} and `assert!(response_types.len() <= 1)` fails (method.rs:1220). Confirmed experimentally: removing the `default` responses (petstore3-default.json) moves the failures to the media-type panic; removing the extra request media types (petstore3-media.json) leaves the assertion; removing both (petstore3-both.json) generates 1226 lines, builds, and `cargo clippy` is clean (ps3fix.log). The two defects block the reference document independently.

### A2. Our fixture as 3.0: per path, all 61 paths

Result: 54 of 61 paths generate, 7 fail (same as pass one). Per operation, 7 of 68 operations fail and 61 generate.

Failing operations (perop.log, after the rerun):

| Operation | Verbatim first line |
|---|---|
| GET /pet/findByStatus | `gen fail: UnexpectedFormat("unsupported style of query parameter DeepObject")` |
| DELETE /pet/{petId} | `gen fail: UnexpectedFormat("cookie parameters are not supported Cookie { ...` |
| PUT /pet/{petId}/avatar | panic `not yet implemented: more media types than expected for setPetAvatar: 3` (method.rs:2057) |
| POST /pet/{petId}/photos | `UnexpectedFormat("unexpected content type: multipart/form-data")` |
| POST /pet/{petId}/certificate | `UnexpectedFormat("unexpected content type: multipart/form-data")` |
| POST /pet/{petId}/documents | panic `not yet implemented: more media types than expected for uploadPetDocument: 2` |
| GET /pet/{petId}/tag/{tagName} | `UnexpectedFormat("unsupported style of query parameter PipeDelimited")` |

7 of 68 operations fail and 61 generate. GET and PUT on /pet/{petId} and GET on avatar generate when alone; /pet/{petId} failed as a path only because of its DELETE.

Per path table (perpath.log):

| Path | progenitor | First error |
|---|---|---|
| /pet | generates |  |
| /pet/findByStatus | FAILS | gen fail: UnexpectedFormat("unsupported style of query parameter DeepObject") |
| /pet/{petId} | FAILS | gen fail: UnexpectedFormat("cookie parameters are not supported Cookie {\n    parameter_data: ParameterData {\ |
| /pet/{petId}/avatar | FAILS | panic method.rs:2057 not yet implemented: more media types than expected for setPetAvatar: 3 |
| /pet/{petId}/avatar/thumbnail | generates |  |
| /pet/{petId}/photos | FAILS | gen fail: UnexpectedFormat("unexpected content type: multipart/form-data") |
| /pet/{petId}/photos/{photoId} | generates |  |
| /pet/{petId}/certificate | FAILS | gen fail: UnexpectedFormat("unexpected content type: multipart/form-data") |
| /pet/{petId}/preferences | generates |  |
| /pet/{petId}/documents | FAILS | panic method.rs:2057 not yet implemented: more media types than expected for uploadPetDocument: 2 |
| /pet/{petId}/documents/{documentId} | generates |  |
| /pet/{petId}/passport | generates |  |
| /pet/{petId}/treatment | generates |  |
| /pet/{petId}/tag/{tagName} | FAILS | gen fail: UnexpectedFormat("unsupported style of query parameter PipeDelimited") |
| /pet/byName/{name} | generates |  |
| /pet/{petId}/external | generates |  |
| /pet/{petId}/staging | generates |  |
| /pet/{petId}/multi | generates |  |
| /store/inventory | generates |  |
| /store/order | generates |  |
| /store/order/{orderId} | generates |  |
| /test/echo | generates |  |
| /test/slow | generates |  |
| /test/compressed/gzip | generates |  |
| /test/compressed/br | generates |  |
| /test/compressed/zstd | generates |  |
| /test/status/400 | generates |  |
| /test/status/401 | generates |  |
| /test/status/403 | generates |  |
| /test/status/404 | generates |  |
| /test/status/409 | generates |  |
| /test/status/418 | generates |  |
| /test/status/422 | generates |  |
| /test/status/500 | generates |  |
| /test/status/502 | generates |  |
| /test/status/400/pet/{petId} | generates |  |
| /test/status/401/pet/{petId} | generates |  |
| /test/status/403/pet/{petId} | generates |  |
| /test/status/404/pet/{petId} | generates |  |
| /test/status/409/pet/{petId} | generates |  |
| /test/status/418/pet/{petId} | generates |  |
| /test/status/422/pet/{petId} | generates |  |
| /test/status/500/pet/{petId} | generates |  |
| /test/status/502/pet/{petId} | generates |  |
| /test/status/400/store/order/{orderId} | generates |  |
| /test/status/401/store/order/{orderId} | generates |  |
| /test/status/403/store/order/{orderId} | generates |  |
| /test/status/404/store/order/{orderId} | generates |  |
| /test/status/409/store/order/{orderId} | generates |  |
| /test/status/418/store/order/{orderId} | generates |  |
| /test/status/422/store/order/{orderId} | generates |  |
| /test/status/500/store/order/{orderId} | generates |  |
| /test/status/502/store/order/{orderId} | generates |  |
| /test/redirect/302 | generates |  |
| /test/redirect/303 | generates |  |
| /test/redirect/307 | generates |  |
| /test/redirect/307-multipart | generates |  |
| /test/response-headers | generates |  |
| /test/set-cookie | generates |  |
| /test/text-plain | generates |  |
| /test/empty | generates |  |

### A3. Generated crates (salvage spec, 61 operations)

| | positional | builder |
|---|---|---|
| lib.rs lines | 3246 | 7656 |
| `cargo build` (dev, warm registry) | Finished in 6.76 s | Finished in 7.71 s |
| `cargo clippy` | no warnings, no errors | no warnings, no errors |
| files | one src/lib.rs | one src/lib.rs |

Our crate for the full fixture is 33459 lines in src/ (first pass); not like for like. Official petstore after the double workaround (petstore3-both.json): 1226 lines, builds, clippy clean. Generated crate Cargo.toml: bytes, chrono (no default features), futures-core, progenitor-client 0.16.1, reqwest 0.13 (default-features off; json, query, stream), serde, serde_json, serde_urlencoded, edition 2024, `license = "SPECIFY A LICENSE BEFORE PUBLISHING"`. No tests, no README, no docs are generated.

### A4. Observed runtime behaviour (verbatim, builder crate; the positional crate printed the same text)

Server: loopback TcpListener. The client is `petstore::Client::new(url)`.

```

--- 400 with JSON body on an op that declares 400 with a schema (test_status400)
Display: Error Response: status: 400 Bad Request; headers: {"content-type": "application/json", "content-length": "34", "connection": "close"}; value: {"code": Number(400), "message": String("bad thing")}
Debug  : Error Response: status: 400 Bad Request; headers: {"content-type": "application/json", "content-length": "34", "connection": "close"}; value: {"code": Number(400), "message": String("bad thing")}
variant ErrorResponse status=400 Bad Request headers={"content-type": "application/json", "content-length": "34", "connection": "close"} body={"code": Number(400), "message": String("bad thing")}
status(): Some(400) is_retryable: false
--- 404 with JSON body on an op that declares 404 without content (get_pet_by_id 404)
Display: Error Response: status: 404 Not Found; headers: {"content-type": "application/json", "content-length": "36", "connection": "close"}; value: ()
Debug  : Error Response: status: 404 Not Found; headers: {"content-type": "application/json", "content-length": "36", "connection": "close"}; value: ()
status(): Some(404) is_retryable: false
--- 503 (undeclared), twice, to look for retries
Display: Unexpected Response: Response { url: "http://127.0.0.1:36177/pet/503", status: 503, headers: {"content-type": "application/json", "content-length": "34", "connection": "close"} }
Debug  : Unexpected Response: Response { url: "http://127.0.0.1:36177/pet/503", status: 503, headers: {"content-type": "application/json", "content-length": "34", "connection": "close"} }
status(): Some(503) is_retryable: true
UnexpectedResponse raw: status=503 Service Unavailable body={"code":503,"message":"try later"}
requests the server saw for one 503 call: 1
--- success 200 (get_pet_by_id 7)
status=200 OK headers={"content-type": "application/json", "content-length": "57", "connection": "close"} body=Pet { category: None, homepage_url: None, id: Some(7), link_template: None, location: None, name: "rex", owner_email: None, photo_urls: [], status: Some(Available), tags: [], thumbnail_ref: None, weight_kg: None }
--- builder with missing required argument
Err(Invalid Request: pet_id was not initialized)
--- builder with wrong arg type conversion (u64 too large)
Err(Invalid Request: conversion to `i64` for pet_id failed)
--- connection refused
Display: Communication Error: error sending request for url (http://127.0.0.1:1/pet/1)
Debug: Communication Error: error sending request for url (http://127.0.0.1:1/pet/1)
is_retryable: true
--- slow response (server sleeps 17 s; default client timeout set by Client::new)
Err after 15.006670007s: Display: Communication Error: error sending request for url (http://127.0.0.1:36177/test/slow)
Debug: Communication Error: error sending request for url (http://127.0.0.1:36177/test/slow)
is_retryable: true
--- raw requests received by the server (headers)
[0] GET /test/status/400 HTTP/1.1 | api-version: 1.0.0 | accept: */* | host: 127.0.0.1:36177 |  | 
[1] GET /pet/404 HTTP/1.1 | accept: application/json | api-version: 1.0.0 | host: 127.0.0.1:36177 |  | 
[2] GET /pet/503 HTTP/1.1 | accept: application/json | api-version: 1.0.0 | host: 127.0.0.1:36177 |  | 
[3] GET /pet/7 HTTP/1.1 | accept: application/json | api-version: 1.0.0 | host: 127.0.0.1:36177 |  | 
[4] GET /test/slow HTTP/1.1 | accept: application/json | api-version: 1.0.0 | host: 127.0.0.1:36177 |  | 
test observe ... ok

test result: ok. 1 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out; finished in 15.01s

```

What this shows:
- A response the spec declares WITH a schema (400 on test_status400) arrives as `Error::ErrorResponse(ResponseValue<Map<..>>)` with status, headers and the decoded body. Display and Debug are identical.
- A response declared WITHOUT content (404 on get_pet_by_id) arrives as `ErrorResponse(ResponseValue<()>)`: the server's JSON body is DISCARDED (`value: ()`).
- A status the spec does not list (503) arrives as `Error::UnexpectedResponse(reqwest::Response)`: the raw response is kept, the body can still be read (`body={"code":503,...}`).
- No retry: one 503 call produced exactly one server request (`requests the server saw for one 503 call: 1`). `is_retryable()` returns true for 503 and for connection errors, but nothing uses it.
- Timeout: `Client::new` builds a reqwest client with connect and total timeout of 15 s (lib.rs:2646 of the crate); the 17 s response failed after 15.0067 s as `Communication Error: error sending request for url (...)`. The Display text does not say "timed out" (the cause is in `source()`; `{:#}` prints the chain, see progenitor_client.rs:484-491).
- Headers sent: `api-version: 1.0.0` (from `info.version`, on every request), `accept: application/json` when the response is typed, `accept: */*` otherwise, `host`. NO User-Agent header at all (reqwest 0.13 sets none by default and the generator does not). No auth header.
- Client-side argument errors are `Error::InvalidRequest` and are produced by the builder: `Invalid Request: pet_id was not initialized`; `conversion to `i64` for pet_id failed` (u64::MAX). This is a client-side check of required arguments, not of schema constraints.
- Not observed: a 503 followed by 200 (the server answered 503 every time; the single request seen proves no retry); redirects, compression, cookies.

## Part B findings (published 0.16.1, file:line)

### B1. method.rs: every panic, todo!, assert and unwrap on spec input

| Where | Trigger | Observed |
|---|---|---|
| method.rs:1220 `assert!(response_types.len() <= 1)` (TODO comment at 1217-1219: "to deal with multiple response types, we'll need to create an enum") | success set or error set holds two different response kinds: `default` without body next to a typed 200; 200 and 201 with different schemas; 400 and 404 with different schemas | PANIC, observed on official petstore (10 of 19 operations), pan/4 and pan/7 |
| method.rs:2057 `todo!("more media types than expected for {}: {}")` | request body with 2 or more media types | PANIC, observed (5 petstore operations; our avatar and documents) |
| method.rs:477 `todo!("media type encoding, no schema")` | application/json response with a media type object that has no schema | PANIC, observed (pan/2) |
| method.rs:467 `assert!(mt.encoding.is_empty())` | JSON response media type with an `encoding` map | not run |
| method.rs:2148 `todo!("media type encoding not empty")` | request body media type with `encoding` | not run |
| method.rs:1023 `todo!("non-default error response handling for upgrade requests")` | a non-default error response on a websocket (101) operation | not run, vendor extension path |
| method.rs:313 `assert!(parameter_data.required)` | path parameter with `required: false` | not run |
| method.rs:210, 214 `assert!(*code < 1000)`, `assert!(*range < 10)` | odd status codes | not run |
| method.rs:937 `assert!(body_func.clone().count() <= 1)`, 2308 `panic!("should only be one body")`, 1591 `assert_eq!(param.name, "body")`, 594/596/931/1541/1655 `unreachable!` | internal invariants | not run |
| method.rs:294 `operation.operation_id.as_ref().unwrap()` | missing operationId | guarded by lib.rs:780, which returns `UnexpectedFormat("path ... is missing operation ID")` (observed, pan/1): an Error, not a panic |
| method.rs:2272, 2276 `unwrap_or_else(panic!("{} missing from path"))` | a `{x}` in the path without a parameter | not run |
| lib.rs:400 `unimplemented!("positional arguments with separate tags are currently unsupported")` | `--interface positional` with `TagStyle::Separate` | read only |
| lib.rs:383 `ref_or_item.as_item().unwrap()` | path item `$ref` | guarded by lib.rs:764 (error "uses reference, unsupported") |
| util.rs:23-28 `rfind('/').unwrap()`, `components.as_ref().unwrap()`, `panic!("key {} is missing")` | `$ref` to a missing component, or a `$ref` with no components | not run |

Errors (not panics): query styles other than form (method.rs:397), path styles other than simple (394), cookie parameters (400), header styles other than simple, parameters with `content` instead of `schema` (2329), content types other than json, urlencoded, octet-stream, text/plain and text/x-markdown (method.rs:140-155), spec version not `3.0.*` (lib.rs:746-753; the 3.0.4 official petstore passes), duplicate or missing operationId (lib.rs:773-783), path-item `$ref` (lib.rs:764).

Parameter handling: query parameters are `style: form` only, always encoded with reserved characters percent-encoded (method.rs:332 `allow_reserved: _`), arrays serialised by the custom `QuerySerializer`/`QueryParam` (progenitor_client.rs:600-700) as repeated names. A query parameter whose type is already `Option` is treated as optional whatever `required` says (method.rs:350-360). Header parameters: simple style only. Path parameter names are snake-cased and de-duplicated per operation (method.rs:316-330). A non-required request body is not supported: `OperationParameterKind::Body(_) => true` with `// TODO may be optional` (method.rs:126-127), so every request body is mandatory.

Content types: request body: json, form-urlencoded, text/plain or markdown (as String), octet-stream (only the exact schema `type: string, format: binary`, else `UnexpectedFormat("invalid schema for application/octet-stream")`, method.rs:2095-2105). Response: only `application/json` or `application/json;...` is typed (method.rs:462-463); any other content type, including xml, text/plain and PDF, becomes `ByteStream` ("Raw"); no content is `()`. If no success response is declared, a raw 2xx is invented (method.rs:520-526). A response media type is never chosen by the caller (no Accept negotiation; `accept` is `application/json` when the first typed success is JSON, observed).

Response handling: the status match is generated per declared status. 2xx declared `ResponseValue::from_response` (buffers the whole body, `response.bytes()`, then `serde_json::from_slice`, progenitor_client.rs:167-172); a decode failure is `Error::InvalidResponsePayload(Bytes, serde_json::Error)`, which KEEPS the raw bytes, but not the status or headers. Declared errors with a schema are `ErrorResponse(ResponseValue<E>)`; undeclared statuses are `UnexpectedResponse(reqwest::Response)` (the raw response, body unread). Hooks: `pre`, `exec`, `post` on every call (generated at method.rs:1100-1112).

### B2. lib.rs settings (Generator)

`InterfaceStyle` Positional or Builder (lib.rs:138), `TagStyle` Merged or Separate (144; Separate is unimplemented for positional, lib.rs:400), `HooksMode` and `inner_type` (151-157), build-time token hooks `with_pre_hook`, `with_post_hook` and async variants (167-203), `with_derive`, `with_patch`, `with_replacement`, `with_conversion` (209-250: per-type serde/derive customisation by Rust type name), `with_unknown_crates`, `with_crate` (259-285, via `x-rust-type`), `with_map_type` (291), `with_timeout` (297, default 15 s). Everything is Rust-specific build-time configuration; nothing here has a twelve-language analogue except hooks and timeout.

### B3. progenitor-client

- `Error<E>` (progenitor_client.rs:332-353): InvalidRequest(String), CommunicationError(reqwest::Error), InvalidUpgrade, ErrorResponse(ResponseValue<E>), ResponseBodyError, InvalidResponsePayload(Bytes, serde_json::Error), UnexpectedResponse(reqwest::Response), Custom(String) for hook errors. `status()` (361), `is_retryable()` (385: 408, 429, 502, 503, 504 and communication errors), `into_untyped()` (402). Implements `std::error::Error` with `source()` (532-545) and, with `{:#}`, appends the cause chain (484-491). Returned as `Result`, never thrown. One `E` per operation, so no per-status types (and APIs with different error schemas per status cannot be generated at all, B1).
- `ResponseValue<T>` (167): inner value, status, headers, `Deref`, `content_length()`, `into_inner()`, constructor `new()` for test fixtures (195). Always carries status and headers on success.
- `ByteStream` (15-47): boxed `Stream<Item = reqwest::Result<Bytes>>`, `Send + Sync`; used for every non-JSON response (observed: `ResponseValue<ByteStream>` for 200 with no declared content type).
- Hooks (ClientHooks, 106-150): `pre(&mut Request, &OperationInfo)`, `post(&Result<Response>, &OperationInfo)`, `exec(Request, &OperationInfo)`; all default to no-ops; overriding is done at RUNTIME by implementing the trait for `Client` (not `&Client`), using auto-ref specialisation (doc at 96-100). `OperationInfo` has only `operation_id` (59-63). `exec` is where a user would add retry. This corrects pass one, which called the hooks build-time only.
- reqwest usage: `reqwest::Client` pass-through (`new_with_client`), all HTTP-level configuration (TLS, proxy, user agent, retries, auth, compression) is the caller's job; the crate is wasm-aware (`#[cfg(target_arch = "wasm32")]`, upgrade not available there).
- Header handling: only `api-version` (from `info.version`), `Accept`, header parameters, and `Content-Type` for form bodies (RequestBuilderExt, 581-597). `encode_path` percent-encodes with the WHATWG path set (565-578).
- Pagination: dropshot only (method.rs:1237 `x-dropshot-pagination`).

### B4. typify usage (observed in out-types/src/lib.rs, typify 0.8.0)

| Schema | Output | Limit |
|---|---|---|
| oneOf with `discriminator.mapping` | `#[serde(untagged)] enum Pet { Cat(Cat), Dog(Dog) }` | the discriminator is IGNORED; first matching variant wins silently |
| anyOf | `#[serde(untagged)] enum Either { Cat(Cat), String(String) }` | same |
| allOf | flattened into one struct (`Derived` has `id` and `name`) | no discriminator inheritance |
| string enum | enum with `Display`, `FromStr`, `TryFrom`, names mangled (`1x` becomes `X1x`, `dark-blue` becomes `DarkBlue`) | unknown values on read fail to deserialise (no catch-all variant) |
| required nullable | `Option<String>` with `deserialize_with = Option::deserialize` (required but may be null) | absent and null still the same on optional nullable (`opt_nullable`) |
| `integer` (no format) | `i64`; `int32` is `i32`; `minimum: 1, maximum: 10` became `NonZeroU64` | constraint derived types can reject server values on read |
| `format: date-time` | `chrono::DateTime<Utc>` (offset lost); `date` NaiveDate; `uuid` Uuid; `byte` and `decimal` plain `String` | |
| `default: 5` | `#[serde(default = "defaults::default_u64::<i64, 5>")]` with `T::try_from(V).unwrap()` (generated code, cannot fail for the literal) | |
| `additionalProperties: {type: integer}` | `HashMap<String, i64>` with `default` and skip-if-empty | |
| `additionalProperties: false` | `#[serde(deny_unknown_fields)]` | rejects responses with extra fields: client-side strictness on read |
| `minLength/maxLength/pattern` | newtype with a validating `FromStr` and `Deserialize` using `regress` | CLIENT-SIDE VALIDATION on read and construct: conflicts with the owner decision "no client-side validation" |
| free-form `{}` | `Option<serde_json::Value>` | |

### Verdicts, item by item (ours = /Users/mridang/Code/mridang/openapi-generator-plus/src/spec/resources/generated/rust)

| # | Item | progenitor behaviour | Ours | Verdict |
|---|---|---|---|---|
| 1 | Reference document (official petstore) | cannot be generated (15 of 19 operations panic) | not run here against the official document | recipe-only: add the official petstore 3.0 document as an extra fixture across the twelve SDKs to prove none of them panics; defer (name the decision: whether the fixture suite grows beyond our own petstore) |
| 2 | Multiple response schemas per status class | assertion panic (method.rs:1220) | per-status errors, typed per declared schema | already-covered (#123, #117) |
| 3 | Multiple request media types | panic (2057) | per-call content type, `_with_content_type` variants | already-covered |
| 4 | Multiple RESPONSE media types, caller chooses | first JSON wins, others raw | not offered | defer #73 |
| 5 | Optional request body | impossible (method.rs:126) | optional bodies handled | already-covered |
| 6 | Error with undecodable success body | `InvalidResponsePayload(Bytes, err)` keeps the raw bytes, not status or headers | #135 is open for exactly this | adopt-in-subset: keep status, headers and raw body (#135), as progenitor keeps the bytes |
| 7 | Error body when the spec declares no schema | body discarded (`value: ()`, observed) | `ApiError.response_body()` keeps the raw text | already-covered (we are ahead) |
| 8 | Undeclared status | `UnexpectedResponse(reqwest::Response)` raw | ApiError with status and raw body | already-covered |
| 9 | Retry | none; `is_retryable()` only (observed: 1 request for a 503) | max_retries 2, backoff, Retry-After | already-covered; recipe-only for an `is_retryable`-style helper on `ApiErrorKind` (docs, no new API) |
| 10 | Timeout | fixed 15 s connect and total in `Client::new`, observed 15.0067 s; Display hides "timeout" | `TransportOptions` timeout; per call: #116 | already-covered; per-call timeout stays #116 |
| 11 | User agent | none sent (observed) | configurable `user_agent`, none by default | already-covered |
| 12 | Version header | sends `api-version: <info.version>` on every request (observed) | none | decline (vendor-specific convention, not in the spec; one fixed API shape) |
| 13 | Request/response hooks | runtime `pre`/`exec`/`post` via trait on the client with operation id | `ApiClient` trait; no per-call hook | adopt: #158 (one hook around every call, include operation id) and the recipe #140; progenitor confirms the minimal shape (operation id only) |
| 14 | Error type returned vs thrown | `Result<_, Error<E>>`, a single enum | typed per status, boxed | already-covered; typed return type #113 |
| 15 | Cause chain in Display | `{:#}` appends sources | `with_source` on ApiError | recipe-only (document how to print the chain) |
| 16 | Streaming responses | `ByteStream` for every non-JSON response | `Vec<u8>` | defer #114 (per-language decision) |
| 17 | oneOf with discriminator | `untagged`, discriminator ignored | hand-written deserialisation | already-covered; ambiguous oneOf warning #138 |
| 18 | anyOf, allOf | untagged enum, flattened struct | struct of Options, allOf+discriminator missing | already-covered; #104 |
| 19 | Nullable | required nullable handled, optional nullable not distinguishable | same limit | already-covered (documented known limit); #148 for query |
| 20 | Unformatted integer | `i64` | `i32` | adopt-in-subset: #159 (decide width for all twelve languages) |
| 21 | decimal | `String` | lossy `f64` | adopt-in-subset: #157 |
| 22 | date-time | `chrono::DateTime<Utc>`, offset lost | keeps the offset | already-covered |
| 23 | Constraints as types (`NonZeroU64`, `HolderLimited`, `deny_unknown_fields`) | validate on read and construct | not enforced (README "Numeric / string constraint validation") | decline (owner decision: no client-side validation); optional validation stays #83 |
| 24 | Enums | no unknown-value variant, names mangled | enums | already-covered; unknown-value behaviour not compared here: defer (name the decision: tolerate unknown enum values in all twelve SDKs) |
| 25 | Query and header styles | form and simple only; deepObject, pipeDelimited rejected | styles handled | already-covered; #109 |
| 26 | Cookie parameters | rejected (method.rs:400) | handled | already-covered; #65 for `style: cookie` |
| 27 | multipart | rejected | supported | already-covered |
| 28 | Per-type Rust customisation (`with_patch`, `with_replacement`, `with_conversion`) | build-time Rust API | the rename normaliser | decline (Rust-only, not a template concern); rename recipe #142 |
| 29 | x-rust-type crate replacement | vendor extension | none | decline (no vendor extensions in templates) |
| 30 | Dropshot pagination and websocket | vendor extensions | none | decline |
| 31 | Positional vs builder, tag styles, proc macro | build-time choices | one fixed shape | decline |
| 32 | CLI and httpmock generators | build.rs only | none | decline CLI; httpmock maps to the mockable-client work already in #122 |
| 33 | Generated tests, README, docs, license | none generated (license string `SPECIFY A LICENSE BEFORE PUBLISHING`) | tests, README, SKILLS | already-covered |
| 34 | OpenAPI version | 3.0.x only (lib.rs:746) | 3.0 and 3.1 | already-covered (#96) |
| 35 | wasm | `cfg(target_arch = "wasm32")` branches | not mentioned | defer (name the decision: wasm target for the Rust SDK; no existing issue found) |

## Findings worth an issue

1. progenitor cannot generate the official Swagger Petstore 3.0.4 (15 of 19 operations panic at method.rs:1220 and 2057) and 7 of 61 paths of our fixture. Evidence for the "why not progenitor" page of #134. No new issue; link from #134.
2. Keep status, headers and raw body when a success body cannot be decoded: progenitor keeps only the bytes (progenitor_client.rs:179 and the `InvalidResponsePayload` variant). Existing issue #135.
3. One hook around every call with the operation id: progenitor shows the smallest useful shape (`pre`, `exec`, `post` with `OperationInfo`). Existing issues #158 and #140.
4. Unformatted integer width (i64 in progenitor, i32 in ours): existing issue #159.
5. Exact decimal type: existing issue #157.
6. Multiple response media types and caller choice: existing issue #73.
7. Typed errors per status and declared schema: progenitor's single `E` per operation panics when two error statuses differ; ours is ahead. Existing issues #123, #117, #113.
8. Per-call timeout: progenitor has only a fixed 15 s; existing issue #116.
9. New issue needed: "test: add the official Swagger Petstore 3.0 document as a second fixture in every SDK's spec run" (it exposed two panics in a mature generator and costs one extra spec per language). Owner decision needed; it is a test-layout change, so it must follow the repository's test rules.
10. New issue needed (only if the owner wants it): "docs: tolerate unknown enum values on read in all twelve SDKs, decide the behaviour" (typify enums have no catch-all variant; we did not verify ours here).
11. New issue needed (low priority, owner decision): "decide whether the Rust SDK targets wasm32" (progenitor does; no issue found).

## Corrections to the first pass

- Hooks are not build-time only: they are runtime trait methods on the client as well (progenitor_client.rs:96-150). The earlier row said build-time TokenStreams only.
- The first pass said /pet/{petId} fails as a path; it fails only for DELETE (cookie parameter). 7 of 68 operations fail (7 paths).
- The first pass recorded "i64 for unformatted integer (not verified)"; now verified: `big: i64` and `minimum:1, maximum:10` becomes `NonZeroU64` (out-types/src/lib.rs).

## What could not be verified

- Our own Rust crate was not built, run or timed here and our oneOf, enum-unknown-value, retry and error behaviour come from reading the committed code and README only. No runtime run of our client against the same server.
- 503 followed by 200 was not exercised; only "one request per call" was observed. Redirects, compression, cookies, multipart, and request bodies were not exercised against progenitor at runtime.
- Panic rows marked "not run" were read in source only.
- Build and clippy times are warm-registry dev builds in a shared container; not a benchmark.
- 3.1-to-3.0 conversion: the fixture contained only four 3.1-only constructs; prefixItems has no 3.0 equivalent and was mapped to `items: {}` (as our normaliser does), so GeoPoint is not a faithful tuple.
- The git checkout (9471dec) differs from published 0.16.1 by about nine lines in method.rs; line numbers are from the published crate, which is what ran.
