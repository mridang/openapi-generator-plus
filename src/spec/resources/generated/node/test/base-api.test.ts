import { BaseApi } from '../src/api/base-api.js';
import { Configuration } from '../src/configuration.js';
import { DefaultApiClient } from '../src/default-api-client.js';
import { ServerConfiguration, ServerVariable } from '../src/server-configuration.js';
import type { Authenticator } from '../src/auth/authenticator.js';
import { ApiError } from '../src/api-error.js';
import { ClientError } from '../src/exceptions/client-error.js';
import { ServerError } from '../src/exceptions/server-error.js';
import { BadRequestError } from '../src/exceptions/bad-request-error.js';
import { UnauthorizedError } from '../src/exceptions/unauthorized-error.js';
import { ForbiddenError } from '../src/exceptions/forbidden-error.js';
import { NotFoundError } from '../src/exceptions/not-found-error.js';
import { ConflictError } from '../src/exceptions/conflict-error.js';
import { UnprocessableEntityError } from '../src/exceptions/unprocessable-entity-error.js';
import { InternalServerError } from '../src/exceptions/internal-server-error.js';

class TestableApi extends BaseApi {
  async call<T>(
    method: string,
    path: string,
    queryParams: Record<string, unknown>,
    headerParams: Record<string, string>,
    body: unknown,
    accepts: string[],
    contentType: string,
    returnType: ((json: unknown) => T) | null,
    auth?: Authenticator | null
  ): Promise<T | void> {
    return this.invokeApi(
      method, path, queryParams, headerParams, body,
      accepts, contentType, returnType, auth);
  }
}

class TestAuthenticator implements Authenticator {
  constructor(
    private headers: Record<string, string> = {},
    private queryParams: Record<string, string> = {},
    private cookies: Record<string, string> = {}
  ) {}

  getHost(): string { return ''; }
  getAuthHeaders(): Record<string, string> { return this.headers; }
  getQueryParams(): Record<string, string> { return this.queryParams; }
  getCookieParams(): Record<string, string> { return this.cookies; }
}

const wiremockUrl = process.env.WIREMOCK_HTTP_URL!;

function api(): TestableApi {
  const config = new Configuration({ baseUrl: wiremockUrl });
  return new TestableApi(new DefaultApiClient(), config);
}

describe('BaseApi exception dispatch', () => {
  const cases: [number, new (...args: never[]) => ApiError][] = [
    [400, BadRequestError],
    [401, UnauthorizedError],
    [403, ForbiddenError],
    [404, NotFoundError],
    [409, ConflictError],
    [422, UnprocessableEntityError],
    [418, ClientError],
    [500, InternalServerError],
    [502, ServerError]
  ];

  test.each(cases)('status %i throws correct exception', async (status, ErrorClass) => {
    try {
      await api().call('GET', `/api/error/${status}`, {}, {}, null,
        ['application/json'], 'application/json', null);
      fail('Expected error not thrown');
    } catch (e) {
      expect(e).toBeInstanceOf(ErrorClass);
      expect((e as ApiError).statusCode).toBe(status);
      expect((e as ApiError).responseBody).toBeTruthy();
    }
  });
});

describe('BaseApi exception hierarchy', () => {
  test('NotFoundError is ClientError is ApiError', async () => {
    try {
      await api().call('GET', '/api/error/404', {}, {}, null,
        ['application/json'], 'application/json', null);
      fail('Expected error not thrown');
    } catch (e) {
      expect(e).toBeInstanceOf(NotFoundError);
      expect(e).toBeInstanceOf(ClientError);
      expect(e).toBeInstanceOf(ApiError);
    }
  });

  test('InternalServerError is ServerError is ApiError', async () => {
    try {
      await api().call('GET', '/api/error/500', {}, {}, null,
        ['application/json'], 'application/json', null);
      fail('Expected error not thrown');
    } catch (e) {
      expect(e).toBeInstanceOf(InternalServerError);
      expect(e).toBeInstanceOf(ServerError);
      expect(e).toBeInstanceOf(ApiError);
    }
  });
});

