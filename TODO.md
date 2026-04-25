# TODO

## Make Python API async

Convert the generated Python API client to use `async`/`await` with `aiohttp` or `httpx` instead of synchronous `urllib3`. This would align with modern Python best practices and enable non-blocking I/O in async frameworks (FastAPI, etc.).

---

## Add eslint-plugin-unicorn to Node client

Add the `unicorn/prefer-node-protocol` rule to the generated Node/TypeScript client's ESLint config to enforce the `node:` prefix on Node.js built-in imports.

Update `eslint.config.mjs` (or the template that generates it) to include:

```js
import mridangPlugin from '@mridang/eslint-defaults';
import unicorn from 'eslint-plugin-unicorn';

export default [
  ...mridangPlugin.configs.recommended,
  {
    plugins: { unicorn },
    rules: {
      'unicorn/prefer-node-protocol': 'error',
    },
  },
];
```

This requires:
1. Adding `eslint-plugin-unicorn` as a devDependency in the Node client's `package.json` template
2. Updating the `eslint.config.mjs` template to import and configure the plugin
3. Fixing any existing imports that don't use the `node:` prefix (e.g. `import * as fs from 'fs'` → `import * as fs from 'node:fs'`)

---

# TDD Bug Verification — Normalized Test Matrix

Write failing tests in each SDK language's native test framework to prove known bugs exist before fixing them.

## Test Files (9 per language × 6 languages + 1 codegen = 55 total)

| # | Canonical Name | Tests |
|---|----------------|-------|
| 1 | BaseApiTest | Query string serialization (arrays, booleans, numbers, empty) |
| 2 | PetTest | Required fields, enum validation, JSON round-trip |
| 3 | DryFoodTest | Required fields, JSON round-trip |
| 4 | OAuth2TokenManagerTest | Refresh token storage, access token extraction, expiry detection |
| 5 | OAuth2AuthorizationCodeAuthenticatorTest | Auth URL, code exchange, refresh flow |
| 6 | OAuth2ImplicitAuthenticatorTest | Authorization URL params |
| 7 | OAuth2ClientCredentialsAuthenticatorTest | Token request params |
| 8 | OAuth2PasswordAuthenticatorTest | Token request params |
| 9 | OpenIdConnectAuthenticatorTest | Discovery URL, token exchange |

## Canonical Test Methods

Each language implements these 25 test methods, adapted to its naming convention.

### BaseApiTest (4 methods)

| Method | Description |
|--------|-------------|
| `test_expands_array_query_params` | Array values expand to `?tags=a&tags=b`, not `?tags=[a, b]` |
| `test_serializes_boolean_query_params` | Boolean serializes as `?active=true`, not `?active=True` |
| `test_serializes_number_query_params` | Number serializes as `?limit=10`, not `?limit=10.0` |
| `test_handles_empty_query_params` | Empty params map produces no `?` suffix |

### PetTest (4 methods)

| Method | Description |
|--------|-------------|
| `test_requires_name_field` | Constructing Pet without `name` should fail |
| `test_requires_photo_urls_field` | Constructing Pet without `photoUrls` should fail |
| `test_rejects_invalid_status_enum` | Deserializing `status: "invalid"` should error |
| `test_serializes_to_json` | JSON round-trip preserves all fields |

### DryFoodTest (3 methods)

| Method | Description |
|--------|-------------|
| `test_requires_food_type_field` | Constructing DryFood without `foodType` should fail |
| `test_requires_weight_kg_field` | Constructing DryFood without `weightKg` should fail |
| `test_serializes_to_json` | JSON round-trip preserves all fields |

### OAuth2TokenManagerTest (3 methods)

| Method | Description |
|--------|-------------|
| `test_stores_refresh_token` | `refresh_token` from token response is persisted |
| `test_extracts_access_token` | `access_token` is extracted from token response |
| `test_detects_token_expiry` | Expired tokens are correctly detected |

### OAuth2AuthorizationCodeAuthenticatorTest (3 methods)

| Method | Description |
|--------|-------------|
| `test_builds_authorization_url` | URL includes `response_type=code`, `client_id`, `redirect_uri` |
| `test_exchanges_code_for_token` | Code exchange sends `grant_type=authorization_code` |
| `test_refresh_includes_refresh_token` | Refresh request includes actual `refresh_token` value |

### OAuth2ImplicitAuthenticatorTest (2 methods)

| Method | Description |
|--------|-------------|
| `test_builds_authorization_url` | URL includes `response_type=token` |
| `test_includes_client_id` | URL includes `client_id` parameter |

### OAuth2ClientCredentialsAuthenticatorTest (2 methods)

| Method | Description |
|--------|-------------|
| `test_sends_grant_type` | Sends `grant_type=client_credentials` |
| `test_sends_client_credentials` | Sends `client_id` and `client_secret` |

### OAuth2PasswordAuthenticatorTest (2 methods)

| Method | Description |
|--------|-------------|
| `test_sends_grant_type` | Sends `grant_type=password` |
| `test_sends_username_and_password` | Sends `username` and `password` fields |

