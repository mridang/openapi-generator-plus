# PHP

The PHP generator (`php-plus`, class `BetterPHPCodegen`) produces a Composer package whose sources live under `lib/`, one class per file, in the namespace set by `invokerPackage`. It sends requests with the Symfony HTTP client, or with any PSR-18 client you inject, and serializes with the Symfony Serializer. This page lists the supported versions, the type mapping, the dependencies, the generator options and the idioms. The shared pipeline is in [how-it-works.md](../how-it-works.md) and the cross-language type table is in [types.md](../types.md).

## Supported versions

| What | Version | Where it comes from |
|---|---|---|
| PHP | 8.3 or newer (`^8.3`) | `require.php` in the generated `composer.json` |
| Required extensions | `ext-curl`, `ext-json`, `ext-mbstring`, `ext-zlib` | `composer.json` |
| Composer | 2.x | the generated README |
| Symfony components | `^6.4` or `^7.4` | `composer.json` |

The floor is written once, in `composer.json`, and PHPStan is configured with `phpVersion: 80300` so that a function newer than the floor fails the build. The support policy is stated in the generated README: the minimum PHP version is the oldest release that still receives security fixes, and it moves up when that release reaches end of life.

## Type mapping

| OpenAPI | PHP type | Notes |
|---|---|---|
| `string`, `format: date` | `\DateTime` | mutable |
| `string`, `format: date-time` | `\DateTime` | mutable; parsed as RFC 3339 |
| `string`, `format: time` | `\DateTimeImmutable` | PHP has no time-of-day type, so the value carries a date part |
| `string`, `format: duration` | `\DateInterval` | the SDK returns a `PreciseDuration` subclass that keeps nanoseconds, which a plain `\DateInterval` truncates to microseconds; the wire form is a protobuf-JSON duration string such as `3600s` |
| `string`, `format: uuid` | `\Symfony\Component\Uid\Uuid` | from `symfony/uid` |
| `string`, `format: byte` | `string` | the raw bytes after base64 decoding |
| `string`, `format: binary` | `BodySource` for a raw request body; `ByteStream` (a PSR-7 `StreamInterface`) for a response | the response body is read as you consume it and the request body is sent as it is read, see Streaming |
| `string`, `format: uri` | `string` | the native URI type needs PHP 8.5; decoding refuses a value that is not an absolute URI |
| `string`, `format: ipv4`, `ipv6` | `string` | no address class in PHP; decoding refuses a value with a leading zero, prefix length, zone, brackets, whitespace or the wrong family |
| `string`, `format: email`, `hostname`, and plain `string` | `string` | no dedicated type, no validation |
| `integer`, `format: int32` | `int` | |
| `integer`, `format: int64` | `int` | see Known limits |
| `integer`, no format | `int` | PHP integers are 64-bit on 64-bit builds |
| `number`, any format | `float` | including `format: decimal`, so money values lose exact decimal representation |
| `boolean` | `bool` | |
| `array` | `\Ds\Vector` | from `php-ds`, with element types in PHPDoc |
| `array` with `uniqueItems` | `\Ds\Set` | sent in insertion order, not sorted |
| `object` with `additionalProperties` | `\Ds\Map` | `T` is the declared value type; a free-form bucket keeps the any-type |
| free-form `object` | `mixed` | |
| nullable property | `?T` | an absent key and an explicit `null` read the same; a required and nullable property keeps its key and is written as `null` |
| `enum` | backed enum | read the wire value with `->value`, map back with `::tryFrom()`; an unknown value decodes to the `UNKNOWN_DEFAULT_OPEN_API` case (D117), which cannot be written |
| `oneOf`, `anyOf` | a wrapper class | see Idioms |
| file upload and download | `BodySource` upload (a multipart file part stays `\SplFileObject`), `ByteStream` download | same as `binary` |

Model properties are `readonly`.

## Dependencies

