package io.github.mridang.codegen.spec;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;
import org.openapitools.codegen.DefaultGenerator;
import org.openapitools.codegen.config.CodegenConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import javax.annotation.Nullable;

@SuppressWarnings("NullAway.Init")
public abstract class AbstractIntegrationSpec {

  protected static final Logger logger = LoggerFactory.getLogger(AbstractIntegrationSpec.class);

  @Nullable protected static Network sharedNetwork;

  @Nullable protected GenericContainer<?> prismContainer;

  @TempDir protected Path tempOutputDir;

  protected String getSpecResourcePath() {
    return "specs/petstore/openapi.yaml";
  }

  protected abstract String getGeneratorName();

  protected abstract DockerImageName getRuntimeImage();

  protected abstract String[] getBuildCommands();

  protected abstract String getTestScript(String prismBaseUrl);

  @BeforeAll
  static void setupSharedInfrastructure() {
    sharedNetwork = Network.newNetwork();
    logger.info("Created shared Docker network: {}", sharedNetwork.getId());
  }

  @BeforeEach
  void logTestContext(TestInfo testInfo) {
    logger.info("========================================");
    logger.info("Test: {}", testInfo.getDisplayName());
    logger.info("Generator: {}", getGeneratorName());
    logger.info("Spec: {}", getSpecResourcePath());
    logger.info("Output directory: {}", tempOutputDir.toAbsolutePath());
    logger.info("========================================");
  }

  @AfterEach
  void cleanup() {
    if (prismContainer != null && prismContainer.isRunning()) {
      prismContainer.stop();
      logger.info("Stopped Prism container");
    }
  }

  protected String startPrismServer() {
    URL specUrl = getClass().getClassLoader().getResource(getSpecResourcePath());
    if (specUrl == null) {
      throw new IllegalStateException("Could not find spec resource: " + getSpecResourcePath());
    }

    logger.info("Starting Prism mock server with spec: {}", specUrl);

    prismContainer =
        new GenericContainer<>(DockerImageName.parse("stoplight/prism:5"))
            .withNetwork(sharedNetwork)
            .withNetworkAliases("prism")
            .withExposedPorts(4010)
            .withCopyFileToContainer(MountableFile.forClasspathResource(getSpecResourcePath()), "/tmp/openapi.yaml")
            .withCommand("mock", "-h", "0.0.0.0", "/tmp/openapi.yaml")
            .waitingFor(Wait.forListeningPort())
            .withStartupTimeout(Duration.ofSeconds(60))
            .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix("prism"));

    prismContainer.start();

    String hostBaseUrl =
        String.format("http://%s:%d", prismContainer.getHost(), prismContainer.getMappedPort(4010));
    String networkBaseUrl = "http://prism:4010";

    logger.info("Prism mock server started:");
    logger.info("  - Host URL: {}", hostBaseUrl);
    logger.info("  - Network URL: {}", networkBaseUrl);
    logger.info("  - Container ID: {}", prismContainer.getContainerId());

    return networkBaseUrl;
  }

  protected void generateClient(Map<String, Object> additionalProperties) {
    URL specUrl = getClass().getClassLoader().getResource(getSpecResourcePath());
    if (specUrl == null) {
      throw new IllegalStateException("Could not find spec resource: " + getSpecResourcePath());
    }

    String specPath = specUrl.getPath();

    logger.info("Generating {} client from spec: {}", getGeneratorName(), specPath);
    logger.info("Output directory: {}", tempOutputDir);

    CodegenConfigurator configurator =
        new CodegenConfigurator()
            .setGeneratorName(getGeneratorName())
            .setInputSpec(specPath)
            .setOutputDir(tempOutputDir.toString().replace("\\", "/"))
            .setAdditionalProperties(additionalProperties);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGenerateMetadata(false);
    generator.opts(configurator.toClientOptInput()).generate();

    logger.info("Code generation complete. Files in output directory:");
    logDirectoryContents(tempOutputDir, 0);
  }

  protected ExecResult executeInRuntimeContainer(String[] commands) {
    try (GenericContainer<?> runtimeContainer =
        new GenericContainer<>(getRuntimeImage())
            .withNetwork(sharedNetwork)
            .withFileSystemBind(
                tempOutputDir.toAbsolutePath().toString(), "/app", BindMode.READ_WRITE)
            .withWorkingDirectory("/app")
            .withCommand("tail", "-f", "/dev/null")
            .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix(getGeneratorName()))) {

      runtimeContainer.start();

      logger.info("Runtime container started:");
      logger.info("  - Image: {}", getRuntimeImage());
      logger.info("  - Container ID: {}", runtimeContainer.getContainerId());

      StringBuilder output = new StringBuilder();
      int exitCode = 0;

      for (String command : commands) {
        logger.info("Executing: {}", command);

        org.testcontainers.containers.Container.ExecResult result =
            runtimeContainer.execInContainer("sh", "-c", command);

        output.append("=== ").append(command).append(" ===\n");
        output.append(result.getStdout());
        if (!result.getStderr().isEmpty()) {
          output.append("STDERR:\n").append(result.getStderr());
        }
        output.append("\n");

        logger.info("Exit code: {}", result.getExitCode());
        if (!result.getStdout().isEmpty()) {
          logger.info("Output:\n{}", result.getStdout());
        }

        if (result.getExitCode() != 0) {
          logger.error("Command failed with stderr:\n{}", result.getStderr());
          exitCode = result.getExitCode();
          break;
        }
      }

      return new ExecResult(exitCode, output.toString());
    } catch (Exception e) {
      logger.error("Failed to execute commands in runtime container", e);
      String message = e.getMessage();
      return new ExecResult(-1, message != null ? message : e.getClass().getName());
    }
  }

  private void logDirectoryContents(Path dir, int depth) {
    if (depth > 3) {
      return;
    }
    try (Stream<Path> paths = Files.list(dir)) {
      paths.forEach(
          path -> {
            String indent = "  ".repeat(depth);
            logger.info("{}{}", indent, path.getFileName());
            if (Files.isDirectory(path)) {
              logDirectoryContents(path, depth + 1);
            }
          });
    } catch (IOException e) {
      logger.warn("Could not list directory: {}", dir);
    }
  }

  public static class ExecResult {
    private final int exitCode;
    private final String output;

    public ExecResult(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = output;
    }

    public int exitCode() {
      return exitCode;
    }

    public String output() {
      return output;
    }

    public boolean isSuccess() {
      return exitCode == 0;
    }
  }
}
