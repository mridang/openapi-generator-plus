# ogen source review

This page compares the Go SDK of this project (`go-plus`) with ogen, a Go OpenAPI v3 generator for typed clients and servers. It exists so that a colleague who says "this lacks feature X that ogen has" gets an answer that is checked against source, not against marketing text.

## Scope, method and sources

* ogen was cloned from `https://github.com/ogen-go/ogen` and read at source level. Version audited: tag `v1.25.0`, commit `98dae3083dc68b2e203803e2fa38307603aaf47c` (2026-11-16). All ogen paths below are relative to that checkout. The clone was deleted after the audit.
* Read in full or in the parts that matter: `gen/_template/client.tmpl`, `cfg.tmpl`, `security.tmpl`, `request_encode.tmpl`, `response_decode.tmpl`, `servers.tmpl`, `server.tmpl`, `labeler.tmpl`, `test_examples.tmpl`, `defaults.tmpl`, `schema/*.tmpl`, the head of `validators.tmpl`; `gen/features.go`, `gen/options.go`, `gen/reduce.go`, `gen/gen_security.go`, `gen/gen_contents.go`, `gen/gen_parameters.go`, `gen/schema_gen_primitive.go`, `cmd/ogen/main.go`; the runtime packages `ogenerrors`, `validate`, `http`, `middleware`, `otelogen`; the extension parsers in `jsonschema/parser.go` and `openapi/parser/`; and the README.
* Our side is the committed Go golden under `src/spec/resources/generated/go/` (written `G/` below) and the templates under `src/main/resources/templates/go/`, plus `docs/how-it-works.md`, `docs/decisions.md`, `docs/languages/go.md` and `README.md`.
* One run of ogen was done on this project's petstore fixture (see section C and D). Nothing else was run.
* "Client or server" in the tables says whether the feature matters to a client SDK user. This project generates client SDKs only, so a server-only ogen feature is marked "server" and is "no" by design.
* Verdicts: yes, partly, no. "n/a" means the feature does not exist for a client in this form.

## A. Every ogen feature, and whether our Go SDK has it

### A1. Client runtime

| Feature | ogen source | Ours | Evidence in ours | Applies to |
|---|---|---|---|---|
| Generated `Client` with `NewClient(serverURL, security, opts...)` | `gen/_template/client.tmpl:280-308` | yes | `G/pkg/client.go:56` (`Client`), `:86` (`NewClient`), `:98` `NewClientWithCredentials`, `:148` `NewClientWithToken`, `:172` `NewClientWithTokenProvider` | client |
| One typed method per operation, `ctx` first, params struct, request body | `client.tmpl:393-397` | yes | `G/pkg/pet_api.go:268` `AddPet(ctx, pet, options *opts.AddPetOptions, callOptions ...RequestOption) (*Pet, error)` | client |
| Interface per client for mocking (`Invoker`, per-group `XInvoker`) | `client.tmpl:245-278` | yes | `G/pkg/pet_api.go:207` `PetApiInterface`, `:258` compile-time assertion, `G/pkg/store_api.go:43`. The top-level `Client` holds concrete `*PetApi` fields (`G/pkg/client.go:56-62`), so mocking means wrapping | client |
| Operation grouping | `x-ogen-operation-group`: `openapi/parser/parse_path_item.go:28`, `gen/gen_operation.go:29` | yes, by tag | `client.Pet`, `client.Store` (`G/pkg/client.go:56-62`). Ours is driven by OpenAPI tags, not an extension | client |
| `WithClient(ht.Client)` to supply the HTTP client | `gen/_template/cfg.tmpl:286-293` | yes | `TransportOptionsBuilder.HttpClient` / `.RoundTripper`, `G/pkg/api_client.go:13-17`, `ErrTransportConflict` in `G/pkg/transport_options.go:57` | client |
| Default HTTP client | `http.DefaultClient`, no timeout: `cfg.tmpl:206` | yes, better | 10 s end-to-end default, `G/pkg/transport_options.go:121-129` | client |
| Per-request options (opt-in feature `client/request/options`): `WithRequestClient`, `WithServerURL`, `WithEditRequest`, `WithEditResponse` | `gen/features.go:116`, `client.tmpl:5-68` | partly | We have `WithTimeout`, `WithHeaders`, `WithMaxRetries`, `WithRequestContentType` (`G/pkg/request_option.go:25-70`). No per-call client, base URL or request/response edit hook | client |
| Client-wide request and response editors (opt-in feature `client/editors`) | `features.go:124`, `cfg.tmpl:304-326`, `client.tmpl:327-345,614-618,646-650` | no | Nothing in `src/main/resources/templates/go` matches `Editor`, `Interceptor`, `Middleware`. Workaround: a caller `http.RoundTripper` (`README.md` "Custom HTTP client") | client |
| Per-call base URL via context when options feature is off | `client.tmpl:310-325` | no | The base URL lives in `Configuration`; only operations that declare their own `servers` take a `server` argument (`G/pkg/pet_api.go:1134`) | client |
| Server URL templates, variables, enum check, default fill | `gen/_template/servers.tmpl:6-69`; name via `x-ogen-server-name` `openapi/parser/parse_server.go:99` | yes | `G/pkg/server_configuration.go:33,97`, `G/pkg/servers.go`, per-operation server types `G/pkg/pet_api.go:23-190`, test `G/test/server_variable_test.go` | client |
| Trailing slash trimmed from base URL | `client.tmpl:238-241` | yes | `G/pkg/base_api.go:147-156` | client |
| Client-side request validation (opt-in feature `client/request/validation`) | `features.go:120`, `client.tmpl:413-445` | no | Deliberate, see decision S3 in `src/main/java/io/github/mridang/codegen/generators/go/AGENTS.md` and `docs/decisions.md` "Models are not validated" | client |
| Security evaluation: OR of requirement sets, each an AND of schemes, via bitset; `ErrSkipClientSecurity` | `client.tmpl:572-612`, `ogenerrors/security.go:6-11` | partly | One credential applies per operation: the first scheme in spec order that has a credential (`G/pkg/base_api.go:152-176`). AND groups are flattened (`AbstractBetterCodegen.securitySchemeOrder` javadoc). `decisions.md` "One credential per security scheme" | client |
| Retries | none (the only retry code is SSE reconnect, `client.tmpl:81-223`) | yes, better | `G/pkg/base_api.go:421-520`, `README.md` "Retries" | client |
| Timeouts | none beyond `ctx` | yes, better | `G/pkg/transport_options.go`, `WithTimeout` `G/pkg/request_option.go:25` | client |
| Proxy, custom CA, `VerifySsl`, redirect policy, `MaxRedirects` | none, supply your own client | yes, better | `G/pkg/transport_options.go:79-95`; no SOCKS (`README.md`) | client |
| `User-Agent`, default headers, `X-Request-ID` | none | yes (see D4 for the Request-ID default) | `G/pkg/transport_options.go:119-128,192-197` | client |
| Response decompression | stdlib gzip only (no `Accept-Encoding` handling in any template or runtime package) | yes, better | gzip, deflate, br, zstd: `G/pkg/default_api_client.go:764-780`, `README.md` "Response compression" | client |
| Graceful `Close()` | none | yes | `G/pkg/client.go:71`, `ErrClientClosed` | client |
| Drain body so connection is reused | `client.tmpl:635-644` | yes, equivalent | Body is read whole then closed: `G/pkg/default_api_client.go:356-361` | client |
| Raw `*http.Response` (`x-ogen-raw-response`) | `openapi/parser/parse_mediatype.go:200`, `response_decode.tmpl:105-109` | partly | `...WithHTTPInfo` returns status, headers and `RawBody` (buffered): `G/pkg/pet_api.go:283`, `G/pkg/api_result.go:18` | client |
| Typed declared response headers and status code on the result | `response_decode.tmpl:77-90,175-186,203-250`, `gen/gen_responses.go:305,373` | partly | `ApiResult[T]{StatusCode, Data, RawBody, Headers}` with lower-cased untyped headers, repeated headers joined (`decisions.md`) | client |
| Several 2xx schemas decoded by status | sum or interface result types, `response_decode.tmpl:22-30` | yes | Union `<Operation>Response` (`decisions.md` "Several success responses decode by status") | client |
| Non-2xx declared responses returned as typed values | `response_decode.tmpl:22-56`, `gen/gen_responses.go` | no | Non-2xx is always an error; the body is `ErrorBody() any`, a parsed JSON value (`G/pkg/errors/api_error.go:181`). Issues #117, #123 | client |
| Pattern (`4XX`) and `default` responses | `response_decode.tmpl:32-56` | partly | Mapped to status errors (`ClientError`, `ServerError`); not typed per schema | client |
| Context cancellation | `ctx` first parameter | yes | `ctx` first, cancelled is never a timeout (`docs/languages/go.md` idioms) | client |

