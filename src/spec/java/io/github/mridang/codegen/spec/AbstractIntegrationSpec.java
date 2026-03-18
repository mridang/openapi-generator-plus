package io.github.mridang.codegen.spec;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
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

import javax.annotation.Nullable;

@SuppressWarnings("NullAway.Init")
public abstract class AbstractIntegrationSpec implements LanguageSpec {

  protected static final Logger logger = LoggerFactory.getLogger(AbstractIntegrationSpec.class);

  @Nullable protected Network sharedNetwork;

  @TempDir protected Path tempOutputDir;

  @SuppressWarnings("SameReturnValue")
  protected String getSpecResourcePath() {
    return "specs/petstore/openapi.yaml";
  }

  protected abstract String[] getBuildCommands();

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
    logger.info("Spec: {}", getSpecResourcePath());
    logger.info("Output directory: {}", tempOutputDir.toAbsolutePath());
    logger.info("========================================");
  }

  protected void generateClientToDirectory(
      Map<String, Object> additionalProperties, Path outputDir) {
    URL specUrl = getClass().getClassLoader().getResource(getSpecResourcePath());
    if (specUrl == null) {
      throw new IllegalStateException("Could not find spec resource: " + getSpecResourcePath());
    }

    String specPath = specUrl.getPath();
    logger.info("Generating {} client from spec: {}", getGeneratorName(), specPath);

    CodegenConfigurator configurator =
        new CodegenConfigurator()
            .setGeneratorName(getGeneratorName())
            .setInputSpec(specPath)
            .setOutputDir(outputDir.toString().replace("\\", "/"))
            .setAdditionalProperties(additionalProperties);

    DefaultGenerator generator = new DefaultGenerator();
    generator.setGenerateMetadata(false);
    generator.opts(configurator.toClientOptInput()).generate();

    logger.info("Code generation complete.");
  }

  protected void copyClasspathResource(String resourcePath, Path targetPath) throws IOException {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new IllegalStateException("Could not find classpath resource: " + resourcePath);
      }
      Files.createDirectories(targetPath.getParent());
      Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
      logger.info("Copied classpath resource {} to {}", resourcePath, targetPath);
    }
  }

  protected static void copyDirectory(Path source, Path target) throws IOException {
    try (Stream<Path> stream = Files.walk(source)) {
      stream.forEach(
          sourcePath -> {
            try {
              Path targetPath = target.resolve(source.relativize(sourcePath));
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
    logger.info("Copied directory from {} to {}", source, target);
  }

  protected ExecResult executeInRuntimeContainer(String[] commands) {
    try (GenericContainer<?> runtimeContainer =
        new GenericContainer<>(getRuntimeImage())
            .withNetwork(sharedNetwork)
            .withFileSystemBind(
                tempOutputDir.toAbsolutePath().toString(), "/app", BindMode.READ_WRITE)
            .withFileSystemBind("/var/run/docker.sock", "/var/run/docker.sock", BindMode.READ_WRITE)
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

      // Fix file permissions before container exits so JUnit can clean up @TempDir
      // Docker containers run as root and create files owned by root, which the
      // host user cannot delete. This makes all files world-writable.
      runtimeContainer.execInContainer("sh", "-c", "chmod -R 777 /app || true");

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

    public String output() {
      return output;
    }

    public boolean isSuccess() {
      return exitCode == 0;
    }
  }
}
