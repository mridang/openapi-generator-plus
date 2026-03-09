package io.github.mridang.codegen.spec.python;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import org.testcontainers.utility.DockerImageName;

interface PythonSpec extends LanguageSpec, DockerImageSpec {

  default String getGeneratorName() {
    return "python-plus";
  }

  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("python:3-slim");
  }

  default String getDockerImage() {
    return "python:3-slim";
  }
}