| Dependency | Why |
|---|---|
| `symfony/http-client` | the default HTTP client |
| `symfony/serializer`, `symfony/property-access`, `symfony/property-info`, `phpdocumentor/reflection-docblock` | JSON decoding into typed models |
| `symfony/mime` | multipart bodies |
| `symfony/uid` | the UUID type |
| `php-ds/php-ds` | `\Ds\Vector`, `\Ds\Set` and `\Ds\Map`; installing the PECL `ext-ds` replaces the userland classes with native ones |
| `psr/http-client`, `psr/http-factory`, `psr/http-message` | the `Psr18ApiClient` adapter for your own HTTP client |
| `ext-brotli`, `ext-zstd` (suggested) | decoding `br` and `zstd` responses; without them only `gzip` and `deflate` are advertised |
| `open-telemetry/api` (suggested) | W3C trace context on outgoing requests, and the `OpenTelemetryCallHook` adapter (PHP 8.2 or newer, below the 8.3 floor) |

Development tooling is Pest, PHP-CS-Fixer (`@PSR12`), PHP_CodeSniffer, PHPStan at level 9 and Rector (`UP_TO_PHP_83`); none of it is a runtime dependency.

## Generator options

| Option | Default | Effect |
|---|---|---|
| `invokerPackage` | `OpenAPI\Client` | root namespace of the SDK |
| `packageName` | derived from the invoker package | Composer package name, such as `vendor/name` |
| `packageVersion` | `1.0.0` | package version |
| `apiPackage`, `modelPackage` | `Api`, `Models` | sub-namespaces under the invoker package |
| `repositoryUrl` | none | written to `composer.json` under `support.source` |
| `homepageUrl` | none | written as `homepage` |
| `authors` | none | comma or semicolon separated `Name` or `Name <email>` entries |
| `documentationUrl` | none | written under `support.docs` |
| `issueTrackerUrl` | none | written under `support.issues` |
| `envVarPrefix` | the API title without a trailing `SDK`, in upper snake case | prefix of the credential environment variables; the same prefix in every language |
| `clientClassName` | `Client` | name of the client facade |
| `errorPrefix` | `OpenAPI` | prefix of the root error type; PHP appends `Exception` |
| `generateTests` | `true` | emit the full test suite |
| `generateUnitTests` | `false` | emit only the spec-independent unit tests |

Only `invokerPackage` and `packageVersion` are declared as options in the generator; the others are read from the same configuration by name.

## Idioms

