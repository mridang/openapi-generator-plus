package io.github.mridang.codegen.spec.swift;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("swift")
interface SwiftSpec extends LanguageSpec, DockerImageSpec {

  @Override
  default String getGeneratorName() {
    return "swift-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse(
        "swift:6.2@sha256:4e50a9e711e8682a8c42bacfeed204568adfd6985a63b3789a165f28d296a28a");
  }

  @Override
  default String getDockerImage() {
    return "swift:6.2@sha256:4e50a9e711e8682a8c42bacfeed204568adfd6985a63b3789a165f28d296a28a";
  }

  @Override
  default List<String> getSetupCommands() {
    return List.of("swift package resolve");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* SWIFTPM_BUILD_DIR — undocumented in `swift build --help` (SwiftPM's
     * CLI help omits any ENVIRONMENT section entirely) but read by
     * SwiftPM's CommandLineToolApiSupport when no --scratch-path flag
     * is given. Relocates the entire .build/ tree (debug, repositories,
     * checkouts, ModuleCache, Modules) so all three Swift specs (Build,
     * Client, Linting) reuse the same artifacts within a run.
     *
     * Pointed at /tmp rather than /root/.cache: the .build/ tree mixes
     * deps (checkouts/repositories) with compiled output and is ~772MB.
     * SwiftPM has no separate deps-only env var, so we keep the whole
     * tree ephemeral — shared across specs in the long-lived container
     * (only /work is wiped between specs), but not persisted between CI
     * runs. Swift re-resolves + recompiles each run. */
    return Map.of("SWIFTPM_BUILD_DIR", "/tmp/build/swift/build");
  }
}
