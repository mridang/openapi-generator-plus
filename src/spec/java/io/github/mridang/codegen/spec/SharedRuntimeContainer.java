package io.github.mridang.codegen.spec;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;

/**
 * Maintains a static pool of long-lived Docker containers keyed by generator name. Each container
 * is started once (with dependencies pre-installed via {@link LanguageSpec#getSetupCommands()}), and
 * all test methods for that language reuse it. A JVM shutdown hook tears them all down.
 */
final class SharedRuntimeContainer {

  private static final Logger logger = LoggerFactory.getLogger(SharedRuntimeContainer.class);

  private static final ConcurrentHashMap<String, GenericContainer<?>> pool =
      new ConcurrentHashMap<>();

  private static final Network sharedNetwork = Network.newNetwork();

  static {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  pool.forEach(
                      (name, container) -> {
                        try {
                          container.stop();
                          logger.info("Stopped shared container for {}", name);
                        } catch (Exception e) {
                          logger.warn("Failed to stop container for {}", name, e);
                        }
                      });
                  pool.clear();
                  try {
                    sharedNetwork.close();
                    logger.info("Closed shared Docker network");
                  } catch (Exception e) {
                    logger.warn("Failed to close shared network", e);
                  }
                },
                "shared-runtime-container-cleanup"));
  }

  private SharedRuntimeContainer() {}

  static GenericContainer<?> getOrCreate(LanguageSpec spec, Path outputDir) {
    return pool.computeIfAbsent(
        spec.getGeneratorName(),
        name -> {
          logger.info("Creating shared container for {}", name);

          GenericContainer<?> container =
              new GenericContainer<>(spec.getRuntimeImage())
                  .withNetwork(sharedNetwork)
                  .withFileSystemBind(
                      outputDir.toAbsolutePath().toString(), "/app", BindMode.READ_WRITE)
                  .withFileSystemBind(
                      "/var/run/docker.sock", "/var/run/docker.sock", BindMode.READ_WRITE)
                  .withExtraHost("host.docker.internal", "host-gateway")
                  .withEnv("TESTCONTAINERS_HOST_OVERRIDE", "host.docker.internal")
                  .withEnv("TC_HOST", "host.docker.internal")
                  .withEnv("DOCKER_HOST", "unix:///var/run/docker.sock")
                  .withEnv("HOST_APP_PATH", outputDir.toAbsolutePath().toString())
                  .withLabel("com.mridang.openapi.testcontainer", "true")
                  .withWorkingDirectory("/app")
                  .withCommand("tail", "-f", "/dev/null")
                  .withCreateContainerCmdModifier(cmd -> cmd.withUser("root"))
                  .withLogConsumer(new Slf4jLogConsumer(logger).withPrefix(name));

          container.start();

          logger.info("Shared container started:");
          logger.info("  - Image: {}", spec.getRuntimeImage());
          logger.info("  - Container ID: {}", container.getContainerId());

          try {
            container.execInContainer(
                "sh", "-c", "chmod 666 /var/run/docker.sock 2>/dev/null || true");

            container.execInContainer("sh", "-c", "cp -a /app /work");

            List<String> setupCommands = spec.getSetupCommands();
            for (String command : setupCommands) {
              logger.info("Running setup command for {}: {}", name, command);
              org.testcontainers.containers.Container.ExecResult result =
                  container.execInContainer("sh", "-c", "cd /work && " + command);

              if (!result.getStdout().isEmpty()) {
                logger.info("Setup output:\n{}", result.getStdout());
              }

              if (result.getExitCode() != 0) {
                logger.error(
                    "Setup command failed for {} with exit code {}: {}",
                    name,
                    result.getExitCode(),
                    result.getStderr());
                throw new RuntimeException(
                    "Setup command failed for "
                        + name
                        + ": "
                        + command
                        + "\n"
                        + result.getStderr());
              }
            }
            container.execInContainer("sh", "-c", "cp -a /work /work-snapshot");
          } catch (RuntimeException e) {
            throw e;
          } catch (Exception e) {
            throw new RuntimeException("Failed to set up shared container for " + name, e);
          }

          return container;
        });
  }

  static AbstractIntegrationSpec.ExecResult execInContainer(
      GenericContainer<?> container, Path outputDir, String[] commands) {
    try {
      container.execInContainer("sh", "-c", "rm -rf /work && cp -a /work-snapshot /work");

      StringBuilder output = new StringBuilder();
      int exitCode = 0;

      for (String command : commands) {
        logger.info("Executing: {}", command);

        org.testcontainers.containers.Container.ExecResult result =
            container.execInContainer("sh", "-c", "cd /work && " + command);

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

      container.execInContainer(
          "sh",
          "-c",
          "mkdir -p /app/.out/reports && "
              + "cp /work/.out/coverage.xml /app/.out/coverage.xml 2>/dev/null || true; "
              + "cp /work/.out/reports/*.xml /app/.out/reports/ 2>/dev/null || true");

      container.execInContainer("sh", "-c", "chmod -R 777 /app/.out 2>/dev/null || true");

      return new AbstractIntegrationSpec.ExecResult(exitCode, output.toString());
    } catch (Exception e) {
      logger.error("Failed to execute commands in shared container", e);
      String message = e.getMessage();
      return new AbstractIntegrationSpec.ExecResult(
          -1, message != null ? message : e.getClass().getName());
    }
  }
}
