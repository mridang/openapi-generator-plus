package io.github.mridang.codegen.spec.node;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated TypeScript code is already properly formatted according to
 * prettier. If this test fails, the Node templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class NodeFormattingSpec extends AbstractIntegrationSpec {

  @Override
  protected String getGeneratorName() {
    return "node-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("node:24-slim");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "npm install -g prettier",
      "prettier --check '**/*.ts'"
    };
  }

  @Override
  protected String getTestScript(String prismBaseUrl) {
    return "";
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() throws IOException {
    generateClientToDirectory(Map.of(), tempOutputDir);

    // Write prettier config to match our generated code style
    Files.writeString(
        tempOutputDir.resolve(".prettierrc"),
        "{ \"tabWidth\": 2, \"singleQuote\": true, \"printWidth\": 120, \"trailingComma\": \"none\" }\n");

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated TypeScript code is not properly formatted:\n%s", result.output())
        .isTrue();
  }

  @Test
  void generatedCodeShouldNotContainHtmlEntities() throws IOException {
    generateClientToDirectory(Map.of(), tempOutputDir);

    Pattern htmlEntity = Pattern.compile("&(lt|gt|amp|quot);");
    List<String> violations = new ArrayList<>();

    try (Stream<Path> files = Files.walk(tempOutputDir)) {
      files
          .filter(p -> p.toString().endsWith(".ts"))
          .forEach(
              p -> {
                try {
                  List<String> lines = Files.readAllLines(p);
                  for (int i = 0; i < lines.size(); i++) {
                    if (htmlEntity.matcher(lines.get(i)).find()) {
                      violations.add(
                          p.getFileName() + ":" + (i + 1) + ": " + lines.get(i).trim());
                    }
                  }
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    }

    assertThat(violations)
        .withFailMessage(
            "Found HTML-encoded entities in generated code:\n%s", String.join("\n", violations))
        .isEmpty();
  }

  @Test
  void generatedCodeShouldNotContainInlineComments() throws IOException {
    generateClientToDirectory(Map.of(), tempOutputDir);

    Pattern inlineComment = Pattern.compile("^\\s*//");
    List<String> violations = new ArrayList<>();

    try (Stream<Path> files = Files.walk(tempOutputDir)) {
      files
          .filter(p -> p.toString().endsWith(".ts"))
          .forEach(
              p -> {
                try {
                  List<String> lines = Files.readAllLines(p);
                  for (int i = 0; i < lines.size(); i++) {
                    if (inlineComment.matcher(lines.get(i)).find()) {
                      violations.add(
                          p.getFileName() + ":" + (i + 1) + ": " + lines.get(i).trim());
                    }
                  }
                } catch (IOException e) {
                  throw new UncheckedIOException(e);
                }
              });
    }

    assertThat(violations)
        .withFailMessage(
            "Found inline comments in generated code:\n%s", String.join("\n", violations))
        .isEmpty();
  }

  private void generateClientToDirectory(Map<String, Object> additionalProperties, Path outputDir) {
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

    org.openapitools.codegen.DefaultGenerator generator = new org.openapitools.codegen.DefaultGenerator();
    generator.setGenerateMetadata(false);
    generator.opts(configurator.toClientOptInput()).generate();
  }
}
