# PHP SDK generator

This folder holds `BetterPHPCodegen` (generator name `php-plus`), which turns an OpenAPI document into a Composer package whose namespace is `invokerPackage` and whose sources live under `lib/` (PSR-4, one class per file). The generated SDK sends requests through the Symfony HTTP client (`DefaultApiClient`) or, when the caller injects one, any PSR-18 client (`Psr18ApiClient`), serialises with Symfony Serializer and `php-ds` collections (`\Ds\Vector`, `\Ds\Set`, `\Ds\Map`), and tests with Pest 4 (parallel) over PHPUnit 12. The floor is PHP 8.5 (the manifest requires `ext-curl`, `ext-json`, `ext-mbstring`, `ext-uri`, `ext-zlib`, and `\Uri\Rfc3986\Uri` is the `format: uri` type). The shipped tooling is php-cs-fixer (`@PSR12`), PHP_CodeSniffer (PSR-12), PHPStan level 9 with peststan over sources and tests, and Rector (`UP_TO_PHP_84`, code quality, dead code, early return, type declarations). Cross-language rules are written once in the shared file; this file states only how php realises them. Decisions marked "pending in the golden" are settled but not yet implemented in the checked-in golden.

## Read first

- Shared cross-language rules: [`../AGENTS.md`](../AGENTS.md)
- Repository root rules: [`AGENTS.md`](../../../../../../../../../AGENTS.md) (parity workflow, error contract, commands)
- Codegen class: [`BetterPHPCodegen.java`](BetterPHPCodegen.java)
- Templates: `src/main/resources/templates/php/` (`api/`, `auth/`, `auth/oauth/`, `errors/`, `models/`, `serializer/`, `tests/`)
- Spec classes: `src/spec/java/io/github/mridang/codegen/spec/php/` (`PhpSpec`, `PhpClientSpec`, `PhpFormattingSpec`, `PhpStaticAnalysisSpec`, `PhpModernizationSpec`, `PhpReservedWordsSpec`)
- Golden: `src/spec/resources/generated/php/` (never hand-edit; regenerate)
- Ledgers: `PARITY_DECISIONS.md`, `PARITY_FINDINGS.json`, `etc/api-surface-allow.json` (php rules)

## Layout

| Path | What lives there | Edit this to change |
|---|---|---|
| `BetterPHPCodegen.java` | type mapping, reserved words, defaults, enum case names, supporting-file registration, formatter commands, mustache lambdas (`phpdocDescription`, `phpSingleQuoted`) | naming, type mapping, which files are emitted, the formatter chain |
| `templates/php/errors/*.mustache`, `api_error.mustache`, `root_exception.mustache` | `ApiException` and its status leaves, `OAuth2ServerException`, `SerializationException`, `NetworkException` | error classes, message rendering, response-header rendering |
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
| One class per file, own files for `ServerVariable`, `ConfigurationBuilder`, `NoAuth` | PSR-4 autoloading, checked by `composer dump-autoload --strict-psr` | PHP has no nested or co-located public classes (`etc/api-surface-allow.json`, "Forced divergences" in the root file) |
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

Newest first. Ids are from `PARITY_DECISIONS.md` (D), the root `AGENTS.md` (R-), and the 2026-11-16 session (S).

### Container responses are checked element by element (S1, F006, 2026-11-16, pending in the golden)
Rule: a response typed as a container of primitives (`map<string,int32>`, `array<array<int32>>`) is checked per element exactly like a single primitive.
Why: php was one of seven SDKs where the container bypassed the strict primitive checks; the owner decided all twelve agree.
Where: `templates/php/object_serializer.mustache`, `base_api.mustache`.
Test: the petstore `getInventory` and `getMatrix` rows in `ObjectSerializerTest` / `BaseApiTest` (to be added with the fix).

### `content:` parameters go through `ObjectSerializer::serialize` (S2, D50, D80, F054, 2026-11-16)
Rule: query, header, path and cookie parameters declared with `content:` are encoded by the SDK's own object serializer, never by `json_encode`, giving the same bytes as the other eleven.
Why: twelve different JSON encoders were in use; one serializer is the only way to get identical bytes.
Where: `templates/php/api/api.mustache`, `object_serializer.mustache`.
Test: `ValueSerializerTest`, `BaseApiTest`, and the petstore content-parameter operations.

### Schema constraints are not enforced client-side (S3, D55, F056, 2026-11-16)
Rule: php does not enforce `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`, `multipleOf`, `minItems`, `maxItems`; the server owns validation.
Why: only python's pydantic enforces them natively and cheaply; php would need a validator dependency for no correctness gain.
Where: `templates/php/readme.mustache` ("Numeric / string constraint validation"; the shared section is named "Schema constraints", rename pending).
Test: README golden.

