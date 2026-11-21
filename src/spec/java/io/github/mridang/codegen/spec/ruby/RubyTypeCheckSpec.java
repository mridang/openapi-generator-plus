package io.github.mridang.codegen.spec.ruby;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies that generated Ruby code passes Steep type checking with RBS signatures. If this test
 * fails, the Ruby templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
@ResourceLock("generated-ruby")
public class RubyTypeCheckSpec extends AbstractIntegrationSpec implements RubySpec {

  @Override
  protected String[] getBuildCommands() {
    return new String[] {"bundle exec steep check"};
  }

  @Test
  void generatedCodeShouldPassTypeChecking() {
    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Ruby code has type checking errors:\n%s", result.output())
        .isTrue();
  }
}
