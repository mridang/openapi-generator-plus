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
    /* SwiftPM has no env var that relocates `.build/` (the compile-output
     * dir that swift test/build produce inside /work and the inter-spec
     * wipe destroys). SwiftPM's small global manifest cache at
     * ~/.cache/org.swift.swiftpm is already preserved by the per-container
     * /root persistence. Routing .build/ would need each Swift command to
     * grow a `--build-path /root/...` CLI flag — left for a follow-up. */
    return Map.of();
  }
}
