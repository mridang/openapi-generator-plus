# Go

The `go-plus` generator (`BetterGoCodegen`) produces a Go module with one file per API tag, one struct per model, an `ApiClient` interface with a `DefaultApiClient`, authenticators, typed errors and a `_test.go` suite. It uses the standard library `net/http`. For the rules shared by all twelve languages see [how it works](../how-it-works.md), for the type table across languages see [types](../types.md), and for settled cross-language choices see [decisions](../decisions.md).

## Versions and support policy

The generated `go.mod` declares `go 1.26.0`, and the generated README says Go 1.26 or newer. The floor follows one rule: it is never newer than the oldest Go release that has not reached end of life. When that release reaches end of life the floor moves up. The generator itself builds and checks the golden module in a Go 1.26 toolchain image.

The module also builds for `GOOS=wasip1 GOARCH=wasm`.

## Type mapping

| OpenAPI type and format | Go type | Notes |
|---|---|---|
| `string` | `string` | Also used for `email`, `hostname`, `password` and unknown formats. |
| `string` / `ipv4`, `ipv6` | `netip.Addr` | A leading zero, a prefix length, a zone id, brackets, whitespace and the wrong family fail the decode with a `*SerializationError`; an address is written in canonical text. |
| `string` / `date` | `string` | The standard library has no calendar-date type. The value is not validated. |
| `string` / `date-time` | `models.DateTime` | A `time.Time` wrapper. The wire form has a numeric offset (never `Z`) and a fixed three-digit millisecond fraction. |
| `string` / `time` | `string` | No standard library type. `iso8601.go` ships `FormatTimeOfDay` and `ParseTimeOfDay` for callers who want a `time.Time`. |
| `string` / `duration` | `models.Duration` | A `time.Duration` wrapper. The wire form is the protobuf-JSON string such as `"3600s"`, not ISO 8601. |
| `string` / `uuid` | `uuid.UUID` | From `github.com/google/uuid`. Written in lower case. |
| `string` / `byte` | `[]byte` | `encoding/json` encodes it as padded standard base64. |
| `string` / `binary`, file bodies | `[]byte` for downloads, `io.Reader` for uploads | Responses are read fully into memory. |
| `string` / `uri` | `string` | |
| `integer` / `int32` | `int32` | |
| `integer` / `int64` | `int64` | |
| `integer` with no format | `int64` | |
| `number` / `float` | `float32` | |
| `number` / `double`, `number` with no format | `float64` | |
| `number` / `decimal` | `float64` | Lossy for exact decimal values such as money. This is documented in the generated README. |
| `boolean` | `bool` | |
| `array` | `[]T` | |
| `array` with `uniqueItems` | `models.Set[T]` | Slice-backed, `T` is `comparable`. Scalar members are written sorted so the bytes are stable. |
| `object` with `additionalProperties` | `map[string]T` | `T` is the declared value type; a free-form bucket keeps the any-type |
| free-form `object`, any type | `any` | |
| nullable | `*T` | A required nullable property keeps its key in the output. |
| `enum` | named string type with constants | Unmarshalling gives the `<Enum>UnknownDefaultOpenApi` constant for a value outside the enum (D117), and marshalling rejects it and any other value outside the enum. A `$ref` to an enum with a `default` next to it starts at that member, whether the model is built with its `New` function or decoded without the key. |
| `oneOf`, `anyOf` | struct holding an `any` value | Has `MarshalJSON` and `UnmarshalJSON`. Variants are tried in declaration order, or chosen by the discriminator when there is one. |

## Dependencies

Runtime modules, from the generated `go.mod`:

| Module | Version | Used for |
|---|---|---|
| `github.com/andybalholm/brotli` | v1.2.1 | Decoding `br` responses |
| `github.com/klauspost/compress` | v1.18.6 | Decoding `zstd` responses |
| `github.com/google/uuid` | v1.6.0 | The `uuid` type |
| `go.opentelemetry.io/otel`, `otel/metric` and `otel/trace` | v1.47.0 | Trace-context propagation, and the `pkg/otelhook` adapter (the core package imports none of them) |

HTTP is the standard library. Other requirements in the manifest are for the generated tests and tooling (`testcontainers-go`, the OpenTelemetry SDK, and a `tool` block with `gotestsum`, `staticcheck`, `gofumpt` and `gocover-cobertura`). Dependencies that need cgo are not accepted.

## Options

