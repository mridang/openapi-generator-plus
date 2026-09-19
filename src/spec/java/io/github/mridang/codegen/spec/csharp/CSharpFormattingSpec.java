package io.github.mridang.codegen.spec.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated C# code is already properly formatted according to
 * CSharpier. If this test fails, the C# templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-csharp")
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
  protected Path getSourceRoot() {
    return tempOutputDir;
  }

  @Override
  protected String getInlineCommentPattern() {
    return "^\\s*//(?!/)";
  }

  @Override
  protected boolean includeFileForInlineCommentCheck(Path file) {
    String path = file.toString();
    // Exclude MSBuild-generated /obj/ (AssemblyInfo.cs etc) — not source.
    return !path.contains("/Models/")
        && !path.contains("/obj/")
        && path.contains("/src/PetstoreClient/");
  }

  @Override
  protected boolean skipFileHeaderComments() {
    return true;
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated C# code is not properly formatted:\n%s", result.output())
        .isTrue();
  }
}
