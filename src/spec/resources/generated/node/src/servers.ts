import { ServerConfiguration } from './server-configuration.js';

/**
 * Generated server configurations from the OpenAPI specification.
 *
 * Each constant corresponds to a server entry defined in the spec's
 * `servers` array. Use these constants with
 * `Configuration.builder().baseUrl(...)` to select a server:
 *
 * ```typescript
 * const config = Configuration.builder()
 *   .baseUrl(Servers.SERVER_0.getUrl())
 *   .build();
 * ```
 *
 * For servers with variables, pass overrides:
 *
 * ```typescript
 * const url = Servers.SERVER_1.getUrl({ environment: 'staging' });
 * ```
 */
export class Servers {
  /**
   * Server 0: /api/v3
   */
  public static readonly SERVER_0 = new ServerConfiguration('/api/v3', null, {});

  /** All server configurations in declaration order. */
  public static readonly ALL: readonly ServerConfiguration[] = [Servers.SERVER_0];

  private constructor() {}
}
