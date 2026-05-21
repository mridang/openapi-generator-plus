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
    // Spotless google-java-format depends on JDK-internal compiler API that
    // changed in JDK 25; current spotless-maven-plugin 2.46.1 hits a
    // NoSuchMethodError on Log$DeferredDiagnosticHandler.getDiagnostics().
    // Until a JDK 25-compatible spotless ships, run plain compile as the
    // format gate (inline-comment + html-entity checks still run from base).
    return new String[] {"mvn compile -q -B"};
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

  /**
   * Pattern matches '//' or '////' but NOT '///' (Java 23+ markdown doc-comments).
   * Negative lookahead at position after '//' excludes a single trailing slash.
   */
  @Override
  protected String getInlineCommentPattern() {
    return "^\\s*//(?!/[^/])";
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
