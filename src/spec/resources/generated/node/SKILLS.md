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

const authenticator = new ApiKeyAuthenticator('https://api.example.com', 'key-name', 'key-value', ApiKeyLocation.Header);
const client = new Client(authenticator);
```

### OAuth2 Client Credentials

```typescript
import { OAuth2ClientCredentialsAuthenticator } from './src/auth/oauth/oauth2-client-credentials-authenticator';

const authenticator = new OAuth2ClientCredentialsAuthenticator(
  'https://api.example.com', 'client-id', 'client-secret', 'https://auth.example.com/token');
const client = new Client(authenticator);
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

const transport = TransportOptions.builder()
  .proxy('http://proxy:3128')
  .timeout(5000)
  .build();

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
