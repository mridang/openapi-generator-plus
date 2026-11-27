# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

```bash
go get petstore
```

## Quick Start

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

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```go file
import (
	"petstore/pkg"
	"petstore/pkg/auth"
)

func newClient() (*petstore.Client, error) {
	authenticator, err := auth.NewBearerAuthenticator("https://api.example.com", "your-token")
	if err != nil {
		return nil, err
	}
	return petstore.NewClient(authenticator, nil), nil
}
```

### Basic Auth

```go file
import (
	"petstore/pkg"
	"petstore/pkg/auth"
)

func newClient() (*petstore.Client, error) {
	authenticator, err := auth.NewBasicAuthenticator("https://api.example.com", "username", "password")
	if err != nil {
		return nil, err
	}
	return petstore.NewClient(authenticator, nil), nil
}
```

### API Key

```go file
import (
	"petstore/pkg"
	"petstore/pkg/auth"
)

func newClient() (*petstore.Client, error) {
	authenticator, err := auth.NewApiKeyAuthenticator("https://api.example.com", "key-name", "key-value", auth.ApiKeyLocationHeader)
	if err != nil {
		return nil, err
	}
	return petstore.NewClient(authenticator, nil), nil
}
```

### OAuth2 Client Credentials

```go file
import (
	"petstore/pkg"
	"petstore/pkg/auth/oauth"
)

func newClient() *petstore.Client {
	authenticator := oauth.NewOAuth2ClientCredentialsAuthenticator(
		"https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token", nil)
	return petstore.NewClient(authenticator, nil)
}
```

### OAuth2 Authorization Code

```go file
import (
	"petstore/pkg"
	"petstore/pkg/auth/oauth"
)

func newClient() *petstore.Client {
	authenticator := oauth.NewOAuth2AuthorizationCodeAuthenticator(
		"https://api.example.com", "client-id", "client-secret",
		"https://auth.example.com/authorize", "https://auth.example.com/token",
		"https://app.example.com/callback", nil, "")
	return petstore.NewClient(authenticator, nil)
}
```

### OAuth2 Password

```go file
import (
	"petstore/pkg"
	"petstore/pkg/auth/oauth"
)

func newClient() *petstore.Client {
	authenticator := oauth.NewOAuth2PasswordAuthenticator(
		"https://api.example.com", "client-id", "client-secret",
		"https://auth.example.com/token", "username", "password", nil, "")
	return petstore.NewClient(authenticator, nil)
}
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```go file
import (
	"petstore/pkg"
	"petstore/pkg/auth/oauth"
)

func newClient() (*petstore.Client, error) {
	authenticator := oauth.NewOAuth2ImplicitAuthenticator(
		"https://api.example.com", "client-id", "https://auth.example.com/authorize", nil)
	if err := authenticator.SetAccessToken("your-access-token"); err != nil {
		return nil, err
	}
	return petstore.NewClient(authenticator, nil), nil
}
```

### OpenID Connect

```go file
import (
	"petstore/pkg"
	"petstore/pkg/auth/oauth"
)

func newClient() *petstore.Client {
	authenticator := oauth.NewOpenIdConnectAuthenticator(
		"https://api.example.com", "https://auth.example.com/.well-known/openid-configuration",
		"client-id", "client-secret", "https://app.example.com/callback", nil)
	return petstore.NewClient(authenticator, nil)
}
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators resolve the access token by making an HTTP call to the token endpoint, which happens before the request carrying it is sent.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed; tune your authorization server's `expires_in` if it is too tight.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethodBody` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethodBasic` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```go file
import (
	"petstore/pkg"
	"petstore/pkg/auth/oauth"
)

func newAuthenticator() petstore.Authenticator {
	return oauth.NewOAuth2ClientCredentialsAuthenticator(
		"https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token", nil).
		WithClientAuthMethod(oauth.ClientAuthMethodBasic)
}
```

## Servers

If the OpenAPI spec defines multiple servers, the generated package exposes each as a `*ServerConfiguration` variable (e.g., `Server0`, `Server1`, ...) plus an `AllServers` slice. Pass the desired server's URL to the client:

```go file
import "petstore/pkg"

func newClient() (*petstore.Client, error) {
	url, err := petstore.Server0.URL(nil)
	if err != nil {
		return nil, err
	}

	return petstore.NewClientWithToken(url, "your-token", nil)
}
```

## Testing

The `Authenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```go file
import "petstore/pkg"

type fakeAuth struct{}

func (fakeAuth) Host() string { return "https://api.example.com" }
func (fakeAuth) AuthHeaders() map[string]string {
	return map[string]string{"Authorization": "Bearer test-token"}
}
func (fakeAuth) QueryParams() map[string]string  { return nil }
func (fakeAuth) CookieParams() map[string]string { return nil }

func newClient() *petstore.Client {
	return petstore.NewClient(fakeAuth{}, nil)
}
```

