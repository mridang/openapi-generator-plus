package io.github.mridang.codegen.spec;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import javax.annotation.Nullable;

/**
 * Base class for TLS/proxy integration tests. Extends {@link AbstractIntegrationSpec}
 * with WireMock (HTTPS) and Squid (HTTP proxy) container management.
 */
@SuppressWarnings("NullAway.Init")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractTlsProxySpec extends AbstractIntegrationSpec {

  @Nullable protected GenericContainer<?> wiremockContainer;
  @Nullable protected GenericContainer<?> proxyContainer;

  protected abstract Path getTestProjectPath();

  protected abstract Map<String, Object> getCodegenProperties();

  @BeforeEach
  void setupTlsProxy() throws IOException {
    copyDirectory(getTestProjectPath(), tempOutputDir);
  }

  @Test
  @Order(1)
  void shouldPassTlsProxyTests() throws IOException {
    startWireMockServer();
    startProxyServer();
    copyCaCertToOutput();

    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("TLS/proxy tests failed:\n%s", result.output())
        .isTrue();
  }

  @Override
  void cleanup() {
    super.cleanup();
    if (wiremockContainer != null && wiremockContainer.isRunning()) {
      wiremockContainer.stop();
      logger.info("Stopped WireMock container");
    }
    if (proxyContainer != null && proxyContainer.isRunning()) {
      proxyContainer.stop();
      logger.info("Stopped Squid proxy container");
    }
  }

  /**
   * Start the WireMock container with HTTPS enabled using custom certificates.
   * Also starts on HTTP port 8080 for proxy testing.
   */
  protected void startWireMockServer() {
    logger.info("Starting WireMock server with HTTPS...");

    wiremockContainer =
        new GenericContainer<>(DockerImageName.parse("wiremock/wiremock:3.13.0"))
            .withNetwork(sharedNetwork)
            .withNetworkAliases("wiremock")
            .withExposedPorts(8080, 8443)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("certs/server-keystore.p12"),
                "/tmp/keystore.p12")
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("wiremock/mappings/"),
                "/home/wiremock/mappings/")
            .withCommand(
                "--port", "8080",
                "--https-port", "8443",
                "--https-keystore", "/tmp/keystore.p12",
                "--keystore-type", "PKCS12",
                "--keystore-password", "changeit",
                "--key-manager-password", "changeit",
                "--verbose")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(60))
            .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix("wiremock"));

    wiremockContainer.start();

    logger.info("WireMock server started:");
    logger.info("  - HTTP:  http://wiremock:8080");
    logger.info("  - HTTPS: https://wiremock:8443");
  }

  /**
   * Start the Squid HTTP proxy container.
   */
  protected void startProxyServer() {
    logger.info("Starting Squid proxy server...");

    proxyContainer =
        new GenericContainer<>(DockerImageName.parse("ubuntu/squid:5.2-22.04_beta"))
            .withNetwork(sharedNetwork)
            .withNetworkAliases("proxy")
            .withExposedPorts(3128)
            .withCopyFileToContainer(
                MountableFile.forClasspathResource("proxy/squid.conf"),
                "/etc/squid/squid.conf")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(60))
            .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix("squid"));

    proxyContainer.start();

    logger.info("Squid proxy started: http://proxy:3128");
  }

  /**
   * Copy the CA certificate to the temp output directory so it is available
   * inside the runtime container at /app/ca.pem.
   */
  protected void copyCaCertToOutput() throws IOException {
    URL caCertUrl = getClass().getClassLoader().getResource("certs/ca.pem");
    if (caCertUrl == null) {
      throw new IllegalStateException("Could not find CA certificate resource: certs/ca.pem");
    }
    Files.copy(Path.of(caCertUrl.getPath()), tempOutputDir.resolve("ca.pem"),
        StandardCopyOption.REPLACE_EXISTING);
    logger.info("Copied CA certificate to {}", tempOutputDir.resolve("ca.pem"));
  }
}
