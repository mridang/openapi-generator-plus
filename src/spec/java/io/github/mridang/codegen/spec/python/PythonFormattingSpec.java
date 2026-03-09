package io.github.mridang.codegen.spec.python;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.util.Map;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated Python code is already properly formatted according to
 * ruff format. If this test fails, the Python templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class PythonFormattingSpec extends AbstractFormattingSpec implements PythonSpec {

  private static final String PACKAGE_NAME = "petstore_client";

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "pip install --quiet -r requirements.txt",
      "ruff format --check ."
    };
  }

  @Override
  protected String getFileExtension() {
    return ".py";
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of(
        CodegenConstants.PACKAGE_NAME, PACKAGE_NAME,
        CodegenConstants.PROJECT_NAME, "petstore-client");
  }

  @Override
  @Nullable
  protected String getInlineCommentPattern() {
    return null;
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Python code is not properly formatted:\n%s", result.output())
        .isTrue();
  }
}
