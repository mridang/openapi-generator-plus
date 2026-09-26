package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated PHP code is already modern according to Rector, run with the generated
 * rector.php over the SDK and its tests exactly as a client's `make format` runs it. The generator
 * runs `rector process` when it formats the output, so a dry run must find nothing to change.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-php")
public class PhpModernizationSpec extends AbstractIntegrationSpec implements PhpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"vendor/bin/rector process --dry-run --no-progress-bar"};
  }

  @Test
  void generatedCodeShouldBeModern() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated PHP code has modernization suggestions:\n%s", result.output())
        .isTrue();
  }
}
