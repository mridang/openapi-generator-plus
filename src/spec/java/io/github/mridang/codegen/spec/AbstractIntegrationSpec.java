package io.github.mridang.codegen.spec;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
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

  private static final java.util.Set<String> EXCLUDED_DIRS =
      java.util.Set.of("certs", "proxy", "specs", "wiremock");

  /**
   * Syncs the contents of the temp output directory to the persistent generated directory
   * at src/spec/resources/generated/{lang}/. This makes generated code and coverage reports
   * available for inspection and committing. Test fixture directories (certs, proxy, specs,
   * wiremock) are excluded since they are copies of shared classpath resources.
   */
  protected void syncToGeneratedDir() {
    String lang = getGeneratorName().replace("-plus", "");
    Path generatedDir = Path.of("src/spec/resources/generated/" + lang).toAbsolutePath();

    try {
      Files.createDirectories(generatedDir);
      Files.walkFileTree(tempOutputDir, new SimpleFileVisitor<>() {
        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
            throws IOException {
          Path relative = tempOutputDir.relativize(dir);
          if (EXCLUDED_DIRS.contains(relative.toString())) {
            return FileVisitResult.SKIP_SUBTREE;
          }
          Files.createDirectories(generatedDir.resolve(relative));
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
          Path relative = tempOutputDir.relativize(file);
          Files.copy(file, generatedDir.resolve(relative), StandardCopyOption.REPLACE_EXISTING);
          return FileVisitResult.CONTINUE;
        }
      });
      logger.info("Synced output to {}", generatedDir);
    } catch (IOException e) {
      logger.warn("Failed to sync to generated dir: {}", e.getMessage());
    }
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

      // Copy output artifacts (coverage reports, etc.) back to the bind mount
      runtimeContainer.execInContainer("sh", "-c", "cp -a /work/.out /app/.out 2>/dev/null || true");

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
