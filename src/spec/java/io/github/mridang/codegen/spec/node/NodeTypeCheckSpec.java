package io.github.mridang.codegen.spec.node;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated TypeScript code passes tsc type checking with strict mode.
 * If this test fails, the Node templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class NodeTypeCheckSpec extends AbstractIntegrationSpec implements NodeSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "npm install",
      "npx tsc --noEmit"
    };
  }

  @Test
  void generatedCodeShouldPassTypeChecking() {
    generateClientToDirectory(Map.of(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated TypeScript code has type checking errors:\n%s", result.output())
        .isTrue();
  }

}