### allOf with a discriminator uses class inheritance (S4, F057, 2026-11-16)
Rule: php models the `allOf` plus discriminator parent as a parent class and subtypes extend it; a model reused in two unions is not re-parented (unions use interfaces and wrappers, not inheritance).
Why: PHP has single inheritance but class inheritance, so the natural shape works; re-parenting would break the second union.
Where: `models/model.mustache`, `BetterPHPCodegen` discriminator defaults (subtype constructor defaults the discriminator and demotes it behind required parameters).
Test: `ComposedSchemaTest`, `ObjectSerializerTest`.

### Required plus nullable (S5, D66, F215, 2026-11-16)
Rule: the key must be present on decode (missing is an error), an explicit `null` is kept, and encode always writes the key (`null` when unset).
Why: required-ness is about the key, nullability about the value; `assertRequiredPresent` already treats key presence as satisfied by null, the normalizer must not skip null for these keys.
Where: `object_serializer.mustache` (`assertRequiredPresent`), `models/model.mustache`.
Test: `ObjectSerializerTest` required-nullable rows.

### uniqueItems order is unspecified; do not sort (S6, supersedes D69, F319, 2026-11-16)
Rule: a `\Ds\Set` is sent in the set's natural iteration order; php never sorts. The README states the order is unspecified.
Why: the owner chose unspecified order over sorted bytes; D69's sort step is withdrawn.
Where: `object_serializer.mustache` (`Ds` set normalizer).
Test: `ObjectSerializerTest` (set round trip, no order assertion).

### Cancellation (S7, F228, 2026-11-16)
Rule: php has no per-call cancellation argument and no cancellation error; the call is bounded by `TransportOptions` timeout and fails with `NetworkTimeoutException`. The README says so.
Why: PHP requests are synchronous and single-threaded with no standard cancellation handle; a timeout is the only honest bound.
Where: `readme.mustache` ("Per-call cancellation").
Test: README golden, `TransportOptionsTest`.

### Parameter order (S8, F803, 2026-11-16, owner confirmation pending)
Rule: required parameters, then server, then the per-operation `Options` object; adding security never moves an argument.
Why: callers' existing call sites must not break when an operation gains security.
Where: `api/api.mustache`, `api/options.mustache`.
Test: `BaseApiTest`, per-tag API tests, `etc/api-surface.py check`.

### Injected transport is PSR-18 (S9, F405, 2026-11-16)
Rule: the `Client` factories accept a trailing `ApiClient` (normally `Psr18ApiClient` wrapping any PSR-18 client); the SDK defaults stay applied, it never closes a caller-supplied client, and conflicts with SDK-owned TLS, proxy or pool settings fail fast. `TransportOptions` TLS, proxy and timeout are not applied to an injected client (documented in the README).
Why: Guzzle, Symfony's adapter and Laravel's client are all PSR-18; the extra argument is allowed in `etc/api-surface-allow.json`. PSR-18 carries no transport config and has no redirect hook, so the SDK runs its own redirect loop and the caller must disable redirects.
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

### Timeout must be a positive millisecond count or null (D77, F121)
Rule: `TransportOptionsBuilder::timeout()` and the `TransportOptions` constructor throw `\InvalidArgumentException` for `<= 0` (pending in the golden).
Why: zero or negative is ambiguous across transports.
Where: `transport_options_builder.mustache`, `transport_options.mustache`.
Test: `TransportOptionsTest` (to be added with the fix).

### Ambient proxy settings are ignored (D74, F090)
Rule: with no `TransportOptions::proxy`, php passes `no_proxy = '*'` to Symfony so `HTTP(S)_PROXY` env vars are ignored (pending in the golden).
Why: one rule in all twelve: no proxy unless configured; verify with the dead-proxy test.
Where: `default_api_client.mustache`.
Test: `DefaultApiClientTest` dead-proxy case.

### CA validation and message grammar (D73, D79, F295, F297)
Rule: `file_get_contents` and `openssl_x509_read` of `caCertPath` run before the `verifySsl` branch; `cafile` is set only when verifying. Proxy and CA error messages use the shared grammar with credentials redacted.
Why: a bad CA path must fail whether or not verification is on.
Where: `default_api_client.mustache`, `transport_options.mustache`.
Test: `TransportOptionsTest`, `DefaultApiClientTest`.

### Redirect, header and error rendering (D13, D16, D17, D21, D29)
Rule: the three redirect refusals pass the real response headers and use the shared wording (`Refusing to follow redirect to non-HTTP(S) URL: <url>`, `... redirect: <from> -> <to>`, `Too many redirects (exceeded maxRedirects=N)`); `ApiException` headers default to `null` (null means no response arrived); the `Response headers:` line is compact JSON with sorted keys and unescaped slashes and unicode (D17 pending in the golden); User-Agent and X-Request-ID checks are case-insensitive via `array_change_key_case`; use-after-close throws `\LogicException` with the canonical message.
Why: one rendering and one injection rule for all twelve; a null versus empty header map is a signal (see R-headers).
Where: `abstract_api_client.mustache`, `api_error.mustache`, `default_api_client.mustache`, `psr18_api_client.mustache`.
Test: `ApiExceptionTest`, `DefaultApiClientTest`, `Psr18ApiClientUnitTest`.

