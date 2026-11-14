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
    return Map.of(
        "gemName", "petstore_client",
        "moduleName", "Petstore::Client",
        "clientClassName", "Petstore");
  }

  @Override
  default List<String> getSetupCommands() {
    /* The compiler toolchain without build-base: build-base also pulls in
     * fortify-headers, whose realpath wrapper aborts the zstd-ruby build on
     * musl ("PATH_MAX unset"). gcc, g++, make, musl-dev and linux-headers
     * build every native gem, the dev toolchain's (rbs/prism/strscan via
     * steep) and the :optional codec group's (brotli, zstd-ruby), so the
     * br/zstd decode tests run instead of skipping. */
    return List.of(
        "apk add --no-cache gcc g++ make musl-dev linux-headers > /dev/null 2>&1",
        "bundle install --quiet");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* No custom BUNDLE_PATH: gems install into the default GEM_HOME so
     * bigdecimal resolves to the image's precompiled default gem (3.1.8)
     * rather than recompiling from source. No BUNDLE_WITHOUT: the :optional
     * codec group is installed as well. */
    return Map.of();
  }
}
