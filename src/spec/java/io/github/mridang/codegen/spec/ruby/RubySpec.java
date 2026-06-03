package io.github.mridang.codegen.spec.ruby;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("ruby")
interface RubySpec extends LanguageSpec, DockerImageSpec {

  @Override
  default String getGeneratorName() {
    return "ruby-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("ruby:3.4-alpine");
  }

  @Override
  default String getDockerImage() {
    return "ruby:3.4-alpine";
  }

  @Override
  default Map<String, Object> getCodegenProperties() {
    return Map.of("gemName", "petstore_client", "moduleName", "PetstoreClient");
  }

  @Override
  default List<String> getSetupCommands() {
    return List.of(
        "apk add --no-cache build-base > /dev/null 2>&1",
        "bundle install --quiet");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* No custom BUNDLE_PATH: gems install into the default GEM_HOME so
     * bigdecimal resolves to the image's precompiled default gem (3.1.8)
     * rather than recompiling from source. BUNDLE_WITHOUT skips the
     * :optional group (brotli/zstd-ruby) — C extensions that do not build
     * on musl. The native gems pulled by the dev toolchain (rbs/prism/
     * strscan via steep) compile via build-base at setup. */
    return Map.of("BUNDLE_WITHOUT", "optional");
  }
}
