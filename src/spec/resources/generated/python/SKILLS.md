# Swagger Petstore - OpenAPI 3.0 SDK - AI Agent Reference

## Installation

```bash
pip install petstore_client
```

## Quick Start

```python
from petstore_client.client import Client

client = Client.with_token("https://api.example.com", "your-token")
```

## Authentication

All authentication is handled via `Authenticator` implementations passed to the client constructor.

### Bearer Token

```python
from petstore_client.auth.bearer_authenticator import BearerAuthenticator

authenticator = BearerAuthenticator("https://api.example.com", "your-token")
client = Client(authenticator)
```

### Basic Auth

```python
from petstore_client.auth.basic_authenticator import BasicAuthenticator

authenticator = BasicAuthenticator("https://api.example.com", "username", "password")
client = Client(authenticator)
```

### API Key

```python
from petstore_client.auth.api_key_authenticator import ApiKeyAuthenticator
from petstore_client.auth.api_key_location import ApiKeyLocation

authenticator = ApiKeyAuthenticator("https://api.example.com", "key-name", "key-value", ApiKeyLocation.HEADER)
client = Client(authenticator)
```

### OAuth2 Client Credentials

```python
from petstore_client.auth.oauth.oauth2_client_credentials_authenticator import OAuth2ClientCredentialsAuthenticator

authenticator = OAuth2ClientCredentialsAuthenticator(
    "https://api.example.com", "client-id", "client-secret", "https://auth.example.com/token")
client = Client(authenticator)
```


## Error Handling

All API errors extend `ApiError`. The exception hierarchy is:

- `ApiError` (base)
  - `ClientException` (4xx)
    - `BadRequestException` (400)
    - `UnauthorizedException` (401)
    - `ForbiddenException` (403)
    - `NotFoundException` (404)
    - `ConflictException` (409)
    - `UnprocessableEntityException` (422)
  - `ServerException` (5xx)
    - `InternalServerErrorException` (500)

```python
from petstore_client.exceptions import (
    NotFoundException,
    ClientException,
    ServerException,
    ApiError,
)

try:
    result = client.pet_api.get_pet_by_id(pet_id)
except NotFoundException as e:
    print(f"Not found: {e}")
except ClientException as e:
    print(f"Client error {e.status_code}: {e}")
except ServerException as e:
    print(f"Server error: {e}")
except ApiError as e:
    print(f"API error: {e}")
```

## Configuration

### Custom Transport Options

```python
from petstore_client.transport_options import TransportOptions

transport = (
    TransportOptions.builder()
    .proxy("http://proxy:3128")
    .timeout(5000)
    .build()
)

client = Client(authenticator, transport)
```

## API Methods

Each API group is exposed as a typed attribute on the client (e.g., `client.pet_api`). API classes have methods that correspond to OpenAPI operations, accepting typed request parameters and returning typed response models.

## Models

Models are generated as Python dataclasses. They are located in `petstore_client.models`.

```python
from petstore_client.models.pet import Pet

pet = Pet(name="Fido", status="available")
```

## Binary / File Uploads

File upload parameters accept file-like objects or `bytes`. Binary response bodies are returned as `bytes`.

## Comment Style

Use multi-line `"""` docstrings or `#` comments on their own line. Never place inline comments on the same line as code.

```good
# This explains the logic
x = 1
```

```bad
x = 1  # This explains the logic
```
