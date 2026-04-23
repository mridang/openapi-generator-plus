package io.github.mridang.codegen.spec.ruby;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.Map;
import org.testcontainers.utility.DockerImageName;

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
}
