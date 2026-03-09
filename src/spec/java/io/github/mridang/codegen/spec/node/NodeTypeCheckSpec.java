package io.github.mridang.codegen.spec.node;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated TypeScript code passes tsc type checking with strict mode.
 * If this test fails, the Node templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class NodeTypeCheckSpec extends AbstractIntegrationSpec {

  @Override
  protected String getGeneratorName() {
    return "node-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("node:24-slim");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "npm install",
      "npx tsc --noEmit"
    };
  }

  @Test
  void generatedCodeShouldPassTypeChecking() throws IOException {
    generateClientToDirectory(Map.of(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated TypeScript code has type checking errors:\n%s", result.output())
        .isTrue();
  }

}