### A2. Errors

| Feature | ogen source | Ours | Evidence in ours | Applies to |
|---|---|---|---|---|
| Wrapped stage errors ("encode request", "do request", "decode response") | `client.tmpl:536-543,632-634,659-665` | partly | Typed tree instead: `OpenAPIError` > `ApiError` > `ClientError`/`ServerError`/`NetworkError` (`G/pkg/errors.go:10-57`); encode failures are `*SerializationError` | client |
| Unexpected status error with the response retained | `validate/errors.go:77-94` (`UnexpectedStatusCodeError{StatusCode, Payload *http.Response}`) | yes, better | `ApiError` has `StatusCode()`, `ResponseHeaders()`, `ResponseBody()`, `ErrorBody()`, `RequestMethod()`, `RequestPath()` (`README.md` "Errors") | client |
| Per-status error types | none (one error type) | yes, better | `BadRequestError` ... `InternalServerError` (`G/pkg/errors.go:14-44`) | client |
| Invalid content type error | `validate/errors.go:60-74` | partly | Surfaces as `*SerializationError`; no dedicated type (UNVERIFIED mapping, see D) | client |
| Decode body error holding content type and raw body | `ogenerrors/json.go` (`DecodeBodyError`) | partly | `SerializationError` plus `ResponseBody()` on the enclosing `ApiError` | client |
| Convenient errors: one shared default error response becomes a Go `error` | `gen/reduce.go:19-125`, `gen/options.go:184-191,317-371`, `response_decode.tmpl:57-65`, sample `internal/integration/sample_err/oas_schemas_gen.go:9` (`ErrorStatusCode.Error()`) | partly | Every non-2xx is an error already, but its body is untyped. Issues #117, #123 | client |
| `ignore_not_implemented` and an error message per unsupported feature | `gen/options.go:179-182`, `cmd/ogen/main.go:108-246` | n/a | Our generator ignores what it does not support (callbacks, links) and documents it in the README | tooling |
| Error carries operation name and ID | `ogenerrors/ogenerrors.go:134-165` (server-side errors only) | n/a | Ours carries method and path instead | server |
| Sentinels for caller mistakes | `ErrSecurityRequirementIsNotSatisfied` | yes | `ErrMissingCredential`, `ErrInvalidTimeout`, ... (`docs/languages/go.md`) | client |

### A3. Security

