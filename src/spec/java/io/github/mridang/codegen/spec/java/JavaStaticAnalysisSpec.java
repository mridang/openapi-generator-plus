package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated Java code passes SpotBugs static analysis. If this test fails, the Java
 * templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-java")
public class JavaStaticAnalysisSpec extends AbstractIntegrationSpec implements JavaSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"mvn compile test-compile spotbugs:check -B"};
  }

  @Test
  void generatedCodeShouldPassStaticAnalysis() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Java code has SpotBugs violations:\n%s", result.output())
        .isTrue();
  }
}
