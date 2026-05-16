# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

Install the SDK as a dependency in your project. If published to npm, use:

```bash
npm install <package-name>
```

If using locally, add it as a file dependency in your `package.json`.

## Quick Start

```typescript
import { Client } from './src/client';

const client = Client.withToken('https://api.example.com', 'your-token');
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```typescript
import { BearerAuthenticator } from './src/auth/bearer-authenticator';
import { Client } from './src/client';

const authenticator = new BearerAuthenticator('https://api.example.com', 'your-token');
const client = new Client(authenticator);
```

### Basic Auth

```typescript
import { BasicAuthenticator } from './src/auth/basic-authenticator';

const authenticator = new BasicAuthenticator('https://api.example.com', 'username', 'password');
const client = new Client(authenticator);
```

### API Key

```typescript
import { ApiKeyAuthenticator } from './src/auth/api-key-authenticator';
import { ApiKeyLocation } from './src/auth/api-key-location';

const authenticator = new ApiKeyAuthenticator(
  'https://api.example.com',
  'key-name',
  'key-value',
  ApiKeyLocation.Header
);
const client = new Client(authenticator);
```

### OAuth2 Client Credentials

```typescript
import { OAuth2ClientCredentialsAuthenticator } from './src/auth/oauth/oauth2-client-credentials-authenticator';

const authenticator = new OAuth2ClientCredentialsAuthenticator(
  'https://api.example.com',
  'client-id',
  'client-secret',
  'https://auth.example.com/token'
);
const client = new Client(authenticator);
```

### OAuth2 Authorization Code

```typescript
import { OAuth2AuthCodeAuthenticator } from './src/auth/oauth/oauth2-auth-code-authenticator';

const authenticator = new OAuth2AuthCodeAuthenticator(
  'https://api.example.com',
  'client-id',
  'client-secret',
  'https://auth.example.com/token',
  'authorization-code',
  'https://app.example.com/callback'
);
const client = new Client(authenticator);
```

### OAuth2 Password

```typescript
import { OAuth2PasswordAuthenticator } from './src/auth/oauth/oauth2-password-authenticator';

const authenticator = new OAuth2PasswordAuthenticator(
  'https://api.example.com',
  'client-id',
  'client-secret',
  'https://auth.example.com/token',
  'username',
  'password'
);
const client = new Client(authenticator);
```

### OAuth2 Implicit

The implicit flow obtains the access token out of band (typically in the browser). Pass the token to the authenticator:

```typescript
import { OAuth2ImplicitAuthenticator } from './src/auth/oauth/oauth2-implicit-authenticator';

const authenticator = new OAuth2ImplicitAuthenticator('https://api.example.com', 'your-access-token');
const client = new Client(authenticator);
```

### OpenID Connect

```typescript
import { OpenIdConnectAuthenticator } from './src/auth/oauth/openid-connect-authenticator';

const authenticator = new OpenIdConnectAuthenticator(
  'https://api.example.com',
  'client-id',
  'client-secret',
  'https://auth.example.com/.well-known/openid-configuration'
);
const client = new Client(authenticator);
```

### OAuth2 token lifecycle

#### Async authentication

OAuth2 authenticators implement an async `getAuthHeaders(request): Promise<Record<string, string>>` because resolving the access token requires an HTTP call to the token endpoint. The generated client always `await`s this call before sending the request; you do not need to interact with it directly.

#### Refresh tokens

When an OAuth2 grant (Authorization Code, Password, or OpenID Connect) returns a `refresh_token` alongside the access token, the generated `OAuth2TokenManager` will automatically use `grant_type=refresh_token` to obtain a fresh access token when the cached one expires. If the refresh attempt fails (for example because the refresh token itself has been revoked or has expired), the token manager falls back to re-running the original grant. Client Credentials never receives a refresh token; that flow always re-runs the client-credentials grant.

#### Token caching

The token manager caches the access token in memory and refreshes it `60` seconds before its declared expiry. This safety margin avoids a race where a token returned by `/token` could be rejected by the API moments later because the clocks of the two services drift. The margin is fixed; tune your authorization server's `expires_in` if it is too tight.

#### Client authentication method

OAuth2 clients can transmit their `client_id` and `client_secret` to the token endpoint two ways (RFC 6749 §2.3.1):

- `ClientAuthMethod.Body` (default) sends them as `application/x-www-form-urlencoded` parameters in the request body.
- `ClientAuthMethod.Basic` sends them as an HTTP Basic `Authorization` header.

Override the default if your authorization server only accepts one form:

```typescript
import { ClientAuthMethod } from './src/auth/oauth/client-auth-method';
import { OAuth2ClientCredentialsAuthenticator } from './src/auth/oauth/oauth2-client-credentials-authenticator';

const authenticator = new OAuth2ClientCredentialsAuthenticator(
  'https://api.example.com',
  'client-id',
  'client-secret',
  'https://auth.example.com/token',
  [],
  ClientAuthMethod.Basic
);
```

## Servers

If the OpenAPI spec defines multiple servers, the generated `Servers` class exposes each as a `ServerConfiguration` static property (e.g., `Servers.SERVER_0`, `Servers.SERVER_1`, ...) plus a `Servers.ALL` array. Pass the desired server's URL to the client:

```typescript
import { Servers } from './src/servers';

const client = Client.withToken(Servers.SERVER_0.url(), 'your-token');
```

## Testing

The `Authenticator` interface is the seam for tests: substitute a fake authenticator that returns a known header map, and assert your code calls the API the way you expect.

```typescript
const fake = {
  async getAuthHeaders(_req: RequestContext): Promise<Record<string, string>> {
    return { Authorization: 'Bearer test-token' };
  },
  getHost(): string {
    return 'https://api.example.com';
  }
};

const client = new Client(fake);
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

```typescript
import { NotFoundError } from './src/exceptions/not-found-error';
import { ClientError } from './src/exceptions/client-error';
import { ServerError } from './src/exceptions/server-error';

try {
  const result = await client.petApi.getPetById(petId);
} catch (error) {
  if (error instanceof NotFoundError) {
    console.log(`Not found: ${error.message}`);
  } else if (error instanceof ClientError) {
    console.log(`Client error ${error.statusCode}: ${error.message}`);
  } else if (error instanceof ServerError) {
    console.log(`Server error: ${error.message}`);
  }
}
```

## Configuration

### Custom Transport Options

```typescript
import { TransportOptions } from './src/transport-options';

const transport = TransportOptions.builder().proxy('http://proxy:3128').timeout(5000).build();

const client = new Client(authenticator, transport);
```

## API Methods

Each API group is exposed as a typed property on the client. API classes have async methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

All API methods return `Promise` values and should be used with `await`.

## Models

Models are generated as TypeScript classes in the `src/models/` directory.

```typescript
import { Pet } from './src/models/pet';

const pet = new Pet();
pet.name = 'Fido';
pet.status = 'available';
```

## Binary / File Uploads

File upload parameters are typed as `Buffer`. Binary response bodies are returned as `Buffer`.

## Comment Style

Never use inline comments (`//`). Always use block comments (`/* ... */`).

```good
/* This explains the logic */
const x = 1;
```

```bad
// This explains the logic
const x = 1;
```
