import { OAuth2ImplicitAuthenticator } from '../../src/auth/oauth/oauth2-implicit-authenticator.js';

describe('OAuth2ImplicitAuthenticator', () => {
  it('builds authorization url', () => {
    const auth = new OAuth2ImplicitAuthenticator(
      'http://api',
      'my-client-id',
      'http://auth/authorize',
      ['read', 'write']
    );
    const url = auth.buildAuthorizationUrl('state123');

    expect(url).toMatch(/^http:\/\/auth\/authorize\?/);
    expect(url).toContain('response_type=token');
  });

  it('includes client id', () => {
    const auth = new OAuth2ImplicitAuthenticator(
      'http://api',
      'my-client-id',
      'http://auth/authorize',
      ['read']
    );
    const url = auth.buildAuthorizationUrl('state123');

    // Implicit flow authorization URL must include client_id per RFC 6749 §4.2.1
    expect(url).toContain('client_id=');
  });
});
