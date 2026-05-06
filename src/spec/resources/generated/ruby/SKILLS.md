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

client = PetstoreClient::Client.with_token('https://api.example.com', 'your-token')
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```ruby
authenticator = PetstoreClient::Auth::BearerAuthenticator.new('https://api.example.com', 'your-token')
client = PetstoreClient::Client.new(authenticator)
```

### Basic Auth

```ruby
authenticator = PetstoreClient::Auth::BasicAuthenticator.new('https://api.example.com', 'username', 'password')
client = PetstoreClient::Client.new(authenticator)
```

### API Key

```ruby
authenticator = PetstoreClient::Auth::ApiKeyAuthenticator.new(
  'https://api.example.com', 'key-name', 'key-value', PetstoreClient::Auth::ApiKeyLocation::HEADER)
client = PetstoreClient::Client.new(authenticator)
```

### OAuth2 Client Credentials

```ruby
authenticator = PetstoreClient::Auth::OAuth::OAuth2ClientCredentialsAuthenticator.new(
  'https://api.example.com', 'client-id', 'client-secret', 'https://auth.example.com/token')
client = PetstoreClient::Client.new(authenticator)
```


## Error Handling

All API errors inherit from `ApiError`. The error hierarchy is:

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

```ruby
begin
  result = client.pet_api.get_pet_by_id(pet_id)
rescue PetstoreClient::Errors::NotFoundError => e
  puts "Not found: #{e.message}"
rescue PetstoreClient::Errors::ClientError => e
  puts "Client error #{e.status_code}: #{e.message}"
rescue PetstoreClient::Errors::ServerError => e
  puts "Server error: #{e.message}"
rescue PetstoreClient::Errors::ApiError => e
  puts "API error: #{e.message}"
end
```

## Configuration

### Custom Transport Options

```ruby
transport = PetstoreClient::TransportOptions.builder
  .proxy('http://proxy:3128')
  .timeout(5000)
  .build

client = PetstoreClient::Client.new(authenticator, transport)
```

## API Methods

Each API group is exposed as a typed attribute on the client (e.g., `client.pet_api`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as Ruby classes under the `PetstoreClient::Models` namespace.

```ruby
pet = PetstoreClient::Models::Pet.new(name: 'Fido', status: 'available')
```

## Binary / File Uploads

File upload parameters accept `File` objects or `IO`-like objects. Binary response bodies are returned as `String` with binary encoding.

## Comment Style

Use `#` comments on their own line. Never place inline comments on the same line as code.

```good
# This explains the logic
x = 1
```

```bad
x = 1  # This explains the logic
```