### OpenIdConnectAuthenticatorTest (2 methods)

| Method | Description |
|--------|-------------|
| `test_builds_authorization_url` | Constructs correct URL from discovered OIDC config |
| `test_obtains_token` | Exchanges authorization code for token |

## Expected PASS/FAIL Matrix

Tests marked **FAIL** are known bugs to be proved and then fixed.

| Test | Java | Node | Python | C# | PHP | Ruby |
|------|------|------|--------|----|-----|------|
| expands_array_query_params | FAIL | FAIL | FAIL | FAIL | PASS | PASS |
| serializes_boolean_query_params | PASS | PASS | PASS | PASS | PASS | PASS |
| serializes_number_query_params | PASS | PASS | PASS | PASS | PASS | PASS |
| handles_empty_query_params | PASS | PASS | PASS | PASS | PASS | PASS |
| requires_name_field | FAIL | FAIL | FAIL | FAIL | FAIL | FAIL |
| requires_photo_urls_field | FAIL | FAIL | FAIL | FAIL | FAIL | FAIL |
| rejects_invalid_status_enum | PASS | FAIL | FAIL | FAIL | FAIL | FAIL |
| serializes_to_json (Pet) | PASS | PASS | PASS | PASS | PASS | PASS |
| requires_food_type_field | FAIL | FAIL | FAIL | FAIL | FAIL | FAIL |
| requires_weight_kg_field | FAIL | FAIL | FAIL | FAIL | FAIL | FAIL |
| serializes_to_json (DryFood) | PASS | PASS | PASS | PASS | PASS | PASS |
| stores_refresh_token | FAIL | FAIL | FAIL | FAIL | FAIL | FAIL |
| extracts_access_token | PASS | PASS | PASS | PASS | PASS | PASS |
| detects_token_expiry | PASS | PASS | PASS | PASS | PASS | PASS |
| builds_auth_url (AuthCode) | PASS | PASS | PASS | PASS | PASS | PASS |
| exchanges_code_for_token | PASS | PASS | PASS | PASS | PASS | PASS |
| refresh_includes_refresh_token | FAIL | FAIL | FAIL | FAIL | FAIL | FAIL |
| builds_auth_url (Implicit) | PASS | PASS | PASS | PASS | PASS | PASS |
| includes_client_id | FAIL | FAIL | FAIL | FAIL | FAIL | FAIL |
| sends_grant_type (CC) | PASS | PASS | PASS | PASS | PASS | PASS |
| sends_client_credentials | PASS | PASS | PASS | PASS | PASS | PASS |
| sends_grant_type (Password) | PASS | PASS | PASS | PASS | PASS | PASS |
| sends_username_and_password | PASS | PASS | PASS | PASS | PASS | PASS |
| builds_auth_url (OIDC) | PASS | PASS | PASS | PASS | PASS | PASS |
| obtains_token (OIDC) | PASS | PASS | PASS | PASS | PASS | PASS |

## File Paths

### Java (`src/spec/resources/generated/java/src/test/java/com/example/petstore/`)

| File | Naming Convention |
|------|-------------------|
| `api/BaseApiTest.java` | `testExpandsArrayQueryParams()` |
| `models/PetTest.java` | `testRequiresNameField()` |
| `models/DryFoodTest.java` | `testRequiresFoodTypeField()` |
| `auth/oauth/OAuth2TokenManagerTest.java` | `testStoresRefreshToken()` |
| `auth/oauth/OAuth2AuthorizationCodeAuthenticatorTest.java` | `testBuildsAuthorizationUrl()` |
| `auth/oauth/OAuth2ImplicitAuthenticatorTest.java` | `testBuildsAuthorizationUrl()` |
| `auth/oauth/OAuth2ClientCredentialsAuthenticatorTest.java` | `testSendsGrantType()` |
| `auth/oauth/OAuth2PasswordAuthenticatorTest.java` | `testSendsGrantType()` |
| `auth/oauth/OpenIdConnectAuthenticatorTest.java` | `testBuildsAuthorizationUrl()` |

### Node/TypeScript (`src/spec/resources/generated/node/tests/`)

| File | Naming Convention |
|------|-------------------|
| `base-api.test.ts` | `it('expands array query params', ...)` |
| `pet.test.ts` | `it('requires name field', ...)` |
| `dry-food.test.ts` | `it('requires food type field', ...)` |
| `oauth2-token-manager.test.ts` | `it('stores refresh token', ...)` |
| `oauth2-auth-code-authenticator.test.ts` | `it('builds authorization url', ...)` |
| `oauth2-implicit-authenticator.test.ts` | `it('builds authorization url', ...)` |
| `oauth2-client-credentials-authenticator.test.ts` | `it('sends grant type', ...)` |
| `oauth2-password-authenticator.test.ts` | `it('sends grant type', ...)` |
| `openid-connect-authenticator.test.ts` | `it('builds authorization url', ...)` |

### Python (`src/spec/resources/generated/python/tests/`)

