package io.github.mridang.codegen.spec.php;

import io.github.mridang.codegen.spec.DockerImageSpec;
import io.github.mridang.codegen.spec.LanguageSpec;
import java.util.List;
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
    return "php:8.5-cli";
  }

  @Override
  default Map<String, Object> getCodegenProperties() {
    return Map.of("invokerPackage", "PetstoreClient");
  }

  @Override
  default List<String> getSetupCommands() {
    return List.of(
        "apk add --no-cache $PHPIZE_DEPS > /dev/null 2>&1 && pecl install pcov > /dev/null 2>&1 && docker-php-ext-enable pcov",
        "COMPOSER_PROCESS_TIMEOUT=600 composer install --no-interaction --prefer-dist");
  }
}
