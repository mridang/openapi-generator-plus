package io.github.mridang.codegen.spec.ruby;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated Ruby code passes RuboCop linting rules (excluding Layout,
 * which is checked by RubyFormattingSpec). If this test fails, the Ruby templates
 * need to be fixed.
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
    return DockerImageName.parse("ruby:3.4-slim");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "apt-get update && apt-get install -y build-essential --no-install-recommends 2>/dev/null",
      "bundle install --quiet",
      "bundle exec rubocop --except Layout --format simple"
    };
  }

  @Test
  void generatedCodeShouldPassLinting() throws IOException {
    generateClientToDirectory(
        Map.of("gemName", "opigen_client", "moduleName", "OpigenClient"),
        tempOutputDir);

    Files.writeString(
        tempOutputDir.resolve(".rubocop.yml"),
        String.join(
            "\n",
            "AllCops:",
            "  NewCops: enable",
            "  SuggestExtensions: false",
            "",
            "# Disable entire departments inappropriate for generated code",
            "Metrics:",
            "  Enabled: false",
            "Naming:",
            "  Enabled: false",
            "Style:",
            "  Enabled: false",
            "",
            ""));

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Ruby code has linting violations:\n%s", result.output())
        .isTrue();
  }
}
