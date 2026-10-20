package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated Java code passes static analysis (SpotBugs) and linting (Checkstyle)
 * under industry-default configs. SpotBugs runs with no custom exclude filter and Checkstyle runs
 * the bundled {@code /google_checks.xml}; findings inherent to generated code are silenced by
 * file-level (class-level) suppressions emitted by the templates. If this test fails, the Java
 * templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-java")
public class JavaStaticAnalysisSpec extends AbstractIntegrationSpec implements JavaSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "mvn compile test-compile spotbugs:check checkstyle:check -B"
    };
  }

  @Test
  void generatedCodeShouldPassStaticAnalysis() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Java code has SpotBugs/Checkstyle violations:\n%s", result.output())
        .isTrue();
  }
}
