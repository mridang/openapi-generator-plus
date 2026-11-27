package io.github.mridang.codegen.spec.java;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that the generated Java SDK and its tests are already on the Java 25 idioms that the pom's
 * OpenRewrite recipe (UpgradeToJava25) would rewrite. The dry run fails on any suggested change and
 * also fails when it leaves a patch file behind, so a finding cannot pass silently. This is the Java
 * counterpart of PhpModernizationSpec (Rector).
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-java")
public class JavaModernizationSpec extends AbstractIntegrationSpec implements JavaSpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "mvn -B -q rewrite:dryRun -Drewrite.failOnDryRunResults=true && test ! -e target/rewrite/rewrite.patch"
    };
  }

  @Test
  void generatedCodeShouldBeModern() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated Java code has OpenRewrite UpgradeToJava25 suggestions:\n%s", result.output())
        .isTrue();
  }
}
