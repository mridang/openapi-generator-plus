# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Add to your `pubspec.yaml`:

```yaml
dependencies:
  petstore_client: ^1.0.0
```

Then run:

```bash
dart pub get
```

## Quick Start

```dart
import 'package:petstore_client/petstore_client.dart';

final client = Client.withToken(
  host: 'https://api.example.com',
  accessToken: 'your-token',
);
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```dart
final authenticator = BearerAuthenticator(host: 'https://api.example.com', token: 'your-token');
final client = Client(authenticator: authenticator);
```

### Credentials from the environment

An authenticator reads a credential you do not pass from an environment variable, once, when it is built. An empty value counts as unset, an explicit argument wins, and a credential found nowhere fails the build of the authenticator with `ArgumentError`. Pass `environment:` a map where there is no process environment, such as the browser.

| Security scheme | Credential | Environment variable |
|---|---|---|
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

### Credentials from the environment

An authenticator reads a credential you do not pass from an environment variable, once, when it is built. An empty value counts as unset, an explicit argument wins, and a credential found nowhere fails the build of the authenticator with `ArgumentError`. Pass `environment:` a map where there is no process environment, such as the browser.

| Security scheme | Credential | Environment variable |
|---|---|---|
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

### Basic Auth

```dart
final authenticator = BasicAuthenticator(
  host: 'https://api.example.com',
  username: 'username',
  password: 'password',
);
final client = Client(authenticator: authenticator);
```

### API Key

```dart
final authenticator = ApiKeyAuthenticator(
  host: 'https://api.example.com',
  keyParamName: 'key-name',
  apiKey: 'key-value',
  location: ApiKeyLocation.header,
);
final client = Client(authenticator: authenticator);
```

### OAuth2 Client Credentials

```dart
final authenticator = OAuth2ClientCredentialsAuthenticator(
  host: 'https://api.example.com',
  clientId: 'client-id',
  clientSecret: 'client-secret',
  tokenUrl: 'https://auth.example.com/token',
);
final client = Client(authenticator: authenticator);
```

### OAuth2 Authorization Code

```dart
final authenticator = OAuth2AuthorizationCodeAuthenticator(
  host: 'https://api.example.com',
  clientId: 'client-id',
  clientSecret: 'client-secret',
  authorizationUrl: 'https://auth.example.com/authorize',
  tokenUrl: 'https://auth.example.com/token',
  redirectUri: 'https://app.example.com/callback',
  scopes: ['read'],
);
final client = Client(authenticator: authenticator);
```

### OAuth2 Password

```dart
final authenticator = OAuth2PasswordAuthenticator(
  host: 'https://api.example.com',
  clientId: 'client-id',
  clientSecret: 'client-secret',
  tokenUrl: 'https://auth.example.com/token',
  username: 'username',
  password: 'password',
);
final client = Client(authenticator: authenticator);
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```dart
final authenticator = OAuth2ImplicitAuthenticator(
  host: 'https://api.example.com',
  clientId: 'client-id',
  authorizationUrl: 'https://auth.example.com/authorize',
  scopes: ['read'],
);
authenticator.setAccessToken('your-access-token');
final client = Client(authenticator: authenticator);
```

### OpenID Connect

