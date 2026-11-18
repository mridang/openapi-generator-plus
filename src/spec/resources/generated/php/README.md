# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated PHP SDK client for the Swagger Petstore - OpenAPI 3.0 API.

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

```php
use PetstoreClient\Client;

$client = Client::withToken('https://api.example.com', 'your-token');

/* $body is the \PetstoreClient\Models\Pet you want to send. */
$result = $client->pet->addPet($body);
$client->close();
```

Closing the client is graceful: a request already sent finishes, every later request fails with a `\LogicException` (`client closed`), and only the transport the SDK built is released, never a client you supplied. Calling `close()` twice is harmless. Errors derive from `OpenAPIException`; `NetworkException` and `NetworkTimeoutException` mean no HTTP response arrived, and `TooManyRequestsException` is HTTP 429. See [SKILLS.md](SKILLS.md) for authentication, servers and error handling.

### Raw responses

Every operation has a `...WithHttpInfo` variant returning an `ApiResult` with the public properties `statusCode`, `data`, `rawBody` and the lower-cased `headers`. It is the way to read a response header; a header repeated by the server arrives as one value joined with `, `.

### Timeouts

`TransportOptions::builder()->timeout(5000)` sets one end-to-end budget in milliseconds for the whole call, redirects and retries included. A call that exceeds it fails with `NetworkTimeoutException`; the default is 10 000 ms and `null` waits indefinitely.

### Automatic retries

A request that fails with a transient error is retried by the SDK. `TransportOptions::builder()->maxRetries()` sets how many times (default 2, so up to three attempts); `maxRetries(0)` disables retrying.

```php
use PetstoreClient\TransportOptions;

$transport = TransportOptions::builder()->maxRetries(4)->build();
```

A request is retried after a network error that happened before a response arrived, and after a 408, 409, 429 or 5xx response (not 501). The wait starts at 0.5 s, doubles up to 8 s and is shortened by up to 25% at random; a `Retry-After` header (seconds or an HTTP date, at most 60 s) replaces it. Retries never extend the request timeout: when the next wait would outlast what is left of it, the last failure is reported. A stream or multipart body is never retried, and every retry is a new request with freshly built authentication headers.

### Expired tokens

When an OAuth2 or OpenID Connect call is answered with 401, the cached access token is dropped (a refresh token is used first when there is one) and the request is sent once more with fresh headers. A second 401 is raised as `UnauthorizedException`. A stream or multipart body is not replayed, and API key, basic and bearer authenticators never replay.

### Per-call options

Every operation takes an options object as its last argument, even one with no query, header, form or cookie parameter and no security scheme (`$api->operation()` with no argument still works). It accepts three fields for that one call: `timeout` (milliseconds, positive), `headers` (a name to value map that wins over a configured header of the same name) and `maxRetries` (0 or more). They replace the transport settings for that call only.

```php
$api->operation($required, new OperationOptions(timeout: 2000, headers: ['X-Tenant' => 'acme'], maxRetries: 0));
```

`timeout` and `maxRetries` need the bundled transport or a subclass of `AbstractApiClient`; a transport you wrote yourself against `ApiClient` sends the headers and ignores the other two.

### Credentials

The client takes one optional credential per security scheme, named after the scheme (`new Client(petStoreBasic: $credential)`): `petStoreBasic`, `adminBasic`, `petStoreBearer`, `serviceToken`, `apiKeyHeader`, `internalApiKey`, `apiKeyQuery`, `sessionCookie`, `machineAuth`, `legacyAuth`, `userAuth`, `browserAuth`, `ssoAuth`. Each operation uses the scheme its security requirement names (the first alternative whose schemes are all configured; the schemes of one alternative are sent together) and sends no credential of another scheme. An operation has no per-call authenticator. The authenticator given first (`Client::withToken()`) is used for a secured operation only when no scheme credential is configured.

The authenticator of a basic, bearer, API key or OAuth 2 security scheme takes its credentials as arguments, or reads each one from an environment variable when you pass `null`. Every variable is named `SWAGGER_PETSTORE_OPEN_API_3_0_` followed by the field (`TOKEN`, `USERNAME`, `PASSWORD`, `API_KEY`, `CLIENT_ID`, `CLIENT_SECRET`, `REDIRECT_URI`); when two schemes have a field of the same name, the scheme id sits between the two so that no scheme reads another's secret. Set the prefix with the `envVarPrefix` generator option.

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

An argument always wins over the environment, an empty variable counts as unset, and the variable is read once, when the authenticator is built. A credential that is neither passed nor in the environment throws `InvalidArgumentException` at that point, so a missing credential never waits for the first request. Pass a `\Closure` token provider (`new BearerAuthenticator($host, null, fn (): string => $vault->token())`) to supply a rotating credential; it is called for every request. The base classes `BearerAuthenticator`, `BasicAuthenticator`, `ApiKeyAuthenticator` and the OAuth 2 authenticators read no variable: only the generated authenticator of a scheme knows its name. The OpenID Connect authenticator takes its credentials as arguments only.

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

