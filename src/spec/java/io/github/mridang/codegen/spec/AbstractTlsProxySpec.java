package io.github.mridang.codegen.spec;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
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
public abstract class AbstractTlsProxySpec extends AbstractIntegrationSpec {

  @Nullable protected GenericContainer<?> wiremockContainer;
  @Nullable protected GenericContainer<?> proxyContainer;

  @Override
  protected String getTestScript(String prismBaseUrl) {
    return "";
  }

  @AfterEach
  void cleanupTlsProxy() {
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

  /**
   * Copy test project directory to temp output directory.
   */
  protected void copyTestProject(Path testProjectPath) throws IOException {
    if (!Files.exists(testProjectPath)) {
      throw new IllegalStateException(
          "Could not find test project at: " + testProjectPath.toAbsolutePath());
    }
    try (Stream<Path> stream = Files.walk(testProjectPath)) {
      stream.forEach(
          sourcePath -> {
            try {
              Path targetPath = tempOutputDir.resolve(testProjectPath.relativize(sourcePath));
              if (Files.isDirectory(sourcePath)) {
                Files.createDirectories(targetPath);
              } else {
                Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
              }
            } catch (IOException e) {
              throw new RuntimeException("Failed to copy " + sourcePath, e);
            }
          });
    }
    logger.info("Copied test project from {} to {}", testProjectPath, tempOutputDir);
  }

  /**
   * Generate client code into the temp output directory.
   */
  protected void generateClientToDirectory(
      java.util.Map<String, Object> additionalProperties, Path outputDir) {
    URL specUrl = getClass().getClassLoader().getResource(getSpecResourcePath());
    if (specUrl == null) {
      throw new IllegalStateException("Could not find spec resource: " + getSpecResourcePath());
    }

    String specPath = specUrl.getPath();
    logger.info("Generating {} client from spec: {}", getGeneratorName(), specPath);

    org.openapitools.codegen.config.CodegenConfigurator configurator =
        new org.openapitools.codegen.config.CodegenConfigurator()
            .setGeneratorName(getGeneratorName())
            .setInputSpec(specPath)
            .setOutputDir(outputDir.toString().replace("\\", "/"))
            .setAdditionalProperties(additionalProperties);

    org.openapitools.codegen.DefaultGenerator generator =
        new org.openapitools.codegen.DefaultGenerator();
    generator.setGenerateMetadata(false);
    generator.opts(configurator.toClientOptInput()).generate();

    logger.info("Code generation complete.");
  }
}
