package io.github.mridang.codegen.spec.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.codegen.CodegenConstants;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that generated C# code compiles cleanly with the strictest .NET SDK settings. Uses
 * TreatWarningsAsErrors, AnalysisLevel=latest-all, EnforceCodeStyleInBuild, and Nullable=enable. If
 * this test fails, the C# templates need to be fixed.
 */
@SuppressWarnings("NewClassNamingConvention")
@Testcontainers
public class CSharpBuildSpec extends AbstractIntegrationSpec {

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
    return new String[] {"dotnet build src/PetstoreClient/PetstoreClient.csproj"};
  }

  @Test
  void generatedCodeShouldCompileWithStrictSettings() throws IOException {
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
            "    <TreatWarningsAsErrors>true</TreatWarningsAsErrors>",
            "    <AnalysisLevel>latest-all</AnalysisLevel>",
            "    <EnforceCodeStyleInBuild>true</EnforceCodeStyleInBuild>",
            "    <WarningLevel>9999</WarningLevel>",
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
            "# Style preferences",
            "csharp_style_var_for_built_in_types = true",
            "csharp_style_var_when_type_is_apparent = true",
            "csharp_style_var_elsewhere = true",
            "csharp_style_namespace_declarations = file_scoped",
            "csharp_style_prefer_primary_constructors = false",
            "",
            "# URL-as-string is standard for generated API clients",
            "dotnet_diagnostic.CA1054.severity = none",
            "dotnet_diagnostic.CA1056.severity = none",
            "# Configuration name is standard across all generated clients",
            "dotnet_diagnostic.CA1724.severity = none",
            "# Enum suffix needed to avoid CS0102 collision with property name",
            "dotnet_diagnostic.CA1711.severity = none",
            "# List<T> is standard for generated model collection properties",
            "dotnet_diagnostic.CA1002.severity = none",
            "# Collection property setters needed for JSON deserialization",
            "dotnet_diagnostic.CA2227.severity = none",
            "# BaseApi holds ApiClient for its lifetime, not a leak",
            "dotnet_diagnostic.CA2000.severity = none",
            ""));

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated C# code has build warnings/errors with strict settings:\n%s",
            result.output())
        .isTrue();
  }
}
