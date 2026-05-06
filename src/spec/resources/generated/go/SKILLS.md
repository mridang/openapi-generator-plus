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


## Error Handling

All API errors implement the error interface. The error hierarchy is:

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

```go
import "petstore/pkg/errors"

result, err := client.PetApi.GetPetById(petId)
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
transport := petstore.NewTransportOptionsBuilder().
    Proxy("http://proxy:3128").
    Timeout(5 * time.Second).
    Build()

client := petstore.NewClient(authenticator, transport)
```

## API Methods

Each API group is exposed as a typed field on the client struct (e.g., `client.PetApi`). API structs have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models along with an error.

## Models

Models are generated as Go structs in the `models` package.

```go
import "petstore/pkg/models"

pet := models.Pet{
    Name:   "Fido",
    Status: "available",
}
```

## Binary / File Uploads

File upload parameters accept `*os.File` or `io.Reader`. Binary response bodies are returned as `[]byte`.

## Comment Style

Never use inline comments (`//`). Always use block comments (`/* ... */`).

```good
/* This explains the logic */
x := 1
```

```bad
// This explains the logic
x := 1
```
