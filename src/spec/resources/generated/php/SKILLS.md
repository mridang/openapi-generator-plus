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

### OAuth2 Authorization Code

```php
use PetstoreClient\Auth\OAuth\OAuth2AuthCodeAuthenticator;

$authenticator = new OAuth2AuthCodeAuthenticator(
    'https://api.example.com', 'client-id', 'client-secret',
    'https://auth.example.com/token', 'authorization-code', 'https://app.example.com/callback');
$client = new Client($authenticator);
```

### OAuth2 Password

```php
use PetstoreClient\Auth\OAuth\OAuth2PasswordAuthenticator;

$authenticator = new OAuth2PasswordAuthenticator(
    'https://api.example.com', 'client-id', 'client-secret',
    'https://auth.example.com/token', 'username', 'password');
$client = new Client($authenticator);
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```php
use PetstoreClient\Auth\OAuth\OAuth2ImplicitAuthenticator;

$authenticator = new OAuth2ImplicitAuthenticator('https://api.example.com', 'your-access-token');
$client = new Client($authenticator);
```

### OpenID Connect

```php
use PetstoreClient\Auth\OAuth\OpenIdConnectAuthenticator;

$authenticator = new OpenIdConnectAuthenticator(
    'https://api.example.com', 'client-id', 'client-secret',
    'https://auth.example.com/.well-known/openid-configuration');
$client = new Client($authenticator);
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators resolve the access token through `getAuthHeaders()` because obtaining a token requires a blocking HTTP call to the token endpoint. The generated client invokes this for you before sending each request; you do not need to interact with it directly. PHP's HTTP client is synchronous, so the token fetch happens inline on the calling thread rather than via a future/promise.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed; tune your authorization server's `expires_in` if it is too tight.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethod::Body` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod::Basic` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```php
use PetstoreClient\Auth\OAuth\ClientAuthMethod;
use PetstoreClient\Auth\OAuth\OAuth2ClientCredentialsAuthenticator;

$authenticator = new OAuth2ClientCredentialsAuthenticator(
    'https://api.example.com', 'client-id', 'client-secret', 'https://auth.example.com/token',
    clientAuthMethod: ClientAuthMethod::Basic);
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `PetstoreClient\Servers` class exposes each as a `ServerConfiguration` constant (e.g., `Servers::SERVER_0`, `Servers::SERVER_1`, ...) plus an `Servers::ALL` array. Pass the desired server's URL to the client:

```php
use PetstoreClient\Servers;

$client = Client::withToken(Servers::SERVER_0->url(), 'your-token');
```

## Testing

The `Authenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```php
$fake = new class implements PetstoreClient\Auth\Authenticator {
    public function getAuthHeaders(RequestContext $request): array {
        return ['Authorization' => 'Bearer test-token'];
    }
    public function getHost(): string { return 'https://api.example.com'; }
};

$client = new Client($fake);
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

## Comment Style

Never use inline comments (`//`). Always use block comments (`/* ... */`). PHPDoc `/** ... */` is fine.

```good
/* This explains the logic */
$x = 1;
```

```bad
// This explains the logic
$x = 1;
```
