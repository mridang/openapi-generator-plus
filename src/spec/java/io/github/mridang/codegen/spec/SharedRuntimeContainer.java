package io.github.mridang.codegen.spec;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

  /* Docker resolves a multi-arch tag against the daemon's default
   * platform, but a tag that is ALREADY in the local image store is
   * reused as-is — even when the only materialised variant is for a
   * foreign architecture. On an Apple Silicon host that silently runs
   * the amd64 variant under emulation, where `rustc -vV` dies with
   * SIGSEGV before it compiles a single line. Naming the platform
   * explicitly turns that into a loud "no matching manifest" failure
   * at container-create time instead of an inscrutable segfault. */
  private static final String HOST_PLATFORM = hostPlatform();

  /* Docker Desktop on macOS exposes host bind mounts over virtiofs,
   * which does not give back a file that was just written: cargo
   * creates target/debug/.fingerprint/<crate>/invoked.timestamp and
   * immediately stats it, and the stat returns ENOENT. Every rust
   * spec dies with "failed to load metadata for path ...
   * invoked.timestamp: No such file or directory (os error 2)". A
   * named volume lives in the Linux VM's own ext4, so it has normal
   * POSIX semantics. CI runs on Linux, where the bind mount is native
   * (and is what action-runner-common's generic-cache restores), so
   * the host path is kept there. */
  private static final boolean CACHE_ON_HOST_PATH =
      !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

  private static String hostPlatform() {
    String arch = System.getProperty("os.arch", "");
    if ("aarch64".equals(arch) || "arm64".equals(arch)) {
      return "linux/arm64";
    }
    if ("amd64".equals(arch) || "x86_64".equals(arch)) {
      return "linux/amd64";
    }
    /* Unrecognised arch: leave the platform unset and let Docker pick. */
    return "";
  }

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

          /* Host-side cache root. On GitHub Actions runners the workflow
           * uses mridang/action-runner-common@v1 with generic-cache: true,
           * which persists $HOME/.cache across CI runs. Placing the
           * per-language cache trees under that path means cargo / gradle
           * / mix / etc. start each new CI job with warm registries +
           * build caches restored by the previous run. Local dev gets the
           * same dir under the developer's HOME — also a one-time cold
           * compile, hot thereafter. */
          String language = name.replace("-plus", "");
          String hostCacheRoot =
              System.getProperty("user.home") + "/.cache/openapi-gen/" + language;
          if (CACHE_ON_HOST_PATH) {
            try {
              java.nio.file.Files.createDirectories(java.nio.file.Path.of(hostCacheRoot));
            } catch (java.io.IOException e) {
              throw new RuntimeException(
                  "Failed to create host cache dir " + hostCacheRoot + " for " + name, e);
            }
          }

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

          /* Persist the toolchain caches outside /work so they survive
           * the inter-spec wipe AND (when running under
           * action-runner-common's generic-cache) the inter-CI-job
           * runner teardown. The container's /root/.cache is the path
           * the per-language Spec interfaces hard-code in
           * getCacheEnv(), so CARGO_HOME=/root/.cache/rust/cargo,
           * GRADLE_USER_HOME=/root/.cache/kotlin/gradle, etc. all land
           * here. On Linux that is the host's HOME/.cache (what CI
           * restores); on macOS it is a named volume, because virtiofs
           * cannot serve back a file cargo has just written. */
          String containerCacheRoot = "/root/.cache/" + language;
          if (CACHE_ON_HOST_PATH) {
            container.withFileSystemBind(hostCacheRoot, containerCacheRoot, BindMode.READ_WRITE);
          } else {
            Bind cacheBind =
                Bind.parse("openapi-gen-cache-" + language + ":" + containerCacheRoot);
            container.withCreateContainerCmdModifier(
                cmd -> {
                  HostConfig hostConfig = cmd.getHostConfig();
                  Bind[] existing =
                      hostConfig == null || hostConfig.getBinds() == null
                          ? new Bind[0]
                          : hostConfig.getBinds();
                  Bind[] merged = Arrays.copyOf(existing, existing.length + 1);
                  merged[existing.length] = cacheBind;
                  cmd.withHostConfig(
                      (hostConfig == null ? HostConfig.newHostConfig() : hostConfig)
                          .withBinds(merged));
                });
          }

          if (!HOST_PLATFORM.isEmpty()) {
            container.withCreateContainerCmdModifier(cmd -> cmd.withPlatform(HOST_PLATFORM));
          }

          /* Per-language env vars hand the toolchain the cache paths
           * inside the container. Combined with the cache mount above,
           * each cargo/gradle/mix write lands in the persistent store. */
          spec.getCacheEnv().forEach(container::withEnv);

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
