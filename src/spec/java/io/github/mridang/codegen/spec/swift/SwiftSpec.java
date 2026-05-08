package io.github.mridang.codegen.spec.swift;

import io.github.mridang.codegen.spec.LanguageSpec;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("swift")
interface SwiftSpec extends LanguageSpec {

  @Override
  default String getGeneratorName() {
    return "swift-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("swift:6.1");
  }
}
