package io.github.mridang.codegen.spec.python;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("python")
interface PythonSpec extends LanguageSpec, DockerImageSpec {

  @Override
  default String getGeneratorName() {
    return "python-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("python:3-slim");
  }

  @Override
  default String getDockerImage() {
    return "python:3-slim";
  }

  @Override
  default Map<String, Object> getCodegenProperties() {
    return Map.of("packageName", "petstore_client", "projectName", "petstore-client");
  }
}