- **Errors.** Failures derive from `OpenAPIException`. `ApiException` carries the status, headers, body, `getRequestMethod()` and `getRequestPath()` (without the query string), and its string form prints a `Request: GET /items/7` line. `NetworkException` and `NetworkTimeoutException` mean no response arrived; `TooManyRequestsException` is HTTP 429. Decoding problems raise `SerializationException`. A malformed request URL (not an absolute http or https URL with a usable host) raises `\InvalidArgumentException` before anything is sent.
- **Retries.** Two retries by default (`TransportOptions::builder()->maxRetries(n)`, `0` disables them), after a network error before any response and after 408, 409, 429 and 5xx except 501. The wait starts at 0.5 seconds, doubles up to 8 seconds, loses up to 25% to jitter and is replaced by `Retry-After` (at most 60 seconds). The default end-to-end timeout is 10 000 ms and `null` waits indefinitely.
- **Cancellation.** The SDK has no cancellation API; bound a call with the timeout.
- **Authentication.** Basic, bearer and API key authenticators, plus OAuth 2 and OpenID Connect. The client takes one optional credential per distinct security scheme, a named parameter named after the scheme (`new Client(petStoreBearer: $credential)`); each operation uses the scheme its security requirement names (the first alternative whose schemes are all configured, several schemes of one alternative together through `CompositeAuthenticator`) and never sends the credential of another scheme. There is no per-call authenticator. The authenticator passed first (and `Client::withToken()`) serves a secured operation only when no scheme credential is configured; a client needs at least one of the two. After a 401 from an OAuth 2 or OpenID Connect call the cached access token is dropped and the request is sent once more. The generated authenticator of a basic, bearer or API key scheme takes its credential as an argument or, when you pass `null`, reads it from an environment variable named for that scheme and field (`{PREFIX}_{SCHEME}_{FIELD}`, for example `PETSTORE_SERVICE_TOKEN_TOKEN`; the scheme id is left out when no other scheme has a field of that name). The explicit argument or provider wins (a provider you pass outranks the variable), then the variable, read once when the authenticator is built (an empty variable counts as unset and an explicit empty value is refused); a credential found in neither place throws `InvalidArgumentException` when the authenticator is built, before any request. `BearerAuthenticator` and `ApiKeyAuthenticator` also take a `\Closure` that supplies the credential for every request. The base classes read no variable, and the OAuth 2 and OpenID Connect authenticators take their credentials as arguments. The generated README lists the variable of every scheme.
- **Immutability.** Properties are `readonly`, and every property has a `withX()` method that returns a changed copy.
- **Cookie parameters.** A cookie parameter value is sent as given when every character is a legal RFC 6265 cookie-octet; any other character (space, comma, semicolon, double quote, backslash, a control or non-ASCII character) fails with `InvalidArgumentException` before the request is sent. Percent-encode the value yourself when it needs it; the SDK never encodes a cookie.
- **No validation at construction.** A model constructor never validates values (PHP's native types are the only check); the wrong type or an enum string that is not a case is reported when decoding, as a `SerializationException`.
- **Defaults beside a reference.** An optional property that is a `$ref` with a `default` written next to it starts at that default in the constructor (`?Availability $availability = Availability::ON_HOLD` for an enum, a literal for a referenced string, integer, number or boolean), so a decoded model without the key and `new Model(...)` agree; an explicit `null` is kept.
- **Unions.** A `oneOf` or `anyOf` model is a wrapper class with `fromX()`, `isX()` and `asX()` for each variant, plus `getActualInstance()`. An operation with several `2xx` responses returns a union decoded by the status that arrived.
- **Optional arguments.** Every operation takes an options object as its last argument, even one with no query, header, form or cookie parameter and no security scheme, and per-call `timeout`, `headers` and `maxRetries` go in it (`new OperationOptions(timeout: 2000)`); no options object has an `auth` field. `timeout` and `maxRetries` need the bundled transport or a subclass of `AbstractApiClient`.
- **Lifecycle.** `$client->close()` is graceful and idempotent: a request already sent finishes, every later request fails with a `\LogicException` whose message is `client closed`, and only a transport the SDK created is released, never a PSR-18 or `ApiClient` you supplied. Cancellation stays per request, through the `timeout` option.
- **Raw responses.** Every operation has a `...WithHttpInfo` variant returning an `ApiResult` with public `statusCode`, `data`, `rawBody` and `headers`.
- **Custom transport.** Wrap any PSR-18 client in `Psr18ApiClient`, or use `Client::withHttpClient(...)`. The SDK keeps its own redirect loop, so disable redirects on your client. `TransportOptions` settings for proxy, TLS and timeout do not apply to an injected client, and setting a proxy, CA bundle or `verifySsl(false)` together with one is rejected with `InvalidArgumentException`.
- **Request id.** `injectRequestId(true)` adds an `X-Request-ID` header to every request.
- Testing your code: `<Group>ApiInterface` and `ClientInterface` (one file each, PSR-4), with `get<Group>()` accessors beside the `readonly` properties because interface properties need PHP 8.4. `ClientInterface` is not the PSR-18 `Psr\Http\Client\ClientInterface`: `Client.php` imports the PSR one, so it names ours by its full name. The tests build fakes with `fakeGroup()` in `tests/bootstrap.php`, which generates a class from the interface with reflection and `eval`.
- **Call hook.** `CallHook::start(CallInfo $info, array &$headers): ?CallObserver` runs once per operation call, after the trace context is injected, and may edit the headers (the array is passed by reference, as `TraceContextUtil::injectTraceContext()` does). The returned `CallObserver::finish(CallOutcome $outcome)` runs exactly once on every exit path with the status (`0` when none arrived), the lower-cased headers (`null` when none arrived) and the error the caller receives. Set it with `Configuration::builder()->callHook($hook)` (read with `getCallHook()`) or the trailing `callHook` argument of the client constructor and factories; `CallHookChain::chain($a, $b)` starts in order and finishes in reverse. It sits above the transport, so it also fires with a supplied PSR-18 or `ApiClient` transport. An exception from `start` reaches the caller and nothing is sent; one from `finish` is swallowed. PHP has no cancellation, so no outcome carries one.
- **OpenTelemetry.** `OpenTelemetryCallHook::create(?TracerProviderInterface, ?MeterProviderInterface, ?TextMapPropagatorInterface)` records a client span named with the operation id, `http.client.request.count` and `http.client.request.duration` per call, with the scope named after the Composer package and the SDK version. It uses only `open-telemetry/api` and does nothing when the package is absent. A PHP process runs one request at a time, so a metric reaches a backend only when the application flushes its meter provider.
- **Close and retries.** `close()` while a call runs lets that call finish, including its retries; only a new call is refused with the closed-client `LogicException`. A call whose time budget is already spent raises `NetworkTimeoutException` before anything is sent, and a response body with bytes that are not valid in its charset decodes with each bad byte replaced by U+FFFD.
- **Streaming.** An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns `ByteStream`, a `final` class that implements the PSR-7 `StreamInterface` (read-only and forward-only), from the one operation method (there is no second method), and `...WithHttpInfo` returns `ApiResult<ByteStream>` with an empty `rawBody`. A `ByteStream` has the public properties `statusCode` and `headers` (lower-cased), `read($length)`, `eof()`, an idempotent `close()` that aborts the connection instead of draining it, a `readAll()` that reads the rest and leaves the stream closed, and a string form that shows only the status and the bytes delivered (it does not read the body, unlike most PSR-7 streams); a stream that is dropped without being read to the end releases its connection. A transport that can stream implements the second interface `StreamingApiClient::sendStreaming($method, $url, $headers, $body, $timeoutMs, $maxRetries, $idleTimeoutMs)`, which returns a `ByteStream`; `ApiClient` is unchanged, so an injected transport that only implements `sendRequest` keeps working for buffered calls, and a binary operation on it fails with `\BadMethodCallException` naming the transport. `DefaultApiClient` and `Psr18ApiClient` both stream through `AbstractApiClient`, which runs the buffered call's header phase (retries, redirects, the end-to-end `timeout`, the header rules) and stops there: the `timeout` ends at the response headers, `TransportOptions::builder()->idleTimeout($millis)` (default 60 seconds, `0` or `null` disables) bounds the gap between two reads of the body and raises `NetworkTimeoutException` (the default transport only: PSR-18 carries no timeouts), and nothing is retried after the first body byte. A streaming call offers `Accept-Encoding: gzip, deflate` and decodes while the body is read. A non-2xx status raises the usual typed error with its body read up to 64 KiB. A reset, or a body shorter than its `Content-Length`, raises `StreamException` (a `NetworkException`, status 0). `$client->close()` cancels the streams still open; their next read raises the closed-client `\LogicException`. Uploads are unchanged.
- **Uploads.** An operation whose request body is raw binary takes a `BodySource` (`final`, in the SDK namespace) in every signature (the operation, `...WithHttpInfo`, the `...ApiInterface` and the client): `BodySource::of(string $bytes)`, `BodySource::ofFile(string $path)` and `BodySource::fromFactory(callable $factory[, ?int $length])` (a factory that returns a stream resource, a PSR-7 stream, an `\SplFileObject` or a string) are replayable, `BodySource::once($stream[, ?int $length])` (a stream resource, a PSR-7 stream or an `\SplFileObject`) is single use. The body is sent as it is read, never buffered: a known length sends `Content-Length`, an unknown one is chunked. A replayable body is opened again for a retry after a 5xx, a 429 or a network error, for a 307 or 308 redirect and for the replay after a token refresh. A single-use body is never retried (the original error is raised unchanged) and a 307 or 308 raises `ApiException` with `Refusing to replay a streamed request body`; it is never buffered. The SDK never closes a stream passed to `once` and closes every stream it opened itself from a file or a factory as soon as the last byte was read. A transport that is not the bundled one reads a body with `BodySource::chunks()`. The string form shows the kind and the length, never the content or the path. A multipart body stays buffered.
- **JSON Lines and server-sent events.** An operation whose success response is JSON Lines (`application/x-ndjson`, `application/jsonl`, `application/jsonlines`) or `text/event-stream` has no buffered method and no `WithHttpInfo`; it has `<operation>Stream`, taking the operation's parameters, its `Options` object and a trailing `?StreamOptions`. JSON Lines returns a `\Generator` of the schema type (leaving the loop or dropping the generator releases the connection, and it carries no status or headers, because a generator has no place for them); server-sent events return `SseStream<T>`, an `\IteratorAggregate` of `ServerSentEvent<T>` (readonly `id`, `event`, `data`, `retry`, `raw`) with `close()`, `getStatusCode()` and `getHeaders()`. The first request is sent by the call itself. `StreamOptions` (named constructor arguments `idleTimeout`, `maxReconnects`, `reconnectDelay`, `lastEventId`, `maxEventBytes`) is immutable. The parser is `EventStream` (lines end with LF, CR or CRLF; the WHATWG event grammar). A line or event over `maxEventBytes` (1 MiB), or a JSON Lines line that does not decode, raises `SerializationException` and ends the stream; an event named `error` raises `ApiException`. An event stream reconnects after a clean end, a network error or an idle timeout with fresh headers (the authenticator is asked again) and `Last-Event-ID`, waits the server's `retry`, else `Retry-After` (429, 502, 503, 504), else `reconnectDelay` doubling to 30 seconds with up to 25% jitter, and raises the last failure (`StreamException` for a clean end) after `maxReconnects` failures in a row without an event.
- With `generateExamples` on, the project ships `examples/quickstart.php`, a script that requires `vendor/autoload.php`, builds a client with `Client::withToken` from `{PREFIX}_BASE_URL` and `{PREFIX}_TOKEN`, calls the example operation and closes the client in a `finally` block. `make examples` lints it with `php -l` and analyses it with PHPStan at level 9, and `make examples-run` runs it.

## Recipes

These are answers to questions that come up often, each built from what the generated SDK already has. None of them adds API.

### Hooks around every call

The call hook added by [#158](https://github.com/mridang/openapi-generator-plus/issues/158) is the one place to edit the request headers before the send and to observe the status or the error afterwards, for every operation and for the client as a whole. It is optional and does nothing when it is not set. It runs once per operation call however many sends happen inside it (retries, the one replay after a `401`), once for the plain and the `WithHttpInfo` variant alike, and also when you supply your own transport. `start` receives the call information (`operationId` as written in the spec, `method`, `urlTemplate`, `url` without its query string, `serverAddress`) and the final headers, which it can read and replace, and it returns the observer to call when the call ends or nothing. The observer's `finish` receives the status code (0 when no response arrived), the lower-cased response headers (none when no response arrived) and the error the caller receives (none on success). An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed; the hook never changes the result or the error. `CallHook` is the name to look for in this SDK.

```php
final class TenantHook implements CallHook
{
    public function start(CallInfo $info, array &$headers): ?CallObserver
    {
        $headers['X-Tenant'] = 'a';

        return null;
    }
}

$client = Client::withToken('https://api.example.com', 'your-token', callHook: new TenantHook());
```

To see every send of a call rather than the call as a whole (for example each retry), wrap the transport you hand to the SDK instead: see "Custom HTTP client" in the generated README. A transport you supply is never closed by the SDK, and the hook above still fires with it.

### Serving another region or tenant

A client is cheap, and its host, credentials, retry settings and connections belong to it. To call another region or another tenant, build another client; there is no per-call server URL or per-call HTTP client, and the per-call options stay `timeout`, `headers` and `maxRetries`.

```php
$eu = Client::withToken('https://eu.api.example.com', 'eu-token');
$us = Client::withToken('https://us.api.example.com', 'us-token');
```

Call `close()` on each when you are done.

### Renaming types, fields and parameters

The names come from the specification, so the seam is the normaliser: the `RUN_SCRIPT` rule runs your own JavaScript over the document before generation (see [how it works](../how-it-works.md#renaming-with-a-script)). A schema renamed to `Animal` is generated as `lib/Models/Animal.php`, and this language's own casing, escaping of reserved words and file naming are applied to the new name like to any other. The SDK has no configuration map of names and never reads a vendor extension for a name. The name of a property or a parameter is also its name on the wire, so renaming one in the script renames it in the requests and responses as well: there is no rename that changes only the SDK name.

### Status, headers and the raw body

Every operation has a `...WithHttpInfo` variant that returns the status code, the lower-cased response headers, the raw body and the decoded data. Every failure that carries an HTTP response is an `ApiException` that holds the status code, the response headers and the response body. A live response object is not exposed, because that would need streaming (see [decisions](../decisions.md)); to see or change what goes over the wire, use the call hook above or the transport you supply.

```php
$result = $client->pet->addPetWithHttpInfo($body);
$status = $result->statusCode;
$etag = $result->headers['etag'] ?? null;
$raw = $result->rawBody;

try {
    $client->pet->addPet($body);
} catch (\PetstoreClient\Errors\ApiException $e) {
    $failed = [$e->getStatusCode(), $e->getResponseHeaders(), $e->getResponseBody()];
}
```

## Known limits

- The generated README states that the Symfony serializer takes `int64` values through `float`, so values above 2^53 may lose precision on 64-bit PHP. See Under review.
- `number` values, including `format: decimal`, are `float`, so `0.1 + 0.2` is not exact. Use BCMath on the raw value when you need exact arithmetic.
- `date` and `date-time` are mutable `\DateTime` objects, which can be changed in place even inside a `readonly` model.
- A JSON, text, form or multipart request body and a JSON, text or form response body are read whole into memory; a raw binary request body is a `BodySource` and is sent as it is read. A binary response is a `ByteStream`, and a JSON Lines or server-sent events response is a stream returned by a `...Stream` method; see Streaming.
- Schema constraints (`pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems`, `maxItems`) are not checked by the SDK; the model layer checks only declared types, required properties and base64 for `format: byte`.
- `dependentRequired` and `dependentSchemas` are not enforced.
- Webhooks and callbacks, XML bodies, Link Objects and `mutualTLS` security schemes are not generated. For mutual TLS, configure the certificate on your own PSR-18 client.
- Only `http://` and `https://` proxies are accepted; SOCKS is rejected.
- A header repeated by the server is joined into one value with `, `.
- The SDK does not log; wrap your own PSR-18 client with a PSR-3 logging decorator.
- No `LICENSE` file is generated.
- Operations are blocking only, as a plain method call, by decision D111. There is no asynchronous variant of an operation, because each SDK follows the style of its own language. Run the call on your own thread or task when you need concurrency.
- An optional and nullable property has no third state: an absent value and an explicit `null` are the same, by decision D112. A three-state type for PATCH-style APIs is declined for now.
- An `integer` with no format is 64 bits, the same as `int64`, by decision D113 ([#159](https://github.com/mridang/openapi-generator-plus/issues/159)). A value above 2^31, such as a large identifier, decodes exactly.

## Under review

These are proposals from a review of the type mapping against current PHP. None of them is decided, and none changes what the generator produces today.

- Map `date` and `date-time` to `\DateTimeImmutable` instead of `\DateTime`. The review says this needs no floor change, but it needs a golden regeneration and updates to the `withX()` methods and tests.
- Settle the `int64` caveat with a test: round-trip ids of 2^62 and 2^63-1 through the response decoder. If they survive, remove the README caveat; if not, fix the decoder so `int64` never goes through `float`.
- Map decimals to `\BcMath\Number`. This needs `ext-bcmath` and would raise the floor from 8.3 to 8.4, so it waits for that floor. Until then the type stays `float`.
- Map `format: uri` to `\Uri\Rfc3986\Uri` only once the floor reaches 8.5. Until then it stays `string`.
- Keep `\DateTimeImmutable` for `time` and `\DateInterval` for durations: the review found no newer PHP type for either.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. `Globals::propagator()->inject` uses the current context (`$span->activate()`); one request at a time, so no hand-off.

## Concurrency, cancellation and equality notes

- A call takes no cancellation signal (S7). To cancel a call that runs in a `Fiber`, throw into the fiber (`Fiber::throw()`): the exception reaches the caller unchanged, is never wrapped in the SDK error or reported as a timeout, and the hook's `finish` receives it. A cancel during a retry wait stops the retrying.
- `OAuth2TokenManager::stop()` drops the held tokens; a later token request throws `OAuth2TokenException` with a fixed message, and stopping again does nothing.
- Every model implements `\Ds\Hashable`, so a `\Ds\Set` or `\Ds\Map` stores equal models once.
