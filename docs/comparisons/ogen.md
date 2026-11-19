# Comparison with ogen

This page compares this project (twelve harmonised SDKs, shown here through its generated Go SDK) with [ogen](https://ogen.dev), a Go-only OpenAPI v3 generator for typed clients and servers. Every claim was checked against either ogen's documentation, ogen's source, or output that was generated and run on 2026-11-18. A claim that was not checked is marked UNVERIFIED. Nothing is quoted from memory.

## How this was verified

ogen v1.25.0 was installed and run in a `golang:1.24` container, with `GOTOOLCHAIN=auto` because `go install github.com/ogen-go/ogen/cmd/ogen@latest` fails on Go 1.24 (see "Findings from running ogen"). The commands, so anyone can reproduce the run:

```shell
# scratch dir S, spec fixtures copied into it
docker run --rm -e GOTOOLCHAIN=auto -v "$S:/w" -w /w golang:1.24 \
  sh -c 'go install github.com/ogen-go/ogen/cmd/ogen@v1.25.0 && ogen --version && cp /go/bin/ogen /w/ogenbin'
# ogen version v1.25.0 (built with go1.26.11) linux/arm64

# 1. our petstore fixture (src/spec/resources/specs/petstore/openapi.yaml)
./ogenbin --target out/petstore --package petstore --clean petstore.yaml
# 2. a second spec with several 2xx, 4xx, default, headers, oneOf+discriminator (errors.yaml, written for this run)
./ogenbin --target out/errors --package errapi --clean errors.yaml
# 3. the same with every operation carrying the same default response (convenient errors)
./ogenbin --target out/conv --package convapi --clean errors_conv.yaml
# 4. the opt-in client features
./ogenbin --config ogen3.yml --target out/opts --package optapi --clean errors.yaml   # features.enable: client/request/options, client/editors, client/request/validation
# 5. a types spec: allOf, duration, date, time, ipv4, uri, decimal, integer without format, additionalProperties, anyOf
./ogenbin --target out/types --package typesapi --clean types.yaml
# every output was then built with: go mod tidy && go build ./...
```

Our Go SDK was read from `src/spec/resources/generated/go/` at commit `b62e10ee2` and was also run: the golden `pkg/` was copied to a scratch module (tests removed) and driven against an `httptest` server with the same scenarios as the ogen client. Scratch output, the extra spec files and the pulled `golang:1.24` image were deleted afterwards. The ogen module source (v1.25.0) was read from the module cache for the claims marked "source".

Evidence notation: `ogen:` followed by a generated file and function is output of the runs above (line numbers are those of the run and move between runs); `ogen src:` is a file in the v1.25.0 module; `ours:` is a path under `src/spec/resources/generated/go/` with a line; a URL is an ogen documentation page.

## Scope

| | ogen | this project |
|---|---|---|
| Languages | Go only | twelve, harmonised (README.md) |
| Output | typed client, typed server, router, JSON codec, validators, optional stubs (ogen: `paths/server`, `paths/client`, `webhooks/*`; https://ogen.dev/docs/config) | a client SDK only: models, operations, authenticators, errors, transport, README, SKILLS.md, tests (docs/how-it-works.md) |
| Runtime | a shared runtime module `github.com/ogen-go/ogen` that the generated code imports (ogen: `oas_client_gen.go` imports `ogen-go/ogen/{conv,http,ogenerrors,otelogen,uri}` and `go-faster/jx`) | the runtime is generated into the SDK; no generator module is imported (ours: `go.mod`) |
| Shape of the output | one flat `Client` with one method per operation (ogen: every operation is a method on `*Client` in `oas_client_gen.go`, 195 `func (c *Client)` declarations in the petstore run including the send helpers), `Invoker` interface | `Client` with one group per tag (`client.Pet`, `ours: pkg/client.go:57`), `ApiClient` interface |

The two are different products. ogen is a spec-to-Go toolkit that also generates the server; this project is a family of opinionated, batteries-included client SDKs. The comparison below is on the client side.

## Findings from running ogen

1. The petstore fixture does not generate with ogen as it stands. ogen rejects it with `unknown security scheme "petstore_auth"` at `petstore.yaml:854` (the fixture's `addPetTreatment` operation references a scheme that is not declared under `components.securitySchemes`). We generate from it without complaint. To continue, the reference was changed to `petStoreBearer` in a scratch copy.
2. After that, generation stops with `Feature "complex anyOf" is not implemented yet.` The message offers `ignore_not_implemented: ["all"]`. With it, `addPetTreatment` is skipped (`Skipping operation ... complex anyOf not implemented`).
3. With both of those handled, ogen wrote Go that does not parse: `oas_schemas_gen.go:3372:3: expected '}', found '['` (and ten more). Isolated per path, the cause is `PUT /pet/{petId}/avatar/thumbnail`, whose request body is `oneOf: [string/byte, array of string/byte]`: `oas_validators_gen.go:63:7: expected selector or type assertion, found '['`. The two thumbnail operations had to be excluded with `filters.path_regex` to get a module that builds. The rest then compiled.
4. ogen v1.25.0 cannot be installed on the Go it documents. https://ogen.dev/docs/intro says "Go 1.24+", but `go install github.com/ogen-go/ogen/cmd/ogen@latest` on Go 1.24.13 failed with `ogen@v1.25.0 requires go >= 1.26.0`. Our Go floor is also 1.26 (docs/languages/go.md), so this is not a conflict, only a documentation mismatch on their side.
5. ogen rewrites header names: `X-API-Key` becomes `X-Api-Key` (`WARN Header name is not canonical, canonical name will be used`).
6. ogen prints `Convenient errors are not available ... operation has no "default" response` for any spec in which one operation lacks a default response (see the error model section).

## Feature table

Verdicts: BETTER means better than the other side for a Go user; EQUAL means comparable; DIFFERENT means a design choice, not a defect; N/A means out of scope for one side.

| Feature | ogen | this project | Verdict | Evidence |
|---|---|---|---|---|
| Server generation | yes: `Handler` interface, code-generated static router, `NewServer` | no, clients only | ogen wider scope | https://ogen.dev/docs/intro; ogen: `oas_server_gen.go`, `oas_router_gen.go`; README.md |
| Webhooks | client and server generation | out of scope, ignored | ogen better | https://ogen.dev/docs/features; docs/decisions.md "Out of scope" |
| SSE (`text/event-stream`) | client generation with reconnect options; no server side | no streaming at all (#114) | ogen better | https://ogen.dev/docs/features; README of ogen (raw) ; docs/decisions.md "Streaming is not supported" |
| Spec versions | OpenAPI v3; a 3.1.0 document (our petstore) parsed | 3.0 and 3.1 input; 3.1/3.2 completeness is epics #96 and #49 | UNVERIFIED for 3.1 depth in both | ogen: petstore run reached schema generation; docs/how-it-works.md |
| Strictness on invalid specs | stops on a dangling security reference | accepts the same fixture | DIFFERENT | finding 1 |
| Unsupported constructs | `complex anyOf`, `openIdConnect`, `mutualTLS` are `ErrNotImplemented`; the operation is skipped or generation fails | all twelve languages generate every fixture operation | ours better | finding 2; ogen src: `gen/gen_security.go:190` |
| Generates compilable output for every fixture schema | no: one `oneOf` produced Go that does not parse | yes, the golden builds and its tests run in a container | ours better | finding 3 |
| Success response type | per-operation sealed interface when more than one response is declared (`GetPetByIdRes`), concrete type when only one (`GetPetByName` returns `*Pet`) | one decoded type; several 2xx with different schemas return a generated union decoded by actual status | EQUAL | ogen: `oas_interfaces_gen.go`, `oas_client_gen.go:2957`; ours: `pkg/store_api.go:76`, docs/decisions.md "Several success responses decode by status" |
| Non-2xx declared in the spec | returned as a value with `err == nil`, one type per status | returned as an error in a status tree | DIFFERENT (see error model) | ogen: `decodeGetPetByIdResponse` (`return &GetPetByIdNotFound{}, nil`); ours: `pkg/errors/api_error.go:327` |
| Typed body of a declared error | yes when the response has a schema (`*Problem`) | no: `ErrorBody() any`, manual `GetTypedErrorBody(&target)` (#117 open) | ogen better | ogen: `errapi` probe `404: res=*errapi.Problem`; ours: `pkg/errors/api_error.go:297` |
| Undeclared status | `*validate.UnexpectedStatusCodeError` keeping the `*http.Response` | still the status tree (`ClientError`, `ServerError`, ...) with headers and body | ours better | ogen src: `validate/errors.go:74`; ours probe: 418 gives `ClientError` and `ApiError` |
| Typed response headers | yes: `WidgetHeaders{XRequestID OptString; Response Widget}`, `ProblemHeaders{RetryAfter OptInt}` | headers are a `map[string]string` on `ApiResult` | ogen better | ogen: `errapi/oas_schemas_gen.go:396,606`; ours: `pkg/api_result.go:18` |
| Optional and nullable | `OptT`, `NilT`, `OptNilT` with `Set` and `Null` flags, no pointers | `*T` for nullable; an absent value and an explicit null are sent the same way | ogen better (three states) | ogen: `OptNilString{Value, Set, Null}`; docs/decisions.md "Optional and nullable are not told apart" |
| `oneOf` | struct with a `Type` tag and one field per variant, discriminator by explicit mapping, JSON type, or unique field | struct holding an `any`, typed constructors, `As<Variant>`/`Is<Variant>`, discriminator or declaration order | ogen better on inference, ours on constructors | https://ogen.dev/docs/types/sumtype; ogen: `Shape` with `NewCircleShape`; ours: `pkg/models/pet_food.go:41-117` |
| `anyOf` | only some forms; the rest is "complex anyOf" and fails | supported | ours better | finding 2; docs/languages/go.md type table |
| `allOf` | merged into one flat struct (`T` has the fields of `Base` and its own) | plain `allOf` supported; `allOf` with a discriminator is not (#104) | ogen better on discriminator, EQUAL otherwise | ogen: `typesapi` `T`; docs/languages/go.md "Known limits" |
| Enums | named type, constants, `AllValues()`, validated on decode; an unknown value in a response is an error (`validate: invalid: kind (invalid value: huge)`) | same loud failure, `SerializationError` naming the value | EQUAL | ogen: probe `200ok`; ours: probe `200enum`; docs/decisions.md "Unknown enum values fail loudly" |
| `format: date` | `time.Time` (`OptDate`) | `string`, not validated (#126) | ogen better | ogen: `typesapi` `Day OptDate`; docs/languages/go.md |
| `format: time` | `time.Time` (`OptTime`) | `string` | ogen better | ogen: `OptTime`; docs/languages/go.md |
| `format: duration` | `time.Duration`; the docs say it uses Go's duration text, not RFC 3339 | `models.Duration`, protobuf-JSON `"3600s"` | DIFFERENT; ogen wire form UNVERIFIED | ogen: `T.D time.Duration`; https://ogen.dev/docs/features ("Incompatibility"); docs/languages/go.md |
| `ipv4`, `uri` | `netip.Addr`, `url.URL` | `string` | ogen better (our own review item) | ogen: `OptIPv4`, `OptURI`; docs/languages/go.md "Under review" |
| Integer without format | `int` (`OptInt`) | `int32`, a larger value fails to decode | ogen better | ogen: `T.Big OptInt`; docs/languages/go.md "Known limits" |
| `format: decimal` | `decimal.Decimal` (shopspring) | `float64`, lossy | ogen better | ogen: `OptDecimal`; docs/languages/go.md |
| `additionalProperties` | typed map (`OptTExtra`) | untyped map (#103) | ogen better | ogen: `T.Extra OptTExtra`; docs/decisions.md |
| Free-form schema | `jx.Raw` for `{}`, `*TFree` for `type: object` | `any` | EQUAL | ogen: `typesapi` `T` |
| Multipart | request struct with `ht.MultipartFile` fields; the server decodes it too | per-operation `Options` struct | EQUAL on the client | ogen: `AddPetPhotosReq.Files []ht.MultipartFile`; ours: `pkg/pet_api.go:386` |
| Binary bodies | `io.Reader` request and response, so they stream | `[]byte` download, `io.Reader` upload; responses are read fully | ogen better | ogen: `SetPetAvatarReqImageJpeg{Data io.Reader}`; docs/languages/go.md type table |
| Schema constraints and validation | generated `Validate()` for every type; responses are validated on decode by default; request validation is an opt-in feature | none: the server is the authority | DIFFERENT; conflicts with a decision | ogen: `decodeGetPetByIdResponse` calls `response.Validate()`; docs/decisions.md "Models are not validated when you build them" |
| Credentials | typed `SecuritySource` interface, one method per scheme, called per operation | one authenticator per scheme, a `Credentials` struct, environment variables, providers | ours better | ogen: `oas_security_gen.go:314`; ours: docs/languages/go.md "Idiom notes" |
| OAuth 2 | the client only sets `Authorization: Bearer <token>`; no flow, no refresh | client credentials, authorization code with PKCE, password, implicit, OIDC, token manager | ours better | ogen: `securityMachineAuth` sets `"Bearer "+t.Token`; ours: `pkg/auth/oauth/` |
| Security alternatives | per-operation requirement sets evaluated with a bitset; a source may return `ogenerrors.ErrSkipClientSecurity` | first scheme the operation lists that has a credential | EQUAL | ogen: `sendGetPetById` ("satisfied"); docs/decisions.md "One credential per security scheme" |
| Retries | none in generated or runtime code (no retry code found; only SSE reconnects) | on by default: 2 retries, backoff with jitter, `Retry-After`, end-to-end budget | ours better | ogen: grep of generated client; https://ogen.dev/docs/faq; ours: `pkg/base_api.go:374,399,437`; docs/decisions.md |
| Timeouts | `context` deadline or the `http.Client` you pass | default 10 s end-to-end budget, `TransportOptions.Timeout`, per-call `WithTimeout` | ours better | ours: `pkg/transport_options.go:318`, `pkg/request_option.go:37` |
| Proxy, TLS, redirects | whatever the supplied `ht.Client` does | `TransportOptions` (proxy, CA, verify, redirects, user agent) | ours better | ours: `pkg/transport_options.go:268,326` |
| Per-call options | opt-in feature `client/request/options`: `WithRequestClient`, `WithServerURL`, `WithEditRequest`, `WithEditResponse` | always on: `WithTimeout`, `WithHeaders`, `WithMaxRetries`, `WithRequestContentType` | DIFFERENT | ogen: `optapi/oas_client_gen.go:52-80`; ours: `pkg/request_option.go` |
| Request and response hooks | opt-in `client/editors`: `WithRequestEditor`, `WithResponseEditor`; server middleware | `http.RoundTripper` or `ApiClient` | ogen better (response hook) | ogen: `optapi/oas_cfg_gen.go:268-290`; https://ogen.dev/docs/concepts/middlewares |
| Server URL and variables | `NewClient(serverURL string, ...)`; the spec's `servers` and their variables are not used; `WithServerURL(ctx, u)` overrides | `Servers`, variables validated against their enum, per-operation server variants | ours better | ogen: `oas_client_gen.go:522,543`; ours: `pkg/servers.go:31-72`, `pkg/pet_api.go:121` |
| OpenTelemetry | spans per operation, a request counter, an error counter and a duration histogram, with `WithTracerProvider`, `WithMeterProvider`, `WithAttributes`; on by default | only injects `traceparent` and `tracestate` through a propagator you register; no spans, no metrics (#108 open) | ogen better | ogen: `sendGetPetById` (`Tracer.Start`, `c.duration.Record`); ours: `pkg/trace_context_util.go:31,68` |
| JSON codec | generated encode and decode with `go-faster/jx`, no reflection | `encoding/json`; single-pass decode is #107 | ogen better on design; speed UNVERIFIED (no benchmark was run) | https://ogen.dev/docs/intro; ogen: `oas_json_gen.go`; docs/languages/go.md |
| Config file and filters | `ogen.yml` with `features`, `filters.path_regex`, `filters.methods`, `ignore_not_implemented`, `convenient_errors`, `infer_types` | generator options per language; normalizer rules | DIFFERENT | https://ogen.dev/docs/config; README.md |
| Extensions | `x-ogen-*` names and `x-oapi-codegen-extra-tags` | none that a spec author sets; the normalizer is the extension point | DIFFERENT | ogen README (raw); AGENTS.md "Never depend on a spec-sourced vendor extension" |
| Generated docs | Go doc comments from descriptions; deprecation notes | Go doc comments, a README and a `SKILLS.md` per SDK, plus a generated test suite | ours better | ogen: `Pet.Status` comment "Deprecated: schema marks this property as deprecated."; ours: `README.md`, `SKILLS.md` |
| Footprint of the output | 56,115 lines of Go for the petstore minus three operations, runtime in a separate module | 20,270 non-test lines for the whole petstore fixture, runtime included | DIFFERENT | `wc -l` on both outputs; ours counted with `find ... ! -name '*_test.go'` |
| Dependencies of the output | `ogen`, `jx`, `go-faster/errors`, `decimal`, `otel`, `otel/metric`, `otel/trace` (7 direct) | `brotli`, `zstd`, `uuid`, `otel`, `otel/trace` (plus test-only modules) | EQUAL | ogen: `out/types/go.mod`; ours: docs/languages/go.md "Dependencies" |
| Idempotent close, graceful shutdown | none generated | `Close()` idempotent, `ErrClientClosed` | ours better | ours: `pkg/client.go:71`; ogen: no such method on `Client` in `oas_client_gen.go` |
| Support for other languages | none | eleven more, same API shape | N/A | README.md |
| Performance (allocation, throughput) | claimed: "no reflection" | not measured | UNVERIFIED on both sides | no benchmark was run |

## What ogen does better

1. **Typed responses and typed error bodies.** A declared 404 with a schema arrives as `*Problem`, with the fields decoded; a declared 429 arrives as `*ProblemHeaders{RetryAfter: OptInt{Value: 7, Set: true}, Response: ...}`. In our SDK the same call returns an `*ApiError` whose body is `map[string]any` (probe output: `errorBody=map[code:nf message:no]`), and the caller decodes it by hand with `GetTypedErrorBody`. This is issue #117 and part of #123.
2. **Typed response headers**, declared in the spec, as fields (`WidgetHeaders.XRequestID`).
3. **Richer scalar types**: `time.Time` for `date` and `time`, `netip.Addr`, `url.URL`, `int` for an unbounded integer, `decimal.Decimal`. Every one of these is a known gap in docs/languages/go.md.
4. **A three-state optional type** (`OptNilString{Set, Null}`), which our decisions list as a possible later addition.
5. **OpenTelemetry** with real spans and metrics. Ours injects a header from a function the user registers, and the comment above `SetTraceContextPropagator` (`pkg/trace_context_util.go:56-62`) says the propagator receives `context.Background()`. That comment also says "API operations take no context.Context", which the generated operations (`GetPetById(ctx context.Context, ...)`, `pkg/pet_api.go:1652`) contradict: the comment is stale.
6. **Response hooks and per-call overrides of the HTTP client and server URL** (opt-in features).
7. **Streaming bodies** (`io.Reader` for binary responses) and SSE client generation.
8. **Server and webhooks** from the same spec.
9. **Discriminator inference** for `oneOf` without an explicit discriminator (JSON type, unique field).
10. **Raw escape hatch on an undeclared status**: `UnexpectedStatusCodeError.Payload` is the `*http.Response` with its body restored (ogen src: `validate/errors.go:74-85`).
11. **A raw body on decode failures**: `ogenerrors.DecodeBodyError{ContentType, Body, Err}`. Our `SerializationError` carries only a message and a cause (`pkg/errors/serialization_error.go`), so a 200 with a broken body loses the status, the headers and the body (probe `200bad`: `Serialization=true Api=false`).

## What we do better

1. **Transport behaviour is part of the SDK.** Retries with backoff, `Retry-After` and an end-to-end budget (ours probe: a 429 made 3 requests, `hits=3`; ogen has no retry code), per-call `WithTimeout`/`WithMaxRetries`, proxy, CA, redirect and user-agent settings, an idempotent `Close`.
2. **Authentication.** Full OAuth 2 and OIDC authenticators with a token manager, credentials from arguments and environment, providers for rotating tokens. ogen puts a bearer token on the request and leaves everything else to a `SecuritySource` you write; `openIdConnect` and `mutualTLS` are not implemented (ogen src `gen/gen_security.go:190`).
3. **Server variables.** The spec's `servers` with variables become validated `ServerConfiguration` values and per-operation server variants. ogen's client takes a plain URL string.
4. **A status-class error tree.** `errors.As(err, new(*ClientError))` catches any 4xx, and `*ApiError` any HTTP failure, whatever the status and whether or not the spec declared it. ogen has no class level: a caller switches on generated types and handles `UnexpectedStatusCodeError` separately. Request method and path travel on every error (`pkg/errors/api_error.go:92-141`), which ogen errors do not carry.
5. **Coverage and robustness of generation.** `anyOf` works, every fixture operation generates and compiles, and a spec with a dangling reference is not an obstacle.
6. **The same SDK in twelve languages**, with one error contract, one set of decisions and one set of goldens.
7. **A finished package**: README, SKILLS.md, generated tests, a manifest, with no runtime module to depend on.

## Adopt or not

Effort: S is one template change per language, M is a shared generator change with all twelve touched, L changes the public API in several places. "Conflict" names the settled decision in docs/decisions.md or the shared AGENTS.md.

| Priority | Idea from ogen | Effort | Languages | Conflict with a decision? |
|---|---|---|---|---|
| 1 | Keep status, headers and the raw body on a decode failure (`DecodeBodyError`), so a broken 2xx is diagnosable. Today a `SerializationError` has none of them. | S | all twelve (the contract is shared) | No. Fits "Errors say which request failed". |
| 2 | Typed error bodies decoded from the declared schema, with the leaf type generated per (operation, status, schema) rather than per schema (see the error section). | M | all twelve; Go via a typed accessor on the leaf, Rust via an enum | No. It is #117 and #123 as already planned. |
| 3 | Fix the stale `SetTraceContextPropagator` comment, then real OpenTelemetry spans and metrics with tracer and meter providers (#108). Use ogen's attribute set (`otelogen.OperationID`, `semconv` URL template) as the reference. | M | Go first; the others have their own tracing story | No. #108 is open. |
| 4 | Typed scalars for `date`, `time`, `ipv4`/`ipv6`, `uri`, integer without format. | M | Go (listed under review in docs/languages/go.md); the other languages already have native date types (docs/types.md) | Needs agreement across languages, as the Go page already says. #126 covers `date`. |
| 5 | Typed response headers declared in the spec (`WidgetHeaders`, `RetryAfter`). Expose them next to the body in the `WithHTTPInfo` result. | M | all twelve | Touches "Repeated response headers are joined" (headers stay lower-cased strings there); a typed layer would be additive. |
| 6 | A three-state optional (absent, null, value) for PATCH-style APIs. | L | all twelve | Yes: "Optional and nullable are not told apart on the way out" says absent and null are sent the same way. The decision already mentions a three-state type "may come later", so this needs the owner's go. |
| 7 | A response hook next to the request headers (ogen `WithResponseEditor`). | S | all twelve through the transport contract | No. Check against "Configuration and transport options are separate". |
| 8 | `oneOf` discriminator inference by JSON type and unique field. | M | all twelve | No. Today's "tried in declaration order" is ambiguous for overlapping variants. |
| 9 | Streaming binary bodies and SSE. | L | all twelve | Yes: "Streaming is not supported" and #114 is open. |
| 10 | Response validation against schema constraints (ogen validates responses by default). | L | all twelve | Yes: "Models are not validated when you build them" and "The server is the authority". Not recommended. |
| 11 | Server and webhook generation. | L | n/a | Yes: out of scope. Not recommended. |
| 12 | Convenient errors as a spec-wide opt-in. | not needed | | Generalised by #123 (see below). |

Not adopted: ogen's value-returning style for declared non-2xx responses. It would make the Go SDK differ from the other eleven, whose contract is that a failure is an error (AGENTS.md "The error contract"), and it would hide HTTP failures from `if err != nil`.

## Error model: ogen's response sum types compared with the status-class tree and issue #123

### What ogen generates

For an operation with several declared responses, ogen generates one sealed interface, one type per declared status, and decodes the status that arrived into that type. The method returns `(Res, error)`. The first excerpt is from the petstore run (`GET /pet/{petId}`, which declares 200, 400 and 404):

```go
// oas_interfaces_gen.go
type GetPetByIdRes interface {
	getPetByIdRes()
}

// oas_schemas_gen.go
// GetPetByIdBadRequest is response for GetPetById operation.
type GetPetByIdBadRequest struct{}
func (*GetPetByIdBadRequest) getPetByIdRes() {}

// GetPetByIdNotFound is response for GetPetById operation.
type GetPetByIdNotFound struct{}
func (*GetPetByIdNotFound) getPetByIdRes() {}

func (*Pet) getPetByIdRes() {}

// oas_client_gen.go
func (c *Client) GetPetById(ctx context.Context, params GetPetByIdParams) (GetPetByIdRes, error)

// oas_response_decoders_gen.go, decodeGetPetByIdResponse
case 404:
	// Code 404.
	return &GetPetByIdNotFound{}, nil
...
return res, validate.UnexpectedStatusCodeWithResponse(resp)
```

Observations from this output:

1. **A declared error is a value, not an error.** `err` is `nil` for a 404 and the caller must type-switch on `GetPetByIdRes`. Forgetting the switch means treating a 404 as success.
2. **A response with no schema is an empty struct.** `GetPetByIdNotFound struct{}` has no status, no headers, no body. Everything the server said is gone. An operation with a single declared response returns the concrete type (`GetPetByName` returns `*Pet`), and any non-2xx there is the untyped `UnexpectedStatusCodeError`.
3. **Types are per operation and status, never per schema alone.** When two statuses reuse one schema, ogen generates distinct defined types, so the status is never lost (spec `dup.yaml`, 400 and 404 both `$ref: P`):

```go
type GetWBadRequest P
func (*GetWBadRequest) getWRes() {}

type GetWNotFound P
func (*GetWNotFound) getWRes() {}
```

4. **Headers turn a response into a wrapper.** A response that declares headers becomes a struct with the headers and a `Response` field.
5. **`default` becomes a status-carrying wrapper.** In the second spec (`errors.yaml`) the `default` response is generated as:

```go
// ProblemStatusCode wraps Problem with StatusCode.
type ProblemStatusCode struct {
	StatusCode int
	Response   Problem
}
func (*ProblemStatusCode) createWidgetRes() {}
func (*ProblemStatusCode) getWidgetRes()    {}
```

The `GetWidget` result interface has these members, from the output: `*Widget`-with-headers (`*WidgetHeaders`), `*Job`, `*Problem` (404), `*ProblemHeaders` (429) and `*ProblemStatusCode` (default). The 204 variant of `CreateWidget` is `*CreateWidgetNoContent` (an empty struct).

### Measured behaviour

A client from `errors.yaml` was run against an `httptest` server (`GetWidget`), and our Go SDK was run against the same kinds of responses (`GetPetById`). Raw output, abridged:

```text
ogen   404 declared with schema   res=*errapi.Problem {Code:nf Message:no}                      err=<nil>
ogen   429 declared with header   res=*errapi.ProblemHeaders {RetryAfter:{Value:7 Set:true} Response:{Code:rl ...}} err=<nil>
ogen   418 (only `default`)       res=*errapi.ProblemStatusCode {StatusCode:418 Response:{Code:tea Message:pot}}  err=<nil>
ogen   200 with broken JSON       res=<nil> err=decode response: decode application/json: decode Widget: ... (DecodeBodyError=true)
ogen   200 with unknown enum      res=<nil> err=decode response: validate: invalid: kind (invalid value: huge)
ogen   204 where only 200 had a body   res=<nil> err=decode response: default (code 204): decode application/json: decode Problem: "{" expected: unexpected EOF
ogen   connection refused         res=<nil> err=do request: Get "...": dial tcp 127.0.0.1:1: connect: connection refused

ours   404 {"code":"nf"...}       pet=<nil> err="API returned status code 404 ..."  NotFound=true Client=true Api=true  errorBody=map[code:nf message:no]
ours   418                        err="API returned status code 418 ..."            NotFound=false Client=true Api=true
ours   429 with Retry-After: 0    hits=3 (retried twice), then ClientError/ApiError
ours   200 with broken JSON       err="failed to deserialize JSON: ..."             Serialization=true Api=false
ours   200 with unknown enum      err="... unexpected value huge for enum PetStatusEnum"  Serialization=true
ours   204 where a body was expected   err="expected a response body for GetPetById but received none"  Api=true status=204
ours   connection refused         err="request failed: Get ..."                     NetworkError=true
```

Two things to note. The unknown-enum behaviour is the same in both, so the settled decision "Unknown enum values fail loudly" is also ogen's behaviour. And ogen's transport failure, decode failure and validation failure are all untyped except `DecodeBodyError` and `UnexpectedStatusCodeError` (found with `errors.As`): there is no `NetworkError`, no `Timeout`, no class a caller can catch.

### Convenient errors, and where they stop

ogen's own answer to "I want an error, not a variant" is "convenient errors" (https://ogen.dev/docs/concepts/convenient_errors; ogen src `gen/reduce.go:17-110`). It applies only when every operation declares an identical `default` response with a single JSON media type. In the `errors_conv.yaml` run, the single-success operation changed shape:

```go
// listWidgets: one 2xx and the shared default
func (c *Client) ListWidgets(ctx context.Context) ([]Widget, error)

func (s *ProblemStatusCode) Error() string {
	return fmt.Sprintf("code %d: %+v", s.StatusCode, s.Response)
}
// decodeListWidgetsResponse ends with: return res, errors.Wrap(defRes, "error")
```

so a failure is a Go error holding the typed body and the status. Operations with more than one declared response kept the sum type (`GetWidget` and `CreateWidget` still returned `GetWidgetRes` and `CreateWidgetRes`), and the declared 404 and 429 variants of `GetWidget` stayed values with `err == nil`. In the run without the shared default (`errors.yaml`), the log line was `Convenient errors are not available ... operation has no "default" response`, and nothing changed. The feature is a spec-wide switch with an all-or-nothing precondition, and it covers only `default`.

### Comparison with the plan for #123

| Aspect | ogen | status-class tree today | #123 as written |
|---|---|---|---|
| Is a declared failure an error? | no, a value (except the convenient `default`) | yes | yes |
| Catch "any client error" | not possible; no class type | `errors.As(err, new(*ClientError))` | kept |
| Catch one status | type switch on a per-operation type | `errors.As(err, new(*NotFoundError))` | kept |
| Catch one declared schema | the type switch already does this, per operation | not possible | a leaf type per schema |
| Body typed by the declared schema | yes | no (`any`) | yes (with #117) |
| Undeclared status | `UnexpectedStatusCodeError` with the `*http.Response` | typed by class, headers and body present | falls back to the status type |
| Headers on the error | typed, if declared; lost for a response without schema | map of lower-cased strings | kept |
| Request method and path | no | yes | kept |
| The operation says which errors it can return | yes, in the return type (`GetPetByIdRes`) | no | the signature or documentation |
| Network, timeout, serialization | untyped Go errors | `NetworkError`, `NetworkTimeoutError`, `SerializationError` | kept |

What this means for the plan:

1. **The Go design in #123 (an error interface, embedded structs, `errors.As` at every level) is the right shape and ogen does not have it.** ogen has per-status precision and no families. The closest thing ogen has to the plan is `ProblemStatusCode`: an error that carries the status and the typed body. #123 generalises that to every declared status, and to a tree.
2. **The leaf must be keyed by (operation, status, schema), not by schema.** ogen's `type GetWBadRequest P` and `type GetWNotFound P` exist because one schema can be declared on several statuses. If our generator names a leaf after the schema alone (the issue's example, `ProjectNotFoundError`), a schema declared for both 400 and 404 would need to be a `BadRequestError` and a `NotFoundError` at once, which an embedding tree cannot express. Name the leaf after the schema when it is declared for one status in the whole spec, and otherwise generate one leaf per status that embeds the status type and shares the body type.
3. **The issue's own example needs a precondition checked.** "A 404 whose body is a project-not-found error and another 404 whose body is a user-not-found error" cannot be two responses of one operation: the `responses` object is a map keyed by status code, so one operation has at most one 404 per content type. The two shapes that really occur are (a) two operations, each with its own 404 schema, and (b) one 404 whose schema is a `oneOf`, usually with a discriminator. ogen handles (a) with two per-operation types and (b) with a sum type inside one response (its `Shape` sum type with `Type` and `IsCircle()` helpers). The fixture requested in #123 ("an operation with two declared 404 schemas") should be both shapes, or the second will not be exercised. UNVERIFIED: that ogen produces a usable type for a 404 whose body is `oneOf` (the sum type was verified for a request body only, `Shape` in `errors.yaml`).
4. **Keep failures as errors in Go.** ogen's value-returning style is visible in the measured output above: a 404 is `err == nil`. That conflicts with the error contract in AGENTS.md ("every SDK raises the same error for the same failure") and with eleven other languages, so it should not be copied. What can be copied is that the type of the failure says what the operation can return. In Go that has to be documentation (a godoc list of the error types an operation can return) because the signature stays `(T, error)`; in Rust it can be an enum per operation, which is the shape #113 and #123 already propose.
5. **Keep what the tree already has that ogen lacks**: the class level, request method and path, headers and the raw body on every failure, retry classification by status class (`isRetryableStatus`, `pkg/base_api.go:437`), and a typed error for transport failures.
6. **Add what ogen has that the tree lacks**: the raw body and the status on a body that fails to decode (`DecodeBodyError`), and a typed body on the leaf. Both belong in the shared contract, not in Go alone.
7. **Multiple 2xx** is the same design in both: ogen returns one type per status behind a sealed interface, we return a generated union decoded by the actual status (docs/decisions.md). Neither guesses from the body shape.

## Unverified

- Whether ogen's `format: duration` text is `1h0m0s` on the wire (the docs say Go's format; the generated type is `time.Duration`, no wire test was run).
- Throughput and allocation claims of both sides; no benchmark was run.
- ogen's behaviour for a declared error whose body is a `oneOf` (only a request-body sum type was generated).
- Cookie and OpenID Connect handling in ogen's generated client (the petstore run only kept the schemes used by the operations that survived the filters; `openIdConnect` and `mutualTLS` are `ErrNotImplemented` in `gen/gen_security.go:190`, cookie keys are handled in `gen/gen_security.go:47`, but no generated cookie client was inspected).
- Depth of OpenAPI 3.1 support in ogen (a 3.1.0 document was accepted; features specific to 3.1 were not tested) and in this project (epics #96 and #49).
- Whether `oneOf` inference by unique field behaves as documented for overlapping variants.
- ogen's server-side generation was not run; only the existence of `oas_server_gen.go` and `oas_router_gen.go` was checked.
