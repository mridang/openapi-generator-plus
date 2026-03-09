package io.github.mridang.codegen.spec.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated C# code is already properly formatted according to
 * CSharpier. If this test fails, the C# templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class CSharpFormattingSpec extends AbstractFormattingSpec implements CSharpSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"dotnet tool restore", "dotnet csharpier check ."};
  }

  @Override
  protected String getFileExtension() {
    return ".cs";
  }

  @Override
  protected Map<String, Object> getCodegenProperties() {
    return Map.of(
        CodegenConstants.PACKAGE_NAME, "PetstoreClient",
        CodegenConstants.SOURCE_FOLDER, "src");
  }

  @Override
  protected String getInlineCommentPattern() {
    return "^\\s*//(?!/)";
  }

  @Override
  protected boolean includeFileForInlineCommentCheck(Path file) {
    return !file.toString().contains("/Models/");
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated C# code is not properly formatted:\n%s", result.output())
        .isTrue();
  }
}
