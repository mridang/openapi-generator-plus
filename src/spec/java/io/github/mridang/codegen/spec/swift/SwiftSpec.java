package io.github.mridang.codegen.spec.swift;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.List;
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
}
