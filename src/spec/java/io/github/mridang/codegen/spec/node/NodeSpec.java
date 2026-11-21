package io.github.mridang.codegen.spec.node;

import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("node")
interface NodeSpec extends LanguageSpec {

  @Override
  default String getGeneratorName() {
    return "node-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("node:24-alpine");
  }

  @Override
  default List<String> getSetupCommands() {
    /* The Alpine image ships without make, which the SDK's own docs target
     * needs. Installed once with the shared container, with retries because
     * the package mirror is flaky on CI runners. */
    return List.of(
        "npm install",
        "for i in 1 2 3 4 5; do apk add --no-cache make && break;"
            + " echo \"apk attempt $i failed; retrying\" >&2; sleep 3; done;"
            + " command -v make > /dev/null || { echo \"make missing after retries\" >&2; exit 1; }");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* npm_config_cache (lowercase per npm docs) owns the content-addressable
     * package cache. node_modules/ stays project-local. */
    return Map.of("npm_config_cache", "/root/.cache/node/npm");
  }
}
