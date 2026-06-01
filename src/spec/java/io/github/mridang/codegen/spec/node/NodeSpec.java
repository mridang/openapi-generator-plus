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
    return DockerImageName.parse("node:24-slim");
  }

  @Override
  default List<String> getSetupCommands() {
    return List.of("npm install");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* npm_config_cache (lowercase per npm docs) owns the content-addressable
     * package cache. node_modules/ stays project-local. */
    return Map.of("npm_config_cache", "/root/.cache/node/npm");
  }
}
