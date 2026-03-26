import type { ApiClient } from '../../src/api-client.js';
import { OAuth2ClientCredentialsAuthenticator } from '../../src/auth/oauth/oauth2-client-credentials-authenticator.js';

describe('OAuth2ClientCredentialsAuthenticator', () => {
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

    const auth = new OAuth2ClientCredentialsAuthenticator(
      'http://api',
      'client-id',
      'client-secret',
      'http://auth/token',
      ['read']
    );
    auth.setApiClient(mockClient);
    await auth.getAuthHeadersAsync();

    expect(capturedBody).toContain('grant_type=client_credentials');
  });

  it('sends client credentials', async () => {
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

    const auth = new OAuth2ClientCredentialsAuthenticator(
      'http://api',
      'my-client',
      'my-secret',
      'http://auth/token',
      ['read']
    );
    auth.setApiClient(mockClient);
    await auth.getAuthHeadersAsync();

    expect(capturedBody).toContain('client_id=my-client');
    expect(capturedBody).toContain('client_secret=my-secret');
  });
});
