package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractFormattingSpec;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated Java code is already properly formatted according to google-java-format.
 * If this test fails, the Java templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class JavaFormattingSpec extends AbstractFormattingSpec implements JavaSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"mvn fmt:check -B"};
  }

  @Override
  protected String getFileExtension() {
    return ".java";
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() {
    generateClientToDirectory(getCodegenProperties(), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Java code is not properly formatted:\n%s", result.output())
        .isTrue();
  }
}
