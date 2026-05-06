# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add to your `mix.exs`:

```elixir
defp deps do
  [
    {:petstore_client, "~> 1.0.0"}
  ]
end
```

Then run:

```bash
mix deps.get
```

## Quick Start

```elixir
client = PetstoreClient.Client.with_token("https://api.example.com", "your-token")
```

## Authentication

All authentication is handled via authenticator structs passed to the client constructor.

### Bearer Token

```elixir
authenticator = PetstoreClient.Auth.BearerAuthenticator.new("https://api.example.com", "your-token")
client = PetstoreClient.Client.new(authenticator)
```

### Basic Auth

```elixir
authenticator = PetstoreClient.Auth.BasicAuthenticator.new("https://api.example.com", "username", "password")
client = PetstoreClient.Client.new(authenticator)
```

### API Key

```elixir
authenticator = PetstoreClient.Auth.ApiKeyAuthenticator.new(
  "https://api.example.com", "key-name", "key-value", :header)
client = PetstoreClient.Client.new(authenticator)
```

### OAuth2 Client Credentials

```elixir
authenticator = PetstoreClient.Auth.OAuth.OAuth2ClientCredentialsAuthenticator.new(
  "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token")
client = PetstoreClient.Client.new(authenticator)
```


## Error Handling

All API errors are represented as exception structs. The error hierarchy is:

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

```elixir
case PetstoreClient.Api.PetApi.get_pet_by_id(client.pet_api, pet_id) do
  {:ok, pet} ->
    IO.inspect(pet)

  {:error, %PetstoreClient.Errors.NotFoundError{} = e} ->
    IO.puts("Not found: #{e.message}")

  {:error, %PetstoreClient.Errors.ClientError{} = e} ->
    IO.puts("Client error #{e.status_code}: #{e.message}")

  {:error, %PetstoreClient.Errors.ServerError{} = e} ->
    IO.puts("Server error: #{e.message}")
end
```

## Configuration

### Custom Transport Options

```elixir
transport = PetstoreClient.TransportOptions.new(
  proxy: "http://proxy:3128",
  timeout: 5000
)

client = PetstoreClient.Client.new(authenticator, transport)
```

## API Methods

Each API group is exposed as a typed field on the client struct (e.g., `client.pet_api`). API modules have functions that correspond to OpenAPI operations, accepting the API struct and typed request parameters and returning `{:ok, result}` or `{:error, reason}` tuples.

## Models

Models are generated as Elixir structs under the `PetstoreClient.Models` namespace.

```elixir
pet = %PetstoreClient.Models.Pet{name: "Fido", status: "available"}
```

## Binary / File Uploads

File upload parameters accept `binary()` data. Binary response bodies are returned as `binary()`.

## Comment Style

Use `#` comments and `@moduledoc`/`@doc` attributes for documentation. Place comments on their own line.

```good
# This explains the logic
x = 1
```

```bad
x = 1  # This explains the logic
```