```dart
final authenticator = OpenIdConnectAuthenticator(
  host: 'https://api.example.com',
  openIdConnectUrl: 'https://auth.example.com/.well-known/openid-configuration',
  clientId: 'client-id',
  clientSecret: 'client-secret',
  redirectUri: 'https://app.example.com/callback',
  scopes: ['openid'],
);
final client = Client(authenticator: authenticator);
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators resolve the access token by making an HTTP call to the token endpoint, which happens before the request carrying it is sent.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Unauthorized answers

When the API answers 401 to a call authenticated by an OAuth2 or OpenID Connect authenticator, the SDK calls the authenticator's `invalidateAccessToken()` (the cached access token is dropped, the refresh token is kept), obtains a new token and sends the request once more. A second 401 is raised as `UnauthorizedException`. A multipart body is not replayed, and Basic, Bearer, API key and Implicit authenticators never replay.

#### PKCE

`buildAuthorizationUrl` adds a PKCE S256 `code_challenge` (RFC 7636) to every authorization URL and keeps the matching 43-character verifier in the authenticator; `exchangeCode` sends it as `code_verifier`. Build the URL and exchange the code on the same authenticator instance.

#### Token caching

The token manager caches the access token in memory and refreshes it up to `60` seconds before its declared expiry (never more than half of the token's lifetime). This single safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed and is not applied twice: a token valid for 60 seconds is reused for 30. A token valid for 1 second or less is fetched again on every call; tune your authorization server's `expires_in` if it is too short.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethod.body` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod.basic` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```dart
final authenticator = OAuth2ClientCredentialsAuthenticator(
  host: 'https://api.example.com',
  clientId: 'client-id',
  clientSecret: 'client-secret',
  tokenUrl: 'https://auth.example.com/token',
  clientAuthMethod: ClientAuthMethod.basic,
);
```

## Servers

If the OpenAPI spec defines multiple servers, the generated library exposes each as a `ServerConfiguration` constant (e.g., `server0`, `server1`, ...) plus an `allServers` list. Pass the desired server's URL to the client:

```dart
final client = Client.withToken(host: server0.url(), accessToken: 'your-token');
```

## Testing

The `Authenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```dart
class FakeAuthenticator extends BaseAuthenticator {
  @override
  String host() => 'https://api.example.com';

  @override
  Map<String, String> authHeaders() => {'Authorization': 'Bearer test-token'};
}

final client = Client(authenticator: FakeAuthenticator());
```

## Error Handling

Every error the SDK raises derives from `OpenAPIException`, so one catch of that type handles every SDK failure. The error hierarchy is:

- `OpenAPIException` (root)
  - `ApiException` (an HTTP response or its absence)
    - `ClientException` (4xx)
      - `BadRequestException` (400)
      - `UnauthorizedException` (401)
      - `ForbiddenException` (403)
      - `NotFoundException` (404)
      - `ConflictException` (409)
      - `TooManyRequestsException` (429)
      - `PaymentRequiredException` (402)
      - `MethodNotAllowedException` (405)
      - `RequestTimeoutException` (408)
      - `GoneException` (410)
      - `PayloadTooLargeException` (413)
      - `UnsupportedMediaTypeException` (415)
      - `LockedException` (423)
      - `UnprocessableEntityException` (422)
    - `ServerException` (5xx)
      - `InternalServerErrorException` (500)
      - `NotImplementedException` (501)
      - `BadGatewayException` (502)
      - `ServiceUnavailableException` (503)
      - `GatewayTimeoutException` (504)
    - `NetworkException` (no HTTP response, status 0)
      - `NetworkTimeoutException` (the request timed out, status 0)
  - `SerializationException` (a request or response body that cannot be encoded or decoded)
  - `OAuth2ServerException` (the token endpoint answered with an OAuth2 error)
  - `OAuth2TokenException` (the token response is unusable)

A malformed response body and a failed token exchange are not `ApiException`s, so end a catch sequence with the root type.

An `ApiException` prints its response body capped at 1 KiB (`... (N more bytes)`) with control characters escaped as `\xNN`, and masks credential headers (authorization, cookie, `*key*`, `*token*`, `*secret*` and the like); `responseBody` and `responseHeaders` keep the full values.

```dart
Future<void> addPetOrReport(Client client, Pet pet, AddPetOptions? options) async {
  try {
    await client.pet.addPet(pet, options);
  } on NotFoundException catch (e) {
    print('Not found: $e');
  } on ClientException catch (e) {
    print('Client error ${e.statusCode}: $e');
  } on ServerException catch (e) {
    print('Server error: $e');
  } on ApiException catch (e) {
    print('API error: $e');
  } on OpenAPIException catch (e) {
    print('SDK error: $e');
  }
}
```

### Credentials per security scheme

