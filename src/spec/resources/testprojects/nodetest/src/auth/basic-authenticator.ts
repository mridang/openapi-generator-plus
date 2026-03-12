import type { Authenticator } from './authenticator.js';

export class BasicAuthenticator implements Authenticator {
  private readonly host: string;
  private readonly authHeader: string;

  constructor(host: string, username: string, password: string) {
    this.host = host;
    this.authHeader = 'Basic ' + Buffer.from(`${username}:${password}`).toString('base64');
  }

  getHost(): string {
    return this.host;
  }

  getAuthHeaders(): Record<string, string> {
    return { Authorization: this.authHeader };
  }

  getQueryParams(): Record<string, string> {
    return {};
  }

  getCookieParams(): Record<string, string> {
    return {};
  }
}
