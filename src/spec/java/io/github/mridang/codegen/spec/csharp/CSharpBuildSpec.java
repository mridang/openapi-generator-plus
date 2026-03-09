package io.github.mridang.codegen.spec.csharp;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mridang.codegen.spec.AbstractIntegrationSpec;
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
  void generatedCodeShouldCompileWithStrictSettings() {
    generateClientToDirectory(
        Map.of(
            CodegenConstants.PACKAGE_NAME, "PetstoreClient",
            CodegenConstants.SOURCE_FOLDER, "src"),
        tempOutputDir);

    ExecResult result = executeInRuntimeContainer(getBuildCommands());

    assertThat(result.isSuccess())
        .withFailMessage(
            "Generated C# code has build warnings/errors with strict settings:\n%s",
            result.output())
        .isTrue();
  }
}
