# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add to your `build.gradle.kts`:

```kts
implementation("com.example.petstore:petstore-client:1.0.0")
```

Or with Maven:

```xml
<dependency>
    <groupId>com.example.petstore</groupId>
    <artifactId>petstore-client</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Quick Start

```kotlin statements
import com.example.petstore.Client
import kotlinx.coroutines.runBlocking

val host = System.getenv("SWAGGER_PETSTORE_OPEN_API_3_0_BASE_URL")
val token = System.getenv("SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN")
Client.withToken(host, token).use { client ->
    val result = runBlocking { client.store.getDefaults() }
    println(result)
}
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```kotlin statements
import com.example.petstore.Client
import com.example.petstore.auth.BearerAuthenticator

val authenticator = BearerAuthenticator("https://api.example.com", "your-token")
val client = Client(authenticator)
```

A credential you pass wins; when you pass none, the authenticator reads `SWAGGER_PETSTORE_OPEN_API_3_0_TOKEN` (Basic auth: `SWAGGER_PETSTORE_OPEN_API_3_0_USERNAME` and `SWAGGER_PETSTORE_OPEN_API_3_0_PASSWORD`, API key: `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY`) once, when it is built, and an empty value counts as unset. A missing credential fails right there with an `IllegalArgumentException` naming the variable. A token that rotates takes `BearerAuthenticator(host, tokenProvider = { currentToken() })`, called on every request (`ApiKeyAuthenticator` has `apiKeyProvider`); a provider is used only when no argument and no environment variable is set.

The authenticator generated for a security scheme reads its own variable instead:

- `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_USERNAME`: the `username` of the `adminBasic` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_ADMIN_BASIC_PASSWORD`: the `password` of the `adminBasic` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_HEADER_API_KEY`: the `apiKey` of the `apiKeyHeader` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_API_KEY_QUERY_API_KEY`: the `apiKey` of the `apiKeyQuery` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_BROWSER_AUTH_CLIENT_ID`: the `clientId` of the `browserAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_INTERNAL_API_KEY_API_KEY`: the `apiKey` of the `internalApiKey` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_ID`: the `clientId` of the `legacyAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_CLIENT_SECRET`: the `clientSecret` of the `legacyAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_USERNAME`: the `username` of the `legacyAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_LEGACY_AUTH_PASSWORD`: the `password` of the `legacyAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_ID`: the `clientId` of the `machineAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_MACHINE_AUTH_CLIENT_SECRET`: the `clientSecret` of the `machineAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_USERNAME`: the `username` of the `petStoreBasic` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BASIC_PASSWORD`: the `password` of the `petStoreBasic` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_PET_STORE_BEARER_TOKEN`: the `token` of the `petStoreBearer` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_SERVICE_TOKEN_TOKEN`: the `token` of the `serviceToken` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_SESSION_COOKIE_API_KEY`: the `apiKey` of the `sessionCookie` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_ID`: the `clientId` of the `ssoAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_CLIENT_SECRET`: the `clientSecret` of the `ssoAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_SSO_AUTH_REDIRECT_URI`: the `redirectUri` of the `ssoAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_ID`: the `clientId` of the `userAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_CLIENT_SECRET`: the `clientSecret` of the `userAuth` scheme
- `SWAGGER_PETSTORE_OPEN_API_3_0_USER_AUTH_REDIRECT_URI`: the `redirectUri` of the `userAuth` scheme

### Credentials per security scheme

`Client(host, <scheme> = credential, transportOptions)` takes one optional credential for each security scheme of the API, named after the scheme: `petStoreBasic`, `adminBasic`, `petStoreBearer`, `serviceToken`, `apiKeyHeader`, `internalApiKey`, `apiKeyQuery`, `sessionCookie`, `machineAuth`, `legacyAuth`, `userAuth`, `browserAuth`, `ssoAuth`. Each operation uses the credential of the first scheme its spec lists that you passed; a call cannot pick another scheme, so an options object has no authenticator. An operation none of whose schemes you passed a credential for uses the authenticator of `Client(authenticator)` if you gave one and is otherwise sent without a credential, and an operation declared with no security never sends one.

