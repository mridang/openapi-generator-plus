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
    return DockerImageName.parse("python:3.14-alpine");
  }

  @Override
  default String getDockerImage() {
    return "python:3.14-alpine";
  }

  @Override
  default Map<String, Object> getCodegenProperties() {
    return Map.of("packageName", "petstore_client", "projectName", "petstore-client");
  }

  @Override
  default List<String> getSetupCommands() {
    /* The python image is Alpine and ships without make, which the SDK's own
     * docs target needs. Installed once with the shared container, with
     * retries because the package mirror is flaky on CI runners. */
    return List.of(
        "for i in 1 2 3 4 5; do apk add --no-cache make && break;"
            + " echo \"apk attempt $i failed; retrying\" >&2; sleep 3; done;"
            + " command -v make > /dev/null || { echo \"make missing after retries\" >&2; exit 1; }",
        "pip install --quiet -e . --group dev");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* PIP_CACHE_DIR redirects the wheel cache. No compile artifacts to
     * cache for Python. */
    return Map.of(
        "PIP_CACHE_DIR", "/root/.cache/python/pip",
        /* The type checker and linter keep content-keyed caches, so a warm
         * cache only skips unchanged files and never hides a finding. */
        "MYPY_CACHE_DIR", "/root/.cache/python/mypy",
        "RUFF_CACHE_DIR", "/root/.cache/python/ruff");
  }
}
