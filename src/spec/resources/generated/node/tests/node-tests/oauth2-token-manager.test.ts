import type { ApiClient } from '../../src/api-client.js';
import { OAuth2TokenManager } from '../../src/auth/oauth/oauth2-token-manager.js';

function createMockClient(responseBody: string): ApiClient {
  return {
    async sendRequest() {
      return { statusCode: 200, body: responseBody, headers: {} };
    }
  };
}

describe('OAuth2TokenManager', () => {
  it('stores refresh token', async () => {
    const mockClient = createMockClient(
      '{"access_token":"test-access","refresh_token":"test-refresh","expires_in":3600}'
    );

    const manager = new OAuth2TokenManager();
    manager.setApiClient(mockClient);
    await manager.getAccessToken('http://auth/token', { grant_type: 'authorization_code' });

    // The manager should store the refresh_token from the token response.
    // Check internal state via any cast.
    const managerAny = manager as any;
    const hasRefreshToken = Object.values(managerAny).some(
      (v: unknown) => v === 'test-refresh'
    );
    expect(hasRefreshToken).toBe(true);
  });

  it('extracts access token', async () => {
    const mockClient = createMockClient(
      '{"access_token":"expected-token","expires_in":3600}'
    );

    const manager = new OAuth2TokenManager();
    manager.setApiClient(mockClient);
    const token = await manager.getAccessToken('http://auth/token', {
      grant_type: 'client_credentials'
    });

    expect(token).toBe('expected-token');
  });

  it('detects token expiry', async () => {
    let callCount = 0;
    const mockClient: ApiClient = {
      async sendRequest() {
        callCount++;
        return {
          statusCode: 200,
          body: `{"access_token":"token-${callCount}","expires_in":1}`,
          headers: {}
        };
      }
    };

    const manager = new OAuth2TokenManager();
    manager.setApiClient(mockClient);

    // First call: token with expires_in=1 (already expired after subtracting 30s buffer)
    const first = await manager.getAccessToken('http://auth/token', {
      grant_type: 'client_credentials'
    });
    // Second call: should detect expiry and fetch a new token
    const second = await manager.getAccessToken('http://auth/token', {
      grant_type: 'client_credentials'
    });

    expect(callCount).toBe(2);
    expect(second).not.toBe(first);
  });
});
