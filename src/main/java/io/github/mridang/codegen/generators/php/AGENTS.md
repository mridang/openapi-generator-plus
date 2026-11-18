# PHP SDK generator

This folder holds `BetterPHPCodegen` (generator name `php-plus`), which turns an OpenAPI document into a Composer package whose namespace is `invokerPackage` and whose sources live under `lib/` (PSR-4, one class per file). The generated SDK sends requests through the Symfony HTTP client (`DefaultApiClient`) or, when the caller injects one, any PSR-18 client (`Psr18ApiClient`), serialises with Symfony Serializer and `php-ds` collections (`\Ds\Vector`, `\Ds\Set`, `\Ds\Map`), and tests with Pest 4 (parallel) over PHPUnit 12. The floor is PHP 8.3, the oldest release that still gets security fixes (the manifest requires `ext-curl`, `ext-json`, `ext-mbstring`, `ext-zlib`; `format: uri` is a `string`, the native URI type needs 8.5). The shipped tooling is php-cs-fixer (`@PSR12`), PHP_CodeSniffer (PSR-12), PHPStan level 9 with peststan over sources and tests (with `phpVersion: 80300`, so a function newer than the floor fails), and Rector (`UP_TO_PHP_83`, code quality, dead code, early return, type declarations). Cross-language rules are written once in the shared file; this file states only how php realises them. Decisions marked "pending in the golden" are settled but not yet implemented in the checked-in golden.

## Read first

- Shared cross-language rules: [`../AGENTS.md`](../AGENTS.md)
- Repository root rules: [`AGENTS.md`](../../../../../../../../../AGENTS.md) (parity workflow, error contract, commands)
- Codegen class: [`BetterPHPCodegen.java`](BetterPHPCodegen.java)
- Templates: `src/main/resources/templates/php/` (`api/`, `auth/`, `auth/oauth/`, `errors/`, `models/`, `serializer/`, `tests/`)
- Spec classes: `src/spec/java/io/github/mridang/codegen/spec/php/` (`PhpSpec`, `PhpClientSpec`, `PhpFormattingSpec`, `PhpStaticAnalysisSpec`, `PhpModernizationSpec`, `PhpReservedWordsSpec`)
- Golden: `src/spec/resources/generated/php/` (never hand-edit; regenerate)

## Layout

| Path | What lives there | Edit this to change |
|---|---|---|
| `BetterPHPCodegen.java` | type mapping, reserved words, defaults, enum case names, supporting-file registration, formatter commands, mustache lambdas (`phpdocDescription`, `phpSingleQuoted`) | naming, type mapping, which files are emitted, the formatter chain |
| `templates/php/errors/*.mustache`, `api_error.mustache`, `root_exception.mustache` | `ApiException` and its status leaves, `OAuth2ServerException`, `SerializationException`, `NetworkException` | error classes, message rendering, response-header rendering; `errors/SchemaException.mustache` renders one typed error per declared error schema (`<Model>Exception`, class hierarchy, `getErrorBody()` typed by the model, static `fromErrorResponse`); each operation passes its own status-to-`fromErrorResponse(...)` map as `errorSchemas:` and `BaseApi` hands it to `ApiException::fromResponse`; `SerializationException::withResponse` attaches the response status, headers, raw body, method and path to a response decode failure |
| `templates/php/auth/`, `auth/oauth/` | authenticators, `NoAuth`, OAuth2 token manager, OIDC | auth, scopes, token handling |
| `templates/php/default_api_client.mustache`, `psr18_api_client.mustache`, `abstract_api_client.mustache` | the two transports and the shared redirect, decompression, charset and header-injection loop | transport behaviour, TLS, proxy, redirects, multipart parts |
| `templates/php/transport_options*.mustache`, `configuration*.mustache`, `server_*.mustache`, `servers.mustache` | `TransportOptions` and builder, `Configuration` and `ConfigurationBuilder`, server configuration | options and their validation |
| `templates/php/base_api.mustache`, `api/api.mustache`, `api/options.mustache`, `api/server_*.mustache` | per-tag API classes, per-operation `Options`, request building and response typing | operation signatures, parameter handling, response decoding |
| `templates/php/object_serializer.mustache`, `value_serializer.mustache`, `serializer/*.mustache`, `models/*.mustache` | body and parameter (de)serialisation, Ds normalizers, models and enums | wire format, models |
| `templates/php/readme.mustache`, `skills.mustache` | README (Requirements, Caveats, Not supported) and the SDK skill file | prose, README sections |
| `templates/php/{composer,dev_dependencies,php_cs_fixer,phpcs_xml,phpstan_neon,rector,makefile,editorconfig,gitignore}.mustache` | manifest and tooling configuration | dependencies, lint and format rules |
| `templates/php/tests/` | Pest tests, `bootstrap.php`, `phpunit.xml`, stubs; golden-only and client-emitted sets (`emitUnitTests()`) | generated tests |
| `src/spec/.../spec/php/` | the container specs: Pest run, formatting, static analysis, modernisation, reserved words | spec commands and image |

## Idioms (deliberate differences from the other eleven)

| Topic | What php does | Why |
|---|---|---|
| Collections | `\Ds\Vector`, `\Ds\Set`, `\Ds\Map` (`ext-ds` optional, `php-ds` userland fallback) | PHP arrays cannot carry a `uniqueItems` set or typed generics; PHPDoc generics satisfy PHPStan 9 |
| One class per file, own files for `ServerVariable`, `ConfigurationBuilder`, `NoAuth` | PSR-4 autoloading, checked by `composer dump-autoload --strict-psr` | PHP has no nested or co-located public classes (see the forced divergences in the shared file) |
| Composer package name `vendor/package` | default `openapi/<namespace lower-case>`; the package segment is spec-derived, the vendor is structural | Composer requires `vendor/package` |
| Typed defaulted properties | a property with a default and a non-nullable schema is non-nullable (`int $retries = 3`); no default stays `?type = null` | spec-faithful; see D-php-defaults below |
| OAuth2 error code | `OAuth2ServerException::$errorCode` | `\Exception` already declares `protected int $code` |
| Error cause | the `$previous` constructor argument, `getPrevious()` | `\Throwable` chaining; `SerializationError.cause` is allowed missing |
| Transport injection | `Psr18ApiClient` plus a trailing client argument on `Client` factories | PSR-18 is the PHP standard HTTP client interface (S9) |
| Concurrency | synchronous only; one `getAuthHeaders`, no lock around OIDC rediscovery | single-threaded request model |
| Reserved words | trailing underscore (`Foo_`) and only for names PHP cannot use (`$this`, class and constant names) | a leading underscore trips the `Squiz.NamingConventions.ValidVariableName` sniff; PHP 8 allows keywords as variables, properties and named arguments |
| Enums | native backed `enum` with `UPPER_SNAKE_CASE` cases, `V_` prefix for digit-leading names | PHP enum case rules |
| Dates and times | `date` and `date-time` as `\DateTime`, `time` as `\DateTimeImmutable`, `duration` as `\DateInterval` or `PreciseDuration` | no time-of-day type; `PreciseDuration` carries sub-second protobuf durations (no duration type in PHP for it) |
| Multipart files | `\SplFileObject` or stream resource, real filename and sniffed MIME | PHP file handles; aligning to the nine would break the published `Options` API |
| Config and tests | Pest on PHPUnit, `phpunit.xml`, `tests/bootstrap.php` builds the chasm and squid containers | testcontainers-php specifics, see "Build-breaking rules" |

## Settled decisions (do not re-open in review)

Newest first. Ids are the numbered decisions of the shared file (D), the owner decisions written out there (F) and the 2026-11-16 session (S).