## Error Handling

Every failure the SDK produces satisfies the `OpenAPIError` interface, so one `errors.As` against it catches all of them. It has four direct kinds: `ApiError` (an HTTP response or a transport failure), `SerializationError` (a request or response body that cannot be encoded or decoded), `OAuth2ServerError` (the token endpoint answered with an error) and `OAuth2TokenError` (the token response was unusable). The last two surface from the first authenticated call, so they are siblings of `ApiError`, not subtypes of it: an `ApiError` branch alone lets them escape. The error hierarchy is:

- `OpenAPIError` (root interface)
  - `SerializationError`
  - `OAuth2ServerError`
  - `OAuth2TokenError`
  - `ApiError`
    - `ClientError` (4xx)
      - `BadRequestError` (400)
      - `UnauthorizedError` (401)
      - `ForbiddenError` (403)
      - `NotFoundError` (404)
      - `ConflictError` (409)
      - `UnprocessableEntityError` (422)
      - `TooManyRequestsError` (429)
      - `PaymentRequiredError` (402)
      - `MethodNotAllowedError` (405)
      - `RequestTimeoutError` (408)
      - `GoneError` (410)
      - `PayloadTooLargeError` (413)
      - `UnsupportedMediaTypeError` (415)
      - `LockedError` (423)
    - `ServerError` (5xx)
      - `InternalServerError` (500)
      - `NotImplementedError` (501)
      - `BadGatewayError` (502)
      - `ServiceUnavailableError` (503)
      - `GatewayTimeoutError` (504)
    - `NetworkError` (no HTTP response, status 0)
      - `NetworkTimeoutError` (the request timed out, status 0)

```go file
import (
	"context"
	"errors"
	"fmt"

	"petstore/pkg"
	"petstore/pkg/models"
	opts "petstore/pkg/options"
)

func addPetOrReport(ctx context.Context, client *petstore.Client, pet models.Pet, options *opts.AddPetOptions) {
	_, err := client.Pet.AddPet(ctx, pet, options)
	var notFound *petstore.NotFoundError
	var clientErr *petstore.ClientError
	var serverErr *petstore.ServerError
	var apiErr *petstore.ApiError
	var root petstore.OpenAPIError
	if errors.As(err, &notFound) {
		fmt.Printf("Not found: %s\n", notFound.Error())
	} else if errors.As(err, &clientErr) {
		fmt.Printf("Client error %d: %s\n", clientErr.StatusCode(), clientErr.Error())
	} else if errors.As(err, &serverErr) {
		fmt.Printf("Server error: %s\n", serverErr.Error())
	} else if errors.As(err, &apiErr) {
		fmt.Printf("API error %d: %s\n", apiErr.StatusCode(), apiErr.Error())
	} else if errors.As(err, &root) {
		fmt.Printf("SDK error: %s\n", root.Error())
	}
}
```

## Configuration

### Custom Transport Options

```go file
import "petstore/pkg"

func newClient(authenticator petstore.Authenticator) (*petstore.Client, error) {
	timeout := 5000
	transport, err := petstore.NewTransportOptionsBuilder().
		Proxy("http://proxy:3128").
		Timeout(&timeout).
		Build()
	if err != nil {
		return nil, err
	}

	return petstore.NewClient(authenticator, transport), nil
}
```

### Retries

A request is retried up to `MaxRetries` times (default 2; `MaxRetries(0)` disables retries) after a network error that happened before a response and after a 408, 409, 429 or 5xx (not 501) response. The wait starts at 0.5 seconds, doubles up to 8 seconds, loses up to 25% to jitter and is replaced by a `Retry-After` header (seconds or an HTTP date, at most 60 seconds). The retries never extend the end-to-end `Timeout`, a cancelled context stops them, and every retry rebuilds the headers, authentication included. A request whose body is a single-use `BodySource`, and a `multipart/form-data` request, is sent once and never retried.

```go file
import "petstore/pkg"

func newTransport() (*petstore.TransportOptions, error) {
	return petstore.NewTransportOptionsBuilder().
		MaxRetries(0).
		Build()
}
```

A 401 on an OAuth2 or OpenID Connect request calls `InvalidateAccessToken` on the authenticator (the refresh token is kept) and replays the request once; a second 401 is an `UnauthorizedError`.

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