| Option | Default | What it does |
|---|---|---|
| `packageName` | none | Name of the generated module. |
| `packageVersion` | `1.0.0` | Version written to the README. |
| `repositoryUrl` | none | Source repository URL, written to the README. |
| `homepageUrl` | none | Project homepage, written to the README. |
| `authors` | none | Comma-separated author names, written to the README. |
| `documentationUrl` | none | Documentation URL, written to the README. |
| `issueTrackerUrl` | none | Issue tracker URL, written to the README. |
| `envVarPrefix` | the API title in upper snake case without a trailing `SDK`, else `API` | Prefix of the credential environment variables, see Idiom notes. The same option and default in every language. |

## Idiom notes

- Every operation takes `ctx context.Context` as its first parameter. Cancelling it cancels the call. `Close()` on the client is graceful and idempotent: it marks the client closed and releases the connections the SDK created, a request already in flight finishes, a later request returns `ErrClientClosed`, and a transport you supplied is never closed.
- Errors are values. Caller mistakes are exported sentinels you match with `errors.Is` (for example `ErrMissingRequiredParameter`, `ErrInvalidTimeout`, `ErrTransportConflict`, `ErrClientClosed`). SDK errors implement `Unwrap` and `As`. Error strings start in lower case, as `staticcheck` requires.
- Optional operation parameters live in an `opts.<Operation>Options` struct. Per-call settings (`WithTimeout`, `WithHeaders`, `WithMaxRetries`, `WithRequestContentType`) are trailing variadic `RequestOption` values. Every operation takes them, including one with no parameters and no security, which has no options struct at all; Go does not generate an empty one, because the variadic tail already carries `timeout`, `headers` and `maxRetries`.
- Credentials come from an argument, then the environment. The constructor of each per-scheme authenticator (for example `auth.NewPetStoreBasicAuthenticator`) takes its credentials as strings; an empty string is read once, when the authenticator is built, from the variable of that scheme, and an empty variable counts as unset. A credential found in neither place returns an error that matches `auth.ErrMissingCredential` (the root package re-exports it as `ErrMissingCredential`) and names the variable, so every per-scheme constructor returns `(T, error)`. A Bearer scheme also has `New<Scheme>AuthenticatorWithProvider(host, provider)`, which calls a `func(ctx) (string, error)` for every request and reads no variable, so a provider you pass always outranks the environment (D108); `auth.NewApiKeyAuthenticatorWithProvider(host, name, provider, location)` does the same for an API key in a header, a query parameter or a cookie. The variable is `{PREFIX}_{FIELD}` in upper snake case (`ACME_TOKEN`, `ACME_CLIENT_SECRET`), or `{PREFIX}_{SCHEME}_{FIELD}` when two schemes have a field of the same name; the generated README lists them. The host is not read from a per-scheme variable. `New<Client>WithToken(host, accessToken, ...)` reads `{PREFIX}_BASE_URL` and `{PREFIX}_TOKEN` the same way.
- One client can hold a credential for every security scheme: `New<Client>WithCredentials(host, Credentials{...}, transport)` takes a `Credentials` struct with one optional `Authenticator` field per scheme, named after the scheme (`PetStoreBearer`, `ApiKeyHeader`). Each operation uses the scheme its spec requires: the first scheme it accepts, in spec order, that has a credential, so a credential is never sent to an operation that does not accept its scheme. An operation declared without security sends none. There is no per-call override of the scheme, so an Options struct has no auth field. `New<Client>(authenticator, transport)` keeps a single authenticator for every secured operation.
- A cookie parameter is sent as you give it when every character is a legal RFC 6265 cookie-octet, so percent-encode a value yourself first; a value with an illegal character (space, `;`, `,`, `"`, `\`, a control character or non-ASCII) fails with `ErrInvalidCookie` before anything is sent.
- Building a model never validates: `New<Model>` and the `With` methods accept any value, including an unknown enum member. Validation happens only when a payload is decoded, and an unknown enum value decodes to the catch-all constant (D117).
- Configuration is built with `NewConfigurationBuilder()` and `NewTransportOptionsBuilder()`.
- You can supply your own HTTP stack with `TransportOptionsBuilder.HttpClient` (anything with `Do(*http.Request)`) or `RoundTripper`. Combining that with the SDK's own proxy, TLS or pool settings fails `Build()` with `ErrTransportConflict`, and the SDK never closes a client you supplied.
- Testing your code: every group has `<Group>ApiInterface` and the `Client` group fields are typed as those interfaces, so `&pkg.Client{Pet: fake}` is a client of fakes whose `Close()` returns nil. There is no client interface, because Go interfaces carry methods and not fields. A fake embeds the group interface so only the operations it fakes are written.
- **Call hook.** `CallHook` is one small hook around every operation call: `Start(ctx, CallInfo, headers map[string]string) (CallObserver, error)` runs once per call after the headers are final and may edit them, and returns a `CallObserver` (or `nil`) whose `Finish(CallOutcome)` runs exactly once on every way the call can end. Go returns the error `Start` would throw: it reaches the caller unchanged, nothing is sent and `Finish` is not called; a panic in `Finish` is recovered. Set it with `NewConfigurationBuilder().CallHook(hook)` or, on a client, the trailing `WithCallHook(hook)` option of the `New<Client>` constructors; `CallHookFunc` adapts a function and `ChainCallHooks(a, b)` combines hooks. `CallInfo` has `OperationID` (as written in the spec), `Method`, `URLTemplate`, `URL` (no query string or fragment) and `ServerAddress`; `CallOutcome` has `StatusCode` (`0` when no response arrived), the lower-cased `Headers` (`nil` when none arrived) and the `Error` the caller receives. The hook runs above the transport, so it also fires with your own `HttpClient`.
- **OpenTelemetry adapter.** `otelhook.New(opts...)` in `pkg/otelhook` (options `WithTracerProvider`, `WithMeterProvider`, `WithPropagator`) is a `CallHook` that records one span (named with the operation id, kind client), the counter `http.client.request.count` (unit `{request}`) and the histogram `http.client.request.duration` (unit `s`) per call, with `http.request.method`, `url.template`, `server.address`, `http.response.status_code` and `error.type`. It uses the OpenTelemetry API only and writes `traceparent` itself.
- Initialisms are upper case (`URL`, `ID`, `HTTP`).
- A model struct can be created without its required properties. Decoding enforces them.
- With `generateExamples` on, the module ships `examples/quickstart/main.go`, a `package main` that builds a client from `{PREFIX}_BASE_URL` and `{PREFIX}_TOKEN`, calls the example operation and closes the client; `make examples` vets and builds everything under `examples/` and `make examples-run` runs the quickstart. `go vet ./...` and `staticcheck ./...` cover it, and `make docs` skips it.

## Recipes

These are answers to questions that come up often, each built from what the generated SDK already has. None of them adds API.

### Hooks around every call

The call hook added by [#158](https://github.com/mridang/openapi-generator-plus/issues/158) is the one place to edit the request headers before the send and to observe the status or the error afterwards, for every operation and for the client as a whole. It is optional and does nothing when it is not set. It runs once per operation call however many sends happen inside it (retries, the one replay after a `401`), once for the plain and the `WithHttpInfo` variant alike, and also when you supply your own transport. `start` receives the call information (`operationId` as written in the spec, `method`, `urlTemplate`, `url` without its query string, `serverAddress`) and the final headers, which it can read and replace, and it returns the observer to call when the call ends or nothing. The observer's `finish` receives the status code (0 when no response arrived), the lower-cased response headers (none when no response arrived) and the error the caller receives (none on success). An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed; the hook never changes the result or the error. `CallHook` is the name to look for in this SDK.

```go
hook := petstore.CallHookFunc(func(ctx context.Context, info petstore.CallInfo, headers map[string]string) (petstore.CallObserver, error) {
    headers["X-Caller"] = "billing"
    return nil, nil
})
client, err := petstore.NewClientWithToken("https://api.example.com", "your-token", nil, petstore.WithCallHook(hook))
```

To see every send of a call rather than the call as a whole (for example each retry), wrap the transport you hand to the SDK instead: see "Custom HTTP client" in the generated README. A transport you supply is never closed by the SDK, and the hook above still fires with it.

### Serving another region or tenant

A client is cheap, and its host, credentials, retry settings and connections belong to it. To call another region or another tenant, build another client; there is no per-call server URL or per-call HTTP client, and the per-call options stay `timeout`, `headers` and `maxRetries`.

```go
eu, err := petstore.NewClientWithToken("https://eu.api.example.com", "eu-token", nil)
if err != nil {
    return err
}
defer eu.Close()
us, err := petstore.NewClientWithToken("https://us.api.example.com", "us-token", nil)
if err != nil {
    return err
}
defer us.Close()
```

Closing one client does not affect the other.

### Renaming types, fields and parameters

The names come from the specification, so the seam is the normaliser: the `RUN_SCRIPT` rule runs your own JavaScript over the document before generation (see [how it works](../how-it-works.md#renaming-with-a-script)). A schema renamed to `Animal` is generated as `pkg/models/animal.go`, and this language's own casing, escaping of reserved words and file naming are applied to the new name like to any other. The SDK has no configuration map of names and never reads a vendor extension for a name. The name of a property or a parameter is also its name on the wire, so renaming one in the script renames it in the requests and responses as well: there is no rename that changes only the SDK name.

### Status, headers and the raw body

Every operation has a `...WithHTTPInfo` variant that returns the status code, the lower-cased response headers, the raw body and the decoded data. Every failure that carries an HTTP response is an `ApiError` that holds the status code, the response headers and the response body. A live response object is not exposed, because that would need streaming (see [decisions](../decisions.md)); to see or change what goes over the wire, use the call hook above or the transport you supply.

```go
result, err := client.Pet.AddPetWithHTTPInfo(ctx, pet, nil)
if err == nil {
    status, etag, raw := result.StatusCode, result.Headers["etag"], result.RawBody
    _, _, _ = status, etag, raw
}

var apiErr *petstore.ApiError
if errors.As(err, &apiErr) {
    fmt.Println(apiErr.StatusCode(), apiErr.ResponseHeaders(), apiErr.ResponseBody())
}
```

## Known limits

- `format: date` and `format: time` are plain strings, so they are not validated.
- `format: decimal` and `number` are `float64` and lose exactness.
- Schema constraints (`pattern`, `minLength`, `maximum` and so on) are not enforced by the client. The server owns validation.
- `allOf` with a discriminator is not implemented yet. Only `oneOf` with a discriminator is.
- Responses are buffered in full. Streaming a download is not supported.
- SOCKS proxies are not supported.
- Callbacks and webhooks, XML bodies, Link Objects and mutual TLS are out of scope in every language.
- Operations are blocking only, as a plain function call that blocks until the call finishes or the context ends, by decision D111. There is no asynchronous variant of an operation, because each SDK follows the style of its own language. Run the call on your own thread or task when you need concurrency.
- An optional and nullable property has no third state: an absent value and an explicit `null` are the same, by decision D112. A three-state type for PATCH-style APIs is declined for now.
- An `integer` with no format is 64 bits, the same as `int64`, by decision D113 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)). A value above 2^31, such as a large identifier, decodes exactly.

## Under review

These are ideas from a type review in November 2026. None of them is decided, and none changes the generated SDK today.

- Add SDK-owned `models.Date` and `models.TimeOfDay` types so `date` and `time` are validated. This needs agreement with the other SDKs because it adds public API.
- `url.URL` for `uri`, once it is shown to keep the text unchanged.
- Replace `google/uuid` with the `uuid` package the review says Go 1.27 adds. The review could not confirm its API, and using it would raise the floor to 1.27, so it is not before Go 1.26 reaches end of life.
- Run the full golden suite on Go 1.27 before moving the floor or the toolchain. The review notes that `encoding/json` is backed by json/v2 in that release, which is stricter and may change error text the SDK's tests assert on.
- Whether the duration wire form should stay protobuf-JSON or move to ISO 8601, across all languages.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. `InjectTraceContext(ctx, headers)` hands the call context to the propagator; Go has no ambient context.

## Behaviour notes

- A security requirement that names several schemes together (`security: [{bearer: [], apiKey: []}]`) sends the credential of every scheme in it, chosen from the `Credentials` struct; an operation whose alternatives are single schemes still uses the first scheme you hold a credential for.
- A multipart request body keeps the subtype the spec declares (`multipart/mixed`) with a generated boundary; a declared value that is not a plain `multipart/<token>` is sent as `multipart/form-data`.
- `OAuth2TokenManager.Stop()` ends the manager; later token requests fail with an `*OAuth2TokenError` and send nothing. Stopping twice does nothing.
- `Model.GetAdditionalProperties()` returns a copy of the undeclared keys of a model that allows them; the exported `AdditionalProperties` field is still the live map.
- A styled object-valued parameter with a property that is itself an object fails with a `*SerializationError` before the request is built.
- `RequestOptions.NoDefaultHeaders` on `ApiClient.SendRequestWithOptions` leaves the transport default headers off one request.