| Feature | ogen source | Ours | Evidence in ours | Applies to |
|---|---|---|---|---|
| apiKey in header, query, cookie | `gen/gen_security.go:44-60`, `security.tmpl:194-205` | yes | `G/pkg/auth/api_key_header_authenticator.go`, `api_key_query_authenticator.go`, `session_cookie_authenticator.go` | client |
| HTTP basic, bearer | `gen_security.go:90-112`, `security.tmpl:209-212` | yes | `G/pkg/auth/basic_authenticator.go`, `bearer_authenticator.go` | client |
| Other HTTP schemes | rejected: `ErrNotImplemented "http security scheme"` `gen_security.go:112` | UNVERIFIED | Not checked in our generator | client |
| OAuth2 | a Bearer string only; flows not implemented: `security.tmpl:213-214`, `gen_security.go:188-190` | yes, better | Client credentials, authorization code, password and implicit, token cache, refresh, 401 replay: `G/pkg/auth/oauth/oauth2_token_manager.go:33,47,201` | client |
| OpenID Connect | unsupported: `gen_security.go:190-191` | yes, better | `G/pkg/auth/oauth/openid_connect_authenticator.go` | client |
| mutualTLS | unsupported: `gen_security.go:190-191`, parser needs 3.1 `parse_security.go:59` | no (same) | Out of scope, `README.md` "Mutual TLS security schemes" | client |
| Custom security (`x-ogen-custom-security`) | `openapi/parser/parse_security.go:192`, `security.tmpl:57-62,177-184` | yes, different mechanism | Implement `Authenticator` (`G/pkg/authenticator.go:15`) and call `NewClientWithAuthenticator` (`G/pkg/client.go:193`) | client |
| Per-request dynamic credentials (`SecuritySource` is called on every request) | `security.tmpl:52-71` | yes | `NewClientWithTokenProvider` and `auth.TokenProvider` (`G/pkg/auth/bearer_authenticator.go:32`) | client |
| Reentrant security (security source can use the client; feature `client/security/reentrant`) | `features.go:112`, `security.tmpl:59,65,179,187` | yes, equivalent | `auth.HttpAwareAuthenticator` receives the shared transport (`G/pkg/client.go:111-130`) | client |
| Credentials from environment | none | yes, better | `auth.ResolveCredential` (`G/pkg/auth/base_authenticator.go:26`) | client |
| Scope and role accessors per operation | `security.tmpl:222-288` (`GetOAuth2ScopesFor...`) | n/a | Scopes are fixed at authenticator construction | server (mainly) |
| Server `SecurityHandler` | `security.tmpl:8-49,98-172` | no, by design | | server |

### A4. Types and models

