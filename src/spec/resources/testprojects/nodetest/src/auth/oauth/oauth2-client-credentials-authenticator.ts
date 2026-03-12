import { BaseAuthenticator } from '../base-authenticator.js';
import { OAuth2TokenManager } from './oauth2-token-manager.js';

/**
 * Authenticator for the OAuth2 Client Credentials flow.
 */
export class OAuth2ClientCredentialsAuthenticator extends BaseAuthenticator {
  private readonly host: string;
  private readonly clientId: string;
  private readonly clientSecret: string;
  private readonly tokenUrl: string;
  private readonly scopes: readonly string[];
  private readonly tokenManager: OAuth2TokenManager;

  constructor(host: string, clientId: string, clientSecret: string, tokenUrl: string, scopes: string[]) {
    super();
    this.host = host;
    this.clientId = clientId;
    this.clientSecret = clientSecret;
    this.tokenUrl = tokenUrl;
    this.scopes = Object.freeze([...scopes]);
    this.tokenManager = new OAuth2TokenManager();
  }

  getHost(): string {
    return this.host;
  }

  getAuthHeaders(): Record<string, string> {
    throw new Error('Use getAuthHeadersAsync() instead');
  }

  async getAuthHeadersAsync(): Promise<Record<string, string>> {
    const params: Record<string, string> = {
      grant_type: 'client_credentials',
      client_id: this.clientId,
      client_secret: this.clientSecret
    };
    if (this.scopes.length > 0) {
      params.scope = this.scopes.join(' ');
    }
    const token = await this.tokenManager.getAccessToken(this.tokenUrl, params);
    return { Authorization: `Bearer ${token}` };
  }
}
