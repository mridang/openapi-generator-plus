package io.github.mridang.codegen.spec.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated C# code passes Roslyn static analysis.
 * Uses dotnet build with warnings as errors and Microsoft.CodeAnalysis.NetAnalyzers.
 * If this test fails, the C# templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class CSharpStaticAnalysisSpec extends AbstractIntegrationSpec {

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
      "dotnet add src/PetstoreClient/PetstoreClient.csproj"
          + " package Microsoft.CodeAnalysis.NetAnalyzers --version 9.0.0",
      "dotnet build src/PetstoreClient/PetstoreClient.csproj --warnaserror"
    };
  }

  @Override
  protected String getTestScript(String prismBaseUrl) {
    return "";
  }

  @Test
  void generatedCodeShouldPassStaticAnalysis() throws IOException {
    generateClientToDirectory(
        Map.of(
            CodegenConstants.PACKAGE_NAME, "PetstoreClient",
            CodegenConstants.SOURCE_FOLDER, "src"),
        tempOutputDir);

    Files.writeString(
        tempOutputDir.resolve("src/PetstoreClient/PetstoreClient.csproj"),
        String.join(
            "\n",
            "<Project Sdk=\"Microsoft.NET.Sdk\">",
            "  <PropertyGroup>",
            "    <TargetFramework>net9.0</TargetFramework>",
            "    <Nullable>enable</Nullable>",
            "    <ImplicitUsings>enable</ImplicitUsings>",
            "  </PropertyGroup>",
            "</Project>",
            ""));

    Files.writeString(
        tempOutputDir.resolve(".editorconfig"),
        String.join(
            "\n",
            "root = true",
            "",
            "[*.cs]",
            "dotnet_analyzer_diagnostic.severity = warning",
            "# Nested enums must use Enum suffix to avoid CS0102 collision with property name",
            "dotnet_diagnostic.CA1711.severity = none",
            ""));

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated C# code has static analysis violations:\n%s", result.output())
        .isTrue();
  }

  private void generateClientToDirectory(
      Map<String, Object> additionalProperties, Path outputDir) {
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
