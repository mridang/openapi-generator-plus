package io.github.mridang.codegen.spec.ruby;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated Ruby code passes all RuboCop linting rules. If this test fails, the Ruby
 * templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-ruby")
public class RubyLintingSpec extends AbstractIntegrationSpec implements RubySpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"bundle exec rubocop --format simple"};
  }

  @Test
  void generatedCodeShouldPassLinting() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Ruby code has linting violations:\n%s", result.output())
        .isTrue();
  }
}