```kotlin statements
import com.example.petstore.Client
import com.example.petstore.auth.PetStoreBasicAuthenticator

/* The credential of the scheme comes from the environment. */
val client = Client("https://api.example.com", petStoreBasic = PetStoreBasicAuthenticator("https://api.example.com"))
```

### Basic Auth

```kotlin statements
import com.example.petstore.Client
import com.example.petstore.auth.BasicAuthenticator

val authenticator = BasicAuthenticator("https://api.example.com", "username", "password")
val client = Client(authenticator)
```

### API Key

```kotlin statements
import com.example.petstore.Client
import com.example.petstore.auth.ApiKeyAuthenticator
import com.example.petstore.auth.ApiKeyLocation

val authenticator = ApiKeyAuthenticator("https://api.example.com", "key-name", "key-value", ApiKeyLocation.HEADER)
val client = Client(authenticator)
```

### OAuth2 Client Credentials

```kotlin statements
import com.example.petstore.Client
import com.example.petstore.auth.oauth.OAuth2ClientCredentialsAuthenticator

val authenticator = OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", listOf())
val client = Client(authenticator)
```

### OAuth2 Authorization Code

```kotlin statements
import com.example.petstore.Client
import com.example.petstore.auth.oauth.OAuth2AuthorizationCodeAuthenticator
import kotlinx.coroutines.runBlocking

val authenticator = OAuth2AuthorizationCodeAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/authorize", "https://auth.example.com/token",
    "https://app.example.com/callback", listOf())
val client = Client(authenticator)
runBlocking { authenticator.exchangeCode("authorization-code") }
```

### OAuth2 Password

```kotlin statements
import com.example.petstore.Client
import com.example.petstore.auth.oauth.OAuth2PasswordAuthenticator

val authenticator = OAuth2PasswordAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", "username", "password", listOf())
val client = Client(authenticator)
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```kotlin statements
import com.example.petstore.Client
import com.example.petstore.auth.oauth.OAuth2ImplicitAuthenticator

val authenticator = OAuth2ImplicitAuthenticator(
    "https://api.example.com", "client-id", "https://auth.example.com/authorize", listOf())
authenticator.setAccessToken("your-access-token")
val client = Client(authenticator)
```

### OpenID Connect

```kotlin statements
import com.example.petstore.Client
import com.example.petstore.auth.oauth.OpenIdConnectAuthenticator
import kotlinx.coroutines.runBlocking

val authenticator = OpenIdConnectAuthenticator(
    "https://api.example.com", "https://auth.example.com/.well-known/openid-configuration",
    "client-id", "client-secret", "https://app.example.com/callback", listOf())
val client = Client(authenticator)
runBlocking { authenticator.exchangeCode("authorization-code") }
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators resolve the access token by making an HTTP call to the token endpoint, which happens before the request carrying it is sent.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Unauthorized answers

When the API answers 401 to a call authenticated by an OAuth2 or OpenID Connect authenticator, the SDK calls the authenticator's `invalidateAccessToken()` (the cached access token is dropped, the refresh token is kept), obtains a new token and sends the request once more. A second 401 is raised as `UnauthorizedException`. A single-use body and a multipart body are not replayed, and Basic, Bearer and API key authenticators never replay.

#### PKCE

`buildAuthorizationUrl` adds a PKCE S256 `code_challenge` (RFC 7636) to every authorization URL and keeps the matching 43-character verifier in the authenticator; `exchangeCode` sends it as `code_verifier`. Build the URL and exchange the code on the same authenticator instance.

#### Token caching

