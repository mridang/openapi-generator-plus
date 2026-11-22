# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated Go SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- Go 1.26+
- [`gofumpt`](https://github.com/mvdan/gofumpt) — stricter formatter (superset of `gofmt`)
- [`golangci-lint`](https://golangci-lint.run/) — linter aggregator (the generated `.golangci.yml` runs its default linters: `errcheck`, `govet`, `ineffassign`, `staticcheck`, `unused`, over the generated sources as well)

### Tooling

```bash
# Format (gofmt is built into the Go toolchain)
go fmt ./...
gofumpt -w .

# Static analysis
go vet ./...

# Lint (runs errcheck, govet, ineffassign, staticcheck and unused)
golangci-lint run
```

## Installation

```bash
go get petstore
```

## Usage

```go statements
import (
	"os"

	"petstore/pkg"
)

client, err := petstore.NewClientWithToken(os.Getenv("SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL"), os.Getenv("SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN"), nil)
if err != nil {
	return err
}
defer func() { _ = client.Close() }()
```

Close the client to release its connections: requests already in flight finish, a request sent afterwards returns `ErrClientClosed`, and a second `Close` does nothing. Streams that are still open are cancelled. Cancel a single request through its context. Errors satisfy the `OpenAPIError` interface; `NetworkError` and `NetworkTimeoutError` mean no HTTP response arrived, and `TooManyRequestsError` is HTTP 429. See [SKILLS.md](SKILLS.md) for authentication, servers and error handling.

## Configuration

### Raw responses

Every operation has a `...WithHTTPInfo` variant returning an `*ApiResult[T]` with `StatusCode`, `Data`, `RawBody` and the lower-cased `Headers`. It is the way to read a response header.

### Timeouts

`NewTransportOptionsBuilder().Timeout(&ms)` sets one end-to-end budget in milliseconds (a positive integer) for the whole call, redirects and retries included. A call that exceeds it fails with `NetworkTimeoutError`. The default is 10000 ms; `Timeout(nil)` asks for none.

### Retries

A failed request is retried automatically, up to `MaxRetries` times (default 2; `MaxRetries(0)` disables retries). The retry applies to a network error that happened before a response and to the statuses 408, 409, 429 and 500-599 except 501. The wait starts at 0.5 seconds, doubles up to 8 seconds and loses up to 25% to jitter; a `Retry-After` header (seconds or an HTTP date, capped at 60 seconds) replaces it. The retries never extend the end-to-end `Timeout`: a wait that would outlast it ends the retries and the last failure is returned. Cancelling the context stops them. Every retry is a fresh request, so the authentication headers are applied again. A request whose body is a single-use `BodySource`, and a `multipart/form-data` request, is sent once and never retried.

```go file
import "petstore/pkg"

func newTransport() (*petstore.TransportOptions, error) {
	return petstore.NewTransportOptionsBuilder().
		MaxRetries(5).
		Build()
}
```

### Expired tokens

A 401 on a request authenticated by an OAuth2 or OpenID Connect authenticator is handled separately: the cached access token is dropped (`InvalidateAccessToken`, the refresh token is kept) and the request is replayed once with new headers; a second 401 is returned as an `UnauthorizedError`. API key, basic and bearer authenticators never replay.

### Request id

The SDK adds a unique `X-Request-ID` header to every request unless you set your own (header names are matched case-insensitively). Quote it when you report a problem with a call. `InjectRequestID(false)` turns it off.

### Cancellation

Every operation takes a `ctx context.Context` as its first parameter.
Cancelling it aborts the request, and the error satisfies
`errors.Is(err, context.Canceled)`; it is distinct from the
`NetworkTimeoutError` raised when the transport timeout elapses. `Close()` cancels
only the streams that are still open: it is graceful otherwise, so a request in
flight finishes.

### Streaming

An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns a `ByteStream` instead of the whole body in memory, so a large download never has to fit in memory. The call returns as soon as the response headers arrive, and the body is read as you consume it. A `ByteStream` has the status code, the lower-cased headers and an idempotent `Close()` that aborts the connection instead of draining it, so close it when you stop early (`defer stream.Close()`). `ReadAll()` reads the rest into memory and leaves the stream closed.

```go file
import (
	"context"
	"fmt"
	"io"
	"os"

	"petstore/pkg"
)

func saveTo(ctx context.Context, client *petstore.Client, petId int64, documentId int64, target string) error {
	stream, err := client.Pet.DownloadPetDocument(ctx, petId, documentId)
	if err != nil {
		return err
	}
	defer func() { _ = stream.Close() }()
	fmt.Println(stream.StatusCode())
	file, err := os.Create(target)
	if err != nil {
		return err
	}
	defer func() { _ = file.Close() }()
	_, err = io.Copy(file, stream)
	return err
}
```

The call `timeout` covers the wait for the response headers and stops there. `IdleTimeout` (on `TransportOptionsBuilder`, default 60 seconds, `0` or `nil` disables it) bounds the gap between two reads of the body and raises `NetworkTimeoutError`. Retries, redirects, token refresh and the error contract apply up to the headers exactly as for a buffered call; a request is never retried after the first body byte, and a non-2xx status raises the usual typed error with its body read up to 64 KiB and never returns a stream. A connection reset, or a body that ends before its declared `Content-Length`, raises `StreamError`, a `NetworkError` with status 0. `client.Close()` cancels the streams that are still open, and their next read raises the closed-client error. A custom transport that does not implement `SendStreaming` fails a stream call with `ErrStreamingUnsupported` naming it, and its buffered calls keep working.

An operation whose request body is raw binary (`type: string, format: binary`) takes a `BodySource` instead of buffering the bytes, so a large upload never has to fit in memory. A `BodySource` is replayable, made with `BodyFromBytes(bytes)`, `BodyFromFile(path)`, `BodyFromReadSeeker(stream)` or `BodyFromFactory(factory)` (a factory that opens a fresh stream each time), or single use, made with `BodyFromReader(stream)` (a reader that can seek is replayable). The body is sent as it is read: a known length (bytes, a file, or a length you pass with `WithLength`) goes out as `Content-Length` and an unknown length is sent chunked. A replayable body is read again for a retry after a 5xx, a 429 or a network error and for a 307 or 308 redirect. A single-use body is never retried, so the original error is raised unchanged, and a 307 or 308 is refused with `Refusing to replay a streamed request body`; it is never buffered to make a replay possible. The SDK never closes a stream you pass to `BodyFromReader` and closes the streams it opens itself from a file or a factory. Multipart bodies stay buffered. A 401 that refreshes the token follows the same rule, and an event stream request with a single-use body is never reconnected.

An operation whose success response is JSON Lines (`application/x-ndjson`, `application/jsonl`, `application/jsonlines`) or server-sent events (`text/event-stream`) has no buffered method. It has a `<operation>Stream` method that takes the operation's parameters, its options object and the call options, and returns a `*JsonLinesStream[T]` of the schema type or a `*SseStream[T]` of `ServerSentEvent[T]` (`ID`, `Event`, `Data`, `Retry` and the `Raw` data text; `T` is the schema of the `data`, or a string). Range over its `All()` method, and close it when you stop early. A JSON Lines line that does not decode, and a line or event longer than `WithMaxEventBytes` (1 MiB), raise `SerializationError` and end the stream.

```go file
import (
	"context"
	"fmt"

	"petstore/pkg"
)

func readItems(ctx context.Context, client *petstore.Client) error {
	feed, err := client.Store.ExportOrdersStream(ctx)
	if err != nil {
		return err
	}
	defer func() { _ = feed.Close() }()
	for item, err := range feed.All() {
		if err != nil {
			return err
		}
		fmt.Println(item)
	}
	return nil
}
```

```go file
import (
	"context"
	"fmt"

	"petstore/pkg"
)

func readEvents(ctx context.Context, client *petstore.Client) error {
	feed, err := client.Store.WatchNoticesStream(ctx)
	if err != nil {
		return err
	}
	defer func() { _ = feed.Close() }()
	for event, err := range feed.All() {
		if err != nil {
			return err
		}
		fmt.Println(event.Data)
	}
	return nil
}
```

An event stream reconnects when the connection ends, fails or stays silent for the idle timeout: it asks again with fresh headers and `Last-Event-ID`, waits the server's `retry` value or `WithReconnectDelay` (1 second, doubling to 30 seconds with jitter), and raises the last failure after `WithMaxReconnects` failures in a row without an event (5; 0 for a request with a body; `-1` is unlimited). It never reconnects after a 204, a status other than 408, 429 and 5xx, a response that is not `text/event-stream`, a cancellation or `client.Close()`. An event named `error` raises `ApiError` with the response status and the data as its body, and a comment line such as a heartbeat counts as activity for the idle timeout. The generator option `sseTerminator` names a data value (such as `[DONE]`) that ends the stream and is not delivered. The call options `WithIdleTimeout` and `WithLastEventID` also apply.

### Pagination

An operation that returns a list one page at a time has a `<operation>Pages` method next to its plain method. It takes the same parameters in the same order and returns a `*Paginator[T, C]`, where `T` is the item type and `C` is the type of the cursor parameter. The method sends no request: the first request goes out when you first read an item or a page, and each next request goes out only when you ask for more, so stopping early sends nothing further and leaves nothing to close. Range over `Items()` for the items, or over `Pages()` for `*Page[T, C]` values with `Items`, `NextCursor` and `Result`, an `ApiResult` that holds the status, the headers, the raw body and the decoded body of the page, so the status and headers stay reachable. Iterating again starts again from the starting position.

```go file
import (
	"context"
	"fmt"

	"petstore/pkg"
	opts "petstore/pkg/options"
)

func listEverything(ctx context.Context, client *petstore.Client, options *opts.ListPetCatalogOptions) error {
	list := client.Pet.ListPetCatalogPages(ctx, options)
	for item, err := range list.Items() {
		if err != nil {
			return err
		}
		fmt.Println(item)
	}
	for page, err := range list.Pages() {
		if err != nil {
			return err
		}
		fmt.Println(len(page.Items), "items, status", page.Result.StatusCode)
	}
	return nil
}
```

The cursor you pass to the method is the starting position, and the next cursor of a page resumes the list: pass it as the starting cursor of a new call. The page size and every other parameter, option and header are repeated unchanged on each page request, and each page is an ordinary call with its own retries, timeout, redirects, authentication and redaction. An error on a later page is yielded from the read that needed it, after the items of the earlier pages were delivered, and ends the iteration; the SDK never skips a page. A list ends when the next cursor is absent, `nil` or empty, and an empty page with a next cursor continues. A page that returns the cursor that was just sent yields a `PaginationError`, a direct child of `OpenAPIError`, after its items were delivered, instead of looping. There is no total limit and no collect-all: break out of the loop.

A list that is followed by a `Link` header (`rel="next"`, RFC 8288) takes the next URL from the header of each response. A next URL that leaves the scheme, host or port of the client is refused with `PaginationError` and nothing, no credentials either, is sent to it; such a method has no starting cursor, so it cannot resume.

The generator finds a paged operation by its names: a GET with one JSON 2xx response that has one array property, and either one cursor query parameter among `cursor`, `after`, `page_token`, `next_token`, `continuation_token` and `marker` paired with one next-cursor property among `next_cursor`, `next_page_token`, `next_token`, `next_marker`, `next_continuation_token`, `continuation_token` and `next` of the same type, or a `Link` header declared on the response. An operation that is ambiguous, or is streamed, keeps only its plain method, and the generation log names it and says why. The `x-fern-pagination` and `x-speakeasy-pagination` (cursor type) extensions are read as hints. The generator option `paginationOverrides` (a JSON object that maps an operation id to `none` or to `{style, requestParam, responseProperty, itemsProperty}`) wins over a hint and over inference, and `pagination=off` switches the feature off.

### Custom HTTP client

`TransportOptionsBuilder.HttpClient(doer)` accepts anything with a
`Do(*http.Request) (*http.Response, error)` method (an `*http.Client`
qualifies) and `TransportOptionsBuilder.RoundTripper(rt)` wraps your
`http.RoundTripper`. The SDK keeps its defaults on top (user agent, headers,
timeout and, around a RoundTripper or an `*http.Client`'s transport, its
redirect policy) and never closes what you supply. Combining a supplied
transport with `Proxy`, `CACertPath` or `VerifySsl(false)` is rejected by
`Build()` with `ErrTransportConflict`: configure those on your own transport.

### Testing your code

Every API group has an interface (`PetApiInterface`) and the client has one (`Client`, a struct whose group fields are typed as those interfaces), so a test can replace the SDK with a hand-written fake: no mocking library, no network.

```go file
import "petstore/pkg"

// Code under test takes the group interface, never the generated struct.
func use(api petstore.PetApiInterface) {}

// The fake embeds the interface, so only the operations you fake are written.
type fakePetApi struct {
    petstore.PetApiInterface
}

func example() {
    fake := fakePetApi{}
    use(fake)

    client := &petstore.Client{ Pet: fake }
    defer client.Close() // a client of fakes holds no transport, so Close returns nil
    _ = client.Pet
}
```

A new operation in the API adds a method to the interface, so a fake that implements the whole interface stops compiling after you regenerate; embed the interface so only the operation you fake is written.

### Credentials

`NewClientWithToken(host, accessToken, transportOptions)` builds a client with a Bearer token. Each credential is resolved once, when the client is built: the argument you pass wins, then the environment variable (`SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL` for the host, `SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN` for the token); an empty value counts as unset. A host or token that cannot be resolved returns `ErrMissingCredential` there and then, not on the first request. `NewClientWithTokenProvider(host, provider, transportOptions)` takes a function returning the current token instead of a static one; the function runs for every request and receives the context of the call.

Each security scheme has an authenticator of its own in `pkg/auth` (`pkg/auth/oauth` for OAuth2 and OpenID Connect), and its constructor resolves credentials the same way. A credential you pass wins. An empty string is read once, when the authenticator is built, from the variable of that scheme, and an empty variable counts as unset. A credential found in neither place returns an error that matches `auth.ErrMissingCredential` (and, for a Bearer scheme, `auth.ErrInvalidBearerToken`) and names the variable, so the client fails when it is built and not on the first request. To hold a credential for several security schemes in one client, build it with `NewClientWithCredentials(host, petstore.Credentials{...}, transportOptions)`: `Credentials` has one optional `Authenticator` field per scheme, named after the scheme. Each operation uses the credential of the scheme its spec requires (the first scheme it accepts, in spec order, that you set), never one of a scheme it does not accept, and an operation declared without security sends none. A call cannot override the scheme. A Bearer scheme also has a `New<Scheme>AuthenticatorWithProvider(host, provider)` constructor, which asks the provider for the token on every request and reads no variable.

| Variable | Security scheme | Credential |
|---|---|---|
| `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` | `adminBasic` | `username` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` | `adminBasic` | `password` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY` | `apiKeyHeader` | `apiKey` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY` | `apiKeyQuery` | `apiKey` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID` | `browserAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY` | `internalApiKey` | `apiKey` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID` | `legacyAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET` | `legacyAuth` | `clientSecret` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME` | `legacyAuth` | `username` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD` | `legacyAuth` | `password` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID` | `machineAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET` | `machineAuth` | `clientSecret` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME` | `petStoreBasic` | `username` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD` | `petStoreBasic` | `password` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN` | `petStoreBearer` | `token` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN` | `serviceToken` | `token` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY` | `sessionCookie` | `apiKey` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID` | `ssoAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET` | `ssoAuth` | `clientSecret` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI` | `ssoAuth` | `redirectUri` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID` | `userAuth` | `clientId` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET` | `userAuth` | `clientSecret` |
| `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI` | `userAuth` | `redirectUri` |

```go file
import (
	"context"
	"os"

	"petstore/pkg"
)

func newClient() (*petstore.Client, error) {
	return petstore.NewClientWithTokenProvider("https://api.example.com", func(ctx context.Context) (string, error) {
		return os.Getenv("API_TOKEN"), nil
	}, nil)
}
```

### Per-call options

Every operation takes any number of `RequestOption` values as its last arguments, including an operation with no parameters and no security, which has no options struct of its own: `WithTimeout` (one end-to-end budget of at least one millisecond, retries included), `WithHeaders` (they replace a default header of the same name) and `WithMaxRetries` (at least 0; `0` turns retrying off for the call). An operation that declares several request content types also takes `WithRequestContentType`. An option you leave out keeps the client's setting.

```go file
import (
	"time"

	"petstore/pkg"
)

func newCallOptions() []petstore.RequestOption {
	return []petstore.RequestOption{
		petstore.WithTimeout(2 * time.Second),
		petstore.WithHeaders(map[string]string{"X-Trace": "abc"}),
		petstore.WithMaxRetries(0),
	}
}
```

Pass them with `callOptions...` after the other arguments of any operation. A client built around your own `ApiClient` never retries, and that client owns its timeout, so for it a per-call timeout only bounds the call's context.

### Hooks and telemetry

One optional hook runs around every operation call. It can edit the request headers before the send and observe the status or the error afterwards, and it does nothing when none is set. It runs once per call, however many sends happen inside it (retries, the one replay after a `401`), once for the plain and the `WithHTTPInfo` variant alike, and also when you supply your own transport.

```go file
import (
	"context"
	"fmt"
	"time"

	"petstore/pkg"
)

type timing struct {
    operation string
    started   time.Time
}

func (t timing) Finish(outcome petstore.CallOutcome) {
    fmt.Println(t.operation, outcome.StatusCode, time.Since(t.started))
}

func newHooked() (*petstore.Client, error) {
    hook := petstore.CallHookFunc(func(ctx context.Context, info petstore.CallInfo, headers map[string]string) (petstore.CallObserver, error) {
        headers["X-Caller"] = "billing"
        return timing{operation: info.OperationID, started: time.Now()}, nil
    })
    return petstore.NewClientWithToken("https://api.example.com", "your-token", nil, petstore.WithCallHook(hook))
}
```

`Start(ctx, info, headers)` runs after the headers are final (defaults, operation headers, per-call headers, authentication, cookies and the propagated trace context), so it can read and replace any of them; reuse the spelling a header already has. A header it sets is on every send of the call. `info` carries `OperationID` (as written in the spec), `Method`, `URLTemplate`, `URL` (without query string and fragment) and `ServerAddress`. It returns the observer to call when the call ends, or `nil`. `Finish(outcome)` runs exactly once on every way the call can end, with `StatusCode` (`0` when no response arrived), the lower-cased response `Headers` (`nil` when no response arrived) and the `Error` the caller receives (`nil` on success). An error returned by `Start` reaches the caller unchanged, nothing is sent and `Finish` is not called; a panic in `Finish` is recovered. The hook never changes the result or the error of the call. Combine several hooks with `ChainCallHooks(first, second)`: they start in the order given and finish in the reverse order. The hook is also set with `NewConfigurationBuilder().CallHook(hook)`.

`otelhook.New()` in the package `pkg/otelhook` is the hook that records one span, one request count and one duration histogram per call with the OpenTelemetry API. Add `go.opentelemetry.io/otel` to your module (the SDK core does not import it), then pass `otelhook.New()` for the global providers or `otelhook.New(otelhook.WithTracerProvider(tp), otelhook.WithMeterProvider(mp))` for your own.

```go file
import (
	"petstore/pkg"
	"petstore/pkg/otelhook"
)

func newTraced() (*petstore.Client, error) {
	return petstore.NewClientWithToken("https://api.example.com", "your-token", nil, petstore.WithCallHook(otelhook.New()))
}
```

The span is named with the operation id, has kind client and carries `http.request.method`, `url.template`, `server.address`, `http.response.status_code` (only when a response arrived) and `error.type` (only on failure: the status code for a status of 400 or more, otherwise `timeout`, `network_error`, `serialization_error`, `cancelled` or `_OTHER`). The counter `http.client.request.count` (unit `{request}`) and the histogram `http.client.request.duration` (unit `s`) carry the same attributes; the histogram's own count equals the request count. The instrumentation scope is the published package name and the SDK version. The hook writes `traceparent` itself, so it names the client span as its parent. No attribute carries a query string, a header value or a credential.

### Errors

An API error (`ApiError` and every subclass) carries `StatusCode()`, `ResponseHeaders()`, `ResponseBody()`, `ErrorBody()`, and the request it answers: `RequestMethod()` and `RequestPath()` (the path without its query string). `Error()` shows them on a `Request: METHOD /path` line before the status code. `NetworkError` and `NetworkTimeoutError` carry them too.

### Operations with several success responses

When an operation declares more than one 2xx response with different schemas, it returns a union (a generated `<Operation>Response` type), and the body is decoded by the status code the server answered with, never by guessing from its shape. Read the variant with `As<Variant>()` or test it with `Is<Variant>()`.

### Models are immutable

A model is a plain struct, and every property has a `With<Property>` method with a value receiver that returns a changed copy, so the original is never touched; an optional or nullable property also has `Without<Property>`. The copy is shallow: a slice or a map in the copy is the same one the original holds, so clone it before you change it in place.

### Logging and tracing

The SDK writes no log lines. To log requests, pass your own `http.RoundTripper` with `TransportOptionsBuilder.RoundTripper` or your own `ApiClient`; every `String` form of an authenticator and an error masks credentials. The current trace context is sent as `traceparent` and `tracestate` request headers once you register a propagator with `SetTraceContextPropagator`.

### Response compression

Requests advertise `Accept-Encoding: gzip, deflate, br, zstd` unless you set your own, and the SDK decodes `gzip`, `deflate`, `br` and `zstd` responses before it reads them, dropping `Content-Encoding` and `Content-Length` from the headers it returns. A response in another encoding is returned as it arrived.

### Versioning and supported Go

The module version follows semantic versioning; the clients, the `RequestOption` functions, the `opts` option structs, the models and the errors are the public API, and an unexported name is private. Go is supported from the oldest release that has not reached end of life; the floor moves up when a release does.

### Behaviours to know

- Optional and nullable: an optional property that is absent and one that is `null` both read as a nil pointer, and a nil pointer is left out when a model is encoded, so a decoded `null` is not sent again. A required, nullable property is the exception: the key must be present, `null` is kept, and encoding writes it. A required nullable query parameter is kept the same way: when its value is `nil` the key is still sent, with an empty value (`?key=`).
- Unknown enum values decode to the catch-all member (`<Enum>UnknownDefaultOpenApi`), which has no wire value, so writing it returns an error; a discriminator value the SDK does not know still returns an error that names the value.
- Repeated response headers are joined into one value with `, ` in `Headers` and `ResponseHeaders()` (so repeated `Set-Cookie` or `Link` lines arrive as one string).
- `Configuration` (base URL, default headers) and `TransportOptions` (timeout, proxy, TLS, retries) stay separate objects: the client builds the first from the authenticator's host and takes the second as an argument.

## Build

```bash
go build ./...
```

## Test

```bash
go test ./...
```

## Module

- Module: `petstore`
- Version: `1.0.0`
- License: MIT

## Caveats

### Decimal / `format: number` precision

Go's `encoding/json` parses JSON numbers into `float64` by default
when the target type is `any`, and into `float64` for `float`
typed fields. `format: decimal` / `format: number` values are
therefore stored as `float64`, so monetary values lose exact decimal
representation. `0.1 + 0.2` in Go is `0.30000000000000004`.

Do not do arithmetic on prices, balances, or other money-typed
fields. If you need exact decimal arithmetic, parse the raw response
body yourself and use `math/big.Rat` or a third-party decimal
library.

`format: int64` is unaffected — Go's `int64` natively represents the
full 64-bit range without precision loss.

### Schema constraints

The constraints `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`,
`multipleOf`, `minItems` and `maxItems` are validated by the server, and by
the generated models only where Go's model layer does it natively. Go's
`encoding/json` validates types, and the generated models additionally check
that required properties are present, that enum values are declared members
and that `format: uri` and `format: byte` values are well formed. The numeric,
string and array constraints above are not checked on the client; if you want
fast-fail validation before the network round trip, plug in a JSON Schema
validator library.

`uniqueItems` arrays are sent in the order the slice holds them, which is
Go's natural collection order: they are not sorted.

## Not supported

### Webhooks and callbacks

This SDK is **client → server** only. Spec entries describing
server-initiated calls — OAS 3.1 top-level `webhooks` and OAS 3.0
per-operation `callbacks` — are intentionally skipped during code
generation. If you need to receive webhook deliveries, write the
handler yourself and use this SDK only to deserialize the incoming
payload (e.g. by reusing the relevant request-body model).

### XML bodies and `nodeType`

Request and response bodies in `application/xml` and the XML `nodeType`
keyword are not generated (#70, #88). An operation that only declares XML has
no usable body in this SDK.

### Link Objects

OpenAPI Link Objects are ignored (#78).

### Mutual TLS security schemes

`mutualTLS` security schemes produce no authenticator (#90). Configure a client
certificate on your own `http.Client` or `http.RoundTripper` and pass it with
`TransportOptionsBuilder.HttpClient(...)` or `TransportOptionsBuilder.RoundTripper(...)`.

### Conditional-required validation (`dependentRequired` / `dependentSchemas`)

JSON Schema 2019-09 keywords for "if field X is present, field Y is
also required" are **not enforced** by this SDK. No mainstream
OpenAPI client codegen implements them. The server is the authoritative
validator; if you want client-side checking, plug in a JSON Schema
validator library for your language.

### SOCKS proxies

`TransportOptionsBuilder.Proxy(...)` accepts only `http://` and `https://` URLs.
Passing a `socks://`, `socks4://`, or `socks5://` scheme is rejected
at construction time with a clear error. SOCKS support would
require enabling extra dependencies / feature flags on the underlying
HTTP library in every one of the 12 SDKs we generate, with non-trivial
API divergence; we explicitly chose not to. If you need SOCKS, route
through a local HTTP-CONNECT bridge or configure it at the OS level.

### `LICENSE` file is not auto-emitted

The package manifest declares MIT, but no `LICENSE` / `LICENSE.md` file
is generated alongside the sources. Drop the appropriate license text
into the generated tree as part of your release pipeline before
publishing to a registry — most registries warn or block on a missing
file, and the GitHub license auto-detect cannot pick up a manifest-only
declaration.
