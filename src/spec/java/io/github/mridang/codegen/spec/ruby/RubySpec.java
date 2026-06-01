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
    return DockerImageName.parse("ruby:3.4");
  }

  @Override
  default String getDockerImage() {
    return "ruby:3.4-slim";
  }

  @Override
  default Map<String, Object> getCodegenProperties() {
    return Map.of("gemName", "petstore_client", "moduleName", "PetstoreClient");
  }

  @Override
  default List<String> getSetupCommands() {
    return List.of("bundle install --quiet");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* BUNDLE_PATH redirects gem installation away from /work — gems are
     * interpreted so this is the only cache that matters. */
    return Map.of("BUNDLE_PATH", "/root/.cache/ruby/bundle");
  }
}