The token manager caches the access token in memory and refreshes it up to `60` seconds before its declared expiry (never more than half of the token's lifetime). This single safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed and is not applied twice: a token valid for 60 seconds is reused for 30. A token valid for 1 second or less is fetched again on every call; tune your authorization server's `expires_in` if it is too short.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethod.BODY` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod.BASIC` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```kotlin statements
import com.example.petstore.auth.oauth.ClientAuthMethod
import com.example.petstore.auth.oauth.OAuth2ClientCredentialsAuthenticator

val authenticator = OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret",
    "https://auth.example.com/token", listOf(),
    clientAuthMethod = ClientAuthMethod.BASIC)
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `Servers` object exposes each as a `ServerConfiguration` property (e.g., `Servers.SERVER_0`, `Servers.SERVER_1`, ...) plus a `Servers.ALL` list. Pass the desired server's URL to the client:

```kotlin statements
import com.example.petstore.Client
import com.example.petstore.Servers

val client = Client.withToken(Servers.SERVER_0.getUrl(), "your-token")
```

## Testing

The `Authenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```kotlin statements
import com.example.petstore.Client
import com.example.petstore.auth.Authenticator

val fake = object : Authenticator {
    override fun getHost() = "https://api.example.com"
    override suspend fun getAuthHeaders() = mapOf("Authorization" to "Bearer test-token")
}

val client = Client(fake)
```

## Error Handling

Every error the SDK raises derives from `OpenAPIException`. Besides the HTTP errors under `ApiException` it has three siblings: `SerializationException` (a body or value that could not be encoded or decoded), `OAuth2ServerException` (the token endpoint answered with an OAuth2 error, with `code`, `description` and `uri`) and `OAuth2TokenException` (a token response that is unusable). The error hierarchy is:

- `OpenAPIException` (root)
  - `SerializationException`
  - `OAuth2ServerException`
  - `OAuth2TokenException`
- `ApiException` (base of the HTTP errors, below `OpenAPIException`)
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

```kotlin file
import com.example.petstore.Client
import com.example.petstore.errors.ApiException
import com.example.petstore.errors.ClientException
import com.example.petstore.errors.NotFoundException
import com.example.petstore.errors.ServerException
import com.example.petstore.models.Pet
import com.example.petstore.api.options.AddPetOptions

/* Operations are suspend functions, so they must be called from a coroutine. */
suspend fun addPetOrReport(client: Client, pet: Pet, options: AddPetOptions?) {
    try {
        client.pet.addPet(pet, options)
    } catch (e: NotFoundException) {
        println("Not found: ${e.message}")
    } catch (e: ClientException) {
        println("Client error ${e.statusCode}: ${e.message}")
    } catch (e: ServerException) {
        println("Server error: ${e.message}")
    } catch (e: ApiException) {
        println("API error: ${e.message}")
    }
}
```

### Request context on errors

`ApiException` has `requestMethod` and `requestPath` (the path without its query string), shown on a `Request:` line of its string form; both are null on an exception you built yourself. When an operation declares several `2xx` responses with different schemas its result is a union decoded by the status code the server sent.

## Configuration

### Per-call options

Every operation takes an options object, also one with no parameters and no security. It takes `timeout` (milliseconds, one end-to-end budget), `headers` (replace a header of the same name) and `maxRetries` (`0` disables) for that call only, in place of the client-wide setting:

```kotlin file
import com.example.petstore.Client
import com.example.petstore.api.options.GetDefaultsOptions

suspend fun getDefaultsWithOptions(client: Client) {
    client.store.getDefaults(options = GetDefaultsOptions(timeout = 2_000, maxRetries = 0))
}
```

### Closing the client

`close()` is graceful and idempotent. It marks the client closed and returns; requests already in flight finish, and the transport the SDK created is released after the last of them. A request issued after that fails with `IllegalStateException` ("ApiClient has been closed and can no longer send requests"). Streams that are still open are cancelled. A Ktor client you supplied through `TransportOptions.builder().httpClient(...)` is never closed. Use `client.use { ... }` to close it when the block ends.

### Cookie parameters

A cookie parameter is sent exactly as you give it when every character is a legal RFC 6265 cookie-octet (`%` and `=` are legal, so a value you already percent-encoded goes out unchanged). A value with a space, quote, comma, semicolon, backslash, control character or non-ASCII character fails with `IllegalArgumentException` before anything is sent, so percent-encode it first. The SDK does not encode cookie values for you.

### Models are never validated when built

A model constructor accepts any value and never raises: an out-of-range number, a string outside its pattern and an empty collection are all kept. A payload is checked only when it is decoded, where a required property that arrives as `null` or is missing and an enum string outside the declared set fail with `SerializationException`.

### Response compression

The SDK sends `Accept-Encoding: gzip, deflate` (`br` and `zstd` too when `org.brotli:dec` and `com.github.luben:zstd-jni` are on the classpath), decodes the body itself and drops `Content-Encoding` and `Content-Length` from the returned headers.

### Logging

The SDK writes no logs; inject a Ktor `HttpClient` with the `Logging` plugin through `TransportOptions.builder().httpClient(...)`.

### Hooks and telemetry

Every operation calls one small, optional hook around the call. The hook can edit the request headers before the send and observe the status or the error afterwards. With no hook set nothing changes, and the hook is the only seam for tracing.

`start` runs once per operation call, however many sends happen inside it (retries, the one replay after a `401`), after the request headers are final and before the body is encoded. It receives the call (`operationId` as written in the spec, `method`, `urlTemplate`, the `url` without its query string and `serverAddress`) and the headers, which it may read and replace; what it sets is on every send of the call. It returns an observer, or nothing. The observer's `finish` runs exactly once, whatever way the call ends, with the `statusCode` (0 when no response arrived), the lower-cased response `headers` (nothing when no response arrived) and the very error the caller receives. An error thrown by `start` reaches the caller unchanged, nothing is sent and `finish` is not called; an error thrown by `finish` is swallowed. A hook never changes the result or the error of a call, and it also runs when you supply your own transport. Several hooks are combined with `chain`, which starts them in the order given and finishes them in the reverse order.

```kotlin statements
import com.example.petstore.CallHook
import com.example.petstore.CallObserver
import com.example.petstore.Client

val hook = CallHook { info, headers ->
    headers["X-Request-Source"] = "reports"
    CallObserver { outcome -> println("${info.operationId} ended with ${outcome.statusCode}") }
}
val client = Client.withToken("https://api.example.com", "token", callHook = hook)
```

The hook runs inline on the calling coroutine, so it must not block.

The optional `OpenTelemetryCallHook` records one span, one request count and one duration value per call. It is built on the OpenTelemetry API package only; add `io.opentelemetry:opentelemetry-api` to your project to use it. The span is named with the operation id, has kind client and is a child of the active span; the hook injects `traceparent` itself. Attributes are `http.request.method`, `url.template`, `server.address`, `http.response.status_code` (only when a response arrived) and `error.type` (only on failure); the instruments are the counter `http.client.request.count` (unit `{request}`) and the histogram `http.client.request.duration` (unit `s`), whose own count equals the counter. The instrumentation scope is `petstore-client` with the SDK version. Without a configured provider the hook does nothing.

```kotlin statements
import com.example.petstore.Client
import com.example.petstore.OpenTelemetryCallHook

val client = Client.withToken("https://api.example.com", "token", callHook = OpenTelemetryCallHook.create())
```

`OpenTelemetryCallHook.create(openTelemetry)` uses the instance you give it instead of the global one.

### Custom Transport Options

```kotlin file
import com.example.petstore.Client
import com.example.petstore.TransportOptions
import com.example.petstore.auth.Authenticator

fun clientWithTransport(authenticator: Authenticator): Client {
    val transport = TransportOptions.builder()
        .proxy("http://proxy:3128")
        .timeout(5000L)
        .build()

    return Client(authenticator, transport)
}
```

### Streaming

An operation whose success response is binary (an octet stream, an image, `type: string, format: binary`) returns a `ByteStream` instead of the whole body in memory, so a large download never has to fit in memory. The call returns as soon as the response headers arrive, and the body is read as you consume it. A `ByteStream` has the status code, the lower-cased headers and an idempotent `close()` that aborts the connection instead of draining it, so close it when you stop early (`use { }`). `readAll()` reads the rest into memory and leaves the stream closed.

```kotlin file
import com.example.petstore.Client
import com.example.petstore.api.options.DownloadPetDocumentOptions
import java.io.FileOutputStream
import java.nio.file.Path

suspend fun saveTo(client: Client, petId: Long, documentId: Long, options: DownloadPetDocumentOptions?, target: Path) {
    client.pet.downloadPetDocument(petId, documentId, options).use { stream ->
        println(stream.statusCode)
        FileOutputStream(target.toFile()).use { out ->
            stream.chunks().collect { chunk -> out.write(chunk) }
        }
    }
}
```

The call `timeout` covers the wait for the response headers and stops there. `idleTimeout` (in `TransportOptions`, default 60 seconds, `0` or `null` disables it) bounds the gap between two reads of the body and raises `NetworkTimeoutException`. Retries, redirects, token refresh and the error contract apply up to the headers exactly as for a buffered call; a request is never retried after the first body byte, and a non-2xx status raises the usual typed error with its body read up to 64 KiB and never returns a stream. A connection reset, or a body that ends before its declared `Content-Length`, raises `StreamException`, a `NetworkException` with status 0. `client.close()` cancels the streams that are still open, and their next read raises the closed-client error. A custom transport that does not implement `sendStreaming` fails a stream call with `UnsupportedOperationException` naming it, and its buffered calls keep working.

An operation whose request body is raw binary (`type: string, format: binary`) takes a `BodySource` instead of buffering the bytes, so a large upload never has to fit in memory. A `BodySource` is replayable, made with `BodySource.of(bytes)`, `BodySource.ofFile(path)` or `BodySource.fromSupplier(factory)` (a factory that opens a fresh stream each time), or single use, made with `BodySource.once(stream)`. The body is sent as it is read: a known length (bytes, a file, or a length you pass) goes out as `Content-Length` and an unknown length is sent chunked. A replayable body is read again for a retry after a 5xx, a 429 or a network error and for a 307 or 308 redirect. A single-use body is never retried, so the original error is raised unchanged, and a 307 or 308 is refused with `Refusing to replay a streamed request body`; it is never buffered to make a replay possible. The SDK never closes a stream you pass to `once` and closes the streams it opens itself from a file or a factory. Multipart bodies stay buffered. A 401 that refreshes the token follows the same rule, and an event stream request with a single-use body is never reconnected.

An operation whose success response is JSON Lines (`application/x-ndjson`, `application/jsonl`, `application/jsonlines`) or server-sent events (`text/event-stream`) has no buffered method. It has a `<operation>Stream` method that takes the operation's parameters, its options object and a trailing `StreamOptions`, and returns a `JsonLinesStream<T>` of the schema type or an `SseStream<T>` of `ServerSentEvent<T>` (`id`, `event`, `data`, `retry` and the raw `data` text; `T` is the schema of the `data`, or a string). Collect its `items()` (its `events()` for an `SseStream`), and close it when you stop early. A JSON Lines line that does not decode, and a line or event longer than `maxEventBytes` (1 MiB), raise `SerializationException` and end the stream.

```kotlin file
import com.example.petstore.Client
import com.example.petstore.api.options.ExportOrdersOptions

suspend fun readJsonLines(client: Client, options: ExportOrdersOptions?) {
    client.store.exportOrdersStream(options).use { feed ->
        feed.items().collect { println(it) }
    }
}
```

```kotlin file
import com.example.petstore.Client
import com.example.petstore.api.options.WatchNoticesOptions

suspend fun readEvents(client: Client, options: WatchNoticesOptions?) {
    client.store.watchNoticesStream(options).use { feed ->
        feed.events().collect { println(it.data) }
    }
}
```

An event stream reconnects when the connection ends, fails or stays silent for the idle timeout: it asks again with fresh headers and `Last-Event-ID`, waits the server's `retry` value or `reconnectDelay` (1 second, doubling to 30 seconds with jitter), and raises the last failure after `maxReconnects` failures in a row without an event (5; 0 for a request with a body; `-1` is unlimited). It never reconnects after a 204, a status other than 408, 429 and 5xx, a response that is not `text/event-stream`, a cancellation or `client.close()`. An event named `error` raises `ApiException` with the response status and the data as its body, and a comment line such as a heartbeat counts as activity for the idle timeout. The generator option `sseTerminator` names a data value (such as `[DONE]`) that ends the stream and is not delivered. `StreamOptions` also takes `idleTimeout` and `lastEventId`.

### Pagination

Pagination covers the token cursor, id cursor, offset, page number, `Link` header and next URL in the body styles. The override option and the vendor hints are as described in the readme.

An operation that returns a list one page at a time has a `<operation>Pages` method next to its plain method. It takes the same parameters in the same order and returns a `Paginator<T, C>`, where `T` is the item type and `C` is the type of the cursor parameter. The method sends no request: the first request goes out when you first read an item or a page, and each next request goes out only when you ask for more, so stopping early sends nothing further and leaves nothing to close. Collect `items()` for a `Flow<T>` of the items, or `pages()` for a `Flow<Page<T, C>>` of `Page<T, C>` values with `items`, `nextCursor` and `result`, the same `ApiResult` that the `WithHttpInfo` variant returns, so the status and headers stay reachable. Collecting again starts again from the starting position.

```kotlin file
import com.example.petstore.Page
import com.example.petstore.Paginator
import com.example.petstore.Client
import com.example.petstore.models.Pet
import com.example.petstore.api.options.ListPetCatalogOptions

suspend fun listEverything(client: Client, options: ListPetCatalogOptions?) {
    val list: Paginator<Pet, String> = client.pet.listPetCatalogPages(options)
    list.items().collect { item: Pet -> println(item) }
    list.pages().collect { page: Page<Pet, String> ->
        println("${page.items.size} items, status ${page.result.statusCode}")
    }
}
```

The cursor you pass to the method is the starting position, and the next cursor of a page resumes the list: pass it as the starting cursor of a new call. The page size and every other parameter, option and header are repeated unchanged on each page request, and each page is an ordinary call with its own retries, timeout, redirects, authentication and redaction. An error on a later page is raised from the read that needed it, after the items of the earlier pages were delivered, and ends the iteration; the SDK never skips a page. A list ends when the next cursor is absent, `null` or empty, and an empty page with a next cursor continues. A page that returns the cursor that was just sent raises `PaginationException`, a direct child of `OpenAPIException`, after its items were delivered, instead of looping. There is no total limit and no collect-all: use `Flow.take`.

How each style ends and resumes: an id list passes the `last_id` or `next_cursor` of each page as the starting cursor of the next request and ends when a declared `has_more` is false. An offset list starts at the offset you pass, sends the offset past the items it received on each next request, and ends on an empty page or when a declared `has_more` is false. A page-number list sends the page you pass, or none, and then asks for the page after each one, and ends on an empty page or when a declared `has_more` is false. A next URL list takes the URL in the body of each page, follows it only within the origin of the client, and ends when the property is absent, `null` or empty. A cursor, offset or page that does not advance raises `PaginationException` as a token cursor does.

A list that is followed by a `Link` header (`rel="next"`, RFC 8288) takes the next URL from the header of each response. A next URL that leaves the scheme, host or port of the client is refused with `PaginationException` and nothing, no credentials either, is sent to it; such a method has no starting cursor, so it cannot resume.

A list whose page body carries the next URL in a property (a next URL list) is followed in the same way as a `Link` list: the URL is taken from the body of each response, a next URL that leaves the scheme, host or port of the client is refused with `PaginationException`, nothing is sent to it, and such a method cannot resume from a saved position.

The generator finds a paged operation by its names and shape. The operation must be a GET with one JSON 2xx response, and it must match exactly one of these styles. A token cursor is one query parameter among `cursor`, `after`, `page_token`, `next_token`, `continuation_token` and `marker`, paired with a next-cursor property among `next_cursor`, `next_page_token`, `next_token`, `next_marker`, `next_continuation_token`, `continuation_token` and `next` of the same type. An id cursor is one query parameter among `starting_after`, `after_id` and `after`, paired with a boolean `has_more` and a string `last_id` or `next_cursor` property. A `Link` list has a `Link` header declared on the response. An offset list has an `offset` query parameter with a `limit`, and a page-number list has a `page` query parameter with one of `per_page`, `page_size`, `size` or `limit`. A next URL list has no cursor parameter and a string property with `format: uri` or `uri-reference`, or one named `next_url`, `next_uri`, `nextlink` or `next_link`, that is optional or nullable. An operation that matches more than one style is ambiguous, and one that is streamed is never paged; either keeps only its plain method, and the generation log names it and says why. The `x-fern-pagination` and `x-speakeasy-pagination` (cursor type) extensions are read as hints. The generator option `paginationOverrides` (a JSON object that maps an operation id to `none` or to `{style, requestParam, responseProperty, itemsProperty, hasMoreProperty}`, where the style is `token`, `link`, `id`, `offset`, `page` or `body`) wins over a hint and over inference, and `pagination=off` switches the feature off.

### Custom HTTP client

`TransportOptions.builder().httpClient(myKtorClient)` sends every request through your own Ktor `HttpClient`, which must be built with `followRedirects = false` (`build()` refuses one that follows redirects). The SDK never closes a client you supply. It cannot be combined with `proxy`, `caCertPath` or `verifySsl(false)`.

### Retries

`TransportOptions.builder().maxRetries(n)` sets how many times a failed request is sent again (default 2, `0` disables). A network error before a response and a 408, 409, 429 or 5xx (not 501) answer are retried after 0.5 s doubling to 8 s with up to 25% jitter, or the `Retry-After` header (seconds or HTTP date, capped at 60 s). Retries share the single end-to-end `timeout` and are never extended past it; a timeout is not retried. Each retry rebuilds the request headers, and a request whose body is a single-use `BodySource` is never retried.

### Cancellation

Operations are `suspend` functions: cancel the calling coroutine or wrap the call in `withTimeout`, and the call raises `kotlinx.coroutines.CancellationException`. `NetworkTimeoutException` is only for the SDK's own `TransportOptions` timeout.

## API Methods

Each API group is exposed as a typed property on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are asynchronous; call them from a coroutine.

## Models

Models are generated as Kotlin data classes in the `com.example.petstore.models` package; a model is copied with a changed property through `copy`.

```kotlin file
import com.example.petstore.models.Account

fun withSameId(model: Account, other: Account): Account = model.copy(id = other.id)
```

## Binary / File Uploads

A raw binary request body is a `BodySource` (see Streaming). Multipart file parameters are typed as `ByteArray` (`List<ByteArray>` for multi-file uploads). A binary response body is returned as a `ByteStream` (see Streaming).

## Comment Style

Never place a comment on the same line as code. Use block comments (`/* ... */`); KDoc (`/** ... */`) is fine.

```good
/* This explains the logic */
val x = 1
```

```bad
// This explains the logic
val x = 1
```
