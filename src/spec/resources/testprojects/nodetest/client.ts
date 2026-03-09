import type { Authenticator } from './auth/authenticator.js';
import type { ApiClient } from './api-client.js';
import { Configuration } from './configuration.js';
import { DefaultApiClient } from './default-api-client.js';
import { PetApi } from './api/pet-api.js';
import { StoreApi } from './api/store-api.js';

/**
 * Unified entry point for all API services. Takes an {@link Authenticator}
 * and exposes each API group as a typed property.
 */
export class Client {
  public readonly pet: PetApi;
  public readonly store: StoreApi;

  /**
   * Creates a new client with the given authenticator.
   *
   * @param authenticator Provides host URL and auth headers.
   */
  constructor(authenticator: Authenticator) {
    const config = new Configuration({
      baseUrl: authenticator.getHost(),
      defaultHeaders: { ...authenticator.getAuthHeaders() }
    });
    const apiClient: ApiClient = new DefaultApiClient(config);
    this.pet = new PetApi(config, apiClient);
    this.store = new StoreApi(config, apiClient);
  }

  /**
   * Creates a client authenticated with a static Bearer token.
   *
   * @param host API base URL.
   * @param accessToken Bearer token.
   * @returns Configured client instance.
   */
  static withToken(host: string, accessToken: string): Client {
    return new Client({
      getHost: () => host,
      getAuthHeaders: () => ({ Authorization: `Bearer ${accessToken}` })
    });
  }
}
