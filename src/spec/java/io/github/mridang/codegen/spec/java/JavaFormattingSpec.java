package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated Java code is already properly formatted according to google-java-format.
 * If this test fails, the Java templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-java")
public class JavaFormattingSpec extends AbstractFormattingSpec implements JavaSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"mvn fmt:check -B"};
  }

  @Override
  protected String getFileExtension() {
    return ".java";
  }

  @Override
  protected Path getSourceRoot() {
    return tempOutputDir.resolve("src");
  }

  @Override
  protected boolean includeFileForInlineCommentCheck(Path file) {
    return file.toString().contains("/src/main/");
  }

  @Override
  protected boolean skipFileHeaderComments() {
    return true;
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Java code is not properly formatted:\n%s", result.output())
        .isTrue();
  }
}
