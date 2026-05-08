package io.github.mridang.codegen.spec.php;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.testcontainers.utility.DockerImageName;

@Tag("php")
interface PhpSpec extends LanguageSpec, DockerImageSpec {

  @Override
  default String getGeneratorName() {
    return "php-plus";
  }

  @Override
  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("composer:2");
  }

  @Override
  default String getDockerImage() {
    return "php:8.3-cli";
  }

  @Override
  default Map<String, Object> getCodegenProperties() {
    return Map.of("invokerPackage", "PetstoreClient");
  }
}
