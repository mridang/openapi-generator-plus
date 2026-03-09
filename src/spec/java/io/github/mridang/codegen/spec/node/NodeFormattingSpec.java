package io.github.mridang.codegen.spec.node;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated TypeScript code is already properly formatted according to
 * prettier. If this test fails, the Node templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class NodeFormattingSpec extends AbstractFormattingSpec {

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
      "npx prettier --check '**/*.ts'"
    };
  }

  @Override
  protected String getFileExtension() {
    return ".ts";
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of();
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() throws IOException {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    Files.writeString(
        tempOutputDir.resolve(".prettierrc"),
        "{ \"tabWidth\": 2, \"singleQuote\": true, \"printWidth\": 120, \"trailingComma\": \"none\" }\n");

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated TypeScript code is not properly formatted:\n%s", result.output())
        .isTrue();
  }
}
