package io.github.mridang.codegen.spec.dart;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("dart")
interface DartSpec extends LanguageSpec, DockerImageSpec {

  @Override
  default String getGeneratorName() {
    return "dart-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("dart:stable");
  }

  @Override
  default String getDockerImage() {
    return "dart:stable";
  }

  @Override
  default List<String> getSetupCommands() {
    return List.of("dart pub get");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* PUB_CACHE owns the resolved package sources. Project .dart_tool/
     * stays local (no env var for it). */
    return Map.of("PUB_CACHE", "/root/.cache/dart/pub");
  }
}
