package io.github.mridang.codegen.spec;

import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import javax.annotation.Nullable;

/**
 * Base class for integration specs. Each generated SDK lives in
 * {@code src/spec/resources/generated/{lang}/} and is already fully self-contained
 * (source, tests, wiremock mappings, certs, specs). Code generation is done once
 * via {@code GenerateClientsTest}; this class only runs tests against the committed code.
 */
@SuppressWarnings("NullAway.Init")
public abstract class AbstractIntegrationSpec implements LanguageSpec {

  protected static final Logger logger = LoggerFactory.getLogger(AbstractIntegrationSpec.class);

  @Nullable protected Network sharedNetwork;

  protected Path tempOutputDir;

  protected abstract String[] getBuildCommands();

  @BeforeEach
  void resolveOutputDir() {
    String lang = getGeneratorName().replace("-plus", "");
    tempOutputDir = Path.of("src/spec/resources/generated/" + lang).toAbsolutePath();
  }

  @BeforeEach
  void setupNetwork() {
    sharedNetwork = Network.newNetwork();
    logger.info("Created Docker network: {}", sharedNetwork.getId());
  }

  @AfterEach
  void teardownNetwork() {
    if (sharedNetwork != null) {
      sharedNetwork.close();
      logger.info("Closed Docker network");
    }
  }

  @BeforeEach
  void logTestContext(TestInfo testInfo) {
    logger.info("========================================");
    logger.info("Test: {}", testInfo.getDisplayName());
    logger.info("Generator: {}", getGeneratorName());
    logger.info("Output directory: {}", tempOutputDir.toAbsolutePath());
    logger.info("========================================");
  }

  protected ExecResult executeInRuntimeContainer(String[] commands) {
    try (GenericContainer<?> runtimeContainer =
        new GenericContainer<>(getRuntimeImage())
            .withNetwork(sharedNetwork)
            .withFileSystemBind(
                tempOutputDir.toAbsolutePath().toString(), "/app", BindMode.READ_WRITE)
            .withFileSystemBind("/var/run/docker.sock", "/var/run/docker.sock", BindMode.READ_WRITE)
            .withExtraHost("host.docker.internal", "host-gateway")
            .withEnv("TESTCONTAINERS_HOST_OVERRIDE", "host.docker.internal")
            .withEnv("TC_HOST", "host.docker.internal")
            .withEnv("DOCKER_HOST", "unix:///var/run/docker.sock")
            .withEnv("TESTCONTAINERS_RYUK_DISABLED", "true")
            .withEnv("HOST_APP_PATH", tempOutputDir.toAbsolutePath().toString())
            .withWorkingDirectory("/app")
            .withCommand("tail", "-f", "/dev/null")
            .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix(getGeneratorName()))) {

      runtimeContainer.start();

      logger.info("Runtime container started:");
      logger.info("  - Image: {}", getRuntimeImage());
      logger.info("  - Container ID: {}", runtimeContainer.getContainerId());

      // Copy bind-mounted files to container-local storage to avoid file corruption
      // caused by heavy parallel I/O on macOS Docker bind mounts (VirtioFS/gRPC-FUSE).
      // Package managers (npm, composer, mvn) write thousands of small files during
      // install, and concurrent containers writing to bind mounts causes truncated or
      // corrupted files. Running in /work avoids this entirely.
      runtimeContainer.execInContainer("sh", "-c", "cp -a /app /work");

      StringBuilder output = new StringBuilder();
      int exitCode = 0;

      for (String command : commands) {
        logger.info("Executing: {}", command);

        org.testcontainers.containers.Container.ExecResult result =
            runtimeContainer.execInContainer("sh", "-c", "cd /work && " + command);

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

      // Copy coverage.xml and JUnit XML reports back to the bind mount
      runtimeContainer.execInContainer("sh", "-c",
          "mkdir -p /app/.out/reports && "
              + "cp /work/.out/coverage.xml /app/.out/coverage.xml 2>/dev/null || true; "
              + "cp /work/.out/reports/*.xml /app/.out/reports/ 2>/dev/null || true");

      // Fix permissions on the .out directory so subsequent runs can overwrite
      runtimeContainer.execInContainer("sh", "-c", "chmod -R 777 /app/.out 2>/dev/null || true");

      return new ExecResult(exitCode, output.toString());
    } catch (Exception e) {
      logger.error("Failed to execute commands in runtime container", e);
      String message = e.getMessage();
      return new ExecResult(-1, message != null ? message : e.getClass().getName());
    }
  }

  public static class ExecResult {
    private final int exitCode;
    private final String output;

    public ExecResult(int exitCode, String output) {
      this.exitCode = exitCode;
      this.output = output;
    }

    public String output() {
      return output;
    }

    public boolean isSuccess() {
      return exitCode == 0;
    }
  }
}
