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
    return DockerImageName.parse("swift:6.2");
  }

  @Override
  default String getDockerImage() {
    return "swift:6.2";
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
     * checkouts, ModuleCache, Modules) outside /work so all three Swift
     * specs (Build, Client, Linting) reuse the same compile artifacts.
     * Verified empirically against swift:6.2 image. */
    return Map.of("SWIFTPM_BUILD_DIR", "/root/.cache/swift/build");
  }
}
