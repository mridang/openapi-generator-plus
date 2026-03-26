import type { ApiClient } from '../../src/api-client.js';
import { OpenIdConnectAuthenticator } from '../../src/auth/oauth/openid-connect-authenticator.js';

const DISCOVERY_JSON = JSON.stringify({
  authorization_endpoint: 'http://auth/authorize',
  token_endpoint: 'http://auth/token'
});

describe('OpenIdConnectAuthenticator', () => {
  it('builds authorization url', async () => {
    const mockClient: ApiClient = {
      async sendRequest() {
        return { statusCode: 200, body: DISCOVERY_JSON, headers: {} };
      }
    };

    const auth = new OpenIdConnectAuthenticator(
      'http://api',
      'http://auth/.well-known/openid-configuration',
      'client-id',
      'client-secret',
      'http://callback',
      ['openid']
    );
    auth.setApiClient(mockClient);

    const url = await auth.buildAuthorizationUrl('state123');
    expect(url).toContain('response_type=code');
    expect(url).toContain('client_id=client-id');
    expect(url).toContain('state=state123');
  });

  it('obtains token', async () => {
    let callCount = 0;
    const mockClient: ApiClient = {
      async sendRequest() {
        callCount++;
        if (callCount === 1) {
          return { statusCode: 200, body: DISCOVERY_JSON, headers: {} };
        }
        return {
          statusCode: 200,
          body: '{"access_token":"oidc-token","expires_in":3600}',
          headers: {}
        };
      }
    };

    const auth = new OpenIdConnectAuthenticator(
      'http://api',
      'http://auth/.well-known/openid-configuration',
      'client-id',
      'client-secret',
      'http://callback',
      ['openid']
    );
    auth.setApiClient(mockClient);

    await auth.exchangeCode('test-code');
    const headers = await auth.getAuthHeadersAsync();

    expect(headers).toHaveProperty('Authorization');
    expect(headers.Authorization).toMatch(/^Bearer /);
  });
});
