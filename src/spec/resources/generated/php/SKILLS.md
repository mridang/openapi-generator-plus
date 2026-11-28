# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add the SDK to your project via Composer:

```bash
composer require openapi/petstoreclient
```

## Quick Start

```php statements
use PetstoreClient\Client;

$host = getenv('SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL') ?: throw new \RuntimeException('SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL is not set');
$token = getenv('SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN') ?: throw new \RuntimeException('SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN is not set');
$client = Client::withToken($host, $token);
try {
    $result = $client->store->getDefaults();
    print_r($result);
} finally {
    $client->close();
}
```

## Authentication

Configure one optional credential per security scheme on the client, by name: `petStoreBasic`, `adminBasic`, `petStoreBearer`, `serviceToken`, `apiKeyHeader`, `internalApiKey`, `apiKeyQuery`, `sessionCookie`, `machineAuth`, `legacyAuth`, `userAuth`, `browserAuth`, `ssoAuth` (`new Client(petStoreBasic: $credential)`). Each operation uses the scheme its security requirement names; there is no per-call authenticator.

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```php statements
use PetstoreClient\Client;
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

```php statements
use PetstoreClient\Client;
use PetstoreClient\Auth\BasicAuthenticator;

$authenticator = new BasicAuthenticator('https://api.example.com', 'username', 'password');
$client = new Client($authenticator);
```

### API Key

```php statements
use PetstoreClient\Client;
use PetstoreClient\Auth\ApiKeyAuthenticator;
use PetstoreClient\Auth\ApiKeyLocation;

$authenticator = new ApiKeyAuthenticator('https://api.example.com', 'key-name', 'key-value', ApiKeyLocation::HEADER);
$client = new Client($authenticator);
```

### OAuth2 Client Credentials

```php statements
use PetstoreClient\Client;
use PetstoreClient\Auth\OAuth\OAuth2ClientCredentialsAuthenticator;

$authenticator = new OAuth2ClientCredentialsAuthenticator(
    'https://api.example.com', 'client-id', 'client-secret', 'https://auth.example.com/token', []);
$client = new Client($authenticator);
```

### OAuth2 Authorization Code

```php statements
use PetstoreClient\Client;
use PetstoreClient\Auth\OAuth\OAuth2AuthorizationCodeAuthenticator;

$authenticator = new OAuth2AuthorizationCodeAuthenticator(
    'https://api.example.com', 'client-id', 'client-secret',
    'https://auth.example.com/authorize', 'https://auth.example.com/token',
    'https://app.example.com/callback', []);
$client = new Client($authenticator);
$authenticator->exchangeCode('authorization-code');
```

### OAuth2 Password

```php statements
use PetstoreClient\Client;
use PetstoreClient\Auth\OAuth\OAuth2PasswordAuthenticator;

$authenticator = new OAuth2PasswordAuthenticator(
    'https://api.example.com', 'client-id', 'client-secret',
    'https://auth.example.com/token', 'username', 'password', []);
$client = new Client($authenticator);
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```php statements
use PetstoreClient\Client;
use PetstoreClient\Auth\OAuth\OAuth2ImplicitAuthenticator;

$authenticator = new OAuth2ImplicitAuthenticator(
    'https://api.example.com', 'client-id', 'https://auth.example.com/authorize', []);
$authenticator->setAccessToken('your-access-token');
$client = new Client($authenticator);
```

### OpenID Connect

```php statements
use PetstoreClient\Client;
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

A 401 on a request authenticated by an OAuth2 or OpenID Connect authenticator drops the cached access token and replays the request once with fresh headers (a refresh grant first). A second 401 is raised as `UnauthorizedException`; a single-use `BodySource` or a multipart body is not replayed.

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed; tune your authorization server's `expires_in` if it is too tight.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethod::Body` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod::Basic` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```php statements
use PetstoreClient\Auth\OAuth\ClientAuthMethod;
use PetstoreClient\Auth\OAuth\OAuth2ClientCredentialsAuthenticator;

$authenticator = new OAuth2ClientCredentialsAuthenticator(
    'https://api.example.com', 'client-id', 'client-secret', 'https://auth.example.com/token', [],
    clientAuthMethod: ClientAuthMethod::Basic);
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `PetstoreClient\Servers` class exposes each as a `ServerConfiguration` static method (e.g., `Servers::server0()`, `Servers::server1()`, ...) plus a `Servers::all()` array. A server the spec gives a name is also found by that name with `Servers::byName('name')`, which fails with `\InvalidArgumentException` when no server has the name. Pass the desired server's URL to the client:

