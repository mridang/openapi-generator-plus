package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated PHP code passes PHPStan at level 9 over the SDK and its Pest tests (the
 * generated phpstan.neon loads the peststan extension), and that every class autoloads under
 * strict PSR-4, one class per file. If this test fails, the PHP templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-php")
public class PhpStaticAnalysisSpec extends AbstractIntegrationSpec implements PhpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "vendor/bin/phpstan analyse --no-progress --memory-limit=-1",
      "composer dump-autoload --optimize --strict-psr"
    };
  }

  @Test
  void generatedCodeShouldPassStaticAnalysis() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated PHP code has static analysis errors:\n%s", result.output())
        .isTrue();
  }
}
