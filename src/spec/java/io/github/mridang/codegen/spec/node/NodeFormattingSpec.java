package io.github.mridang.codegen.spec.node;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated TypeScript code is already properly formatted according to prettier. If
 * this test fails, the Node templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-node")
public class NodeFormattingSpec extends AbstractFormattingSpec implements NodeSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"npm install", "npx prettier --check ."};
  }

  @Override
  protected String getFileExtension() {
    return ".ts";
  }

  @Override
  protected Path getSourceRoot() {
    return tempOutputDir.resolve("src");
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated TypeScript code is not properly formatted:\n%s", result.output())
        .isTrue();
  }
}