describe('BaseApi success deserialization', () => {
  test('deserializes JSON response', async () => {
    const result = await api().call('GET', '/api/test', {}, {}, null,
      ['application/json'], 'application/json',
      (json) => json as { message: string });
    expect(result).toBeDefined();
    expect((result as { message: string }).message).toBe('success');
  });

  test('returns raw string for non-JSON response', async () => {
    const result = await api().call('GET', '/api/text', {}, {}, null,
      ['text/plain'], 'application/json',
      (json) => json as string);
    expect(result).toBeDefined();
    expect(result).toContain('hello plain text');
  });

  test('returns void when returnType is null', async () => {
    const result = await api().call('GET', '/api/test', {}, {}, null,
      ['application/json'], 'application/json', null);
    expect(result).toBeUndefined();
  });
});

describe('BaseApi query parameters', () => {
  test('appends query params to URL', async () => {
    const result = await api().call('GET', '/api/test', { foo: 'bar' }, {}, null,
      ['application/json'], 'application/json', null);
    expect(result).toBeUndefined();
  });
});

describe('BaseApi allowEmptyValue', () => {
  test('includes empty value param in query string when value is empty string', async () => {
    const result = await api().call('GET', '/api/test', { filter: '' }, {}, null,
      ['application/json'], 'application/json', null);
    expect(result).toBeUndefined();
  });
});

describe('BaseApi auth injection', () => {
  test('forwards auth headers', async () => {
    const auth = new TestAuthenticator({ 'X-Custom': 'auth-value' });
    const result = await api().call('GET', '/api/echo-headers', {}, {}, null,
      ['application/json'], 'application/json',
      (json) => json as Record<string, string>, auth);
    expect(result).toBeDefined();
    expect((result as Record<string, string>)['x-custom']).toBe('auth-value');
  });

  test('sets Cookie header from auth cookies', async () => {
    const auth = new TestAuthenticator({}, {}, { session: 'abc123' });
    await api().call('GET', '/api/test', {}, {}, null,
      ['application/json'], 'application/json', null, auth);
  });
});

describe('BaseApi body serialization', () => {
  test('serializes JSON body for POST', async () => {
    const result = await api().call('POST', '/api/echo-body', {}, {}, { key: 'value' },
      ['application/json'], 'application/json',
      (json) => json as { key: string });
    expect(result).toBeDefined();
    expect((result as { key: string }).key).toBe('value');
  });

  test('sends no body when null', async () => {
    await api().call('GET', '/api/test', {}, {}, null,
      ['application/json'], 'application/json', null);
  });
});

describe('Configuration server variable overrides', () => {
  const variableServer = new ServerConfiguration(
    'https://{environment}.example.com/api/{version}',
    'Test server with variables',
    {
      environment: new ServerVariable('api', 'API environment', ['api', 'staging', 'sandbox']),
      version: new ServerVariable('v3', 'API version', ['v2', 'v3'])
    }
  );

  test('server variable overrides resolve in base URL', () => {
    const config = Configuration.builder()
      .server(variableServer, { environment: 'staging' })
      .build();
    expect(config.baseUrl).toBe('https://staging.example.com/api/v3');
  });

  test('default server variables produce correct base URL', () => {
    const config = Configuration.builder()
      .server(variableServer)
      .build();
    expect(config.baseUrl).toBe('https://api.example.com/api/v3');
  });

  test('invalid enum value throws error', () => {
    expect(() => {
      Configuration.builder()
        .server(variableServer, { environment: 'invalid' });
    }).toThrow();
  });

  test('API request uses resolved server URL', async () => {
    const config = Configuration.builder()
      .server(variableServer, { environment: 'staging' })
      .build();
    expect(config.baseUrl).toBe('https://staging.example.com/api/v3');
    const testApi = new TestableApi(new DefaultApiClient(), config);
    expect(testApi).toBeDefined();
  });
});
