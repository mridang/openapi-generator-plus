# Swagger Petstore - OpenAPI 3.0 SDK

Auto-generated Java SDK client for the Swagger Petstore - OpenAPI 3.0 API.

## Requirements

- **Java 21** (LTS) or newer — minimum required JDK to build and run
- **Maven 3.9+** — build tool

### Tooling

The generated project comes pre-wired with the following Maven plugins:

| Tool | Plugin | Invoke |
| --- | --- | --- |
| Formatter | `spotless-maven-plugin` (google-java-format) | `mvn spotless:apply` (write) / `mvn spotless:check` (verify) |
| Linter | `maven-checkstyle-plugin` (bundled `google_checks.xml` plus `checkstyle-suppressions.xml`) | `mvn checkstyle:check` |
| Static analyser | `spotbugs-maven-plugin` | `mvn spotbugs:check` |
| Static analyser (compile-time) | Error Prone + NullAway (via `maven-compiler-plugin`) | `mvn compile` |
| Auto-upgrader | `rewrite-maven-plugin` (`org.openrewrite.java.migrate.UpgradeToJava25` recipe) | `mvn rewrite:run` |

## Installation

Add the dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.example.petstore</groupId>
    <artifactId>petstore-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Usage

```java
import com.example.petstore.Client;
import com.example.petstore.models.Pet;

try (Client client = Client.withToken("https://api.example.com", "your-token")) {
    var result = client.pet.addPet(new Pet());
}
```

