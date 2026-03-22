package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated PHP code is already modern according to Rector. Rector in --dry-run mode
 * exits 0 if no changes are needed (code is already modern). If this test fails, the PHP templates
 * need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-php")
public class PhpModernizationSpec extends AbstractIntegrationSpec implements PhpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "COMPOSER_PROCESS_TIMEOUT=600 composer install --no-interaction --prefer-dist",
      "vendor/bin/rector process --dry-run"
    };
  }

  @Test
  void generatedCodeShouldBeModern() {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated PHP code has modernization suggestions:\n%s", result.output())
        .isTrue();
  }
}
