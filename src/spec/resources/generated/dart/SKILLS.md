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
  keyName: 'key-name',
  keyValue: 'key-value',
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
final authenticator = OAuth2AuthCodeAuthenticator(
  host: 'https://api.example.com',
  clientId: 'client-id',
  clientSecret: 'client-secret',
  tokenUrl: 'https://auth.example.com/token',
  authorizationCode: 'authorization-code',
  redirectUri: 'https://app.example.com/callback',
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
final authenticator = OAuth2ImplicitAuthenticator(host: 'https://api.example.com', accessToken: 'your-access-token');
final client = Client(authenticator: authenticator);
```

### OpenID Connect

```dart
final authenticator = OpenIdConnectAuthenticator(
  host: 'https://api.example.com',
  clientId: 'client-id',
  clientSecret: 'client-secret',
  discoveryUrl: 'https://auth.example.com/.well-known/openid-configuration',
);
final client = Client(authenticator: authenticator);
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators expose `Future<Map<String, String>> getAuthHeaders(request)` because resolving the access token requires an HTTP call to the token endpoint. The generated API methods always `await` this call before sending the request.

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
class FakeAuthenticator implements Authenticator {
  @override
  String get host => 'https://api.example.com';

  @override
  Future<Map<String, String>> getAuthHeaders(RequestContext request) async =>
      {'Authorization': 'Bearer test-token'};
}

final client = Client(authenticator: FakeAuthenticator());
```

## Error Handling

All API errors extend `ApiError`. The error hierarchy is:

- `ApiError` (base)
  - `ClientError` (4xx)
    - `BadRequestError` (400)
    - `UnauthorizedError` (401)
    - `ForbiddenError` (403)
    - `NotFoundError` (404)
    - `ConflictError` (409)
    - `UnprocessableEntityError` (422)
  - `ServerError` (5xx)
    - `InternalServerError` (500)

```dart
try {
  final pet = await client.petApi.getPetById(petId: petId);
} on NotFoundError catch (e) {
  print('Not found: $e');
} on ClientError catch (e) {
  print('Client error ${e.statusCode}: $e');
} on ServerError catch (e) {
  print('Server error: $e');
} on ApiError catch (e) {
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

Each API group is exposed as a typed field on the client (e.g., `client.petApi`). API classes have async methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods return `Future<T>` and should be used with `await`.

## Models

Models are generated as Dart classes in the `models` directory.

```dart
final pet = Pet(name: 'Fido', status: 'available');
```

## Binary / File Uploads

File upload parameters accept `Uint8List` (from `dart:typed_data`). Binary response bodies are returned as `Uint8List`. `Uint8List` is a subtype of `List<int>`, so any code that consumes the result as `List<int>` continues to work; producers should wrap byte literals via `Uint8List.fromList([...])`.

## Comment Style

Never use inline comments (`//`). Always use block comments (`/* ... */`). Doc comments (`///`) are fine.

```good
/* This explains the logic */
final x = 1;
```

```bad
// This explains the logic
final x = 1;
```