| File | Naming Convention |
|------|-------------------|
| `test_base_api.py` | `test_expands_array_query_params()` |
| `test_pet.py` | `test_requires_name_field()` |
| `test_dry_food.py` | `test_requires_food_type_field()` |
| `test_oauth2_token_manager.py` | `test_stores_refresh_token()` |
| `test_oauth2_auth_code_authenticator.py` | `test_builds_authorization_url()` |
| `test_oauth2_implicit_authenticator.py` | `test_builds_authorization_url()` |
| `test_oauth2_client_credentials_authenticator.py` | `test_sends_grant_type()` |
| `test_oauth2_password_authenticator.py` | `test_sends_grant_type()` |
| `test_openid_connect_authenticator.py` | `test_builds_authorization_url()` |

### C# (`src/spec/resources/generated/csharp/Tests/`)

| File | Naming Convention |
|------|-------------------|
| `BaseApiTest.cs` | `ExpandsArrayQueryParams()` |
| `PetTest.cs` | `RequiresNameField()` |
| `DryFoodTest.cs` | `RequiresFoodTypeField()` |
| `OAuth2TokenManagerTest.cs` | `StoresRefreshToken()` |
| `OAuth2AuthorizationCodeAuthenticatorTest.cs` | `BuildsAuthorizationUrl()` |
| `OAuth2ImplicitAuthenticatorTest.cs` | `BuildsAuthorizationUrl()` |
| `OAuth2ClientCredentialsAuthenticatorTest.cs` | `SendsGrantType()` |
| `OAuth2PasswordAuthenticatorTest.cs` | `SendsGrantType()` |
| `OpenIdConnectAuthenticatorTest.cs` | `BuildsAuthorizationUrl()` |

### PHP (`src/spec/resources/generated/php/tests/`)

| File | Naming Convention |
|------|-------------------|
| `BaseApiTest.php` | `testExpandsArrayQueryParams()` |
| `PetTest.php` | `testRequiresNameField()` |
| `DryFoodTest.php` | `testRequiresFoodTypeField()` |
| `OAuth2TokenManagerTest.php` | `testStoresRefreshToken()` |
| `OAuth2AuthorizationCodeAuthenticatorTest.php` | `testBuildsAuthorizationUrl()` |
| `OAuth2ImplicitAuthenticatorTest.php` | `testBuildsAuthorizationUrl()` |
| `OAuth2ClientCredentialsAuthenticatorTest.php` | `testSendsGrantType()` |
| `OAuth2PasswordAuthenticatorTest.php` | `testSendsGrantType()` |
| `OpenIdConnectAuthenticatorTest.php` | `testBuildsAuthorizationUrl()` |

### Ruby (`src/spec/resources/generated/ruby/spec/`)

| File | Naming Convention |
|------|-------------------|
| `base_api_spec.rb` | `it 'expands array query params'` |
| `pet_spec.rb` | `it 'requires name field'` |
| `dry_food_spec.rb` | `it 'requires food type field'` |
| `oauth2_token_manager_spec.rb` | `it 'stores refresh token'` |
| `oauth2_auth_code_authenticator_spec.rb` | `it 'builds authorization url'` |
| `oauth2_implicit_authenticator_spec.rb` | `it 'builds authorization url'` |
| `oauth2_client_credentials_authenticator_spec.rb` | `it 'sends grant type'` |
| `oauth2_password_authenticator_spec.rb` | `it 'sends grant type'` |
| `openid_connect_authenticator_spec.rb` | `it 'builds authorization url'` |

### Codegen (`src/test/java/io/github/mridang/codegen/generators/`)

| File | Description |
|------|-------------|
| `AbstractBetterCodegenTest.java` | `testGlobalSecurityInheritance()` — ops without explicit security inherit global schemes |

## Running Tests

| Language | Command | Working Directory |
|----------|---------|-------------------|
| Java | `mvn test` | `src/spec/resources/generated/java/` |
| Node/TS | `npm test` | `src/spec/resources/generated/node/` |
| Python | `pytest` | `src/spec/resources/generated/python/` |
| C# | `dotnet test` | `src/spec/resources/generated/csharp/` |
| PHP | `vendor/bin/phpunit` | `src/spec/resources/generated/php/` |
| Ruby | `bundle exec rspec` | `src/spec/resources/generated/ruby/` |
| Codegen | `devbox run -- mvn test -Dtest=AbstractBetterCodegenTest` | project root |

## Key Bugs Being Verified

1. **Array query params** (Java, Node, Python, C#): Values like `["a","b"]` are stringified instead of expanded to `key=a&key=b`. PHP and Ruby handle this correctly.
2. **OAuth2 refresh_token not stored** (all 6): TokenManager extracts `access_token` but ignores `refresh_token` from the token endpoint response.
3. **OAuth2 auth code refresh broken** (all 6): Refresh sends `grant_type=refresh_token` without the actual `refresh_token` value, causing the token endpoint to reject the request.
4. **Model required fields not validated** (all 6): Required fields per OpenAPI spec are marked `@Nullable`/optional — no validation enforced.
5. **Global security inheritance** (codegen): Operations without explicit `security` get their auth stripped instead of inheriting global security schemes.
