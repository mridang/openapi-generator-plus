package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractClientSpec;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.testcontainers.junit.jupiter.Testcontainers;

@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class PhpClientSpec extends AbstractClientSpec implements PhpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "apk add --no-cache $PHPIZE_DEPS > /dev/null 2>&1 && pecl install pcov > /dev/null 2>&1 && docker-php-ext-enable pcov",
      "COMPOSER_PROCESS_TIMEOUT=600 composer install --no-interaction --prefer-dist",
      "mkdir -p .out && vendor/bin/phpunit --testdox"
    };
  }

  @Override
  protected Path getTestProjectPath() {
    return Paths.get("src/spec/resources/testprojects/phptest");
  }

  @Override
  protected void assertGeneratedStructure(Path outputDir) {
    assertThat(outputDir.resolve("lib/Api")).exists();
    assertThat(outputDir.resolve("lib/Models")).exists();
  }
}