### Batch 5 (2026-11-16)
Rule: `Client::close()` (and `__destruct`) closes only the `DefaultApiClient` the SDK built itself, which then rejects later requests with the closed-client `\LogicException`; a supplied transport is never closed and keeps working (S9). HTTP 429 maps to `TooManyRequestsException` (a `ClientException`, same constructor shape). The redirect loop follows RFC 9110 15.4 (301/302: only POST becomes GET; 303: all but HEAD become GET; 307/308 keep method), and the HTTPS to HTTP body-replay refusal applies whenever the body would be replayed. Binary bodies and multipart parts also accept a PSR-7 `StreamInterface`. `content:` cookies are `rawurlencode` of the serializer JSON (already the D52 set). The 'Unexpected response Content-Type' error does not exist in php.
Open: the declared parameter type of binary bodies and upload fields is still `\SplFileObject` (a union with string, resource and `StreamInterface` needs an API-shape decision); the runtime already accepts them.
Where: `client.mustache`, `abstract_api_client.mustache`, `base_api.mustache`, `default_api_client.mustache`, `psr18_api_client.mustache`, `errors/TooManyRequestsException.mustache`, `api_error.mustache`.
Test: `ClientTest`, `ApiExceptionTest`, `BaseApiTest`, `Psr18ApiClientUnitTest`, `DefaultApiClientUnitTest`, `ObjectSerializerTest`.

### Container responses are checked element by element (S1, 2026-11-16, pending in the golden)
Rule: a response typed as a container of primitives (`map<string,int32>`, `array<array<int32>>`) is checked per element exactly like a single primitive.
Why: php was one of seven SDKs where the container bypassed the strict primitive checks; the owner decided all twelve agree.
Where: `templates/php/object_serializer.mustache`, `base_api.mustache`.
Test: the container rows of `ObjectSerializerTest` (wrong type, null, quoted number, fractional and out-of-range int elements); already satisfied, wave 2 only added the out-of-range row.

### `content:` parameters go through `ObjectSerializer::serialize` (S2, D50, D80, 2026-11-16)
Rule: query, header, path and cookie parameters declared with `content:` are encoded by the SDK's own object serializer, never by `json_encode`, giving the same bytes as the other eleven.
Why: twelve different JSON encoders were in use; one serializer is the only way to get identical bytes.
Where: `templates/php/api/api.mustache`, `object_serializer.mustache`.
Test: `ValueSerializerTest`, `BaseApiTest`, and the petstore content-parameter operations.

### Schema constraints are not enforced client-side (S3, D55, 2026-11-16)
Rule: php does not enforce `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems`, `maxItems`; the server owns validation.
Why: only python's pydantic enforces them natively and cheaply; php would need a validator dependency for no correctness gain.
Where: `templates/php/readme.mustache` (the single "Schema constraints" section; the older "Numeric / string constraint validation" caveat was removed in batch 6).
Test: README golden.

