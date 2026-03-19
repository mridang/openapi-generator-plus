package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated PHP code passes PHPStan static analysis at level 9. If this test fails,
 * the PHP templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class PhpStaticAnalysisSpec extends AbstractIntegrationSpec implements PhpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "COMPOSER_PROCESS_TIMEOUT=600 composer install --no-interaction --prefer-dist",
      "vendor/bin/phpstan analyse --no-progress"
    };
  }

  @Test
  void generatedCodeShouldPassStaticAnalysis() {
    generateClientToDirectory(
        Map.of(CodegenConstants.INVOKER_PACKAGE, "PetstoreClient"), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated PHP code has static analysis errors:\n%s", result.output())
        .isTrue();
  }
}
