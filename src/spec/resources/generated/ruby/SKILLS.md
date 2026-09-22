# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add to your `Gemfile`:

```ruby
gem 'petstore_client'
```

Then run:

```bash
bundle install
```

## Quick Start

```ruby
require 'petstore_client'

client = Petstore::Client::Petstore.with_token('https://api.example.com', 'your-token')
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```ruby
authenticator = Petstore::Client::Auth::BearerAuthenticator.new('https://api.example.com', 'your-token')
client = Petstore::Client::Petstore.new(authenticator)
```

### Basic Auth

```ruby
authenticator = Petstore::Client::Auth::BasicAuthenticator.new('https://api.example.com', 'username', 'password')
client = Petstore::Client::Petstore.new(authenticator)
```

### API Key

```ruby
authenticator = Petstore::Client::Auth::ApiKeyAuthenticator.new(
  'https://api.example.com', 'key-name', 'key-value', Petstore::Client::Auth::ApiKeyLocation::HEADER)
client = Petstore::Client::Petstore.new(authenticator)
```

### OAuth2 Client Credentials

```ruby
authenticator = Petstore::Client::Auth::OAuth::OAuth2ClientCredentialsAuthenticator.new(
  'https://api.example.com', 'client-id', 'client-secret', 'https://auth.example.com/token')
client = Petstore::Client::Petstore.new(authenticator)
```

### OAuth2 Authorization Code

```ruby
authenticator = Petstore::Client::Auth::OAuth::OAuth2AuthorizationCodeAuthenticator.new(
  'https://api.example.com', 'client-id', 'client-secret',
  'https://auth.example.com/token', 'authorization-code', 'https://app.example.com/callback')
client = Petstore::Client::Petstore.new(authenticator)
```

### OAuth2 Password

```ruby
authenticator = Petstore::Client::Auth::OAuth::OAuth2PasswordAuthenticator.new(
  'https://api.example.com', 'client-id', 'client-secret',
  'https://auth.example.com/token', 'username', 'password')
client = Petstore::Client::Petstore.new(authenticator)
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```ruby
authenticator = Petstore::Client::Auth::OAuth::OAuth2ImplicitAuthenticator.new('https://api.example.com', 'your-access-token')
client = Petstore::Client::Petstore.new(authenticator)
```

### OpenID Connect

```ruby
authenticator = Petstore::Client::Auth::OAuth::OpenIdConnectAuthenticator.new(
  'https://api.example.com', 'client-id', 'client-secret',
  'https://auth.example.com/.well-known/openid-configuration')
client = Petstore::Client::Petstore.new(authenticator)
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

- `ClientAuthMethod::BODY` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod::BASIC` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```ruby
authenticator = Petstore::Client::Auth::OAuth::OAuth2ClientCredentialsAuthenticator.new(
  'https://api.example.com', 'client-id', 'client-secret', 'https://auth.example.com/token',
  client_auth_method: Petstore::Client::Auth::OAuth::ClientAuthMethod::BASIC)
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `Petstore::Client::Servers` module exposes each as a `ServerConfiguration` constant (e.g., `SERVER_0`, `SERVER_1`, ...) plus an `ALL` array. Pass the desired server's URL to the client:

```ruby
client = Petstore::Client::Petstore.with_token(Petstore::Client::Servers::SERVER_0.url, 'your-token')
```

## Testing

The `Authenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```ruby
fake_authenticator = Class.new do
  def host = 'https://api.example.com'
  def auth_headers = { 'Authorization' => 'Bearer test-token' }
  def query_params = {}
  def cookie_params = {}
end.new

client = Petstore::Client::Petstore.new(fake_authenticator)
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

```ruby
begin
  result = client.pet.add_pet(request)
rescue Petstore::Client::Errors::NotFoundError => e
  puts "Not found: #{e.message}"
rescue Petstore::Client::Errors::ClientError => e
  puts "Client error #{e.status_code}: #{e.message}"
rescue Petstore::Client::Errors::ServerError => e
  puts "Server error: #{e.message}"
rescue Petstore::Client::ApiError => e
  puts "API error: #{e.message}"
end
```

## Configuration

### Custom Transport Options

```ruby
transport = Petstore::Client::TransportOptions.builder
  .proxy('http://proxy:3128')
  .timeout(5000)
  .build

client = Petstore::Client::Petstore.new(authenticator, transport)
```

## API Methods

Each API group is exposed as a typed attribute on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as Ruby classes under the `Petstore::Client::Models` namespace.

```ruby
model = Petstore::Client::Models::ApiResponse.new
```

## Binary / File Uploads

File upload parameters are typed as `IO`. Binary response bodies are returned as `String`.

## Comment Style

Never place a comment on the same line as code. Use `#` comments.

```good
# This explains the logic
x = 1
```

```bad
x = 1  # This explains the logic
```
