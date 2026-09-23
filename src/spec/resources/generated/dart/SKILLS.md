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

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed; tune your authorization server's `expires_in` if it is too tight.

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

All API errors derive from `ApiException`. The error hierarchy is:

- `ApiException` (base)
  - `ClientException` (4xx)
    - `BadRequestException` (400)
    - `UnauthorizedException` (401)
    - `ForbiddenException` (403)
    - `NotFoundException` (404)
    - `ConflictException` (409)
    - `UnprocessableEntityException` (422)
  - `ServerException` (5xx)
    - `InternalServerErrorException` (500)
  - `NetworkException` (no HTTP response, status 0)
    - `NetworkTimeoutException` (the request timed out, status 0)

```dart
try {
  final result = await client.pet.addPet(/* parameters */);
} on NotFoundException catch (e) {
  print('Not found: $e');
} on ClientException catch (e) {
  print('Client error ${e.statusCode}: $e');
} on ServerException catch (e) {
  print('Server error: $e');
} on ApiException catch (e) {
  print('API error: $e');
}
```

## Configuration

### Custom Transport Options

```dart
final transport = TransportOptionsBuilder()
  .proxy('http://proxy:3128')
  .timeout(5000)
  .build();

final client = Client(
  authenticator: authenticator,
  transportOptions: transport,
);
```

## API Methods

Each API group is exposed as a typed field on the client (e.g., `client.pet`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods are asynchronous; await the returned `Future`.

## Models

Models are generated as Dart classes in the `models` directory.

```dart
final model = ApiResponse(/* properties */);
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