How each style ends and resumes: an id list passes the `last_id` or `next_cursor` of each page as the starting cursor of the next request and ends when a declared `has_more` is false. An offset list starts at the offset you pass, sends the offset past the items it received on each next request, and ends on an empty page or when a declared `has_more` is false. A page-number list sends the page you pass, or none, and then asks for the page after each one, and ends on an empty page or when a declared `has_more` is false. A next URL list takes the URL in the body of each page, follows it only within the origin of the client, and ends when the property is absent, `null` or empty. A cursor, offset or page that does not advance raises `PaginationError` as a token cursor does.

A list that is followed by a `Link` header (`rel="next"`, RFC 8288) takes the next URL from the header of each response. A next URL that leaves the scheme, host or port of the client is refused with `PaginationError` and nothing, no credentials either, is sent to it; such a method has no starting cursor, so it cannot resume.

A list whose page body carries the next URL in a property (a next URL list) is followed in the same way as a `Link` list: the URL is taken from the body of each response, a next URL that leaves the scheme, host or port of the client is refused with `PaginationError`, nothing is sent to it, and such a method cannot resume from a saved position.

The generator finds a paged operation by its names and shape. The operation must be a GET with one JSON 2xx response, and it must match exactly one of these styles. A token cursor is one query parameter among `cursor`, `after`, `page_token`, `next_token`, `continuation_token` and `marker`, paired with a next-cursor property among `next_cursor`, `next_page_token`, `next_token`, `next_marker`, `next_continuation_token`, `continuation_token` and `next` of the same type. An id cursor is one query parameter among `starting_after`, `after_id` and `after`, paired with a boolean `has_more` and a string `last_id` or `next_cursor` property. A `Link` list has a `Link` header declared on the response. An offset list has an `offset` query parameter with a `limit`, and a page-number list has a `page` query parameter with one of `per_page`, `page_size`, `size` or `limit`. A next URL list has no cursor parameter and a string property with `format: uri` or `uri-reference`, or one named `next_url`, `next_uri`, `nextlink` or `next_link`, that is optional or nullable. An operation that matches more than one style is ambiguous, and one that is streamed is never paged; either keeps only its plain method, and the generation log names it and says why. The `x-fern-pagination` and `x-speakeasy-pagination` (cursor type) extensions are read as hints. The generator option `paginationOverrides` (a JSON object that maps an operation id to `none` or to `{style, requestParam, responseProperty, itemsProperty, hasMoreProperty}`, where the style is `token`, `link`, `id`, `offset`, `page` or `body`) wins over a hint and over inference, and `pagination=off` switches the feature off.

Pagination covers the token cursor, id cursor, offset, page number, `Link` header and next URL in the body styles. The override option and the vendor hints are as described in the readme.

### Custom HTTP client

```go file
import (
	"net/http"

	"petstore/pkg"
)

func newTransport() (*petstore.TransportOptions, error) {
	httpClient := &http.Client{Transport: &http.Transport{}}
	return petstore.NewTransportOptionsBuilder().
		HttpClient(httpClient).
		Build()
}
```

The SDK sends every request through your client (anything with a `Do(*http.Request)` method; `RoundTripper(rt)` wraps an `http.RoundTripper`) and never closes it. Combining it with `Proxy`, `CACertPath` or `VerifySsl(false)` fails at `Build()` with `ErrTransportConflict`.

### Cancellation

Every operation takes a `ctx context.Context` as its first parameter; cancelling it aborts the request and the error satisfies `errors.Is(err, context.Canceled)`. It is never reported as `NetworkTimeoutError`.

```go file
import (
	"context"
	"errors"
	"fmt"
	"time"

	"petstore/pkg"
	"petstore/pkg/models"
	opts "petstore/pkg/options"
)

func addPetWithCancel(client *petstore.Client, pet models.Pet, options *opts.AddPetOptions) {
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	go func() {
		time.Sleep(time.Second)
		cancel()
	}()
	_, err := client.Pet.AddPet(ctx, pet, options)
	if errors.Is(err, context.Canceled) {
		fmt.Println("cancelled")
	}
}
```

### Raw responses, timeouts and the request id

Each operation has a `...WithHTTPInfo` variant returning `*ApiResult[T]` (`StatusCode`, `Data`, `RawBody`, `Headers`). `Timeout` is one end-to-end budget in positive milliseconds. An `X-Request-ID` header is added to each request unless you set one.

`NewTransportOptionsBuilder().MaxResponseBytes(&n)` bounds a buffered response body to `n` bytes (off by default): a body that passes it, on the wire or once decompressed, fails with `ResponseTooLargeError` and is never retried. A streamed response is not bounded by it.

