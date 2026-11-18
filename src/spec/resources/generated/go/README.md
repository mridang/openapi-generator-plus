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

```go
import "petstore/pkg"

client, err := petstore.NewClientWithToken("https://api.example.com", "your-token", nil)
if err != nil {
    return err
}
defer client.Close()
```

Close the client to release its connections: requests already in flight finish, a request sent afterwards returns `ErrClientClosed`, and a second `Close` does nothing. Cancel a single request through its context. Errors satisfy the `OpenAPIError` interface; `NetworkError` and `NetworkTimeoutError` mean no HTTP response arrived, and `TooManyRequestsError` is HTTP 429. See [SKILLS.md](SKILLS.md) for authentication, servers and error handling.

## Configuration

### Raw responses

Every operation has a `...WithHTTPInfo` variant returning an `*ApiResult[T]` with `StatusCode`, `Data`, `RawBody` and the lower-cased `Headers`. It is the way to read a response header.

### Timeouts

`NewTransportOptionsBuilder().Timeout(&ms)` sets one end-to-end budget in milliseconds (a positive integer) for the whole call, redirects and retries included. A call that exceeds it fails with `NetworkTimeoutError`. The default is 10000 ms; `Timeout(nil)` asks for none.

### Retries

A failed request is retried automatically, up to `MaxRetries` times (default 2; `MaxRetries(0)` disables retries). The retry applies to a network error that happened before a response and to the statuses 408, 409, 429 and 500-599 except 501. The wait starts at 0.5 seconds, doubles up to 8 seconds and loses up to 25% to jitter; a `Retry-After` header (seconds or an HTTP date, capped at 60 seconds) replaces it. The retries never extend the end-to-end `Timeout`: a wait that would outlast it ends the retries and the last failure is returned. Cancelling the context stops them. Every retry is a fresh request, so the authentication headers are applied again. A `multipart/form-data` request is sent once and never retried, because its file parts are streams.

```go
transport, err := petstore.NewTransportOptionsBuilder().
    MaxRetries(5).
    Build()
```

### Expired tokens

A 401 on a request authenticated by an OAuth2 or OpenID Connect authenticator is handled separately: the cached access token is dropped (`InvalidateAccessToken`, the refresh token is kept) and the request is replayed once with new headers; a second 401 is returned as an `UnauthorizedError`. API key, basic and bearer authenticators never replay.

### Request id

The SDK adds a unique `X-Request-ID` header to every request unless you set your own (header names are matched case-insensitively). Quote it when you report a problem with a call. `InjectRequestID(false)` turns it off.

### Cancellation

Every operation takes a `ctx context.Context` as its first parameter.
Cancelling it aborts the request, and the error satisfies
`errors.Is(err, context.Canceled)`; it is distinct from the
`NetworkTimeoutError` raised when the transport timeout elapses. `Close()` does
not cancel anything: it is graceful, so a request in flight finishes.

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

```go
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

```go
client, err := petstore.NewClientWithTokenProvider("https://api.example.com", func(ctx context.Context) (string, error) {
    return os.Getenv("API_TOKEN"), nil
}, nil)
```

### Per-call options

Every operation takes any number of `RequestOption` values as its last arguments, including an operation with no parameters and no security, which has no options struct of its own: `WithTimeout` (one end-to-end budget of at least one millisecond, retries included), `WithHeaders` (they replace a default header of the same name) and `WithMaxRetries` (at least 0; `0` turns retrying off for the call). An operation that declares several request content types also takes `WithRequestContentType`. An option you leave out keeps the client's setting.

```go
callOptions := []petstore.RequestOption{
    petstore.WithTimeout(2 * time.Second),
    petstore.WithHeaders(map[string]string{"X-Trace": "abc"}),
    petstore.WithMaxRetries(0),
}
```

Pass them with `callOptions...` after the other arguments of any operation. A client built around your own `ApiClient` never retries, and that client owns its timeout, so for it a per-call timeout only bounds the call's context.

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
- Unknown values fail loudly: an enum value or a discriminator value the SDK does not know returns an error that names the value; it is never coerced to a default.
- Repeated response headers are joined into one value with `, ` in `Headers` and `ResponseHeaders()` (so repeated `Set-Cookie` or `Link` lines arrive as one string).
- Streaming is not supported: a response is read whole before it is decoded, so server-sent events and JSON Lines are out of scope. Supply your own `ApiClient` or `http.RoundTripper` for those calls.
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
