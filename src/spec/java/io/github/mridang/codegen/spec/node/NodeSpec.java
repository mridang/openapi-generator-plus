package io.github.mridang.codegen.spec.node;

import io.github.mridang.codegen.spec.LanguageSpec;
import org.testcontainers.utility.DockerImageName;

interface NodeSpec extends LanguageSpec {

  @Override
  default String getGeneratorName() {
    return "node-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("node:24-slim");
  }
}