### allOf with a discriminator uses class inheritance (S4, 2026-11-16)
Rule: php models the `allOf` plus discriminator parent as a parent class and subtypes extend it; a model reused in two unions is not re-parented (unions use interfaces and wrappers, not inheritance).
Why: PHP has single inheritance but class inheritance, so the natural shape works; re-parenting would break the second union.
Where: `models/model.mustache`, `BetterPHPCodegen` discriminator defaults (subtype constructor defaults the discriminator and demotes it behind required parameters).
Status (#104): a classic base (`x-allof-base`) gets `DISCRIMINATOR_MAPPING` and a public static `subtypeClass(array)` that names the subtype or throws on a missing, non-string or unknown value; `ObjectSerializer::deserializeInternal` calls it before the union branch and decodes to the subtype it returns (a subtype returns itself, so decoding stops). Subtypes keep their own constructors and readonly fields, which write the discriminator default, so an encode writes `vehicleType`/`shapeType`.
Test: `ComposedSchemaTest` (bhv unions 301-304, bhv serializationobject 301).

### Required plus nullable (S5, D66, 2026-11-16)
Rule: the key must be present on decode (missing is an error), an explicit `null` is kept, and encode always writes the key (`null` when unset).
Why: required-ness is about the key, nullability about the value; `assertRequiredPresent` already treats key presence as satisfied by null, the normalizer must not skip null for these keys.
Where: `object_serializer.mustache` (`assertRequiredPresent`), `models/model.mustache`.
Test: `ObjectSerializerTest` required-nullable rows.

### uniqueItems order is unspecified; do not sort (S6, supersedes D69, 2026-11-16)
Rule: a `\Ds\Set` is sent in the set's natural iteration order; php never sorts. The README states the order is unspecified.
Why: the owner chose unspecified order over sorted bytes; D69's sort step is withdrawn.
Where: `object_serializer.mustache` (`Ds` set normalizer).
Test: `ObjectSerializerTest` (set round trip, no order assertion).

### Cancellation (S7, 2026-11-16)
Rule: php has no per-call cancellation argument and no cancellation error; the call is bounded by `TransportOptions` timeout and fails with `NetworkTimeoutException`. The README says so.
Why: PHP requests are synchronous and single-threaded with no standard cancellation handle; a timeout is the only honest bound.
Where: `readme.mustache` ("Per-call cancellation").
Test: README golden, `TransportOptionsTest`.

### Parameter order (S8, 2026-11-16, owner confirmation pending)
Rule: required parameters, then server, then the per-operation `Options` object; adding security never moves an argument.
Why: callers' existing call sites must not break when an operation gains security.
Where: `api/api.mustache`, `api/options.mustache`.
Test: `BaseApiTest`, per-tag API tests.

### Injected transport is PSR-18 (S9, 2026-11-16)
Rule: the `Client` factories accept a trailing `ApiClient` (normally `Psr18ApiClient` wrapping any PSR-18 client); the SDK defaults stay applied, it never closes a caller-supplied client, and conflicts with SDK-owned TLS, proxy or pool settings fail fast. `TransportOptions` TLS, proxy and timeout are not applied to an injected client (documented in the README).
Why: Guzzle, Symfony's adapter and Laravel's client are all PSR-18. PSR-18 carries no transport config and has no redirect hook, so the SDK runs its own redirect loop and the caller must disable redirects.
Where: `psr18_api_client.mustache`, `client.mustache`, `readme.mustache`.
Test: `Psr18ApiClientUnitTest`, `ClientTest`.

### Dependencies (S10, 2026-11-16)
Rule: a small pure library is acceptable when it standardises behaviour; never one needing a native binary. `ext-brotli` and `ext-zstd` stay optional (`suggest`), `ext-ds` optional; mandatory `ext-uri` and `ext-zlib` are listed in the README Requirements.
Why: a native binary breaks installs on bare PHP images; the manifest and README Requirements must agree (R-readme-ext).
Where: `composer.mustache`, `readme.mustache`.
Test: `PhpClientSpec` (installs the optional extensions so their tests run, not skip).

### Out of scope (S11, closed #53, #70, #78, #88, #90, 2026-11-16)
Rule: callbacks and webhooks, XML bodies and `nodeType`, Link Objects and mutual TLS are not generated; the README "Not supported" section says so.
Why: closed backlog issues; client-to-server only.
Where: `readme.mustache`.
Test: README golden.

### Test runner speed and caches (S13, 2026-11-16)
Rule: the php spec runs `vendor/bin/pest --parallel --log-junit .out/reports/junit.xml` (no `--testdox`), keeps `COMPOSER_HOME` under `/root/.cache/php/composer`, and installs `pcov brotli zstd` with a five-attempt retry; spec parallelism is 3; never touch `.github` workflows from this repo.
Why: the CI test job is CPU-bound; `--testdox --parallel` leaks serialised PHPUnit blobs (Pest 4.x bug, no fixed version); pecl downloads are flaky.
Where: `PhpClientSpec`, `PhpSpec`.
Test: the php spec itself.

### Scope order, UUID case, blank keys, error cause (S14, 2026-11-16)
Rule: OAuth scopes keep the spec's declared order; uuid is written lowercase and validated; an api key or bearer token is blank only when empty or ASCII whitespace; every status-specific error accepts an optional cause (`$previous`).
Why: deterministic wire and identical blank-credential behaviour in all twelve.
Where: `auth/*.mustache`, `errors/*.mustache`, `BetterPHPCodegen.formatPhpScopes`.
Test: `ApiKeyAuthenticatorTest`, `BearerAuthenticatorTest`, `ApiExceptionTest`, `OAuth2*Test`.

### Secrets are redacted in string forms (S15, 2026-11-16)
Rule: string forms of `Configuration` and `TransportOptions` redact the proxy password and default headers named `Authorization`, `Proxy-Authorization`, `Cookie` or containing key, token or secret as `***`. Sensitive constructor parameters (`apiKey`, `token`, `password`, `clientSecret`) are also kept out of stack traces (`SENSITIVE_PARAMS`).
Why: logs and stack traces must not leak credentials.
Where: `transport_options.mustache`, `configuration.mustache`, `BetterPHPCodegen` (`SENSITIVE_PARAMS`, `#[\SensitiveParameter]`).
Test: `TransportOptionsTest`, `ConfigurationTest`.

### Timeouts, redirects and proxy validation (S16, 2026-11-16)
Rule: one end-to-end budget across redirect hops and the body read; a caller-supplied stream or file handle is never closed by the SDK; proxy URL validation happens in `build()`, not in the builder setter; `caCertPath` is validated even when `verifySsl` is false.
Why: a per-hop timeout lets a redirect chain exceed the budget; setter validation makes builders order-dependent.
Where: `abstract_api_client.mustache` (`hrtime` deadline, `NetworkTimeoutException`), `default_api_client.mustache`, `transport_options*.mustache`.
Test: `DefaultApiClientTest`, `TransportOptionsTest`.

### A malformed request URL is a caller mistake (bhv errors 040)
Rule: `AbstractApiClient::sendRequest` refuses any URL that is not an absolute http or https URL with a usable host (a bracketed IPv6 literal must parse) before a header is merged or the transport is touched, and throws `\InvalidArgumentException("Malformed request URL: <url>")`; the URL in the message has no query and any userinfo shown as `***@`. Both transports (`DefaultApiClient` and `Psr18ApiClient`) get it from the shared method.
Why: `parse_url()` accepts `http://[bad`, and the Symfony transport then reported it as a `NetworkException`; the cross-language contract makes a caller mistake the language's argument error, never an SDK error. Dart and elixir refuse it up front in the same way.
Where: `abstract_api_client.mustache` (`isUsableWebUrl`, `hasUsableHost`, `loggableUrl`, shared with `resolveUrl`).
Test: `DefaultApiClientUnitTest` (`bhv errors 040`, rows for `http://[bad` and a scheme-less `not a url`).

### Timeout must be a positive millisecond count or null (D77)
Rule: the `TransportOptions` constructor (which `build()` runs) throws `\InvalidArgumentException('Invalid timeout: must be a positive number of milliseconds (got N)')` for `<= 0` (templates done in batch 6, golden regeneration pending).
Why: zero or negative is ambiguous across transports.
Where: `transport_options_builder.mustache`, `transport_options.mustache`.
Test: `TransportOptionsTest` (negative and zero rows pin the message).

### Ambient proxy settings are ignored (D74)
Rule: with no `TransportOptions::proxy`, php passes `no_proxy = '*'` to Symfony so `HTTP(S)_PROXY` env vars are ignored (pending in the golden).
Why: one rule in all twelve: no proxy unless configured; verify with the dead-proxy test.
Where: `default_api_client.mustache`.
Test: `DefaultApiClientTest` dead-proxy case.

### CA validation and message grammar (D73, D79)
Rule: `file_get_contents` and `openssl_x509_read` of `caCertPath` run before the `verifySsl` branch; `cafile` is set only when verifying. Proxy and CA error messages use the shared grammar with credentials redacted.
Why: a bad CA path must fail whether or not verification is on.
Where: `default_api_client.mustache`, `transport_options.mustache`.
Test: `TransportOptionsTest`, `DefaultApiClientTest`.

### Redirect, header and error rendering (D13, D16, D17, D21, D29)
Rule: the three redirect refusals pass the real response headers and use the shared wording (`Refusing to follow redirect to non-HTTP(S) URL: <url>`, `... redirect: <from> -> <to>`, `Too many redirects (exceeded N)`); `ApiException` headers default to `null` (null means no response arrived); the `Response headers:` line is compact JSON with sorted keys and unescaped slashes and unicode (D17 pending in the golden); User-Agent and X-Request-ID checks are case-insensitive via `array_change_key_case`; use-after-close throws `\LogicException` with the canonical message.
Why: one rendering and one injection rule for all twelve; a null versus empty header map is a signal (see R-headers).
Where: `abstract_api_client.mustache`, `api_error.mustache`, `default_api_client.mustache`, `psr18_api_client.mustache`.
Test: `ApiExceptionTest`, `DefaultApiClientTest`, `Psr18ApiClientUnitTest`.

### Serializer messages and BOM (D15, D18)
Rule: serialize and deserialize failures use the shared messages (no `to <class>` suffix); a pre-scan enforces the depth cap with the canonical text rather than relying on `JsonException`; a leading BOM is stripped in `ObjectSerializer::decodeJson`, which the error-body path also uses.
Why: error text and BOM handling must match the success path in all twelve.
Where: `object_serializer.mustache`, `api_error.mustache`.
Test: `ObjectSerializerTest`, `ApiExceptionTest`.

### Credential emptiness (D19)
Rule: `trim($x, " \t\n\v\f\r")` (not default `trim`, which strips NUL and not form feed), after stripping a `Bearer ` prefix in the constructor.
Why: one explicit ASCII-whitespace set.
Where: `auth/api_key_authenticator.mustache`, `auth/bearer_authenticator.mustache`.
Test: `ApiKeyAuthenticatorTest`, `BearerAuthenticatorTest`.

### OAuth2 session, refresh and scopes (D01, D02, D03, D04, D05, D06, D07, D08, D11)
Rule: the exchanged flag lives in `OAuth2TokenManager`; `OpenIdConnectAuthenticator` rebuilds its delegate around the previous manager on rediscovery (second constructor argument `?OAuth2TokenManager`), with no lock; `getAccessToken` takes an optional `?string $refreshUrl`; `exchangeCode` always POSTs the authorization_code grant and restores state in a catch and rethrow; scopes come from the generator in spec order and OIDC always requests `openid`; an empty implicit access token and an empty `state` count as absent; token and discovery responses are decoded with `JSON_THROW_ON_ERROR` and must be objects with non-blank endpoint strings; `expires_in` and `max-age` match `/^[0-9]+(\.[0-9]+)?$/` and are clamped before the integer cast.
Why: a cache refresh must not discard a session, and parser detail must not reach error messages. No lock is forced: PHP serves one request per process.
Where: `templates/php/auth/oauth/*.mustache`, `BetterPHPCodegen.renderSchemeAuthenticator`.
Test: `OAuth2TokenManagerTest`, `OAuth2AuthCodeAuthenticatorTest`, `OAuth2PasswordAuthenticatorTest`, `OAuth2ImplicitAuthenticatorTest`, `OpenIdConnectAuthenticatorTest`.

### Authorize URL and form space encoding (R-space-encoding)
Rule: `buildAuthorizationUrl` uses `http_build_query(..., PHP_QUERY_RFC3986)` (`%20`); the form-urlencoded request body keeps `+`.
Why: RFC 3986 for URLs, form encoding for bodies; never revert the authorize URL to a default encoder.
Where: `auth/oauth/oauth2_auth_code_authenticator.mustache`, `value_serializer.mustache`.
Test: `OAuth2AuthCodeAuthenticatorTest`, `ValueSerializerTest`.

### Binary and non-JSON response handling (D30, D31, D32)
Rule: a `format: binary` response yields raw bytes through a distinct `binary` return token (the codegen rewrites `\SplFileObject` response types to `string`); a text or absent content type returns the body unchanged, anything else is strictly base64-decoded and a failure throws `SerializationException`, never routed to `ObjectSerializer::deserialize`. A non-JSON success type on a non-binary return gives the body only for `string` with a text media type; otherwise `SerializationException`. `format: byte` responses use one strict decoder.
Why: one rule for JSON, absent, text and binary content types.
Where: `base_api.mustache`, `api/api.mustache`, `BetterPHPCodegen` binary-response rewrite.
Test: `BaseApiTest`.

### Request bodies and multipart (D35, D36, D39, D89, R-multipart-filename)
Rule: a binary request body is sent verbatim for any content type (the `\SplFileObject` or resource drain runs whenever the raw body is set); multipart scalar parts are plain text parts, `DateTimeInterface`, `BackedEnum` and uuid objects count as scalars and only models are `application/json` parts; no part carries `Content-Transfer-Encoding` (a `DataPart` subclass overrides the prepared headers); x-www-form-urlencoded rejects nested objects; file parts send a real filename and sniffed MIME.
Why: identical multipart bytes; changing file handles to `application/octet-stream` would break php's published `Options` API.
Where: `base_api.mustache`, `default_api_client.mustache`, `psr18_api_client.mustache`.
Test: `BaseApiTest`, `DefaultApiClientTest`, `MultipartModelPart` fixture.

### Required string parameters and error naming (D28, R-empty-required)
Rule: a required string parameter (path, query, header, form, cookie) must be non-empty, else `\InvalidArgumentException("Missing the required parameter '<name>' when calling <Class>.<operation>")`, naming the caller-facing `{{paramName}}`; the exception is a query parameter with `allowEmptyValue: true`. A null required Options object reports the same message.
Why: an empty string is not a present value; one message in all twelve.
Where: `api/api.mustache`.
Test: per-tag API tests (`tests/Api`), `BaseApiTest`.

### Multi-consumes selector rejects unknown values (R-content-type-selector)
Rule: an unrecognised request content type throws `\InvalidArgumentException`; null defaults to the first declared type.
Why: a typo must never ride the wire as an undeclared Content-Type.
Where: `api/api.mustache`, `base_api.mustache`.
Test: `BaseApiTest`.

### Compressed responses (R-content-encoding, R-empty-body)
Rule: `br` and `zstd` raise when the optional extension is absent (never return still-encoded bytes); an unrecognised or chained `Content-Encoding` raises and is wrapped into `ApiException` with the response status; `identity`, absent, and an empty body (`$body === ''`) pass through; returned response headers drop `Content-Encoding` and `Content-Length` once the body is decoded (D92, same decision).
Why: returning encoded bytes is silent corruption; an empty 204 with a stale header has nothing to decode.
Where: `default_api_client.mustache`, `abstract_api_client.mustache`.
Test: `DefaultApiClientTest`, `DefaultApiClientUnitTest` (brotli and zstd tests run in the container where the extensions are installed).

### Parameter encoding (D47, D48, D49, D50, D51, D81, D82)
Rule: object-valued query and header parameters go through `ObjectSerializer` into a wire-keyed map before expansion (`toWireMap`, widen `serializeDeepObject`, pending in the golden); a path value of exactly `.` or `..` throws; `allowReserved` leaves only the RFC 3986 query-legal reserved set literal (PSR-7 already encodes `# [ ]`); `format: byte` parameters are `base64_encode`d; `format: time` is `H:i:s` plus `.v` only when non-zero (pending in the golden); a `\Ds\Set` or `\Ds\Collection` parameter expands like an array via `toArray()`.
Why: one wire rule per type in all twelve.
Where: `value_serializer.mustache`, `object_serializer.mustache`, `api/api.mustache`.
Test: `ValueSerializerTest`, `ObjectSerializerTest`.

### Model decode and encode rules (D54, D57, D58, D60, D62, D63, D65, D66)
Rule: `int32` range is checked after denormalise against a per-model `INT32_PROPERTIES` list and on normalise (pending in the golden; `int64` needs nothing, overflow is already rejected); `date-time` accepts only RFC 3339 with an offset through `ObjectSerializer::parseRfc3339`, never the host clock or zone; `format: byte` fields are validated in the post-denormalise hook with a strict base64 regex and `base64_decode`; a defaulted `$ref` enum emits `Availability::ON_HOLD` as the constructor and property default; `format: uri` rejects relative references; an `additionalProperties` key that collides with a declared property loses to the declared value; a null element of an array or map of models throws unless the element schema is nullable.
Why: identical accepted and rejected inputs in all twelve.
Where: `object_serializer.mustache`, `models/model.mustache`, `BetterPHPCodegen` enum default fix.
Test: `ObjectSerializerTest`.

### Codegen escaping and names (D40, D41, D42, D43, D44, D45, D46, D22)
Rule: string defaults and enum literals are single-quoted with only `\` and `'` escaped (the single-quoted PHP literal interprets nothing else), so `shouldEscapeQuotationMark` is false and `escapesEnumControlCharacters` is false; doc prose breaks `*/`; `composer.json` descriptions use a JSON-string lambda; per-operation server enums are rendered once per `serverEnums` entry; digit-leading enum names get the `V_` prefix; reserved-word escaping applies only to names PHP cannot use (`shouldEscapeReservedVarName` is true only for `this`); model names that collide with PHP or SDK types get the `Model` prefix from a dedicated reserved-model-name set; the default `User-Agent` product token is the composer package segment (not `invokerPackage`).
Why: one escaper per target literal and one reserved-word rule in all twelve; leading underscores trip phpcs.
Where: `BetterPHPCodegen.java` (`escapeReservedWord`, `shouldEscapeReservedVarName`, `toPhpEnumCaseName`, `toDefaultValue`, `userAgentDefault`), `composer.mustache`, `api/api.mustache`.
Test: `PhpReservedWordsSpec`, `PhpFormattingSpec`, golden diff.

### List rendering in messages (D20)
Rule: allowed values for server variables and declared content types render as `[a, b, c]` (`'[' . implode(', ', $x) . ']'`); done in the templates in batch 10, the golden waits for regeneration.
Why: one list syntax in all twelve.
Where: `server_configuration.mustache`, `api/api.mustache`.
Test: `ServerConfigurationTest`, per-tag API tests.

### Value semantics and string forms (D23, D24, D27)
Rule: every model has a field-dumping string form and union wrappers get the same equality; `ApiResult::headers` is a defensive copy. Equality (issue #154) is `equals(?self $other): bool` on every model and union wrapper, comparing all properties through `ObjectSerializer::valuesEqual`: nested models by their own `equals`, lists and maps by content, sets as unordered members, date-times by instant, enums by case; a different class or null is never equal and nothing throws.
Why: identical diagnostic and equality behaviour; php had no equality before #154, so `==` on the readonly objects was the only comparison.
Where: `models/model.mustache`, `model_equals.mustache`, `object_serializer.mustache` (`valuesEqual`).
Test: `ApiResultTest`, `ComposedSchemaTest`.

### Typed defaults (R-defaults, forced)
Rule: php types a defaulted, schema-non-nullable property as non-nullable (`Defaults::$retries int = 3`, `Order::$status OrderStatusEnum = PLACED`) and rejects `null`; a property with no default stays `?type = null`. Do not align php to the looser majority.
Why: spec-faithful (it honours the nullable versus optional distinction).
Where: `models/model.mustache`, `BetterPHPCodegen.toDefaultValue`.
Test: `ObjectSerializerTest`, petstore `Defaults` model golden.

### Own files and names forced by PSR-4 and `\Exception` (R-forced-files)
Rule: `ConfigurationBuilder`, `ServerVariable`, `NoAuth`, `Psr18ApiClient`, `RawHttpResponse`, `PreciseDuration` and the Ds and URI normalizers are separate public classes; the OAuth2 error code is `errorCode`, with `statusCode` held in `OAuth2ServerException`'s own property (its `getCode()` is 0, while `ApiException` passes `statusCode` to the parent as `$code`); the composer package name carries the `openapi/` vendor.
Why: PSR-4 one class per file, `\Exception::$code` is `int`, Composer needs `vendor/package`.
Where: `BetterPHPCodegen.registerAuthSupportingFiles`, `errors/OAuth2ServerException.mustache`, `composer.mustache`.
Test: `PhpStaticAnalysisSpec` (`composer dump-autoload --optimize --strict-psr`).

### `format: number` is a PHP float (R-number)
Rule: bare `number` and `decimal` map to `float`; the README "Decimal / `format: number` precision" caveat states the loss; Symfony also routes `int64` through `float` above 2^53 (documented, same caveat).
Why: stdlib JSON numbers are floats; switching to strings or BCMath changes the unquoted-number wire form and fails strict mock servers (verified with Chasm). Do not align to a decimal type.
Where: `BetterPHPCodegen` type mapping, `readme.mustache`.
Test: README golden, `ObjectSerializerTest`.

### Security comment only on `security: []` (R-no-auth-comment)
Rule: the "explicitly unauthenticated, pass the no-auth sentinel" comment appears only on `vendorExtensions.op.securityNone` operations; inherited-auth operations say they inherit the global requirement and pass `null`.
Why: the comment used to contradict the code on every other operation.
Where: `api/api.mustache`.
Test: golden.

### Maps of `format: byte` (R-byte-map)
Rule: `format: byte` map values round-trip as base64 strings on the wire through an accessor pair, never a plain map copy.
Why: raw map copy left the surface base64-undecoded.
Where: `models/model.mustache`, `object_serializer.mustache`.
Test: `ObjectSerializerTest`.

### oneOf and anyOf no-match string (R-oneof-string)
Rule: the central `ObjectSerializer` throws `No oneOf/anyOf variant matched the JSON`; non-discriminated variants are tried in declaration order.
Why: shared central-helper wording; the per-model wordings elsewhere are not harmonised.
Where: `object_serializer.mustache`, `models/model.mustache`.
Test: `ComposedSchemaTest`.

### Spec gates are thinner than the client Makefile in one place (R-known-thin)
Rule: the client `make format` is `phpcbf || true` (no Rector) and `make lint` is phpcs only, while the specs also gate `php-cs-fixer check` and a Rector dry run. Accepted as-is; do not strengthen the Makefile into a cascade of golden fixes.
Why: WONTFIX, see the known-thin gates in the shared file.
Where: `makefile.mustache`, `PhpFormattingSpec`, `PhpModernizationSpec`.
Test: the two specs.

### Mock seam (mocking001 to 009, issue #122 and the Invoker line of #131)
Rule: every API group has an interface and the client has one where the language can state it, additive next to the existing classes, using only SDK and standard-library types. `<Group>ApiInterface` and `ClientInterface` (one file each, PSR-4), with `get<Group>()` accessors beside the `readonly` properties because interface properties need PHP 8.4. `ClientInterface` is not the PSR-18 `Psr\Http\Client\ClientInterface`: `Client.php` imports the PSR one, so it names ours by its full name. The tests build fakes with `fakeGroup()` in `tests/bootstrap.php`, which generates a class from the interface with reflection and `eval`. Tests: tests/ClientTest.mustache (001, 002, 007, 008), tests/api/PetApiTest.mustache (003 to 006), tests/bootstrap.php (the fake builder). Where: the templates named above, `BetterPhpCodegen`, the README template (`Testing your code`), `docs/languages/php.md`.

## Decisions taken in batch 6

### Float text is the shortest round-trip form
Rule: `ObjectSerializer::stringify` writes a finite float with `json_encode(..., JSON_PRESERVE_ZERO_FRACTION)` (exponent marker upper-cased), so query, path, header, form and multipart text carry the same digits as a JSON body whatever the `precision` ini says. Non-finite values keep the `(string)` form.
Where: `object_serializer.mustache`. Test: `ObjectSerializerTest` (precision ini set to 14).

### A bad `caCertPath` fails with `verifySsl` off too (D73)
Rule: the CA file is read and parsed first; `cafile`/`capath` are set only when verifying.
Where: `default_api_client.mustache`. Test: `DefaultApiClientUnitTest`.

### An unreadable body or part throws
Rule: a binary body file or stream, and a PSR-18 or Symfony multipart file or stream part, that cannot be read throws `\InvalidArgumentException` naming the path ("Failed to read request body file", "Failed to read multipart file", "Failed to read ... stream"); it never sends an empty body.
Where: `base_api.mustache`, `psr18_api_client.mustache`, `default_api_client.mustache`. Test: `BaseApiTest`, `Psr18ApiClientUnitTest`.

### One OAuth2 expiry margin and expiry reset
Rule: the token is stored expiring 30 s early (never more than its lifetime) and read with no second margin, so a 60 s token is reused for 30 s; a response without `expires_in` clears the previous expiry; invalid JSON reports `Token response is not valid JSON`, a list or scalar `Token response is not a JSON object`.
Where: `auth/oauth/oauth2_token_manager.mustache`. Test: `OAuth2TokenManagerTest`.

### Redirect refusals, no-Location tail, maxRedirects, Accept-Encoding
Rule: the three refusals use the shared wording and carry the real lower-cased response headers; a final 3xx without a usable `Location` after the budget is returned, not refused; `maxRedirects < 0` throws `Invalid maxRedirects: must not be negative (got N)`; a caller's `accept-encoding` in any casing suppresses the SDK's own, and the body-less Content-Type removal is case-insensitive.
Where: `abstract_api_client.mustache`, `transport_options.mustache`. Test: `DefaultApiClientUnitTest`, `TransportOptionsTest`.

### SKILLS model sample
Rule: the sample builds the first model with named arguments for its required scalar and collection properties.
Where: `skills.mustache`. Test: golden.

### Pinned messages
Rule: the timeout message, the use-after-close message and `Authorization code must not be empty` are asserted verbatim.
Test: `TransportOptionsTest`, `DefaultApiClientUnitTest`, `OAuth2AuthCodeAuthenticatorTest`.

## Decisions taken in batch 7

### One OAuth2 expiry margin, one-year ceiling, strict grammar (D08)
Rule: a token is stored expiring `min(60 s, expires_in / 2)` early and read with no second margin, so a 60 s token is reused for 30 s and a 30 s token for 15 s; tests that need an already-stale token send `expires_in` 0. `expires_in` strings match `^[0-9]+(\.[0-9]+)?$` after trimming ASCII whitespace (hex, exponent, sign, underscore, trailing dot are stale), and the parsed value and the OIDC `max-age` are clamped to 31 536 000 s before any arithmetic (an over-long digit run saturates to the ceiling).
Where: `auth/oauth/oauth2_token_manager.mustache`, `auth/oauth/openid_connect_authenticator.mustache`. Test: `OAuth2TokenManagerTest`, `OpenIdConnectAuthenticatorTest`.

### Ambient proxy variables are ignored (D74)
Rule: with no `TransportOptions::proxy` the Symfony client gets `no_proxy = '*'`, so `http_proxy`, `https_proxy`, `all_proxy` and `no_proxy` in the environment are never used (implemented in the template in batch 7; it was only described before).
Where: `default_api_client.mustache`. Test: `DefaultApiClientTest` (dead proxy in `$_SERVER`).

### A text body for a typed return is a `SerializationException`
Rule: a non-JSON text 2xx body (text/plain, XML) for any return type other than `string` or no return type throws `SerializationException` naming the Content-Type; `string` and untyped operations still get the body unchanged. Binary (non-text) bodies keep the base64 path.
Where: `base_api.mustache`. Test: `BaseApiTest`.

### Canonical serializer messages (D15)
Rule: deserialize failures read `Failed to deserialize JSON: <detail>` (no `to <class>`), and a JSON text nested past 1000 levels fails with `JSON nesting depth <N> exceeds limit 1000` from an SDK pre-scan that only runs when the text holds more than 1000 openers and ignores brackets inside strings.
Where: `object_serializer.mustache`. Test: `ObjectSerializerTest`.

### Proxy errors do not echo credentials (D79)
Rule: the three `Invalid proxy URL` messages render the URL with any userinfo replaced by `***@`.
Where: `transport_options.mustache`. Test: `TransportOptionsTest`.

### Symfony constraint floor
Rule: Symfony components are required as `^6.4|^7.4` (6.0 to 6.3 are end of life). Symfony 8 is not admitted until the templates and the php spec are verified against it; the PSR-18 transport already lets an application avoid the Symfony client.
Where: `composer.mustache`.

### Auth behaviour added in batch 7
Rule: a 401 on a request authenticated by an OAuth2 or OIDC authenticator calls its new public `invalidateAccessToken()` and replays the request once with a fresh token (a refresh grant first); a body that is not a string or null is not replayed and a failed re-authentication leaves the 401. A per-call authenticator from `Options` gets the transport injected before use. The authorization-code URL carries a PKCE S256 `code_challenge` and `exchangeCode` sends the matching `code_verifier`. OIDC discovery rejects an `issuer` that differs from the discovery URL minus `/.well-known/openid-configuration` (a document with no issuer is accepted) and an authorization or token endpoint that is not https unless loopback. A request sent with `noRedirect` (the token POST) omits `TransportOptions::defaultHeaders`. A cross-origin redirect also strips any header whose name contains key, token, secret, auth or credential, and any transport default header except `User-Agent`. No OIDC nonce is sent because the SDK never validates an ID token.
Where: `base_api.mustache`, `auth/oauth/*.mustache`, `abstract_api_client.mustache`. Test: `BaseApiTest`, `OAuth2AuthCodeAuthenticatorTest`, `OpenIdConnectAuthenticatorTest`, `DefaultApiClientUnitTest`.

### Rendering and headers
Rule: `ApiException::__toString()` shows at most 1024 body bytes with `... (N more bytes)`, escapes control characters as `\xNN`, and renders `authorization`, `proxy-authorization`, `cookie`, `set-cookie`, `www-authenticate`, `proxy-authenticate` and any header named like key, token or secret as `***`. A decoded response drops `content-encoding` and `content-length` from the returned headers (D92). Models with a `format: password` property define `__debugInfo()` that masks it.
Where: `api_error.mustache`, `abstract_api_client.mustache`, `models/model.mustache` (`hasPasswordVars` set in `BetterPHPCodegen.postProcessModels`). Test: `ApiExceptionTest`, `DefaultApiClientUnitTest`, `ObjectSerializerTest`.

### Decisions taken in batch 9 (verified)
Rule: `TransportOptions::$maxRetries` (builder `maxRetries()`, default 2, 0 disables, negative rejected) retries a request after a network error that happened before a response and after 408, 409, 429 and 5xx except 501. The wait is 0.5 s doubling to 8 s, cut by up to 25% of jitter, and a `Retry-After` (seconds or HTTP date, at most 60 s) replaces it. The loop is `AbstractApiClient::sendWithRetries()`, which `BaseApi` uses for any client derived from `AbstractApiClient` (a bare `ApiClient` implementation sends once); it takes a header builder that is called on every attempt, so authentication, cookies and trace context are rebuilt. A body that is neither null nor a string (stream, multipart parts) is sent once. The whole call, waits included, stays inside the one `timeout` (an attempt is bounded by the remaining budget, and a wait that would outlast it ends the loop with the last failure). Token requests, redirect hops and the 401 replay go through `sendRequest()` and are not retried. PHP has no per-call cancellation (S7), so there is nothing to cancel during a wait. F855 (public `invalidateAccessToken()`, one replay, no replay of a non-string body), F858 (PKCE S256, no nonce) and F1080 (rendering cap, `\xNN`, masked headers) were already in place from batch 7 and are unchanged. F825 is rust only.
Where: `abstract_api_client.mustache`, `base_api.mustache`, `transport_options.mustache`, `transport_options_builder.mustache`, `readme.mustache`, `skills.mustache`. Test: `DefaultApiClientUnitTest` (count, backoff, `Retry-After`, no retry on 400/404/501, budget, `maxRetries(0)`, network errors, bodies, rebuilt headers), `BaseApiTest` (rebuilt authentication on retry; the status table runs with `maxRetries(0)`), `TransportOptionsTest`. `StubbedDefaultApiClient` overrides the protected `pause()` and `jitter()` seams so no test sleeps.

### Wire names on encode, object parameters, deserialize message (verification fixes)
Rule: the `DsAwareObjectNormalizer` is built with a `MetadataAwareNameConverter` over an `AttributeLoader`, so every model key is read and written under its `SerializedName` in both directions (without it a `min_age` or `2fa` property was emitted as `minAge` or `n2fa` and a `{"2fa":true}` body left it null); `flattenAdditionalProperties` therefore matches properties by wire name. A model passed as a styled query, path or header parameter is laid out by `ValueSerializer::serializeObjectStyled` (form and simple, exploded or not, label, matrix, space and pipe delimited); an exploded form object is a name-keyed map that `ValueSerializer::putQueryParam` merges into the query, every other value is set under the parameter name. Every deserialize failure that is not already a `SerializationException` is wrapped as `Failed to deserialize JSON: <detail>`. A `Location` with an unusable host (`http://[bad`) is not followed.
Where: `object_serializer.mustache`, `value_serializer.mustache`, `api/api.mustache`, `abstract_api_client.mustache`. Test: `PetApiTest`, `ObjectSerializerTest`, `DefaultApiClientUnitTest`.

### Decisions taken in batch 10 (-)
Rule: a model named like a type PHP forbids as a class name (`String`, `Object`, `Int`, `Mixed` and the rest, any case) takes the `Model` prefix from `BetterPHPCodegen.toModelName`; `Error` and `Result` are legal in the models namespace and keep their names. A path or body parameter named `options`, `server` or `requestContentType` gets a `Param` suffix (`getInjectedArgumentNames`). Non-discriminated anyOf variants are tried in declaration order (`composedSchemas.anyOf`, as oneOf already was; the bare `anyOf` set is alphabetical, D64). A token response whose `token_type` is neither missing nor `bearer` (any case) is an `OAuth2TokenException` `Unsupported token_type in token response` that never echoes the value. The server-variable and content-type messages render `[a, b, c]` (D20, the template had still printed a bare `implode`), `ServerConfiguration::getUrl()` substitutes in one `strtr` pass so a placeholder a value introduces is never expanded again, and an unreadable CA file reads `failed to read CA certificate from "<path>": <cause>` (D79). The named examples of an optional parameter reach the options class docblock as `@example`. The Makefile has an `audit` target (`composer audit`). The README follows the java layout (Installation, Usage, Raw responses, Timeouts, Automatic retries, Expired tokens, Request id, Custom HTTP client, Build, Test, Package, Caveats, Not supported with XML, Link Objects and mutual TLS listed) and SKILLS Configuration has `Custom Transport Options`, `Retries`, `Custom HTTP client`, `Cancellation` and `Raw responses, timeouts and the request id`. Enum cases are documented as `->value` (a backed enum cannot be interpolated; forced).
Not done in php: binary request bodies stay `\SplFileObject` (the runtime also takes a PSR-7 stream, but widening the declared type changes every signature and the multipart filename rule; that belongs to the upload decision in #114); a `retryAfter` accessor (a public API addition that must land in all twelve at once); a `maxResponseBytes` option (#112); and a released redirect hop, which needs no test because every hop is read to completion by `getContent(false)` before the loop continues.
Where: `BetterPHPCodegen.java`, `models/model.mustache`, `auth/oauth/oauth2_token_manager.mustache`, `server_configuration.mustache`, `api/api.mustache`, `api/options.mustache`, `default_api_client.mustache`, `makefile.mustache`, `readme.mustache`, `skills.mustache`.
Test: `BetterPHPCodegenTest`, `BearerAuthenticatorTest` (a Unicode space is rejected by the printable-ASCII rule), `TransportOptionsTest` (IPv6 literal proxy), `ServerConfigurationTest`, `DefaultApiClientUnitTest` (non-BMP filename, extension table, identity encoding, CA cause:), `DefaultApiClientTest` (repeated response headers), `StoreApiTest` and `PetApiTest` (typed status routes), `OAuth2TokenManagerTest` (the three D07 messages, `token_type`), `ObjectSerializerTest` (byte-array content), `ComposedSchemaTest` (declaration order). F1124 already holds in php (Separator, o'neil server, nickname ZZZZZZZZZZ, slug 12/ab).

### Decisions taken in wave 2 (-)
Rule: every `ApiException` (and subclass) built by `BaseApi` carries `getRequestMethod()` and `getRequestPath()` (the path of the URL, query and fragment dropped); `__toString()` adds a `Request: GET /items/7` line after the status line; `getMessage()` is unchanged (`API returned status code N`). An options object (`...Options`) gains `timeout` (positive ms), `headers` (win over a configured header of the same name, also over the authenticator's) and `maxRetries` (0 or more), validated at construction with the `TransportOptions` messages; `BaseApi` passes them to `AbstractApiClient::sendWithRetries()`, a transport written straight against `ApiClient` gets the headers and ignores the other two. A parameter named `auth`, `timeout`, `headers` or `maxRetries` takes the `Param` suffix like `options` does. Models are immutable: every property is `public readonly`, there is no default on the property (the constructor keeps the defaults), and each property has `withX()` returning a copy built with named constructor arguments (`withAdditionalProperties()` too; `additionalProperties` is a read-only property holding the `\Ds\Map` the constructor creates, filled by the serializer through `putAll`). A oneOf or anyOf wrapper has `fromX()`, `isX()` and `asX()` for each model, scalar and date variant (the `\Ds` container and file variants have none: they need generics a native type cannot carry); the loose `__construct(mixed)` stays because the serializer builds wrappers with it. An operation with several 2xx schemas passes `successTypes` (`'201' => \Model`, `'2XX' => ...`) to `BaseApi`, which decodes the body as the schema of the status that arrived and wraps it in the result union, falling back to trial decoding only when no status matches. The floor is PHP 8.3: `format: uri` is a `string` (no `UriNormalizer`, no `ext-uri`), redirect `Location` resolution is RFC 3986 section 5.2 in pure PHP, `array_any` is gone. `Bearer`, `Basic` and `ApiKey` authenticators take their credential as an argument or, when it is `null`, from an environment variable (read once at construction, empty is unset, a missing one throws `InvalidArgumentException` when the authenticator is built); Bearer and ApiKey also take a `\Closure` provider called per request; the variable names are the shared per-scheme names of wave 3 below. The generator options `authors` (comma separated, `Name <email>`), `homepageUrl`, `repositoryUrl`, `documentationUrl`, `issueTrackerUrl` and `licenseId` reach `composer.json` (`authors`, `homepage`, `support`, `license`) and the README. `sendRequest()` has a `noDefaultHeaders` argument that the token POST and OpenID Connect discovery set so an `Authorization` or API key default header never reaches the identity provider (`noRedirect` is no longer about headers). A decoded number that overflows to infinity (`1e400`) is a `SerializationException` in a top-level float, a container element and a model field. A required property that is nullable is `?type` with no default, keeps an explicit `null`, a missing key is an error, and its key is always written (`null` when unset) through the `REQUIRED_NULLABLE_PROPERTIES` constant. A binary request body is `\SplFileObject|StreamInterface|string` (raw bytes), a multipart file field stays `\SplFileObject` or a PSR-7 stream. The options docblock keeps the named examples of an optional parameter.
Why: the owner decisions of 2026-11-17 plus the triaged parity findings; the same shape in all twelve, in PHP idiom (named arguments, `readonly`, `withX`).
Where: `api_error.mustache`, `errors/*`, `base_api.mustache`, `abstract_api_client.mustache`, `api_client.mustache`, `api/api.mustache`, `api/options.mustache`, `per_call_arguments.mustache`, `models/model.mustache`, `model_var_type.mustache`, `object_serializer.mustache`, `auth/*`, `composer.mustache`, `readme.mustache`, `skills.mustache`, `phpstan_neon.mustache`, `rector.mustache`, `BetterPHPCodegen.java` (`unionVariants`, `addCopyArguments`, `composerExtras`, `resolveEnvPrefix`, `exampleDocLines`).
Test: `ApiExceptionTest` and `BaseApiTest` (request context), `PetApiTest` (per-call headers, options validation, in-memory binary body), `DefaultApiClientUnitTest` (per-call `maxRetries` and `timeout`, default headers off without `noRedirect`), `StoreApiTest` (multi 2xx by status, union accessors), `ComposedSchemaTest` and `MetadataTest` (variant accessors, immutability, `withX`), `ObjectSerializerTest` (uri string, required nullable, infinity, out of range), `BearerAuthenticatorTest`, `BasicAuthenticatorTest`, `ApiKeyAuthenticatorTest` (environment, provider), `OpenIdConnectAuthenticatorTest` and `OAuth2TokenManagerTest` (no default headers), `Psr18ApiClientUnitTest` and `DefaultApiClientUnitTest` (relative redirects), `BetterPHPCodegenTest`, `PhpFormattingSpec` (plural examples).
Not done in php: binary multipart file fields stay `\SplFileObject` or a PSR-7 stream, because a union cannot take the `?` prefix the options template writes (#114). The new package metadata options have no defaults or CLI registration in `AbstractBetterCodegen`; the templates read the keys when they are present.

### Decisions taken in wave 3 (for every operation,  per-scheme variables)
Rule: `mintsOptionsForEveryOperation()` is `true`, so every operation, also one with no query, header, form or cookie parameter and no security scheme, takes `?<Operation>Options $options = null` as its last argument (the server argument still comes before an optional options object). The options object of such an operation holds only `timeout`, `headers` and `maxRetries` (no `auth` field); `BaseApi` receives them through `per_call_arguments.mustache` exactly as for the other operations. Credentials: the environment variable of a credential is no longer one name per authenticator type (`<PREFIX>_TOKEN`) but one per scheme and field, from the shared `securitySchemeEnvVars` (`{PREFIX}_{FIELD}`, `{PREFIX}_{SCHEME}_{FIELD}` when two schemes share a field name; the prefix is the shared `envVarPrefix` option, else the API title without a trailing `SDK`; the php-only `envPrefix` option and `resolveEnvPrefix` are gone). Each generated basic, bearer or API key authenticator declares `protected const array ENV_VARS = ['token' => 'NAME']` (fields `token`, `username`, `password`, `apiKey`); `BaseAuthenticator::environmentCredential()` and `environmentHint()` read `static::ENV_VARS`, so the base classes `BearerAuthenticator`, `BasicAuthenticator` and `ApiKeyAuthenticator` used directly read no variable and no constructor signature changed. Order: the explicit argument, then the variable (read once when the authenticator is built, an empty value is unset), then the `\Closure` provider where the class has one (a provider is never combined with an environment read); a credential found nowhere throws `InvalidArgumentException` naming the variable(s), before the client exists. The OAuth 2 and OpenID Connect authenticators stay explicit-only (a client secret is not a single-field credential and a missing one is already an `ArgumentCountError` or `TypeError` at construction). F866: a `$ref` with a sibling `default` starts at it: `BetterPHPCodegen.siblingDefaultLiteral` turns `siblingDefault*` into a constant expression (an enum case, or a string, int, float or bool literal) that `addSiblingDefaultLiterals` stores as `vendorExtensions.siblingDefaultLiteral` on `vars`, `allVars`, `requiredVars` and `optionalVars`; `model.mustache` writes it as the constructor default of an optional property (`?Availability $availability = Availability::ON_HOLD`). It is deliberately not the property's own `defaultValue`: that would reorder the constructor behind the other optional properties and drop the `?`.
F857 (owner decision): the `Client` constructor takes `?Authenticator $authenticator = null`, the transport options and the transport, then one optional `?Authenticator` named parameter per distinct supported security scheme, named by the camelCase scheme id (`BetterPHPCodegen.processOpenAPI`, `schemeCredentials`). The credentials reach `BaseApi` as `array<string, Authenticator>` keyed by scheme id; each operation passes its requirement as a literal (`securityRequirements: [['apiKeyHeader'], ['petStoreBearer']]`, from `fromOperation`, `phpSecurityRequirements`) and `BaseApi::schemeAuthenticatorFor()` takes the first alternative whose schemes are all configured, joining several with `CompositeAuthenticator`; none configured sends no credential. With no scheme credential the first authenticator still serves a secured operation (the `withToken()` path). There is no per-call authenticator: `injectAuthFieldContext` gives no options object an `auth` field. F1078: `ClientLifecycle` is shared by the client and every API group; `Client::close()` closes it, so a later call fails with `\LogicException('client closed')` (the same message `AbstractApiClient` raises), and only a transport the SDK built is released. F840: an operation cookie value passes raw when every character is a cookie-octet, otherwise `InvalidArgumentException` before sending (already so; tests added). F316: a model constructor validates nothing beyond PHP's native types, and an unknown enum string fails at decode (already so; tests added). F1085 is done as a shared change: the run generates the test certificates and mounts them at `tests/fixtures/certs`; none is committed.
Why: the shared generator now offers the three behaviours to all twelve languages; the same shape in every SDK, in PHP idiom (named arguments, a class constant, `?T` with a default).
Where: `BetterPHPCodegen.java` (`mintsOptionsForEveryOperation`, `schemeCredentialEnvVars`, `recordCredentialScheme`, `siblingDefaultLiteral`, `addSiblingDefaultLiterals`), `api/api.mustache`, `per_call_arguments.mustache`, `auth/base_authenticator.mustache`, `auth/bearer_authenticator.mustache`, `auth/basic_authenticator.mustache`, `auth/api_key_authenticator.mustache`, `auth/scheme_authenticator.mustache`, `models/model.mustache`, `readme.mustache`, `skills.mustache`, `client.mustache`, `base_api.mustache`, `client_lifecycle.mustache`, `auth/composite_authenticator.mustache`.
Test: `BetterPHPCodegenTest` (every-operation flag, which fields read the environment, the sibling default literal for an enum, a scalar and the shapes with none), `StoreApiTest` (per-call headers and validation on `getInventory`, no `auth` field), `BearerAuthenticatorTest`, `BasicAuthenticatorTest` and `ApiKeyAuthenticatorTest` (precedence, empty and missing, read once, a provider wins, the base class reads nothing; they build the first generated scheme class of each kind and its generated variable name), `ObjectSerializerTest` (construction, decode without the key, decode with the key, explicit `null`).

## Known limitations and out of scope

- Model fields of `format: byte` hold the base64 text (`?string`), with raw bytes through `getXAsBytes()` and `setXFromBytes()`; the other eleven hold native bytes. Decode validation is decided (D58) but the representation difference is deferred, so the root "native bytes in memory in all 12" sentence is false for php.
- Deferred: schema-constraint enforcement (S3), SOCKS proxies (README), the missing `LICENSE` file, per-call cancellation (S7), webhooks, callbacks, XML, Link Objects and mutual TLS (S11, closed #53, #70, #78, #88, #90).
- Loading OpenAPI 3.2 documents and the 3.1 and 3.2 feature backlog are tracked as epics #49 (3.2) and #96 (3.1); loading 3.2 is #50 (the pinned swagger-parser rejects it).
- Pending in the golden although decided: S1, D17, D20, D47, D54, D74, D81. D73, D77 and the batch 6 items below are done in the templates and wait for golden regeneration.
- Not run by a spec: the warnings-fatal phpDocumentor docs build (`make docs`, deferred).
- Typed `int64` values above 2^53 lose precision through Symfony's `float` step (README caveat; not fixable without changing the wire layer).

## Build-breaking rules

- No `//` line comments in generated php (`PhpFormattingSpec`, `^\s*//`); use `/* */` or `/** */`. `rector.php` alone is exempt.
- No HTML entities in generated code (`&amp;` and the like); write `&` and use `{{{triple}}}` or the `phpdocDescription` lambda.
- No generated file disables a tool or sniff (`phpcs:ignoreFile` is banned); name a sniff in `phpcs.xml` with a reason (currently only `PSR1.Files.SideEffects` and `Generic.Files.LineLength`).
- Every class must autoload under strict PSR-4, one class per file (`composer dump-autoload --optimize --strict-psr`).
- PHPStan level 9 must pass over sources and tests; PHPDoc generics are required on every `\Ds\*` type (`missingType.generics`).
- Formatting runs Rector, then php-cs-fixer, then phpcbf (exit 1 tolerated), and the output must pass `phpcs`, `php-cs-fixer check` and a Rector dry run unchanged.
- No leading-underscore names (escape with a trailing underscore instead); a leading underscore trips `Squiz.NamingConventions.ValidVariableName`.
- Do not use Pest `--testdox` with `--parallel` (leaks serialised result blobs).
- testcontainers-php sets `PortBindings` but not `ExposedPorts`, so on a strict daemon (CI) published ports get no binding; `tests/bootstrap.php` builds the chasm (4010, 8443) and squid (3128, 3129) fixtures through a container that sets `ExposedPorts` for every requested port. Any new published fixture port must be exposed there and verified on CI.
- The Dev-dependency list for client-emitted tests must match what `emitUnitTests()` emits (the shared guardrail), including for php.

## Verify

- `etc/ci-local.sh check Php` (spotless, spotbugs, unit tests and the php integration specs)
- `etc/ci-local.sh mvn -B verify -Dgroups=php` for the php specs alone
- Regenerate the goldens with `etc/ci-local.sh mvn -B test -Dtest='Generate*ClientTest,FixtureVocabularyLeakTest' -DforkCount=0 -Djacoco.skip=true -DfailIfNoTests=false`
- A container or exit-137 failure is infrastructure: re-run once before concluding anything

## Gate lessons (what broke the gate and how to avoid it)

- Generated code is verified only by the Docker specs (PhpClientSpec, PhpStaticAnalysisSpec, PhpFormattingSpec, PhpModernizationSpec). Run them with `etc/ci-local.sh check Php` before every push, not only a quick check; CI takes about 25 minutes. Rerun only that language when Docker flakes ("port already in use", mock-server startup), after `docker ps` and `lsof -i :PORT`.
- Pest `toThrow(fn ...)` resolves the closure parameter with `class_exists()`: an interface such as `\Throwable` is read as message text and the closure never runs. Type it `\Exception` and narrow with `instanceof ApiException` for PHPStan (the neon ignore pattern expects `Closure(Exception): void`). Command: `etc/ci-local.sh check Php`.
- PHPStan level 9 covers tests too: no `assertTrue(true)` (use `addToAssertionCount(1)`), guard nullable properties with `instanceof`, cast `json_encode()` (`string|false`) and `mixed` body fields, no anonymous wrapper classes in `Client`, and `\Ds\Map` keys are `mixed` so use `->toArray()` first.
- Never enable php-cs-fixer `fully_qualified_strict_types` with `import_symbols`: it shortens `\Ds\Vector` to `Vector` without a `use`. Use short imported names in signatures (a 134-character FQN signature broke the 120-column limit). Command: php-cs-fixer, then PhpStaticAnalysisSpec.
- `composer dump-autoload --optimize --strict-psr` needs one class per file and namespaces that follow directories (after `exceptions/` became `errors/` the class vanished). A shared test double such as `MockTokenApiClient` is its own supporting file. Command: PhpClientSpec, not only static analysis.
- int64 stays `int`: turning it into string put `{"id":"1"}` on the wire and Prism rejected every call. Any numeric type change needs a round-trip test against the mock server first.
- Symfony PropertyInfo turns `\Ds\Vector<Model>` into `Model[]`, so constructors receive plain arrays; test the real `PetApi::findPetsByStatus` path, not only `ObjectSerializer::deserialize`. Rewrite generic doc types for operation `@return` as well as model properties. The serializer name converter must be in line with the generated property names (escape a leading underscore with a trailing one).
- Symfony timeouts: set both `timeout` and `max_duration` and map both to the SDK timeout error. `MockHttpClient` keeps headers in `$options['normalized_headers']`, not `headers`. Command: a mock test that throws exactly the total-budget exception.
- Rector and php-cs-fixer run as write steps in post-process, and Rector can delete live branches (a redirect loop became an unconditional `break`) and wants `#[\Override]` on `__debugInfo()`. Keep dead-code rules off and keep a redirect test. Command: `composer rector -- --dry-run`.
- Do not stop emitting a type models still reference (the enum refactor dropped `*ConnectErrorCodeEnum.php`, 58 phpstan errors); keep an enum-typed property in the fixture. `#[\Deprecated]` cannot target properties, the file docblock must be `/**`, and an optional parameter before a required one is deprecated: order required, server, options.
- No `//` comments or HTML entities in generated PHP. Command: `grep -rnE '^\s*//([^/]|$)|&[a-z]+;' src/main/resources/templates/php`.
- Pest: `--testdox` with `--parallel` leaks serialised blobs, never filter with `--testsuite unit` in CI (the specs never ran), and a repo with both `test/` and `spec/` cannot bootstrap Pest. Check library signatures in `vendor/` first (testcontainers-php `withExposedPorts(array)`, Pest 3+ for phpunit 10.5).
- Toolchain: `php -v` must equal the composer `require.php` (php 8.3 crashed php-cs-fixer on 8.5 vendor code), Alpine `composer:2` uses `apk`, and a transient `pecl install` outage shows as PhpModernizationSpec ERROR, not FAILURE: reproduce with `docker run php:8.5-cli pecl install pcov` before debugging code.
- Date parameters need the `|date` marker at every `serializeStyled` call site (query, header, cookie, form); keep `format: date` fixture parameters for each. After any encoder change, `grep -rn "+profile\|scope=.*+" src/main/resources/templates/php`.
