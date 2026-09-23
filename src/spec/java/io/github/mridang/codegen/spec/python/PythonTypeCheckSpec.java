package io.github.mridang.codegen.spec.python;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated Python code, sources and tests, passes the mypy and ty type
 * checkers a real client runs.
 * If this test fails, the Python templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-python")
public class PythonTypeCheckSpec extends AbstractIntegrationSpec implements PythonSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"mypy petstore_client/ test/", "ty check"};
  }

  @Test
  void generatedCodeShouldPassTypeChecking() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Python code has type checking errors:\n%s", result.output())
        .isTrue();
  }
}
