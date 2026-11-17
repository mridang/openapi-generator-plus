package io.github.mridang.codegen.spec;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

/**
 * Starts the Chasm mock server and the Squid proxy once per Maven run on the shared network, so
 * the twelve generated SDK test suites reuse them instead of each starting their own pair. The
 * addresses reach every runtime container as environment variables; a generated fixture that finds
 * them uses these instances, and starts its own containers otherwise (a standalone SDK).
 *
 * <p>Both servers are stateless, and the certificates, the OpenAPI document and squid.conf are
 * byte-identical in all twelve SDKs, so one instance serves every language.
 */
final class SharedHelperContainers {

  private static final Logger logger = LoggerFactory.getLogger(SharedHelperContainers.class);

  /* The name a runtime container reaches the Docker host under; the same one the
   * generated fixtures get from TESTCONTAINERS_HOST_OVERRIDE. server.pem lists it. */
  private static final String HOST = "host.docker.internal";

  private static final java.util.List<GenericContainer<?>> started = new java.util.ArrayList<>();
  private static Map<String, String> env = Map.of();

  private SharedHelperContainers() {}

  static synchronized Map<String, String> environment(Network network) {
    if (!env.isEmpty()) {
      return env;
    }
    String workdir = System.getProperty("openapi.workdir", "");
    Path fixtures = Path.of("src/main/resources/fixtures").toAbsolutePath();
    Path spec = Path.of("src/spec/resources/specs/petstore/openapi.yaml").toAbsolutePath();

    GenericContainer<?> chasm =
        new GenericContainer<>("mridang/chasm:1.3.0")
            .withExposedPorts(4010, 8443)
            .withCopyFileToContainer(MountableFile.forHostPath(spec), "/tmp/openapi.yaml")
            .withCopyFileToContainer(
                MountableFile.forHostPath(fixtures.resolve("certs/server.pem")), "/certs/cert.pem")
            .withCopyFileToContainer(
                MountableFile.forHostPath(fixtures.resolve("certs/server-key.pem")),
                "/certs/key.pem")
            .withCommand(
                "mock",
                "/tmp/openapi.yaml",
                "--host",
                "0.0.0.0",
                "--tls-cert",
                "/certs/cert.pem",
                "--tls-key",
                "/certs/key.pem",
                "--tls-port",
                "8443")
            .withNetwork(network)
            .withNetworkAliases("chasm")
            .waitingFor(Wait.forLogMessage(".*Listening on.*", 1))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withLabel("com.mridang.openapi.testcontainer", "true")
            .withLabel("com.mridang.openapi.workdir", workdir);
    chasm.start();
    started.add(chasm);

    GenericContainer<?> squid =
        new GenericContainer<>("ubuntu/squid:6.10-24.10_beta")
            .withExposedPorts(3128, 3129)
            .withCopyFileToContainer(
                MountableFile.forHostPath(fixtures.resolve("proxy/squid.conf")),
                "/etc/squid/squid.conf")
            .withNetwork(network)
            .withTmpFs(
                Map.of("/var/log/squid", "rw,mode=1777", "/var/spool/squid", "rw,mode=1777"))
            .withStartupTimeout(Duration.ofMinutes(2))
            .withLabel("com.mridang.openapi.testcontainer", "true")
            .withLabel("com.mridang.openapi.workdir", workdir);
    squid.start();
    started.add(squid);
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    String chasmHttp = "http://" + HOST + ":" + chasm.getMappedPort(4010);
    Map<String, String> values = new LinkedHashMap<>();
    values.put("API_BASE_URL", chasmHttp);
    values.put("CHASM_HTTP_URL", chasmHttp);
    values.put("CHASM_HTTPS_URL", "https://" + HOST + ":" + chasm.getMappedPort(8443));
    values.put("CHASM_INTERNAL_HTTP_URL", "http://chasm:4010");
    values.put("CHASM_INTERNAL_HTTPS_URL", "https://chasm:8443");
    /* The mock server by IP address, which server.pem does not list: the hostname-mismatch
     * test needs an address that is reachable but does not match the certificate. */
    String address =
        chasm.getContainerInfo().getNetworkSettings().getNetworks().values().iterator().next()
            .getIpAddress();
    values.put("CHASM_INTERNAL_HTTPS_ADDRESS_URL", "https://" + address + ":8443");
    values.put("PROXY_URL", "http://" + HOST + ":" + squid.getMappedPort(3128));
    values.put("PROXY_AUTH_HOST_PORT", HOST + ":" + squid.getMappedPort(3129));
    values.put("PROXY_AUTH_URL", "http://" + HOST + ":" + squid.getMappedPort(3129));
    env = Map.copyOf(values);
    logger.info("Shared helper containers started: {}", env);
    return env;
  }

  static synchronized void stop() {
    for (int i = started.size() - 1; i >= 0; i--) {
      try {
        started.get(i).stop();
      } catch (Exception e) {
        logger.warn("Failed to stop shared helper container", e);
      }
    }
    started.clear();
    env = Map.of();
  }
}
