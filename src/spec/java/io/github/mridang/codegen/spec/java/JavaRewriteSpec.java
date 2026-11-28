package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the OpenRewrite recipe the generated pom wires ({@code UpgradeToJava25}, run by
 * the SDK's {@code make upgrade}) has nothing left to change in the generated Java code. The dry
 * run fails when the recipe would rewrite a file, so code the templates emit in an older idiom
 * is caught here instead of being rewritten silently in a client (#124). If this test fails, the
 * Java templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-java")
public class JavaRewriteSpec extends AbstractIntegrationSpec implements JavaSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"mvn rewrite:dryRun -Drewrite.failOnDryRunResults=true -B"};
  }

  @Test
  void generatedCodeShouldNeedNoRewrite() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("OpenRewrite would change the generated Java code:\n%s", result.output())
        .isTrue();
  }
}
