package io.github.mridang.codegen.spec.ruby;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.net.URL;
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
      "gem install rubocop --no-document",
      "rubocop --except Layout --format simple"
    };
  }

  @Override
  protected String getTestScript(String prismBaseUrl) {
    return "";
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
            "Style:",
            "  Enabled: false",
            "",
            "# Keep Lint cops but disable ones inherent to code generation",
            "Lint/UnusedMethodArgument:",
            "  Enabled: false",
            "Lint/DuplicateBranch:",
            "  Enabled: false",
            "Lint/MissingSuper:",
            "  Enabled: false",
            "Lint/SymbolConversion:",
            "  Enabled: false",
            ""));

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated Ruby code has linting violations:\n%s", result.output())
        .isTrue();
  }

  private void generateClientToDirectory(
      Map<String, Object> additionalProperties, java.nio.file.Path outputDir) {
    URL specUrl = getClass().getClassLoader().getResource(getSpecResourcePath());
    if (specUrl == null) {
      throw new IllegalStateException("Could not find spec resource: " + getSpecResourcePath());
    }

    String specPath = specUrl.getPath();

    org.openapitools.codegen.config.CodegenConfigurator configurator =
        new org.openapitools.codegen.config.CodegenConfigurator()
            .setGeneratorName(getGeneratorName())
            .setInputSpec(specPath)
            .setOutputDir(outputDir.toString().replace("\\", "/"))
            .setAdditionalProperties(additionalProperties);

    org.openapitools.codegen.DefaultGenerator generator =
        new org.openapitools.codegen.DefaultGenerator();
    generator.setGenerateMetadata(false);
    generator.opts(configurator.toClientOptInput()).generate();
  }
}
