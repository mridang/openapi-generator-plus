package io.github.mridang.codegen.spec.node;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated TypeScript code passes ESLint linting with recommended rules. If this
 * test fails, the Node templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-node")
public class NodeLintingSpec extends AbstractIntegrationSpec implements NodeSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"npm install", "npx eslint ."};
  }

  @Test
  void generatedCodeShouldPassLinting() {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated TypeScript code has linting violations:\n%s", result.output())
        .isTrue();
  }
}