| Feature | ogen source | Ours | Evidence in ours | Applies to |
|---|---|---|---|---|
| `OptX`, `NilX`, `OptNilX` generic wrappers (`Set`, `Null`, `Get`, `Or`, `IsEmpty`) | `gen/_template/schema/generic.tmpl:1-101` | no, deliberate | Optional and nullable are `*T`; an absent value and `null` are the same (`G/pkg/models/pet.go:93-114`, `decisions.md` "Optional and nullable are not told apart") | client |
| Required and nullable keeps its key | `gen/gen_parameters.go` / JSON encoders | yes | `docs/languages/go.md` type table, `G/pkg/models/required_nullable.go` | client |
| `oneOf` as sum type with `Type` enum, `IsX`, `GetX`, `SetX`, `NewX` | `schema/sum.tmpl:1-101` | yes | `G/pkg/models/pet_food.go:23-124` (`NewDryFoodPetFood`, `IsDryFood`, `AsDryFood`, `ActualInstance`) | client |
| Sum inference without a discriminator: by JSON type, field name, field type, field value (disjoint enums) | `gen/schema_gen_sum.go:306-1228`, README "Discriminator Inference" | partly | Variants are tried in declaration order (`docs/languages/go.md` type table, `G/test/composed_schema_test.go:186-349` pins collisions and order) | client |
| Explicit discriminator and mapping | `schema_gen_sum.go:593-663` | yes | `G/pkg/models/pet_food.go:85-115`; unknown value is an error | client |
| `anyOf` | `schema_gen_sum.go:537` ("complex anyOf" not implemented) | yes, better | `PetTreatment` is generated and `AddPetTreatment` exists (`G/pkg/pet_api.go:523`); ogen skipped that operation on the same spec, see C | client |
| `allOf` merging | `schema_gen_sum.go:1743-1886` (several unsupported cases) | yes | Classic `allOf` with a discriminator is open (#104) | client |
| `additionalProperties` as map, `patternProperties` | `schema/map.tmpl`, `schema_gen_sum.go:1743` | partly | Untyped `map[string]any` bucket (`G/pkg/models/metadata.go:20`), issue #103; `patternProperties` not handled | client |
| String enums with `AllValues()`, text marshal and unmarshal that reject unknown values | `schema/enum.tmpl:12-55` | yes | `AllPetStatusEnumValues`, `IsValid`, strict `UnmarshalJSON` and `MarshalJSON` (`G/pkg/models/pet.go:29-80`) | client |
| Integer, non-primitive enums | `schema_gen_primitive.go:30-100`, test dir `internal/integration/test_non_primitive_enum` | partly | Integer enums yes (`G/pkg/models/priority.go:19-60`); non-primitive enums UNVERIFIED | client |
| Enum value coercion with a warning | `schema_gen_primitive.go:127-133` | n/a | | tooling |
| Enum constant naming strategies | `gen/names.go`, `_testdata/positive/enum_naming.yml` | n/a | We name by our own rule; no `x-enum-varnames` in ogen either (grep) | client |
| `default` values applied on decode | `gen/_template/defaults.tmpl:1-24` (`setDefaults`) | partly | `G/pkg/models/defaults.go`; enum defaults applied (`docs/languages/go.md`). Depth for other types UNVERIFIED | client |
| `const` values | README "Const values" | UNVERIFIED | | client |
| Recursive types use pointers | README "Recursive types" | yes | `G/pkg/models/tree_node.go` | client |
| Getters and setters on every struct | `schema/struct.tmpl:115-126` | different | Immutable `With<Prop>` and `Without<Prop>` copies (`G/pkg/models/pet.go:129-270`), `decisions.md` "Models are immutable" | client |
| `Equal` and `Hash` methods | `gen/gen_equality.go:15-30`, `gen_equality_detect.go:17` | partly | `Equal` by JSON comparison (`G/pkg/models/pet.go:119`); no `Hash` | client |
| `uniqueItems` as slice plus runtime check | `validators.tmpl:56-73`, `validate/array.go` | different | `models.Set[T]`, sorted scalars on the wire (`G/pkg/models/set.go`) | client |
| Struct aliases embedding the base type (opt-in `schema/embed_struct_aliases`) | `features.go:153`, `schema/alias.tmpl:3-9` | no | | client |
| Extra struct tags (`x-oapi-codegen-extra-tags`) | `jsonschema/parser.go:243` | no | | client |
| External Go type per schema (`x-ogen-type`), with encode and decode via native, JSON, text or binary interfaces | `jsonschema/parser.go:233`, `gen/ir/external.go:14-100` | no | See B | client |
| Custom time layout (`x-ogen-time-format`) | `jsonschema/parser.go:238`, `json/time.go:15-60` | no | Fixed wire form (`docs/types.md` "Dates and times are exact on the wire") | client |
| Faker (`SetFake`) for round-trip tests | `gen/_template/faker/*`, `gen/write.go:400` | no | Not needed; we test against a mock server | tooling |

### A5. Formats

| Format | ogen | Ours |
|---|---|---|
| `date`, `time`, `date-time`, `http-date` | `time.Time` (`schema_gen_primitive.go:189-207`, `json/time.go`) | `date`, `time`: `string`; `date-time`: `models.DateTime` (`docs/languages/go.md`). Issue #126 |
| `duration` | `time.Duration` (`json/std_duration.go`) | `time.Duration` (D133), protobuf-JSON form |
| `uuid` | `uuid.UUID` | `uuid.UUID` |
| `ip`, `ipv4`, `ipv6` | `net.IP` | `string` |
| `mac` | `net.HardwareAddr` | `string` |
| `uri` | `url.URL` | `string` |
| `byte`, `base64` | `[]byte` | `[]byte` |
| `int8` ... `int64`, `uint` ... `uint64`, string-encoded ints | yes (`schema_gen_primitive.go:176-210`) | `int32`, `int64` only |
| unformatted `integer` | `int` | `int32` (documented gap, `docs/languages/go.md` "Under review") |
| `float`, `double`, `decimal` | `float32`, `float64`, `decimal.Decimal` (`json/decimal.go`) | `float32`, `float64`; `decimal` is `float64`, lossy |
| `unix`, `unix-seconds`, `unix-milli`, `unix-micro`, `unix-nano` (integer or string) | `time.Time` (`schema_gen_primitive.go:187-191,234-238`, `json/unix.go`) | no |

### A6. JSON

| Feature | ogen source | Ours | Evidence in ours | Applies to |
|---|---|---|---|---|
| Code-generated encoder and decoder on `jx`, no reflection | `gen/_template/json/*.tmpl`, README "JSON" | no | `encoding/json` with HTML escaping off (`G/pkg/object_serializer.go`); single-pass decode is #107 | client |
| Unknown fields skipped on decode | README sample (`d.Skip()`) | yes (stdlib default); additionalProperties kept | `G/pkg/models/metadata.go:20` | client |
| Trailing data rejected | `response_decode.tmpl:136-138` | UNVERIFIED | | client |
| Streaming JSON bodies (`x-ogen-json-streaming`) | `parse_mediatype.go:189`, `request_encode.tmpl:109-122`, `response_decode.tmpl:123-125` | no | #114 | client |
| Depth guard | UNVERIFIED in ogen | yes | `maxJSONDepth` 1000 on success and error bodies (`G/pkg/errors/api_error.go:19-60`) | client |
| Response size cap | none (`io.ReadAll`, `response_decode.tmpl:126`) | yes, `MaxResponseBytes` (D138) | #112 | client |
| Server-Sent Events client with reconnect, `Last-Event-ID`, three event shapes (`x-ogen-sse-event-shape`) | `client.tmpl:70-236,555-752`, `schema/sse_stream.tmpl`, `sse/`, `parse_mediatype.go:211` | no | #114 | client |

### A7. Parameters and bodies

| Feature | ogen source | Ours | Evidence in ours | Applies to |
|---|---|---|---|---|
| Path, query, header, cookie parameters | `gen/_template/parameter_encode.tmpl`, `uri/` | yes | `G/pkg/value_serializer.go`, `G/pkg/path_utils.go`, cookie rule `ErrInvalidCookie` | client |
| Styles: simple, label, matrix, form, spaceDelimited, pipeDelimited, deepObject, explode | `uri/query_encoder.go` and siblings | yes | `G/pkg/value_serializer.go:160,295-330,372-480` | client |
| Object-valued query and header parameters | `uri/object.go` | partly | #109 | client |
| ogen limits: spaceDelimited or pipeDelimited object, nested arrays or objects in form parameters | `gen/gen_parameters.go:237,246,278-283` | n/a | we do not refuse | client |
| `content:` encoded parameters | `gen_parameters.go:146`, `parameter_encode.tmpl` | yes | decision S2 in the Go `AGENTS.md` | client |
| Untyped (`any`) parameters | README | UNVERIFIED | | client |
| Body content types: JSON, `+json` families, problem+json, form-urlencoded, multipart, octet-stream, text/plain, event-stream | `gen/ir/media.go:20-34`, `gen/gen_contents.go:24-45,496-660` | yes (not event-stream, not XML) | JSON-like regex `G/pkg/header_selector.go:17`, `base_api.go:654-684`, multipart builders `base_api.go:764-1000` | client |
| `content_type_aliases` and `wildcard_content_type_default` | `gen/options.go:192-201,373-407` | no | | client |
| Wildcard media types (`image/*`, `*/*`) with a `ContentType` wrapper | `request_encode.tmpl:29-43`, `gen_contents.go:102-125` | UNVERIFIED | | client |
| Several request content types, caller picks | per-type request interface, `request_encode.tmpl:44-72` | yes | `WithRequestContentType` (`G/pkg/request_option.go:62`) | client |
| Optional body | `request_encode.tmpl:82-87` | yes | | client |
| Streaming upload from `io.Reader` | `request_encode.tmpl:89-107` | partly | Accepts `io.Reader`, buffers whole body, cannot retry from a stream (`decisions.md` "Streaming is not supported") | client |
| Multipart file parts, multipart with JSON parts | `request_encode.tmpl:175-186,196-227`, `http/multipart*.go` | yes | `G/pkg/base_api.go:764-1000` | client |

### A8. Config and CLI

| Feature | ogen source | Ours | Applies to |
|---|---|---|---|
| CLI flags `-config`, `-target`, `-package`, `-clean`, `-strict`, `-initialisms`, `-initialisms-extra`, `-v`, `-loglevel`, `-color`, `-cpuprofile`, `-memprofile`, `-version` | `cmd/ogen/main.go:326-359` | We use OpenAPI Generator flags: `--generator-name go-plus`, `--config`, `--openapi-normalizer` (`README.md` "Usage") | tooling |
| Config file auto-discovered: `ogen.yml`, `ogen.yaml`, `.ogen.yml`, `.ogen.yaml` | `main.go:284-296` | We use `--config file.yml` of generator options | tooling |
| `-clean` deletes only `oas*_gen.go` and `*_gen_test.go` | `main.go:32-52` | UNVERIFIED | tooling |
| Feature flags: 16 (`paths/client`, `paths/server`, `webhooks/client`, `webhooks/server`, `client/security/reentrant`, `client/request/options`, `client/request/validation`, `client/editors`, `server/response/validation`, `server/spec`, `server/swagger_ui`, `ogen/otel`, `ogen/unimplemented`, `debug/example_tests`, `schema/embed_struct_aliases`, `naming/camel_initialisms`); defaults: paths/client, paths/server, webhooks/client, webhooks/server, ogen/otel, ogen/unimplemented | `gen/features.go:96-163,167-175,177-194` | No feature flags: one fixed shape across 12 languages | tooling |
| Operation filters by path regex and method | `gen/options.go:416-447` | partly: `FILTER_PATHS` (regex list on paths only, `docs/how-it-works.md`); a method filter in the stock normalizer is UNVERIFIED | tooling |
| `convenient_errors` on, off, auto | `options.go:184-191` | n/a | tooling |
| `infer_types`, `depth_limit`, `authentication_schemes`, `allow_cross_type_constraints`, `disallow_duplicate_method_paths` | `options.go:58-94` | n/a (OpenAPI Generator parser) | tooling |
| `allow_remote` remote `$ref` resolution | `options.go:62-69,96-163` | UNVERIFIED (inherits OpenAPI Generator behaviour) | tooling |
| `spec_source` raw, expanded, auto; `expand` output | `options.go:207-253` | n/a | tooling |
| `initialisms` customisation | `options.go:203-205,255-315` | no: fixed upper-case initialisms (`docs/languages/go.md`) | tooling |
| OpenAPI 3.0, 3.1 and 3.2 features (webhooks, `additionalOperations`, `query` method, mutualTLS) | `openapi/parser/version.go:45`, `parse_path_item.go:62-80`, `parse_webhook.go:36` | 3.1 and 3.2 are separate epics (`docs/how-it-works.md`) | tooling |
| Pretty errors with source positions | `cmd/ogen/main.go:116` (`location.PrintPrettyError`) | OpenAPI Generator messages | tooling |
| Output is goimports-formatted `oas_*_gen.go` files in one package | `gen/write.go` | multi-package module formatted by gofumpt in Docker (`docs/how-it-works.md` "Formatting") | tooling |
| Container image | `ghcr.io/ogen-go/ogen` (README) | `ghcr.io/mridang/openapi-generator-plus` (`README.md`) | tooling |

### A9. OpenAPI extensions ogen honours

| Extension | ogen source | Effect | Ours |
|---|---|---|---|
| `x-ogen-name` | schema `jsonschema/parser.go:190`; parameter `openapi/parser/parse_parameter.go:203`, `gen/gen_parameters.go:179` | Go type or parameter name | not read |
| `x-ogen-properties` | `jsonschema/parser.go:198` | Go field names | not read |
| `x-ogen-type` | `jsonschema/parser.go:233`, `gen/ir/external.go:82` | external Go type | not read |
| `x-ogen-time-format` | `jsonschema/parser.go:238` | custom time layout | not read |
| `x-oapi-codegen-extra-tags` | `jsonschema/parser.go:243` | extra struct tags | not read |
| `x-ogen-validate` | `jsonschema/parser.go:248`, `validate/ogen.go:91` (`RegisterValidator`) | user validators | not read |
| `x-ogen-operation-group` | `parse_path_item.go:28` | handler and invoker groups | not read; tags group operations |
| `x-ogen-server-name` | `parse_server.go:99` | server type name | not read |
| `x-ogen-json-streaming` | `parse_mediatype.go:189` | streamed JSON body | not read |
| `x-ogen-raw-response` | `parse_mediatype.go:200` | `*http.Response` result | not read |
| `x-ogen-sse-event-shape` | `parse_mediatype.go:211` | SSE event shape | not read |
| `x-ogen-custom-security` | `parse_security.go:192` | custom security hook | not read |

The only extension this project acts on is `x-internal`, which removes an operation from generation (`docs/how-it-works.md`). The rule "never depend on a spec-sourced vendor extension in a template" is in the repository `AGENTS.md`.

### A10. Tooling, tests and examples

| Feature | ogen source | Ours | Evidence in ours |
|---|---|---|---|
| Generated tests: JSON round trip per type and example tests, opt-in | `gen/write.go:399-400`, `gen/_template/test_examples.tmpl` | yes, far more | 779 test functions in `G/test/` and 477 in `G/pkg/*_test.go`, run against a mock server and a proxy container (`docs/how-it-works.md` "The specs and the gates") |
| Example projects | `examples/` (ex_petstore, ex_github, ex_openai_v1, ...) | partly | `README.md` and `SKILLS.md` of the SDK; compiled examples are #111 |
| Lint and format config shipped | none | yes | `G/.golangci.yml`, `G/Makefile`, gofumpt, staticcheck, `GOOS=wasip1` build (`GoWasmSpec`) |
| Machine-readable docs for assistants | none | yes | `G/SKILLS.md` |
| Spec embedded in the package, `SpecJSON`, `SpecYAML`, Swagger UI | `gen/_template/spec.tmpl`, `swaggerui/` | no, by design | server feature |

### A11. Observability

| Feature | ogen source | Ours | Evidence in ours | Applies to |
|---|---|---|---|---|
| Span per client call, named by operation, with stage tracking and `RecordError` | `client.tmpl:447-494` | no | | client |
| Span attributes: OperationID, `http.request.method`, `url.template` | `client.tmpl:452-460`, `gen/ir/operation.go:50-65` | no | | client |
| Client metrics `ogen.client.request_count`, `errors_count`, `duration` (ms) | `otelogen/keys.go:8-35`, `client.tmpl:466-475` | no | | client |
| `WithTracerProvider`, `WithMeterProvider`, `WithAttributes` (default feature `ogen/otel`) | `cfg.tmpl:254-281`, `features.go:167-175` | no | Issue #108 | client |
| Trace context propagation to the server | by the OTel transport the caller supplies | partly | `traceparent` and `tracestate` from a registered propagator (`G/pkg/trace_context_util.go:30-73`), but the propagator receives `context.Background()`, see D3 | client |
| `Labeler` for custom server metric attributes | `gen/_template/labeler.tmpl:1-37` | n/a | | server |
| Logging | generator uses zap; generated code does not log | none in the SDK either | `README.md` "Logging and tracing" | client |

### A12. Server features (not applicable to a client SDK)

All of these are "no, by design": generated `Handler` and `Server` (`server.tmpl:5-70`), static radix router (`router.tmpl`, README), request decoding and response encoding (`request_decode.tmpl`, `response_encode.tmpl`), `ServerOption`s `WithNotFound`, `WithMethodNotAllowed`, `WithErrorHandler`, `WithPathPrefix`, `WithMiddleware`, `WithMaxMultipartMemory` (`cfg.tmpl:330-387`), `middleware.Request` with parsed params and raw body (`middleware/middleware.go:47-141`), `ogenerrors.DefaultErrorHandler` and `ErrorCode` (`ogenerrors/handler.go:20-54`), CORS and OPTIONS handling (`cfg.tmpl:117-148`), server response validation (`features.go:128`), `UnimplementedHandler` (`unimplemented.tmpl`), webhook servers (`server.tmpl:73-105`), `SecurityHandler`, and the embedded spec with Swagger UI (`spec.tmpl`, `cfg.tmpl:389-434`). ogen also generates a webhook client (`client.tmpl:353-382`, feature `webhooks/client`, on by default); we skip webhooks and callbacks.

## B. Questions colleagues will ask

Ordered by how likely a user of a generated client is to ask. "Real gap" means a user loses something; "decision" means we chose it and wrote it down.

1. **"ogen gives me the server too. Where is it?"**
   * ogen: generates client, server, router, middleware (A12).
   * Ours: client SDKs only (`README.md` first paragraph).
   * Real gap or decision: decision (scope). No server in any of the 12 languages.
   * Effort: L (a second product). Languages: all 12.

2. **"Where are the OpenTelemetry traces and metrics?"**
   * ogen: on by default, one span and three metrics per call, `WithTracerProvider`, `WithMeterProvider`, `WithAttributes` (`cfg.tmpl:254-281`, `client.tmpl:447-494`).
   * Ours: nothing emitted; only header propagation via a registered propagator that cannot see the caller's span (D3). A caller can wrap `otelhttp.NewTransport` through `RoundTripper` (UNVERIFIED end to end).
   * Gap: real, tracked as #108 (`docs/decisions.md` "Under review").
   * Effort: M for Go; M per language for the other 11, and it must land in all 12 together. Languages: all, Go verified.

3. **"Why pointers? I want `OptString` so I can tell absent from `null`."**
   * ogen: `OptX`, `NilX`, `OptNilX` with `Set` and `Null` (`schema/generic.tmpl`).
   * Ours: `*T`; absent and `null` are one state on the way out (`docs/decisions.md` "Optional and nullable are not told apart"; a three-state type "may come later").
   * Gap: real for PATCH-style APIs; documented as a decision.
   * Effort: L (changes every model signature). Languages: all except TypeScript.

4. **"Where are my typed errors for the 404 or `default` schema? ogen gives me `*ErrorStatusCode`."**
   * ogen: declared non-2xx responses are returned as typed values; convenient errors turn the shared default response into an `error` (`reduce.go:19`, `response_decode.tmpl:22-65`).
   * Ours: an error per status class with `ErrorBody() any` holding parsed JSON (`G/pkg/errors/api_error.go:181`).
   * Gap: real, tracked as #117 and #123.
   * Effort: M to L (needs a shared model in the generator). Languages: all 12.

5. **"How do I add a request hook (sign, log, edit) like `WithEditRequest` or the client editors?"**
   * ogen: opt-in features `client/request/options` and `client/editors` (`features.go:116,124`).
   * Ours: wrap `http.RoundTripper` or implement `ApiClient`; no first-class hook, no per-call hook.
   * Gap: partly real; the workaround is idiomatic in Go but not in every language.
   * Effort: S in Go (an option list on `Configuration`), M across 12 because the API shape must match. Languages: all 12.

6. **"Does it validate min, max, pattern, minLength, uniqueItems like ogen?"**
   * ogen: generated validators run on decode, and on request when `client/request/validation` is on (`validators.tmpl`, `validate/*.go`).
   * Ours: required properties, enum membership, `format: uri` and `byte` are checked; numeric, string and array constraints are not (`G/README.md` "Schema constraints").
   * Decision: S3, "the server is the authority" (`docs/decisions.md`, Go `AGENTS.md`).
   * Effort: L. Languages: all except Python, which gets it from pydantic.

7. **"`date` is a `string`, `ipv4` is a `string`, `uri` is a `string`."**
   * ogen: `time.Time`, `net.IP`, `url.URL` (A5).
   * Ours: strings; `date` is #126; `net/netip` and `url.URL` are "under review" (`docs/languages/go.md`).
   * Gap: real, small. Effort: S to M (changes public types, so decide with the other languages). Languages: `date` also Node and Swift; IP and URI in PHP, Go, Rust, Swift, Dart, Elixir (`docs/types.md`).

8. **"Can I stream a response or consume SSE?"**
   * ogen: SSE client with reconnect and typed events, `x-ogen-json-streaming`, `x-ogen-raw-response` (A6).
   * Ours: whole body in memory both ways (`docs/decisions.md` "Streaming is not supported"); escape hatch is your own `ApiClient`.
   * Gap: real, tracked as #114. Effort: L. Languages: all 12.

9. **"Can I change the server URL per call?"**
   * ogen: `WithServerURL` as a request option or context value (`client.tmpl:49-54,310-325`).
   * Ours: base URL fixed in `Configuration`; per-operation `server` arguments only where the spec declares `servers`.
   * Gap: real, small. Effort: S in Go; the other languages UNVERIFIED. Languages: probably all.

10. **"ogen is faster: generated `jx` encoders, no reflection."**
    * ogen: no reflection, code-generated JSON (README, `json/`).
    * Ours: `encoding/json`; single-pass decode is #107. No benchmark exists on either side in this review (D5).
    * Gap: real only if a benchmark shows it matters. Effort: L (would add a dependency and diverge from the other 11). Languages: Go only.

11. **"I need typed `additionalProperties`."**
    * ogen: `map[string]T` types (`schema/map.tmpl`).
    * Ours: `map[string]any` (#103). Effort: M. Languages: all 12.

12. **"I want to rename a type or field, add struct tags, or map a schema to my own Go type (`x-ogen-name`, `x-ogen-properties`, `x-ogen-type`, extra tags, initialisms)."**
    * ogen: nine naming and typing extensions plus `initialisms` config (A9, A8).
    * Ours: none; the rule is that templates never read spec extensions. The escape hatch is the `RUN_SCRIPT` normalizer rule; the stock OpenAPI Generator `typeMappings` and `importMappings` for `go-plus` are UNVERIFIED.
    * Gap: real, deliberate. Effort: M, and each extension must work in all 12 or none. Languages: all 12.

13. **"Webhooks and callbacks."**
    * ogen: a `WebhookClient` posting to a target URL, and webhook servers (`client.tmpl:353-382`).
    * Ours: ignored (`docs/decisions.md` "Out of scope"). Effort: M to L. Languages: all 12.

14. **"My operation needs two schemes at once (API key and bearer)."**
    * ogen: requirement sets are evaluated as OR of AND (`client.tmpl:572-612`).
    * Ours: first credentialed scheme only (`G/pkg/base_api.go:152-176`, `decisions.md` "One credential per security scheme"). The generator logic is shared, so other languages probably behave alike (UNVERIFIED).
    * Gap: real for such APIs. Effort: M. Languages: all 12 probably.

15. **"oneOf without a discriminator: ogen infers by field names, types or enum values."**
    * ogen: three inference strategies, and a clear error when ambiguous (`schema_gen_sum.go`, `cmd/ogen/main.go:163-243`).
    * Ours: declaration order, last collision pinned by tests (`G/test/composed_schema_test.go:317-349`). Effort: M. Languages: shared model, Go verified only.

16. **"Unsigned and sized integers, `time` unix formats, `decimal.Decimal`, `uint64`."**
    * ogen: A5. Ours: `int32`, `int64`, `float64`. Effort: S per format but a public type change. Languages: Go (formats are not in standard OpenAPI; `unix` and `uintN` are ogen custom formats).

17. **"Generate only some operations by method or tag."**
    * ogen: `filters.path_regex` and `methods` (`options.go:416-447`). Ours: `FILTER_PATHS` by path regex. Effort: S. Languages: all 12 (shared normalizer).

18. **"Will you support OpenAPI 3.1 and 3.2 the way ogen does (webhooks, `additionalOperations`, `query`)?"**
    * ogen: `openapi/parser/version.go:45`. Ours: separate epics (`docs/how-it-works.md`). Effort: L. Languages: all 12.

## C. Where we are better

Each row compares our generated Go (`G/`) with ogen's templates and runtime. "Verified" means both sides were read; the source is the evidence, not a benchmark.

| Area | ogen | Ours | Verified by |
|---|---|---|---|
| Transport options | `WithClient` only; default is `http.DefaultClient` with no timeout (`cfg.tmpl:206`). Proxy, TLS, CA, redirects and timeouts belong to the caller | `TransportOptions` builder: proxy (http, https only), custom CA, `VerifySsl`, redirect limit and policy, 10 s end-to-end default, user agent, default headers, conflict check with a supplied client (`G/pkg/transport_options.go`, `default_api_client.go`) | read `cfg.tmpl:167-241`; read `transport_options.go:79-130` |
| Retries | none; the only retry loop is SSE reconnect (`client.tmpl:167-223`); a repo-wide search for retry or backoff in templates and runtime packages finds nothing else | retries on network error before a response and on 408, 409, 429, 5xx except 501; backoff 0.5 s doubling to 8 s with jitter; `Retry-After` honoured; shares the end-to-end deadline; per-call `WithMaxRetries` (`G/pkg/base_api.go:196-520`) | grep over the clone; read `base_api.go` |
| OAuth2 token management | OAuth2 is a Bearer string you return from `SecuritySource` (`security.tmpl:213-214`); flows are marked not implemented in `gen_security.go:188-190`; OpenID Connect and mutualTLS are refused (`:190-191`) | four OAuth2 flows and OpenID Connect, token cache, refresh 60 s before expiry (`oauth2_token_manager.go:33`), one 401 replay with a fresh token (`base_api.go:395-412`), PKCE, redirect refusal on token POSTs (`api_client.go:24-33`) | read both |
| Secret redaction | none found: no `String()`, `GoString` or masking in templates or runtime packages (grep for redact finds nothing) | `String` and `GoString` on `Configuration`, `TransportOptions`, authenticators mask proxy password, `Authorization`, `Cookie` and key or token headers (`G/pkg/configuration.go:139-184`, `transport_options.go:294-311,454-464`) | grep; read ours |
| Error context | `UnexpectedStatusCodeError` has status and the response only; stage strings like "do request" (`validate/errors.go:77-94`, `client.tmpl:632-634`) | method and path on every API, network and timeout error (query stripped because it may hold a credential), per-status types, cancel is never a timeout (`README.md` "Errors", `G/pkg/errors/api_error.go:142`) | read both |
| Credentials | caller code supplies every value | argument, then environment variable, fail at construction with a named variable; provider function (`G/pkg/auth/base_authenticator.go:22-40`, `client.go:98-190`) | read ours |
| Lifecycle | no `Close`; cancellation by `ctx` only | graceful, idempotent `Close`, `ErrClientClosed`, a supplied client is never closed (`G/pkg/client.go:71`) | read ours |
| Response decompression | stdlib transparent gzip only; no `Accept-Encoding` handling in templates or runtime | gzip, deflate, br, zstd advertised and decoded (`G/pkg/default_api_client.go:764-780`) | grep over the clone; read ours |
| Per-call settings | opt-in feature; client, URL, edit hooks | timeout, headers, retries, request content type on every operation, always on (`G/pkg/request_option.go`) | read both |
| Generated tests | none by default; round-trip and example tests behind `debug/example_tests` (`gen/write.go:399-400`) | 1,256 test functions covering transport, retries, redirects, auth flows, serialization, error mapping, against a mock server and a proxy | counted `^func Test` in `G/test` (779) and `G/pkg` (477) |
| Docs shipped | godoc on types and operations | README, `SKILLS.md`, godoc, lint config, Makefile, a WebAssembly build check | read the golden tree |
| Spec coverage on this project's fixture | stopped on three problems (below) | generated the whole fixture | ran ogen on `src/spec/resources/specs/petstore/openapi.yaml` |

The fixture run, with ogen `v1.25.0` in a `golang:1.26-alpine` container: (1) it refused the spec with `unknown security scheme "petstore_auth"` at line 854, because the fixture references a scheme it does not define; our generator accepted it. (2) After that reference was pointed at a defined scheme in a scratch copy, ogen with `ignore_not_implemented: ["all"]` skipped `POST /pet/{petId}/treatment` with "complex anyOf not implemented" (`gen/schema_gen_sum.go:537`, logged at `gen/generator.go:236`); ours generates `AddPetTreatment` (`G/pkg/pet_api.go:523`). (3) It then failed in the `validators` template: goimports reported `oas_validators_gen.go:645:7: expected selector or type assertion, found '['`, so no output was written. Read this as a statement about our adversarial fixture, not about ogen on ordinary specs.

## D. Unverified, and things found on the way

Unverified:

* Whether other languages' SDKs inject trace context, emit spans or accept per-call server URLs. Only the Go templates were read.
* Whether our generator accepts or refuses HTTP security schemes other than basic and bearer; whether wildcard media types (`image/*`) are handled; `const`; `any`-typed parameters; non-primitive enums; trailing JSON data rejected on decode.
* Whether stock OpenAPI Generator options (`typeMappings`, `importMappings`, the `FILTER` normalizer by method) work with `go-plus`. They are inherited but untested here.
* Whether remote `$ref` resolution works the same as ogen's `allow_remote`.
* ogen's own list of supported and unsupported OpenAPI features lives on its website, not in the repository. The unsupported list used here is derived from `ErrNotImplemented` sites in `gen/` (`gen_security.go:112,191`, `gen_parameters.go:146,237,246,278,283`, `schema_gen_sum.go:306-1886`, `gen_contents.go:140-513`).
* Performance. No benchmark was run on either side, so every speed statement is about design, not numbers.
* Whether wrapping `otelhttp.NewTransport` through `TransportOptionsBuilder.RoundTripper` yields spans that join the caller's trace. The request is built with the call context (`G/pkg/default_api_client.go:325`), which suggests it does.

Found on the way, outside the comparison but worth fixing before the rollout:

* **D3, trace context.** `G/pkg/trace_context_util.go:41-61` says "API operations take no context.Context", which is no longer true (every operation takes `ctx` first), and `InjectTraceContext(headers)` (`G/pkg/base_api.go:295`) calls the propagator with `context.Background()`. A propagator therefore cannot read the caller's span from the call context. This is the practical reason the answer to question 2 is "no".
* **D4, X-Request-ID default.** `docs/how-it-works.md` ("Options and retries") and the generated README ("Request id") say the SDK adds a unique `X-Request-ID` to every request unless you turn it off with `InjectRequestID(false)`. The Go code defaults to off: `G/pkg/transport_options.go:99,111` (`injectRequestID: false`), and only injects when `InjectRequestID()` is true (`default_api_client.go:251`); the tests that check it call `InjectRequestID(true)` (`G/test/default_api_client_test.go:242,273,304`). Either the docs or the default is wrong for Go.
* **D5, fixture.** `src/spec/resources/specs/petstore/openapi.yaml:854` references `petstore_auth`, which is not defined in `components.securitySchemes`. Our generator tolerates it; strict tools (ogen) do not.
* **D6, floor.** The Go `AGENTS.md` says the `go.mod` floor is `go 1.25`; the golden `go.mod`, `docs/languages/go.md` and `README.md` say 1.26.
