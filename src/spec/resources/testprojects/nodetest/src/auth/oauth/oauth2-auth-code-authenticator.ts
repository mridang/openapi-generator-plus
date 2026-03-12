import type { Authenticator } from '../authenticator.js';
import { OAuth2TokenManager } from './oauth2-token-manager.js';

export class OAuth2AuthorizationCodeAuthenticator implements Authenticator {
  private readonly host: string;
  private readonly clientId: string;
  private readonly clientSecret: string;
  private readonly authorizationUrl: string;
  private readonly tokenUrl: string;
  private readonly redirectUri: string;
  private readonly scopes: string[];
  private readonly tokenManager: OAuth2TokenManager;
  private tokenExchanged = false;

  constructor(
    host: string,
    clientId: string,
    clientSecret: string,
    authorizationUrl: string,
    tokenUrl: string,
    redirectUri: string,
    scopes: string[]
  ) {
    this.host = host;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.authorizationUrl = authorizationUrl;
    this.tokenUrl = tokenUrl;
    this.redirectUri = redirectUri;
    this.scopes = [...scopes];
    this.tokenManager = new OAuth2TokenManager();
  }

  buildAuthorizationUrl(state?: string): string {
    const params = new URLSearchParams({
      response_type: 'code',
      client_id: this.clientId,
      redirect_uri: this.redirectUri
    });
    if (this.scopes.length > 0) {
      params.set('scope', this.scopes.join(' '));
    }
    if (state) {
      params.set('state', state);
    }
    return `${this.authorizationUrl}?${params.toString()}`;
  }

  async exchangeCode(code: string): Promise<void> {
    const params: Record<string, string> = {
      grant_type: 'authorization_code',
      code,
      client_id: this.clientId,
      client_secret: this.clientSecret,
      redirect_uri: this.redirectUri
    };
    await this.tokenManager.getAccessToken(this.tokenUrl, params);
    this.tokenExchanged = true;
  }

  getHost(): string {
    return this.host;
  }

  getAuthHeaders(): Record<string, string> {
    throw new Error('Use getAuthHeadersAsync() instead');
  }

  async getAuthHeadersAsync(): Promise<Record<string, string>> {
    if (!this.tokenExchanged) {
      throw new Error('Must call exchangeCode() before making API requests');
    }
    const params = { grant_type: 'refresh_token' };
    const token = await this.tokenManager.getAccessToken(this.tokenUrl, params);
    return { Authorization: `Bearer ${token}` };
  }

  getQueryParams(): Record<string, string> {
    return {};
  }

  getCookieParams(): Record<string, string> {
    return {};
  }
}
