import type { Authenticator } from '../authenticator.js';

export class OAuth2ImplicitAuthenticator implements Authenticator {
  private readonly host: string;
  private readonly authorizationUrl: string;
  private readonly scopes: string[];
  private accessToken: string | null = null;

  constructor(host: string, authorizationUrl: string, scopes: string[]) {
    this.host = host;
    this.authorizationUrl = authorizationUrl;
    this.scopes = [...scopes];
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

  getQueryParams(): Record<string, string> {
    return {};
  }

  getCookieParams(): Record<string, string> {
    return {};
  }
}
