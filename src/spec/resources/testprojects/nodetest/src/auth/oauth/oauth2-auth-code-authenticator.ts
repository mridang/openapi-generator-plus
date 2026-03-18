import type { ApiClient } from '../../api-client.js';
import type { HttpAwareAuthenticator } from '../http-aware-authenticator.js';
import { OAuth2TokenManager } from './oauth2-token-manager.js';

/**
 * Authenticator for the OAuth2 Authorization Code flow.
 *
 * Implements {@link HttpAwareAuthenticator} so that token exchange requests
 * use the shared {@link ApiClient} with the same transport configuration
 * (proxy, TLS, timeouts) as regular API calls.
 *
 * Usage:
 * 1. Call {@link buildAuthorizationUrl} to get the authorization URL
 * 2. Redirect the user to that URL
 * 3. After the callback, call {@link exchangeCode} with the auth code
 * 4. Use the authenticator normally -- tokens are managed automatically
 */
export class OAuth2AuthorizationCodeAuthenticator implements HttpAwareAuthenticator {
  private readonly host: string;
  private readonly clientId: string;
  private readonly clientSecret: string;
  private readonly authorizationUrl: string;
  private readonly tokenUrl: string;
  private readonly redirectUri: string;
  private readonly scopes: readonly string[];
  private readonly tokenManager: OAuth2TokenManager;
  private tokenExchanged = false;

  /**
   * Create a new authorization code authenticator.
   *
   * @param host API base URL
   * @param clientId OAuth2 client ID
   * @param clientSecret OAuth2 client secret
   * @param authorizationUrl authorization endpoint URL
   * @param tokenUrl token endpoint URL
   * @param redirectUri redirect URI registered with the OAuth2 provider
   * @param scopes requested scopes
   */
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
    this.scopes = Object.freeze([...scopes]);
    this.tokenManager = new OAuth2TokenManager();
  }

  /**
   * Inject the shared API client for making token requests.
   *
   * @param apiClient the shared API client instance
   */
  setApiClient(apiClient: ApiClient): void {
    this.tokenManager.setApiClient(apiClient);
  }

  /**
   * Build the authorization URL to redirect the user to.
   *
   * @param state optional CSRF state parameter
   * @returns the authorization URL
   */
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

  /**
   * Exchange an authorization code for an access token.
   *
   * @param code the authorization code from the callback
   */
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

  /**
   * Returns the base URL of the API.
   *
   * @returns the host URL
   */
  getHost(): string {
    return this.host;
  }

  /**
   * Returns the authentication headers with a valid Bearer token.
   *
   * This method is synchronous and will throw. Use {@link getAuthHeadersAsync} instead.
   *
   * @throws Error always -- use getAuthHeadersAsync() instead
   */
  getAuthHeaders(): Record<string, string> {
    throw new Error('Use getAuthHeadersAsync() instead');
  }

  /**
   * Returns the authentication headers with a valid Bearer token.
   *
   * Requires {@link exchangeCode} to have been called first.
   *
   * @returns a promise resolving to the authorization headers
   * @throws Error if exchangeCode() has not been called
   */
  async getAuthHeadersAsync(): Promise<Record<string, string>> {
    if (!this.tokenExchanged) {
      throw new Error('Must call exchangeCode() before making API requests');
    }
    const params = { grant_type: 'refresh_token' };
    const token = await this.tokenManager.getAccessToken(this.tokenUrl, params);
    return { Authorization: `Bearer ${token}` };
  }

  /**
   * Returns query parameters to include for authentication.
   *
   * @returns empty record (not used for OAuth2)
   */
  getQueryParams(): Record<string, string> {
    return {};
  }

  /**
   * Returns cookie parameters to include for authentication.
   *
   * @returns empty record (not used for OAuth2)
   */
  getCookieParams(): Record<string, string> {
    return {};
  }
}