### Serializer messages and BOM (D15, D18, F245, F268)
Rule: serialize and deserialize failures use the shared messages (no `to <class>` suffix); a pre-scan enforces the depth cap with the canonical text rather than relying on `JsonException`; a leading BOM is stripped in `ObjectSerializer::decodeJson`, which the error-body path also uses.
Why: error text and BOM handling must match the success path in all twelve.
Where: `object_serializer.mustache`, `api_error.mustache`.
Test: `ObjectSerializerTest`, `ApiExceptionTest`.

### Credential emptiness (D19, F274, F275)
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

### Binary and non-JSON response handling (D30, D31, D32, F033, F085, F149, F150)
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

### List rendering in messages (D20, F298, F340)
Rule: allowed values for server variables and declared content types render as `[a, b, c]` (`'[' . implode(', ', $x) . ']'`); pending in the golden (it still prints a bare `implode`).
Why: one list syntax in all twelve.
Where: `server_configuration.mustache`, `api/api.mustache`.
Test: `ServerConfigurationTest`, per-tag API tests.

### Value semantics and string forms (D23, D24, D27)
Rule: every model has a field-dumping string form and union wrappers get the same equality; `ApiResult::headers` is a defensive copy.
Why: identical diagnostic and equality behaviour; php was already canonical.
Where: `models/model.mustache`, `api_result.mustache`.
Test: `ApiResultTest`, `ComposedSchemaTest`.

### Typed defaults (R-defaults, forced)
Rule: php types a defaulted, schema-non-nullable property as non-nullable (`Defaults::$retries int = 3`, `Order::$status OrderStatusEnum = PLACED`) and rejects `null`; a property with no default stays `?type = null`. Do not align php to the looser majority.
Why: spec-faithful (it honours the nullable versus optional distinction); allowed in `etc/api-surface-allow.json` (`prop-optional`).
Where: `models/model.mustache`, `BetterPHPCodegen.toDefaultValue`.
Test: `ObjectSerializerTest`, petstore `Defaults` model golden.

### Own files and names forced by PSR-4 and `\Exception` (R-forced-files)
Rule: `ConfigurationBuilder`, `ServerVariable`, `NoAuth`, `Psr18ApiClient`, `RawHttpResponse`, `PreciseDuration` and the Ds and URI normalizers are separate public classes; the OAuth2 error code is `errorCode`, with `statusCode` held in `OAuth2ServerException`'s own property (its `getCode()` is 0, while `ApiException` passes `statusCode` to the parent as `$code`); the composer package name carries the `openapi/` vendor.
Why: PSR-4 one class per file, `\Exception::$code` is `int`, Composer needs `vendor/package`; these are allowed in `etc/api-surface-allow.json`.
Where: `BetterPHPCodegen.registerAuthSupportingFiles`, `errors/OAuth2ServerException.mustache`, `composer.mustache`.
Test: `PhpStaticAnalysisSpec` (`composer dump-autoload --optimize --strict-psr`), `etc/api-surface.py check`.

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
Why: shared central-helper wording (F396); the per-model wordings elsewhere are not harmonised.
Where: `object_serializer.mustache`, `models/model.mustache`.
Test: `ComposedSchemaTest`.

### Spec gates are thinner than the client Makefile in one place (R-known-thin)
Rule: the client `make format` is `phpcbf || true` (no Rector) and `make lint` is phpcs only, while the specs also gate `php-cs-fixer check` and a Rector dry run. Accepted as-is; do not strengthen the Makefile into a cascade of golden fixes.
Why: ledger F382, WONTFIX.
Where: `makefile.mustache`, `PhpFormattingSpec`, `PhpModernizationSpec`.
Test: the two specs.

## Known limitations and out of scope

- Model fields of `format: byte` hold the base64 text (`?string`), with raw bytes through `getXAsBytes()` and `setXFromBytes()`; the other eleven hold native bytes. Decode validation is decided (D58) but the representation difference is deferred (F136), so the root "native bytes in memory in all 12" sentence is false for php.
- Deferred: schema-constraint enforcement (S3), SOCKS proxies (README), the missing `LICENSE` file, per-call cancellation (S7), webhooks, callbacks, XML, Link Objects and mutual TLS (S11, closed #53, #70, #78, #88, #90).
- Loading OpenAPI 3.2 documents and the 3.1 and 3.2 feature backlog are tracked as epics #49 (3.2) and #96 (3.1); loading 3.2 is #50 (the pinned swagger-parser rejects it).
- Pending in the golden although decided: S1, D17, D20, D47, D54, D74, D77, D81.
- Not run by a spec: the warnings-fatal phpDocumentor docs build (`make docs`, F191, deferred).
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
- Regenerate the goldens with `etc/ci-local.sh mvn -B test -Dtest='Generate*ClientTest,FixtureVocabularyLeakTest' -DforkCount=0 -Djacoco.skip=true -DfailIfNoTests=false`, then `python3 etc/api-surface.py check`
- A container or exit-137 failure is infrastructure: re-run once before concluding anything
