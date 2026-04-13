package com.example.petstore;

import java.nio.file.Path;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.MountableFile;

/** Singleton Squid proxy container shared across all test classes. */
public final class SquidContainer {

  private static final GenericContainer<?> INSTANCE;

  static {
    // Force WireMockContainer class initialization so the shared network is created first.
    String ignored = WireMockContainer.getHttpUrl();

    INSTANCE =
        new GenericContainer<>("ubuntu/squid:5.2-22.04_beta")
            .withExposedPorts(3128)
            .withCopyFileToContainer(
                MountableFile.forHostPath(Path.of("/app/proxy/squid.conf")),
                "/etc/squid/squid.conf")
            .withNetwork(WireMockContainer.PROXY_NETWORK)
            .withStartupTimeout(Duration.ofSeconds(60));
    INSTANCE.start();
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private SquidContainer() {}

  public static String getProxyUrl() {
    return "http://" + INSTANCE.getHost() + ":" + INSTANCE.getMappedPort(3128);
  }
}
