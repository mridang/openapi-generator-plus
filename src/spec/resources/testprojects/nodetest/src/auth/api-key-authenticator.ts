import type { Authenticator } from './authenticator.js';
import { ApiKeyLocation } from './api-key-location.js';

export class ApiKeyAuthenticator implements Authenticator {
  private readonly host: string;
  private readonly keyParamName: string;
  private readonly apiKey: string;
  private readonly location: ApiKeyLocation;

  constructor(host: string, keyParamName: string, apiKey: string, location: ApiKeyLocation) {
    this.host = host;
    this.keyParamName = keyParamName;
    this.apiKey = apiKey;
    this.location = location;
  }

  getHost(): string {
    return this.host;
  }

  getAuthHeaders(): Record<string, string> {
    if (this.location === ApiKeyLocation.HEADER) {
      return { [this.keyParamName]: this.apiKey };
    }
    return {};
  }

  getQueryParams(): Record<string, string> {
    if (this.location === ApiKeyLocation.QUERY) {
      return { [this.keyParamName]: this.apiKey };
    }
    return {};
  }

  getCookieParams(): Record<string, string> {
    if (this.location === ApiKeyLocation.COOKIE) {
      return { [this.keyParamName]: this.apiKey };
    }
    return {};
  }
}
