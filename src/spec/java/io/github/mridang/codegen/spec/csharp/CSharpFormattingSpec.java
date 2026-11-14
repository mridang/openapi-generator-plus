package io.github.mridang.codegen.spec.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import java.util.Set;
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
    return new String[] {
      "dotnet tool restore",
      "dotnet csharpier check .",
      /* The client's `make lint` runs dotnet format as well: the .editorconfig
       * analyzers at warning severity, over the SDK and the test project. */
      "dotnet format --verify-no-changes --severity warn"
    };
  }

  @Override
  protected String getFileExtension() {
    return ".cs";
  }

  @Override
  protected Path getSourceRoot() {
    return tempOutputDir;
  }

  /**
   * An XML doc comment must escape {@code &}, {@code <} and {@code >}; the named forms are banned
   * by the gate, so the C# templates write the numeric references instead.
   */
  @Override
  protected boolean isAllowedNumericEntity(Path file, String entity) {
    return Set.of("&#38;", "&#60;", "&#62;").contains(entity);
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
