package io.github.mridang.codegen.spec.node;

import io.github.mridang.codegen.spec.LanguageSpec;
import org.testcontainers.utility.DockerImageName;

interface NodeSpec extends LanguageSpec {

  default String getGeneratorName() {
    return "node-plus";
  }

  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("node:24-slim");
  }
}
