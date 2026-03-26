import type { ApiClient } from '../../src/api-client.js';
import { OAuth2PasswordAuthenticator } from '../../src/auth/oauth/oauth2-password-authenticator.js';

describe('OAuth2PasswordAuthenticator', () => {
  it('sends grant type', async () => {
    let capturedBody = '';
    const mockClient: ApiClient = {
      async sendRequest(_method, _url, _headers, body) {
        capturedBody = body as string;
        return {
          statusCode: 200,
          body: '{"access_token":"token","expires_in":3600}',
          headers: {}
        };
      }
    };

    const auth = new OAuth2PasswordAuthenticator(
      'http://api',
      'client-id',
      'client-secret',
      'http://auth/token',
      'user',
      'pass',
      ['read']
    );
    auth.setApiClient(mockClient);
    await auth.getAuthHeadersAsync();

    expect(capturedBody).toContain('grant_type=password');
  });

  it('sends username and password', async () => {
    let capturedBody = '';
    const mockClient: ApiClient = {
      async sendRequest(_method, _url, _headers, body) {
        capturedBody = body as string;
        return {
          statusCode: 200,
          body: '{"access_token":"token","expires_in":3600}',
          headers: {}
        };
      }
    };

    const auth = new OAuth2PasswordAuthenticator(
      'http://api',
      'client-id',
      'client-secret',
      'http://auth/token',
      'testuser',
      'testpass',
      ['read']
    );
    auth.setApiClient(mockClient);
    await auth.getAuthHeadersAsync();

    expect(capturedBody).toContain('username=testuser');
    expect(capturedBody).toContain('password=testpass');
  });
});
