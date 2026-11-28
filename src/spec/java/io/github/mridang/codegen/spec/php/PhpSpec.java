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
        /* brotli and zstd are the optional extensions the SDK decodes those
         * Content-Encodings with; installing them lets the brotli and zstd
         * decompression tests run instead of skipping. `yes ''` accepts the
         * default (bundled library) answer to each extension's prompt. */
        "for i in 1 2 3 4 5; do apk add --no-cache $PHPIZE_DEPS"
            + " && (yes '' | pecl install pcov brotli zstd)"
            + " && docker-php-ext-enable pcov brotli zstd && break;"
            + " echo \"pecl setup attempt $i failed; retrying\" >&2; sleep 3; done;"
            + " for ext in pcov brotli zstd; do php -m | grep -qi \"^$ext$\""
            + " || { echo \"$ext missing after retries\" >&2; exit 1; }; done",
        /* The docs spec runs phpDocumentor, pinned to v3.10.0 and checked against the SHA-256
         * digest GitHub publishes for that release asset. It is fetched once into the cache
         * volume (/root/.cache/php survives runs), so the docs target never downloads at test
         * time. A per-process temporary name keeps parallel specs from racing on one file. A
         * failed fetch only warns: the docs spec then fails at its copy step, and the other php
         * specs keep running. */
        "mkdir -p /root/.cache/php/phpdoc && { test -f /root/.cache/php/phpdoc/phpDocumentor.phar"
            + " || { f=/root/.cache/php/phpdoc/phpDocumentor.phar.$$.part"
            + " && curl -fsSL --retry 3 -o $f"
            + " https://github.com/phpDocumentor/phpDocumentor/releases/download/v3.10.0/phpDocumentor.phar"
            + " && echo \"fe1e7c23ba3329aa6f19ac3c807446159a431a195ec5d9163b0c281a15105207  $f\" | sha256sum -c - >/dev/null"
            + " && mv $f /root/.cache/php/phpdoc/phpDocumentor.phar || { rm -f $f; false; }; }; }"
            + " || echo 'phpDocumentor 3.10.0 could not be fetched; the docs spec will fail' >&2",
        "COMPOSER_PROCESS_TIMEOUT=600 composer install --no-interaction --prefer-dist");
  }

  @Override
  default Map<String, String> getCacheEnv() {
    /* COMPOSER_HOME owns composer config + the package cache; vendor/
     * stays project-local (no env-var redirect for it). */
    return Map.of("COMPOSER_HOME", "/root/.cache/php/composer");
  }
}
