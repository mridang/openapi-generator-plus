package com.example.petstore;

import java.util.List;
import java.util.Map;

/**
 * Generated server configurations from the OpenAPI specification.
 *
 * <p>Each constant corresponds to a server entry defined in the spec's {@code servers} array. Use
 * these constants with {@link Configuration.Builder#baseUrl(String)} to select a server:
 *
 * <pre>{@code
 * Configuration config = Configuration.builder()
 *     .baseUrl(Servers.SERVER_0.getUrl())
 *     .build();
 * }</pre>
 *
 * <p>For servers with variables, pass overrides:
 *
 * <pre>{@code
 * String url = Servers.SERVER_1.getUrl(Map.of("environment", "staging"));
 * }</pre>
 */
public final class Servers {

  /**
   * Server 0: /api/v3
   *
   * <p>Relative URL (no variables)
   */
  public static final ServerConfiguration SERVER_0 =
      new ServerConfiguration("/api/v3", "Relative URL (no variables)", Map.of());

  /**
   * Server 1: https://{environment}.example.com/api/{version}
   *
   * <p>Main API server with variables
   */
  public static final ServerConfiguration SERVER_1 =
      new ServerConfiguration(
          "https://{environment}.example.com/api/{version}",
          "Main API server with variables",
          Map.of(
              "environment",
                  new ServerVariable(
                      "api", "API environment", List.of("api", "staging", "sandbox")),
              "version", new ServerVariable("v3", "API version", List.of("v2", "v3"))));

  /** All server configurations in declaration order. */
  public static final List<ServerConfiguration> ALL = List.of(SERVER_0, SERVER_1);

  private Servers() {}
}