Client takes one optional credential for each security scheme of the API, named after the scheme: `petStoreBasic`, `adminBasic`, `petStoreBearer`, `serviceToken`, `apiKeyHeader`, `internalApiKey`, `apiKeyQuery`, `sessionCookie`, `machineAuth`, `legacyAuth`, `userAuth`, `browserAuth`, `ssoAuth`. Each operation uses the credential of the scheme its spec names; there is no per-call override. An optional `authenticator` serves any operation no scheme credential matches. The client is refused when it gets no credential at all.

## Per-call options

Every operation takes an options object as its trailing argument, `null` when nothing is set. Besides any query, header, form or cookie parameters it carries `timeout` (milliseconds, end to end), `headers` (replace a header already set) and `maxRetries` (`0` turns retries off) for that call only.

```dart
// SomeOperationOptions stands for the options class of any operation.
const options = SomeOperationOptions(
  timeout: 2000,
  headers: {'X-Trace': 'abc'},
  maxRetries: 0,
);
```

## Configuration

### Custom Transport Options

```dart
final transport = TransportOptions.builder()
  .proxy('http://proxy:3128')
  .timeout(5000)
  .build();

final client = Client(
  authenticator: authenticator,
  transportOptions: transport,
);
```

### Retries

```dart
final transport = TransportOptions.builder()
  .maxRetries(3) // default 2; 0 disables retries
  .build();
```

Network errors before a response for GET, HEAD, OPTIONS, TRACE, PUT, DELETE and QUERY (TLS and certificate failures are never retried, and POST and PATCH are sent once), and statuses 408, 409, 429 and 500-599 (not 501) are retried with a backoff of 0.5 s doubling to 8 s with 25% jitter; `Retry-After` (at most 60 s) is honoured, the request timeout is never extended, and a multipart body is never retried.

### Custom HTTP client

```dart
final transport = TransportOptions.builder()
  .httpClient(myHttpClient) // a package:http Client you own; the SDK never closes it
  .build();
```

### Cancellation

The SDK has no cancellation handle of its own. Bound a call by time with `TransportOptions.builder().timeout(ms)`: a call that exceeds it fails with `NetworkTimeoutException`. Closing the client (`client.close()`) makes the next call raise `StateError`.

### Raw responses, timeouts and the request id

Each operation has a `...WithHttpInfo` variant returning `ApiResult<T>` (`statusCode`, `data`, `rawBody`, `headers`). `timeout` is one end-to-end budget in milliseconds. An `X-Request-ID` header is added to each request when you set `TransportOptions.builder().injectRequestId(true)`.

## Hooks and telemetry

Every operation calls one small, optional hook around the call: it can edit the request headers before the request is sent and observe the status or the error afterwards, and it does nothing when no hook is set. Set it with `callHook:` on the client or `.callHook(hook)` on the `ConfigurationBuilder`, and combine several with `chainCallHooks`.

```dart
class SourceHook implements CallHook {
  @override
  CallObserver? start(CallInfo info, Map<String, String> headers) {
    headers['X-Source'] = 'nightly-job';
    return _Watcher();
  }
}

class _Watcher implements CallObserver {
  @override
  void finish(CallOutcome outcome) {
    print('${outcome.statusCode} ${outcome.error}');
  }
}

final client = Client(
  authenticator: authenticator,
  callHook: SourceHook(),
);
```

`start` runs once per call, after the trace context is injected, with the final header set, and `finish` runs exactly once with the status code (0 when no response arrived), the lower-cased response headers (`null` when no response arrived) and the error the caller receives. An error thrown by `start` reaches the caller and nothing is sent; an error thrown by `finish` is swallowed. A header the hook sets is on every send of the call. The README shows a recipe that records a span, a request count and a duration with the OpenTelemetry API.

## API Methods

Each API group is exposed as a typed field on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are asynchronous; call them with `await`.

## Models

Models are generated as Dart classes in the `models` directory.

```dart
final model = Account(
  username: 'value',
);
print(model.username);
```

## Binary / File Uploads

File upload parameters are typed as `Uint8List`. Binary response bodies are returned as `Uint8List`.

## Comment Style

Never place a comment on the same line as code. Use block comments (`/* ... */`); doc comments (`///`) are fine.

```good
/* This explains the logic */
final x = 1;
```

```bad
// This explains the logic
final x = 1;
```
