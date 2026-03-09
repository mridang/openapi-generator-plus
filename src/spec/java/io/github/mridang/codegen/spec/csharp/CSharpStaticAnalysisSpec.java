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
            "",
            "# Do not declare visible instance fields",
            "dotnet_diagnostic.CA1051.severity = none",
            "# Do not expose generic lists",
            "dotnet_diagnostic.CA1002.severity = none",
            "# Properties should not return arrays",
            "dotnet_diagnostic.CA1819.severity = none",
            "# Mark members as static",
            "dotnet_diagnostic.CA1822.severity = none",
            "# Validate arguments of public methods",
            "dotnet_diagnostic.CA1062.severity = none",
            "# Identifiers should not contain underscores",
            "dotnet_diagnostic.CA1707.severity = none",
            "# Use properties where appropriate",
            "dotnet_diagnostic.CA1024.severity = none",
            "# Implement standard exception constructors",
            "dotnet_diagnostic.CA1032.severity = none",
            "# Nested types should not be visible",
            "dotnet_diagnostic.CA1034.severity = none",
            "# Override methods on comparable types",
            "dotnet_diagnostic.CA1036.severity = none",
            "# Enum storage should be Int32",
            "dotnet_diagnostic.CA1028.severity = none",
            "# Use literals where appropriate",
            "dotnet_diagnostic.CA1802.severity = none",
            "# Do not initialize unnecessarily",
            "dotnet_diagnostic.CA1805.severity = none",
            "# Call GC.SuppressFinalize correctly",
            "dotnet_diagnostic.CA1816.severity = none",
            "# Type name should not end in Enum",
            "dotnet_diagnostic.CA1711.severity = none",
            "# Prefer static readonly over constant array args",
            "dotnet_diagnostic.CA1861.severity = none",
            "# ThrowIfNull on non-nullable is no-op",
            "dotnet_diagnostic.CA2264.severity = none",
            "# Type owns disposable field but is not disposable",
            "dotnet_diagnostic.CA1001.severity = none",
            "# Use GeneratedRegexAttribute",
            "dotnet_diagnostic.SYSLIB1045.severity = none",
            "# Non-nullable property must contain non-null value",
            "dotnet_diagnostic.CS8618.severity = none",
            "# Possible null reference return",
            "dotnet_diagnostic.CS8603.severity = none",
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
