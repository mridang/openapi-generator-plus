# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

```bash
go get petstore
```

## Quick Start

```go
import "petstore/pkg/auth"

client := petstore.NewClientWithToken("https://api.example.com", "your-token", nil)
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```go
import "petstore/pkg/auth"

authenticator := auth.NewBearerAuthenticator("https://api.example.com", "your-token")
client := petstore.NewClient(authenticator, nil)
```

### Basic Auth

```go
authenticator := auth.NewBasicAuthenticator("https://api.example.com", "username", "password")
client := petstore.NewClient(authenticator, nil)
```

### API Key

```go
authenticator := auth.NewApiKeyAuthenticator("https://api.example.com", "key-name", "key-value", auth.ApiKeyLocationHeader)
client := petstore.NewClient(authenticator, nil)
```

### OAuth2 Client Credentials

```go
import "petstore/pkg/auth/oauth"

authenticator := oauth.NewOAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token")
client := petstore.NewClient(authenticator, nil)
```

### OAuth2 Authorization Code

```go
authenticator := oauth.NewOAuth2AuthorizationCodeAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", "authorization-code", "https://app.example.com/callback")
client := petstore.NewClient(authenticator, nil)
```

### OAuth2 Password

```go
authenticator := oauth.NewOAuth2PasswordAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", "username", "password")
client := petstore.NewClient(authenticator, nil)
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```go
authenticator := oauth.NewOAuth2ImplicitAuthenticator("https://api.example.com", "your-access-token")
client := petstore.NewClient(authenticator, nil)
```

### OpenID Connect

```go
authenticator := oauth.NewOpenIdConnectAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/.well-known/openid-configuration")
client := petstore.NewClient(authenticator, nil)
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

```go
authenticator := oauth.NewOAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token").
    WithClientAuthMethod(oauth.ClientAuthMethodBasic)
```

## Servers

If the OpenAPI spec defines multiple servers, the generated package exposes each as a `*ServerConfiguration` variable (e.g., `Server0`, `Server1`, ...) plus an `AllServers` slice. Pass the desired server's URL to the client:

```go
url, err := petstore.Server0.URL(nil)
if err != nil {
	log.Fatal(err)
}

client := petstore.NewClientWithToken(url, "your-token", nil)
```

## Testing

The `Authenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```go
type fakeAuth struct{}

func (fakeAuth) Host() string { return "https://api.example.com" }
func (fakeAuth) AuthHeaders() map[string]string {
    return map[string]string{"Authorization": "Bearer test-token"}
}
func (fakeAuth) QueryParams() map[string]string  { return nil }
func (fakeAuth) CookieParams() map[string]string { return nil }

client := petstore.NewClient(fakeAuth{}, nil)
```

## Error Handling

All API errors derive from `ApiError`. The error hierarchy is:

- `ApiError` (base)
  - `ClientError` (4xx)
    - `BadRequestError` (400)
    - `UnauthorizedError` (401)
    - `ForbiddenError` (403)
    - `NotFoundError` (404)
    - `ConflictError` (409)
    - `UnprocessableEntityError` (422)
  - `ServerError` (5xx)
    - `InternalServerError` (500)
  - `NetworkError` (no HTTP response, status 0)
    - `NetworkTimeoutError` (the request timed out, status 0)

```go
import "petstore/pkg/errors"

result, err := client.Pet.AddPet(/* params */)
if err != nil {
    var notFound *errors.NotFoundError
    var clientErr *errors.ClientError
    var serverErr *errors.ServerError
    if stderrors.As(err, &notFound) {
        fmt.Printf("Not found: %s\n", notFound.Error())
    } else if stderrors.As(err, &clientErr) {
        fmt.Printf("Client error %d: %s\n", clientErr.StatusCode(), clientErr.Error())
    } else if stderrors.As(err, &serverErr) {
        fmt.Printf("Server error: %s\n", serverErr.Error())
    }
}
```

## Configuration

### Custom Transport Options

```go
transport, err := petstore.NewTransportOptionsBuilder().
    Proxy("http://proxy:3128").
    Timeout(5000).
    Build()
if err != nil {
    return err
}

client := petstore.NewClient(authenticator, transport)
```

## API Methods

Each API group is exposed as a typed field on the client (e.g., `client.Pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as Go structs in the `models` package.

```go
import "petstore/pkg/models"

m := models.ApiResponse{}
```

## Binary / File Uploads

File upload parameters are typed as `io.Reader`. Binary response bodies are returned as `[]byte`.

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
