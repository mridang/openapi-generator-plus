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

Configure one optional credential per security scheme on the client, by name: `petStoreBasic`, `adminBasic`, `petStoreBearer`, `serviceToken`, `apiKeyHeader`, `internalApiKey`, `apiKeyQuery`, `sessionCookie`, `machineAuth`, `legacyAuth`, `userAuth`, `browserAuth`, `ssoAuth` (`new Client(petStoreBasic: $credential)`). Each operation uses the scheme its security requirement names; there is no per-call authenticator.

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```php
use PetstoreClient\Auth\BearerAuthenticator;

$authenticator = new BearerAuthenticator('https://api.example.com', 'your-token');
$client = new Client($authenticator);
```

A generated authenticator of a basic, bearer, API key or OAuth 2 scheme reads each credential from an environment variable when you pass `null` (read once when the authenticator is built, an empty value counts as unset, a missing credential throws `InvalidArgumentException`; an argument always wins):

- `PetStoreBasicAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME` for `username`.
- `PetStoreBasicAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD` for `password`.
- `AdminBasicAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` for `username`.
- `AdminBasicAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` for `password`.
- `PetStoreBearerAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN` for `token`.
- `ServiceTokenAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN` for `token`.
- `ApiKeyHeaderAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY` for `apiKey`.
- `InternalApiKeyAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY` for `apiKey`.
- `ApiKeyQueryAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY` for `apiKey`.
- `SessionCookieAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY` for `apiKey`.
- `MachineAuthClientCredentialsAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID` for `clientId`.
- `MachineAuthClientCredentialsAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET` for `clientSecret`.
- `LegacyAuthPasswordAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID` for `clientId`.
- `LegacyAuthPasswordAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET` for `clientSecret`.
- `LegacyAuthPasswordAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME` for `username`.
- `LegacyAuthPasswordAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD` for `password`.
- `UserAuthAuthorizationCodeAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID` for `clientId`.
- `UserAuthAuthorizationCodeAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET` for `clientSecret`.
- `UserAuthAuthorizationCodeAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI` for `redirectUri`.
- `BrowserAuthImplicitAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID` for `clientId`.

The prefix `SWAGGER_PETSTORE_OPEN_API_3_0` comes from the `envVarPrefix` generator option. Pass a `\Closure` as the third argument of `BearerAuthenticator` to supply a rotating token on every request. The base classes read no variable; the OAuth 2 and OpenID Connect authenticators take their credentials as arguments.

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

#### Expired tokens

A 401 on a request authenticated by an OAuth2 or OpenID Connect authenticator drops the cached access token and replays the request once with fresh headers (a refresh grant first). A second 401 is raised as `UnauthorizedException`; a stream or multipart body is not replayed.

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

An `ApiException` carries `getRequestMethod()` and `getRequestPath()` (no query string) next to `getStatusCode()`.

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
      - `PaymentRequiredException` (402)
      - `MethodNotAllowedException` (405)
      - `RequestTimeoutException` (408)
      - `GoneException` (410)
      - `PayloadTooLargeException` (413)
      - `UnsupportedMediaTypeException` (415)
      - `LockedException` (423)
    - `ServerException` (5xx)
      - `InternalServerErrorException` (500)
      - `NotImplementedException` (501)
      - `BadGatewayException` (502)
      - `ServiceUnavailableException` (503)
      - `GatewayTimeoutException` (504)
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

### Retries

`TransportOptions::builder()->maxRetries(int)` sets the retries after a transient failure (default 2; 0 disables). Network errors before a response and 408, 409, 429 and 5xx (not 501) are retried with a 0.5 s to 8 s backoff and `Retry-After` honoured (at most 60 s). The retries stay inside the request timeout, a stream or multipart body is never retried, and each retry rebuilds the headers, authentication included.

```php
$transport = TransportOptions::builder()->maxRetries(0)->build();
```

### Custom HTTP client

```php
use PetstoreClient\Psr18ApiClient;

/* $httpClient is any PSR-18 client with redirects disabled; $psr17 is any PSR-17 factory. */
$apiClient = new Psr18ApiClient($httpClient, $psr17, $psr17);
$client = Client::withToken('https://api.example.com', 'your-token', null, $apiClient);
```

The SDK sends every request through your client and never closes it. Combining a supplied client with `proxy`, `caCertPath` or `verifySsl(false)` throws `InvalidArgumentException`.

### Per-call options

Every operation takes an options object as its last argument, including one with no parameters and no security scheme, and it carries `timeout` (ms), `headers` and `maxRetries` for that call only, replacing the transport settings.

```php
$api->operation($required, new OperationOptions(timeout: 2000, headers: ['X-Tenant' => 'acme'], maxRetries: 0));
```

### Cancellation

The SDK has no per-call cancellation. Bound a call with `TransportOptions::builder()->timeout(5000)`; a call that exceeds it throws `NetworkTimeoutException`.

### Raw responses, timeouts and the request id

Each operation has a `...WithHttpInfo` variant returning an `ApiResult` (`statusCode`, `data`, `rawBody`, `headers`). `timeout` is one end-to-end budget in milliseconds. `TransportOptions::builder()->injectRequestId(true)` adds an `X-Request-ID` header to each request unless you set one.

### Hooks and telemetry

One optional hook runs around every call: `Client::withToken($host, $token, callHook: $hook)` (or `Configuration::builder()->callHook($hook)`). `CallHook::start($info, $headers)` runs once per call after the headers are final and may edit them; it returns a `CallObserver` (or `null`) whose `finish($outcome)` runs exactly once with the status (`0` when no response arrived), the lower-cased headers (`null` when none arrived) and the error. An error from `start` reaches the caller; an error from `finish` is swallowed. `CallHookChain::chain($a, $b)` starts in order and finishes in reverse. `OpenTelemetryCallHook::create()` records one span, `http.client.request.count` and `http.client.request.duration` per call; it needs `open-telemetry/api` (`composer require open-telemetry/api`).

## API Methods

Each API group is exposed as a typed property on the client (e.g., `$client->pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as PHP classes under the `PetstoreClient\Models` namespace. They are immutable: properties are `readonly` and `withX()` returns a changed copy. A `oneOf` or `anyOf` wrapper has `fromX()`, `isX()` and `asX()` per variant, and an operation with several 2xx responses returns such a wrapper decoded by the status code that arrived.

```php
use PetstoreClient\Models\Account;

$model = new Account(username: 'example');
```

## Binary / File Uploads

A multipart file field is a `\SplFileObject` (or a PSR-7 stream). A binary request body is a `\SplFileObject`, a PSR-7 `StreamInterface` or a `string` of raw bytes, so in-memory content needs no temporary file. Binary response bodies are returned as `string`.

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
