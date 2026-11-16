# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add the SDK to your project via Composer:

```bash
composer require openapi/petstoreclient
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

$authenticator = new ApiKeyAuthenticator('https://api.example.com', 'key-name', 'key-value', ApiKeyLocation::HEADER);
$client = new Client($authenticator);
```

### OAuth2 Client Credentials

```php
use PetstoreClient\Auth\OAuth\OAuth2ClientCredentialsAuthenticator;

$authenticator = new OAuth2ClientCredentialsAuthenticator(
    'https://api.example.com', 'client-id', 'client-secret', 'https://auth.example.com/token', []);
$client = new Client($authenticator);
```

### OAuth2 Authorization Code

```php
use PetstoreClient\Auth\OAuth\OAuth2AuthorizationCodeAuthenticator;

$authenticator = new OAuth2AuthorizationCodeAuthenticator(
    'https://api.example.com', 'client-id', 'client-secret',
    'https://auth.example.com/authorize', 'https://auth.example.com/token',
    'https://app.example.com/callback', []);
$client = new Client($authenticator);
$authenticator->exchangeCode('authorization-code');
```

### OAuth2 Password

```php
use PetstoreClient\Auth\OAuth\OAuth2PasswordAuthenticator;

$authenticator = new OAuth2PasswordAuthenticator(
    'https://api.example.com', 'client-id', 'client-secret',
    'https://auth.example.com/token', 'username', 'password', []);
$client = new Client($authenticator);
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```php
use PetstoreClient\Auth\OAuth\OAuth2ImplicitAuthenticator;

$authenticator = new OAuth2ImplicitAuthenticator(
    'https://api.example.com', 'client-id', 'https://auth.example.com/authorize', []);
$authenticator->setAccessToken('your-access-token');
$client = new Client($authenticator);
```

### OpenID Connect

```php
use PetstoreClient\Auth\OAuth\OpenIdConnectAuthenticator;

$authenticator = new OpenIdConnectAuthenticator(
    'https://api.example.com', 'https://auth.example.com/.well-known/openid-configuration',
    'client-id', 'client-secret', 'https://app.example.com/callback', []);
$client = new Client($authenticator);
$authenticator->exchangeCode('authorization-code');
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

- `ClientAuthMethod::Body` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod::Basic` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```php
use PetstoreClient\Auth\OAuth\ClientAuthMethod;
use PetstoreClient\Auth\OAuth\OAuth2ClientCredentialsAuthenticator;

$authenticator = new OAuth2ClientCredentialsAuthenticator(
    'https://api.example.com', 'client-id', 'client-secret', 'https://auth.example.com/token', [],
    clientAuthMethod: ClientAuthMethod::Basic);
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `PetstoreClient\Servers` class exposes each as a `ServerConfiguration` static method (e.g., `Servers::server0()`, `Servers::server1()`, ...) plus a `Servers::all()` array. Pass the desired server's URL to the client:

```php
use PetstoreClient\Servers;

$client = Client::withToken(Servers::server0()->getUrl(), 'your-token');
```

## Testing

The `Authenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```php
use PetstoreClient\Auth\Authenticator;

$fake = new class implements Authenticator {
    public function getHost(): string { return 'https://api.example.com'; }
    public function getAuthHeaders(): array {
        return ['Authorization' => 'Bearer test-token'];
    }
    public function getQueryParams(): array { return []; }
    public function getCookieParams(): array { return []; }
};

$client = new Client($fake);
```

## Error Handling

Every failure the SDK raises derives from `OpenAPIException`; catching it handles them all. It has four direct subtypes: `ApiException` (an HTTP response or transport failure), `SerializationException` (a request or response body that cannot be encoded or decoded), `OAuth2ServerException` (the token endpoint answered with an error) and `OAuth2TokenException` (the token response was unusable). The last two are raised from the first authenticated call, so they are siblings of `ApiException`, not subclasses. The error hierarchy is:

- `OpenAPIException` (root)
  - `SerializationException`
  - `OAuth2ServerException`
  - `OAuth2TokenException`
  - `ApiException`
    - `ClientException` (4xx)
      - `BadRequestException` (400)
      - `UnauthorizedException` (401)
      - `ForbiddenException` (403)
      - `NotFoundException` (404)
      - `ConflictException` (409)
      - `UnprocessableEntityException` (422)
      - `TooManyRequestsException` (429)
    - `ServerException` (5xx)
      - `InternalServerErrorException` (500)
    - `NetworkException` (no HTTP response, status 0)
      - `NetworkTimeoutException` (the request timed out, status 0)

```php
use PetstoreClient\Client;
use PetstoreClient\Errors\ApiException;
use PetstoreClient\Errors\NotFoundException;
use PetstoreClient\Errors\ClientException;
use PetstoreClient\Errors\ServerException;
use PetstoreClient\Errors\OpenAPIException;

function addPetOrReport(Client $client, \PetstoreClient\Models\Pet $pet): void
{
    try {
        $client->pet->addPet($pet);
    } catch (NotFoundException $e) {
        echo "Not found: " . $e->getMessage();
    } catch (ClientException $e) {
        echo "Client error " . $e->getStatusCode() . ": " . $e->getMessage();
    } catch (ServerException $e) {
        echo "Server error: " . $e->getMessage();
    } catch (ApiException $e) {
        echo "API error: " . $e->getMessage();
    } catch (OpenAPIException $e) {
        echo "SDK error: " . $e->getMessage();
    }
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

### Automatic retries

`TransportOptions::builder()->maxRetries(int)` sets the retries after a transient failure (default 2; 0 disables). Network errors before a response and 408, 409, 429 and 5xx (not 501) are retried with a 0.5 s to 8 s backoff and `Retry-After` honoured (at most 60 s). The retries stay inside the request timeout, a stream or multipart body is never retried, and each retry rebuilds the headers, authentication included.

```php
$transport = TransportOptions::builder()->maxRetries(0)->build();
```

## API Methods

Each API group is exposed as a typed property on the client (e.g., `$client->pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as PHP classes under the `PetstoreClient\Models` namespace.

```php
use PetstoreClient\Models\Account;

$model = new Account(username: 'example');
```

## Binary / File Uploads

File upload parameters are typed as `\SplFileObject`. Binary response bodies are returned as `string`.

## Comment Style

Never place a comment on the same line as code. Use block comments (`/* ... */`); PHPDoc (`/** ... */`) is fine.

```good
/* This explains the logic */
$x = 1;
```

```bad
// This explains the logic
$x = 1;
```
