import type { Authenticator } from './auth/authenticator.js';
import { isHttpAwareAuthenticator } from './auth/http-aware-authenticator.js';
import { BearerAuthenticator } from './auth/bearer-authenticator.js';
import type { ApiClient } from './api-client.js';
import { Configuration } from './configuration.js';
import { DefaultApiClient } from './default-api-client.js';
import { TransportOptions } from './transport-options.js';
import { PetApi } from './api/pet-api.js';
import { StoreApi } from './api/store-api.js';

/**
 * Unified entry point for all API services.
 *
 * Takes an {@link Authenticator} and optionally {@link TransportOptions},
 * then exposes each API group as a typed property. If the authenticator
 * implements {@link HttpAwareAuthenticator}, the shared {@link ApiClient}
 * is injected so that authentication HTTP calls (token exchange, discovery)
 * use the same transport configuration as regular API calls.
 *
 * Usage:
 *
 * ```typescript
 * // Default transport
 * const client = new Client(authenticator);
 *
 * // Custom transport (proxy, timeouts, etc.)
 * const transport = TransportOptions.builder()
 *   .proxy('http://proxy:3128')
 *   .timeout(5000)
 *   .build();
 * const client = new Client(authenticator, transport);
 * ```
 */
export class Client {
  /** API operations for the PetApi group. */
  public readonly Pet: PetApi;
  /** API operations for the StoreApi group. */
  public readonly Store: StoreApi;

  /**
   * Creates a new client with the given authenticator and default transport settings.
   *
   * @param authenticator provides host URL and auth credentials
   */
  constructor(authenticator: Authenticator);

  /**
   * Creates a new client with the given authenticator and transport options.
   *
   * If the authenticator implements {@link HttpAwareAuthenticator}, the
   * shared {@link ApiClient} is injected so that token exchange and
   * discovery requests use the same proxy, TLS, and timeout settings.
   *
   * @param authenticator provides host URL and auth credentials
   * @param transportOptions HTTP transport configuration (proxy, TLS, timeouts, etc.)
   */
  constructor(authenticator: Authenticator, transportOptions: TransportOptions);

  constructor(authenticator: Authenticator, transportOptions?: TransportOptions) {
    const transport = transportOptions ?? TransportOptions.builder().build();
    const apiClient: ApiClient = new DefaultApiClient(transport);

    if (isHttpAwareAuthenticator(authenticator)) {
      authenticator.setApiClient(apiClient);
    }

    const config = Configuration.builder()
      .baseUrl(authenticator.getHost())
      .defaultHeaders(authenticator.getAuthHeaders())
      .build();
    this.Pet = new PetApi(apiClient, config);
    this.Store = new StoreApi(apiClient, config);
  }

  /**
   * Creates a client authenticated with a static Bearer token and default transport.
   *
   * @param host API base URL
   * @param accessToken Bearer token
   * @returns configured client instance
   */
  static withToken(host: string, accessToken: string): Client {
    return new Client(new BearerAuthenticator(host, accessToken));
  }
}
