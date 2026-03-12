import { BaseAuthenticator } from '../base-authenticator.js';

/**
 * Authenticator for the OAuth2 Implicit flow.
 */
export class OAuth2ImplicitAuthenticator extends BaseAuthenticator {
  private readonly host: string;
  private readonly authorizationUrl: string;
  private readonly scopes: readonly string[];
  private accessToken: string | null = null;

  constructor(host: string, authorizationUrl: string, scopes: string[]) {
    super();
    this.host = host;
    this.authorizationUrl = authorizationUrl;
    this.scopes = Object.freeze([...scopes]);
  }

  buildAuthorizationUrl(state?: string): string {
    const params = new URLSearchParams({ response_type: 'token' });
    if (this.scopes.length > 0) {
      params.set('scope', this.scopes.join(' '));
    }
    if (state) {
      params.set('state', state);
    }
    return `${this.authorizationUrl}?${params.toString()}`;
  }

  setAccessToken(token: string): void {
    this.accessToken = token;
  }

  getHost(): string {
    return this.host;
  }

  getAuthHeaders(): Record<string, string> {
    if (!this.accessToken) {
      throw new Error('Must call setAccessToken() before making API requests');
    }
    return { Authorization: `Bearer ${this.accessToken}` };
  }
}
