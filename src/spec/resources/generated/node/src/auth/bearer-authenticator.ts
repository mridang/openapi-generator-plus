import { BaseAuthenticator } from './base-authenticator.js';

/**
 * Authenticator for HTTP Bearer token authentication.
 */
export class BearerAuthenticator extends BaseAuthenticator {
  private readonly host: string;
  private readonly token: string;

  constructor(host: string, token: string) {
    super();
    this.host = host;
    this.token = token;
  }

  getHost(): string {
    return this.host;
  }

  getAuthHeaders(): Record<string, string> {
    return { Authorization: `Bearer ${this.token}` };
  }
}