```php statements
use PetstoreClient\Client;
use PetstoreClient\Servers;

$client = Client::withToken(Servers::server0()->getUrl(), 'your-token');
```

## Testing

The `Authenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```php statements
use PetstoreClient\Client;
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

```php file
use PetstoreClient\Client;
use PetstoreClient\Api\Options\AddPetOptions;
use PetstoreClient\Errors\ApiException;
use PetstoreClient\Errors\NotFoundException;
use PetstoreClient\Errors\ClientException;
use PetstoreClient\Errors\ServerException;
use PetstoreClient\Errors\OpenAPIException;

function addPetOrReport(Client $client, \PetstoreClient\Models\Pet $pet, ?AddPetOptions $options): void
{
    try {
        $client->pet->addPet($pet, $options);
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

```php file
use PetstoreClient\Client;
use PetstoreClient\Auth\Authenticator;
use PetstoreClient\TransportOptions;

function clientWithTransport(Authenticator $authenticator): Client
{
    $transport = TransportOptions::builder()
        ->proxy('http://proxy:3128')
        ->timeout(5000)
        ->build();

    return new Client($authenticator, $transport);
}
```

`TransportOptions::builder()->maxResponseBytes(n)` bounds a buffered response body to `n` bytes (off by default): a body that passes it, on the wire or once decompressed, fails with `ResponseTooLargeException` and is never retried. A streamed response is not bounded by it.

### Retries

`TransportOptions::builder()->maxRetries(int)` sets the retries after a transient failure (default 2; 0 disables). Network errors before a response and 408, 409, 429 and 5xx (not 501) are retried with a 0.5 s to 8 s backoff and `Retry-After` honoured (at most 60 s). The retries stay inside the request timeout, a single-use `BodySource` or a multipart body is never retried, and each retry rebuilds the headers, authentication included.

```php statements
use PetstoreClient\TransportOptions;

$transport = TransportOptions::builder()->maxRetries(0)->build();
```

### Streaming

An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns a `ByteStream` instead of the whole body in memory, so a large download never has to fit in memory. The call returns as soon as the response headers arrive, and the body is read as you consume it. A `ByteStream` has the status code, the lower-cased headers and an idempotent `close()` that aborts the connection instead of draining it, so close it when you stop early (try/finally). `readAll()` reads the rest into memory and leaves the stream closed.

```php file
use PetstoreClient\Client;
use PetstoreClient\Api\Options\DownloadPetDocumentOptions;

function saveTo(Client $client, int $petId, int $documentId, ?DownloadPetDocumentOptions $options, string $target): void
{
    $stream = $client->pet->downloadPetDocument($petId, $documentId, $options);
    try {
        $file = new \SplFileObject($target, 'wb');
        echo $stream->statusCode . "\n";
        while (!$stream->eof()) {
            $file->fwrite($stream->read(8192));
        }
    } finally {
        $stream->close();
    }
}
```

The call `timeout` covers the wait for the response headers and stops there. `idleTimeout` (in `TransportOptions`, default 60 seconds, `0` or `null` disables it) bounds the gap between two reads of the body and raises `NetworkTimeoutException`. Retries, redirects, token refresh and the error contract apply up to the headers exactly as for a buffered call; a request is never retried after the first body byte, and a non-2xx status raises the usual typed error with its body read up to 64 KiB and never returns a stream. A connection reset, or a body that ends before its declared `Content-Length`, raises `StreamException`, a `NetworkException` with status 0. `client.close()` cancels the streams that are still open, and their next read raises the closed-client error. A custom transport that does not implement `sendStreaming` (the `StreamingApiClient` interface) fails a stream call with `BadMethodCallException` naming it, and its buffered calls keep working.

An operation whose request body is raw binary (`type: string, format: binary`) takes a `BodySource` instead of buffering the bytes, so a large upload never has to fit in memory. A `BodySource` is replayable, made with `BodySource::of($bytes)`, `BodySource::ofFile($path)` or `BodySource::fromFactory($factory)` (a factory that opens a fresh stream each time), or single use, made with `BodySource::once($stream)`. The body is sent as it is read: a known length (bytes, a file, or a length you pass) goes out as `Content-Length` and an unknown length is sent chunked. A replayable body is read again for a retry after a 5xx, a 429 or a network error and for a 307 or 308 redirect. A single-use body is never retried, so the original error is raised unchanged, and a 307 or 308 is refused with `Refusing to replay a streamed request body`; it is never buffered to make a replay possible. The SDK never closes a stream you pass to `BodySource::once` and closes the streams it opens itself from a file or a factory. Multipart bodies stay buffered. A 401 that refreshes the token follows the same rule, and an event stream request with a single-use body is never reconnected.

An operation whose success response is JSON Lines (`application/x-ndjson`, `application/jsonl`, `application/jsonlines`) or server-sent events (`text/event-stream`) has no buffered method. It has a `<operation>Stream` method that takes the operation's parameters, its options object and a trailing `StreamOptions`, and returns a `\Generator` of the schema type or an `SseStream<T>` of `ServerSentEvent<T>` (`id`, `event`, `data`, `retry` and the raw `data` text; `T` is the schema of the `data`, or a string). Iterate it, and close it when you stop early (leave the `foreach`; an `SseStream` also has `close()`, and a stream that is dropped releases its connection). A JSON Lines line that does not decode, and a line or event longer than `maxEventBytes` (1 MiB), raise `SerializationException` and end the stream.

```php file
use PetstoreClient\Client;
use PetstoreClient\Api\Options\ExportOrdersOptions;

