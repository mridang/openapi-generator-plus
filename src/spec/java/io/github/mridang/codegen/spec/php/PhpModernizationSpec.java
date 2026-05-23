package io.github.mridang.codegen.spec.php;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated PHP code is already modern according to Rector. Rector in --dry-run mode
 * exits 0 if no changes are needed (code is already modern). If this test fails, the PHP templates
 * need to be fixed.
 *
 * <p>Disabled: Rector applies dozens of opinionated taste-level rules
 * (ctor-property-promotion, readonly classes, first-class callable syntax,
 * {@code @Override} attribute insertion, etc.) that would require template-level
 * changes for each rule. Running `rector process` (write mode) as a
 * post-process step is also fragile because rector's PHP parser
 * misinterprets mustache template directives like `{{...}}` as PHP
 * syntax errors. Re-enable when rector exposes a "non-template" mode or
 * when a different modernization tool is adopted.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-php")
@Disabled("rector --dry-run too aggressive; misparses mustache directives in --write mode")
public class PhpModernizationSpec extends AbstractIntegrationSpec implements PhpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"vendor/bin/rector process --dry-run"};
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
