package io.github.mridang.codegen.spec.csharp;

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
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated C# code is already properly formatted according to
 * CSharpier. If this test fails, the C# templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class CSharpFormattingSpec extends AbstractIntegrationSpec {

  @Override
  protected String getGeneratorName() {
    return "csharp-plus";
  }

  @Override
  protected DockerImageName getRuntimeImage() {
    return DockerImageName.parse("mcr.microsoft.com/dotnet/sdk:9.0");
  }

  @Override
  protected String[] getBuildCommands() {
    return new String[] {
      "dotnet tool install --global csharpier",
      "/root/.dotnet/tools/csharpier check ."
    };
  }

  @Override
  protected String getTestScript(String prismBaseUrl) {
    return "";
  }

  @Test
  void generatedCodeShouldBeProperlyFormatted() {
    generateClientToDirectory(
        Map.of(
            CodegenConstants.PACKAGE_NAME, "PetstoreClient",
            CodegenConstants.SOURCE_FOLDER, "src"),
        tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage("Generated C# code is not properly formatted:\n%s", result.output())
        .isTrue();
  }

  @Test
  void generatedCodeShouldNotContainHtmlEntities() throws IOException {
    generateClientToDirectory(
        Map.of(
            CodegenConstants.PACKAGE_NAME, "PetstoreClient",
            CodegenConstants.SOURCE_FOLDER, "src"),
        tempOutputDir);

    Pattern htmlEntity = Pattern.compile("&(lt|gt|amp|quot);");
    List<String> violations = new ArrayList<>();

    try (Stream<Path> files = Files.walk(tempOutputDir)) {
      files
          .filter(p -> p.toString().endsWith(".cs"))
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
    generateClientToDirectory(
        Map.of(
            CodegenConstants.PACKAGE_NAME, "PetstoreClient",
            CodegenConstants.SOURCE_FOLDER, "src"),
        tempOutputDir);

    Pattern inlineComment = Pattern.compile("^\\s*//(?!/)");
    List<String> violations = new ArrayList<>();

    try (Stream<Path> files = Files.walk(tempOutputDir)) {
      files
          .filter(p -> p.toString().endsWith(".cs"))
          .filter(p -> !p.toString().contains("/Models/"))
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