The client is `AutoCloseable`: close it (or use try-with-resources) to release
its connections. `close()` is graceful and idempotent: it marks the client closed and
returns, requests already in flight finish, the transport the SDK created is released
when the last of them completes, and a second `close()` does nothing. A request issued
after that throws `IllegalStateException` ("ApiClient has been closed and can no longer
send requests"). A `java.net.http.HttpClient` you supplied is never closed. Errors derive
from `ApiException`; `NetworkException` and `NetworkTimeoutException` mean no
HTTP response arrived, and `TooManyRequestsException` is HTTP 429. See
[SKILLS.md](SKILLS.md) for authentication, servers and error handling.

### Raw responses

Every operation has a `...WithHttpInfo` variant returning an `ApiResult<T>`
with `statusCode()`, `data()`, `rawBody()` and the lower-cased `headers()`.
It is the way to read a response header.

### Timeouts

`TransportOptions.builder().timeout(5000)` sets one end-to-end budget in
milliseconds (a positive integer) for the whole call, redirects and the one
stale-connection retry included. A call that exceeds it fails with
`NetworkTimeoutException`; there is no default timeout.

### Automatic retries

`TransportOptions.builder().maxRetries(n)` sets how many times a failed call is
retried; the default is 2 and `0` turns retries off. A call is retried after a
network error that happened before any response arrived, and after HTTP 408,
409, 429 and 500 to 599 (501 excepted). The wait starts at 0.5 seconds, doubles
up to 8 seconds and is shortened by up to 25% of jitter; a `Retry-After` header
(seconds or an HTTP date, at most 60 seconds) replaces it. Retries share the one
end-to-end `timeout` and never extend it: when the remaining time cannot cover
the wait, the last response or error is returned. A thread interrupt during the
wait raises `CancellationException`. A request whose body is a stream is never
retried, and every retry is a fresh request with the same headers.

### Expired tokens

When an OAuth2 or OpenID Connect call is answered with 401, the cached access
token is dropped (the refresh token is kept) and the request is sent once more
with fresh headers. A second 401 is raised as `UnauthorizedException`. A stream or
multipart body is not replayed, and API key, basic and bearer authenticators never
replay.

### Request id

The SDK adds a unique `X-Request-ID` header to every request unless you set
your own (header names are matched case-insensitively). Quote it when you
report a problem with a call.

### Cancellation

Interrupt the calling thread (`Future.cancel(true)`, an executor shutdown, or
`Thread.interrupt()`). The call throws `java.util.concurrent.CancellationException`,
with the `InterruptedException` as its cause and the thread's interrupt flag
restored. It is never reported as `NetworkTimeoutException`. To bound a call by
time instead, set the timeout above.

### Custom HTTP client

`TransportOptions.builder().httpClient(client)` makes the SDK send every
request through your own `java.net.http.HttpClient`. The SDK keeps its request
timeout, user agent and redirect handling on top and never closes the
client; you own its lifecycle. Because the client carries its own proxy and TLS
settings, combining it with `proxy`, `caCertPath` or `verifySsl(false)` fails
at `build()`, as does a client whose redirect policy is not `Redirect.NEVER`.

### Per-call options

Every operation takes an optional trailing options object named after it
(`FindItemsOptions` and the like), even an operation that has no query, header,
form or cookie parameter and no security. Besides the operation's own optional
parameters, it accepts three per-call settings that apply to that one call only:
`timeout(millis)` replaces the transport's end-to-end timeout, `maxRetries(n)`
replaces its retry count (`0` turns retries off), and `headers(map)` adds
request headers after the default headers; authentication headers are applied
last and still win. Leave the options object out, or pass `null`, to use the
client's settings. An options object carries no authenticator: the credential comes
from the client, as described next.

### Credentials per security scheme

`Client.builder(host)` takes one optional credential per security scheme,
named after the scheme, so a client can hold several at once:

```java
var client = Client.builder("https://api.example.com")
    .petStoreBasic(...)
    .adminBasic(...)
    .petStoreBearer(...)
    .serviceToken(...)
    .apiKeyHeader(...)
    .internalApiKey(...)
    .apiKeyQuery(...)
    .sessionCookie(...)
    .machineAuth(...)
    .legacyAuth(...)
    .userAuth(...)
    .browserAuth(...)
    .ssoAuth(...)
    .transportOptions(transportOptions)
    .build();
```

Each operation uses the credential of the first scheme in its own security list that
the client holds. When it holds none of them, the operation falls back to the single
authenticator of `new Client(authenticator)` if there is one, and is sent
without a credential otherwise. An operation that declares `security: []` never sends one.
There is no per-call override of the scheme.

### Cookie parameters

A cookie parameter is sent exactly as you give it when every character is a legal RFC 6265
cookie-octet (a `%` is one, so a value you percent-encoded yourself goes out unchanged). A
value with a space, double quote, comma, semicolon, backslash, control character or
non-ASCII character fails with `IllegalArgumentException` before any request is sent;
percent-encode it first. The SDK never encodes a cookie value for you.

### Credentials from the environment

The authenticator generated for each security scheme reads a credential you leave
out from an environment variable, once, when the authenticator is built. A value
you pass always wins; a `null` or empty value (and an empty variable) counts as
not set. A credential found in neither place fails the constructor with an
`IllegalArgumentException` that names the variable. A token that rotates comes
from a callback instead: `new BearerAuthenticator(host, () -> currentToken())`
runs it before every request (passing both a token and a callback is refused).
The variables are:

| Scheme | Credential | Variable |
| --- | --- | --- |
| `adminBasic` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME` |
| `adminBasic` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD` |
| `apiKeyHeader` | `apiKey` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY` |
| `apiKeyQuery` | `apiKey` | `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY` |
| `browserAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID` |
| `internalApiKey` | `apiKey` | `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY` |
| `legacyAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID` |
| `legacyAuth` | `clientSecret` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET` |
| `legacyAuth` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME` |
| `legacyAuth` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD` |
| `machineAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID` |
| `machineAuth` | `clientSecret` | `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET` |
| `petStoreBasic` | `username` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME` |
| `petStoreBasic` | `password` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD` |
| `petStoreBearer` | `token` | `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN` |
| `serviceToken` | `token` | `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN` |
| `sessionCookie` | `apiKey` | `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY` |
| `ssoAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID` |
| `ssoAuth` | `clientSecret` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET` |
| `ssoAuth` | `redirectUri` | `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI` |
| `userAuth` | `clientId` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID` |
| `userAuth` | `clientSecret` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET` |
| `userAuth` | `redirectUri` | `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI` |

The prefix `SWAGGER_PETSTORE_OPEN_API_3_0` comes from the `envVarPrefix` generator option, or
the API title when the option is not set. The general `BearerAuthenticator`,
`BasicAuthenticator` and `ApiKeyAuthenticator` classes never read the environment.

### Errors

A failed call throws a subclass of `ApiException`. Besides `getStatusCode()`,
`getResponseHeaders()` and `getResponseBody()`, it carries `getRequestMethod()`
and `getRequestPath()` (the path without its query string), and `toString()`
renders them on a `Request: GET /items/1` line, so a log line names the call
without the URL's query string, which may hold a credential.

### Models are immutable

A model's fields are `final`. Build one with `Model.builder()` (or its
constructor when it has required properties) and copy it with one property
changed through `model.toBuilder().property(value).build()`. A required property
that is missing fails when the model is built, whether from code or from JSON.
Collections are copied into unmodifiable ones. A model that accepts extra keys
exposes them read-only through `getAdditionalProperties()` and writes them with
the builder's `putAdditionalProperty`, `putAllAdditionalProperties`,
`removeAdditionalProperty` and `additionalProperties(map)`.

### Unions

A `oneOf` or `anyOf` model has a typed factory per variant (`Union.ofVariant(v)`)
and, for a variant that is a plain class, `isVariant()` and `asVariant()`
(an `Optional`). An operation with several `2xx` responses decodes the body as
the schema of the status that arrived and returns the union.

## Build

```bash
mvn compile
```

## Test

```bash
mvn test
```

## Package

- Group: `com.example.petstore`
- Artifact: `petstore-client`
- Version: `1.0.0`

## GraalVM native image

This SDK ships GraalVM reflection metadata
(`META-INF/native-image/.../reflect-config.json`) registering every generated
model and inline enum, so it works inside a GraalVM `native-image` build without
manual configuration — Jackson can (de)serialize the models reflectively in the
native binary. Drop the dependency into a native-image project and build as
usual; no agent run or hand-written `reflect-config` is required for the model
types. (TLS for the HTTP client still needs `--enable-url-protocols=https`, and
the optional brotli/zstd decoders, loaded reflectively, are unavailable in a
native image unless you register them yourself.)

## Caveats

### Optional and nullable are one `null`

A property that is optional and one that is nullable both read as `null`: the
model cannot tell a missing key from an explicit `null`. The one exception is a
property that is both required and nullable, which must be present when decoding
and is always written, `null` included. A required nullable query parameter is kept the same way: when its value is `null` the key is still sent, with an empty value (`?key=`).

### Unknown enum values and discriminators fail loudly

An enum value the spec does not list, and a discriminator value that maps to no
variant, are errors (`SerializationException`), never a silent fallback. Regenerate
the client when the API adds a value.

### Response headers are one string each

`ApiResult.headers()` and `ApiException.getResponseHeaders()` hold one string per
header name, lower-cased. A header sent on several lines is joined with `, ` in
the order received; `Set-Cookie` cannot be read back apart from that join.

### Response compression

Requests advertise `Accept-Encoding: gzip, deflate`, plus `br` and `zstd` when the
optional `org.brotli:dec` and `zstd-jni` dependencies are on the class path. A
compressed body is decoded for you and the returned headers drop `content-encoding`
and `content-length`. Set your own `Accept-Encoding` header to change it.

### Logging

The SDK writes no log output and has no logging hook. To log requests, wrap the
`ApiClient` interface (it has one `sendRequest` method) around the default
transport, or give `TransportOptions.builder().httpClient(...)` a `HttpClient` of
your own.

### Streaming is not supported

Request and response bodies are read whole into memory. There is no streaming
download or server-sent events API; a large or streamed body needs your own
`java.net.http.HttpClient` outside the SDK.

### Configuration and `TransportOptions` stay separate

`Configuration` holds what belongs to the API (the base URL and default headers);
`TransportOptions` holds how bytes travel (proxy, TLS, timeout, retries,
redirects). They are two types on purpose and the `Client` client
builds the first from the authenticator and takes the second as an argument.

### Versioning

`1.0.0` is the version the generator was given (`artifactVersion`).
It is not derived from the spec, so regenerating from a changed spec without
raising the version produces different code under the same number.

### Decimal / `format: number` precision

`format: number` / `format: decimal` fields are typed as
`java.math.BigDecimal` and serialized through a precision-preserving
serializer that round-trips the JSON number as a string, so exact
decimal values survive deserialization unchanged — unlike languages
that decode JSON numbers into a binary `double`. You can safely do
arithmetic on prices, balances, and other money-typed fields using
`BigDecimal`'s exact operators.

The IEEE-754 binary types are a different story: `format: float` and
`format: double` map to Java's `Float` / `Double`, which **cannot**
represent every decimal fraction exactly (`0.1 + 0.2` is
`0.30000000000000004`). Do not use a `double`/`float`-typed field for
money. If a monetary value is typed as `format: double` in the spec,
read it from the raw response body and parse it into a `BigDecimal`
yourself.

`format: int64` is unaffected — Java's `long` natively represents the
full 64-bit range without precision loss.

### Schema constraints

The constraints `pattern`, `minLength`, `maxLength`, `minimum`, `maximum`,
`multipleOf`, `minItems` and `maxItems` are validated by the server. The
generated models do not check them: Java has no validation in its model layer
(the models are plain Jackson-bound classes), so a value outside a constraint
is sent as given and rejected by the server. The only client-side checks are
the type checks the models make while decoding and the presence of required
properties. If you want fast-fail validation before the network round trip,
plug in a Bean Validation or JSON Schema library.

`uniqueItems` arrays are held in a `java.util.Set` (a `LinkedHashSet`). They
are written in the set's own order, which is insertion order; the SDK does not
sort them, because the order carries no meaning.

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
certificate on your own `java.net.http.HttpClient` and pass it with
`TransportOptions.builder().httpClient(client)`.

### Conditional-required validation (`dependentRequired` / `dependentSchemas`)

JSON Schema 2019-09 keywords for "if field X is present, field Y is
also required" are **not enforced** by this SDK. No mainstream
OpenAPI client codegen implements them. The server is the authoritative
validator; if you want client-side checking, plug in a JSON Schema
validator library for your language.

### SOCKS proxies

`TransportOptions.builder().proxy(...)` accepts only `http://` and `https://` URLs.
Passing a `socks://`, `socks4://`, or `socks5://` scheme is rejected
at construction time with a clear error. SOCKS support would
require enabling extra dependencies / feature flags on the underlying
HTTP library in every one of the 12 SDKs we generate, with non-trivial
API divergence; we explicitly chose not to. If you need SOCKS, route
through a local HTTP-CONNECT bridge or configure it at the OS level.

### `LICENSE` file is not auto-emitted

The package manifest declares MIT, but no `LICENSE` / `LICENSE.md` file
is generated alongside the sources. Drop the appropriate license text
into the generated tree as part of your release pipeline before
publishing to a registry — most registries warn or block on a missing
file, and the GitHub license auto-detect cannot pick up a manifest-only
declaration.
