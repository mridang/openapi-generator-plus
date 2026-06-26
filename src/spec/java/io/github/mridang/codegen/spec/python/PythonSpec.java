package io.github.mridang.codegen.spec.python;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.List;
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
    return DockerImageName.parse("python:3-alpine");
  }

  @Override
  default String getDockerImage() {
    return "python:3-alpine";
  }

  @Override
  default Map<String, Object> getCodegenProperties() {
    return Map.of("packageName", "petstore_client", "projectName", "petstore-client");
  }

  @Override
  default List<String> getSetupCommands() {
    return List.of("pip install --quiet -e . --group dev");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* PIP_CACHE_DIR redirects the wheel cache. No compile artifacts to
     * cache for Python. */
    return Map.of("PIP_CACHE_DIR", "/root/.cache/python/pip");
  }
}
