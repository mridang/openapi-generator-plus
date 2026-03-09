package io.github.mridang.codegen.spec.php;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import org.testcontainers.utility.DockerImageName;

interface PhpSpec extends LanguageSpec, DockerImageSpec {

  default String getGeneratorName() {
    return "php-plus";
  }

  default DockerImageName getRuntimeImage() {
    return DockerImageName.parse("composer:2");
  }

  default String getDockerImage() {
    return "php:8.3-cli";
  }
}