### Credentials

`NewClientWithToken(host, accessToken, transportOptions)` resolves each credential once, when the client is built: the argument wins, then `SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL` for the host and `SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN` for the token; an empty value counts as unset, and a credential that cannot be resolved returns `ErrMissingCredential`. `NewClientWithTokenProvider(host, provider, transportOptions)` takes a `func(ctx context.Context) (string, error)` that runs for every request.

`NewClientWithCredentials(host, petstore.Credentials{...}, transportOptions)` takes one optional authenticator per security scheme, named after the scheme; each operation uses the credential of the scheme its spec requires and there is no per-call override. Each security scheme has its own authenticator constructor, which resolves a credential the same way: a value you pass wins, an empty string is read once from the scheme's variable (an empty variable counts as unset), and a credential found in neither place returns an error matching `auth.ErrMissingCredential` that names the variable. A Bearer scheme also has `New<Scheme>AuthenticatorWithProvider(host, provider)`, which reads no variable.

- `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME`: the `username` of the `adminBasic` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD`: the `password` of the `adminBasic` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY`: the `apiKey` of the `apiKeyHeader` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY`: the `apiKey` of the `apiKeyQuery` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID`: the `clientId` of the `browserAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY`: the `apiKey` of the `internalApiKey` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID`: the `clientId` of the `legacyAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET`: the `clientSecret` of the `legacyAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME`: the `username` of the `legacyAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD`: the `password` of the `legacyAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID`: the `clientId` of the `machineAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET`: the `clientSecret` of the `machineAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME`: the `username` of the `petStoreBasic` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD`: the `password` of the `petStoreBasic` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN`: the `token` of the `petStoreBearer` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN`: the `token` of the `serviceToken` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY`: the `apiKey` of the `sessionCookie` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID`: the `clientId` of the `ssoAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET`: the `clientSecret` of the `ssoAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI`: the `redirectUri` of the `ssoAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID`: the `clientId` of the `userAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET`: the `clientSecret` of the `userAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI`: the `redirectUri` of the `userAuth` scheme

### Per-call options

Every operation, also one with no parameters and no security, takes `RequestOption` values as its last arguments: `WithTimeout(time.Duration)`, `WithHeaders(map[string]string)`, `WithMaxRetries(int)` and, for an operation with several request content types, `WithRequestContentType(string)`. An option left out keeps the client's setting.

### Response compression, logging and versioning

Responses in `gzip`, `deflate`, `br` and `zstd` are decoded for you. The SDK writes no log lines: wrap the transport with `TransportOptionsBuilder.RoundTripper` to log requests. The module follows semantic versioning, and Go is supported from the oldest release that has not reached end of life.

### Hooks and telemetry

One optional hook runs around every call: `petstore.NewClientWithToken(host, token, nil, petstore.WithCallHook(hook))` (or `NewConfigurationBuilder().CallHook(hook)`). `CallHook.Start(ctx, info, headers)` runs once per call after the headers are final and may edit them; it returns a `CallObserver` (or `nil`) whose `Finish(outcome)` runs exactly once with the status (`0` when no response arrived), the lower-cased headers (`nil` when none arrived) and the error. An error from `Start` reaches the caller; a panic in `Finish` is recovered. `ChainCallHooks(a, b)` starts in order and finishes in reverse. `otelhook.New()` (package `pkg/otelhook`) records one span, `http.client.request.count` and `http.client.request.duration` per call; it needs `go.opentelemetry.io/otel` in your module.

## API Methods

Every operation takes a `ctx context.Context` as its first parameter; cancelling it aborts the request (`errors.Is(err, context.Canceled)`), and `client.Close()` is graceful and idempotent: requests in flight finish and new ones return `ErrClientClosed`. Streams that are still open are cancelled. Each API group is exposed as a typed field on the client (e.g., `client.Pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as Go structs in the `models` package.

```go file
import "petstore/pkg/models"

func accept(m models.Account) models.Account {
	return m
}
```

## Binary / File Uploads

A raw binary request body is a `*BodySource` (see Streaming). Multipart file parameters are typed as `io.Reader`; an `*os.File` satisfies it, and so does `bytes.NewReader` or `strings.NewReader`. A binary response body is returned as a `*ByteStream`, which is an `io.ReadCloser` (see Streaming).

## Comment Style

Never place a comment on the same line as code. Use block comments (`/* ... */`).

```good
/* This explains the logic */
x := 1
```

```bad
// This explains the logic
x := 1
```
