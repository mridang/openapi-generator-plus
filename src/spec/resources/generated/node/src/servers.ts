import { ServerConfiguration, ServerVariable } from './server-configuration.js';

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
   *
   * Relative URL (no variables)
   */
  public static readonly SERVER_0 = new ServerConfiguration('/api/v3', 'Relative URL (no variables)', {});

  /**
   * Server 1: https://{environment}.example.com/api/{version}
   *
   * Main API server with variables
   */
  public static readonly SERVER_1 = new ServerConfiguration(
    'https://{environment}.example.com/api/{version}',
    'Main API server with variables',
    {
      environment: new ServerVariable('api', 'API environment', ['api', 'staging', 'sandbox']),
      version: new ServerVariable('v3', 'API version', ['v2', 'v3'])
    }
  );

  /** All server configurations in declaration order. */
  public static readonly ALL: readonly ServerConfiguration[] = [Servers.SERVER_0, Servers.SERVER_1];

  private constructor() {}
}
