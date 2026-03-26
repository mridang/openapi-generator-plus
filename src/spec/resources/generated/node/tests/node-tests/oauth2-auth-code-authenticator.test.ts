import type { ApiClient } from '../../src/api-client.js';
import { OAuth2AuthorizationCodeAuthenticator } from '../../src/auth/oauth/oauth2-auth-code-authenticator.js';

describe('OAuth2AuthorizationCodeAuthenticator', () => {
  it('builds authorization url', () => {
    const auth = new OAuth2AuthorizationCodeAuthenticator(
      'http://api',
      'my-client-id',
      'my-secret',
      'http://auth/authorize',
      'http://auth/token',
      'http://callback',
      ['read', 'write']
    );

    const url = auth.buildAuthorizationUrl('state123');

    expect(url).toContain('response_type=code');
    expect(url).toContain('client_id=my-client-id');
    expect(url).toContain('redirect_uri=');
    expect(url).toContain('state=state123');
    expect(url).toContain('scope=read+write');
  });

  it('exchanges code for token', async () => {
    let capturedBody = '';
    const mockClient: ApiClient = {
      async sendRequest(_method, _url, _headers, body) {
        capturedBody = body as string;
        return {
          statusCode: 200,
          body: '{"access_token":"test-token","refresh_token":"test-refresh","expires_in":3600}',
          headers: {}
        };
      }
    };

    const auth = new OAuth2AuthorizationCodeAuthenticator(
      'http://api',
      'my-client-id',
      'my-secret',
      'http://auth/authorize',
      'http://auth/token',
      'http://callback',
      ['read']
    );
    auth.setApiClient(mockClient);
    await auth.exchangeCode('test-code');

    expect(capturedBody).toContain('grant_type=authorization_code');
    expect(capturedBody).toContain('code=test-code');
    expect(capturedBody).toContain('client_id=my-client-id');
    expect(capturedBody).toContain('client_secret=my-secret');
  });

  it('refresh includes refresh token', async () => {
    let lastBody = '';
    let callCount = 0;
    const mockClient: ApiClient = {
      async sendRequest(_method, _url, _headers, body) {
        lastBody = body as string;
        callCount++;
        return {
          statusCode: 200,
          body: `{"access_token":"token-${callCount}","refresh_token":"refresh-abc","expires_in":1}`,
          headers: {}
        };
      }
    };

    const auth = new OAuth2AuthorizationCodeAuthenticator(
      'http://api',
      'my-client-id',
      'my-secret',
      'http://auth/authorize',
      'http://auth/token',
      'http://callback',
      ['read']
    );
    auth.setApiClient(mockClient);

    // Initial code exchange (token expires immediately: expires_in=1, minus 30s buffer)
    await auth.exchangeCode('test-code');
    expect(callCount).toBe(1);

    // Getting auth headers triggers refresh since token is expired
    await auth.getAuthHeadersAsync();
    expect(callCount).toBe(2);

    // The refresh request body should include the actual refresh_token value
    expect(lastBody).toContain('refresh_token=refresh-abc');
  });
});
