# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated PHP SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## API information

Pets, owners and orders for integration tests

- **Contact:** Petstore API support
- **Contact email:** support@example.com
- **Contact URL:** https://example.com/support
- **License:** Apache 2.0 (https://spdx.org/licenses/Apache-2.0.html)
- **Terms of service:** https://example.com/terms

## Requirements

- PHP 8.3 or newer
- Composer 2.x
- PHP extensions: `ext-curl`, `ext-json`, `ext-mbstring`, `ext-zlib`
- Optional: PECL `ext-ds` (auto-replaces the `php-ds/php-ds` userland
  `\Ds\Vector` / `\Ds\Set` / `\Ds\Map` classes with their native C
  implementations for ~5–10x throughput on container-heavy payloads)

### Tooling

The generated project ships with a complete formatter, linter, static
analyser, and Rector configuration. Run any of them via Composer's
`vendor/bin`:

```bash
# Formatter — PHP-CS-Fixer (@PSR12 ruleset)
vendor/bin/php-cs-fixer fix

# Linter — PHP_CodeSniffer (PSR-12)
vendor/bin/phpcs

# Static analyser — PHPStan at level 9 (max)
vendor/bin/phpstan analyse

# Dependency audit — Composer advisories for the locked dependencies
composer audit

# Automated upgrades — Rector (UP_TO_PHP_83 + DEAD_CODE + CODE_QUALITY + EARLY_RETURN + TYPE_DECLARATION)
vendor/bin/rector
```

## Installation

```bash
composer require openapi/petstoreclient
```

## Usage

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

Closing the client is graceful: a request already sent finishes, every later request fails with a `\LogicException` (`client closed`), and only the transport the SDK built is released, never a client you supplied. Calling `close()` twice is harmless. Streams that are still open are cancelled. Errors derive from `OpenAPIException`; `NetworkException` and `NetworkTimeoutException` mean no HTTP response arrived, and `TooManyRequestsException` is HTTP 429. See [SKILLS.md](SKILLS.md) for authentication, servers and error handling.

### Raw responses

Every operation has a `...WithHttpInfo` variant returning an `ApiResult` with the public properties `statusCode`, `data`, `rawBody` and the lower-cased `headers`. It is the way to read a response header; a header repeated by the server arrives as one value joined with `, `.

### Timeouts

`TransportOptions::builder()->timeout(5000)` sets one end-to-end budget in milliseconds for the whole call, redirects and retries included. A call that exceeds it fails with `NetworkTimeoutException`; the default is 60 000 ms and `null` waits indefinitely.

`TransportOptions::builder()->maxResponseBytes(n)` bounds a buffered response body to `n` bytes (off by default): a body that passes it, on the wire or once decompressed, fails with `ResponseTooLargeException` and is never retried. A streamed response is not bounded by it.

### Automatic retries

A request that fails with a transient error is retried by the SDK. `TransportOptions::builder()->maxRetries()` sets how many times (default 2, so up to three attempts); `maxRetries(0)` disables retrying.

```php statements
use PetstoreClient\TransportOptions;

$transport = TransportOptions::builder()->maxRetries(4)->build();
```

A request is retried after a network error that happened before a response arrived, and after a 408, 409, 429 or 5xx response (not 501). The wait starts at 0.5 s, doubles up to 8 s and is shortened by up to 25% at random; a `Retry-After` header (seconds or an HTTP date, at most 60 s) replaces it. Retries never extend the request timeout: when the next wait would outlast what is left of it, the last failure is reported. A single-use `BodySource` or a multipart body is never retried, and every retry is a new request with freshly built authentication headers.

### Expired tokens

When an OAuth2 or OpenID Connect call is answered with 401, the cached access token is dropped (a refresh token is used first when there is one) and the request is sent once more with fresh headers. A second 401 is raised as `UnauthorizedException`. A single-use `BodySource` or a multipart body is not replayed, and API key, basic and bearer authenticators never replay.

### Per-call options

Every operation takes an options object as its last argument, even one with no query, header, form or cookie parameter and no security scheme (`$api->operation()` with no argument still works). It accepts three fields for that one call: `timeout` (milliseconds, positive), `headers` (a name to value map that wins over a configured header of the same name) and `maxRetries` (0 or more). They replace the transport settings for that call only.

```php file
use PetstoreClient\Client;
use PetstoreClient\Api\Options\GetDefaultsOptions;

function getDefaultsWithinTwoSeconds(Client $client): void
{
    $client->store->getDefaults(new GetDefaultsOptions(timeout: 2000, headers: ['X-Tenant' => 'acme'], maxRetries: 0));
}
```

`timeout` and `maxRetries` need the bundled transport or a subclass of `AbstractApiClient`; a transport you wrote yourself against `ApiClient` sends the headers and ignores the other two.

### Credentials

The client takes one optional credential per security scheme, named after the scheme (`new Client(petStoreBasic: $credential)`): `petStoreBasic`, `adminBasic`, `archiveDigest`, `petStoreBearer`, `serviceToken`, `apiKeyHeader`, `internalApiKey`, `apiKeyQuery`, `sessionCookie`, `machineAuth`, `legacyAuth`, `userAuth`, `browserAuth`, `deviceAuth`, `ssoAuth`. Each operation uses the scheme its security requirement names (the first alternative whose schemes are all configured; the schemes of one alternative are sent together) and sends no credential of another scheme. An operation has no per-call authenticator. The authenticator given first (`Client::withToken()`) is used for a secured operation only when no scheme credential is configured.

The authenticator of a basic, bearer, API key or OAuth 2 security scheme takes its credentials as arguments, or reads each one from an environment variable when you pass `null`. Every variable is named `SWAGGER_PETSTORE_OPEN_API_3_0_` followed by the field (`TOKEN`, `USERNAME`, `PASSWORD`, `API_KEY`, `CLIENT_ID`, `CLIENT_SECRET`, `REDIRECT_URI`); when two schemes have a field of the same name, the scheme id sits between the two so that no scheme reads another's secret. Set the prefix with the `envVarPrefix` generator option.

- `PetStoreBasicAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME` for `username`.
- `PetStoreBasicAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD` for `password`.
- `AdminBasicAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` for `username`.
- `AdminBasicAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` for `password`.
- `ArchiveDigestAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_ARCHIVE_DIGEST_USERNAME` for `username`.
- `ArchiveDigestAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_ARCHIVE_DIGEST_PASSWORD` for `password`.
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
- `DeviceAuthDeviceAuthorizationAuthenticator` reads `SWAGGER_PETSTORE_OPEN_API_3_0_DEVICE_AUTH_CLIENT_ID` for `clientId`.

An argument always wins over the environment, an empty variable counts as unset, and the variable is read once, when the authenticator is built. A credential that is neither passed nor in the environment throws `InvalidArgumentException` at that point, so a missing credential never waits for the first request. Pass a `\Closure` token provider (`new BearerAuthenticator($host, null, fn (): string => $vault->token())`) to supply a rotating credential; it is called for every request. The base classes `BearerAuthenticator`, `BasicAuthenticator`, `ApiKeyAuthenticator` and the OAuth 2 authenticators read no variable: only the generated authenticator of a scheme knows its name. The OpenID Connect authenticator takes its credentials as arguments only.

The device authorization flow (RFC 8628) gets an OAuth2DeviceAuthorizationAuthenticator: call requestDeviceAuthorization() to get the user code and verification URI to show to the user, then pollForToken() to wait for the approval.

HTTP Digest schemes get a DigestAuthenticator built from a username and a password: the first request goes without credentials, and the SDK answers the server's 401 challenge and replays the request once. The Negotiate scheme is not supported, because it needs a native Kerberos library.

### Request id

Set `TransportOptions::builder()->injectRequestId(true)` and the SDK adds a unique `X-Request-ID` header to every request unless you set your own (header names are matched case-insensitively). Quote it when you report a problem with a call.

### Errors

An `ApiException` and each of its subclasses carries the request that failed: `getRequestMethod()` and `getRequestPath()` (the path without its query string, so a secret in a query parameter never reaches a log). The string form prints them as `Request: GET /items/7` after the status code.

### Models

Models are immutable: every property is `readonly`, and each has a `withX()` method that returns a changed copy (`$model->withName('example')`), leaving the original as it was. A model with `additionalProperties` also has `withAdditionalProperties()`. A `oneOf` or `anyOf` wrapper has `fromX()`, `isX()` and `asX()` for each variant (`Result::fromVariant($variant)`, `$union->asVariant()`), where `asX()` returns `null` for another variant. An operation with several 2xx responses of different schemas returns such a wrapper, decoded by the status code that arrived, never by trial.

### Response compression

The SDK sends `Accept-Encoding` for the encodings it can decode and decompresses the body before it reaches you: `gzip` and `deflate` always, `br` with `ext-brotli` and `zstd` with `ext-zstd`. A response in an encoding whose extension is missing fails with a clear error instead of returning compressed bytes.

### Hooks and telemetry

One optional hook runs around every operation call. It can edit the request headers before the send and observe the status or the error afterwards, and it does nothing when none is set. It runs once per call, however many sends happen inside it (retries, the one replay after a `401`), once for the plain and the `WithHttpInfo` variant alike, and also when you supply your own transport.

```php file
use PetstoreClient\CallHook;
use PetstoreClient\CallInfo;
use PetstoreClient\CallObserver;
use PetstoreClient\CallOutcome;
use PetstoreClient\Client;

final class TimingHook implements CallHook
{
    public function start(CallInfo $info, array &$headers): CallObserver
    {
        $headers['X-Caller'] = 'billing';
        $started = hrtime(true);

        return new class ($info, $started) implements CallObserver {
            public function __construct(private CallInfo $info, private int|float $started)
            {
            }

            public function finish(CallOutcome $outcome): void
            {
                printf("%s %d %.1f ms\n", $this->info->operationId, $outcome->statusCode, (hrtime(true) - $this->started) / 1e6);
            }
        };
    }
}

$client = Client::withToken('https://api.example.com', 'your-token', callHook: new TimingHook());
```

`start($info, $headers)` runs after the headers are final (defaults, operation headers, per-call headers, authentication, cookies and the propagated trace context), so it can read and replace any of them; reuse the spelling a header already has. A header it sets is on every send of the call. `$info` carries `operationId` (as written in the spec), `method`, `urlTemplate`, `url` (without query string and fragment) and `serverAddress`. It returns the observer to call when the call ends, or `null`. `finish($outcome)` runs exactly once on every way the call can end, with `statusCode` (`0` when no response arrived), the lower-cased response `headers` (`null` when no response arrived) and the `error` the caller receives (`null` on success). An error thrown by `start` reaches the caller unchanged, nothing is sent and `finish` is not called; an error thrown by `finish` is swallowed. The hook never changes the result or the error of the call. Combine several hooks with `CallHookChain::chain($first, $second)`: they start in the order given and finish in the reverse order. `start` and `finish` run inline and must not block.

`OpenTelemetryCallHook` is the hook that records one span, one request count and one duration histogram per call with the OpenTelemetry API. Add `open-telemetry/api` to your `composer.json` (the SDK itself does not need it) and configure the OpenTelemetry SDK in your application, then pass `OpenTelemetryCallHook::create()` for the global providers or `OpenTelemetryCallHook::create($tracerProvider, $meterProvider)` for your own. A PHP process runs one request at a time, so a metric only reaches a backend when your application flushes its meter provider.

```php statements
use PetstoreClient\Client;
use PetstoreClient\OpenTelemetryCallHook;

$client = Client::withToken('https://api.example.com', 'your-token', callHook: OpenTelemetryCallHook::create());
```

The span is named with the operation id, has kind client and carries `http.request.method`, `url.template`, `server.address`, `http.response.status_code` (only when a response arrived) and `error.type` (only on failure: the status code for a status of 400 or more, otherwise `timeout`, `network_error`, `serialization_error`, `cancelled` or `_OTHER`). The counter `http.client.request.count` (unit `{request}`) and the histogram `http.client.request.duration` (unit `s`) carry the same attributes; the histogram's own count equals the request count. The instrumentation scope is the published package name and the SDK version. The hook writes `traceparent` itself, so it names the client span as its parent. No attribute carries a query string, a header value or a credential.

### Logging

The SDK does not log. To log requests and responses, wrap your own PSR-18 client with a logging decorator (PSR-3) and pass it as described under Custom HTTP client.

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

The cursor you pass to the method is the starting position, and the next cursor of a page resumes the list: pass it as the starting cursor of a new call. The page size and every other parameter, option and header are repeated unchanged on each page request, and each page is an ordinary call with its own retries, timeout, redirects, authentication and redaction. An error on a later page is raised from the read that needed it, after the items of the earlier pages were delivered, and ends the iteration; the SDK never skips a page. A list ends when the next cursor is absent, `null` or empty, and an empty page with a next cursor continues. A page that returns the cursor that was just sent raises `PaginationException`, a direct child of `OpenAPIException`, after its items were delivered, instead of looping. There is no total limit and no collect-all: stop the loop when you have enough.

How each style ends and resumes: an id list passes the `last_id` or `next_cursor` of each page as the starting cursor of the next request and ends when a declared `has_more` is false. An offset list starts at the offset you pass, sends the offset past the items it received on each next request, and ends on an empty page or when a declared `has_more` is false. A page-number list sends the page you pass, or none, and then asks for the page after each one, and ends on an empty page or when a declared `has_more` is false. A next URL list takes the URL in the body of each page, follows it only within the origin of the client, and ends when the property is absent, `null` or empty. A cursor, offset or page that does not advance raises `PaginationException` as a token cursor does.

A list that is followed by a `Link` header (`rel="next"`, RFC 8288) takes the next URL from the header of each response. A next URL that leaves the scheme, host or port of the client is refused with `PaginationException` and nothing, no credentials either, is sent to it; such a method has no starting cursor, so it cannot resume.

A list whose page body carries the next URL in a property (a next URL list) is followed in the same way as a `Link` list: the URL is taken from the body of each response, a next URL that leaves the scheme, host or port of the client is refused with `PaginationException`, nothing is sent to it, and such a method cannot resume from a saved position.

The generator finds a paged operation by its names and shape. The operation must be a GET with one JSON 2xx response, and it must match exactly one of these styles. A token cursor is one query parameter among `cursor`, `after`, `page_token`, `next_token`, `continuation_token` and `marker`, paired with a next-cursor property among `next_cursor`, `next_page_token`, `next_token`, `next_marker`, `next_continuation_token`, `continuation_token` and `next` of the same type. An id cursor is one query parameter among `starting_after`, `after_id` and `after`, paired with a boolean `has_more` and a string `last_id` or `next_cursor` property. A `Link` list has a `Link` header declared on the response. An offset list has an `offset` query parameter with a `limit`, and a page-number list has a `page` query parameter with one of `per_page`, `page_size`, `size` or `limit`. A next URL list has no cursor parameter and a string property with `format: uri` or `uri-reference`, or one named `next_url`, `next_uri`, `nextlink` or `next_link`, that is optional or nullable. An operation that matches more than one style is ambiguous, and one that is streamed is never paged; either keeps only its plain method, and the generation log names it and says why. The `x-fern-pagination` and `x-speakeasy-pagination` (cursor type) extensions are read as hints. The generator option `paginationOverrides` (a JSON object that maps an operation id to `none` or to `{style, requestParam, responseProperty, itemsProperty, hasMoreProperty}`, where the style is `token`, `link`, `id`, `offset`, `page` or `body`) wins over a hint and over inference, and `pagination=off` switches the feature off.

### Custom HTTP client

By default the SDK sends requests through `DefaultApiClient`, which is backed by
the Symfony HTTP client and needs no configuration. To drive the SDK with your
own framework-managed HTTP stack instead — Guzzle, Symfony's PSR-18 adapter,
Laravel's client, or a test double — wrap any [PSR-18](https://www.php-fig.org/psr/psr-18/)
`ClientInterface` in `Psr18ApiClient` and pass it to the client facade:

```php file
use PetstoreClient\Client;
use PetstoreClient\Psr18ApiClient;
use Psr\Http\Client\ClientInterface;
use Psr\Http\Message\RequestFactoryInterface;
use Psr\Http\Message\StreamFactoryInterface;

/* $httpClient is any PSR-18 client; $psr17 is any PSR-17 request/stream factory. */
function clientWithHttpClient(
    ClientInterface $httpClient,
    RequestFactoryInterface&StreamFactoryInterface $psr17,
    string $token
): Client {
    $apiClient = new Psr18ApiClient($httpClient, $psr17, $psr17);

    return Client::withToken('https://api.example.com', $token, null, $apiClient);
}
```

The facade also offers a shortcut that builds the adapter for you:
`Client::withHttpClient($authenticator, $httpClient, $psr17, $psr17, $transportOptions)`.
The SDK never closes or manages your client; you own its lifecycle. Setting a
proxy, a CA bundle or `verifySsl(false)` on `TransportOptions` together with a
supplied client is rejected with an `InvalidArgumentException`, because those
settings belong on your client.

Redirect handling, response decompression, charset decoding, and User-Agent /
X-Request-ID injection are applied uniformly regardless of the transport, so the
injected client gets the same behaviour as the bundled one. Two consequences
follow from PSR-18 being a deliberately minimal, send-only contract:

- **Disable redirects on your client.** The SDK runs its own security-aware
  redirect loop (cross-origin header stripping, HTTPS→HTTP body-replay refusal).
  A transport that also auto-follows would double-follow and defeat those
  protections — e.g. configure Guzzle with `['allow_redirects' => false]`.
- **Transport settings live on your client.** PSR-18 carries no transport
  configuration, so `TransportOptions`' TLS-verification, custom-CA, proxy, and
  timeout settings are not applied to an injected client; configure those when
  you build it. The options still honoured are the request-data ones: default
  headers, User-Agent, and X-Request-ID.

### Testing your code

Every API group has an interface (`PetApiInterface`) and the client has one (`ClientInterface`), so a test can replace the SDK with a hand-written fake: no mocking library, no network.

```php file
use PetstoreClient\Api\PetApiInterface;
use PetstoreClient\ClientInterface;

// Code under test takes the group interface, never the generated class.
function useGroup(PetApiInterface $api): void
{
}

// A fake is any class that implements the interface; this one is generated once for every
// group and records each call it receives.
/**
 * @template T of object
 * @param class-string<T> $interface
 * @return T
 */
function fake(string $interface): object
{
    $methods = '';
    foreach ((new \ReflectionClass($interface))->getMethods() as $method) {
        $params = [];
        foreach ($method->getParameters() as $param) {
            $default = $param->isDefaultValueAvailable() ? ' = ' . var_export($param->getDefaultValue(), true) : '';
            $params[] = ($param->getType() === null ? '' : $param->getType() . ' ') . '$' . $param->getName() . $default;
        }
        $returnType = $method->hasReturnType() ? ': ' . $method->getReturnType() : '';
        $stub = (string) $method->getReturnType() === 'void' ? '$this->calls[] = __FUNCTION__;' : 'throw new \\LogicException();';
        $methods .= 'public function ' . $method->getName() . '(' . implode(', ', $params) . ')' . $returnType . ' { ' . $stub . ' }';
    }

    return eval('return new class implements \\' . ltrim($interface, '\\') . ' { public array $calls = []; ' . $methods . ' };');
}

useGroup(fake(PetApiInterface::class));
```

A new operation in the API adds a method to the interface, so a fake that implements the whole interface stops compiling after you regenerate; generate the fake from the interface as above, or extend a base fake, so only the operation you fake is written.

## Build

```bash
composer install
```

## Test

```bash
vendor/bin/pest
```

## Package

- Name: `openapi/petstoreclient`
- Namespace: `PetstoreClient`
- Version: `1.0.0`
- License: Apache-2.0
- Authors: 

### Versioning

The package follows semantic versioning. The minimum PHP version is the oldest release that still receives security fixes (PHP 8.3 today); it moves up when that release reaches end of life.

## Caveats

### Decimal / `format: number` precision

Symfony's serializer (used internally for JSON deserialization)
downgrades `format: int64` values through PHP `float` before assigning
them to model fields. On 64-bit PHP this means values larger than
2^53 silently lose precision — the same IEEE-754 ceiling that bites
JavaScript and Dart. Snowflake / Twitter / Discord-style IDs above
9 007 199 254 740 991 are rounded.

`format: decimal` fields are `Brick\Math\BigDecimal` from
`brick/math`, an exact decimal. The digits of a decimal are read from
the JSON text itself, never through a PHP `float`, so
`12.345678901234567890123` keeps every digit, and the decimal is
written back with the same digits in plain notation, never with an
exponent. A `type: number` decimal travels as a bare JSON number and a
`type: string` decimal as a JSON string; a string decimal is read only
from a JSON string. `BigDecimal` keeps the scale it was written with,
so `12.50` is written back as `12.50`, and two decimals compare equal
by value (`1.5` equals `1.50`).

A bare `type: number` with no format is still a PHP `float`, so
`0.1 + 0.2` is `0.30000000000000004` there. Do not do arithmetic on
prices, balances, or other money-typed fields that the spec types as a
plain number or as `format: float` or `double`; type them as
`format: decimal` instead.

### Optional and nullable are one

A property that is not required is `null` when the response leaves it out and when it carries an explicit `null`; the model cannot tell the two apart. A property that is required and nullable keeps an explicit `null` and is written as `null` when unset. A required nullable query parameter is kept the same way: when its value is `null` the key is still sent, with an empty value (`?key=`).

### Unknown enum values and discriminators

An enum value the spec does not list decodes to the enum's catch-all member (`UNKNOWN_DEFAULT_OPEN_API`) instead of failing the
response, so a server that adds a value does not break a client built before it. The catch-all member has no
wire value, so writing it raises `SerializationException`. A `oneOf` payload whose discriminator is missing or not mapped still fails
loudly with `SerializationException`.

### Repeated response headers

A header the server repeats arrives in `ApiResult::$headers` and in `ApiException::getResponseHeaders()` as one value joined with `, `, under its lower-cased name.

### Configuration and transport options are separate

`Configuration` holds what belongs to the API (base URL, default headers for every operation) and `TransportOptions` holds how bytes travel (timeouts, retries, proxy, TLS, redirects). They are built separately on purpose: a different transport can be injected without changing either.

### URI formats are strings

A property or parameter of `format: uri` is a PHP `string`: the native URI type needs PHP 8.5 and the SDK supports PHP 8.3.

### Enum string form

A generated enum is a PHP backed enum, which cannot be interpolated into a string. Read the wire value with `->value` (`$case->value`), and use `::tryFrom($value)` to map a string back to a case.

### Schema constraints

The constraint keywords `pattern`, `minLength`, `maxLength`, `minimum`,
`maximum`, `multipleOf`, `minItems` and `maxItems` are validated by the server.
The generated PHP models do not enforce them: the model layer only checks the
declared types, required properties and `format: byte` base64 on decode, so a
value that violates a constraint is sent as given and a response that violates
one is decoded as given. Arrays declared `uniqueItems` are sent in the order of
the collection the caller holds (insertion order of the `\Ds\Set`), not sorted.

Constraint validation can be turned on with the `validateConstraints` configuration option, which is off by default. The SDK then checks every request body against the `minLength`, `maxLength`, `pattern`, `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`, `multipleOf`, `minItems`, `maxItems`, `minProperties` and `maxProperties` keywords of its schema and of the models it holds before the request is sent, and refuses a body that breaks one with an argument error such as `Invalid request body: label must be at least 2 characters long`. Responses are never checked, and a pattern the regular expression engine cannot compile is not enforced.

## Not supported

### Webhooks and callbacks

This SDK is **client → server** only. Spec entries describing
server-initiated calls — OAS 3.1 top-level `webhooks` and OAS 3.0
per-operation `callbacks` — are intentionally skipped during code
generation. If you need to receive webhook deliveries, write the
handler yourself and use this SDK only to deserialize the incoming
payload (e.g. by reusing the relevant request-body model).

### XML bodies and `nodeType`

Request and response bodies in `application/xml` and the XML `nodeType`
keyword are not generated (#70, #88). An operation that only declares XML has
no usable body in this SDK.

### Link Objects

OpenAPI Link Objects are ignored (#78).

### Mutual TLS security schemes

`mutualTLS` security schemes produce no authenticator (#90). Configure a client
certificate on your own PSR-18 client and pass it through `Psr18ApiClient`.

### Conditional-required validation (`dependentRequired` / `dependentSchemas`)

JSON Schema 2019-09 keywords for "if field X is present, field Y is
also required" are **not enforced** by this SDK. No mainstream
OpenAPI client codegen implements them. The server is the authoritative
validator; if you want client-side checking, plug in a JSON Schema
validator library for your language.

### SOCKS proxies

`TransportOptions::builder()->proxy()` accepts only `http://` and `https://` URLs.
Passing a `socks://`, `socks4://`, or `socks5://` scheme is rejected
at construction time with a clear error. SOCKS support would
require enabling extra dependencies / feature flags on the underlying
HTTP library in every one of the 12 SDKs we generate, with non-trivial
API divergence; we explicitly chose not to. If you need SOCKS, route
through a local HTTP-CONNECT bridge or configure it at the OS level.

### Per-call cancellation

The SDK has no cancellation API of its own. To bound how long a call can
take, set the request timeout on `TransportOptions`; a call that exceeds it
fails with `NetworkTimeoutException`.

### `LICENSE` file is not auto-emitted

The package manifest declares Apache-2.0, but no `LICENSE` / `LICENSE.md` file
is generated alongside the sources. Drop the appropriate license text
into the generated tree as part of your release pipeline before
publishing to a registry — most registries warn or block on a missing
file, and the GitHub license auto-detect cannot pick up a manifest-only
declaration.
