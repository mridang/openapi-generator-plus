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
| `string`, `format: binary` | `string` for bodies, `\SplFileObject` for files | a download is written to a temporary file named from `Content-Disposition` |
| `string`, `format: uri` | `string` | the native URI type needs PHP 8.5 |
| `string`, `format: email`, `hostname`, `ipv4`, `ipv6`, and plain `string` | `string` | no dedicated type |
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
| `enum` | backed enum | read the wire value with `->value`, map back with `::tryFrom()`; an unknown value fails with `SerializationException` |
| `oneOf`, `anyOf` | a wrapper class | see Idioms |
| file upload and download | `\SplFileObject` | |

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
| `open-telemetry/api` (suggested) | W3C trace context on outgoing requests |

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
- **Authentication.** Basic, bearer and API key authenticators, plus OAuth 2 and OpenID Connect. The client takes one optional credential per distinct security scheme, a named parameter named after the scheme (`new Client(petStoreBearer: $credential)`); each operation uses the scheme its security requirement names (the first alternative whose schemes are all configured, several schemes of one alternative together through `CompositeAuthenticator`) and never sends the credential of another scheme. There is no per-call authenticator. The authenticator passed first (and `Client::withToken()`) serves a secured operation only when no scheme credential is configured; a client needs at least one of the two. After a 401 from an OAuth 2 or OpenID Connect call the cached access token is dropped and the request is sent once more. The generated authenticator of a basic, bearer or API key scheme takes its credential as an argument or, when you pass `null`, reads it from an environment variable named for that scheme and field (`{PREFIX}_{SCHEME}_{FIELD}`, for example `PETSTORE_SERVICE_TOKEN_TOKEN`; the scheme id is left out when no other scheme has a field of that name). The explicit argument wins, then the variable, read once when the authenticator is built (an empty value counts as unset); a credential found in neither place throws `InvalidArgumentException` when the authenticator is built, before any request. `BearerAuthenticator` and `ApiKeyAuthenticator` also take a `\Closure` that supplies the credential for every request. The base classes read no variable, and the OAuth 2 and OpenID Connect authenticators take their credentials as arguments. The generated README lists the variable of every scheme.
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

## Known limits

- The generated README states that the Symfony serializer takes `int64` values through `float`, so values above 2^53 may lose precision on 64-bit PHP. See Under review.
- `number` values, including `format: decimal`, are `float`, so `0.1 + 0.2` is not exact. Use BCMath on the raw value when you need exact arithmetic.
- `date` and `date-time` are mutable `\DateTime` objects, which can be changed in place even inside a `readonly` model.
- Request and response bodies are read into memory; there is no streaming.
- Schema constraints (`pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems`, `maxItems`) are not checked by the SDK; the model layer checks only declared types, required properties and base64 for `format: byte`.
- `dependentRequired` and `dependentSchemas` are not enforced.
- Webhooks and callbacks, XML bodies, Link Objects and `mutualTLS` security schemes are not generated. For mutual TLS, configure the certificate on your own PSR-18 client.
- Only `http://` and `https://` proxies are accepted; SOCKS is rejected.
- A header repeated by the server is joined into one value with `, `.
- The SDK does not log; wrap your own PSR-18 client with a PSR-3 logging decorator.
- No `LICENSE` file is generated.

## Under review

These are proposals from a review of the type mapping against current PHP. None of them is decided, and none changes what the generator produces today.

- Map `date` and `date-time` to `\DateTimeImmutable` instead of `\DateTime`. The review says this needs no floor change, but it needs a golden regeneration and updates to the `withX()` methods and tests.
- Settle the `int64` caveat with a test: round-trip ids of 2^62 and 2^63-1 through the response decoder. If they survive, remove the README caveat; if not, fix the decoder so `int64` never goes through `float`.
- Map decimals to `\BcMath\Number`. This needs `ext-bcmath` and would raise the floor from 8.3 to 8.4, so it waits for that floor. Until then the type stays `float`.
- Map `format: uri` to `\Uri\Rfc3986\Uri` only once the floor reaches 8.5. Until then it stays `string`.
- Keep `\DateTimeImmutable` for `time` and `\DateInterval` for durations: the review found no newer PHP type for either.

Trace context: a request carries the span active in the context of the call, never one the SDK builds itself. `Globals::propagator()->inject` uses the current context (`$span->activate()`); one request at a time, so no hand-off.