function readFeed(Client $client, ?ExportOrdersOptions $options): void
{
    foreach ($client->store->exportOrdersStream($options) as $item) {
        print_r($item);
    }
}
```

```php file
use PetstoreClient\Client;
use PetstoreClient\Api\Options\WatchNoticesOptions;

function followEvents(Client $client, ?WatchNoticesOptions $options): void
{
    $feed = $client->store->watchNoticesStream($options);
    try {
        foreach ($feed as $event) {
            echo $event->raw . "\n";
        }
    } finally {
        $feed->close();
    }
}
```

An event stream reconnects when the connection ends, fails or stays silent for the idle timeout: it asks again with fresh headers and `Last-Event-ID`, waits the server's `retry` value or `reconnectDelay` (1 second, doubling to 30 seconds with jitter), and raises the last failure after `maxReconnects` failures in a row without an event (5; 0 for a request with a body; `-1` is unlimited). It never reconnects after a 204, a status other than 408, 429 and 5xx, a response that is not `text/event-stream`, a cancellation or `client.close()`. An event named `error` raises `ApiException` with the response status and the data as its body, and a comment line such as a heartbeat counts as activity for the idle timeout. The generator option `sseTerminator` names a data value (such as `[DONE]`) that ends the stream and is not delivered. `StreamOptions` also takes `idleTimeout` and `lastEventId`.

### Pagination

An operation that returns a list one page at a time has a `<operation>Pages` method next to its plain method. It takes the same parameters in the same order and returns a `Paginator<T, C>`, where `T` is the item type and `C` is the type of the cursor parameter. The method sends no request: the first request goes out when you first read an item or a page, and each next request goes out only when you ask for more, so stopping early sends nothing further and leaves nothing to close. Iterate the paginator for the items, call `items()` for the same items as a generator, or call `pages()` for `Page<T, C>` values with `items`, `nextCursor` and `result`, the same `ApiResult` that the `WithHttpInfo` variant returns, so the status and headers stay reachable. Iterating again starts again from the starting position.

```php file
use PetstoreClient\Client;
use PetstoreClient\Api\Options\ListPetCatalogOptions;

