package io.github.mridang.codegen.spec.ruby;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated Ruby code passes all RuboCop linting rules. If this test fails, the Ruby
 * templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class RubyLintingSpec extends AbstractIntegrationSpec {

  @Override
  protected String getGeneratorName() {
    return "ruby-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("ruby:3.4");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "bundle install --quiet",
      "bundle exec rubocop --format simple"
    };
  }

  @Test
  void generatedCodeShouldPassLinting() {
    generateClientToDirectory(
        Map.of("gemName", "opigen_client", "moduleName", "OpigenClient"), tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Ruby code has linting violations:\n%s", result.output())
        .isTrue();
  }
}
