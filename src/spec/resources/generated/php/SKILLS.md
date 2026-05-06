# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add the SDK to your project via Composer:

```bash
composer require <vendor>/<package-name>
```

## Quick Start

```php
use PetstoreClient\Client;

$client = Client::withToken('https://api.example.com', 'your-token');
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```php
use PetstoreClient\Auth\BearerAuthenticator;

$authenticator = new BearerAuthenticator('https://api.example.com', 'your-token');
$client = new Client($authenticator);
```

### Basic Auth

```php
use PetstoreClient\Auth\BasicAuthenticator;

$authenticator = new BasicAuthenticator('https://api.example.com', 'username', 'password');
$client = new Client($authenticator);
```

### API Key

```php
use PetstoreClient\Auth\ApiKeyAuthenticator;
use PetstoreClient\Auth\ApiKeyLocation;

$authenticator = new ApiKeyAuthenticator('https://api.example.com', 'key-name', 'key-value', ApiKeyLocation::Header);
$client = new Client($authenticator);
```

### OAuth2 Client Credentials

```php
use PetstoreClient\Auth\OAuth\OAuth2ClientCredentialsAuthenticator;

$authenticator = new OAuth2ClientCredentialsAuthenticator(
    'https://api.example.com', 'client-id', 'client-secret', 'https://auth.example.com/token');
$client = new Client($authenticator);
```


## Error Handling

All API errors extend `ApiError`. The exception hierarchy is:

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

```php
use PetstoreClient\Errors\NotFoundError;
use PetstoreClient\Errors\ClientError;
use PetstoreClient\Errors\ServerError;
use PetstoreClient\Errors\ApiError;

try {
    $result = $client->petApi->getPetById($petId);
} catch (NotFoundError $e) {
    echo "Not found: " . $e->getMessage();
} catch (ClientError $e) {
    echo "Client error " . $e->getStatusCode() . ": " . $e->getMessage();
} catch (ServerError $e) {
    echo "Server error: " . $e->getMessage();
} catch (ApiError $e) {
    echo "API error: " . $e->getMessage();
}
```

## Configuration

### Custom Transport Options

```php
use PetstoreClient\TransportOptions;

$transport = TransportOptions::builder()
    ->proxy('http://proxy:3128')
    ->timeout(5000)
    ->build();

$client = new Client($authenticator, $transport);
```

## API Methods

Each API group is exposed as a typed property on the client (e.g., `$client->petApi`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as PHP classes under the `PetstoreClient\Models` namespace.

```php
use PetstoreClient\Models\Pet;

$pet = new Pet(name: 'Fido', status: 'available');
```

## Binary / File Uploads

File upload parameters accept `SplFileInfo` or file path strings. Binary response bodies are returned as `string`.