function listEverything(Client $client, ?ListPetCatalogOptions $options): void
{
    $list = $client->pet->listPetCatalogPages($options);
    foreach ($list as $item) {
        print_r($item);
    }
    foreach ($list->pages() as $page) {
        echo count($page->items) . ' items, status ' . $page->result->statusCode . "\n";
    }
}
```

Pagination covers the token cursor, id cursor, offset, page number, `Link` header and next URL in the body styles. The override option and the vendor hints are as described in the readme.

The cursor you pass to the method is the starting position, and the next cursor of a page resumes the list: pass it as the starting cursor of a new call. The page size and every other parameter, option and header are repeated unchanged on each page request, and each page is an ordinary call with its own retries, timeout, redirects, authentication and redaction. An error on a later page is raised from the read that needed it, after the items of the earlier pages were delivered, and ends the iteration; the SDK never skips a page. A list ends when the next cursor is absent, `null` or empty, and an empty page with a next cursor continues. A page that returns the cursor that was just sent raises `PaginationException`, a direct child of `OpenAPIException`, after its items were delivered, instead of looping. There is no total limit and no collect-all: stop the loop when you have enough.

How each style ends and resumes: an id list passes the `last_id` or `next_cursor` of each page as the starting cursor of the next request and ends when a declared `has_more` is false. An offset list starts at the offset you pass, sends the offset past the items it received on each next request, and ends on an empty page or when a declared `has_more` is false. A page-number list sends the page you pass, or none, and then asks for the page after each one, and ends on an empty page or when a declared `has_more` is false. A next URL list takes the URL in the body of each page, follows it only within the origin of the client, and ends when the property is absent, `null` or empty. A cursor, offset or page that does not advance raises `PaginationException` as a token cursor does.

A list that is followed by a `Link` header (`rel="next"`, RFC 8288) takes the next URL from the header of each response. A next URL that leaves the scheme, host or port of the client is refused with `PaginationException` and nothing, no credentials either, is sent to it; such a method has no starting cursor, so it cannot resume.

A list whose page body carries the next URL in a property (a next URL list) is followed in the same way as a `Link` list: the URL is taken from the body of each response, a next URL that leaves the scheme, host or port of the client is refused with `PaginationException`, nothing is sent to it, and such a method cannot resume from a saved position.

The generator finds a paged operation by its names and shape. The operation must be a GET with one JSON 2xx response, and it must match exactly one of these styles. A token cursor is one query parameter among `cursor`, `after`, `page_token`, `next_token`, `continuation_token` and `marker`, paired with a next-cursor property among `next_cursor`, `next_page_token`, `next_token`, `next_marker`, `next_continuation_token`, `continuation_token` and `next` of the same type. An id cursor is one query parameter among `starting_after`, `after_id` and `after`, paired with a boolean `has_more` and a string `last_id` or `next_cursor` property. A `Link` list has a `Link` header declared on the response. An offset list has an `offset` query parameter with a `limit`, and a page-number list has a `page` query parameter with one of `per_page`, `page_size`, `size` or `limit`. A next URL list has no cursor parameter and a string property with `format: uri` or `uri-reference`, or one named `next_url`, `next_uri`, `nextlink` or `next_link`, that is optional or nullable. An operation that matches more than one style is ambiguous, and one that is streamed is never paged; either keeps only its plain method, and the generation log names it and says why. The `x-fern-pagination` and `x-speakeasy-pagination` (cursor type) extensions are read as hints. The generator option `paginationOverrides` (a JSON object that maps an operation id to `none` or to `{style, requestParam, responseProperty, itemsProperty, hasMoreProperty}`, where the style is `token`, `link`, `id`, `offset`, `page` or `body`) wins over a hint and over inference, and `pagination=off` switches the feature off.

### Custom HTTP client

```php file
use PetstoreClient\Client;
use PetstoreClient\Psr18ApiClient;
use Psr\Http\Client\ClientInterface;
use Psr\Http\Message\RequestFactoryInterface;
use Psr\Http\Message\StreamFactoryInterface;

/* $httpClient is any PSR-18 client with redirects disabled; $psr17 is any PSR-17 factory. */
function clientWithHttpClient(
    ClientInterface $httpClient,
    RequestFactoryInterface&StreamFactoryInterface $psr17,
    string $token
): Client {
    $apiClient = new Psr18ApiClient($httpClient, $psr17, $psr17);

    return Client::withToken('https://api.example.com', $token, null, $apiClient);
}
```

The SDK sends every request through your client and never closes it. Combining a supplied client with `proxy`, `caCertPath` or `verifySsl(false)` throws `InvalidArgumentException`.

### Per-call options

Every operation takes an options object as its last argument, including one with no parameters and no security scheme, and it carries `timeout` (ms), `headers` and `maxRetries` for that call only, replacing the transport settings.

```php file
use PetstoreClient\Client;
use PetstoreClient\Api\Options\GetDefaultsOptions;

function getDefaultsWithinTwoSeconds(Client $client): void
{
    $client->store->getDefaults(new GetDefaultsOptions(timeout: 2000, headers: ['X-Tenant' => 'acme'], maxRetries: 0));
}
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

```php file
use PetstoreClient\Models\Account;

function withIdFrom(Account $model, Account $other): Account
{
    return $model->withId($other->id);
}
```

## Binary / File Uploads

A raw binary request body is a `BodySource` (see Streaming). A multipart file field is a `\SplFileObject` (or a PSR-7 stream). A binary response body is returned as a `ByteStream`, a PSR-7 `StreamInterface` (see Streaming).

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
