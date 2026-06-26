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
    return DockerImageName.parse(
        "composer:2@sha256:1b73755de4f19775ba6087fd5313664493e06fab72b6fc27dc2044e87bb7c4c3");
  }

  @Override
  default String getDockerImage() {
    return "php:8.5-cli@sha256:f7476cffd8d6c48daa07fd80a58b85f97da095ad5a03fcf3361fd872122e3d91";
  }

  @Override
  default Map<String, Object> getCodegenProperties() {
    return Map.of("invokerPackage", "PetstoreClient");
  }

  @Override
  default List<String> getSetupCommands() {
    return List.of(
        /* The runtime image (getRuntimeImage = composer:2) is Alpine, so apk
         * is correct here. Output is intentionally NOT redirected to /dev/null
         * so an install failure surfaces in the logs (the framework already
         * fails the spec on a non-zero setup exit, but the prior `> /dev/null
         * 2>&1` hid the diagnostics that would explain why). pcov is the
         * coverage driver for the cobertura report configured in phpunit.xml. */
        /* pecl/apk fetch over the network, which is flaky on CI runners; retry
         * a few times before giving up so a single transient download failure
         * doesn't fail every php spec sharing this container. */
        "for i in 1 2 3 4 5; do apk add --no-cache $PHPIZE_DEPS"
            + " && pecl install pcov && docker-php-ext-enable pcov && break;"
            + " echo \"pcov setup attempt $i failed; retrying\" >&2; sleep 3; done;"
            + " php -m | grep -qi pcov || { echo 'pcov missing after retries' >&2; exit 1; }",
        "COMPOSER_PROCESS_TIMEOUT=600 composer install --no-interaction --prefer-dist");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* COMPOSER_HOME owns composer config + the package cache; vendor/
     * stays project-local (no env-var redirect for it). */
    return Map.of("COMPOSER_HOME", "/root/.cache/php/composer");
  }
}