```php
use PetstoreClient\CallHook;
use PetstoreClient\CallInfo;
use PetstoreClient\CallObserver;
use PetstoreClient\CallOutcome;
use PetstoreClient\Client;

final class TimingHook implements CallHook
{
    public function start(CallInfo $info, array &$headers): ?CallObserver
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

```php
use PetstoreClient\OpenTelemetryCallHook;

$client = Client::withToken('https://api.example.com', 'your-token', callHook: OpenTelemetryCallHook::create());
```

The span is named with the operation id, has kind client and carries `http.request.method`, `url.template`, `server.address`, `http.response.status_code` (only when a response arrived) and `error.type` (only on failure: the status code for a status of 400 or more, otherwise `timeout`, `network_error`, `serialization_error`, `cancelled` or `_OTHER`). The counter `http.client.request.count` (unit `{request}`) and the histogram `http.client.request.duration` (unit `s`) carry the same attributes; the histogram's own count equals the request count. The instrumentation scope is the published package name and the SDK version. The hook writes `traceparent` itself, so it names the client span as its parent. No attribute carries a query string, a header value or a credential.

### Logging

The SDK does not log. To log requests and responses, wrap your own PSR-18 client with a logging decorator (PSR-3) and pass it as described under Custom HTTP client.

### Custom HTTP client

By default the SDK sends requests through `DefaultApiClient`, which is backed by
the Symfony HTTP client and needs no configuration. To drive the SDK with your
own framework-managed HTTP stack instead — Guzzle, Symfony's PSR-18 adapter,
Laravel's client, or a test double — wrap any [PSR-18](https://www.php-fig.org/psr/psr-18/)
`ClientInterface` in `Psr18ApiClient` and pass it to the client facade:

```php
use PetstoreClient\Psr18ApiClient;

/* $httpClient is any PSR-18 client; $psr17 is any PSR-17 request/stream factory. */
$apiClient = new Psr18ApiClient($httpClient, $psr17, $psr17);
$client = Client::withToken('/api/v3', $token, null, $apiClient);
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

```php
<?php

use PetstoreClient\Api\PetApiInterface;
use PetstoreClient\ClientInterface;

// Code under test takes the group interface, never the generated class.
function useGroup(PetApiInterface $api): void
{
}

// A fake is any class that implements the interface; this one is generated once for every
// group and records each call it receives.
function fake(string $interface): object
{
    $methods = '';
    foreach ((new ReflectionClass($interface))->getMethods() as $method) {
        $params = [];
        foreach ($method->getParameters() as $param) {
            $default = $param->isDefaultValueAvailable() ? ' = ' . var_export($param->getDefaultValue(), true) : '';
            $params[] = ($param->getType() === null ? '' : $param->getType() . ' ') . '$' . $param->getName() . $default;
        }
        $returnType = $method->hasReturnType() ? ': ' . $method->getReturnType() : '';
        $body = (string) $method->getReturnType() === 'void' ? '$this->calls[] = __FUNCTION__;' : 'throw new LogicException();';
        $methods .= 'public function ' . $method->getName() . '(' . implode(', ', $params) . ')' . $returnType . ' { ' . $body . ' }';
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
- License: MIT
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

`format: decimal` / `format: number` fields are likewise stored as
PHP `float`, so monetary values lose their exact decimal
representation. `0.1 + 0.2` in PHP is `0.30000000000000004` — do not
do arithmetic on prices, balances, or other money-typed fields. Pass
them through as strings via `BCMath` if you need exact arithmetic.

Fixing this end-to-end would require switching the wire layer to
emit JSON numbers from internal string/GMP representations, which
breaks `format: int64` schema validation against strict mock servers
(verified with Chasm). Documented as a known limitation.

### Optional and nullable are one

A property that is not required is `null` when the response leaves it out and when it carries an explicit `null`; the model cannot tell the two apart. A property that is required and nullable keeps an explicit `null` and is written as `null` when unset. A required nullable query parameter is kept the same way: when its value is `null` the key is still sent, with an empty value (`?key=`).

### Unknown values fail loudly

A response value that is not in a declared enum, or a `oneOf` payload whose discriminator is missing or not mapped, is not guessed at: decoding throws `SerializationException` (or `InvalidArgumentException` from `build()`), so a new server-side value surfaces as an error instead of a wrong model.

### Repeated response headers

A header the server repeats arrives in `ApiResult::$headers` and in `ApiException::getResponseHeaders()` as one value joined with `, `, under its lower-cased name.

### Streaming is not supported

Request and response bodies are read into memory. To stream, supply your own transport (see Custom HTTP client).

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

The package manifest declares MIT, but no `LICENSE` / `LICENSE.md` file
is generated alongside the sources. Drop the appropriate license text
into the generated tree as part of your release pipeline before
publishing to a registry — most registries warn or block on a missing
file, and the GitHub license auto-detect cannot pick up a manifest-only
declaration.
