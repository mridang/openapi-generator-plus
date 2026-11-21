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

A request is retried up to `MaxRetries` times (default 2; `MaxRetries(0)` disables retries) after a network error that happened before a response and after a 408, 409, 429 or 5xx (not 501) response. The wait starts at 0.5 seconds, doubles up to 8 seconds, loses up to 25% to jitter and is replaced by a `Retry-After` header (seconds or an HTTP date, at most 60 seconds). The retries never extend the end-to-end `Timeout`, a cancelled context stops them, and every retry rebuilds the headers, authentication included. A `multipart/form-data` request is sent once and never retried, because its file parts are streams.

```go file
import "petstore/pkg"

func newTransport() (*petstore.TransportOptions, error) {
	return petstore.NewTransportOptionsBuilder().
		MaxRetries(0).
		Build()
}
```

A 401 on an OAuth2 or OpenID Connect request calls `InvalidateAccessToken` on the authenticator (the refresh token is kept) and replays the request once; a second 401 is an `UnauthorizedError`.

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

Every operation takes a `ctx context.Context` as its first parameter; cancelling it aborts the request (`errors.Is(err, context.Canceled)`), and `client.Close()` is graceful and idempotent: requests in flight finish and new ones return `ErrClientClosed`. Each API group is exposed as a typed field on the client (e.g., `client.Pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as Go structs in the `models` package.

```go file
import "petstore/pkg/models"

func accept(m models.Account) models.Account {
	return m
}
```

## Binary / File Uploads

File upload parameters and binary request bodies are typed as `io.Reader`; an `*os.File` satisfies it, and so does `bytes.NewReader` or `strings.NewReader`. Binary response bodies are returned as `[]byte`.

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
