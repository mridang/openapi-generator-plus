package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated Java code compiles cleanly under Error Prone and NullAway with zero
 * warnings. Uses failOnWarning and -Xlint:all with no package exclusions. If this test fails, the
 * Java templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-java")
public class JavaBuildSpec extends AbstractIntegrationSpec implements JavaSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"mvn compile -B"};
  }

  @Test
  void generatedCodeShouldCompileWithErrorProne() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated Java code has Error Prone/NullAway violations:\n%s", result.output())
        .isTrue();
  }
}
